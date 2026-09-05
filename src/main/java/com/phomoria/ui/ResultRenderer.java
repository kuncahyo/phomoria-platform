package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.cloud.CloudFrameSupport;
import com.phomoria.cloud.FrameCache;
import com.phomoria.debug.DebugLog;
import com.phomoria.effects.PhotoEffectSession;
import com.phomoria.frame.FrameDefinition;
import com.phomoria.frame.FrameRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public final class ResultRenderer {
    private ResultRenderer() {}

    public static File render(
            List<BufferedImage> photos,
            File output
    ) throws Exception {

        PhotoEffectSession effectSession = AppContext.effectSession();
        List<BufferedImage> processed = effectSession.process(photos);

        FrameDefinition cloudDefinition =
                CloudFrameSupport.selectedDefinition(photos.size());

        Path cloudPng = CloudFrameSupport.selectedPng();
        FrameCache.CacheMetadata metadata =
                CloudFrameSupport.selectedMetadata();

        if (cloudDefinition != null
                && cloudPng != null
                && metadata != null
                && "ACTIVE".equalsIgnoreCase(metadata.getStatus())) {

            BufferedImage frameAsset = ImageIO.read(cloudPng.toFile());
            if (frameAsset == null) {
                throw new IllegalStateException(
                        "PNG frame cloud tidak dapat dibaca: " + cloudPng);
            }

            DebugLog.info(
                    "Using cloud frame runtime: id=" + metadata.getId() +
                    ", name=" + metadata.getName() +
                    ", placements=" + metadata.getPlacements().size());

            return FrameRenderer.render(
                    cloudDefinition,
                    frameAsset,
                    processed,
                    output);
        }

        DebugLog.warn(
                "Selected cloud frame unavailable. " +
                "Keeping existing FrameCatalog fallback.");

        return renderLegacyFallback(photos, processed, output);
    }

    private static File renderLegacyFallback(
            List<BufferedImage> photos,
            List<BufferedImage> processed,
            File output
    ) throws Exception {

        com.phomoria.frame.FramePreset preset =
                com.phomoria.frame.FrameCatalog.find(
                        AppContext.settings().getSelectedFrameId());

        if (preset == null) {
            preset = com.phomoria.frame.FrameCatalog.find(
                    "standard_vertical");
        }

        FrameDefinition definition =
                com.phomoria.frame.FrameCatalog.createDefinition(
                        preset,
                        photos.size());

        BufferedImage frameAsset =
                com.phomoria.frame.FrameTemplateLoader.loadAsset(
                        AppContext.settings().getFrameName());

        return FrameRenderer.render(
                definition,
                frameAsset,
                processed,
                output);
    }
}
