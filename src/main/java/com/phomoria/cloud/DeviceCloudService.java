package com.phomoria.cloud;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.phomoria.debug.DebugLog;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

public final class DeviceCloudService {
    private static final Gson GSON = new Gson();
    private final CloudConfig config;

    public DeviceCloudService() { this(CloudConfigManager.getConfig()); }

    public DeviceCloudService(CloudConfig config) {
        if (config == null) throw new IllegalArgumentException("CloudConfig tidak boleh null.");
        this.config = config;
    }

    public boolean register() throws Exception {
        DeviceInfo device = DeviceManager.getDevice();
        if (device == null || device.getUuid().isBlank()) {
            throw new IllegalStateException("Device UUID belum tersedia.");
        }

        String token = config.getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Token cloud belum tersedia. Silakan login terlebih dahulu.");
        }

        String url = config.getServer().replaceAll("/+$", "") + "/api/device/register";
        JsonObject request = new JsonObject();
        request.addProperty("device_uuid", device.getUuid());
        request.addProperty("computer_name", device.getComputerName());
        request.addProperty("windows_user", device.getWindowsUser());
        request.addProperty("operating_system", device.getOperatingSystem());
        request.addProperty("java_version", device.getJavaVersion());

        // hardcode sementara, wajib ubah
        String appVersion = "1.0.0";
        request.addProperty("app_version", appVersion);

        DebugLog.info("Registering device: uuid=" + device.getUuid() + ", computer=" + device.getComputerName());

        HttpPost post = new HttpPost(url);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-Type", "application/json");
        post.setHeader("Authorization", "Bearer " + token);
        post.setEntity(new StringEntity(GSON.toJson(request), ContentType.APPLICATION_JSON));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
            int status = response.getCode();
            if (status < 200 || status >= 300) {
                DebugLog.error("Device registration failed. HTTP " + status + ": " + body, null);
                throw new DeviceCloudException("Registrasi device gagal. HTTP " + status + (body.isBlank() ? "" : ": " + body));
            }
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            boolean success = json != null && json.has("success") && json.get("success").getAsBoolean();
            if (!success) throw new DeviceCloudException("Server menolak registrasi device: " + body);
            DebugLog.info("Device registration successful: uuid=" + device.getUuid());
            return true;
        }
    }

    public static final class DeviceCloudException extends RuntimeException {
        public DeviceCloudException(String message) { super(message); }
    }
}
