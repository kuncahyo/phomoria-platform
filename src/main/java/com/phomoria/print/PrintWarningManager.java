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
    private static final long STALE_PRINTING_MILLIS = 120_000L;
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private PrintWarningManager() {}

    public static synchronized void beginSession(String sessionId, long timestamp) {
        if (sessionId == null || sessionId.isBlank()) return;
        write(sessionId, timestamp, "PENDING", System.currentTimeMillis());
        DebugLog.info("Pending print warning registered for session=" + sessionId);
    }

    public static synchronized void markPrinting(String sessionId) {
        updateStatus(sessionId, "PRINTING");
    }

    public static synchronized void markPrintFailed(String sessionId) {
        updateStatus(sessionId, "PENDING");
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

    /**
     * While a print job is still running, let the Start screen wait instead of
     * displaying a premature warning. A stale PRINTING state is treated as a
     * failed/abandoned attempt after two minutes (for example after a crash).
     */
    public static synchronized boolean isReadyToShow() {
        PendingWarning pending = read();
        if (pending == null) return true;
        return !"PRINTING".equals(pending.status)
                || System.currentTimeMillis() - pending.statusChangedAt >= STALE_PRINTING_MILLIS;
    }

    public static synchronized PendingWarning consumeUnprinted() {
        if (!isReadyToShow()) return null;
        PendingWarning pending = read();
        if (pending == null) return null;
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException ex) {
            DebugLog.error("Unable to consume pending print warning.", ex);
        }
        return pending;
    }

    private static void updateStatus(String sessionId, String status) {
        if (sessionId == null || sessionId.isBlank()) return;
        PendingWarning pending = read();
        if (pending != null && pending.sessionId.equals(sessionId)) {
            write(pending.sessionId, pending.timestamp, status, System.currentTimeMillis());
        }
    }

    private static void write(String sessionId, long timestamp, String status, long statusChangedAt) {
        try {
            Files.createDirectories(ROOT);
            Files.writeString(FILE, sessionId + "\n" + timestamp + "\n" + status + "\n" + statusChangedAt,
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            DebugLog.error("Unable to save pending print warning.", ex);
        }
    }

    private static PendingWarning read() {
        try {
            if (!Files.isRegularFile(FILE)) return null;
            String[] lines = Files.readString(FILE, StandardCharsets.UTF_8).split("\\R");
            if (lines.length < 2 || lines[0].isBlank()) return null;
            long timestamp = Long.parseLong(lines[1].trim());
            String status = lines.length >= 3 ? lines[2].trim() : "PENDING";
            long statusChangedAt = lines.length >= 4 ? Long.parseLong(lines[3].trim()) : timestamp;
            return new PendingWarning(lines[0].trim(), timestamp, status, statusChangedAt);
        } catch (Exception ex) {
            DebugLog.error("Unable to read pending print warning.", ex);
            return null;
        }
    }

    public static final class PendingWarning {
        private final String sessionId;
        private final long timestamp;
        private final String status;
        private final long statusChangedAt;

        private PendingWarning(String sessionId, long timestamp, String status, long statusChangedAt) {
            this.sessionId = sessionId;
            this.timestamp = timestamp;
            this.status = status;
            this.statusChangedAt = statusChangedAt;
        }

        public String getFormattedTime() {
            return FORMAT.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()));
        }
    }
}
