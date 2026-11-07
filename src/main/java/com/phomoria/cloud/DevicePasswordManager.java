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
    private static final String LOCKED_INVALID_STATE = "!LOCKED_INVALID_STATE!";
    private static String passwordHash;
    private static boolean loaded;

    private DevicePasswordManager() {}

    public static synchronized void syncFromCloudHash(String cloudHash) {
        String normalized = cloudHash == null ? "" : cloudHash.trim();
        if (!normalized.isEmpty() && !isBcryptHash(normalized)) {
            DebugLog.warn("Device password sync returned an unsupported hash; keeping the previous local password.");
            return;
        }
        try {
            Files.createDirectories(ROOT);
            // An empty file explicitly represents a device with no configured password.
            Files.writeString(FILE, normalized, StandardCharsets.UTF_8);
            passwordHash = normalized;
            loaded = true;
            DebugLog.info(normalized.isEmpty()
                    ? "Device password cleared by cloud configuration."
                    : "Device password configuration synchronized.");
        } catch (IOException ex) {
            // Keep the previous in-memory and on-disk state if persistence fails.
            DebugLog.error("Failed to persist device password configuration; previous local state retained.", ex);
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
        if (value.length == 0 || !isBcryptHash(passwordHash)) return false;
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
            if (!Files.exists(FILE)) {
                passwordHash = "";
                return;
            }
            if (!Files.isRegularFile(FILE) || !Files.isReadable(FILE)) {
                passwordHash = LOCKED_INVALID_STATE;
                DebugLog.warn("Local device password file exists but cannot be read; access remains locked.");
                return;
            }

            String stored = Files.readString(FILE, StandardCharsets.UTF_8).trim();
            if (stored.isEmpty() || isBcryptHash(stored)) {
                passwordHash = stored;
            } else {
                passwordHash = LOCKED_INVALID_STATE;
                DebugLog.warn("Ignoring malformed local device password hash; access remains locked.");
            }
        } catch (IOException ex) {
            passwordHash = LOCKED_INVALID_STATE;
            DebugLog.error("Failed to read local device password configuration; access remains locked.", ex);
        }
    }

    private static boolean isBcryptHash(String value) {
        return value != null && value.matches("^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$");
    }
}
