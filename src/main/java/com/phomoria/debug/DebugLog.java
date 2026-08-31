package com.phomoria.debug;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DebugLog {
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static DebugConsoleDialog console;

    private DebugLog() {}

    public static void info(String message) {
        write("INFO", message);
    }

    public static void warn(String message) {
        write("WARN", message);
    }

    public static void error(String message, Throwable error) {
        write("ERROR", message + (error == null ? "" : " | " + error));
        if (error != null) error.printStackTrace(System.out);
    }

    public static void error(String message) {
        write("ERROR", message);
    }

    private static synchronized void write(String level, String message) {
        String line = "[" + LocalDateTime.now().format(FORMAT) + "] ["
                + level + "] " + message;

        System.out.println(line);
        System.out.flush();

        if (console != null) {
            console.append(line);
        }
    }

    public static void attachConsole(DebugConsoleDialog dialog) {
        console = dialog;
    }

    public static void detachConsole(DebugConsoleDialog dialog) {
        if (console == dialog) console = null;
    }
}
