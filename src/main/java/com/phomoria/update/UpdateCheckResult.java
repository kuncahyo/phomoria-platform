package com.phomoria.update;

public final class UpdateCheckResult {
    public enum Status {
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        UPDATE_REQUIRED
    }

    private final Status status;
    private final UpdateInfo updateInfo;

    public UpdateCheckResult(Status status, UpdateInfo updateInfo) {
        this.status = status;
        this.updateInfo = updateInfo;
    }

    public Status getStatus() {
        return status;
    }

    public UpdateInfo getUpdateInfo() {
        return updateInfo;
    }

    public boolean isUpdateRequired() {
        return status == Status.UPDATE_REQUIRED;
    }

    public boolean isUpdateAvailable() {
        return status == Status.UPDATE_AVAILABLE
                || status == Status.UPDATE_REQUIRED;
    }
}
