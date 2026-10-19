package com.phomoria.theme;

import javax.swing.JButton;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.nio.file.Files;
import java.nio.file.Path;

/** PNG-backed Swing button. Transparency affects painting only, never hit testing. */
public class ThemeButton extends JButton {
    private final String themeId;
    public ThemeButton(String id, String fallbackText) {
        super(fallbackText); themeId = id;
        setFocusPainted(false); setBorderPainted(false); setContentAreaFilled(false); setOpaque(false);
        setForeground(Color.WHITE); setFont(new Font("SansSerif", Font.BOLD, 18)); reloadImage(id);
    }
    public final void reloadImage(String id) {
        Path image = ThemeManager.buttonImage(id);
        if (image != null && Files.isRegularFile(image)) {
            ImageIcon source = new ImageIcon(image.toString());
            if (source.getIconWidth() > 0) {
                ThemeConfig.Element e = ThemeManager.config().elements.get(id);
                int w = e != null && e.width > 0 ? e.width : source.getIconWidth();
                int h = e != null && e.height > 0 ? e.height : source.getIconHeight();
                setIcon(new ImageIcon(source.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH)));
                setText(""); setToolTipText(fallbackTextFor(id)); setContentAreaFilled(false); return;
            }
        }
        setIcon(null); setText(fallbackTextFor(id)); setContentAreaFilled(true);
        setBackground(new Color(35,35,42,220));
        setBorder(javax.swing.BorderFactory.createLineBorder(new Color(255,255,255,110)));
    }
    private boolean visuallyTransparent() {
        ThemeConfig.Element e = ThemeManager.config().elements.get(themeId);
        return e != null && e.transparent;
    }
    @Override protected void paintComponent(Graphics g) { if (!visuallyTransparent()) super.paintComponent(g); }
    @Override protected void paintBorder(Graphics g) { if (!visuallyTransparent()) super.paintBorder(g); }
    private static String fallbackTextFor(String id) {
        return switch (id) {
            case "start-payment" -> "BAYAR & MULAI FOTO";
            case "start-settings" -> "SETTINGS";
            case "frame-selection-continue" -> "LANJUTKAN";
            default -> id;
        };
    }
}
