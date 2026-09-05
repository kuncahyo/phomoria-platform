package com.phomoria.cloud;

import com.phomoria.app.AppContext;
import com.phomoria.frame.FrameDefinition;
import com.phomoria.frame.FrameLayoutType;
import com.phomoria.frame.FramePlacement;

import java.util.ArrayList;
import java.util.List;

public final class CloudFrameSupport {

    private CloudFrameSupport() {
    }

    public static FrameCache.CacheMetadata selectedMetadata() {
        long id = selectedFrameId();
        return id > 0 ? FrameCache.readMetadata(id) : null;
    }

    public static java.nio.file.Path selectedPng() {
        long id = selectedFrameId();
        return id > 0 ? FrameCache.getPng(id) : null;
    }

    public static FrameDefinition selectedDefinition(int photoCount) {
        FrameCache.CacheMetadata metadata = selectedMetadata();
        if (metadata == null) {
            return null;
        }

        int canvasWidth = metadata.getWidth();
        int canvasHeight = metadata.getHeight();

        if (canvasWidth <= 0 || canvasHeight <= 0) {
            return null;
        }

        List<FrameCloudService.CloudPlacement> cloudPlacements =
                metadata.getPlacements();

        if (cloudPlacements == null || cloudPlacements.isEmpty()) {
            return null;
        }

        /*
         * Photobooth Split:
         *
         * The PNG has two copies of the photo strip on one sheet.
         * Laravel detects six holes for a three-photo session:
         *
         *   hole 1 + hole 2 -> photo 1
         *   hole 3 + hole 4 -> photo 2
         *   hole 5 + hole 6 -> photo 3
         *
         * We intentionally do NOT capture six photos.
         */
        boolean splitDuplicateLayout =
                photoCount > 0
                        && cloudPlacements.size() == photoCount * 2;

        List<FramePlacement> placements = new ArrayList<>();

        for (int i = 0; i < cloudPlacements.size(); i++) {
            FrameCloudService.CloudPlacement p =
                    cloudPlacements.get(i);

            if (p.getWidth() <= 0 || p.getHeight() <= 0) {
                continue;
            }

            int sourceSlot;

            if (splitDuplicateLayout) {
                sourceSlot = i / 2;
            } else {
                sourceSlot = Math.max(0, p.getSlot() - 1);
            }

            if (sourceSlot < 0 || sourceSlot >= photoCount) {
                continue;
            }

            double x = p.getX() / (double) canvasWidth;
            double y = p.getY() / (double) canvasHeight;
            double width = p.getWidth() / (double) canvasWidth;
            double height = p.getHeight() / (double) canvasHeight;

            placements.add(
                    new FramePlacement(
                            sourceSlot,
                            x,
                            y,
                            width,
                            height,
                            true));
        }

        if (placements.isEmpty()) {
            return null;
        }

        return new FrameDefinition(
                metadata.getName(),
                FrameLayoutType.SINGLE,
                canvasWidth,
                canvasHeight,
                placements);
    }

    private static long selectedFrameId() {
        String value =
                AppContext.settings().getSelectedFrameId();

        if (value == null || value.isBlank()) {
            return -1L;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }
}
