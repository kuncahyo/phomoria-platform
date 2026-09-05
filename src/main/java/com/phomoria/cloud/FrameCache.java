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

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    private static final Path ROOT = Path.of(
            System.getenv().getOrDefault(
                    "APPDATA",
                    System.getProperty("user.home")
            ),
            "Phomoria",
            "frames"
    );

    private FrameCache() {}

    public static Path getRoot() { return ROOT; }

    public static synchronized Path save(
            FrameCloudService.CloudFrame frame,
            byte[] png
    ) throws IOException {
        if (frame == null) throw new IllegalArgumentException("Frame tidak boleh null.");
        if (png == null || png.length == 0) {
            throw new IllegalArgumentException("Data PNG kosong.");
        }

        Path directory = frameDirectory(frame.getId());
        Files.createDirectories(directory);

        Path pngPath = directory.resolve("frame.png");
        Path metadataPath = directory.resolve("metadata.json");
        Path pngTemp = directory.resolve("frame.png.tmp");
        Path metadataTemp = directory.resolve("metadata.json.tmp");

        try {
            Files.write(pngTemp, png);
            CacheMetadata metadata = CacheMetadata.from(frame, png.length);
            Files.writeString(metadataTemp, GSON.toJson(metadata));
            moveReplace(pngTemp, pngPath);
            moveReplace(metadataTemp, metadataPath);

            DebugLog.info("Frame cache saved: id=" + frame.getId() +
                    ", bytes=" + png.length +
                    ", placements=" + frame.getPlacements().size());
            return pngPath;
        } finally {
            Files.deleteIfExists(pngTemp);
            Files.deleteIfExists(metadataTemp);
        }
    }

    public static synchronized boolean contains(long frameId) {
        if (frameId <= 0) return false;
        Path directory = frameDirectory(frameId);
        return Files.isRegularFile(directory.resolve("frame.png"))
                && Files.isRegularFile(directory.resolve("metadata.json"));
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
        try {
            return GSON.fromJson(Files.readString(file), CacheMetadata.class);
        } catch (Exception ex) {
            DebugLog.warn("Failed to read frame cache metadata for id=" +
                    frameId + ": " + ex.getMessage());
            return null;
        }
    }

    public static synchronized boolean delete(long frameId) throws IOException {
        if (frameId <= 0) return false;
        Path directory = frameDirectory(frameId);
        if (!Files.exists(directory)) return false;

        Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        throw new CacheDeleteRuntimeException(ex);
                    }
                });

        DebugLog.info("Frame cache deleted: id=" + frameId);
        return true;
    }

    public static synchronized List<Long> listCachedFrameIds() throws IOException {
        if (!Files.isDirectory(ROOT)) return List.of();

        List<Long> result = new ArrayList<>();
        try (var stream = Files.list(ROOT)) {
            stream.filter(Files::isDirectory).forEach(directory -> {
                try {
                    long id = Long.parseLong(directory.getFileName().toString());
                    if (contains(id)) result.add(id);
                } catch (NumberFormatException ignored) {}
            });
        }
        result.sort(Long::compareTo);
        return List.copyOf(result);
    }

    private static Path frameDirectory(long frameId) {
        return ROOT.resolve(Long.toString(frameId));
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static final class CacheMetadata {
        private long id;
        private String name;
        private String category;
        private String imagePath;
        private int version;
        private String sha256;
        private int width;
        private int height;
        private String status;
        private long cachedBytes;
        private List<FrameCloudService.CloudPlacement> placements = List.of();

        public CacheMetadata() {}

        private static CacheMetadata from(
                FrameCloudService.CloudFrame frame,
                long cachedBytes
        ) {
            CacheMetadata metadata = new CacheMetadata();
            metadata.id = frame.getId();
            metadata.name = frame.getName();
            metadata.category = frame.getCategory();
            metadata.imagePath = frame.getImagePath();
            metadata.version = frame.getVersion();
            metadata.sha256 = frame.getSha256();
            metadata.width = frame.getWidth();
            metadata.height = frame.getHeight();
            metadata.status = frame.getStatus();
            metadata.cachedBytes = cachedBytes;
            metadata.placements = frame.getPlacements();
            return metadata;
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
        public long getCachedBytes() { return cachedBytes; }
        public List<FrameCloudService.CloudPlacement> getPlacements() {
            return placements == null ? List.of() : List.copyOf(placements);
        }
        public boolean hasPlacementMetadata() {
            return placements != null && !placements.isEmpty();
        }
    }

    private static final class CacheDeleteRuntimeException
            extends RuntimeException {
        CacheDeleteRuntimeException(IOException cause) { super(cause); }
    }
}
