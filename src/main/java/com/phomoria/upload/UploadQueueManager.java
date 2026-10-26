package com.phomoria.upload;

import com.phomoria.cloud.CloudConfigManager;
import com.phomoria.cloud.UploadService;
import com.phomoria.debug.DebugLog;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UploadQueueManager {
    private static final File ROOT = new File(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "Phomoria/upload-queue");
    private static final UploadQueueStore STORE = new UploadQueueStore(ROOT);
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Phomoria-Upload-Queue"); t.setDaemon(true); return t;
    });
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private UploadQueueManager() {}

    public static void start() {
        if (STARTED.compareAndSet(false, true)) {
            WORKER.scheduleWithFixedDelay(UploadQueueManager::processNext, 0, 10, TimeUnit.SECONDS);
            DebugLog.info("Persistent upload queue started: " + ROOT.getAbsolutePath());
        }
    }

    public static synchronized void enqueue(String sessionId, String frameName, File source) throws Exception {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("Session ID kosong.");
        File result = new File(source, "result.png");
        if (!result.isFile() || result.length() <= 0) throw new IllegalArgumentException("Hasil render belum tersedia.");
        STORE.initialize();
        File target = STORE.folder(sessionId);
        if (new File(target, "manifest.json").isFile()) { start(); return; }

        File staging = new File(ROOT, sessionId + ".staging");
        deleteTree(staging);
        Files.createDirectories(staging.toPath());
        copy(source, staging, "result.png");
        copy(source, staging, "animation.gif");
        copy(source, staging, "live_photo.mp4");
        File[] files = source.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
            for (File file : files) if (file.isFile() && file.getName().matches("photo_\\d+\\.jpg")) {
                Files.copy(file.toPath(), new File(staging, file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        UploadQueueEntry entry = new UploadQueueEntry();
        entry.sessionId = sessionId;
        entry.frameName = frameName == null ? "" : frameName;
        entry.createdAt = entry.updatedAt = System.currentTimeMillis();
        try (java.io.FileWriter writer = new java.io.FileWriter(new File(staging, "manifest.json"))) {
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(entry, writer);
        }
        try { Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(staging.toPath(), target.toPath()); }
        DebugLog.info("Upload queued durably. session=" + sessionId);
        start();
    }

    private static void processNext() {
        if (!BUSY.compareAndSet(false, true)) return;
        try {
            if (CloudConfigManager.getConfig().getToken().isBlank()) return;
            for (UploadQueueEntry entry : STORE.loadAll()) {
                if ("COMPLETED".equals(entry.status) || entry.nextAttemptAt > System.currentTimeMillis()) continue;
                if (!"PENDING".equals(entry.status) && !"RETRY".equals(entry.status) && !"UPLOADING".equals(entry.status)) continue;
                upload(entry);
                break;
            }
        } catch (Exception ex) { DebugLog.error("Upload queue scan failed.", ex); }
        finally { BUSY.set(false); }
    }

    private static void upload(UploadQueueEntry entry) {
        File folder = STORE.folder(entry.sessionId);
        entry.status = "UPLOADING";
        entry.updatedAt = System.currentTimeMillis();
        try { STORE.save(entry); } catch (Exception ex) { DebugLog.error("Cannot save queue state.", ex); return; }
        try {
            UploadService.UploadResult response = new UploadService().upload(entry.frameName, folder, entry.sessionId);
            if (response.getGalleryUrl().isBlank()) throw new IllegalStateException("URL galeri kosong dari server.");
            entry.galleryUrl = response.getGalleryUrl();
            entry.sessionCode = response.getSessionCode();
            entry.serverId = response.getServerId();
            entry.status = "COMPLETED";
            entry.lastError = "";
            entry.nextAttemptAt = 0;
            DebugLog.info("Upload queue completed. session=" + entry.sessionId + ", gallery=" + entry.galleryUrl);
        } catch (Exception ex) {
            entry.attemptCount++;
            entry.lastError = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
            entry.status = "RETRY";
            long delay = Math.min(3600L, 15L * (1L << Math.min(8, entry.attemptCount - 1)));
            entry.nextAttemptAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(delay);
            DebugLog.error("Upload failed; retry in " + delay + " seconds. session=" + entry.sessionId, ex);
        }
        entry.updatedAt = System.currentTimeMillis();
        try { STORE.save(entry); } catch (Exception ex) { DebugLog.error("Cannot persist upload result.", ex); }
    }

    public static UploadQueueEntry find(String id) {
        try { return STORE.find(id); }
        catch (Exception ex) { DebugLog.error("Cannot read upload queue item.", ex); return null; }
    }

    public static List<UploadQueueEntry> history() {
        try { return STORE.loadAll(); }
        catch (Exception ex) { DebugLog.error("Cannot read upload history.", ex); return List.of(); }
    }

    private static void copy(File source, File target, String name) throws Exception {
        File file = new File(source, name);
        if (file.isFile() && file.length() > 0) Files.copy(file.toPath(), new File(target, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteTree(File dir) throws Exception {
        if (!dir.exists()) return;
        try (var paths = Files.walk(dir.toPath())) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) {}
            });
        }
    }
}
