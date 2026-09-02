package com.phomoria.camera;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.phomoria.debug.DebugLog;
import com.phomoria.frame.FrameDefinition;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;
import com.phomoria.ui.OverlayPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;

public final class LiveCameraPanel extends JPanel {

    private final JPanel content = new OverlayPanel();
    private final CropGuideOverlay cropGuideOverlay = new CropGuideOverlay();
    private int liveGuideShadeAlpha = 105;
    private int reviewGuideShadeAlpha = 185;
    private WebcamPanel webcamPanel;

    private FrameDefinition frameDefinition;
    private int guideSlotIndex;

    public LiveCameraPanel() {
        super(new BorderLayout());
        setBackground(Color.BLACK);
        content.setBackground(Color.BLACK);
        content.setLayout(new OverlayLayout(content));
        add(content, BorderLayout.CENTER);
    }

    public void setCropGuide(FrameDefinition definition, int sourceSlotIndex) {
        this.frameDefinition = definition;
        this.guideSlotIndex = Math.max(0, sourceSlotIndex);
        if (webcamPanel != null && webcamPanel.getWebcam() != null) {
            updateCropGuide(webcamPanel.getWebcam());
        }
    }

    public void hideCropGuide() {
        cropGuideOverlay.hideGuide();
    }

    public void showCropGuide() {
        cropGuideOverlay.setShadeAlpha(liveGuideShadeAlpha);
        if (webcamPanel != null && webcamPanel.getWebcam() != null) {
            updateCropGuide(webcamPanel.getWebcam());
        }
    }

    public void attach(Webcam webcam) {
        content.removeAll();
        webcamPanel = null;
        cropGuideOverlay.hideGuide();

        if (webcam == null) {
            showMessage("CAMERA TIDAK TERSEDIA");
            DebugLog.warn("LiveCameraPanel.attach(): webcam is null.");
            return;
        }

        DebugLog.info("LiveCameraPanel.attach(): " + webcam.getName());

        webcamPanel = new WebcamPanel(webcam);
        cropGuideOverlay.setShadeAlpha(liveGuideShadeAlpha);
        webcamPanel.setMirrored(true);
        webcamPanel.setFillArea(true);
        // The library defaults to an effectively unlimited repaint loop (1 ms delay).
        // That floods Swing when transparent overlays are stacked over the camera.
        // Limit rendering to a smooth, stable 25 FPS.
        webcamPanel.setFPSLimit(25.0);
        webcamPanel.setFPSLimited(true);
        webcamPanel.setBackground(Color.BLACK);

        // OverlayLayout paints the first component on top. Keep the guide
        // above the WebcamPanel while MainScreen's countdown remains above
        // this entire LiveCameraPanel.
        content.add(cropGuideOverlay);
        content.add(webcamPanel);

        revalidate();
        repaint();
        updateCropGuide(webcam);

        DebugLog.info("Live camera view attached.");
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

    public void showCapturedImage(BufferedImage image) {
        content.removeAll();
        cropGuideOverlay.hideGuide();

        if (image == null) {
            showMessage("NO IMAGE");
            DebugLog.warn("LiveCameraPanel.showCapturedImage(): image is null.");
            return;
        }

        CameraImagePanel imagePanel = new CameraImagePanel(image);
        cropGuideOverlay.setShadeAlpha(reviewGuideShadeAlpha);
        cropGuideOverlay.setGuide(
                frameDefinition,
                guideSlotIndex,
                new Dimension(image.getWidth(), image.getHeight())
        );
        content.add(cropGuideOverlay);
        content.add(imagePanel);
        revalidate();
        repaint();

        DebugLog.info(
                "Captured image displayed: "
                        + image.getWidth() + "x" + image.getHeight()
        );
    }

    private void showMessage(String message) {
        content.removeAll();
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setText(message);
        label.setForeground(Color.WHITE);
        label.setBackground(Color.BLACK);
        label.setOpaque(true);
        label.setFont(new Font("SansSerif", Font.BOLD, 24));
        content.add(label);
        revalidate();
        repaint();
    }

    public void clear() {
        content.removeAll();
        webcamPanel = null;
        cropGuideOverlay.hideGuide();
        revalidate();
        repaint();
        DebugLog.info("LiveCameraPanel cleared.");
    }
}
