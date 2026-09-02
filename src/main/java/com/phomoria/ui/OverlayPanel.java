package com.phomoria.ui;

import javax.swing.JPanel;

/**
 * Swing container intended for transparent overlapping components.
 *
 * Swing normally assumes child components do not overlap when optimized
 * drawing is enabled. That assumption is false for the camera stack:
 * WebcamPanel + crop guide + countdown all occupy the same pixels.
 */
public final class OverlayPanel extends JPanel {

    public OverlayPanel() {
        setOpaque(false);
        setDoubleBuffered(false);
    }

    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }
}
