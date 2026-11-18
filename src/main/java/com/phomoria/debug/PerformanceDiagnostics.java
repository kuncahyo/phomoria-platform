package com.phomoria.debug;

import com.phomoria.app.AppContext;
import com.phomoria.config.AppSettings;
import com.phomoria.session.PhotoSession;
import com.phomoria.upload.UploadQueueEntry;
import com.phomoria.upload.UploadQueueManager;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight, opt-in snapshots taken only when a photo session ends.
 * No polling, forced GC, or additional worker thread is created here.
 *
 * Diagnostic messages are deliberately written as readable, labelled lines.
 * They still use DebugLog, so they remain in the same daily application log.
 */
public final class PerformanceDiagnostics {
    private PerformanceDiagnostics() {}

    public static synchronized boolean isEnabled() {
        AppSettings settings = AppContext.settings();
        if (settings == null || !settings.isPerformanceDiagnosticsEnabled()) return false;

        long expiry = settings.getPerformanceDiagnosticsEnabledAt()
                + TimeUnit.DAYS.toMillis(settings.getPerformanceDiagnosticsDurationDays());

        if (settings.getPerformanceDiagnosticsDurationDays() > 0
                && settings.getPerformanceDiagnosticsEnabledAt() > 0
                && System.currentTimeMillis() >= expiry) {
            settings.setPerformanceDiagnosticsEnabled(false);
            AppContext.saveSettings();
            DebugLog.info("Diagnostik performa dinonaktifkan otomatis karena masa aktif telah berakhir.");
            return false;
        }
        return true;
    }

    public static synchronized void recordSessionEnd(PhotoSession session, String reason) {
        if (session == null || !isEnabled()) return;

        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();

        long totalRam = -1L;
        long availableRam = -1L;
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            totalRam = os.getTotalMemorySize();
            availableRam = os.getFreeMemorySize();
        } catch (Throwable ignored) {
            // Keep diagnostics resilient on JVMs that do not expose extended OS metrics.
        }

        UploadQueueEntry upload = UploadQueueManager.find(session.getSessionId());
        int livePhotoClips = session.getLivePhotoClips().size();
        String livePhotoState = !AppContext.settings().isLivePhotoEnabled()
                ? "Nonaktif"
                : livePhotoClips > 0 ? "Klip tersimpan, menunggu proses render" : "Aktif, tetapi tidak ada klip tersimpan";

        DebugLog.info("========== RINGKASAN DIAGNOSTIK AKHIR SESI ==========");
        DebugLog.info("ID sesi                 : " + session.getSessionId());
        DebugLog.info("Waktu selesai           : " + Instant.now());
        DebugLog.info("Alasan sesi berakhir    : " + display(reason));
        DebugLog.info("Durasi sesi             : " + formatDuration(session.getDurationMs()));
        DebugLog.info("Foto berhasil diambil   : " + session.getCapturedCount()
                + " dari " + session.getSlotCount() + " slot");
        DebugLog.info("Live Photo              : " + livePhotoClips + " klip tersimpan");
        DebugLog.info("Status Live Photo       : " + livePhotoState);

        if (upload == null) {
            DebugLog.info("Status antrean upload   : Belum masuk antrean saat snapshot diagnostik diambil");
            DebugLog.info("Status server upload    : Belum tersedia");
            DebugLog.info("Kesalahan upload        : Tidak ada data (belum masuk antrean)");
        } else {
            DebugLog.info("Status antrean upload   : " + display(upload.status));
            DebugLog.info("Status dari server      : " + display(upload.serverStatus));
            DebugLog.info("Kesalahan upload        : " + display(upload.lastError));
        }

        DebugLog.info("--- Penggunaan memori ---");
        DebugLog.info("Java Heap terpakai      : " + formatBytes(heap.getUsed()));
        DebugLog.info("Java Heap dialokasikan  : " + formatBytes(heap.getCommitted()));
        DebugLog.info("Batas maksimum Heap     : " + formatBytes(heap.getMax()));
        DebugLog.info("Memori Non-Heap terpakai: " + formatBytes(nonHeap.getUsed()));
        DebugLog.info("RAM proses (Working Set): " + formatBytes(processMemory("workingSet")));
        DebugLog.info("Memori privat proses    : " + formatBytes(processMemory("private")));
        DebugLog.info("RAM fisik total         : " + formatBytes(totalRam));
        DebugLog.info("RAM fisik tersedia      : " + formatBytes(availableRam));
        DebugLog.info("Jumlah thread Java      : " + ManagementFactory.getThreadMXBean().getThreadCount());

