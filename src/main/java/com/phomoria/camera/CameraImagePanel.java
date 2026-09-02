package com.phomoria.camera;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Displays a captured photo using the same crop-to-fill presentation style as live view. */
public final class CameraImagePanel extends JPanel {

    private BufferedImage image;

    public CameraImagePanel(BufferedImage image) {
        this.image = image;
        setBackground(Color.BLACK);
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null || getWidth() <= 0 || getHeight() <= 0) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        double scale = Math.max(
                getWidth() / (double) image.getWidth(),
                getHeight() / (double) image.getHeight()
        );

        int dw = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int dh = Math.max(1, (int) Math.round(image.getHeight() * scale));
        int x = (getWidth() - dw) / 2;
        int y = (getHeight() - dh) / 2;

        g2.drawImage(image, x, y, dw, dh, null);
        g2.dispose();
    }
}
