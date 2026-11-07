package com.phomoria.cloud;

import com.phomoria.debug.DebugLog;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stores the device-specific bcrypt verifier synced from Phomoria Cloud.
 * The operator's password is never stored locally in plaintext.
 */
public final class DevicePasswordManager {
    private static final Path ROOT = Path.of(
            System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
            "Phomoria");
    private static final Path FILE = ROOT.resolve("device-password.hash");
    private static String passwordHash;
    private static boolean loaded;

    private DevicePasswordManager() {}

    public static synchronized void syncFromCloudHash(String cloudHash) {
        String normalized = cloudHash == null ? "" : cloudHash.trim();
        if (!normalized.isEmpty() && !isBcryptHash(normalized)) {
            DebugLog.warn("Device password sync returned an unsupported hash; keeping the previous local password.");
            return;
        }
        passwordHash = normalized;
        loaded = true;
        try {
            Files.createDirectories(ROOT);
            if (normalized.isEmpty()) {
                Files.deleteIfExists(FILE);
            } else {
                Files.writeString(FILE, normalized, StandardCharsets.UTF_8);
            }
            DebugLog.info(normalized.isEmpty()
                    ? "Device password cleared by cloud configuration."
                    : "Device password configuration synchronized.");
        } catch (IOException ex) {
            DebugLog.error("Failed to persist device password configuration.", ex);
        }
    }

    public static synchronized boolean isPasswordConfigured() {
        loadIfNeeded();
        return passwordHash != null && !passwordHash.isBlank();
    }

    /**
     * Empty input is valid only when the cloud-synchronized device password
     * is not configured. This same rule protects Settings, Close and Minimize.
     */
    public static synchronized boolean verify(char[] candidate) {
        loadIfNeeded();
        char[] value = candidate == null ? new char[0] : candidate;
        if (passwordHash == null || passwordHash.isBlank()) {
            return value.length == 0;
        }
        if (value.length == 0) return false;
        String hash = passwordHash.startsWith("$2y$")
                ? "$2a$" + passwordHash.substring(4)
                : passwordHash;
        try {
            return BCrypt.checkpw(new String(value), hash);
        } catch (IllegalArgumentException ex) {
            DebugLog.error("Local device password hash is invalid.", ex);
            return false;
        }
    }

    private static void loadIfNeeded() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.isRegularFile(FILE)) {
                String stored = Files.readString(FILE, StandardCharsets.UTF_8).trim();
                if (stored.isEmpty() || isBcryptHash(stored)) {
                    passwordHash = stored;
                } else {
                    passwordHash = "";
                    DebugLog.warn("Ignoring malformed local device password hash.");
                }
            } else {
                passwordHash = "";
            }
        } catch (IOException ex) {
            // Do not invent a password state if the local config cannot be read.
            passwordHash = "";
            DebugLog.error("Failed to read local device password configuration.", ex);
        }
    }

    private static boolean isBcryptHash(String value) {
        return value != null && value.matches("^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$");
    }
}
