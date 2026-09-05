package com.phomoria.ui;

import com.github.sarxos.webcam.Webcam;
import com.phomoria.app.AppContext;
import com.phomoria.app.ApplicationFrame;
import com.phomoria.camera.CameraBackend;
import com.phomoria.camera.CameraManager;
import com.phomoria.camera.CameraMirrorProcessor;
import com.phomoria.camera.CameraReconnectController;
import com.phomoria.camera.GPhoto2PersistentCameraBackend;
import com.phomoria.camera.LiveCameraPanel;
import com.phomoria.cloud.CloudFrameSupport;
import com.phomoria.debug.DebugLog;
import com.phomoria.frame.FrameCatalog;
import com.phomoria.frame.FrameDefinition;
import com.phomoria.frame.FramePreset;
import com.phomoria.session.CaptureController;
import com.phomoria.session.PhotoSession;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;




public final class MainScreen
        extends JPanel
        implements CaptureController.Listener {

    private final ApplicationFrame frame;
    private final JLabel status =
            new JLabel("MENYIAPKAN KAMERA...");
    private final JLabel counter =
            new JLabel("0 / 0");

    private final LiveCameraPanel cameraView =
            new LiveCameraPanel();

    private final CountdownOverlay countdownOverlay =
            new CountdownOverlay();

    private final FramePreviewPanel preview =
            new FramePreviewPanel();

    private final JPanel cameraLayer =
            new OverlayPanel();

    private final FrameDefinition selectedFrameDefinition;
    private final CaptureController captureController;

    private Webcam activeWebcam;
    private CameraBackend activeBackend;

    private CameraReconnectController reconnectController;

    private Timer starterTimer;
    private Timer freezeTimer;

    private int freezeSeconds;
    private int selectedIndex = -1;

    private boolean retakeMode;
    private boolean active = false;
    private boolean shuttingDown = false;

    public MainScreen(ApplicationFrame frame) {
        this.frame = frame;
        this.captureController =
                new CaptureController(this);

        setLayout(
                new BorderLayout(12, 12)
        );

        setBorder(
                BorderFactory.createEmptyBorder(
                        12, 12, 12, 12
                )
        );

        setBackground(
                new Color(18, 18, 22)
        );

        cameraLayer.setLayout(
                new OverlayLayout(cameraLayer)
        );

        cameraLayer.setBackground(Color.BLACK);

        cameraLayer.add(countdownOverlay);
        cameraLayer.add(cameraView);

        add(
                createTopBar(),
                BorderLayout.NORTH
        );

        JSplitPane split =
                new JSplitPane(
                        JSplitPane.HORIZONTAL_SPLIT,
                        cameraLayer,
                        preview
                );

        split.setResizeWeight(0.68);
        split.setBorder(null);

        add(
                split,
                BorderLayout.CENTER
        );

        add(
                createBottomBar(),
                BorderLayout.SOUTH
        );

        preview.setSelectionListener(
                index -> {
                    selectedIndex = index;

                    DebugLog.info(
                            "Preview selected photo index="
                                    + index
                    );

                    status.setText(
                            "FOTO "
                                    + (index + 1)
                                    + " DIPILIH — TEKAN RETAKE"
                    );
                }
        );

        FrameDefinition cloudDefinition =
                CloudFrameSupport.selectedDefinition(
                        AppContext.settings()
                                .getPhotoSlotCount()
                );

        if (cloudDefinition != null
                && !cloudDefinition.getPlacements().isEmpty()) {

            selectedFrameDefinition = cloudDefinition;

            DebugLog.info(
                    "MainScreen using cloud frame definition: name="
                            + cloudDefinition.getName()
                            + ", placements="
                            + cloudDefinition
                                    .getPlacements()
                                    .size()
            );

        } else {

            FramePreset selectedPreset =
                    FrameCatalog.find(
                            AppContext.settings()
                                    .getSelectedFrameId()
                    );

            if (selectedPreset == null) {
                selectedPreset =
                        FrameCatalog.find(
                                "standard_vertical"
                        );
            }

            selectedFrameDefinition =
                    FrameCatalog.createDefinition(
                            selectedPreset,
                            AppContext.settings()
                                    .getPhotoSlotCount()
                    );

            DebugLog.info(
                    "MainScreen frame="
                            + selectedPreset.id()
                            + ", placements="
                            + selectedFrameDefinition
                                    .getPlacements()
                                    .size()
            );
        }

        refreshPreview();

        active = true;

        DebugLog.info(
                "MainScreen activated."
        );

        cameraView.setCameraConnectionLostListener(
                this::handleCameraConnectionLost
        );

        startCamera();
    }

    private JPanel createTopBar() {
        JPanel panel =
                new JPanel(
                        new BorderLayout()
                );

        panel.setOpaque(false);

        JLabel title =
                new JLabel(
                        "PHOMORIA  •  PHOTO BOOTH"
                );

        title.setForeground(Color.WHITE);
        title.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        22
                )
        );

        status.setForeground(
                new Color(120, 220, 150)
        );

        panel.add(
                title,
                BorderLayout.WEST
        );

        panel.add(
                status,
                BorderLayout.EAST
        );

        return panel;
    }

    private JPanel createBottomBar() {
        JPanel panel =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.CENTER,
                                14,
                                8
                        )
                );

        panel.setOpaque(false);

        JButton retake =
                new JButton(
                        "RETAKE FOTO TERPILIH"
                );

        retake.addActionListener(
                e -> startRetake()
        );

        panel.add(counter);
        panel.add(retake);

        if (AppContext.settings().isShowCancelSession()) {
            JButton cancelSession =
                    new JButton("PILIH ULANG FRAME");

            cancelSession.addActionListener(
                    e -> cancelPhotoSession()
            );

            panel.add(cancelSession);
        }

        return panel;
    }

    private void cancelPhotoSession() {
        if (!active || shuttingDown) {
            return;
        }

        if (!AppContext.settings().isShowCancelSession()) {
            DebugLog.warn(
                    "Cancel session ignored because feature is disabled."
            );
            return;
        }

        DebugLog.info(
                "Cancel photo session requested."
        );

        stopAllTimers();
        captureController.stop();

        if (reconnectController != null) {
            reconnectController.stop();
        }

        frame.cancelPhotoSession();
    }

    private void startCamera() {
        if (!active || shuttingDown) {
            DebugLog.warn(
                    "startCamera ignored because MainScreen "
                            + "is inactive."
            );

            return;
        }

        DebugLog.info(
                "Starting camera screen."
        );

        SwingUtilities.invokeLater(
                () -> {
                    try {
                        String configuredName =
                                AppContext.settings()
                                        .getCameraName();

                        DebugLog.info(
                                "Camera configured in Settings="
                                        + configuredName
                        );

                        if (configuredName != null
                                && (configuredName.startsWith(
                                "gphoto2:"
                        )
                                || configuredName.endsWith(
                                " [gPhoto2]"
                        ))) {

                            activeWebcam = null;

                            activeBackend =
                                    CameraManager
                                            .openConfiguredBackend(
                                                    configuredName
                                            );

                            createReconnectController(
                                    configuredName
                            );

                            cameraView.setCropGuide(
                                    selectedFrameDefinition,
                                    0
                            );

                            cameraView.attach(
                                    activeBackend
                            );

                            cameraView.showCropGuide();

                            status.setText(
                                    "SIAP — SESSION OTOMATIS DIMULAI"
                            );

                        } else if (
                                configuredName == null
                                        || configuredName.isBlank()
                        ) {

                            List<Webcam> cameras =
                                    CameraManager.list();

                            DebugLog.info(
                                    "Camera count detected="
                                            + cameras.size()
                            );

                            if (cameras.isEmpty()) {
                                showCameraError(
                                        "Camera tidak ditemukan."
                                );

                                return;
                            }

                            activeWebcam =
                                    cameras.get(0);

                            activeBackend = null;

                            DebugLog.warn(
                                    "No camera saved in Settings. "
                                            + "Using first detected camera: "
                                            + activeWebcam.getName()
                            );

                            CameraManager.open(
                                    activeWebcam
                            );

                            cameraView.setCropGuide(
                                    selectedFrameDefinition,
                                    0
                            );

                            cameraView.attach(
                                    activeWebcam
                            );

                            cameraView.showCropGuide();

                            status.setText(
                                    "SIAP — SESSION OTOMATIS DIMULAI"
                            );

                        } else {

                            activeWebcam =
                                    CameraManager.openConfigured(
                                            configuredName
                                    );

                            activeBackend = null;

                            if (activeWebcam == null) {
                                showCameraError(
                                        "Kamera yang dipilih tidak "
                                                + "tersedia: "
                                                + configuredName
                                );

                                return;
                            }

                            cameraView.setCropGuide(
                                    selectedFrameDefinition,
                                    0
                            );

                            cameraView.attach(
                                    activeWebcam
                            );

                            cameraView.showCropGuide();

                            status.setText(
                                    "SIAP — SESSION OTOMATIS DIMULAI"
                            );
                        }

                        starterTimer =
                                new Timer(
                                        1200,
                                        e -> {
                                            starterTimer.stop();
                                            startNextCapture();
                                        }
                                );

                        starterTimer.setRepeats(false);
                        starterTimer.start();

                    } catch (Exception ex) {
                        DebugLog.error(
                                "Camera initialization failed.",
                                ex
                        );

                        showCameraError(
                                "Camera gagal dibuka: "
                                        + ex.getMessage()
                        );
                    }
                }
        );
    }

    private void createReconnectController(
            String configuredName
    ) {
        if (configuredName == null
                || (!configuredName.startsWith("gphoto2:")
                && !configuredName.endsWith(" [gPhoto2]"))) {

            return;
        }

        if (reconnectController != null) {
            reconnectController.stop();
        }

        reconnectController =
                new CameraReconnectController(
                        configuredName,
                        new CameraReconnectController.Listener() {

                            @Override
                            public void onWaitingForCamera() {
                                if (!active || shuttingDown) {
                                    return;
                                }

                                status.setText(
                                        "KAMERA TERPUTUS — "
                                                + "MENUNGGU KAMERA..."
                                );

                                countdownOverlay.hideOverlay();
                                cameraView.hideCropGuide();
                            }

                            @Override
                            public void onCameraReconnected(
                                    CameraBackend backend
                            ) {
                                if (!active || shuttingDown) {
                                    CameraManager.close();
                                    return;
                                }

                                activeBackend = backend;
                                activeWebcam = null;

                                DebugLog.info(
                                        "New gPhoto2 backend attached "
                                                + "after reconnect."
                                );

                                cameraView.setCropGuide(
                                        selectedFrameDefinition,
                                        AppContext.session()
                                                .getCapturedCount()
                                );

                                cameraView.attach(
                                        backend
                                );

                                cameraView.showCropGuide();

                                status.setText(
                                        "KAMERA TERHUBUNG KEMBALI — "
                                                + "SESSION DILANJUTKAN"
                                );

                                Timer resume =
                                        new Timer(
                                                1000,
                                                e -> {
                                                    ((Timer) e.getSource())
                                                            .stop();

                                                    if (!active
                                                            || shuttingDown) {
                                                        return;
                                                    }

                                                    startNextCapture();
                                                }
                                        );

                                resume.setRepeats(false);
                                resume.start();
                            }
                        }
                );
    }

    private void handleCameraConnectionLost() {
        if (!active || shuttingDown) {
            return;
        }

        if (activeBackend == null) {
            return;
        }

        DebugLog.warn(
                "MainScreen received gPhoto2 connection-loss event."
        );

        stopAllTimers();
        captureController.stop();

        if (reconnectController != null) {
            reconnectController.handleDisconnect();
        }
    }

    private void startNextCapture() {
        if (!active || shuttingDown) {
            return;
        }

        if (reconnectController != null
                && reconnectController.isReconnecting()) {
            return;
        }

        PhotoSession session =
                AppContext.session();

        if (session.isComplete()) {
            showResult();
            return;
        }

        retakeMode = false;
        selectedIndex = -1;
        preview.clearSelection();

        cameraView.setCropGuide(
                selectedFrameDefinition,
                session.getCapturedCount()
        );

        showLiveCamera();

        captureController.startNewPhoto();
    }

    private void startRetake() {
        if (!active || shuttingDown) {
            return;
        }

        if (reconnectController != null
                && reconnectController.isReconnecting()) {
            return;
        }

        stopAllTimers();
        captureController.stop();

        PhotoSession session =
                AppContext.session();

        if (selectedIndex < 0
                || selectedIndex >= session.getCapturedCount()) {

            status.setText(
                    "PILIH FOTO DI PANEL KANAN TERLEBIH DAHULU"
            );

            return;
        }

        if (freezeTimer != null) {
            freezeTimer.stop();
            freezeTimer = null;
        }

        retakeMode = true;

        final int retakeIndex =
                selectedIndex;

        DebugLog.info(
                "Retake requested for photo index="
                        + retakeIndex
        );

        cameraView.setCropGuide(
                selectedFrameDefinition,
                retakeIndex
        );

        showLiveCamera();

        status.setText(
                "LIVE CAMERA — SIAP RETAKE FOTO "
                        + (retakeIndex + 1)
        );

        Timer prepare =
                new Timer(
                        700,
                        e -> {
                            ((Timer) e.getSource()).stop();

                            if (!active || shuttingDown) {
                                return;
                            }

                            captureController.startRetake(
                                    retakeIndex
                            );
                        }
                );

        prepare.setRepeats(false);
        prepare.start();
    }

    @Override
    public void onCountdown(
            int seconds,
            boolean retake,
            int targetIndex
    ) {
        if (!active || shuttingDown) {
            return;
        }

        countdownOverlay.showNumber(
                seconds
        );

        if (retake) {
            status.setText(
                    "RETAKE FOTO "
                            + (targetIndex + 1)
                            + "  •  "
                            + seconds
            );
        } else {
            status.setText(
                    "FOTO "
                            + (AppContext.session()
                            .getCapturedCount() + 1)
                            + "  •  "
                            + seconds
            );
        }
    }

    @Override
    public void onCaptured(
            BufferedImage image,
            boolean retake,
            int targetIndex
    ) {
        if (!active || shuttingDown) {
            return;
        }

        if (reconnectController != null
                && reconnectController.isReconnecting()) {
            DebugLog.warn(
                    "Ignoring capture callback while camera "
                            + "reconnect is active."
            );

            return;
        }

        countdownOverlay.hideOverlay();
        cameraView.hideCropGuide();

        PhotoSession session =
                AppContext.session();

        BufferedImage storedImage =
                CameraMirrorProcessor.process(
                        image,
                        AppContext.settings()
                                .isMirrorPhoto()
                );

        DebugLog.info(
                "Capture orientation applied: mirror="
                        + AppContext.settings()
                        .isMirrorPhoto()
        );

        if (retake) {
            if (!session.replacePhoto(
                    targetIndex,
                    storedImage
            )) {

                status.setText(
                        "RETAKE GAGAL"
                );

                return;
            }

            selectedIndex = -1;
            preview.clearSelection();

            status.setText(
                    "FOTO "
                            + (targetIndex + 1)
                            + " DIGANTI"
            );

        } else {
            session.addPhoto(
                    storedImage
            );

            DebugLog.info(
                    "Photo added. count="
                            + session.getCapturedCount()
                            + "/"
                            + session.getSlotCount()
            );
        }

        refreshPreview();
        showCapturedPhoto(
                storedImage
        );
    }

    @Override
    public void onCaptureError(
            Exception error
    ) {
        if (!active || shuttingDown) {
            return;
        }

        countdownOverlay.hideOverlay();
        cameraView.hideCropGuide();

        if (activeBackend != null
                && GPhoto2PersistentCameraBackend
                .isConnectionFailure(error)) {

            DebugLog.error(
                    "gPhoto2 camera connection lost. "
                            + "Starting reconnect.",
                    error
            );

            stopAllTimers();
            captureController.stop();

            if (reconnectController != null) {
                reconnectController.handleDisconnect();
            }

            return;
        }

        status.setText(
                "GAGAL MENGAMBIL FOTO — MENCOBA LAGI"
        );

        DebugLog.error(
                "Capture error. Retrying.",
                error
        );

        Timer retry =
                new Timer(
                        1000,
                        e -> {
                            ((Timer) e.getSource()).stop();

                            if (!active
                                    || shuttingDown) {
                                return;
                            }

                            if (reconnectController != null
                                    && reconnectController
                                    .isReconnecting()) {
                                return;
                            }

                            showLiveCamera();

                            if (retakeMode) {
                                cameraView.setCropGuide(
                                        selectedFrameDefinition,
                                        selectedIndex
                                );

                                captureController.startRetake(
                                        selectedIndex
                                );

                            } else {
                                cameraView.setCropGuide(
                                        selectedFrameDefinition,
                                        AppContext.session()
                                                .getCapturedCount()
                                );

                                captureController.startNewPhoto();
                            }
                        }
                );

        retry.setRepeats(false);
        retry.start();
    }

    private void showCapturedPhoto(
            BufferedImage image
    ) {
        countdownOverlay.hideOverlay();

        cameraView.showCapturedImage(
                image
        );

        freezeSeconds = 5;

        status.setText(
                "FOTO TERSIMPAN  •  LIVE KAMERA KEMBALI "
                        + freezeSeconds
                        + " DETIK"
        );

        if (freezeTimer != null) {
            freezeTimer.stop();
        }

        freezeTimer =
                new Timer(
                        1000,
                        e -> {
                            freezeSeconds--;

                            if (freezeSeconds <= 0) {
                                freezeTimer.stop();
                                resumeAfterFreeze();

                            } else {
                                status.setText(
                                        "FOTO TERSIMPAN  •  "
                                                + "LIVE KAMERA KEMBALI "
                                                + freezeSeconds
                                                + " DETIK"
                                );
                            }
                        }
                );

        freezeTimer.start();
    }

    private void resumeAfterFreeze() {
        if (!active || shuttingDown) {
            return;
        }

        PhotoSession session =
                AppContext.session();

        DebugLog.info(
                "5-second photo review finished. complete="
                        + session.isComplete()
        );

        if (session.isComplete()) {
            showResult();
            return;
        }

        if (reconnectController != null
                && reconnectController.isReconnecting()) {
            return;
        }

        showLiveCamera();

        Timer next =
                new Timer(
                        700,
                        e -> {
                            ((Timer) e.getSource()).stop();

                            if (!active || shuttingDown) {
                                return;
                            }

                            startNextCapture();
                        }
                );

        next.setRepeats(false);
        next.start();
    }

    private void showLiveCamera() {
        countdownOverlay.hideOverlay();

        int guideSlot =
                retakeMode
                        ? Math.max(0, selectedIndex)
                        : AppContext.session()
                        .getCapturedCount();

        cameraView.setCropGuide(
                selectedFrameDefinition,
                guideSlot
        );

        if (activeBackend != null
                && CameraManager.currentBackend()
                == activeBackend) {

            cameraView.attach(
                    activeBackend
            );

            cameraView.showCropGuide();

            DebugLog.info(
                    "LIVE gPhoto2 camera restored."
            );

        } else if (activeWebcam != null
                && CameraManager.current()
                == activeWebcam) {

            cameraView.attach(
                    activeWebcam
            );

            cameraView.showCropGuide();

            DebugLog.info(
                    "LIVE webcam restored."
            );

        } else if (
                CameraManager.currentBackend() != null
        ) {

            activeBackend =
                    CameraManager.currentBackend();

            cameraView.attach(
                    activeBackend
            );

            cameraView.showCropGuide();

        } else if (
                CameraManager.current() != null
        ) {

            activeWebcam =
                    CameraManager.current();

            cameraView.attach(
                    activeWebcam
            );

            cameraView.showCropGuide();

        } else {
            DebugLog.warn(
                    "Cannot restore live camera: "
                            + "no active camera."
            );
        }
    }

    private void showResult() {
        shutdown();
        frame.showResult();
    }

    private void stopAllTimers() {
        if (starterTimer != null) {
            starterTimer.stop();
            starterTimer = null;
        }

        if (freezeTimer != null) {
            freezeTimer.stop();
            freezeTimer = null;
        }
    }

    public void shutdown() {
        if (shuttingDown) {
            return;
        }

        shuttingDown = true;
        active = false;

        DebugLog.info(
                "MainScreen shutdown started."
        );

        captureController.stop();
        stopAllTimers();

        if (reconnectController != null) {
            reconnectController.stop();
            reconnectController = null;
        }

        CameraManager.close();

        activeBackend = null;
        activeWebcam = null;

        cameraView.clear();

        DebugLog.info(
                "MainScreen shutdown complete."
        );
    }

    @Override
    public void removeNotify() {
        shutdown();
        super.removeNotify();
    }

    private void refreshPreview() {
        PhotoSession session =
                AppContext.session();

        preview.setFrameDefinition(
                selectedFrameDefinition
        );

        preview.setPhotos(
                session.getPhotos(),
                session.getSlotCount()
        );

        counter.setText(
                session.getCapturedCount()
                        + " / "
                        + session.getSlotCount()
        );
    }

    private void showCameraError(
            String message
    ) {
        status.setText(
                "CAMERA ERROR"
        );

        cameraView.clear();

        JLabel error =
                new JLabel(
                        "<html><center>"
                                + message
                                + "</center></html>",
                        SwingConstants.CENTER
                );

        error.setForeground(
                Color.WHITE
        );

        cameraView.add(
                error,
                BorderLayout.CENTER
        );

        cameraView.revalidate();
        cameraView.repaint();
    }
}
