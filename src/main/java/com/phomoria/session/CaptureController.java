package com.phomoria.session;

import com.phomoria.camera.CameraManager;
import com.phomoria.debug.DebugLog;

import javax.swing.*;
import java.awt.image.BufferedImage;

public final class CaptureController {
    public interface Listener {
        void onCountdown(int seconds, boolean retake, int targetIndex);
        void onCaptured(BufferedImage image, boolean retake, int targetIndex);
        void onCaptureError(Exception error);
    }

    private final Listener listener;
    private Timer timer;
    private int countdown;
    private boolean retake;
    private int targetIndex = -1;

    public CaptureController(Listener listener) {
        this.listener = listener;
    }

    public void startNewPhoto() {
        start(false, -1);
    }

    public void startRetake(int index) {
        start(true, index);
    }

    private void start(boolean retake, int targetIndex) {
        stop();

        this.retake = retake;
        this.targetIndex = targetIndex;
        this.countdown = 3;

        DebugLog.info(
                "Countdown started. mode=" + (retake ? "RETAKE" : "NEW")
                        + ", targetIndex=" + targetIndex
        );

        listener.onCountdown(countdown, retake, targetIndex);

        timer = new Timer(1000, e -> tick());
        timer.setInitialDelay(1000);
        timer.start();
    }

    private void tick() {
        countdown--;

        if (countdown <= 0) {
            stop();
            capture();
            return;
        }

        DebugLog.info("Countdown tick: " + countdown);
        listener.onCountdown(countdown, retake, targetIndex);
    }

    private void capture() {
        try {
            BufferedImage image = CameraManager.capture();

            if (image == null) {
                throw new IllegalStateException("Camera returned null image.");
            }

            DebugLog.info(
                    "Capture success: " + image.getWidth() + "x" + image.getHeight()
                            + ", mode=" + (retake ? "RETAKE" : "NEW")
                            + ", targetIndex=" + targetIndex
            );

            listener.onCaptured(image, retake, targetIndex);
        } catch (Exception ex) {
            DebugLog.error("Capture failed.", ex);
            listener.onCaptureError(ex);
        }
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}
