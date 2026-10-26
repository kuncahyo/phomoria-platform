package com.phomoria.upload;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class UploadQueueStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final File root;
    UploadQueueStore(File root) { this.root = root; }

    synchronized void initialize() throws Exception { Files.createDirectories(root.toPath()); }
    synchronized File folder(String id) { return new File(root, id); }

    synchronized void save(UploadQueueEntry entry) throws Exception {
        File dir = folder(entry.sessionId);
        Files.createDirectories(dir.toPath());
        File temp = new File(dir, "manifest.json.tmp");
        File target = new File(dir, "manifest.json");
        try (FileWriter writer = new FileWriter(temp)) { JSON.toJson(entry, writer); }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    synchronized List<UploadQueueEntry> loadAll() throws Exception {
        initialize();
        List<UploadQueueEntry> result = new ArrayList<>();
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return result;
        for (File dir : dirs) {
            File manifest = new File(dir, "manifest.json");
            if (!manifest.isFile()) continue;
            try (FileReader reader = new FileReader(manifest)) {
                UploadQueueEntry item = JSON.fromJson(reader, UploadQueueEntry.class);
                if (item != null && item.sessionId != null && !item.sessionId.isBlank()) result.add(item);
            } catch (Exception ignored) {
                // Preserve damaged records for manual recovery.
            }
        }
        result.sort(java.util.Comparator.comparingLong(e -> e.createdAt));
        return result;
    }

    synchronized UploadQueueEntry find(String id) throws Exception {
        for (UploadQueueEntry entry : loadAll()) if (id.equals(entry.sessionId)) return entry;
        return null;
    }
}
