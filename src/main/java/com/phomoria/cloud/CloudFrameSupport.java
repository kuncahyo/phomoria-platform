package com.phomoria.cloud;

import com.phomoria.app.AppContext;
import com.phomoria.debug.DebugLog;
import com.phomoria.frame.FrameDefinition;
import com.phomoria.frame.FrameLayoutType;
import com.phomoria.frame.FramePlacement;
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

    public static FrameDefinition selectedDefinition(int photoCount) {
        FrameCache.CacheMetadata metadata = selectedMetadata();
        if (metadata == null || metadata.getWidth() <= 0 || metadata.getHeight() <= 0) return null;
        List<FrameCloudService.CloudPlacement> cloud = metadata.getPlacements();
        if (cloud.isEmpty()) return null;

        boolean hasExplicitMapping = cloud.stream().anyMatch(p -> p.getSourceSlot() >= 0);
        boolean legacySplit = !hasExplicitMapping && photoCount > 0 && cloud.size() == photoCount * 2;
        List<FramePlacement> placements = new ArrayList<>();

        for (int i = 0; i < cloud.size(); i++) {
            FrameCloudService.CloudPlacement p = cloud.get(i);
            if (p.getWidth() <= 0 || p.getHeight() <= 0) continue;

            int sourceIndex;
            if (p.getSourceSlot() >= 0) {
                // Website editor stores source_slot as a one-based photo number.
                sourceIndex = p.getSourceSlot() - 1;
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
