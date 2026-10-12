package com.phomoria.payment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;

/** Direct temporary bridge to the legacy MySQL device/transactions tables and Midtrans. */
public final class LegacyPaymentService {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12)).build();
    private final String jdbcUrl, dbUser, dbPassword;
    private final boolean sandbox;

    public record Payment(String orderId, int amount, String deviceName, String qrUrl) {}

    public LegacyPaymentService() throws Exception {
        Properties p = new Properties();
        Path cfg = Path.of(System.getProperty("user.home"), ".phomoria", "payment-db.properties");
        if (Files.isRegularFile(cfg)) try (var in = Files.newInputStream(cfg)) { p.load(in); }
        String host = value(p, "db.host", "PHOMORIA_DB_HOST", "");
        String port = value(p, "db.port", "PHOMORIA_DB_PORT", "3306");
        String name = value(p, "db.name", "PHOMORIA_DB_NAME", "u559092880_fbox_db");
        dbUser = value(p, "db.user", "PHOMORIA_DB_USER", "");
        dbPassword = value(p, "db.password", "PHOMORIA_DB_PASSWORD", "");
        if (host.isBlank() || dbUser.isBlank() || dbPassword.isBlank())
            throw new IllegalStateException("Konfigurasi database belum lengkap. Isi ~/.phomoria/payment-db.properties.");
        jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + name + "?sslMode=PREFERRED&serverTimezone=Asia/Jakarta&connectTimeout=10000&socketTimeout=15000";
        sandbox = Boolean.parseBoolean(value(p, "midtrans.sandbox", "PHOMORIA_MIDTRANS_SANDBOX", "false"));
    }

    private static String value(Properties p, String key, String env, String fallback) {
        String v = System.getenv(env);
        if (v != null && !v.isBlank()) return v.trim();
        v = p.getProperty(key);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    private Connection db() throws SQLException { return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword); }

    private String computerName() throws Exception {
        String n = System.getenv("COMPUTERNAME");
        if (n == null || n.isBlank()) n = InetAddress.getLocalHost().getHostName();
        return n.trim();
    }

    public Payment createPayment() throws Exception {
        String device = computerName();
        int amount;
        String serverKey;
        try (Connection c = db(); PreparedStatement ps = c.prepareStatement(
                "SELECT harga, serial_key FROM device WHERE name_device = ? ORDER BY id_device DESC LIMIT 1")) {
            ps.setString(1, device);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Perangkat " + device + " tidak terdaftar pada tabel device.");
                amount = rs.getInt("harga");
                serverKey = rs.getString("serial_key");
            }
        }
        if (amount < 1) throw new IllegalStateException("Harga perangkat tidak valid: " + amount);
        if (serverKey == null || serverKey.isBlank()) throw new IllegalStateException("Midtrans Server Key perangkat kosong.");

        String orderId = "trx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 13);
        JsonObject details = new JsonObject();
        details.addProperty("order_id", orderId);
        details.addProperty("gross_amount", amount);
        JsonObject payload = new JsonObject();
        payload.add("transaction_details", details);
        payload.addProperty("payment_type", "gopay");
        JsonObject customer = new JsonObject();
        customer.addProperty("first_name", device);
        payload.add("customer_details", customer);

        JsonObject response = midtrans(serverKey, "/v2/charge", "POST", payload);
        String qrUrl = null;
        JsonArray actions = response.has("actions") && response.get("actions").isJsonArray()
                ? response.getAsJsonArray("actions") : new JsonArray();
        for (var el : actions) {
            JsonObject action = el.getAsJsonObject();
            if (action.has("name") && "generate-qr-code".equals(action.get("name").getAsString())) {
                qrUrl = action.get("url").getAsString(); break;
            }
        }
        if (qrUrl == null) throw new IOException("Midtrans tidak mengembalikan URL QR. Respons: " + response);
        try (Connection c = db(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO transactions (device_name, order_id, status, amount) VALUES (?, ?, 'pending', ?)")) {
            ps.setString(1, device); ps.setString(2, orderId); ps.setInt(3, amount); ps.executeUpdate();
        }
        return new Payment(orderId, amount, device, qrUrl);
    }

    public String checkStatus(String orderId) throws Exception {
        String key = getServerKey(computerName());
        JsonObject status = midtrans(key, "/v2/" + orderId + "/status", "GET", null);
        String state = status.has("transaction_status") ? status.get("transaction_status").getAsString() : "unknown";
        updateLocalStatus(orderId, state);
        return state;
    }

    public void cancel(String orderId) throws Exception {
        String state = checkStatus(orderId);
        if ("settlement".equalsIgnoreCase(state) || "capture".equalsIgnoreCase(state)) return;
        String key = getServerKey(computerName());
        midtrans(key, "/v2/" + orderId + "/cancel", "POST", new JsonObject());
        updateLocalStatus(orderId, "cancel");
    }

    private String getServerKey(String device) throws Exception {
        try (Connection c = db(); PreparedStatement ps = c.prepareStatement(
                "SELECT serial_key FROM device WHERE name_device = ? ORDER BY id_device DESC LIMIT 1")) {
            ps.setString(1, device);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Data perangkat tidak ditemukan.");
                return rs.getString(1);
            }
        }
    }

    private void updateLocalStatus(String orderId, String state) throws SQLException {
        try (Connection c = db(); PreparedStatement ps = c.prepareStatement(
                "UPDATE transactions SET status = ? WHERE order_id = ?")) {
            ps.setString(1, state); ps.setString(2, orderId); ps.executeUpdate();
        }
    }

    private JsonObject midtrans(String serverKey, String path, String method, JsonObject body) throws Exception {
        String base = sandbox ? "https://api.sandbox.midtrans.com" : "https://api.midtrans.com";
        String auth = Base64.getEncoder().encodeToString((serverKey + ":").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(25)).header("Authorization", "Basic " + auth)
                .header("Accept", "application/json");
        if ("GET".equals(method)) b.GET();
        else b.header("Content-Type", "application/json; charset=utf-8")
                .method(method, HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body.toString()));
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonObject json;
        try { json = JsonParser.parseString(r.body()).getAsJsonObject(); }
        catch (RuntimeException ex) { throw new IOException("Respons Midtrans tidak valid (HTTP " + r.statusCode() + ")."); }
        if (r.statusCode() < 200 || r.statusCode() >= 300) {
            String message = json.has("status_message") ? json.get("status_message").getAsString() : r.body();
            throw new IOException("Midtrans HTTP " + r.statusCode() + ": " + message);
        }
        return json;
    }
}
