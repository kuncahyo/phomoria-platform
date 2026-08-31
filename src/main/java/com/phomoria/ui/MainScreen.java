package com.phomoria.ui;

import com.github.sarxos.webcam.Webcam;
import com.phomoria.app.AppContext;
import com.phomoria.app.ApplicationFrame;
import com.phomoria.camera.CameraManager;
import com.phomoria.camera.LiveCameraPanel;
import com.phomoria.camera.CameraMirrorProcessor;
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

public final class MainScreen extends JPanel implements CaptureController.Listener {
    private final ApplicationFrame frame;
    private final JLabel status = new JLabel("MENYIAPKAN KAMERA...");
    private final JLabel counter = new JLabel("0 / 0");
    private final LiveCameraPanel cameraView = new LiveCameraPanel();
    private final CountdownOverlay countdownOverlay = new CountdownOverlay();
    private final FramePreviewPanel preview = new FramePreviewPanel();
    private final JPanel cameraLayer = new JPanel();
    private final FrameDefinition selectedFrameDefinition;
    private final CaptureController captureController;

    private Webcam activeWebcam;
    private Timer starterTimer;
    private Timer freezeTimer;
    private int freezeSeconds;
    private int selectedIndex = -1;
    private boolean retakeMode;
    private boolean active = false;
    private boolean shuttingDown = false;

