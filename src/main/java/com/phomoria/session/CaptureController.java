package com.phomoria.session;

import com.phomoria.camera.CameraManager;
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

        void onCaptureError(Exception error);
    }

    private final Listener listener;

    private Timer timer;

    private int countdown;

    private boolean retake;

    private int targetIndex = -1;

    public CaptureController(
            Listener listener
    ) {
        this.listener = listener;
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
        this.countdown = 3;

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

            captureAsync();

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
    private void captureAsync() {

        final boolean captureRetake =
                retake;

        final int captureTargetIndex =
                targetIndex;

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

                            listener.onCaptured(
                                    image,
                                    captureRetake,
                                    captureTargetIndex
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
