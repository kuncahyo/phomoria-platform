package com.phomoria.camera;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.phomoria.debug.DebugLog;
import com.phomoria.frame.FrameDefinition;
import com.phomoria.ui.OverlayPanel;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;

public final class LiveCameraPanel extends JPanel {

    private final JPanel content = new OverlayPanel();
    private final CropGuideOverlay cropGuideOverlay =
            new CropGuideOverlay();

    private int liveGuideShadeAlpha = 105;
    private int reviewGuideShadeAlpha = 185;

    private WebcamPanel webcamPanel;
    private CameraImagePanel backendImagePanel;
    private Webcam webcam;
    private Timer webcamPreviewTimer;
    private CameraBackend backend;

    private FrameDefinition frameDefinition;
    private int guideSlotIndex;

    private Timer backendPreviewTimer;
    private SwingWorker<BufferedImage, Void> backendPreviewWorker;

    private Runnable cameraConnectionLostListener;

    public LiveCameraPanel() {
        super(new BorderLayout());

        setBackground(Color.BLACK);

        content.setBackground(Color.BLACK);
        content.setLayout(new OverlayLayout(content));

        add(content, BorderLayout.CENTER);
    }

    public void setCameraConnectionLostListener(Runnable listener) {
        this.cameraConnectionLostListener = listener;
    }

    public void setCropGuide(
            FrameDefinition definition,
            int sourceSlotIndex
    ) {
        this.frameDefinition = definition;
        this.guideSlotIndex = Math.max(0, sourceSlotIndex);

        if (webcam != null) {
            updateCropGuide(webcam);

        } else if (backendImagePanel != null) {
            updateBackendCropGuide();
        }
    }

    public void hideCropGuide() {
        cropGuideOverlay.hideGuide();
    }

    public void showCropGuide() {
        cropGuideOverlay.setShadeAlpha(liveGuideShadeAlpha);

        if (webcam != null) {
            updateCropGuide(webcam);

        } else if (backendImagePanel != null) {
            updateBackendCropGuide();
        }
    }

    /**
     * Existing webcam path. Kept intact for v21.4.
     */
    public void attach(Webcam webcam) {
        stopBackendPreview();
        stopWebcamPreview();

        backend = null;
        this.webcam = webcam;

        content.removeAll();
        webcamPanel = null;
        backendImagePanel = null;

        cropGuideOverlay.hideGuide();

        if (webcam == null) {
            showMessage("CAMERA TIDAK TERSEDIA");

            DebugLog.warn(
                    "LiveCameraPanel.attach(): webcam is null."
            );

            return;
        }

        DebugLog.info(
                "LiveCameraPanel.attach(): "
                        + webcam.getName()
        );

        backendImagePanel = new CameraImagePanel(
                CameraManager.latestWebcamPreviewFrame()
        );

        cropGuideOverlay.setShadeAlpha(
                liveGuideShadeAlpha
        );

        content.add(cropGuideOverlay);
        content.add(backendImagePanel);
        startWebcamPreview();

        revalidate();
        repaint();

        updateCropGuide(webcam);

        DebugLog.info(
                "Live camera view attached."
        );
    }

    /**
     * gPhoto2 path.
     *
     * Preview capture is performed by a SwingWorker so the EDT remains free.
     * A new request is started only after the previous one finishes.
     */
    public void attach(CameraBackend cameraBackend) {
        stopBackendPreview();
        stopWebcamPreview();

        webcam = null;
        backend = cameraBackend;

        content.removeAll();
        webcamPanel = null;
        backendImagePanel = null;

        cropGuideOverlay.hideGuide();

        if (cameraBackend == null) {
            showMessage("CAMERA TIDAK TERSEDIA");

            DebugLog.warn(
                    "LiveCameraPanel.attach(): backend is null."
            );

            return;
        }

        DebugLog.info(
                "LiveCameraPanel.attach(): backend="
                        + cameraBackend.getDisplayName()
        );

        backendImagePanel = new CameraImagePanel(null);

        content.add(cropGuideOverlay);
        content.add(backendImagePanel);

        revalidate();
        repaint();

        startBackendPreview();

        DebugLog.info(
                "gPhoto2 live preview started."
        );
    }

    /**
     * The webcam preview consumes the same sampled frames that feed the
     * rolling Live Photo buffer. It must not open a second webcam reader.
     */
    private void startWebcamPreview() {
        stopWebcamPreview();
        if (webcam == null || backendImagePanel == null) return;

        webcamPreviewTimer = new Timer(40, e -> {
            if (webcam == null || backendImagePanel == null) return;
            BufferedImage image = CameraManager.latestWebcamPreviewFrame();
            if (image == null) return;
            backendImagePanel.setImage(image);
            updateBackendCropGuide(image);
        });
        webcamPreviewTimer.setCoalesce(true);
        webcamPreviewTimer.start();
    }

