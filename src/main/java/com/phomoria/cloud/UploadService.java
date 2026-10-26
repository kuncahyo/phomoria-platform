package com.phomoria.cloud;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UploadService {
    private final Gson gson = new Gson();

    public String upload(String frameName, File sessionFolder) throws Exception {
        return upload(frameName, sessionFolder, UUID.randomUUID().toString()).getGalleryUrl();
    }

    /** Compatibility method: reserve first, then upload every manifest asset. */
    public UploadResult upload(String frameName, File sessionFolder, String clientSessionId) throws Exception {
        if (clientSessionId == null || clientSessionId.isBlank()) {
            clientSessionId = UUID.randomUUID().toString();
        }
        UploadResult reservation = reserveSession(frameName, sessionFolder, clientSessionId);
        for (AssetFile asset : getAssets(sessionFolder)) {
            uploadAsset(clientSessionId, asset.getKey(), asset.getFile());
        }
        return reservation;
    }

    public UploadResult reserveSession(String frameName, File sessionFolder, String clientSessionId) throws Exception {
        CloudConfig config = CloudConfigManager.getConfig();
        DeviceInfo device = DeviceManager.getDevice();
        if (config.getToken().isBlank()) throw new IllegalStateException("Belum login ke API.");
        if (clientSessionId == null || clientSessionId.isBlank()) {
            throw new IllegalArgumentException("Client session ID kosong.");
        }

        List<AssetFile> assets = getAssets(sessionFolder);
        if (assets.isEmpty() || assets.stream().noneMatch(a -> "result".equals(a.getType()))) {
            throw new IllegalArgumentException("result.png tidak ditemukan atau kosong.");
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("device_uuid", device.getUuid());
        payload.addProperty("client_session_id", clientSessionId);
        payload.addProperty("frame_name", frameName == null ? "" : frameName);
        JsonArray manifest = new JsonArray();
        for (AssetFile asset : assets) {
            JsonObject item = new JsonObject();
            item.addProperty("asset_key", asset.getKey());
            item.addProperty("asset_type", asset.getType());
            item.addProperty("filename", asset.getFile().getName());
            item.addProperty("sort_order", asset.getSortOrder());
            item.addProperty("size", asset.getFile().length());
            manifest.add(item);
        }
        payload.add("assets", manifest);

        HttpPost post = new HttpPost(apiUrl(config, "/api/session/reserve"));
        post.setHeader("Authorization", "Bearer " + config.getToken());
        post.setHeader("Accept", "application/json");
        post.setEntity(new StringEntity(gson.toJson(payload), ContentType.APPLICATION_JSON));
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
            if (response.getCode() < 200 || response.getCode() >= 300) {
                throw new IllegalStateException("Reservasi sesi HTTP " + response.getCode() + ": " + body);
            }
            JsonObject json = gson.fromJson(body, JsonObject.class);
            if (json == null || !json.has("success") || !json.get("success").getAsBoolean()
                    || !json.has("session")) {
                throw new IllegalStateException("Response reservasi tidak valid: " + body);
            }
            JsonObject session = json.getAsJsonObject("session");
            if (!session.has("gallery_url") || session.get("gallery_url").getAsString().isBlank()) {
                throw new IllegalStateException("Response reservasi tidak memiliki gallery_url: " + body);
            }
            return new UploadResult(
                    session.get("gallery_url").getAsString(),
                    session.has("session_code") ? session.get("session_code").getAsString() : "",
                    session.has("id") ? session.get("id").getAsString() : ""
            );
        }
    }

    /** Upload exactly one manifest asset. Repeating the same key is safe. */
    public boolean uploadAsset(String clientSessionId, String assetKey, File file) throws Exception {
        CloudConfig config = CloudConfigManager.getConfig();
        DeviceInfo device = DeviceManager.getDevice();
        if (config.getToken().isBlank()) throw new IllegalStateException("Belum login ke API.");
        if (file == null || !file.isFile() || file.length() <= 0) {
            throw new IllegalArgumentException("File aset tidak ditemukan atau kosong: " + assetKey);
        }

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("device_uuid", device.getUuid(), ContentType.TEXT_PLAIN);
        builder.addTextBody("client_session_id", clientSessionId, ContentType.TEXT_PLAIN);
        builder.addTextBody("asset_key", assetKey, ContentType.TEXT_PLAIN);
        builder.addBinaryBody("file", file, ContentType.DEFAULT_BINARY, file.getName());

        HttpPost post = new HttpPost(apiUrl(config, "/api/session/asset"));
        post.setHeader("Authorization", "Bearer " + config.getToken());
        post.setHeader("Accept", "application/json");
        post.setEntity(builder.build());
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
            if (response.getCode() < 200 || response.getCode() >= 300) {
                throw new IllegalStateException("Upload aset " + assetKey + " HTTP "
                        + response.getCode() + ": " + body);
            }
            JsonObject json = gson.fromJson(body, JsonObject.class);
            if (json == null || !json.has("success") || !json.get("success").getAsBoolean()) {
                throw new IllegalStateException("Response upload aset tidak valid: " + body);
            }
            return json.has("already_uploaded") && json.get("already_uploaded").getAsBoolean();
        }
    }

    /** Read authoritative server-side session state. */
    public String fetchSessionStatus(String sessionCode) throws Exception {
        CloudConfig config = CloudConfigManager.getConfig();
        HttpGet get = new HttpGet(apiUrl(config, "/gallery/" + sessionCode + "/status"));
        get.setHeader("Accept", "application/json");
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(get)) {
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
            if (response.getCode() < 200 || response.getCode() >= 300) {
                throw new IllegalStateException("Status sesi HTTP " + response.getCode() + ": " + body);
            }
            JsonObject json = gson.fromJson(body, JsonObject.class);
            return json != null && json.has("status") ? json.get("status").getAsString() : "";
        }
    }

    public List<AssetFile> getAssets(File sessionFolder) {
        List<AssetFile> assets = new ArrayList<>();
        addIfPresent(assets, sessionFolder, "result.png", "result", "result", 0);
        addIfPresent(assets, sessionFolder, "animation.gif", "gif", "gif", 9000);
        addIfPresent(assets, sessionFolder, "live_photo.mp4", "live_photo", "live_photo", 10000);

        File[] files = sessionFolder == null ? null : sessionFolder.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            Pattern pattern = Pattern.compile("photo_(\\d+)\\.jpg", Pattern.CASE_INSENSITIVE);
            for (File file : files) {
                if (!file.isFile() || file.length() <= 0) continue;
                Matcher matcher = pattern.matcher(file.getName());
                if (matcher.matches()) {
                    int number = Integer.parseInt(matcher.group(1));
                    assets.add(new AssetFile("photo_" + number, "photo", number, file));
                }
            }
        }
        return assets;
    }

    private void addIfPresent(List<AssetFile> assets, File folder, String filename,
                              String key, String type, int order) {
        if (folder == null) return;
        File file = new File(folder, filename);
        if (file.isFile() && file.length() > 0) {
            assets.add(new AssetFile(key, type, order, file));
        }
    }

    private String apiUrl(CloudConfig config, String path) {
        return config.getServer().replaceAll("/+$", "") + path;
    }

    public static final class AssetFile {
        private final String key;
        private final String type;
        private final int sortOrder;
        private final File file;
        public AssetFile(String key, String type, int sortOrder, File file) {
            this.key = key;
            this.type = type;
            this.sortOrder = sortOrder;
            this.file = file;
        }
        public String getKey() { return key; }
        public String getType() { return type; }
        public int getSortOrder() { return sortOrder; }
        public File getFile() { return file; }
    }

    public static final class UploadResult {
        private final String galleryUrl, sessionCode, serverId;
        public UploadResult(String galleryUrl, String sessionCode, String serverId) {
            this.galleryUrl = galleryUrl == null ? "" : galleryUrl;
            this.sessionCode = sessionCode == null ? "" : sessionCode;
            this.serverId = serverId == null ? "" : serverId;
        }
        public String getGalleryUrl() { return galleryUrl; }
        public String getSessionCode() { return sessionCode; }
        public String getServerId() { return serverId; }
    }
}
