package com.phomoria.ui;

import com.phomoria.theme.ThemeConfig;
import com.phomoria.theme.ThemeManager;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

/** Transparent, theme-driven Live Photo loading overlay using a user-supplied animated GIF. */
public final class LivePhotoLoadingPanel extends JPanel {
    private final JLabel animation = new JLabel();
    private final JLabel caption = new JLabel("", SwingConstants.CENTER);
    private String textPosition = "BOTTOM";

    public LivePhotoLoadingPanel() {
        setOpaque(false);
        setLayout(null);
        setVisible(false);
        animation.setOpaque(false);
        caption.setOpaque(false);
        add(animation);
        add(caption);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { layoutContent(); }
        });
    }

    public void setActive(boolean active) {
        if (active) refreshTheme();
        setVisible(active);
        if (active) {
            revalidate();
            layoutContent();
        }
        repaint();
    }

    private void refreshTheme() {
        ThemeConfig.Element settings = ThemeManager.config().elements.get("result-live-photo-loading");
        if (settings == null) return;

        String text = settings.text == null ? "" : settings.text;
        caption.setText(text);
        textPosition = settings.loadingTextPosition == null ? "BOTTOM" : settings.loadingTextPosition.toUpperCase();
        caption.setVisible(!text.isBlank() && !"NONE".equals(textPosition));
        try {
            caption.setForeground(settings.foreground == null ? Color.WHITE : Color.decode(settings.foreground));
        } catch (RuntimeException ex) {
            caption.setForeground(Color.WHITE);
        }
        caption.setFont(new Font("SansSerif", Font.BOLD, 18));

        Path gif = ThemeManager.livePhotoLoadingImage();
        if (gif != null) {
            ImageIcon icon = new ImageIcon(gif.toAbsolutePath().toString());
            animation.setIcon(icon);
            animation.setVisible(icon.getIconWidth() > 0 && icon.getIconHeight() > 0);
        } else {
            animation.setIcon(null);
            animation.setVisible(false);
        }
        layoutContent();
    }

    private void layoutContent() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        Dimension image = animation.isVisible() && animation.getIcon() != null
                ? new Dimension(animation.getIcon().getIconWidth(), animation.getIcon().getIconHeight())
                : new Dimension(0, 0);
        Dimension text = caption.isVisible() ? caption.getPreferredSize() : new Dimension(0, 0);
        int gap = image.width > 0 && image.height > 0 && text.width > 0 && text.height > 0 ? 18 : 0;
        boolean horizontal = "LEFT".equals(textPosition) || "RIGHT".equals(textPosition);
        int totalW = horizontal ? image.width + text.width + gap : Math.max(image.width, text.width);
        int totalH = horizontal ? Math.max(image.height, text.height) : image.height + text.height + gap;
        int left = (getWidth() - totalW) / 2;
        int top = (getHeight() - totalH) / 2;

        if (!animation.isVisible()) animation.setBounds(0, 0, 0, 0);
        else animation.setBounds(left, top + (totalH - image.height) / 2, image.width, image.height);

        if (!caption.isVisible()) caption.setBounds(0, 0, 0, 0);
        else if ("LEFT".equals(textPosition)) {
            caption.setBounds(left, top + (totalH - text.height) / 2, text.width, text.height);
            if (animation.isVisible()) animation.setLocation(left + text.width + gap, top + (totalH - image.height) / 2);
        } else if ("RIGHT".equals(textPosition)) {
            caption.setBounds(left + image.width + gap, top + (totalH - text.height) / 2, text.width, text.height);
        } else if ("TOP".equals(textPosition)) {
            caption.setBounds(left + (totalW - text.width) / 2, top, text.width, text.height);
            if (animation.isVisible()) animation.setLocation(left + (totalW - image.width) / 2, top + text.height + gap);
        } else {
            caption.setBounds(left + (totalW - text.width) / 2, top + image.height + gap, text.width, text.height);
        }
    }
}
