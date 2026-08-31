package com.phomoria.frame;

public record FramePreset(
        String id,
        String name,
        FrameCategory category
) {
    public FramePreset {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Frame preset id is required.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Frame preset name is required.");
        }
        if (category == null) {
            throw new IllegalArgumentException("Frame preset category is required.");
        }
    }
}