    private void stopWebcamPreview() {
        if (webcamPreviewTimer != null) {
            webcamPreviewTimer.stop();
            webcamPreviewTimer = null;
        }
    }

    private void startBackendPreview() {
        stopBackendPreview();

        if (backend == null) {
            return;
        }

        backendPreviewTimer =
                new Timer(
                        120,
                        e -> requestBackendPreview()
                );

        backendPreviewTimer.setRepeats(true);
        backendPreviewTimer.setCoalesce(true);

        requestBackendPreview();
        backendPreviewTimer.start();
    }

    private void requestBackendPreview() {
        if (backend == null
                || backendPreviewWorker != null
                || backendImagePanel == null) {
            return;
        }

        CameraBackend requestedBackend = backend;

        backendPreviewWorker =
                new SwingWorker<>() {

                    @Override
                    protected BufferedImage doInBackground()
                            throws Exception {

                        return requestedBackend.getLiveImage();
                    }

                    @Override
                    protected void done() {
                        try {
                            if (backend != requestedBackend) {
                                return;
                            }

                            BufferedImage image = get();

                            if (image == null) {
                                return;
                            }

                            backendImagePanel.setImage(image);

                            updateBackendCropGuide(image);

                        } catch (Exception ex) {
                            DebugLog.warn(
                                    "gPhoto2 preview frame failed: "
                                            + ex.getMessage()
                            );

                            if (backend == requestedBackend
                                    && GPhoto2PersistentCameraBackend
                                    .isConnectionFailure(ex)) {

                                DebugLog.warn(
                                        "gPhoto2 preview connection "
                                                + "lost."
                                );

                                if (cameraConnectionLostListener != null) {
                                    cameraConnectionLostListener.run();
                                }
                            }

                        } finally {
                            backendPreviewWorker = null;
                        }
                    }
                };

        backendPreviewWorker.execute();
    }

    private void stopBackendPreview() {
        if (backendPreviewTimer != null) {
            backendPreviewTimer.stop();
            backendPreviewTimer = null;
        }

        if (backendPreviewWorker != null) {
            backendPreviewWorker.cancel(true);
            backendPreviewWorker = null;
        }
    }

    private void updateCropGuide(Webcam webcam) {
        if (frameDefinition == null || webcam == null) {
            cropGuideOverlay.hideGuide();
            return;
        }

        Dimension viewSize = webcam.getViewSize();

        cropGuideOverlay.setGuide(
                frameDefinition,
                guideSlotIndex,
                viewSize
        );
    }

    private void updateBackendCropGuide() {
        if (backendImagePanel == null
                || backendImagePanel.getImage() == null) {
            return;
        }

        updateBackendCropGuide(
                backendImagePanel.getImage()
        );
    }

    private void updateBackendCropGuide(
            BufferedImage image
    ) {
        if (frameDefinition == null || image == null) {
            cropGuideOverlay.hideGuide();
            return;
        }

        cropGuideOverlay.setGuide(
                frameDefinition,
                guideSlotIndex,
                new Dimension(
                        image.getWidth(),
                        image.getHeight()
                )
        );
    }

    public void showCapturedImage(BufferedImage image) {
        stopBackendPreview();
        stopWebcamPreview();
        webcam = null;

        content.removeAll();

        webcamPanel = null;
        backendImagePanel = null;
        backend = null;

        cropGuideOverlay.hideGuide();

        if (image == null) {
            showMessage("NO IMAGE");

            DebugLog.warn(
                    "LiveCameraPanel.showCapturedImage(): "
                            + "image is null."
            );

            return;
        }

        CameraImagePanel imagePanel =
                new CameraImagePanel(image);

        cropGuideOverlay.setShadeAlpha(
                reviewGuideShadeAlpha
        );

        cropGuideOverlay.setGuide(
                frameDefinition,
                guideSlotIndex,
                new Dimension(
                        image.getWidth(),
                        image.getHeight()
                )
        );

        content.add(cropGuideOverlay);
        content.add(imagePanel);

        revalidate();
        repaint();

        DebugLog.info(
                "Captured image displayed: "
                        + image.getWidth()
                        + "x"
                        + image.getHeight()
        );
    }

    private void showMessage(String message) {
        content.removeAll();

        JLabel label = new JLabel();

        label.setHorizontalAlignment(
                SwingConstants.CENTER
        );

        label.setVerticalAlignment(
                SwingConstants.CENTER
        );

        label.setText(message);
        label.setForeground(Color.WHITE);
        label.setBackground(Color.BLACK);
        label.setOpaque(true);

        label.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        24
                )
        );

        content.add(label);

        revalidate();
        repaint();
    }

    public void clear() {
        stopBackendPreview();
        stopWebcamPreview();
        webcam = null;

        content.removeAll();

        webcamPanel = null;
        backendImagePanel = null;
        backend = null;

        cropGuideOverlay.hideGuide();

        revalidate();
        repaint();

        DebugLog.info(
                "LiveCameraPanel cleared."
        );
    }
}
