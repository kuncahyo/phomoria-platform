package com.phomoria.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

/**
 * Animated, lightweight Live Photo preparation overlay for the Result screen.
 * Animation is independent from the upload queue and stops as soon as playback starts.
 */
public final class LivePhotoLoadingPanel extends JPanel {
    private final Timer animationTimer;
    private float phase;
    private int rotation;

    public LivePhotoLoadingPanel() {
        setOpaque(false);
        setVisible(false);
        animationTimer = new Timer(32, e -> {
            phase += 0.075f;
            if (phase > Math.PI * 2) phase -= (float) (Math.PI * 2);
            rotation = (rotation + 5) % 360;
            repaint();
        });
    }

    public void setActive(boolean active) {
        setVisible(active);
        if (active) {
            if (!animationTimer.isRunning()) animationTimer.start();
        } else {
            animationTimer.stop();
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (!isVisible() || getWidth() <= 0 || getHeight() <= 0) return;

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int panelW = Math.min(310, Math.max(220, w - 20));
        int panelH = Math.min(154, Math.max(126, h - 12));
        int x = (w - panelW) / 2;
        int y = (h - panelH) / 2;

        // Soft shadow and translucent charcoal glass surface.
        g.setColor(new Color(0, 0, 0, 48));
        g.fillRoundRect(x + 3, y + 5, panelW, panelH, 24, 24);
        g.setColor(new Color(24, 25, 32, 224));
        g.fillRoundRect(x, y, panelW, panelH, 24, 24);
        g.setColor(new Color(255, 255, 255, 54));
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(x, y, panelW, panelH, 24, 24);

        int cx = w / 2;
        int cy = y + 43;
        float pulse = 0.5f + 0.5f * (float) Math.sin(phase);
        int glow = 20 + Math.round(pulse * 8);
        g.setColor(new Color(210, 35, 55, 35));
        g.fill(new Ellipse2D.Float(cx - glow, cy - glow, glow * 2, glow * 2));
        g.setColor(new Color(255, 255, 255, 38));
        g.fill(new Ellipse2D.Float(cx - 12, cy - 12, 24, 24));

        g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 55));
        g.drawArc(cx - 18, cy - 18, 36, 36, 0, 360);
        g.setColor(new Color(236, 65, 82));
        g.drawArc(cx - 18, cy - 18, 36, 36, rotation, 245);
        g.setColor(new Color(255, 255, 255, 220));
        g.fill(new Ellipse2D.Float(cx + 11, cy - 17, 7, 7));

        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        String title = "MENYIAPKAN LIVE PHOTO";
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.WHITE);
        g.drawString(title, cx - fm.stringWidth(title) / 2, y + 91);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        String subtitle = "Sebentar lagi animasi akan siap";
        fm = g.getFontMetrics();
        g.setColor(new Color(205, 207, 216));
        g.drawString(subtitle, cx - fm.stringWidth(subtitle) / 2, y + 113);
        g.dispose();
    }
}