        if (AppContext.settings().isPerformanceDiagnosticsCpuEnabled()) {
            recordCpuSnapshot();
        }

        if (AppContext.settings().isPerformanceDiagnosticsGcEnabled()) {
            DebugLog.info("--- Riwayat Garbage Collector sejak aplikasi berjalan ---");
            ManagementFactory.getGarbageCollectorMXBeans().forEach(gc ->
                    DebugLog.info("GC " + gc.getName()
                            + " | jumlah proses: " + gc.getCollectionCount()
                            + " | total waktu: " + gc.getCollectionTime() + " ms"));
        }

        DebugLog.info("========== AKHIR DIAGNOSTIK SESI ==========");
    }

    private static void recordCpuSnapshot() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            DebugLog.info("--- Penggunaan CPU saat snapshot ---");
            DebugLog.info("CPU aplikasi           : " + formatPercent(os.getProcessCpuLoad()));
            DebugLog.info("CPU seluruh sistem     : " + formatPercent(os.getCpuLoad()));
        } catch (Throwable ex) {
            DebugLog.warn("Data CPU tidak tersedia: " + ex.getClass().getSimpleName());
        }
    }

    private static long processMemory(String metric) {
        try {
            com.sun.jna.platform.win32.WinNT.HANDLE process =
                    com.sun.jna.platform.win32.Kernel32.INSTANCE.GetCurrentProcess();
            ProcessMemoryCounters counters = new ProcessMemoryCounters();
            counters.cb = new com.sun.jna.platform.win32.WinDef.DWORD(counters.size());
            if (!PsapiMemory.INSTANCE.GetProcessMemoryInfo(process, counters, counters.size())) return -1L;
            counters.read();
            return "workingSet".equals(metric)
                    ? counters.WorkingSetSize.longValue()
                    : counters.PagefileUsage.longValue();
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "Tidak tersedia";
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024.0) {
            return String.format(Locale.US, "%.2f GB (%.0f MB)", mb / 1024.0, mb);
        }
        return String.format(Locale.US, "%.1f MB", mb);
    }

    private static String formatDuration(long durationMs) {
        if (durationMs < 0) return "Tidak diketahui";
        long seconds = durationMs / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d jam %d menit %d detik", hours, minutes, remainingSeconds);
        }
        if (minutes > 0) {
            return String.format(Locale.US, "%d menit %d detik", minutes, remainingSeconds);
        }
        return remainingSeconds + " detik";
    }

    private static String formatPercent(double value) {
        if (value < 0) return "Tidak tersedia";
        return String.format(Locale.US, "%.1f%%", value * 100.0);
    }

    private static String display(Object value) {
        if (value == null || value.toString().isBlank()) return "Tidak ada";
        return value.toString().replace("\r", " ").replace("\n", " ");
    }

    private interface PsapiMemory extends com.sun.jna.win32.StdCallLibrary {
        PsapiMemory INSTANCE = com.sun.jna.Native.load("psapi", PsapiMemory.class,
                com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);
        boolean GetProcessMemoryInfo(com.sun.jna.platform.win32.WinNT.HANDLE process,
                                     ProcessMemoryCounters counters, int size);
    }

    @com.sun.jna.Structure.FieldOrder({
            "cb", "PageFaultCount", "PeakWorkingSetSize", "WorkingSetSize",
            "QuotaPeakPagedPoolUsage", "QuotaPagedPoolUsage",
            "QuotaPeakNonPagedPoolUsage", "QuotaNonPagedPoolUsage",
            "PagefileUsage", "PeakPagefileUsage"
    })
    public static final class ProcessMemoryCounters extends com.sun.jna.Structure {
        public com.sun.jna.platform.win32.WinDef.DWORD cb;
        public com.sun.jna.platform.win32.WinDef.DWORD PageFaultCount;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T PeakWorkingSetSize;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T WorkingSetSize;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T QuotaPeakPagedPoolUsage;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T QuotaPagedPoolUsage;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T QuotaPeakNonPagedPoolUsage;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T QuotaNonPagedPoolUsage;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T PagefileUsage;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T PeakPagefileUsage;
    }
}
