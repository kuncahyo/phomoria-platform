package com.phomoria.update;

import com.phomoria.debug.DebugLog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;

public final class UpdateDownloader {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public Path download(UpdateInfo info) throws IOException, InterruptedException {
        if (info.getDownloadUrl() == null || info.getDownloadUrl().isBlank()) {
            throw new IOException("Update download URL is empty.");
        }

        Path dir = Path.of(
                System.getProperty("user.home"),
                "AppData", "Roaming", "Phomoria", "updates"
        );

        Files.createDirectories(dir);

        String safeVersion = info.getVersion().replaceAll("[^a-zA-Z0-9._-]", "_");
        Path destination = dir.resolve("Phomoria-" + safeVersion + ".zip");

        DebugLog.info("Update download started: " + info.getDownloadUrl());
        DebugLog.info("Update destination: " + destination);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(info.getDownloadUrl()))
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();

        HttpResponse<InputStream> response =
                client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Update download failed. HTTP " + response.statusCode()
            );
        }

        try (InputStream input = response.body()) {
            Files.copy(input, destination,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        DebugLog.info("Update package downloaded: " + destination);

        verifySha256(destination, info.getSha256());

        return destination;
    }

    private void verifySha256(Path file, String expected)
            throws IOException {

        if (expected == null || expected.isBlank()) {
            DebugLog.warn(
                    "SHA-256 not supplied. Verification skipped in development."
            );
            return;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;

                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }

            String actual = toHex(digest.digest());

            if (!actual.equalsIgnoreCase(expected.trim())) {
                throw new IOException(
                        "SHA-256 mismatch. expected="
                                + expected + ", actual=" + actual
                );
            }

            DebugLog.info("Update SHA-256 verification successful.");
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 unavailable.", ex);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();

        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }

        return result.toString();
    }
}
