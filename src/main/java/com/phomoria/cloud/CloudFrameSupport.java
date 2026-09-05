package com.phomoria.cloud;

import com.phomoria.frame.FrameDefinition;
import com.phomoria.frame.FrameLayoutType;
import com.phomoria.frame.FramePlacement;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CloudFrameSupport {

    private CloudFrameSupport() {}

    public static FrameCache.CacheMetadata selectedMetadata() {
        long id = selectedFrameId();
        return id > 0 ? FrameCache.readMetadata(id) : null;
    }

    public static Path selectedPng() {
        long id = selectedFrameId();
        return id > 0 ? FrameCache.getPng(id) : null;
    }

    public static BufferedImage selectedImage() throws IOException {
        Path path = selectedPng();
        return path == null ? null : ImageIO.read(path.toFile());
    }

    public static FrameDefinition selectedDefinition(int fallbackSlotCount) {
        FrameCache.CacheMetadata metadata = selectedMetadata();
        if (metadata == null) return null;

        int width = metadata.getWidth();
        int height = metadata.getHeight();
        if (width <= 0 || height <= 0) return null;

        List<FramePlacement> placements = new ArrayList<>();
        for (FrameCloudService.CloudPlacement p : metadata.getPlacements()) {
            if (p.getWidth() <= 0 || p.getHeight() <= 0) continue;

            double x = p.getX() / (double) width;
            double y = p.getY() / (double) height;
            double w = p.getWidth() / (double) width;
            double h = p.getHeight() / (double) height;

            int sourceSlot = Math.max(0, p.getSlot() - 1);
            placements.add(new FramePlacement(
                    sourceSlot, x, y, w, h, true));
        }

        if (placements.isEmpty() && fallbackSlotCount > 0) {
            return new FrameDefinition(
                    metadata.getName(),
                    FrameLayoutType.SINGLE,
                    width,
                    height,
                    List.of());
        }

        return new FrameDefinition(
                metadata.getName(),
                FrameLayoutType.SINGLE,
                width,
                height,
                placements);
    }

    private static long selectedFrameId() {
        return AppContextFrameId.get();
    }

    /*
     * Kept in a tiny adapter so the runtime has one place that interprets
     * the existing AppSettings selectedFrameId field.
     */
    private static final class AppContextFrameId {
        private static long get() {
            try {
                return Long.parseLong(
                        String.valueOf(
                                com.phomoria.app.AppContext.settings()
                                        .getSelectedFrameId()));
            } catch (Exception ignored) {
                return -1L;
            }
        }
    }
}
