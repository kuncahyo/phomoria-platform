package com.phomoria.theme;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared theme-aware absolute layout support for modal dialogs. */
public final class ThemeDialogSupport {
    private ThemeDialogSupport() {}

    public static JPanel createPanel(String screenId) {
        ThemeConfig.Screen screen = ThemeManager.config().screens.get(screenId);
        int width = screen == null || screen.designWidth <= 0 ? 500 : screen.designWidth;
        int height = screen == null || screen.designHeight <= 0 ? 400 : screen.designHeight;
        JPanel panel = new JPanel(null) {
            private Image backgroundImage;
            private Path loadedPath;

            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                ThemeConfig.Screen current = ThemeManager.config().screens.get(screenId);
                Color base = parseColor(current == null ? null : current.backgroundColor, new Color(245, 245, 247));
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setColor(base);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    Path path = ThemeManager.background(screenId);
                    if (path == null || !Files.isRegularFile(path)) {
                        backgroundImage = null;
                        loadedPath = null;
                    } else if (!path.equals(loadedPath)) {
                        ImageIcon icon = new ImageIcon(path.toString());
                        backgroundImage = icon.getImage();
                        loadedPath = path;
                    }
                    if (backgroundImage != null) {
                        g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
                    }
                } finally {
                    g.dispose();
                }
            }
        };
        panel.setPreferredSize(new Dimension(width, height));
        panel.setBackground(parseColor(screen == null ? null : screen.backgroundColor, new Color(245, 245, 247)));
        panel.setOpaque(true);
        return panel;
    }

    public static void add(JPanel panel, String id, JComponent component, String fallbackText,
                           int x, int y, int width, int height, boolean editableText) {
        ThemeConfig.Element element = ThemeManager.config().elements.computeIfAbsent(id,
                key -> new ThemeConfig.Element(null, x, y, width, height, fallbackText, "#222222", true));
        component.setBounds(element.x, element.y, Math.max(1, element.width), Math.max(1, element.height));
        component.setVisible(Boolean.TRUE.equals(element.visible));

        if (element.foreground != null) component.setForeground(parseColor(element.foreground, component.getForeground()));
        if (element.background != null && !element.background.isBlank()) {
            component.setBackground(parseColor(element.background, component.getBackground()));
            component.setOpaque(true);
        }
        if (element.transparent) component.setOpaque(false);
        if (element.fontSize > 0) {
            Font current = component.getFont();
            component.setFont(current.deriveFont((float) element.fontSize));
        }

        Path imagePath = ThemeManager.buttonImage(id);
        if (component instanceof AbstractButton button) {
            if (imagePath != null && Files.isRegularFile(imagePath)) {
                ImageIcon source = new ImageIcon(imagePath.toString());
                if (source.getIconWidth() > 0) {
                    Image scaled = source.getImage().getScaledInstance(Math.max(1, element.width),
                            Math.max(1, element.height), Image.SCALE_SMOOTH);
                    button.setIcon(new ImageIcon(scaled));
                    button.setText("");
                    button.setContentAreaFilled(false);
                    button.setBorderPainted(false);
                }
            } else {
                button.setIcon(null);
                if (editableText && element.text != null) button.setText(element.text);
                button.setForeground(parseColor(element.foreground, Color.WHITE));
                if (element.background != null) {
                    button.setBackground(parseColor(element.background, button.getBackground()));
                    button.setContentAreaFilled(true);
                    button.setOpaque(true);
                }
            }
            button.setFocusPainted(false);
        } else if (component instanceof JLabel label) {
            if (imagePath != null && Files.isRegularFile(imagePath)) {
                ImageIcon source = new ImageIcon(imagePath.toString());
                if (source.getIconWidth() > 0) {
                    label.setIcon(new ImageIcon(source.getImage().getScaledInstance(
                            Math.max(1, element.width), Math.max(1, element.height), Image.SCALE_SMOOTH)));
                    if (editableText) label.setText("");
                }
            } else if (editableText && element.text != null) {
                label.setText(element.text);
            }
        }
        panel.add(component);
    }

    public static Color screenBackgroundColor(String screenId) {
        ThemeConfig.Screen screen = screenId == null ? null : ThemeManager.config().screens.get(screenId);
        return parseColor(screen == null ? null : screen.backgroundColor, new Color(18, 18, 22));
    }

    private static Color parseColor(String value, Color fallback) {
        try { return value == null || value.isBlank() ? fallback : Color.decode(value); }
        catch (RuntimeException ex) { return fallback == null ? Color.WHITE : fallback; }
    }
}
