package com.phomoria.camera;

import com.phomoria.frame.FrameDefinition;
import com.phomoria.frame.FramePlacement;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * Shows the exact crop guide for the selected frame slot.
 *
 * The guide is calculated from the actual FramePlacement hole size in pixels
 * (normalized placement x/y/width/height multiplied by the FrameDefinition
 * width/height). This is intentionally the same slot geometry used by
 * FrameRenderer when it crop-fills the captured photo.
 */
public final class CropGuideOverlay extends JComponent {

    private FrameDefinition frameDefinition;
    private int sourceSlotIndex;
    private double sourceAspect = 4.0 / 3.0;
    private int shadeAlpha = 105;
    private boolean guideVisible;

    public CropGuideOverlay() {
        setOpaque(false);
        setDoubleBuffered(false);
        setVisible(false);
    }

    public void setGuide(FrameDefinition definition, int sourceSlotIndex, Dimension sourceSize) {
        this.frameDefinition = definition;
        this.sourceSlotIndex = Math.max(0, sourceSlotIndex);
        if (sourceSize != null && sourceSize.width > 0 && sourceSize.height > 0) {
            sourceAspect = sourceSize.width / (double) sourceSize.height;
        }
        guideVisible = definition != null && findPlacement() != null;
        setVisible(guideVisible);
        repaint();
    }

    public void setShadeAlpha(int alpha) {
        shadeAlpha = Math.max(0, Math.min(255, alpha));
        repaint();
    }

    public void hideGuide() {
        guideVisible = false;
        setVisible(false);
        repaint();
    }

    public void showGuide() {
        guideVisible = frameDefinition != null && findPlacement() != null;
        setVisible(guideVisible);
        repaint();
    }

    private FramePlacement findPlacement() {
        if (frameDefinition == null) return null;
        for (FramePlacement placement : frameDefinition.getPlacements()) {
            if (placement.sourceSlotIndex() == sourceSlotIndex) {
                return placement;
            }
        }
        return null;
    }

    /**
     * Actual physical aspect ratio of the selected hole in the rendered frame.
     */
    private double targetAspect(FramePlacement placement) {
        double frameWidth = Math.max(1, frameDefinition.getWidth());
        double frameHeight = Math.max(1, frameDefinition.getHeight());
        double holeWidth = placement.width() * frameWidth;
        double holeHeight = placement.height() * frameHeight;
        if (holeWidth <= 0 || holeHeight <= 0) return 1.0;
        return holeWidth / holeHeight;
    }

    /**
     * Maps the final crop region of the original camera image onto the
     * currently visible camera panel. This mirrors crop-to-fill rather than
     * merely comparing the panel aspect ratio with the normalized placement.
     */
    private Rectangle cropBounds() {
        FramePlacement placement = findPlacement();
        if (placement == null || getWidth() <= 0 || getHeight() <= 0) {
            return new Rectangle();
        }

        double target = targetAspect(placement);
        double panelAspect = getWidth() / (double) getHeight();

        // Image area actually visible inside a fill-area camera panel.
        double scale = Math.max(
                getWidth() / sourceAspect,
                getHeight()
        );
        double imageWidth = sourceAspect * scale;
        double imageHeight = scale;
        double imageX = (getWidth() - imageWidth) / 2.0;
        double imageY = (getHeight() - imageHeight) / 2.0;

        double sourceVisibleLeft = Math.max(0.0, -imageX / scale);
        double sourceVisibleTop = Math.max(0.0, -imageY / scale);
        double sourceVisibleWidth = Math.min(1.0, getWidth() / scale);
        double sourceVisibleHeight = Math.min(1.0, getHeight() / scale);

        double safeLeft;
        double safeTop;
        double safeWidth;
        double safeHeight;

        // FrameRenderer crop-to-fill: choose the largest centered source
        // rectangle having exactly the target aspect ratio.
        if (target <= sourceAspect) {
            safeHeight = 1.0;
            safeWidth = target / sourceAspect;
            safeLeft = (1.0 - safeWidth) / 2.0;
            safeTop = 0.0;
        } else {
            safeWidth = 1.0;
            safeHeight = sourceAspect / target;
            safeLeft = 0.0;
            safeTop = (1.0 - safeHeight) / 2.0;
        }

        // Convert source coordinates into the same screen coordinates used
        // by the camera image, then intersect with the actual panel bounds.
        int x1 = (int) Math.round(imageX + safeLeft * imageWidth);
        int y1 = (int) Math.round(imageY + safeTop * imageHeight);
        int x2 = (int) Math.round(imageX + (safeLeft + safeWidth) * imageWidth);
        int y2 = (int) Math.round(imageY + (safeTop + safeHeight) * imageHeight);

        x1 = Math.max(0, Math.min(getWidth(), x1));
        y1 = Math.max(0, Math.min(getHeight(), y1));
        x2 = Math.max(0, Math.min(getWidth(), x2));
        y2 = Math.max(0, Math.min(getHeight(), y2));

        return new Rectangle(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1));
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!guideVisible) return;

        FramePlacement placement = findPlacement();
        if (placement == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle safe = cropBounds();
        if (safe.width <= 0 || safe.height <= 0) {
            g2.dispose();
            return;
        }

        // Only darken the part that will not survive the final frame crop.
        g2.setColor(new Color(0, 0, 0, shadeAlpha));
        if (safe.x > 0) {
            g2.fillRect(0, 0, safe.x, getHeight());
        }
        int right = safe.x + safe.width;
        if (right < getWidth()) {
            g2.fillRect(right, 0, getWidth() - right, getHeight());
        }
        if (safe.y > 0) {
            g2.fillRect(0, 0, getWidth(), safe.y);
        }
        int bottom = safe.y + safe.height;
        if (bottom < getHeight()) {
            g2.fillRect(0, bottom, getWidth(), getHeight() - bottom);
        }

        // The border is deliberately stronger than the shade so the user can
        // immediately understand the exact safe photo area.
        g2.setColor(new Color(255, 255, 255, 230));
        g2.setStroke(new BasicStroke(3f));
        g2.drawRect(
                safe.x + 1,
                safe.y + 1,
                Math.max(1, safe.width - 2),
                Math.max(1, safe.height - 2)
        );

        int tick = Math.min(28, Math.max(12, Math.min(safe.width, safe.height) / 12));
        g2.setColor(new Color(255, 255, 255, 210));
        g2.setStroke(new BasicStroke(1f));
        drawCorner(g2, safe.x, safe.y, tick, 1, 1);
        drawCorner(g2, safe.x + safe.width, safe.y, tick, -1, 1);
        drawCorner(g2, safe.x, safe.y + safe.height, tick, 1, -1);
        drawCorner(g2, safe.x + safe.width, safe.y + safe.height, tick, -1, -1);

        g2.dispose();
    }

    private void drawCorner(Graphics2D g2, int x, int y, int length, int dx, int dy) {
        g2.drawLine(x, y, x + dx * length, y);
        g2.drawLine(x, y, x, y + dy * length);
    }
}
