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
import java.util.concurrent.TimeUnit;

/**
 * Lightweight, opt-in snapshots taken only when a photo session ends.
 * No polling, forced GC, or additional worker thread is created here.
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
            DebugLog.info("PERF_DIAGNOSTICS automatically disabled after configured duration.");
            return false;
        }
        return true;
    }

    public static synchronized void recordSessionEnd(PhotoSession session, String reason) {
        if (session == null || !isEnabled()) return;

        Runtime runtime = Runtime.getRuntime();
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

        DebugLog.info("PERF_SESSION_END"
                + " session=" + session.getSessionId()
                + ", endedAt=" + Instant.now()
                + ", durationMs=" + session.getDurationMs()
                + ", reason=" + safe(reason)
                + ", photos=" + session.getCapturedCount()
                + ", slots=" + session.getSlotCount()
                + ", livePhotoClipsSaved=" + livePhotoClips
                + ", livePhotoEnabled=" + AppContext.settings().isLivePhotoEnabled()
                + ", uploadQueueStatus=" + (upload == null ? "NOT_QUEUED_YET" : safe(upload.status))
                + ", uploadServerStatus=" + (upload == null ? "" : safe(upload.serverStatus))
                + ", uploadError=" + (upload == null ? "" : safe(upload.lastError))
                + ", heapUsedBytes=" + heap.getUsed()
                + ", heapCommittedBytes=" + heap.getCommitted()
                + ", heapMaxBytes=" + heap.getMax()
                + ", nonHeapUsedBytes=" + nonHeap.getUsed()
                + ", processWorkingSetBytes=" + processMemory("workingSet")
                + ", processPrivateBytes=" + processMemory("private")
                + ", systemRamTotalBytes=" + totalRam
                + ", systemRamAvailableBytes=" + availableRam
                + ", threadCount=" + Thread.activeCount());

        if (AppContext.settings().isPerformanceDiagnosticsCpuEnabled()) {
            try {
                com.sun.management.OperatingSystemMXBean os =
                        (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                DebugLog.info("PERF_CPU session=" + session.getSessionId()
                        + ", processCpuLoad=" + os.getProcessCpuLoad()
                        + ", systemCpuLoad=" + os.getCpuLoad());
            } catch (Throwable ex) {
                DebugLog.warn("PERF_CPU unavailable: " + ex.getClass().getSimpleName());
            }
        }

        if (AppContext.settings().isPerformanceDiagnosticsGcEnabled()) {
            ManagementFactory.getGarbageCollectorMXBeans().forEach(gc ->
                    DebugLog.info("PERF_GC session=" + session.getSessionId()
                            + ", collector=" + gc.getName()
                            + ", count=" + gc.getCollectionCount()
                            + ", timeMs=" + gc.getCollectionTime()));
        }
    }

    private static long processMemory(String metric) {
        try {
            com.sun.jna.platform.win32.WinNT.HANDLE process =
                    com.sun.jna.platform.win32.Kernel32.INSTANCE.GetCurrentProcess();
            com.sun.jna.platform.win32.Psapi.PROCESS_MEMORY_COUNTERS counters =
                    new com.sun.jna.platform.win32.Psapi.PROCESS_MEMORY_COUNTERS();
            if (!com.sun.jna.platform.win32.Psapi.INSTANCE.GetProcessMemoryInfo(
                    process, counters, counters.size())) return -1L;
            counters.read();
            return "workingSet".equals(metric)
                    ? counters.WorkingSetSize.longValue()
                    : counters.PagefileUsage.longValue();
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("\\r", " ").replace("\\n", " ");
    }
}
