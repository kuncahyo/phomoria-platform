package com.phomoria.ui;

import javax.swing.*;
import java.awt.*;

public final class CountdownOverlay extends JComponent {
    private int number = 0;
    private float alpha = 0f;
    private Timer animationTimer;

    public CountdownOverlay() {
        setOpaque(false);
        setVisible(false);
    }

    public void showNumber(int number) {
        this.number = number;
        this.alpha = 1f;
        setVisible(true);

        if (animationTimer != null) {
            animationTimer.stop();
        }

        animationTimer = new Timer(30, e -> {
            alpha -= 0.035f;

            if (alpha <= 0f) {
                alpha = 0f;
                setVisible(false);
                animationTimer.stop();
            }

            repaint();
        });

        animationTimer.start();
        repaint();
    }

    public void hideOverlay() {
        if (animationTimer != null) {
            animationTimer.stop();
        }

        setVisible(false);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!isVisible() || number <= 0 || alpha <= 0f) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        g2.setComposite(
                AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER,
                        Math.min(1f, alpha)
                )
        );

        int size = Math.min(getWidth(), getHeight()) / 4;
        size = Math.max(100, Math.min(size, 300));

        Font font = new Font(
                "SansSerif",
                Font.BOLD,
                size
        );

        g2.setFont(font);

        String text = String.valueOf(number);
        FontMetrics fm = g2.getFontMetrics();

        int x = (getWidth() - fm.stringWidth(text)) / 2;
        int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();

        // translucent circle
        int diameter = size + 80;
        int circleX = (getWidth() - diameter) / 2;
        int circleY = (getHeight() - diameter) / 2;

        g2.setColor(new Color(0, 0, 0, 145));
        g2.fillOval(circleX, circleY, diameter, diameter);

        g2.setColor(Color.WHITE);
        g2.drawString(text, x, y);

        g2.dispose();
    }
}
