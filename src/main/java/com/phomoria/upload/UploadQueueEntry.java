package com.phomoria.upload;

public final class UploadQueueEntry {
    public String sessionId;
    public String frameName = "";
    public String status = "PENDING";
    public String galleryUrl = "";
    public String sessionCode = "";
    public String serverId = "";
    public String lastError = "";
    public long createdAt;
    public long updatedAt;
    public long nextAttemptAt;
    public int attemptCount;
    public UploadQueueEntry() {}
}