    public MainScreen(ApplicationFrame frame) {
        this.frame = frame;
        this.captureController = new CaptureController(this);

        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(new Color(18, 18, 22));

        cameraLayer.setLayout(new OverlayLayout(cameraLayer));
        cameraLayer.setBackground(Color.BLACK);
        cameraLayer.add(countdownOverlay);
        cameraLayer.add(cameraView);

        add(createTopBar(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                cameraLayer,
                preview
        );
        split.setResizeWeight(0.68);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        add(createBottomBar(), BorderLayout.SOUTH);

        preview.setSelectionListener(index -> {
            selectedIndex = index;
            DebugLog.info("Preview selected photo index=" + index);
            status.setText("FOTO " + (index + 1) + " DIPILIH — TEKAN RETAKE");
        });

        FramePreset selectedPreset =
                FrameCatalog.find(
                        AppContext.settings().getSelectedFrameId()
                );

        if (selectedPreset == null) {
            selectedPreset =
                    FrameCatalog.find("standard_vertical");
        }

        selectedFrameDefinition =
                FrameCatalog.createDefinition(
                        selectedPreset,
                        AppContext.settings().getPhotoSlotCount()
                );

        DebugLog.info(
                "MainScreen frame="
                        + selectedPreset.id()
                        + ", placements="
                        + selectedFrameDefinition.getPlacements().size()
        );

        refreshPreview();
        active = true;
        DebugLog.info("MainScreen activated.");
        startCamera();
    }

    private JPanel createTopBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JLabel title = new JLabel("PHOMORIA  •  PHOTO BOOTH");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));

        status.setForeground(new Color(120, 220, 150));

        panel.add(title, BorderLayout.WEST);
        panel.add(status, BorderLayout.EAST);
        return panel;
    }

    private JPanel createBottomBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 8));
        panel.setOpaque(false);

        JButton retake = new JButton("RETAKE FOTO TERPILIH");
        retake.addActionListener(e -> startRetake());

        panel.add(counter);
        panel.add(retake);
        return panel;
    }

    private void startCamera() {
        if (!active || shuttingDown) {
            DebugLog.warn("startCamera ignored because MainScreen is inactive.");
            return;
        }

        DebugLog.info("Starting camera screen.");

        SwingUtilities.invokeLater(() -> {
            try {
                String configuredName =
                        AppContext.settings().getCameraName();

                DebugLog.info(
                        "Camera configured in Settings=" + configuredName
                );

                if (configuredName == null || configuredName.isBlank()) {
                    List<Webcam> cameras = CameraManager.list();
                    DebugLog.info("Camera count detected=" + cameras.size());

                    if (cameras.isEmpty()) {
                        showCameraError("Camera tidak ditemukan.");
                        return;
                    }

                    activeWebcam = cameras.get(0);
                    DebugLog.warn(
                            "No camera saved in Settings. Using first detected camera: "
                                    + activeWebcam.getName()
                    );
                    CameraManager.open(activeWebcam);
                } else {
                    activeWebcam =
                            CameraManager.openConfigured(configuredName);

                    if (activeWebcam == null) {
                        showCameraError(
                                "Kamera yang dipilih tidak tersedia: "
                                        + configuredName
                        );
                        return;
                    }
                }
                cameraView.attach(activeWebcam);

                status.setText("SIAP — SESSION OTOMATIS DIMULAI");

                starterTimer = new Timer(1200, e -> {
                    starterTimer.stop();
                    startNextCapture();
                });
                starterTimer.setRepeats(false);
                starterTimer.start();

            } catch (Exception ex) {
                DebugLog.error("Camera initialization failed.", ex);
                showCameraError("Camera gagal dibuka: " + ex.getMessage());
            }
        });
    }

    private void startNextCapture() {
        if (!active || shuttingDown) {
            DebugLog.warn("startNextCapture ignored because MainScreen is inactive.");
            return;
        }

        PhotoSession session = AppContext.session();

        if (session.isComplete()) {
            DebugLog.info("Session complete. Showing result.");
            showResult();
            return;
        }

        retakeMode = false;
        selectedIndex = -1;
        preview.clearSelection();

        // Live camera is guaranteed to be visible before countdown starts.
        showLiveCamera();

        captureController.startNewPhoto();
    }

    private void startRetake() {
        if (!active || shuttingDown) {
            DebugLog.warn("startRetake ignored because MainScreen is inactive.");
            return;
        }

        // Cancel EVERY pending operation before entering retake.
        stopAllTimers();
        captureController.stop();

        PhotoSession session = AppContext.session();

        if (selectedIndex < 0 || selectedIndex >= session.getCapturedCount()) {
            status.setText("PILIH FOTO DI PANEL KANAN TERLEBIH DAHULU");
            DebugLog.warn("Retake requested without valid selection. selectedIndex=" + selectedIndex);
            return;
        }

        if (freezeTimer != null) {
            freezeTimer.stop();
            freezeTimer = null;
        }

        retakeMode = true;
        final int retakeIndex = selectedIndex;

        DebugLog.info("Retake requested for photo index=" + retakeIndex);

        // LIVE CAMERA must be visible before the retake countdown.
        showLiveCamera();
        status.setText("LIVE CAMERA — SIAP RETAKE FOTO " + (retakeIndex + 1));

        Timer prepare = new Timer(700, e -> {
            ((Timer) e.getSource()).stop();

            if (!active || shuttingDown) {
                DebugLog.warn("Retake countdown cancelled because MainScreen is inactive.");
                return;
            }

            captureController.startRetake(retakeIndex);
        });
        prepare.setRepeats(false);
        prepare.start();
    }

    @Override
    public void onCountdown(int seconds, boolean retake, int targetIndex) {
        if (!active || shuttingDown) return;

        countdownOverlay.showNumber(seconds);

        if (retake) {
            status.setText(
                    "RETAKE FOTO " + (targetIndex + 1) + "  •  " + seconds
            );
        } else {
            status.setText(
                    "FOTO " + (AppContext.session().getCapturedCount() + 1)
                            + "  •  " + seconds
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
            DebugLog.warn("onCaptured ignored because MainScreen is inactive.");
            return;
        }

        countdownOverlay.hideOverlay();

        PhotoSession session = AppContext.session();

        // The live camera remains visually mirrored for natural posing.
        // The captured/stored photo is mirrored here according to Settings.
        BufferedImage storedImage =
                CameraMirrorProcessor.process(
                        image,
                        AppContext.settings().isMirrorPhoto()
                );

        DebugLog.info(
                "Capture orientation applied: mirror="
                        + AppContext.settings().isMirrorPhoto()
        );

        if (retake) {
            boolean replaced = session.replacePhoto(targetIndex, storedImage);

            if (!replaced) {
                DebugLog.error("Retake failed: invalid targetIndex=" + targetIndex);
                status.setText("RETAKE GAGAL");
                return;
            }

            DebugLog.info("Photo replaced at index=" + targetIndex);
            selectedIndex = -1;
            preview.clearSelection();
            status.setText("FOTO " + (targetIndex + 1) + " DIGANTI");
        } else {
            session.addPhoto(storedImage);
            DebugLog.info(
                    "Photo added. count=" + session.getCapturedCount()
                            + "/" + session.getSlotCount()
            );
        }

        refreshPreview();
        showCapturedPhoto(storedImage);
    }

    @Override
    public void onCaptureError(Exception error) {
        if (!active || shuttingDown) {
            DebugLog.warn("onCaptureError ignored because MainScreen is inactive.");
            return;
        }

        countdownOverlay.hideOverlay();
        status.setText("GAGAL MENGAMBIL FOTO — MENCOBA LAGI");
        DebugLog.error("Capture error. Retrying.", error);

        Timer retry = new Timer(1000, e -> {
            ((Timer) e.getSource()).stop();
            showLiveCamera();

            if (retakeMode) {
                captureController.startRetake(selectedIndex);
            } else {
                captureController.startNewPhoto();
            }
        });
        retry.setRepeats(false);
        retry.start();
    }

    private void showCapturedPhoto(BufferedImage image) {
        countdownOverlay.hideOverlay();
        cameraView.showCapturedImage(scale(image, 900, 650));

        freezeSeconds = 5;
        status.setText(
                "FOTO TERSIMPAN  •  LIVE KAMERA KEMBALI "
                        + freezeSeconds + " DETIK"
        );

        if (freezeTimer != null) freezeTimer.stop();

        freezeTimer = new Timer(1000, e -> {
            freezeSeconds--;

            if (freezeSeconds <= 0) {
                freezeTimer.stop();
                resumeAfterFreeze();
            } else {
                status.setText(
                        "FOTO TERSIMPAN  •  LIVE KAMERA KEMBALI "
                                + freezeSeconds + " DETIK"
                );
            }
        });

        freezeTimer.start();
    }

    private void resumeAfterFreeze() {
        if (!active || shuttingDown) {
            DebugLog.warn("resumeAfterFreeze ignored because MainScreen is inactive.");
            return;
        }

        PhotoSession session = AppContext.session();

        DebugLog.info(
                "5-second photo review finished. complete="
                        + session.isComplete()
        );

        if (session.isComplete()) {
            showResult();
            return;
        }

        showLiveCamera();

        Timer next = new Timer(700, e -> {
            ((Timer) e.getSource()).stop();
            startNextCapture();
        });
        next.setRepeats(false);
        next.start();
    }

    private void showLiveCamera() {
        countdownOverlay.hideOverlay();

        if (activeWebcam != null && CameraManager.current() == activeWebcam) {
            cameraView.attach(activeWebcam);
            DebugLog.info("LIVE CAMERA restored.");
        } else if (CameraManager.current() != null) {
            activeWebcam = CameraManager.current();
            cameraView.attach(activeWebcam);
            DebugLog.info("LIVE CAMERA restored from current camera.");
        } else {
            DebugLog.warn("Cannot restore live camera: no active webcam.");
        }
    }

    private void showResult() {
        shutdown();
        DebugLog.info("Camera closed. Navigating to ResultScreen.");
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
        if (shuttingDown) return;

        shuttingDown = true;
        active = false;

        DebugLog.info("MainScreen shutdown started.");

        captureController.stop();
        stopAllTimers();

        CameraManager.close();

        cameraView.clear();

        DebugLog.info("MainScreen shutdown complete.");
    }

    @Override
    public void removeNotify() {
        // Safety net: if ApplicationFrame removes this screen,
        // no countdown or camera operation may remain alive.
        shutdown();
        super.removeNotify();
    }

    private void refreshPreview() {
        PhotoSession session = AppContext.session();

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

    private void showCameraError(String message) {
        status.setText("CAMERA ERROR");
        cameraView.clear();

        JLabel error = new JLabel(
                "<html><center>" + message + "</center></html>",
                SwingConstants.CENTER
        );
        error.setForeground(Color.WHITE);

        cameraView.add(error, BorderLayout.CENTER);
        cameraView.revalidate();
        cameraView.repaint();
    }

    private BufferedImage scale(
            BufferedImage image,
            int maxW,
            int maxH
    ) {
        double scale = Math.min(
                1.0,
                Math.min(
                        maxW / (double) image.getWidth(),
                        maxH / (double) image.getHeight()
                )
        );

        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));

        BufferedImage out = new BufferedImage(
                w,
                h,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = out.createGraphics();
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();

        return out;
    }
}
