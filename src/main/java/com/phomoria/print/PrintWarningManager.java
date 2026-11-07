package com.phomoria.print;

import com.phomoria.debug.DebugLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Persists a pending "not printed" notice so it can be shown on the next
 * Start screen, including after an unexpected application restart.
 */
public final class PrintWarningManager {
    private static final Path ROOT = Path.of(
            System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
            "Phomoria");
    private static final Path FILE = ROOT.resolve("pending-print-warning.txt");
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private PrintWarningManager() {}

    public static synchronized void beginSession(String sessionId, long timestamp) {
        if (sessionId == null || sessionId.isBlank()) return;
        try {
            Files.createDirectories(ROOT);
            Files.writeString(FILE, sessionId + "\n" + timestamp, StandardCharsets.UTF_8);
            DebugLog.info("Pending print warning registered for session=" + sessionId);
        } catch (IOException ex) {
            DebugLog.error("Unable to save pending print warning.", ex);
        }
    }

    public static synchronized void markPrinted(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        PendingWarning pending = read();
        if (pending != null && pending.sessionId.equals(sessionId)) {
            try {
                Files.deleteIfExists(FILE);
                DebugLog.info("Pending print warning cleared after successful print. session=" + sessionId);
            } catch (IOException ex) {
                DebugLog.error("Unable to clear pending print warning.", ex);
            }
        }
    }

    public static synchronized PendingWarning consumeUnprinted() {
        PendingWarning pending = read();
        if (pending == null) return null;
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException ex) {
            DebugLog.error("Unable to consume pending print warning.", ex);
        }
        return pending;
    }

    private static PendingWarning read() {
        try {
            if (!Files.isRegularFile(FILE)) return null;
            String[] lines = Files.readString(FILE, StandardCharsets.UTF_8).split("\\R");
            if (lines.length < 2 || lines[0].isBlank()) return null;
            long timestamp = Long.parseLong(lines[1].trim());
            return new PendingWarning(lines[0].trim(), timestamp);
        } catch (Exception ex) {
            DebugLog.error("Unable to read pending print warning.", ex);
            return null;
        }
    }

    public static final class PendingWarning {
        private final String sessionId;
        private final long timestamp;

        private PendingWarning(String sessionId, long timestamp) {
            this.sessionId = sessionId;
            this.timestamp = timestamp;
        }

        public String getFormattedTime() {
            return FORMAT.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()));
        }
    }
}
