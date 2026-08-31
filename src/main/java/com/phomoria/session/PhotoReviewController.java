package com.phomoria.session;

import javax.swing.*;

public final class PhotoReviewController {
    public interface Listener {
        void onReviewFinished();
        void onReviewCountdown(int seconds);
    }

    private Timer timer;
    private int seconds;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void start(int seconds) {
        stop();

        this.seconds = Math.max(1, seconds);

        if (listener != null) {
            listener.onReviewCountdown(this.seconds);
        }

        timer = new Timer(1000, e -> tick());
        timer.setInitialDelay(1000);
        timer.start();
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    private void tick() {
        seconds--;

        if (seconds <= 0) {
            stop();

            if (listener != null) {
                listener.onReviewFinished();
            }

            return;
        }

        if (listener != null) {
            listener.onReviewCountdown(seconds);
        }
    }
}
