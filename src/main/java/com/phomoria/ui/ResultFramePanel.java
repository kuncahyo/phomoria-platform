package com.phomoria.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public final class ResultFramePanel extends JPanel {
    private BufferedImage image;
    private List<BufferedImage> animationFrames = List.of();
    private int animationIndex;
    private long pauseUntilMillis;
    private Timer animationTimer;

    public ResultFramePanel() {
        // The image is drawn over the Result screen theme, not over a solid black panel.
        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));
    }

    public void setImage(BufferedImage image) {
        stopAnimation();
        this.image = image;
        repaint();
    }

    public BufferedImage getImage() { return image; }

    public void setAnimationFrames(List<BufferedImage> frames) {
        stopAnimation();
        animationFrames = frames == null ? List.of() : List.copyOf(frames);
        animationIndex = 0;
        pauseUntilMillis = 0L;
        if (!animationFrames.isEmpty()) {
            animationTimer = new Timer(66, e -> advanceAnimation());
            animationTimer.setCoalesce(true);
            animationTimer.start();
        }
        repaint();
    }

    public void stopAnimation() {
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
        animationFrames = List.of();
        animationIndex = 0;
        pauseUntilMillis = 0L;
        repaint();
    }

    private void advanceAnimation() {
        if (animationFrames.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (pauseUntilMillis > 0L) {
            if (now < pauseUntilMillis) return;
            pauseUntilMillis = 0L;
            animationIndex = 0;
        } else if (animationIndex + 1 < animationFrames.size()) {
            animationIndex++;
        } else {
            pauseUntilMillis = now + 2000L;
        }
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        BufferedImage current = !animationFrames.isEmpty() ? animationFrames.get(animationIndex) : image;
        if (current == null) {
            g.setColor(new Color(180, 180, 190));
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            String text = "HASIL FOTO";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, getHeight() / 2);
            return;
        }
        int availableW = Math.max(1, getWidth() - 30);
        int availableH = Math.max(1, getHeight() - 30);
        double scale = Math.min(availableW / (double) current.getWidth(), availableH / (double) current.getHeight());
        int w = Math.max(1, (int) Math.round(current.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(current.getHeight() * scale));
        int x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.drawImage(current, x, y, w, h, null);
        g2.dispose();
    }
}
