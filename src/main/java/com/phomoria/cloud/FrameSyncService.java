package com.phomoria.cloud;

import com.phomoria.debug.DebugLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * V22.7 - Synchronizes assigned cloud frames with the local FrameCache.
 *
 * Rules:
 *  - new frame          -> download
 *  - changed frame      -> download again
 *  - unchanged frame   -> keep local cache
 *  - no longer assigned -> delete local cache
 *
 * Comparison uses frame ID plus version/SHA-256.
 */
public final class FrameSyncService {

    public enum Action {
        DOWNLOADED,
        UPDATED,
        UNCHANGED,
        DELETED
    }

    public static final class SyncItem {
        private final long frameId;
        private final String name;
        private final Action action;

        public SyncItem(long frameId, String name, Action action) {
            this.frameId = frameId;
            this.name = name == null ? "" : name;
            this.action = action;
        }

        public long getFrameId() {
            return frameId;
        }

        public String getName() {
            return name;
        }

        public Action getAction() {
            return action;
        }
    }

    public static final class SyncResult {
        private final List<SyncItem> items;

        public SyncResult(List<SyncItem> items) {
            this.items = List.copyOf(items);
        }

        public List<SyncItem> getItems() {
            return items;
        }

        public long count(Action action) {
            return items.stream()
                    .filter(item -> item.getAction() == action)
                    .count();
        }
    }

    private final FrameCloudService cloudService;

    public FrameSyncService() {
        this(new FrameCloudService());
    }

    public FrameSyncService(FrameCloudService cloudService) {
        if (cloudService == null) {
            throw new IllegalArgumentException("Cloud service tidak boleh null.");
        }
        this.cloudService = cloudService;
    }

    /**
     * Performs one complete cloud -> local cache synchronization.
     *
     * FrameCloudService currently exposes checked Exception from its
     * network operations, so this method intentionally propagates Exception.
     */
    public SyncResult sync() throws Exception {
        DebugLog.info("Starting frame cache sync...");

        List<FrameCloudService.CloudFrame> assigned =
                cloudService.fetchAssignedFrames();

        List<Long> cachedIds = FrameCache.listCachedFrameIds();
        Set<Long> assignedIds = new HashSet<>();

        ArrayList<SyncItem> result = new ArrayList<>();

        for (FrameCloudService.CloudFrame frame : assigned) {
            long id = frame.getId();
            assignedIds.add(id);

            if (id <= 0) {
                DebugLog.warn("Ignoring cloud frame with invalid id: " + id);
                continue;
            }

            if (!FrameCache.contains(id)) {
                byte[] png = cloudService.downloadFrame(id);
                FrameCache.save(frame, png);

                result.add(new SyncItem(
                        id,
                        frame.getName(),
                        Action.DOWNLOADED
                ));

                DebugLog.info("Frame sync downloaded: id=" + id);
                continue;
            }

            FrameCache.CacheMetadata local =
                    FrameCache.readMetadata(id);

            if (needsUpdate(frame, local)) {
                byte[] png = cloudService.downloadFrame(id);
                FrameCache.save(frame, png);

                result.add(new SyncItem(
                        id,
                        frame.getName(),
                        Action.UPDATED
                ));

                DebugLog.info("Frame sync updated: id=" + id);
            } else {
                result.add(new SyncItem(
                        id,
                        frame.getName(),
                        Action.UNCHANGED
                ));
            }
        }

        for (Long cachedId : cachedIds) {
            if (!assignedIds.contains(cachedId)) {
                FrameCache.delete(cachedId);

                result.add(new SyncItem(
                        cachedId,
                        "",
                        Action.DELETED
                ));

                DebugLog.info(
                        "Frame sync deleted unassigned cache: id=" + cachedId
                );
            }
        }

        DebugLog.info(
                "Frame cache sync completed: assigned=" + assigned.size()
                        + ", downloaded=" + count(result, Action.DOWNLOADED)
                        + ", updated=" + count(result, Action.UPDATED)
                        + ", unchanged=" + count(result, Action.UNCHANGED)
                        + ", deleted=" + count(result, Action.DELETED)
        );

        return new SyncResult(result);
    }

    private static boolean needsUpdate(
            FrameCloudService.CloudFrame cloud,
            FrameCache.CacheMetadata local
    ) {
        if (local == null) {
            return true;
        }

        if (local.getVersion() != cloud.getVersion()) {
            return true;
        }

        String cloudSha = normalize(cloud.getSha256());
        String localSha = normalize(local.getSha256());

        return !cloudSha.equals(localSha);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static long count(
            List<SyncItem> items,
            Action action
    ) {
        return items.stream()
                .filter(item -> item.getAction() == action)
                .count();
    }
}
