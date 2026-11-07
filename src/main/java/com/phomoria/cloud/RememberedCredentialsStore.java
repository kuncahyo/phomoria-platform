package com.phomoria.cloud;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.platform.win32.Crypt32Util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Stores Remember Login credentials protected with the current Windows user's
 * DPAPI key. The password is never written as plain text.
 */
public final class RememberedCredentialsStore {
    private static final Gson GSON = new Gson();

    private static final File ROOT = new File(
            System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
            "Phomoria"
    );
    private static final File FILE = new File(ROOT, "remembered-login.bin");

    private RememberedCredentialsStore() {
    }

    public static synchronized Credentials load() throws IOException {
        if (!FILE.isFile()) {
            return null;
        }
        requireWindows();

        byte[] encrypted = Files.readAllBytes(FILE.toPath());
        byte[] plain = null;
        try {
            plain = Crypt32Util.cryptUnprotectData(encrypted);
            JsonObject json = JsonParser.parseString(
                    new String(plain, StandardCharsets.UTF_8)
            ).getAsJsonObject();

            String email = json.has("email") ? json.get("email").getAsString() : "";
            String password = json.has("password") ? json.get("password").getAsString() : "";
            if (email.isBlank() || password.isEmpty()) {
                return null;
            }
            return new Credentials(email, password);
        } catch (RuntimeException ex) {
            throw new IOException("Kredensial tersimpan tidak dapat dibuka oleh Windows DPAPI.", ex);
        } finally {
            Arrays.fill(encrypted, (byte) 0);
            if (plain != null) {
                Arrays.fill(plain, (byte) 0);
            }
        }
    }

    public static synchronized void save(String email, String password) throws IOException {
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Email dan password wajib tersedia.");
        }
        requireWindows();

        if (!ROOT.exists() && !ROOT.mkdirs() && !ROOT.isDirectory()) {
            throw new IOException("Tidak dapat membuat folder konfigurasi Phomoria.");
        }

        JsonObject json = new JsonObject();
        json.addProperty("email", email.trim());
        json.addProperty("password", password);

        byte[] plain = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = null;
        try {
            encrypted = Crypt32Util.cryptProtectData(plain);
            Files.write(FILE.toPath(), encrypted);
        } catch (RuntimeException ex) {
            throw new IOException("Windows gagal mengenkripsi kredensial Remember Login.", ex);
        } finally {
            Arrays.fill(plain, (byte) 0);
            if (encrypted != null) {
                Arrays.fill(encrypted, (byte) 0);
            }
        }
    }

    public static synchronized void clear() throws IOException {
        if (FILE.exists() && !Files.deleteIfExists(FILE.toPath())) {
            throw new IOException("File Remember Login tidak berhasil dihapus.");
        }
    }

    private static void requireWindows() throws IOException {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            throw new IOException("Remember Login aman saat ini memerlukan Windows DPAPI.");
        }
    }

    public record Credentials(String email, String password) {
    }
}
