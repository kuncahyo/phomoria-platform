package com.phomoria.cloud;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FrameCloudService {

    private static final Gson GSON = new Gson();

    private final CloudConfig config;
    private final String deviceUuid;

    public FrameCloudService() {
        this(CloudConfigManager.getConfig(), DeviceManager.getDevice().getUuid());
    }

    public FrameCloudService(CloudConfig config, String deviceUuid) {
        if (config == null) throw new IllegalArgumentException("CloudConfig tidak boleh null.");
        if (deviceUuid == null || deviceUuid.isBlank()) {
            throw new IllegalArgumentException("Device UUID belum tersedia.");
        }
        this.config = config;
        this.deviceUuid = deviceUuid.trim();
    }

    public String getDeviceUuid() { return deviceUuid; }

    public List<CloudFrame> fetchAssignedFrames() throws Exception {
        String url = buildUrl("/api/device/frames?device_uuid=" +
                URLEncoder.encode(deviceUuid, StandardCharsets.UTF_8));

        HttpGet get = createAuthenticatedGet(url);

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(get)) {

            String body = readBody(response);
            if (response.getCode() < 200 || response.getCode() >= 300) {
                throw new FrameCloudException(
                        "Gagal mengambil daftar frame. HTTP " +
                        response.getCode() + ": " + body);
            }

            JsonObject root = parseObject(body);
            JsonObject data = getObject(root, "data");
            if (data == null) return Collections.emptyList();

            JsonArray frames = getArray(data, "frames");
            if (frames == null || frames.isEmpty()) return Collections.emptyList();

            List<CloudFrame> result = new ArrayList<>();
            for (JsonElement element : frames) {
                if (!element.isJsonObject()) continue;
                CloudFrame frame = parseFrame(element.getAsJsonObject());
                if (frame != null) result.add(frame);
            }
            return Collections.unmodifiableList(result);
        }
    }

    public byte[] downloadFrame(long frameId) throws Exception {
        if (frameId <= 0) throw new IllegalArgumentException("Frame ID tidak valid: " + frameId);

        String url = buildUrl("/api/device/frames/" + frameId +
                "/download?device_uuid=" +
                URLEncoder.encode(deviceUuid, StandardCharsets.UTF_8));

        HttpGet get = createAuthenticatedGet(url);

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(get)) {

            byte[] body = response.getEntity() == null
                    ? new byte[0]
                    : EntityUtils.toByteArray(response.getEntity());

            if (response.getCode() < 200 || response.getCode() >= 300) {
                String message = new String(body, StandardCharsets.UTF_8);
                throw new FrameCloudException(
                        "Gagal mengunduh frame " + frameId +
                        ". HTTP " + response.getCode() +
                        (message.isBlank() ? "" : ": " + message));
            }
            if (body.length == 0) {
                throw new FrameCloudException(
                        "Download frame " + frameId + " berhasil tetapi file kosong.");
            }
            return body;
        }
    }

    private HttpGet createAuthenticatedGet(String url) {
        String token = config.getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "Token cloud belum tersedia. Silakan login terlebih dahulu.");
        }
        HttpGet get = new HttpGet(url);
        get.setHeader("Accept", "application/json, image/png, image/*");
        get.setHeader("Authorization", "Bearer " + token);
        return get;
    }

    private String buildUrl(String path) {
        String server = config.getServer();
        if (server == null || server.isBlank()) {
            throw new IllegalStateException("Server cloud belum dikonfigurasi.");
        }
        return server.replaceAll("/+$", "") + path;
    }

    private static String readBody(CloseableHttpResponse response) throws Exception {
        return response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
    }

    private static JsonObject parseObject(String body) {
        if (body == null || body.isBlank()) {
            throw new FrameCloudException("Response API kosong.");
        }
        JsonElement element = GSON.fromJson(body, JsonElement.class);
        if (element == null || !element.isJsonObject()) {
            throw new FrameCloudException("Response API bukan JSON object.");
        }
        return element.getAsJsonObject();
    }

    private static JsonObject getObject(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray getArray(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static CloudFrame parseFrame(JsonObject json) {
        if (!json.has("id") || json.get("id").isJsonNull()) return null;

        long id = json.get("id").getAsLong();
        String name = getString(json, "name");
        String category = getString(json, "category");
        String imagePath = getString(json, "image_path");
        String sha256 = getString(json, "sha256");
        String status = getString(json, "status");
        int version = getInt(json, "version");
        int width = getInt(json, "width");
        int height = getInt(json, "height");

        List<CloudPlacement> placements = new ArrayList<>();
        JsonArray array = getArray(json, "placements");
        if (array != null) {
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject p = element.getAsJsonObject();
                placements.add(new CloudPlacement(
                        getInt(p, "slot"),
                        getInt(p, "x"),
                        getInt(p, "y"),
                        getInt(p, "width"),
                        getInt(p, "height"),
                        getDouble(p, "rotation")
                ));
            }
        }

        return new CloudFrame(
                id, name, category, imagePath, version, sha256,
                width, height, status, placements);
    }

    private static String getString(JsonObject json, String name) {
        JsonElement value = json.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static int getInt(JsonObject json, String name) {
        JsonElement value = json.get(name);
        return value == null || value.isJsonNull() ? 0 : value.getAsInt();
    }

    private static double getDouble(JsonObject json, String name) {
        JsonElement value = json.get(name);
        return value == null || value.isJsonNull() ? 0.0 : value.getAsDouble();
    }

    public static final class CloudPlacement {
        private final int slot;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final double rotation;

        public CloudPlacement(int slot, int x, int y, int width, int height, double rotation) {
            this.slot = slot;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.rotation = rotation;
        }

        public int getSlot() { return slot; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public double getRotation() { return rotation; }
    }

    public static final class CloudFrame {
        private final long id;
        private final String name;
        private final String category;
        private final String imagePath;
        private final int version;
        private final String sha256;
        private final int width;
        private final int height;
        private final String status;
        private final List<CloudPlacement> placements;

        public CloudFrame(long id, String name, String category, String imagePath,
                          int version, String sha256, int width, int height,
                          String status, List<CloudPlacement> placements) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.imagePath = imagePath;
            this.version = version;
            this.sha256 = sha256;
            this.width = width;
            this.height = height;
            this.status = status;
            this.placements = List.copyOf(
                    placements == null ? List.of() : placements);
        }

        public long getId() { return id; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public String getImagePath() { return imagePath; }
        public int getVersion() { return version; }
        public String getSha256() { return sha256; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public String getStatus() { return status; }
        public List<CloudPlacement> getPlacements() { return placements; }

        @Override
        public String toString() {
            return "CloudFrame{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", category='" + category + '\'' +
                    ", version=" + version +
                    ", width=" + width +
                    ", height=" + height +
                    ", placements=" + placements.size() +
                    ", status='" + status + '\'' +
                    '}';
        }
    }

    public static final class FrameCloudException extends RuntimeException {
        public FrameCloudException(String message) { super(message); }
    }
}
