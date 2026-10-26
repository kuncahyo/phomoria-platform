package com.phomoria.session;

import com.phomoria.camera.CameraManager;
import com.phomoria.camera.WebcamLivePhotoBuffer;
import com.phomoria.app.AppContext;
import java.util.List;
import com.phomoria.debug.DebugLog;

import javax.swing.*;
import java.awt.image.BufferedImage;

public final class CaptureController {

    public interface Listener {
        void onCountdown(
                int seconds,
                boolean retake,
                int targetIndex
        );

        void onCaptured(
                BufferedImage image,
                boolean retake,
                int targetIndex
        );

        default void onCapturedWithLivePhoto(
                BufferedImage image, boolean retake, int targetIndex,
                List<WebcamLivePhotoBuffer.Frame> livePhotoFrames) {
            onCaptured(image, retake, targetIndex);
        }

        void onCaptureError(Exception error);
    }

    private final Listener listener;

    private Timer timer;

    private int countdown;
    private int countdownSeconds = 3;

    private boolean retake;

    private int targetIndex = -1;

    public CaptureController(
            Listener listener
    ) {
        this.listener = listener;
    }

    public void setCountdownSeconds(int seconds) {
        countdownSeconds = Math.max(1, Math.min(30, seconds));
    }

    public void startNewPhoto() {
        start(false, -1);
    }

    public void startRetake(int index) {
        start(true, index);
    }

    private void start(
            boolean retake,
            int targetIndex
    ) {

        stop();

        this.retake = retake;
        this.targetIndex = targetIndex;
        this.countdown = countdownSeconds;

        DebugLog.info(
                "Countdown started. mode="
                        + (retake ? "RETAKE" : "NEW")
                        + ", targetIndex="
                        + targetIndex
        );

        listener.onCountdown(
                countdown,
                retake,
                targetIndex
        );

        timer = new Timer(
                1000,
                e -> tick()
        );

        timer.setInitialDelay(1000);
        timer.start();
    }

    private void tick() {

        countdown--;

        if (countdown <= 0) {

            stop();

            // Freeze the rolling buffer at the shutter instant, before the
            // still-photo capture begins (which may take seconds on DSLR).
            List<WebcamLivePhotoBuffer.Frame> livePhotoFrames = List.of();
            if (AppContext.settings().isLivePhotoEnabled()
                    && CameraManager.current() != null) {
                livePhotoFrames = CameraManager.snapshotLivePhoto(
                        AppContext.settings().getLivePhotoDurationSeconds());
            }
            captureAsync(livePhotoFrames);

            return;
        }

        DebugLog.info(
                "Countdown tick: "
                        + countdown
        );

        listener.onCountdown(
                countdown,
                retake,
                targetIndex
        );
    }

    /**
     * Capture is executed away from the Swing EDT.
     * This is important for gPhoto2 because a physical DSLR capture can
     * take several seconds while the camera writes/downloads the JPEG.
     */
    private void captureAsync(
            List<WebcamLivePhotoBuffer.Frame> livePhotoFrames) {

        final boolean captureRetake =
                retake;

        final int captureTargetIndex =
                targetIndex;
        final List<WebcamLivePhotoBuffer.Frame> capturedLivePhotoFrames =
                livePhotoFrames == null ? List.of() : List.copyOf(livePhotoFrames);

        SwingWorker<BufferedImage, Void> worker =
                new SwingWorker<>() {

                    @Override
                    protected BufferedImage doInBackground()
                            throws Exception {

                        return CameraManager.capture();
                    }

                    @Override
                    protected void done() {

                        try {

                            BufferedImage image =
                                    get();

                            if (image == null) {
                                throw new IllegalStateException(
                                        "Camera returned null image."
                                );
                            }

                            DebugLog.info(
                                    "Capture success: "
                                            + image.getWidth()
                                            + "x"
                                            + image.getHeight()
                                            + ", mode="
                                            + (captureRetake
                                                    ? "RETAKE"
                                                    : "NEW")
                                            + ", targetIndex="
                                            + captureTargetIndex
                            );

                            listener.onCapturedWithLivePhoto(
                                    image, captureRetake, captureTargetIndex,
                                    capturedLivePhotoFrames
                            );

                        } catch (Exception ex) {

                            Throwable cause =
                                    ex.getCause();

                            Exception error =
                                    cause instanceof Exception
                                            ? (Exception) cause
                                            : ex;

                            DebugLog.error(
                                    "Capture failed.",
                                    error
                            );

                            listener.onCaptureError(
                                    error
                            );
                        }
                    }
                };

        worker.execute();
    }

    public void stop() {

        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}
