package com.phomoria.session;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.phomoria.debug.DebugLog;

public final class PhotoSession {
    private final List<BufferedImage> photos = new ArrayList<>();
    private final int slotCount;
    private final String sessionId = UUID.randomUUID().toString();

    public PhotoSession(int slotCount) {
        this.slotCount = Math.max(1, slotCount);
    }

    public int getSlotCount() {
        return slotCount;
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<BufferedImage> getPhotos() {
        return Collections.unmodifiableList(photos);
    }

    public int getCapturedCount() {
        return photos.size();
    }

    public boolean isComplete() {
        return photos.size() >= slotCount;
    }

    public void addPhoto(BufferedImage image) {
        if (image != null && !isComplete()) {
            photos.add(image);
            DebugLog.info(
                    "PhotoSession[" + sessionId + "].addPhoto -> "
                            + photos.size() + "/" + slotCount
            );
        } else {
            DebugLog.warn(
                    "PhotoSession.addPhoto ignored. imageNull="
                            + (image == null) + ", complete=" + isComplete()
            );
        }
    }

    public boolean replacePhoto(int index, BufferedImage image) {
        if (image == null || index < 0 || index >= photos.size()) {
            return false;
        }
        photos.set(index, image);
        DebugLog.info(
                "PhotoSession[" + sessionId + "].replacePhoto -> index=" + index
        );
        return true;
    }

    public void clear() {
        photos.clear();
    }
}
