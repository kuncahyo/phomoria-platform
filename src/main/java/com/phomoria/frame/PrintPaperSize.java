package com.phomoria.frame;

public enum PrintPaperSize {

    TWO_BY_SIX(
            "2 x 6 inch (5 x 15 cm)",
            2.0,
            6.0,
            false
    ),

    FOUR_BY_SIX(
            "4 x 6 inch (10 x 15 cm)",
            4.0,
            6.0,
            false
    ),

    FIVE_BY_SEVEN(
            "5 x 7 inch (13 x 18 cm)",
            5.0,
            7.0,
            false
    ),

    A4(
            "A4",
            8.27,
            11.69,
            false
    ),

    CUSTOM(
            "Custom",
            0.0,
            0.0,
            true
    );

    private final String label;
    private final double widthInches;
    private final double heightInches;
    private final boolean custom;

    PrintPaperSize(
            String label,
            double widthInches,
            double heightInches,
            boolean custom
    ) {
        this.label = label;
        this.widthInches = widthInches;
        this.heightInches = heightInches;
        this.custom = custom;
    }

    public String getLabel() {
        return label;
    }

    public double getWidthInches() {
        return widthInches;
    }

    public double getHeightInches() {
        return heightInches;
    }

    public boolean isCustom() {
        return custom;
    }

    public int getWidthPixels(int dpi) {
        return Math.max(
                1,
                (int) Math.round(
                        widthInches * dpi
                )
        );
    }

    public int getHeightPixels(int dpi) {
        return Math.max(
                1,
                (int) Math.round(
                        heightInches * dpi
                )
        );
    }

    @Override
    public String toString() {
        return label;
    }
}
