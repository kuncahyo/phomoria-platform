package com.phomoria.session;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.phomoria.camera.LivePhotoClipStore;
import com.phomoria.debug.DebugLog;

public final class PhotoSession {
    private final List<BufferedImage> photos = new ArrayList<>();
    private final Map<Integer, Path> livePhotoClips = new HashMap<>();
    private final int slotCount;
    private final String sessionId = UUID.randomUUID().toString();

    public PhotoSession(int slotCount) { this.slotCount = Math.max(1, slotCount); }
    public int getSlotCount() { return slotCount; }
    public String getSessionId() { return sessionId; }
    public List<BufferedImage> getPhotos() { return Collections.unmodifiableList(photos); }
    public int getCapturedCount() { return photos.size(); }
    public boolean isComplete() { return photos.size() >= slotCount; }
    public void addPhoto(BufferedImage image) {
        if (image != null && !isComplete()) { photos.add(image); DebugLog.info("PhotoSession[" + sessionId + "].addPhoto -> " + photos.size() + "/" + slotCount); }
        else DebugLog.warn("PhotoSession.addPhoto ignored. imageNull=" + (image == null) + ", complete=" + isComplete());
    }
    public boolean replacePhoto(int index, BufferedImage image) {
        if (image == null || index < 0 || index >= photos.size()) return false;
        photos.set(index, image); DebugLog.info("PhotoSession[" + sessionId + "].replacePhoto -> index=" + index); return true;
    }
    public void setLivePhotoClip(int index, Path directory) {
        if (index < 0 || index >= slotCount) return;
        Path previous = livePhotoClips.remove(index);
        if (previous != null && !previous.equals(directory)) LivePhotoClipStore.deleteTree(previous);
        if (directory != null) livePhotoClips.put(index, directory);
    }
    public Path getLivePhotoClip(int index) { return livePhotoClips.get(index); }
    public Map<Integer, Path> getLivePhotoClips() { return Collections.unmodifiableMap(livePhotoClips); }
    public void clear() {
        photos.clear();
        for (Path path : livePhotoClips.values()) LivePhotoClipStore.deleteTree(path);
        livePhotoClips.clear();
    }
}
