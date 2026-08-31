package com.phomoria.frame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FrameDefinition {
    private final String name;
    private final FrameLayoutType layoutType;
    private final int width;
    private final int height;
    private final List<FramePlacement> placements;

    public FrameDefinition(String name, FrameLayoutType layoutType, int width, int height, List<FramePlacement> placements) {
        this.name = name == null ? "Default Frame" : name;
        this.layoutType = layoutType == null ? FrameLayoutType.SINGLE : layoutType;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.placements = Collections.unmodifiableList(new ArrayList<>(placements == null ? List.of() : placements));
    }

    public String getName() { return name; }
    public FrameLayoutType getLayoutType() { return layoutType; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public List<FramePlacement> getPlacements() { return placements; }

    public static FrameDefinition defaultVertical(int slotCount) {
        int count = Math.max(1, slotCount);
        List<FramePlacement> p = new ArrayList<>();
        double marginX = 0.10;
        double top = 0.08;
        double bottom = 0.10;
        double gap = count > 1 ? 0.015 : 0;
        double h = (1.0 - top - bottom - gap * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            p.add(new FramePlacement(i, marginX, top + i * (h + gap), 1.0 - marginX * 2, h, true));
        }
        return new FrameDefinition("Default Frame", FrameLayoutType.SINGLE, 1200, 1800, p);
    }

    // Future split support: multiple placements may reference the same sourceSlotIndex.
    // PhotoSession remains the source of captured photos; the frame layout decides how
    // those photos are reused for print/composition.
    public static FrameDefinition splitFromSingle(FrameDefinition single) {
        return new FrameDefinition(
                single.name,
                FrameLayoutType.SPLIT,
                single.width,
                single.height,
                single.placements
        );
    }

    /**
     * Simulation layout for a cut-in-half photobooth strip.
     *
     * Every captured photo is placed twice:
     * left strip + right strip.
     *
     * This is intentionally a layout concern. PhotoSession does not know
     * that a photo is being printed twice.
     */
    public static FrameDefinition splitVertical(int slotCount) {
        int count = Math.max(1, slotCount);

        List<FramePlacement> p = new ArrayList<>();

        double outerX = 0.055;
        double top = 0.055;
        double bottom = 0.055;
        double centerGap = 0.025;
        double columnWidth =
                (1.0 - outerX * 2 - centerGap) / 2.0;

        double innerX = 0.055;
        double gap = count > 1 ? 0.018 : 0;
        double usableHeight =
                1.0 - top - bottom - gap * (count - 1);
        double h = usableHeight / count;

        for (int i = 0; i < count; i++) {
            double y = top + i * (h + gap);

            // Same source photo is intentionally referenced twice.
            p.add(
                    new FramePlacement(
                            i,
                            outerX + innerX * 0.15,
                            y,
                            columnWidth - innerX * 0.15,
                            h,
                            true
                    )
            );

            p.add(
                    new FramePlacement(
                            i,
                            0.5 + centerGap / 2.0,
                            y,
                            columnWidth - innerX * 0.15,
                            h,
                            true
                    )
            );
        }

        return new FrameDefinition(
                "Photobooth Split",
                FrameLayoutType.SPLIT,
                1200,
                1800,
                p
        );
    }

    public FrameDefinition withPlacements(List<FramePlacement> newPlacements) {
        return new FrameDefinition(name, layoutType, width, height, newPlacements);
    }
}
