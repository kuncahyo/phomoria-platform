package com.phomoria.cloud;

import com.phomoria.debug.DebugLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FrameSyncService {

    public enum Action { DOWNLOADED, UPDATED, UNCHANGED, DELETED }

    public static final class SyncItem {
        private final long frameId;
        private final String name;
        private final Action action;

        public SyncItem(long frameId, String name, Action action) {
            this.frameId = frameId;
            this.name = name == null ? "" : name;
            this.action = action;
        }

        public long getFrameId() { return frameId; }
        public String getName() { return name; }
        public Action getAction() { return action; }
    }

    public static final class SyncResult {
        private final List<SyncItem> items;
        public SyncResult(List<SyncItem> items) { this.items = List.copyOf(items); }
        public List<SyncItem> getItems() { return items; }
        public long count(Action action) {
            return items.stream().filter(item -> item.getAction() == action).count();
        }
    }

    private final FrameCloudService cloudService;

    public FrameSyncService() { this(new FrameCloudService()); }

    public FrameSyncService(FrameCloudService cloudService) {
        if (cloudService == null) {
            throw new IllegalArgumentException("Cloud service tidak boleh null.");
        }
        this.cloudService = cloudService;
    }

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
                result.add(new SyncItem(id, frame.getName(), Action.DOWNLOADED));
                continue;
            }

            FrameCache.CacheMetadata local = FrameCache.readMetadata(id);

            if (needsUpdate(frame, local)) {
                byte[] png = cloudService.downloadFrame(id);
                FrameCache.save(frame, png);
                result.add(new SyncItem(id, frame.getName(), Action.UPDATED));
                DebugLog.info("Frame sync updated: id=" + id);
            } else {
                result.add(new SyncItem(id, frame.getName(), Action.UNCHANGED));
            }
        }

        for (Long cachedId : cachedIds) {
            if (!assignedIds.contains(cachedId)) {
                FrameCache.delete(cachedId);
                result.add(new SyncItem(cachedId, "", Action.DELETED));
            }
        }

        DebugLog.info(
                "Frame cache sync completed: assigned=" + assigned.size() +
                ", downloaded=" + count(result, Action.DOWNLOADED) +
                ", updated=" + count(result, Action.UPDATED) +
                ", unchanged=" + count(result, Action.UNCHANGED) +
                ", deleted=" + count(result, Action.DELETED));

        return new SyncResult(result);
    }

    private static boolean needsUpdate(
            FrameCloudService.CloudFrame cloud,
            FrameCache.CacheMetadata local
    ) {
        if (local == null) return true;
        if (local.getVersion() != cloud.getVersion()) return true;

        String cloudSha = normalize(cloud.getSha256());
        String localSha = normalize(local.getSha256());
        if (!cloudSha.equals(localSha)) return true;

        if (local.getWidth() != cloud.getWidth()
                || local.getHeight() != cloud.getHeight()) return true;

        if (!local.hasPlacementMetadata()
                && !cloud.getPlacements().isEmpty()) return true;

        if (local.getPlacements().size() != cloud.getPlacements().size()) return true;

        for (int i = 0; i < cloud.getPlacements().size(); i++) {
            FrameCloudService.CloudPlacement a = cloud.getPlacements().get(i);
            FrameCloudService.CloudPlacement b = local.getPlacements().get(i);
            if (a.getSlot() != b.getSlot()
                    || a.getX() != b.getX()
                    || a.getY() != b.getY()
                    || a.getWidth() != b.getWidth()
                    || a.getHeight() != b.getHeight()
                    || Double.compare(a.getRotation(), b.getRotation()) != 0) {
                return true;
            }
        }

        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static long count(List<SyncItem> items, Action action) {
        return items.stream().filter(item -> item.getAction() == action).count();
    }
}
