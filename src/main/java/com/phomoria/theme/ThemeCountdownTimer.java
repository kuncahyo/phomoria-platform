package com.phomoria.theme;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Image;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.ImageIcon;

/**
 * Themeable page inactivity timer. The countdown number is rendered as text;
 * an optional transparent PNG can be used as its decorative background.
 */
public final class ThemeCountdownTimer extends JPanel {
    private int remainingSeconds;
    private Timer timer;
    private Runnable onExpire;
    private Image background;
    private Color numberColor = new Color(80, 80, 80);

    public ThemeCountdownTimer(String elementId) {
        setOpaque(false);
        applyTheme(elementId);
    }

    public void applyTheme(String elementId) {
        ThemeConfig.Element element = ThemeManager.config().elements.get(elementId);
        if (element != null) {
            try {
                numberColor = element.foreground == null
                        ? new Color(80, 80, 80) : Color.decode(element.foreground);
            } catch (RuntimeException ignored) {
                numberColor = new Color(80, 80, 80);
            }
            Path image = ThemeManager.timerBackground(elementId);
            background = image != null && Files.isRegularFile(image)
                    ? new ImageIcon(image.toString()).getImage() : null;
            setVisible(Boolean.TRUE.equals(element.visible));
        }
        repaint();
    }

    public void start(int seconds, Runnable expiration) {
        stop();
        remainingSeconds = Math.max(1, seconds);
        onExpire = expiration;
        repaint();
        timer = new Timer(1000, event -> {
            remainingSeconds--;
            repaint();
            if (remainingSeconds <= 0) {
                stopClock();
                Runnable action = onExpire;
                onExpire = null;
                if (action != null) action.run();
            }
        });
        timer.setCoalesce(true);
        timer.start();
    }

    public void stop() {
        stopClock();
        onExpire = null;
    }

    private void stopClock() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    public int getRemainingSeconds() { return remainingSeconds; }

    private String displayValue() {
        if (remainingSeconds >= 60) {
            return String.format("%d:%02d", remainingSeconds / 60, remainingSeconds % 60);
        }
        return Integer.toString(Math.max(0, remainingSeconds));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            if (background != null) {
                int iw = background.getWidth(this), ih = background.getHeight(this);
                if (iw > 0 && ih > 0) {
                    double scale = Math.max(getWidth() / (double) iw, getHeight() / (double) ih);
                    int w = (int) Math.ceil(iw * scale), h = (int) Math.ceil(ih * scale);
                    g.drawImage(background, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, this);
                }
            }
            String value = displayValue();
            int heightFit = (int) (getHeight() * 0.52);
            int widthFit = (int) (getWidth() / Math.max(1.0, value.length() * 0.66));
            int fontSize = Math.max(12, Math.min(heightFit, widthFit));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, fontSize));
            FontMetrics fm = g.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(value)) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g.setColor(numberColor);
            g.drawString(value, x, y);
        } finally {
            g.dispose();
        }
    }
}
