package com.phomoria.update;

import com.google.gson.Gson;
import com.phomoria.debug.DebugLog;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class UpdateService {
    private final HttpClient client;
    private final Gson gson = new Gson();
    private final String updateUrl;

    public UpdateService(String updateUrl) {
        this.updateUrl = updateUrl;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public UpdateInfo check() throws IOException, InterruptedException {
        DebugLog.info("Update check started. url=" + updateUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(updateUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        DebugLog.info("Update server HTTP status=" + response.statusCode());

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Update server returned HTTP " + response.statusCode()
            );
        }

        UpdateInfo info = gson.fromJson(response.body(), UpdateInfo.class);

        if (info == null || info.getVersion() == null
                || info.getVersion().isBlank()) {
            throw new IOException("Invalid update response: version missing.");
        }

        DebugLog.info(
                "Update metadata received. latest=" + info.getVersion()
                        + ", minimum=" + info.getMinimumVersion()
        );

        return info;
    }

    public UpdateCheckResult evaluate(UpdateInfo info) {
        String installed = AppVersion.current();
        String latest = info.getVersion();
        String minimum = info.getMinimumVersion();

        if (minimum == null || minimum.isBlank()) {
            minimum = latest;
        }

        DebugLog.info(
                "Version evaluation. installed=" + installed
                        + ", latest=" + latest
                        + ", minimum=" + minimum
        );

        if (VersionComparator.isOlder(installed, minimum)) {
            DebugLog.warn("Mandatory update required.");
            return new UpdateCheckResult(
                    UpdateCheckResult.Status.UPDATE_REQUIRED, info
            );
        }

        if (VersionComparator.isOlder(installed, latest)) {
            DebugLog.info("Optional update available.");
            return new UpdateCheckResult(
                    UpdateCheckResult.Status.UPDATE_AVAILABLE, info
            );
        }

        DebugLog.info("Application is up to date.");
        return new UpdateCheckResult(
                UpdateCheckResult.Status.UP_TO_DATE, info
        );
    }
}
