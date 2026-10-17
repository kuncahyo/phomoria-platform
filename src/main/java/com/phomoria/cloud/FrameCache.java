package com.phomoria.cloud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.phomoria.debug.DebugLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FrameCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
            "Phomoria", "frames");

    private FrameCache() {}
    public static Path getRoot() { return ROOT; }

    public static synchronized Path save(FrameCloudService.CloudFrame frame, byte[] png) throws IOException {
        if (frame == null) throw new IllegalArgumentException("Frame tidak boleh null.");
        if (png == null || png.length == 0) throw new IllegalArgumentException("Data PNG kosong.");
        Path directory = frameDirectory(frame.getId());
        Files.createDirectories(directory);
        Path pngPath = directory.resolve("frame.png"), metadataPath = directory.resolve("metadata.json");
        Path pngTemp = directory.resolve("frame.png.tmp"), metadataTemp = directory.resolve("metadata.json.tmp");
        try {
            Files.write(pngTemp, png);
            Files.writeString(metadataTemp, GSON.toJson(CacheMetadata.from(frame, png.length)));
            moveReplace(pngTemp, pngPath); moveReplace(metadataTemp, metadataPath);
            DebugLog.info("Frame cache saved: id=" + frame.getId() + ", bytes=" + png.length
                    + ", placements=" + frame.getPlacements().size());
            return pngPath;
        } finally {
            Files.deleteIfExists(pngTemp); Files.deleteIfExists(metadataTemp);
        }
    }

    public static synchronized boolean contains(long frameId) {
        if (frameId <= 0) return false;
        Path dir = frameDirectory(frameId);
        return Files.isRegularFile(dir.resolve("frame.png")) && Files.isRegularFile(dir.resolve("metadata.json"));
    }
    public static synchronized Path getPng(long frameId) {
        if (frameId <= 0) return null;
        Path png = frameDirectory(frameId).resolve("frame.png");
        return Files.isRegularFile(png) ? png : null;
    }
    public static synchronized CacheMetadata readMetadata(long frameId) {
        if (frameId <= 0) return null;
        Path file = frameDirectory(frameId).resolve("metadata.json");
        if (!Files.isRegularFile(file)) return null;
        try { return GSON.fromJson(Files.readString(file), CacheMetadata.class); }
        catch (Exception ex) {
            DebugLog.warn("Failed to read frame cache metadata for id=" + frameId + ": " + ex.getMessage());
            return null;
        }
    }
    public static synchronized boolean delete(long frameId) throws IOException {
        if (frameId <= 0) return false;
        Path dir = frameDirectory(frameId);
        if (!Files.exists(dir)) return false;
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException ex) { throw new CacheDeleteRuntimeException(ex); }
            });
        }
        DebugLog.info("Frame cache deleted: id=" + frameId);
        return true;
    }
    public static synchronized List<Long> listCachedFrameIds() throws IOException {
        if (!Files.isDirectory(ROOT)) return List.of();
        List<Long> ids = new ArrayList<>();
        try (var stream = Files.list(ROOT)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                try {
                    long id = Long.parseLong(dir.getFileName().toString());
                    if (contains(id)) ids.add(id);
                } catch (NumberFormatException ignored) {}
            });
        }
        ids.sort(Long::compareTo);
        return List.copyOf(ids);
    }
    private static Path frameDirectory(long id) { return ROOT.resolve(Long.toString(id)); }
    private static void moveReplace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException ex) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    public static final class CacheMetadata {
        private long id;
        private String name, category, imagePath, sha256, status, printerProfile;
        private int version, width, height;
        private long cachedBytes;
        private List<FrameCloudService.CloudPlacement> placements = List.of();
        public CacheMetadata() {}
        private static CacheMetadata from(FrameCloudService.CloudFrame frame, long bytes) {
            CacheMetadata m = new CacheMetadata();
            m.id = frame.getId(); m.name = frame.getName(); m.category = frame.getCategory();
            m.imagePath = frame.getImagePath(); m.version = frame.getVersion(); m.sha256 = frame.getSha256();
            m.width = frame.getWidth(); m.height = frame.getHeight(); m.status = frame.getStatus();
            m.printerProfile = frame.getPrinterProfile(); m.cachedBytes = bytes; m.placements = frame.getPlacements();
            return m;
        }
        public long getId() { return id; }
        public String getName() { return name == null ? "" : name; }
        public String getCategory() { return category == null ? "" : category; }
        public String getImagePath() { return imagePath == null ? "" : imagePath; }
        public int getVersion() { return version; }
        public String getSha256() { return sha256 == null ? "" : sha256; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public String getStatus() { return status == null ? "" : status; }
        public String getPrinterProfile() { return printerProfile == null || printerProfile.isBlank() ? "PROFILE_1" : printerProfile; }
        public long getCachedBytes() { return cachedBytes; }
        public List<FrameCloudService.CloudPlacement> getPlacements() { return placements == null ? List.of() : List.copyOf(placements); }
        public boolean hasPlacementMetadata() { return placements != null && !placements.isEmpty(); }
    }
    private static final class CacheDeleteRuntimeException extends RuntimeException {
        CacheDeleteRuntimeException(IOException cause) { super(cause); }
    }
}
