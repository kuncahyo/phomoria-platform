package com.phomoria.camera;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Rolling timestamp-based PRE/POST frame buffer.
 *
 * Reference basis: GPhoto2LivePhotoWarmupBufferDiagnostic.
 *
 * This class intentionally does not control gPhoto2. It only stores
 * the most recent decoded Live View frames.
 */
final class GPhoto2LivePhotoBuffer {

    private final long preWindowNanos;
    private final long postWindowNanos;

    private final Deque<Frame> rollingPre = new ArrayDeque<>();
    private final List<Frame> postFrames = new ArrayList<>();

    private boolean shuttered;
    private long shutterAtNanos;

    GPhoto2LivePhotoBuffer(
            double preSeconds,
            double postSeconds
    ) {
        preWindowNanos =
                (long) (preSeconds * 1_000_000_000L);

        postWindowNanos =
                (long) (postSeconds * 1_000_000_000L);
    }

    synchronized void accept(
            BufferedImage image,
            long timestampNanos
    ) {
        if (image == null) {
            return;
        }

        Frame frame = new Frame(
                timestampNanos,
                image
        );

        if (!shuttered) {
            rollingPre.addLast(frame);
            trimPre(timestampNanos);
            return;
        }

        if (timestampNanos <= shutterAtNanos + postWindowNanos) {
            postFrames.add(frame);
        }
    }

    synchronized void markShutter() {
        shuttered = true;
        shutterAtNanos = System.nanoTime();
    }

    synchronized List<BufferedImage> preFrames() {
        List<BufferedImage> result = new ArrayList<>();

        for (Frame frame : rollingPre) {
            result.add(frame.image);
        }

        return result;
    }

    synchronized List<BufferedImage> postFrames() {
        List<BufferedImage> result = new ArrayList<>();

        for (Frame frame : postFrames) {
            result.add(frame.image);
        }

        return result;
    }

    synchronized void reset() {
        rollingPre.clear();
        postFrames.clear();
        shuttered = false;
        shutterAtNanos = 0;
    }

    private void trimPre(long now) {
        while (!rollingPre.isEmpty()
                && now - rollingPre.peekFirst().timestampNanos
                > preWindowNanos) {
            rollingPre.removeFirst();
        }
    }

    private record Frame(
            long timestampNanos,
            BufferedImage image
    ) {
    }
}
