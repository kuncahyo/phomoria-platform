package com.phomoria.upload;

import com.phomoria.cloud.CloudConfigManager;
import com.phomoria.cloud.UploadService;
import com.phomoria.debug.DebugLog;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UploadQueueManager {
    private static final File ROOT = new File(
            System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
            "Phomoria/upload-queue");
    private static final UploadQueueStore STORE = new UploadQueueStore(ROOT);

    // Reservation and file transfer are deliberately independent. A slow old
    // asset request must never prevent a newer session from obtaining its QR.
    private static final ScheduledExecutorService RESERVATION_WORKER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Phomoria-Session-Reservation");
                t.setDaemon(true);
                return t;
            });
    private static final ScheduledExecutorService ASSET_WORKER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Phomoria-Asset-Upload");
                t.setDaemon(true);
                return t;
            });
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final AtomicBoolean RESERVATION_BUSY = new AtomicBoolean();
    private static final AtomicBoolean ASSET_BUSY = new AtomicBoolean();

    private UploadQueueManager() {}

    public static void start() {
        if (STARTED.compareAndSet(false, true)) {
            // Recovery can copy large session folders. Keep it off Swing's EDT.
            RESERVATION_WORKER.execute(() -> {
                recoverInterruptedPreparations();
                recoverOrphanedSessionFolders();
                RESERVATION_WORKER.scheduleWithFixedDelay(
                        UploadQueueManager::processNextReservation, 0, 3, TimeUnit.SECONDS);
                ASSET_WORKER.scheduleWithFixedDelay(
                        UploadQueueManager::processNextAsset, 0, 3, TimeUnit.SECONDS);
                DebugLog.info("Persistent upload queue started: " + ROOT.getAbsolutePath());
            });
        }
    }

    public static synchronized void enqueue(String sessionId, String frameName, File source) throws Exception {
        enqueue(sessionId, frameName, source, System.currentTimeMillis());
    }

    public static synchronized void enqueue(String sessionId, String frameName, File source,
                                             long previewStartedAt) throws Exception {
        enqueue(sessionId, frameName, source, previewStartedAt, "");
    }

    public static synchronized void enqueue(String sessionId, String frameName, File source,
                                             long previewStartedAt, String voucherToken) throws Exception {
        enqueue(sessionId, frameName, source, previewStartedAt, voucherToken, false);
    }

    /** Persists a session immediately but can hold network processing until optional assets finish. */
    public static synchronized void enqueue(String sessionId, String frameName, File source,
                                             long previewStartedAt, String voucherToken,
                                             boolean deferProcessing) throws Exception {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID kosong.");
        }
        File result = new File(source, "result.png");
        if (!result.isFile() || result.length() <= 0) {
            throw new IllegalArgumentException("Hasil render belum tersedia.");
        }
        STORE.initialize();
        File target = STORE.folder(sessionId);
        if (new File(target, "manifest.json").isFile()) {
            start();
            return;
        }

        long enqueueStarted = System.nanoTime();
        DebugLog.info("UPLOAD_QUEUE_STAGE START stage=copy-session-assets, session=" + sessionId
                + ", source=" + source.getAbsolutePath() + ", thread=" + Thread.currentThread().getName());
        File staging = new File(ROOT, sessionId + ".staging");
        deleteTree(staging);
        Files.createDirectories(staging.toPath());
        copy(source, staging, "result.png");
        copy(source, staging, "animation.gif");
        // A deferred session must never snapshot an MP4 while FFmpeg is still writing it.
        if (!deferProcessing) copy(source, staging, "live_photo.mp4");
        File[] files = source.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) {
                if (file.isFile() && file.getName().matches("photo_\\d+\\.jpg") && file.length() > 0) {
                    Files.copy(file.toPath(), new File(staging, file.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        UploadQueueEntry entry = new UploadQueueEntry();
        entry.sessionId = sessionId;
        entry.frameName = frameName == null ? "" : frameName;
        entry.voucherToken = voucherToken == null ? "" : voucherToken;
        entry.status = deferProcessing ? "PREPARING" : "PENDING";
        entry.createdAt = previewStartedAt > 0 ? previewStartedAt : System.currentTimeMillis();
        entry.updatedAt = System.currentTimeMillis();
        try (java.io.FileWriter writer = new java.io.FileWriter(new File(staging, "manifest.json"))) {
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(entry, writer);
        }
        try {
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(staging.toPath(), target.toPath());
        }
        DebugLog.info("UPLOAD_QUEUE_STAGE COMPLETE stage=copy-session-assets, session=" + sessionId
                + ", elapsedMs=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - enqueueStarted)
                + ", queuedBytes=" + folderSize(target) + ", status=" + entry.status);
        DebugLog.info("Upload queued durably. session=" + sessionId);
        start();
    }

    /** Releases queue entries left in PREPARING by an interrupted previous process. */
    private static void recoverInterruptedPreparations() {
        try {
            for (UploadQueueEntry entry : STORE.loadAll()) {
                if ("PREPARING".equals(entry.status)) {
                    entry.status = "PENDING";
                    entry.lastError = "Recovered after application restart; optional Live Photo may be absent.";
                    entry.updatedAt = System.currentTimeMillis();
                    STORE.save(entry);
                    DebugLog.warn("Recovered interrupted upload preparation. session=" + entry.sessionId);
                }
            }
        } catch (Exception ex) {
            DebugLog.error("Could not recover interrupted upload preparations.", ex);
        }
    }

    /** Finds session folders that were saved locally but never reached the upload queue. */
    private static void recoverOrphanedSessionFolders() {
        File sessionsRoot = new File(ROOT.getParentFile(), "sessions");
        File[] folders = sessionsRoot.listFiles(File::isDirectory);
        if (folders == null) return;
        for (File folder : folders) {
            File idFile = new File(folder, "session-id.txt");
            File result = new File(folder, "result.png");
            if (!idFile.isFile() || !result.isFile() || result.length() <= 0) continue;
            try {
                String sessionId = Files.readString(idFile.toPath()).trim();
                if (sessionId.isBlank() || STORE.find(sessionId) != null) continue;
                DebugLog.warn("Found orphaned local session; restoring upload queue. folder="
                        + folder.getName() + ", session=" + sessionId);
                enqueue(sessionId, "", folder, folder.lastModified(), "", false);
            } catch (Exception ex) {
                DebugLog.error("Failed to recover orphaned session folder=" + folder.getName(), ex);
            }
        }
    }

    /** Copies optional assets produced after the initial durable enqueue. */
    public static synchronized void refreshOptionalAssets(String sessionId, File source) throws Exception {
        if (sessionId == null || sessionId.isBlank() || source == null)
            throw new IllegalArgumentException("Session/source tidak valid untuk refresh aset.");
        File target = STORE.folder(sessionId);
        if (!new File(target, "manifest.json").isFile())
            throw new IllegalStateException("Manifest antrean belum tersedia: " + sessionId);
        copy(source, target, "live_photo.mp4");
        DebugLog.info("Optional upload assets refreshed. session=" + sessionId
                + ", livePhotoPresent=" + new File(target, "live_photo.mp4").isFile());
    }

    /** Allows the Live Photo worker to release a durably queued session for upload. */
    public static synchronized void markReady(String sessionId) throws Exception {
        UploadQueueEntry entry = STORE.find(sessionId);
        if (entry == null) throw new IllegalStateException("Queue entry not found: " + sessionId);
        if ("PREPARING".equals(entry.status)) {
            entry.status = "PENDING";
            entry.updatedAt = System.currentTimeMillis();
            entry.lastError = "";
            STORE.save(entry);
            DebugLog.info("Queued session is ready for network processing. session=" + sessionId);
        }
        start();
    }

    private static void processNextReservation() {
        if (!RESERVATION_BUSY.compareAndSet(false, true)) return;
        try {
            if (CloudConfigManager.getConfig().getToken().isBlank()) return;
            long now = System.currentTimeMillis();
            for (UploadQueueEntry entry : newestFirst(STORE.loadAll())) {
                if (!entry.galleryUrl.isBlank() || entry.nextAttemptAt > now) continue;
                if (!"PENDING".equals(entry.status) && !"RETRY".equals(entry.status)
                        && !"RESERVING".equals(entry.status)) continue;
                reserve(entry);
                break;
            }
        } catch (Exception ex) {
            DebugLog.error("Upload reservation queue scan failed.", ex);
        } finally {
            RESERVATION_BUSY.set(false);
        }
    }

    private static void reserve(UploadQueueEntry entry) {
        long operationStarted = System.nanoTime();
        DebugLog.info("UPLOAD_STAGE START stage=session-reservation, session=" + entry.sessionId
                + ", thread=" + Thread.currentThread().getName());
        entry.status = "RESERVING";
        entry.updatedAt = System.currentTimeMillis();
        try {
            STORE.save(entry);
            UploadService.UploadResult response = new UploadService().reserveSession(
                    entry.frameName, STORE.folder(entry.sessionId), entry.sessionId, entry.voucherToken);
            if (response.getGalleryUrl().isBlank()) {
                throw new IllegalStateException("URL galeri kosong dari server.");
            }
            entry.voucherToken = "";
            entry.galleryUrl = response.getGalleryUrl();
            entry.sessionCode = response.getSessionCode();
            entry.serverId = response.getServerId();
            entry.status = "RESERVED";
            entry.serverStatus = "UPLOADING";
            entry.lastError = "";
            entry.attemptCount = 0;
            entry.nextAttemptAt = 0;
            DebugLog.info("Session reserved; QR is available. session=" + entry.sessionId
                    + ", gallery=" + entry.galleryUrl
                    + ", elapsedMs=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - operationStarted));
        } catch (Exception ex) {
            DebugLog.error("UPLOAD_STAGE FAILED stage=session-reservation, session=" + entry.sessionId
                    + ", elapsedMs=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - operationStarted), ex);
            scheduleRetry(entry, ex, "Session reservation failed");
        }
        entry.updatedAt = System.currentTimeMillis();
        try {
            STORE.save(entry);
        } catch (Exception ex) {
            DebugLog.error("Cannot persist reservation result.", ex);
        }
    }

    private static void processNextAsset() {
        if (!ASSET_BUSY.compareAndSet(false, true)) return;
        try {
            if (CloudConfigManager.getConfig().getToken().isBlank()) return;
            long now = System.currentTimeMillis();
            for (UploadQueueEntry entry : newestFirst(STORE.loadAll())) {
                if (entry.galleryUrl.isBlank() || "COMPLETED".equals(entry.status)
                        || entry.nextAttemptAt > now) continue;
                if (!"RESERVED".equals(entry.status) && !"UPLOADING".equals(entry.status)
                        && !"RETRY".equals(entry.status) && !"PARTIAL".equals(entry.status)) continue;
                uploadNextAsset(entry);
                break;
            }
        } catch (Exception ex) {
            DebugLog.error("Upload asset queue scan failed.", ex);
        } finally {
            ASSET_BUSY.set(false);
        }
    }

    private static void uploadNextAsset(UploadQueueEntry entry) {
        long operationStarted = System.nanoTime();
        UploadService service = new UploadService();
        File folder = STORE.folder(entry.sessionId);
        try {
            List<UploadService.AssetFile> assets = service.getAssets(folder);
            if (assets.isEmpty()) throw new IllegalStateException("Manifest lokal tidak memiliki aset.");
            if (entry.uploadedAssetKeys == null) entry.uploadedAssetKeys = new ArrayList<>();

            UploadService.AssetFile next = assets.stream()
                    .filter(asset -> !entry.uploadedAssetKeys.contains(asset.getKey()))
                    .findFirst().orElse(null);

            if (next != null) {
                entry.status = "UPLOADING";
                entry.updatedAt = System.currentTimeMillis();
                STORE.save(entry);
                DebugLog.info("UPLOAD_STAGE START stage=asset-upload, session=" + entry.sessionId
                        + ", asset=" + next.getKey() + ", bytes=" + next.getFile().length()
                        + ", thread=" + Thread.currentThread().getName());
                long assetStarted = System.nanoTime();
                service.uploadAsset(entry.sessionId, next.getKey(), next.getFile());
                DebugLog.info("UPLOAD_STAGE COMPLETE stage=asset-upload, session=" + entry.sessionId
                        + ", asset=" + next.getKey() + ", elapsedMs="
                        + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - assetStarted));
                entry.uploadedAssetKeys.add(next.getKey());
                entry.serverStatus = service.fetchSessionStatus(entry.sessionCode);
                if ("COMPLETED".equalsIgnoreCase(entry.serverStatus)) {
                    entry.status = "COMPLETED";
                }
                entry.lastError = "";
                entry.attemptCount = 0;
                entry.nextAttemptAt = 0;
                DebugLog.info("Uploaded session asset. session=" + entry.sessionId
                        + ", asset=" + next.getKey());
            } else {
                // The server, not the local manifest, decides whether the
                // gallery is fully synchronized.
                entry.serverStatus = service.fetchSessionStatus(entry.sessionCode);
                if ("COMPLETED".equalsIgnoreCase(entry.serverStatus)) {
                    entry.status = "COMPLETED";
                    entry.lastError = "";
                    entry.nextAttemptAt = 0;
                    DebugLog.info("Upload queue completed from server status. session=" + entry.sessionId);
                } else {
                    entry.status = "PARTIAL";
                    entry.nextAttemptAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(8);
                }
            }
        } catch (Exception ex) {
            DebugLog.error("UPLOAD_STAGE FAILED stage=asset-upload, session=" + entry.sessionId
                    + ", elapsedMs=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - operationStarted), ex);
            scheduleRetry(entry, ex, "Asset upload failed");
        }
        entry.updatedAt = System.currentTimeMillis();
        try {
            STORE.save(entry);
        } catch (Exception ex) {
            DebugLog.error("Cannot persist asset upload progress.", ex);
        }
    }

    private static void scheduleRetry(UploadQueueEntry entry, Exception ex, String message) {
        entry.attemptCount++;
        entry.lastError = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
        entry.status = "RETRY";
        long delay = Math.min(3600L, 15L * (1L << Math.min(8, entry.attemptCount - 1)));
        entry.nextAttemptAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(delay);
        DebugLog.error(message + "; retry in " + delay + " seconds. session=" + entry.sessionId, ex);
    }

    private static List<UploadQueueEntry> newestFirst(List<UploadQueueEntry> entries) {
        entries.sort(Comparator.comparingLong((UploadQueueEntry e) -> e.createdAt).reversed());
        return entries;
    }

    public static UploadQueueEntry find(String id) {
        try {
            return STORE.find(id);
        } catch (Exception ex) {
            DebugLog.error("Cannot read upload queue item.", ex);
            return null;
        }
    }

    public static List<UploadQueueEntry> history() {
        try {
            return STORE.loadAll();
        } catch (Exception ex) {
            DebugLog.error("Cannot read upload history.", ex);
            return List.of();
        }
    }

    private static void copy(File source, File target, String name) throws Exception {
        File file = new File(source, name);
        if (file.isFile() && file.length() > 0) {
            Files.copy(file.toPath(), new File(target, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long folderSize(File folder) {
        if (folder == null || !folder.exists()) return 0L;
        try (var paths = Files.walk(folder.toPath())) {
            return paths.filter(Files::isRegularFile).mapToLong(path -> {
                try { return Files.size(path); } catch (Exception ignored) { return 0L; }
            }).sum();
        } catch (Exception ignored) { return 0L; }
    }

    private static void deleteTree(File dir) throws Exception {
        if (!dir.exists()) return;
        try (var paths = Files.walk(dir.toPath())) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) {}
            });
        }
    }
}
