package com.phomoria.upload;

import java.util.ArrayList;
import java.util.List;

public final class UploadQueueEntry {
    public String sessionId;
    public String frameName = "";
    public String status = "PENDING";
    public String serverStatus = "";
    public String galleryUrl = "";
    public String sessionCode = "";
    public String serverId = "";
    public String lastError = "";
    public List<String> uploadedAssetKeys = new ArrayList<>();
    public long createdAt;
    public long updatedAt;
    public long nextAttemptAt;
    public int attemptCount;
    public UploadQueueEntry() {}
}
