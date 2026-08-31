package com.phomoria.cloud;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

public final class AuthService {
    private final Gson gson = new Gson();
    private String lastError = "Login gagal.";

    public String getLastError() {
        return lastError;
    }

    public boolean login(String server, String email, String password) throws Exception {
        String cleanServer = server.replaceAll("/+$", "");
        String url = cleanServer + "/api/auth/login";

        JsonObject request = new JsonObject();
        request.addProperty("email", email);
        request.addProperty("password", password);

        HttpPost post = new HttpPost(url);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(
                gson.toJson(request),
                ContentType.APPLICATION_JSON
        ));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {

            String body = response.getEntity() == null
                    ? ""
                    : EntityUtils.toString(response.getEntity());

            if (response.getCode() < 200 || response.getCode() >= 300) {
                lastError = body.isBlank()
                        ? "Login ditolak. HTTP " + response.getCode()
                        : body;
                return false;
            }

            JsonObject json = gson.fromJson(body, JsonObject.class);
            String token = "";

            if (json != null && json.has("data")) {
                JsonObject data = json.getAsJsonObject("data");
                if (data.has("token")) token = data.get("token").getAsString();

                CloudConfig config = CloudConfigManager.getConfig();
                config.setServer(cleanServer);
                config.setToken(token);

                if (data.has("user") && data.get("user").isJsonObject()) {
                    JsonObject user = data.getAsJsonObject("user");
                    if (user.has("name")) config.setUserName(user.get("name").getAsString());
                    if (user.has("email")) config.setEmail(user.get("email").getAsString());
                }
                CloudConfigManager.save();
            }

            if (token.isBlank()) {
                lastError = "Login berhasil tetapi token tidak ditemukan pada response API.";
                return false;
            }

            lastError = "";
            return true;
        }
    }
}
