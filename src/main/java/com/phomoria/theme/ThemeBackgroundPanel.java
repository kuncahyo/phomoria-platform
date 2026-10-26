package com.phomoria.theme;

import javax.swing.JPanel;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Screen background. A custom PNG/GIF overrides the factory background;
 * when no custom asset is selected, the original code-defined dark surface
 * and its screen overlay remain active.
 */
public class ThemeBackgroundPanel extends JPanel {
    private Image image;
    private float overlayAlpha;

    public ThemeBackgroundPanel(String screenId, float overlayAlpha) {
        this.overlayAlpha = Math.max(0f, Math.min(1f, overlayAlpha));
        Path path = ThemeManager.background(screenId);
        if (path != null && Files.isRegularFile(path)) {
            image = new ImageIcon(path.toString()).getImage();
        }
        setOpaque(true);
        setBackground(new Color(18, 18, 22));
    }

    public void setImage(Path path) {
        image = path != null && Files.isRegularFile(path)
                ? new ImageIcon(path.toString()).getImage() : null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            if (image != null) {
                int iw = image.getWidth(this);
                int ih = image.getHeight(this);
                if (iw > 0 && ih > 0) {
                    double scale = Math.max((double) getWidth() / iw,
                            (double) getHeight() / ih);
                    int w = (int) Math.ceil(iw * scale);
                    int h = (int) Math.ceil(ih * scale);
                    g.drawImage(image, (getWidth() - w) / 2,
                            (getHeight() - h) / 2, w, h, this);
                }
            }
            // Keep the original code-defined overlay even when no custom image
            // is selected; this is the factory/default appearance.
            if (overlayAlpha > 0f) {
                g.setColor(new Color(0, 0, 0,
                        Math.round(overlayAlpha * 255)));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        } finally {
            g.dispose();
        }
    }
}
