package com.phomoria.frame;

public enum FrameCategory {
    STANDARD("Standard"),
    SPLIT("Photobooth Split");

    private final String label;

    FrameCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
