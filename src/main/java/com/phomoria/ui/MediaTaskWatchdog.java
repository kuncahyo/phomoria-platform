package com.phomoria.ui;

import com.phomoria.debug.DebugLog;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bounds optional media jobs so an old session cannot occupy resources forever.
 * Five minutes produces a user/admin warning; ten minutes cancels the job.
 */
public final class MediaTaskWatchdog {
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Phomoria-Media-Watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private MediaTaskWatchdog() {}

    public static void watch(String mediaName, String sessionId, Future<?> task,
                             Runnable warning, Runnable timeout) {
        TIMER.schedule(() -> {
            if (task.isDone()) return;
            DebugLog.warn(mediaName + " masih berjalan setelah 5 menit. session=" + sessionId);
            warning.run();
        }, 5, TimeUnit.MINUTES);
        TIMER.schedule(() -> {
            if (task.isDone()) return;
            DebugLog.error(mediaName + " dihentikan setelah melewati batas 10 menit. session=" + sessionId);
            task.cancel(true);
            timeout.run();
        }, 10, TimeUnit.MINUTES);
    }
}
