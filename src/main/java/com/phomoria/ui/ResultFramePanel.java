package com.phomoria.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class ResultFramePanel extends JPanel {

    private BufferedImage image;

    public ResultFramePanel() {
        setBackground(new Color(12, 12, 15));
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    public BufferedImage getImage() {
        return image;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (image == null) {
            g.setColor(new Color(180, 180, 190));
            g.setFont(new Font("SansSerif", Font.BOLD, 20));

            String text = "HASIL FOTO";
            FontMetrics fm = g.getFontMetrics();

            g.drawString(
                    text,
                    (getWidth() - fm.stringWidth(text)) / 2,
                    getHeight() / 2
            );

            return;
        }

        int availableW = Math.max(1, getWidth() - 30);
        int availableH = Math.max(1, getHeight() - 30);

        double scale = Math.min(
                availableW / (double) image.getWidth(),
                availableH / (double) image.getHeight()
        );

        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));

        int x = (getWidth() - w) / 2;
        int y = (getHeight() - h) / 2;

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
        );

        g2.drawImage(image, x, y, w, h, null);
        g2.dispose();
    }
}
