package com.phomoria.ui;

import com.phomoria.theme.ThemeConfig;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/** Stable countdown overlay; its drawing region and text color are themeable. */
public final class CountdownOverlay extends JComponent {
    private int number;
    private ThemeConfig.Element themeElement;
    private int designWidth = 1920;
    private int designHeight = 1080;

    public CountdownOverlay() {
        setOpaque(false);
        setDoubleBuffered(false);
        setVisible(false);
    }

    public void setThemeElement(ThemeConfig.Element element) {
        themeElement = element;
        if (number > 0) setVisible(element == null || !Boolean.FALSE.equals(element.visible));
        repaint();
    }

    public void setDesignSize(int width, int height) {
        designWidth = Math.max(1, width);
        designHeight = Math.max(1, height);
    }

    public void showNumber(int number) {
        if (number <= 0) { hideOverlay(); return; }
        this.number = number;
        setVisible(themeElement == null || !Boolean.FALSE.equals(themeElement.visible));
        repaint();
    }

    public void hideOverlay() {
        number = 0;
        setVisible(false);
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        if (!isVisible() || number <= 0) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.SrcOver);
            int x = 0, y = 0, w = getWidth(), h = getHeight();
            if (themeElement != null) {
                x = (int) Math.round(themeElement.x * getWidth() / (double) designWidth);
                y = (int) Math.round(themeElement.y * getHeight() / (double) designHeight);
                w = Math.max(80, (int) Math.round(themeElement.width * getWidth() / (double) designWidth));
                h = Math.max(80, (int) Math.round(themeElement.height * getHeight() / (double) designHeight));
            }
            int diameter = Math.max(80, Math.min(Math.min(w, h), 380));
            int circleX = x + (w - diameter) / 2;
            int circleY = y + (h - diameter) / 2;
            int fontSize = Math.max(48, (int) (diameter * 0.58));
            g.setColor(new Color(0, 0, 0, 145));
            g.fillOval(circleX, circleY, diameter, diameter);
            g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            String text = String.valueOf(number);
            FontMetrics fm = g.getFontMetrics();
            int tx = circleX + (diameter - fm.stringWidth(text)) / 2;
            int ty = circleY + (diameter - fm.getHeight()) / 2 + fm.getAscent();
            Color foreground = Color.WHITE;
            if (themeElement != null && themeElement.foreground != null) {
                try { foreground = Color.decode(themeElement.foreground); }
                catch (RuntimeException ignored) { }
            }
            g.setColor(foreground);
            g.drawString(text, tx, ty);
        } finally { g.dispose(); }
    }
}
