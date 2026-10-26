package com.phomoria.cloud;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

public final class UploadService {
    private final Gson gson = new Gson();

    public String upload(String frameName, File sessionFolder) throws Exception {
        return upload(frameName, sessionFolder, null).getGalleryUrl();
    }

    public UploadResult upload(String frameName, File sessionFolder, String clientSessionId) throws Exception {
        CloudConfig config = CloudConfigManager.getConfig();
        DeviceInfo device = DeviceManager.getDevice();
        if (config.getToken().isBlank()) throw new IllegalStateException("Belum login ke API.");

        File result = new File(sessionFolder, "result.png");
        if (!result.isFile() || result.length() <= 0) throw new IllegalArgumentException("result.png tidak ditemukan atau kosong.");

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("device_uuid", device.getUuid(), ContentType.TEXT_PLAIN);
        builder.addTextBody("frame_name", frameName == null ? "" : frameName, ContentType.TEXT_PLAIN);
        if (clientSessionId != null && !clientSessionId.isBlank()) {
            builder.addTextBody("client_session_id", clientSessionId, ContentType.TEXT_PLAIN);
        }
        builder.addBinaryBody("result", result, ContentType.create("image/png"), result.getName());
        addOptional(builder, new File(sessionFolder, "animation.gif"), "gif", "image/gif");
        addOptional(builder, new File(sessionFolder, "live_photo.mp4"), "live_photo", "video/mp4");

        File[] files = sessionFolder.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            int index = 1;
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).matches("photo_\\d+\\.jpg")) {
                    builder.addBinaryBody("photo" + index++, file, ContentType.DEFAULT_BINARY, file.getName());
                }
            }
        }

        HttpPost post = new HttpPost(config.getServer().replaceAll("/+$", "") + "/api/session/upload");
        post.setHeader("Authorization", "Bearer " + config.getToken());
        post.setEntity(builder.build());
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
            if (response.getCode() < 200 || response.getCode() >= 300) {
                throw new IllegalStateException("Upload HTTP " + response.getCode() + ": " + body);
            }
            JsonObject json = gson.fromJson(body, JsonObject.class);
            if (json == null || !json.has("session") || !json.getAsJsonObject("session").has("gallery_url")) {
                throw new IllegalStateException("Response upload tidak memiliki session.gallery_url: " + body);
            }
            JsonObject session = json.getAsJsonObject("session");
            return new UploadResult(
                    session.get("gallery_url").getAsString(),
                    session.has("session_code") ? session.get("session_code").getAsString() : "",
                    session.has("id") ? session.get("id").getAsString() : ""
            );
        }
    }

    private void addOptional(MultipartEntityBuilder builder, File file, String field, String mime) {
        if (file.isFile() && file.length() > 0) builder.addBinaryBody(field, file, ContentType.create(mime), file.getName());
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
