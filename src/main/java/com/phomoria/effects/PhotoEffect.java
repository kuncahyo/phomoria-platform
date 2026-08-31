package com.phomoria.effects;

public enum PhotoEffect {
    NORMAL("Normal"),
    SEPIA("Sepia"),
    NEGATIVE("Negatif"),
    GRAYSCALE("Hitam Putih"),
    WARM("Warm"),
    COOL("Cool");

    private final String label;

    PhotoEffect(String label) {
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
