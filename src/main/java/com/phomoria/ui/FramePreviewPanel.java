package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.cloud.CloudFrameSupport;
import com.phomoria.cloud.FrameCache;
import com.phomoria.debug.DebugLog;
import com.phomoria.frame.FrameDefinition;
import com.phomoria.frame.FramePlacement;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class FramePreviewPanel extends JPanel {

    public interface SelectionListener { void selected(int index); }

    private List<BufferedImage> photos = List.of();
    private int slotCount = 1;
    private int selectedIndex = -1;
    private SelectionListener selectionListener;

    private FrameDefinition frameDefinition =
            FrameDefinition.defaultVertical(1);

    private BufferedImage cloudFrameImage;
    private boolean cloudFrameActive;

    public FramePreviewPanel() {
        setBackground(new Color(30, 30, 36));
        setPreferredSize(new Dimension(390, 620));

        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                int index = slotAt(e.getX(), e.getY());
                if (index >= 0 && index < photos.size()) {
                    selectedIndex = index;
                    repaint();
                    if (selectionListener != null) {
                        selectionListener.selected(index);
                    }
                }
            }
        });
    }

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    public void setFrameDefinition(FrameDefinition definition) {
        if (definition == null) return;
        this.frameDefinition = definition;
        loadCloudFrame();
        repaint();

        DebugLog.info(
                "Frame preview changed: " + definition.getName() +
                ", cloudAsset=" + cloudFrameActive +
                ", placements=" + definition.getPlacements().size());
    }

    public void setPhotos(List<BufferedImage> photos, int slotCount) {
        this.photos = photos == null ? List.of() : photos;
        this.slotCount = Math.max(1, slotCount);
        if (selectedIndex >= this.photos.size()) selectedIndex = -1;
        repaint();
    }

    public int getSelectedIndex() { return selectedIndex; }

    public void clearSelection() {
        selectedIndex = -1;
        repaint();
    }

    private void loadCloudFrame() {
        cloudFrameImage = null;
        cloudFrameActive = false;

        try {
            Path path = CloudFrameSupport.selectedPng();
            FrameCache.CacheMetadata metadata =
                    CloudFrameSupport.selectedMetadata();

            if (path == null || metadata == null
                    || !"ACTIVE".equalsIgnoreCase(metadata.getStatus())) {
                return;
            }

            BufferedImage image = ImageIO.read(path.toFile());
            if (image != null) {
                cloudFrameImage = image;
                cloudFrameActive = true;
            }
        } catch (IOException ex) {
            DebugLog.warn("Failed to load cached cloud frame for runtime preview: "
                    + ex.getMessage());
        }
    }

    private Rectangle frameBounds() {
        int w = getWidth();
        int h = getHeight();
        int maxW = Math.max(100, w - 40);
        int maxH = Math.max(100, h - 40);

        double aspect = frameDefinition.getWidth()
                / (double) frameDefinition.getHeight();

        int frameW = maxW;
        int frameH = (int) Math.round(frameW / aspect);

        if (frameH > maxH) {
            frameH = maxH;
            frameW = (int) Math.round(frameH * aspect);
        }

        return new Rectangle(
                (w - frameW) / 2, (h - frameH) / 2,
                frameW, frameH);
    }

    private int slotAt(int mx, int my) {
        Rectangle bounds = frameBounds();
        for (FramePlacement placement : frameDefinition.getPlacements()) {
            Rectangle r = placementBounds(placement, bounds);
            if (r.contains(mx, my)) return placement.sourceSlotIndex();
        }
        return -1;
    }

    private Rectangle placementBounds(FramePlacement placement, Rectangle bounds) {
        int x = bounds.x + (int) Math.round(
                placement.x() * bounds.width);
        int y = bounds.y + (int) Math.round(
                placement.y() * bounds.height);
        int w = Math.max(1, (int) Math.round(
                placement.width() * bounds.width));
        int h = Math.max(1, (int) Math.round(
                placement.height() * bounds.height));

        return new Rectangle(x, y, w, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle bounds = frameBounds();

        g2.setColor(Color.WHITE);
        g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        for (FramePlacement placement : frameDefinition.getPlacements()) {
            Rectangle r = placementBounds(placement, bounds);
            int source = placement.sourceSlotIndex();

            if (source == selectedIndex) {
                g2.setColor(new Color(55, 120, 255, 190));
                g2.fillRoundRect(
                        r.x - 5, r.y - 5,
                        r.width + 10, r.height + 10, 12, 12);
            }

            if (source >= 0 && source < photos.size()
                    && photos.get(source) != null) {
                drawPhoto(g2, photos.get(source), r,
                        placement.cropToFill());
            } else {
                g2.setColor(new Color(225, 225, 230));
                g2.fillRoundRect(
                        r.x, r.y, r.width, r.height, 10, 10);
            }
        }

        if (cloudFrameActive && cloudFrameImage != null) {
            g2.drawImage(
                    cloudFrameImage,
                    bounds.x, bounds.y,
                    bounds.width, bounds.height,
                    null);
        } else {
            g2.setColor(new Color(40, 40, 45));
            g2.setStroke(new BasicStroke(4));
            g2.drawRect(
                    bounds.x, bounds.y,
                    bounds.width, bounds.height);
        }

        g2.dispose();
    }

    private void drawPhoto(
            Graphics2D g2,
            BufferedImage img,
            Rectangle r,
            boolean crop) {

        double scale = crop
                ? Math.max(
                        r.width / (double) img.getWidth(),
                        r.height / (double) img.getHeight())
                : Math.min(
                        r.width / (double) img.getWidth(),
                        r.height / (double) img.getHeight());

        int dw = Math.max(1,
                (int) Math.round(img.getWidth() * scale));
        int dh = Math.max(1,
                (int) Math.round(img.getHeight() * scale));
        int ix = r.x + (r.width - dw) / 2;
        int iy = r.y + (r.height - dh) / 2;

        Shape old = g2.getClip();
        g2.clip(new RoundRectangle2D.Float(
                r.x, r.y, r.width, r.height, 10, 10));
        g2.drawImage(img, ix, iy, dw, dh, null);
        g2.setClip(old);
    }
}
