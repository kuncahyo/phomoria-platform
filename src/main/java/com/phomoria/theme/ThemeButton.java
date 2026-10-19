package com.phomoria.theme;

import javax.swing.JButton;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.nio.file.Files;
import java.nio.file.Path;

/** Theme-aware button. A user PNG fully replaces the code-rendered button label. */
public class ThemeButton extends JButton {
    private final String themeId;
    private Path cachedImagePath;
    private long cachedImageModified = -1L;
    private int cachedWidth = -1;
    private int cachedHeight = -1;

    public ThemeButton(String id, String fallbackText) {
        super(fallbackText);
        themeId = id;
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setHorizontalTextPosition(CENTER);
        setVerticalTextPosition(CENTER);
        setForeground(Color.WHITE);
        setFont(new Font("SansSerif", Font.BOLD, 18));
        reloadImage(id);
    }

    public final void applyTheme(ThemeConfig.Element element) {
        if (element == null) return;
        if (getIcon() == null && element.text != null) setText(element.text);
        if (element.foreground != null) {
            try { setForeground(Color.decode(element.foreground)); }
            catch (RuntimeException ignored) { }
        }
        setVisible(Boolean.TRUE.equals(element.visible));
        reloadImage(themeId);
    }

    public final void reloadImage(String id) {
        ThemeConfig.Element element = ThemeManager.config().elements.get(id);
        Path image = ThemeManager.buttonImage(id);
        if (image != null && Files.isRegularFile(image)) {
            int w = getWidth() > 0 ? getWidth() : (element != null && element.width > 0 ? element.width : 1);
            int h = getHeight() > 0 ? getHeight() : (element != null && element.height > 0 ? element.height : 1);
            try {
                long modified = Files.getLastModifiedTime(image).toMillis();
                if (!image.equals(cachedImagePath) || modified != cachedImageModified
                        || w != cachedWidth || h != cachedHeight || getIcon() == null) {
                    ImageIcon source = new ImageIcon(image.toString());
                    if (source.getIconWidth() <= 0) throw new IllegalStateException("PNG tombol tidak valid");
                    setIcon(new ImageIcon(source.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH)));
                    cachedImagePath = image;
                    cachedImageModified = modified;
                    cachedWidth = w;
                    cachedHeight = h;
                }
                setContentAreaFilled(false);
                setBorderPainted(false);
                // The PNG itself is the complete button design; do not overlay Swing text.
                setText("");
                return;
            } catch (Exception ignored) { }
        }
        setIcon(null);
        cachedImagePath = null;
        cachedImageModified = -1L;
        cachedWidth = -1;
        cachedHeight = -1;
        if (element != null && element.text != null) setText(element.text);
        else setText(fallbackTextFor(id));
        setContentAreaFilled(true);
        setBackground(new Color(35, 35, 42, 220));
        setBorder(javax.swing.BorderFactory.createLineBorder(new Color(255, 255, 255, 110)));
    }

    private boolean visuallyTransparent() {
        ThemeConfig.Element element = ThemeManager.config().elements.get(themeId);
        return element != null && element.transparent;
    }

    @Override protected void paintComponent(Graphics graphics) {
        if (!visuallyTransparent()) super.paintComponent(graphics);
    }

    @Override protected void paintBorder(Graphics graphics) {
        if (!visuallyTransparent()) super.paintBorder(graphics);
    }

    private static String fallbackTextFor(String id) {
        return switch (id) {
            case "start-payment" -> "BAYAR & MULAI FOTO";
            case "start-settings" -> "SETTINGS";
            case "frame-selection-continue" -> "LANJUTKAN";
            case "main-retake" -> "RETAKE FOTO TERPILIH";
            case "main-cancel" -> "PILIH ULANG FRAME";
            default -> id;
        };
    }
}
