package com.phomoria.camera;

import com.phomoria.debug.DebugLog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Samples the active UVC webcam once and shares those frames with both
 * the live preview and the Live Photo rolling buffer.
 *
 * The buffer stores JPEG bytes rather than full-size BufferedImages so its
 * memory use remains bounded even when the webcam uses a high resolution.
 */
public final class WebcamLivePhotoBuffer {
    public static final int SAMPLE_FPS = 15;
    public static final int MAX_BUFFER_SECONDS = 10;

    public record Frame(long timestampMillis, byte[] jpeg) {
        public Frame {
            jpeg = jpeg == null ? new byte[0] : jpeg.clone();
        }

        @Override
        public byte[] jpeg() {
            return jpeg.clone();
        }
    }

    private final Object lock = new Object();
    private final Deque<Frame> frames = new ArrayDeque<>();
    private volatile BufferedImage latestPreviewFrame;
    private ScheduledExecutorService executor;
    private volatile boolean running;

    public void start() {
        synchronized (lock) {
            if (running) return;
            running = true;
            frames.clear();
            latestPreviewFrame = null;
            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "Phomoria-Webcam-FramePump");
                thread.setDaemon(true);
                return thread;
            });
            long periodMillis = Math.max(1, 1000 / SAMPLE_FPS);
            executor.scheduleWithFixedDelay(
                    this::sampleSafely, 0, periodMillis, TimeUnit.MILLISECONDS
            );
        }
        DebugLog.info("Webcam Live Photo frame pump started: "
                + SAMPLE_FPS + " FPS, buffer=" + MAX_BUFFER_SECONDS + "s");
    }

    private void sampleSafely() {
        if (!running) return;
        try {
            BufferedImage image = CameraManager.readWebcamFrameForBuffer();
            if (image == null) return;

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "jpg", output)) {
                throw new IOException("JPEG encoder is not available.");
            }

            long now = System.currentTimeMillis();
            Frame frame = new Frame(now, output.toByteArray());

            synchronized (lock) {
                if (!running) return;
                frames.addLast(frame);
                latestPreviewFrame = image;

                long oldestAllowed = now - MAX_BUFFER_SECONDS * 1000L;
                while (!frames.isEmpty()
                        && frames.peekFirst().timestampMillis() < oldestAllowed) {
                    frames.removeFirst();
                }
            }
        } catch (Exception ex) {
            if (running) {
                DebugLog.warn("Webcam frame pump sample failed: " + ex.getMessage());
            }
        }
    }

    public BufferedImage getLatestPreviewFrame() {
        return latestPreviewFrame;
    }

    /**
     * Returns only frames captured in the requested interval ending now.
     * The caller owns the returned immutable list; frame JPEG data is copied.
     */
    public List<Frame> snapshotLast(int durationSeconds) {
        int seconds = Math.max(1, Math.min(MAX_BUFFER_SECONDS, durationSeconds));
        long shutterTime = System.currentTimeMillis();
        long startTime = shutterTime - seconds * 1000L;

        synchronized (lock) {
            List<Frame> result = new ArrayList<>();
            for (Frame frame : frames) {
                if (frame.timestampMillis() >= startTime
                        && frame.timestampMillis() <= shutterTime) {
                    result.add(new Frame(frame.timestampMillis(), frame.jpeg()));
                }
            }
            // Do not silently create a shorter Live Photo when the webcam has
            // not yet warmed up for the full configured pre-shutter interval.
            long requiredFirstFrame = startTime + Math.max(1, 1000 / SAMPLE_FPS) * 2L;
            if (result.size() < 2 || result.get(0).timestampMillis() > requiredFirstFrame
                    || shutterTime - result.get(result.size() - 1).timestampMillis() > 500L) {
                return List.of();
            }
            return Collections.unmodifiableList(result);
        }
    }

    public void stop() {
        ScheduledExecutorService toStop;
        synchronized (lock) {
            if (!running && executor == null) return;
            running = false;
            toStop = executor;
            executor = null;
            frames.clear();
            latestPreviewFrame = null;
        }
        if (toStop != null) {
            toStop.shutdownNow();
        }
        DebugLog.info("Webcam Live Photo frame pump stopped.");
    }

    public boolean isRunning() {
        return running;
    }
}
