package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.cloud.CloudFrameSupport;
import com.phomoria.cloud.FrameCache;
import com.phomoria.debug.DebugLog;
import com.phomoria.effects.PhotoEffectSession;
import com.phomoria.frame.FrameDefinition;
import com.phomoria.frame.FrameRenderer;
import com.phomoria.frame.PrintPaperSize;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public final class ResultRenderer {
    private static final int RENDER_DPI = 300;
    private ResultRenderer() {}

    public static File render(List<BufferedImage> photos, File output) throws Exception {
        int[] canvas = selectedPaperCanvas();
        return render(photos, output, canvas[0], canvas[1]);
    }

    public static File render(List<BufferedImage> photos, File output,
                              int targetWidth, int targetHeight) throws Exception {
        PhotoEffectSession effectSession = AppContext.effectSession();
        List<BufferedImage> processed = effectSession.process(photos);
        FrameDefinition cloudDefinition = CloudFrameSupport.selectedDefinition(photos.size());
        Path cloudPng = CloudFrameSupport.selectedPng();
        FrameCache.CacheMetadata metadata = CloudFrameSupport.selectedMetadata();

        if (cloudDefinition != null && cloudPng != null && metadata != null
                && "ACTIVE".equalsIgnoreCase(metadata.getStatus())) {
            BufferedImage frameAsset = ImageIO.read(cloudPng.toFile());
            if (frameAsset == null) throw new IllegalStateException("PNG frame cloud tidak dapat dibaca: " + cloudPng);
            int[] orientedCanvas = matchFrameOrientation(targetWidth, targetHeight,
                    metadata.getWidth(), metadata.getHeight());
            DebugLog.info("Using cloud frame runtime: id=" + metadata.getId()
                    + ", name=" + metadata.getName()
                    + ", frameOrientation=" + metadata.getWidth() + "x" + metadata.getHeight()
                    + ", outputCanvas=" + orientedCanvas[0] + "x" + orientedCanvas[1]
                    + ", printerProfile=" + metadata.getPrinterProfile()
                    + ", placements=" + metadata.getPlacements().size());
            return FrameRenderer.render(cloudDefinition, frameAsset, processed, output,
                    orientedCanvas[0], orientedCanvas[1]);
        }
        DebugLog.warn("Selected cloud frame unavailable. Keeping existing FrameCatalog fallback.");
        return renderLegacyFallback(photos, processed, output, targetWidth, targetHeight);
    }

    private static int[] matchFrameOrientation(int targetWidth, int targetHeight, int frameWidth, int frameHeight) {
        if (targetWidth <= 0 || targetHeight <= 0 || frameWidth <= 0 || frameHeight <= 0) {
            return new int[]{targetWidth, targetHeight};
        }
        boolean targetLandscape = targetWidth > targetHeight;
        boolean frameLandscape = frameWidth > frameHeight;
        if (targetLandscape != frameLandscape) return new int[]{targetHeight, targetWidth};
        return new int[]{targetWidth, targetHeight};
    }

    private static File renderLegacyFallback(List<BufferedImage> photos, List<BufferedImage> processed,
            File output, int targetWidth, int targetHeight) throws Exception {
        com.phomoria.frame.FramePreset preset = com.phomoria.frame.FrameCatalog.find(
                AppContext.settings().getSelectedFrameId());
        if (preset == null) preset = com.phomoria.frame.FrameCatalog.find("standard_vertical");
        FrameDefinition definition = com.phomoria.frame.FrameCatalog.createDefinition(preset, photos.size());
        BufferedImage frameAsset = com.phomoria.frame.FrameTemplateLoader.loadAsset(AppContext.settings().getFrameName());
        return FrameRenderer.render(definition, frameAsset, processed, output, targetWidth, targetHeight);
    }

    private static int[] selectedPaperCanvas() {
        PrintPaperSize size = AppContext.settings().getPrintPaperSize();
        if (size == null) size = PrintPaperSize.FOUR_BY_SIX;
        double width = size.isCustom() ? AppContext.settings().getCustomPaperWidthInches() : size.getWidthInches();
        double height = size.isCustom() ? AppContext.settings().getCustomPaperHeightInches() : size.getHeightInches();
        if (width <= 0 || height <= 0) {
            DebugLog.warn("Invalid custom paper size. Falling back to 4x6 canvas.");
            width = 4.0; height = 6.0;
        }
        return new int[]{Math.max(1, (int) Math.round(width * RENDER_DPI)),
                Math.max(1, (int) Math.round(height * RENDER_DPI))};
    }
}
