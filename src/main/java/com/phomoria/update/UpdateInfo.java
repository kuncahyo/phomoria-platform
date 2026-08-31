package com.phomoria.update;

import com.google.gson.annotations.SerializedName;

public final class UpdateInfo {
    private final String version;

    @SerializedName(value = "minimumVersion", alternate = {"minimum_version"})
    private final String minimumVersion;

    @SerializedName(value = "downloadUrl", alternate = {"download_url"})
    private final String downloadUrl;

    private final String sha256;

    @SerializedName(value = "releaseNotes", alternate = {"release_notes"})
    private final String releaseNotes;

    public UpdateInfo(
            String version,
            String minimumVersion,
            String downloadUrl,
            String sha256,
            String releaseNotes) {
        this.version = version;
        this.minimumVersion = minimumVersion;
        this.downloadUrl = downloadUrl;
        this.sha256 = sha256;
        this.releaseNotes = releaseNotes;
    }

    public String getVersion() {
        return version;
    }

    public String getMinimumVersion() {
        return minimumVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getSha256() {
        return sha256;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }
}
