package com.phomoria.ui;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Stable countdown overlay. The countdown number is intentionally static
 * for each one-second step. Fading the number between ticks caused a visible
 * blink/flicker on top of the live camera and also forced unnecessary
 * repaints of the transparent camera overlay.
 */
public final class CountdownOverlay extends JComponent {

    private int number = 0;

    public CountdownOverlay() {
        setOpaque(false);
        setDoubleBuffered(false);
        setVisible(false);
    }

    public void showNumber(int number) {
        if (number <= 0) {
            hideOverlay();
            return;
        }

        this.number = number;
        setVisible(true);
        repaint();
    }

    public void hideOverlay() {
        number = 0;
        setVisible(false);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!isVisible() || number <= 0) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );
        g2.setComposite(AlphaComposite.SrcOver);

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
