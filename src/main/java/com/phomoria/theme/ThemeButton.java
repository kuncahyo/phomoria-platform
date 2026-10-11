package com.phomoria.theme;

import javax.swing.JButton;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.nio.file.Files;
import java.nio.file.Path;

/** PNG-backed button. ActionListeners remain owned by the calling screen. */
public class ThemeButton extends JButton {
    public ThemeButton(String id, String fallbackText) {
        super(fallbackText);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setForeground(Color.WHITE);
        setFont(new Font("SansSerif", Font.BOLD, 18));
        reloadImage(id);
    }

    public final void reloadImage(String id) {
        Path image = ThemeManager.buttonImage(id);
        if (image != null && Files.isRegularFile(image)) {
            ImageIcon source = new ImageIcon(image.toString());
            if (source.getIconWidth() > 0) {
                ThemeConfig.Element element = ThemeManager.config().elements.get(id);
                int targetWidth = element != null && element.width > 0 ? element.width : source.getIconWidth();
                int targetHeight = element != null && element.height > 0 ? element.height : source.getIconHeight();
                setIcon(new ImageIcon(source.getImage().getScaledInstance(
                        targetWidth, targetHeight, Image.SCALE_SMOOTH)));
                setText("");
                setToolTipText(fallbackTextFor(id));
                return;
            }
        }
        setIcon(null);
        setText(fallbackTextFor(id));
        setContentAreaFilled(true);
        setBackground(new Color(35, 35, 42, 220));
        setBorder(javax.swing.BorderFactory.createLineBorder(new Color(255,255,255,110)));
    }

    private static String fallbackTextFor(String id) {
        return switch (id) {
            case "start-payment" -> "BAYAR & MULAI FOTO";
            case "start-settings" -> "SETTINGS";
            default -> id;
        };
    }
}
