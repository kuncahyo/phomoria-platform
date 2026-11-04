package com.phomoria.cloud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

public final class VoucherService {
    public record Authorization(String token, String expiresAt) {}

    public Authorization authorize(String code) throws Exception {
        CloudConfig config = CloudConfigManager.getConfig();
        DeviceInfo device = DeviceManager.getDevice();
        if (config.getToken() == null || config.getToken().isBlank()) {
            throw new IllegalStateException("Silakan login ke akun Phomoria terlebih dahulu.");
        }
        if (config.getServer() == null || config.getServer().isBlank()) {
            throw new IllegalStateException("Alamat server belum dikonfigurasi.");
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("code", code == null ? "" : code.trim());
        payload.addProperty("device_uuid", device.getUuid());

        String url = config.getServer().replaceAll("/+$", "") + "/api/voucher/authorize-session";
        HttpPost post = new HttpPost(url);
        post.setHeader("Accept", "application/json");
        post.setHeader("Authorization", "Bearer " + config.getToken());
        post.setEntity(new StringEntity(payload.toString(), ContentType.APPLICATION_JSON));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
            JsonObject json;
            try {
                json = JsonParser.parseString(body).getAsJsonObject();
            } catch (RuntimeException ex) {
                throw new IllegalStateException("Respons server tidak dapat dibaca (HTTP "
                        + response.getCode() + ").");
            }

            if (response.getCode() < 200 || response.getCode() >= 300
                    || !json.has("success") || !json.get("success").getAsBoolean()) {
                String message = json.has("message") ? json.get("message").getAsString()
                        : "Voucher tidak dapat digunakan.";
                throw new IllegalArgumentException(message);
            }

            if (!json.has("session_authorization_token")
                    || json.get("session_authorization_token").getAsString().isBlank()) {
                throw new IllegalStateException("Server tidak mengembalikan token otorisasi sesi.");
            }

            return new Authorization(
                    json.get("session_authorization_token").getAsString(),
                    json.has("expires_at") ? json.get("expires_at").getAsString() : ""
            );
        }
    }
}
