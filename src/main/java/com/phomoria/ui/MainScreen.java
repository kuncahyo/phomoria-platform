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
    private final JPanel cameraLayer = new OverlayPanel();
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
                FrameCatalog.find(AppContext.settings().getSelectedFrameId());
        if (selectedPreset == null) {
            selectedPreset = FrameCatalog.find("standard_vertical");
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
                String configuredName = AppContext.settings().getCameraName();
                DebugLog.info("Camera configured in Settings=" + configuredName);
                if (configuredName == null || configuredName.isBlank()) {
                    List<Webcam> cameras = CameraManager.list();
                    DebugLog.info("Camera count detected=" + cameras.size());
                    if (cameras.isEmpty()) {
                        showCameraError("Camera tidak ditemukan.");
                        return;
                    }
                    activeWebcam = cameras.get(0);
                    DebugLog.warn("No camera saved in Settings. Using first detected camera: " + activeWebcam.getName());
                    CameraManager.open(activeWebcam);
                } else {
                    activeWebcam = CameraManager.openConfigured(configuredName);
                    if (activeWebcam == null) {
                        showCameraError("Kamera yang dipilih tidak tersedia: " + configuredName);
                        return;
                    }
                }
                cameraView.setCropGuide(selectedFrameDefinition, 0);
                cameraView.attach(activeWebcam);
                cameraView.showCropGuide();
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
        // The current slot determines the crop guide shown over live view.
        cameraView.setCropGuide(
                selectedFrameDefinition,
                session.getCapturedCount()
        );
        showLiveCamera();
        captureController.startNewPhoto();
    }

    private void startRetake() {
        if (!active || shuttingDown) {
            DebugLog.warn("startRetake ignored because MainScreen is inactive.");
            return;
        }
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
        cameraView.setCropGuide(selectedFrameDefinition, retakeIndex);
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
            status.setText("RETAKE FOTO " + (targetIndex + 1) + "  •  " + seconds);
        } else {
            status.setText("FOTO " + (AppContext.session().getCapturedCount() + 1) + "  •  " + seconds);
        }
    }

    @Override
    public void onCaptured(BufferedImage image, boolean retake, int targetIndex) {
        if (!active || shuttingDown) {
            DebugLog.warn("onCaptured ignored because MainScreen is inactive.");
            return;
        }
        countdownOverlay.hideOverlay();
        cameraView.hideCropGuide();
        PhotoSession session = AppContext.session();
        BufferedImage storedImage =
                CameraMirrorProcessor.process(
                        image,
                        AppContext.settings().isMirrorPhoto()
                );
        DebugLog.info("Capture orientation applied: mirror=" + AppContext.settings().isMirrorPhoto());
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
            DebugLog.info("Photo added. count=" + session.getCapturedCount() + "/" + session.getSlotCount());
        }
        refreshPreview();
        showCapturedPhoto(storedImage);
    }

    @Override
    public void onCaptureError(Exception error) {
        if (!active || shuttingDown) return;
        countdownOverlay.hideOverlay();
        cameraView.hideCropGuide();
        status.setText("GAGAL MENGAMBIL FOTO — MENCOBA LAGI");
        DebugLog.error("Capture error. Retrying.", error);
        Timer retry = new Timer(1000, e -> {
            ((Timer) e.getSource()).stop();
            showLiveCamera();
            if (retakeMode) {
                cameraView.setCropGuide(selectedFrameDefinition, selectedIndex);
                captureController.startRetake(selectedIndex);
            } else {
                cameraView.setCropGuide(selectedFrameDefinition, AppContext.session().getCapturedCount());
                captureController.startNewPhoto();
            }
        });
        retry.setRepeats(false);
        retry.start();
    }

    private void showCapturedPhoto(BufferedImage image) {
        countdownOverlay.hideOverlay();
        cameraView.showCapturedImage(image);
        freezeSeconds = 5;
        status.setText("FOTO TERSIMPAN  •  LIVE KAMERA KEMBALI " + freezeSeconds + " DETIK");
        if (freezeTimer != null) freezeTimer.stop();
        freezeTimer = new Timer(1000, e -> {
            freezeSeconds--;
            if (freezeSeconds <= 0) {
                freezeTimer.stop();
                resumeAfterFreeze();
            } else {
                status.setText("FOTO TERSIMPAN  •  LIVE KAMERA KEMBALI " + freezeSeconds + " DETIK");
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
        DebugLog.info("5-second photo review finished. complete=" + session.isComplete());
        if (session.isComplete()) {
            showResult();
            return;
        }
        // The next call to startNextCapture() will set the exact slot guide.
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
        int guideSlot = retakeMode
                ? Math.max(0, selectedIndex)
                : AppContext.session().getCapturedCount();
        cameraView.setCropGuide(selectedFrameDefinition, guideSlot);
        if (activeWebcam != null && CameraManager.current() == activeWebcam) {
            cameraView.attach(activeWebcam);
            cameraView.showCropGuide();
            DebugLog.info("LIVE CAMERA restored.");
        } else if (CameraManager.current() != null) {
            activeWebcam = CameraManager.current();
            cameraView.attach(activeWebcam);
            cameraView.showCropGuide();
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
        shutdown();
        super.removeNotify();
    }

    private void refreshPreview() {
        PhotoSession session = AppContext.session();
        preview.setFrameDefinition(selectedFrameDefinition);
        preview.setPhotos(session.getPhotos(), session.getSlotCount());
        counter.setText(session.getCapturedCount() + " / " + session.getSlotCount());
    }

    private void showCameraError(String message) {
        status.setText("CAMERA ERROR");
        cameraView.clear();
        JLabel error = new JLabel("<html><center>" + message + "</center></html>", SwingConstants.CENTER);
        error.setForeground(Color.WHITE);
        cameraView.add(error, BorderLayout.CENTER);
        cameraView.revalidate();
        cameraView.repaint();
    }
}
