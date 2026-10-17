package com.phomoria.frame;

/**
 * A physical photo opening. sourceSlotIndex is zero-based and may be shared
 * by multiple physical openings; rotation is expressed in degrees.
 */
public record FramePlacement(
        int sourceSlotIndex,
        double x, double y, double width, double height,
        boolean cropToFill,
        double rotation
) {
    public FramePlacement {
        if (sourceSlotIndex < 0) throw new IllegalArgumentException("sourceSlotIndex < 0");
    }

    /** Backward-compatible constructor for existing built-in frame definitions. */
    public FramePlacement(int sourceSlotIndex, double x, double y, double width,
                          double height, boolean cropToFill) {
        this(sourceSlotIndex, x, y, width, height, cropToFill, 0.0);
    }
}
