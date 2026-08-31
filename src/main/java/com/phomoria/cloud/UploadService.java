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

public final class UploadService {
    private final Gson gson = new Gson();

    public String upload(String frameName, File sessionFolder) throws Exception {
        CloudConfig config = CloudConfigManager.getConfig();
        DeviceInfo device = DeviceManager.getDevice();

        if (config.getToken().isBlank()) {
            throw new IllegalStateException("Belum login ke API.");
        }

        File result = new File(sessionFolder, "result.png");
        if (!result.exists()) {
            throw new IllegalArgumentException("result.png tidak ditemukan.");
        }

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("device_uuid", device.getUuid(), ContentType.TEXT_PLAIN);
        builder.addTextBody("frame_name", frameName, ContentType.TEXT_PLAIN);
        builder.addBinaryBody("result", result, ContentType.DEFAULT_BINARY, result.getName());

        File[] files = sessionFolder.listFiles();
        int index = 1;
        if (files != null) {
            for (File file : files) {
                if (!file.isFile()) continue;
                String name = file.getName().toLowerCase();
                if (!name.startsWith("photo_")) continue;

                builder.addBinaryBody(
                        "photo" + index,
                        file,
                        ContentType.DEFAULT_BINARY,
                        file.getName()
                );
                index++;
            }
        }

        String url = config.getServer().replaceAll("/+$", "") + "/api/session/upload";
        HttpPost post = new HttpPost(url);
        post.setHeader("Authorization", "Bearer " + config.getToken());
        post.setEntity(builder.build());

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {

            String body = response.getEntity() == null
                    ? ""
                    : EntityUtils.toString(response.getEntity());

            if (response.getCode() < 200 || response.getCode() >= 300) {
                throw new IllegalStateException("Upload HTTP " + response.getCode() + ": " + body);
            }

            JsonObject json = gson.fromJson(body, JsonObject.class);
            if (json == null
                    || !json.has("session")
                    || !json.getAsJsonObject("session").has("gallery_url")) {
                throw new IllegalStateException("Response upload tidak memiliki session.gallery_url: " + body);
            }

            return json.getAsJsonObject("session")
                    .get("gallery_url")
                    .getAsString();
        }
    }
}
