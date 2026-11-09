package com.phomoria.debug;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DebugLog {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE;

    private static final int MAX_HISTORY = 5000;

    private static final Deque<String> HISTORY =
            new ArrayDeque<>();

    /*
     * File writes run on a dedicated daemon thread so disk I/O does not
     * block the application or Swing event-dispatch thread.
     */
    private static final ExecutorService FILE_WRITER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Phomoria-Log-Writer");
                thread.setDaemon(true);
                return thread;
            });

    private static final Path LOG_DIRECTORY = resolveLogDirectory();

    private static DebugConsoleDialog console;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            FILE_WRITER.shutdown();
            try {
                if (!FILE_WRITER.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    System.err.println("[Phomoria] Log writer did not finish before shutdown.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Phomoria-Log-Shutdown"));
    }

    private DebugLog() {}

    /** Starts a measured operation. Always close the returned scope in a finally/try-with-resources block. */
    public static Operation operation(String name, String context) {
        return new Operation(name, context);
    }

    public static final class Operation implements AutoCloseable {
        private final String name;
        private final long startedNanos = System.nanoTime();
        private final String context;
        private boolean failed;
        private boolean closed;

        private Operation(String name, String context) {
            this.name = name == null ? "unknown" : name;
            this.context = context == null || context.isBlank() ? "" : ", " + context;
            info("OPERATION START name=" + this.name + this.context
                    + ", thread=" + Thread.currentThread().getName());
        }

        public void fail(Throwable error) {
            failed = true;
            DebugLog.error("OPERATION FAILED name=" + name + this.context
                    + ", elapsedMs=" + elapsedMs()
                    + ", thread=" + Thread.currentThread().getName(), error);
        }

        public long elapsedMs() {
            return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            info("OPERATION " + (failed ? "FINISHED_WITH_ERROR" : "END")
                    + " name=" + name + this.context
                    + ", elapsedMs=" + elapsedMs()
                    + ", thread=" + Thread.currentThread().getName());
        }
    }

    public static void info(String message) {
        write("INFO", message);
    }

    public static void warn(String message) {
        write("WARN", message);
    }

    public static void error(String message, Throwable error) {
        write("ERROR", message, error);
    }

    public static void error(String message) {
        write("ERROR", message);
    }

    private static Path resolveLogDirectory() {
        String appData = System.getenv("APPDATA");

        if (appData != null && !appData.isBlank()) {
            return Paths.get(appData, "Phomoria", "logs");
        }

        // Fallback for environments where APPDATA is not defined.
        String userHome = System.getProperty("user.home", ".");
        return Paths.get(userHome, "AppData", "Roaming", "Phomoria", "logs");
    }

    private static synchronized void write(String level, String message) {
        write(level, message, null);
    }

    private static synchronized void write(
            String level,
            String message,
            Throwable error
    ) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate logDate = now.toLocalDate();

        String line = "[" + now.format(FORMAT) + "] [" + level + "] " + message;
        publishLine(line, logDate);

        if (error != null) {
            StringWriter stackTraceBuffer = new StringWriter();
            error.printStackTrace(new PrintWriter(stackTraceBuffer));

            String[] stackTraceLines = stackTraceBuffer.toString().split("\\R");
            for (String stackTraceLine : stackTraceLines) {
                if (!stackTraceLine.isBlank()) {
                    publishLine("    " + stackTraceLine, logDate);
                }
            }
        }
    }

    private static void publishLine(String line, LocalDate logDate) {
        HISTORY.addLast(line);

        while (HISTORY.size() > MAX_HISTORY) {
            HISTORY.removeFirst();
        }

        System.out.println(line);
        System.out.flush();

        appendToFileAsync(line, logDate);

        if (console != null) {
            console.append(line);
        }
    }

    private static void appendToFileAsync(String line, LocalDate logDate) {
        FILE_WRITER.execute(() -> {
            try {
                Files.createDirectories(LOG_DIRECTORY);

                String fileName = "phomoria-"
                        + logDate.format(FILE_DATE_FORMAT)
                        + ".log";
                Path logFile = LOG_DIRECTORY.resolve(fileName);

                Files.writeString(
                        logFile,
                        line + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND
                );
            } catch (Exception e) {
                // Do not call DebugLog here: that would cause a logging loop.
                System.err.println("[Phomoria] Failed to write application log: " + e);
            }
        });
    }

    /**
     * Connects DebugLog to the debug console and sends the current in-memory
     * history to the newly opened console.
     */
    public static synchronized void attachConsole(DebugConsoleDialog dialog) {
        console = dialog;

        if (console == null) {
            return;
        }

        List<String> history = new ArrayList<>(HISTORY);
        for (String line : history) {
            console.append(line);
        }
    }

    /**
     * Detaches the debug console.
     */
    public static synchronized void detachConsole(DebugConsoleDialog dialog) {
        if (console == dialog) {
            console = null;
        }
    }

    /**
     * Returns a copy of the current in-memory history.
     */
    public static synchronized List<String> getHistory() {
        return new ArrayList<>(HISTORY);
    }

    /**
     * Clears only the in-memory history. Existing daily log files are kept.
     */
    public static synchronized void clearHistory() {
        HISTORY.clear();
    }
}
