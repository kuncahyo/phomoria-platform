package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.camera.LivePhotoVideoEncoder;
import com.phomoria.cloud.CloudFrameSupport;
import com.phomoria.cloud.FrameCache;
import com.phomoria.debug.DebugLog;
import com.phomoria.effects.PhotoEffectSession;
import com.phomoria.frame.FrameDefinition;
import com.phomoria.frame.FramePreset;
import com.phomoria.frame.FrameCatalog;
import com.phomoria.frame.FrameTemplateLoader;
import com.phomoria.frame.FrameRenderer;
import com.phomoria.frame.PrintPaperSize;
import com.phomoria.session.PhotoSession;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ResultRenderer {
    private static final int RENDER_DPI = 300;
    private ResultRenderer() {}

    public static File render(List<BufferedImage> photos, File output) throws Exception {
        int[] canvas = selectedPaperCanvas();
        List<BufferedImage> completeSlots = new ArrayList<>(photos == null ? List.of() : photos);
        PhotoSession session = AppContext.session();
        int slotCount = session == null ? completeSlots.size() : session.getSlotCount();
        while (completeSlots.size() < slotCount) completeSlots.add(null);
        // Null entries deliberately represent unfilled frame slots when the
        // session timer ends before all photos have been captured.
        if (completeSlots.isEmpty()) completeSlots.add(null);
        return render(completeSlots, output, canvas[0], canvas[1]);
    }

    public static File render(List<BufferedImage> photos, File output,
                              int targetWidth, int targetHeight) throws Exception {
        List<BufferedImage> processed = AppContext.effectSession().process(photos);
        Composition composition = resolveComposition(photos.size(), targetWidth, targetHeight);
        return FrameRenderer.render(composition.definition, composition.frameAsset,
                processed, output, composition.width, composition.height);
    }

    /**
     * Builds one synchronized Live Photo movie. Each captured slot contributes
     * its own pre-shutter sequence; slots without a clip remain still photos.
     * The same frame definition, placements, masks and overlay asset used by
     * result.png are reused for every video frame.
     */
    public static Path renderLivePhotoVideo(PhotoSession session, Path outputMp4) throws Exception {
        if (session == null || session.getPhotos().isEmpty()) return null;
        Map<Integer, Path> clipDirectories = session.getLivePhotoClips();
        if (clipDirectories == null || clipDirectories.isEmpty()) return null;

        List<List<Path>> clips = new ArrayList<>();
        for (int slot = 0; slot < session.getSlotCount(); slot++) {
            Path directory = clipDirectories.get(slot);
            List<Path> files = new ArrayList<>();
            if (directory != null && Files.isDirectory(directory)) {
                try (var stream = Files.list(directory)) {
                    files = stream.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jpg"))
                            .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                            .toList();
                }
            }
            clips.add(files);
        }
        int durationSeconds = AppContext.settings().getLivePhotoDurationSeconds();
        int outputFrameCount = Math.max(2, durationSeconds * WebcamFps.VALUE);
        boolean hasUsableClip = clips.stream().anyMatch(files -> files.size() >= 2);
        if (!hasUsableClip) {
            DebugLog.warn("Live Photo composition skipped: fewer than two motion frames.");
            return null;
        }

        List<BufferedImage> stillPhotos = new ArrayList<>(
                AppContext.effectSession().process(session.getPhotos()));
        while (stillPhotos.size() < session.getSlotCount()) stillPhotos.add(null);
        int[] paper = selectedPaperCanvas();
        Composition composition = resolveComposition(stillPhotos.size(), paper[0], paper[1]);
        int width = composition.width;
        int height = composition.height;
        // H.264 yuv420p requires even frame dimensions.
        width = Math.max(2, width - (width & 1));
        height = Math.max(2, height - (height & 1));

        Path framesDirectory = outputMp4.toAbsolutePath().getParent().resolve("live-photo-rendered-frames");
        deleteTree(framesDirectory);
        Files.createDirectories(framesDirectory);
        try {
            for (int frameIndex = 0; frameIndex < outputFrameCount; frameIndex++) {
                List<BufferedImage> currentPhotos = new ArrayList<>(stillPhotos);
                double progress = outputFrameCount <= 1 ? 0.0
                        : frameIndex / (double) (outputFrameCount - 1);
                for (int slot = 0; slot < clips.size(); slot++) {
                    List<Path> slotFrames = clips.get(slot);
                    if (slotFrames.size() < 2) continue;
                    int sourceIndex = findFrameForProgress(slotFrames, progress);
                    BufferedImage moving = ImageIO.read(slotFrames.get(sourceIndex).toFile());
                    if (moving != null) {
                        moving = com.phomoria.camera.CameraMirrorProcessor.process(
                                moving, AppContext.settings().isMirrorPhoto());
                        currentPhotos.set(slot,
                                AppContext.effectSession().processPhoto(moving, slot));
                    }
                }
                BufferedImage rendered = FrameRenderer.renderImage(
                        composition.definition, composition.frameAsset, currentPhotos, width, height);
                Path frameFile = framesDirectory.resolve(String.format("frame-%05d.png", frameIndex));
                if (!ImageIO.write(rendered, "png", frameFile.toFile()))
                    throw new IllegalStateException("PNG writer tidak tersedia untuk Live Photo.");
            }
            LivePhotoVideoEncoder.encode(framesDirectory, outputMp4, WebcamFps.VALUE);
            return outputMp4;
        } finally {
            deleteTree(framesDirectory);
        }
    }

    /** Selects the source frame nearest the requested point in the clip's timestamp range. */
    private static int findFrameForProgress(List<Path> frames, double progress) {
        long first = timestampFromFilename(frames.get(0));
        long last = timestampFromFilename(frames.get(frames.size() - 1));
        if (first < 0 || last <= first) {
            return (int) Math.round(progress * (frames.size() - 1));
        }
        long target = first + Math.round((last - first) * Math.max(0.0, Math.min(1.0, progress)));
        int bestIndex = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < frames.size(); i++) {
            long timestamp = timestampFromFilename(frames.get(i));
            if (timestamp < 0) continue;
            long distance = Math.abs(timestamp - target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static long timestampFromFilename(Path frame) {
        String name = frame.getFileName().toString();
        int dash = name.lastIndexOf('-');
        int dot = name.lastIndexOf('.');
        if (dash < 0 || dot <= dash + 1) return -1L;
        try { return Long.parseLong(name.substring(dash + 1, dot)); }
        catch (NumberFormatException ignored) { return -1L; }
    }

    private static Composition resolveComposition(int photoCount, int targetWidth, int targetHeight) throws Exception {
        FrameDefinition cloudDefinition = CloudFrameSupport.selectedDefinition(photoCount);
        Path cloudPng = CloudFrameSupport.selectedPng();
        FrameCache.CacheMetadata metadata = CloudFrameSupport.selectedMetadata();
        if (cloudDefinition != null && cloudPng != null && metadata != null
                && "ACTIVE".equalsIgnoreCase(metadata.getStatus())) {
            BufferedImage frameAsset = ImageIO.read(cloudPng.toFile());
            if (frameAsset == null) throw new IllegalStateException("PNG frame cloud tidak dapat dibaca: " + cloudPng);
            int[] oriented = matchFrameOrientation(targetWidth, targetHeight,
                    metadata.getWidth(), metadata.getHeight());
            return new Composition(cloudDefinition, frameAsset, oriented[0], oriented[1]);
        }
        FramePreset preset = FrameCatalog.find(AppContext.settings().getSelectedFrameId());
        if (preset == null) preset = FrameCatalog.find("standard_vertical");
        FrameDefinition definition = FrameCatalog.createDefinition(preset, photoCount);
        BufferedImage frameAsset = FrameTemplateLoader.loadAsset(AppContext.settings().getFrameName());
        return new Composition(definition, frameAsset, targetWidth, targetHeight);
    }

    private static int[] matchFrameOrientation(int targetWidth, int targetHeight, int frameWidth, int frameHeight) {
        if (targetWidth <= 0 || targetHeight <= 0 || frameWidth <= 0 || frameHeight <= 0)
            return new int[]{targetWidth, targetHeight};
        if ((targetWidth > targetHeight) != (frameWidth > frameHeight))
            return new int[]{targetHeight, targetWidth};
        return new int[]{targetWidth, targetHeight};
    }

    private static int[] selectedPaperCanvas() {
        PrintPaperSize size = AppContext.settings().getPrintPaperSize();
        if (size == null) size = PrintPaperSize.FOUR_BY_SIX;
        double width = size.isCustom() ? AppContext.settings().getCustomPaperWidthInches() : size.getWidthInches();
        double height = size.isCustom() ? AppContext.settings().getCustomPaperHeightInches() : size.getHeightInches();
        if (width <= 0 || height <= 0) { width = 4.0; height = 6.0; }
        return new int[]{Math.max(1, (int) Math.round(width * RENDER_DPI)),
                Math.max(1, (int) Math.round(height * RENDER_DPI))};
    }

    private static void deleteTree(Path path) {
        if (path == null || !Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    private record Composition(FrameDefinition definition, BufferedImage frameAsset, int width, int height) {}
    private static final class WebcamFps { private static final int VALUE = 15; }
}
