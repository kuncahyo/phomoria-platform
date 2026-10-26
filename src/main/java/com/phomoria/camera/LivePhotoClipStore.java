package com.phomoria.camera;

import com.phomoria.debug.DebugLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Persists the shutter-frozen JPEG sequence for one session slot. */
public final class LivePhotoClipStore {
    private LivePhotoClipStore() {}

    public static Path save(String sessionId, int slotIndex,
                            List<WebcamLivePhotoBuffer.Frame> frames) throws IOException {
        if (sessionId == null || sessionId.isBlank() || slotIndex < 0)
            throw new IllegalArgumentException("Invalid Live Photo slot identity.");
        Path base = appDataDirectory().resolve("live-photo-temp")
                .resolve(sessionId).resolve("slot-" + (slotIndex + 1));
        deleteTree(base);
        Files.createDirectories(base);
        int written = 0;
        for (int i = 0; i < frames.size(); i++) {
            byte[] jpeg = frames.get(i).jpeg();
            if (jpeg.length == 0) continue;
            Path file = base.resolve(String.format("frame-%05d-%013d.jpg", i,
                    frames.get(i).timestampMillis()));
            Files.write(file, jpeg);
            written++;
        }
        if (written == 0) { deleteTree(base); return null; }
        DebugLog.info("Live Photo frames saved: slot=" + (slotIndex + 1)
                + ", frames=" + written + ", dir=" + base);
        return base;
    }

    public static void deleteTree(Path path) {
        if (path == null || !Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    private static Path appDataDirectory() {
        String appData = System.getenv("APPDATA");
        return (appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"))
                : Path.of(appData)).resolve("Phomoria");
    }
}
