package com.phomoria.debug;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class DebugLog {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final int MAX_HISTORY = 5000;

    private static final Deque<String> HISTORY =
            new ArrayDeque<>();

    private static DebugConsoleDialog console;

    private DebugLog() {}

    public static void info(String message) {
        write("INFO", message);
    }

    public static void warn(String message) {
        write("WARN", message);
    }

    public static void error(String message, Throwable error) {
        write(
                "ERROR",
                message + (error == null ? "" : " | " + error)
        );

        if (error != null) {
            error.printStackTrace(System.out);
        }
    }

    public static void error(String message) {
        write("ERROR", message);
    }

    private static synchronized void write(
            String level,
            String message
    ) {

        String line =
                "["
                + LocalDateTime.now().format(FORMAT)
                + "] ["
                + level
                + "] "
                + message;

        // ==========================================
        // 1. SELALU SIMPAN KE HISTORY
        // ==========================================

        HISTORY.addLast(line);

        while (HISTORY.size() > MAX_HISTORY) {
            HISTORY.removeFirst();
        }

        // ==========================================
        // 2. TETAP KIRIM KE SYSTEM.OUT
        // ==========================================

        System.out.println(line);
        System.out.flush();

        // ==========================================
        // 3. JIKA DEBUG CONSOLE TERBUKA
        //    TAMPILKAN REALTIME
        // ==========================================

        if (console != null) {
            console.append(line);
        }
    }

    /**
     * Menghubungkan DebugLog dengan debug console.
     *
     * Ketika console dibuka, seluruh history yang
     * sudah tercatat sebelumnya langsung dikirim.
     */
    public static synchronized void attachConsole(
            DebugConsoleDialog dialog
    ) {

        console = dialog;

        if (console == null) {
            return;
        }

        // Kirim seluruh history lama
        List<String> history =
                new ArrayList<>(HISTORY);

        for (String line : history) {
            console.append(line);
        }
    }

    /**
     * Melepaskan debug console.
     */
    public static synchronized void detachConsole(
            DebugConsoleDialog dialog
    ) {

        if (console == dialog) {
            console = null;
        }
    }

    /**
     * Mengambil salinan seluruh history.
     *
     * Berguna jika nantinya kita ingin membuat
     * export log atau penyimpanan ke file.
     */
    public static synchronized List<String> getHistory() {
        return new ArrayList<>(HISTORY);
    }

    /**
     * Menghapus history.
     */
    public static synchronized void clearHistory() {
        HISTORY.clear();
    }
}