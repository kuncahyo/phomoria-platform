package com.phomoria.cloud;

import com.phomoria.app.AppContext;
import com.phomoria.debug.DebugLog;
import com.phomoria.frame.FrameDefinition;
import com.phomoria.frame.FrameLayoutType;
import com.phomoria.frame.FramePlacement;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Counts distinct source photos used by the selected cloud frame.
     * Multiple physical holes may reference the same source_slot.
     * Legacy frames without explicit source_slot metadata keep the fallback count.
     */
    public static int selectedPhotoCount(int fallbackCount) {
        int fallback = Math.max(1, fallbackCount);
        FrameCache.CacheMetadata metadata = selectedMetadata();
        if (metadata == null) return fallback;

        List<FrameCloudService.CloudPlacement> cloud = metadata.getPlacements();
        boolean hasExplicitMapping = cloud.stream().anyMatch(p -> p.getSourceSlot() > 0);
        if (!hasExplicitMapping) return fallback;

        java.util.Set<Integer> sourceSlots = new java.util.LinkedHashSet<>();
        for (FrameCloudService.CloudPlacement placement : cloud) {
            if (placement.getWidth() <= 0 || placement.getHeight() <= 0) continue;
            int sourceSlot = placement.getSourceSlot() > 0
                    ? placement.getSourceSlot()
                    : placement.getSlot();
            if (sourceSlot > 0) sourceSlots.add(sourceSlot);
        }

        return sourceSlots.isEmpty() ? fallback : sourceSlots.size();
    }

    public static FrameDefinition selectedDefinition(int photoCount) {
        FrameCache.CacheMetadata metadata = selectedMetadata();
        if (metadata == null || metadata.getWidth() <= 0 || metadata.getHeight() <= 0) return null;
        List<FrameCloudService.CloudPlacement> cloud = metadata.getPlacements();
        if (cloud.isEmpty()) return null;

        boolean hasExplicitMapping = cloud.stream().anyMatch(p -> p.getSourceSlot() > 0);
        boolean legacySplit = !hasExplicitMapping && photoCount > 0 && cloud.size() == photoCount * 2;
        Map<Integer, Integer> sourceIndexes = new LinkedHashMap<>();
        if (hasExplicitMapping) {
            for (FrameCloudService.CloudPlacement placement : cloud) {
                if (placement.getWidth() <= 0 || placement.getHeight() <= 0) continue;
                int sourceSlot = placement.getSourceSlot() > 0
                        ? placement.getSourceSlot()
                        : placement.getSlot();
                if (sourceSlot > 0 && !sourceIndexes.containsKey(sourceSlot)) {
                    sourceIndexes.put(sourceSlot, sourceIndexes.size());
                }
            }
        }
        List<FramePlacement> placements = new ArrayList<>();

        for (int i = 0; i < cloud.size(); i++) {
            FrameCloudService.CloudPlacement p = cloud.get(i);
            if (p.getWidth() <= 0 || p.getHeight() <= 0) continue;

            int sourceIndex;
            if (hasExplicitMapping) {
                // Several physical holes can point to one captured source photo.
                int sourceSlot = p.getSourceSlot() > 0
                        ? p.getSourceSlot()
                        : p.getSlot();
                Integer mappedIndex = sourceIndexes.get(sourceSlot);
                sourceIndex = mappedIndex == null ? -1 : mappedIndex;
            } else if (legacySplit) {
                sourceIndex = i / 2;
            } else {
                sourceIndex = Math.max(0, p.getSlot() - 1);
            }

            if (sourceIndex < 0 || sourceIndex >= photoCount) {
                DebugLog.warn("Skipping invalid frame source mapping: physicalSlot=" + p.getSlot()
                        + ", sourceSlot=" + p.getSourceSlot() + ", capturedPhotos=" + photoCount);
                continue;
            }

            placements.add(new FramePlacement(sourceIndex,
                    p.getX() / (double) metadata.getWidth(),
                    p.getY() / (double) metadata.getHeight(),
                    p.getWidth() / (double) metadata.getWidth(),
                    p.getHeight() / (double) metadata.getHeight(),
                    true, p.getRotation()));
        }

        if (placements.isEmpty()) return null;
        DebugLog.info("Cloud frame mapping: frameId=" + metadata.getId()
                + ", physicalHoles=" + cloud.size()
                + ", capturedPhotos=" + photoCount
                + ", explicitSourceMapping=" + hasExplicitMapping
                + ", printerProfile=" + metadata.getPrinterProfile());
        return new FrameDefinition(metadata.getName(), FrameLayoutType.SINGLE,
                metadata.getWidth(), metadata.getHeight(), placements);
    }

    private static long selectedFrameId() {
        String value = AppContext.settings().getSelectedFrameId();
        if (value == null || value.isBlank()) return -1L;
        try { return Long.parseLong(value); }
        catch (NumberFormatException ex) { return -1L; }
    }
}
