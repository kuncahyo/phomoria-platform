package com.phomoria.ui;

import com.github.sarxos.webcam.WebcamPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class CameraViewPanel extends JPanel {
    private final JLabel frozenImage = new JLabel("", SwingConstants.CENTER);
    private WebcamPanel webcamPanel;

    public CameraViewPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);
    }

    public void showLive(WebcamPanel panel) {
        webcamPanel = panel;
        removeAll();

        if (webcamPanel != null) {
            add(webcamPanel, BorderLayout.CENTER);
        }

        revalidate();
        repaint();
    }

    public void showCapturedPhoto(BufferedImage image) {
        frozenImage.setIcon(new ImageIcon(scale(image, 1000, 700)));
        frozenImage.setOpaque(true);
        frozenImage.setBackground(Color.BLACK);

        removeAll();
        add(frozenImage, BorderLayout.CENTER);

        revalidate();
        repaint();
    }

    public WebcamPanel getWebcamPanel() {
        return webcamPanel;
    }

    private BufferedImage scale(BufferedImage image, int maxW, int maxH) {
        if (image == null) return null;

        double scale = Math.min(
                1.0,
                Math.min(
                        maxW / (double) image.getWidth(),
                        maxH / (double) image.getHeight()
                )
        );

        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));

        BufferedImage result = new BufferedImage(
                w,
                h,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = result.createGraphics();
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();

        return result;
    }
}
