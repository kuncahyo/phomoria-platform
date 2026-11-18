package com.phomoria.debug;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Lightweight system-pressure monitor. It warns after two consecutive critical
 * samples and never restarts the computer or discards an active photo session.
 */
public final class SystemHealthMonitor {
    private static final long LOW_RAM_BYTES = 1024L * 1024L * 1024L;
    private static final long LOW_DISK_BYTES = 1024L * 1024L * 1024L;
    private static final ScheduledExecutorService MONITOR = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Phomoria-System-Health");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile boolean warned;
    private static int consecutiveCriticalSamples;

    private SystemHealthMonitor() {}

    public static void start() {
        MONITOR.scheduleWithFixedDelay(SystemHealthMonitor::sampleSafely, 30, 30, TimeUnit.SECONDS);
        DebugLog.info("System health monitor started; sampling every 30 seconds.");
    }

    private static void sampleSafely() {
        try {
            long freeRam = -1L;
            try {
                com.sun.management.OperatingSystemMXBean os =
                        (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                freeRam = os.getFreeMemorySize();
            } catch (Throwable ignored) {}

            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            double heapRatio = heap.getMax() > 0 ? heap.getUsed() / (double) heap.getMax() : 0.0;
            long freeDisk = new File(System.getProperty("user.dir", ".")).getUsableSpace();

            boolean critical = (freeRam >= 0 && freeRam < LOW_RAM_BYTES)
                    || heapRatio >= 0.92
                    || (freeDisk > 0 && freeDisk < LOW_DISK_BYTES);

            if (critical) {
                consecutiveCriticalSamples++;
                DebugLog.warn("SYSTEM_RESOURCE_PRESSURE freeRamMb=" + (freeRam < 0 ? "unknown" : freeRam / 1048576)
                        + ", heapUsedPercent=" + Math.round(heapRatio * 100)
                        + ", freeDiskMb=" + freeDisk / 1048576
                        + ", consecutiveSamples=" + consecutiveCriticalSamples);
                if (consecutiveCriticalSamples >= 2 && !warned) {
                    warned = true;
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            null,
                            "Komputer mulai kekurangan sumber daya (RAM, memori aplikasi, atau ruang disk). "
                                    + "Foto yang sudah tersimpan tetap aman, tetapi proses tambahan dapat melambat atau gagal. "
                                    + "Selesaikan sesi yang sedang berjalan, hindari memulai sesi baru sementara, "
                                    + "lalu minta admin memeriksa komputer. Restart komputer dapat dipertimbangkan "
                                    + "setelah tidak ada sesi yang sedang diproses.",
                            "Peringatan Performa Komputer",
                            JOptionPane.WARNING_MESSAGE));
                }
            } else {
                consecutiveCriticalSamples = 0;
                if (warned) {
                    warned = false;
                    DebugLog.info("SYSTEM_RESOURCE_PRESSURE cleared; resource levels returned below warning thresholds.");
                }
            }
        } catch (Throwable ex) {
            DebugLog.error("System health sample failed.", ex);
        }
    }
}
