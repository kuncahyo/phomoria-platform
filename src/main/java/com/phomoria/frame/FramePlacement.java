package com.phomoria.frame;

public record FramePlacement(
        int sourceSlotIndex,
        double x, double y, double width, double height,
        boolean cropToFill
) {
    public FramePlacement {
        if (sourceSlotIndex < 0) throw new IllegalArgumentException("sourceSlotIndex < 0");
    }
}
