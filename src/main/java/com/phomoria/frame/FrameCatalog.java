package com.phomoria.frame;

import com.phomoria.app.AppContext;
import com.phomoria.debug.DebugLog;

import java.util.List;

public final class FrameCatalog {
    private FrameCatalog() {}

    /*
     * Registry is deliberately isolated.
     *
     * To remove a frame later, remove one entry here.
     * Other application code uses only the selected FramePreset id.
     */
    private static final List<FramePreset> PRESETS = List.of(
            new FramePreset(
                    "standard_vertical",
                    "Standard Vertical",
                    FrameCategory.STANDARD
            ),
            new FramePreset(
                    "split_vertical",
                    "Photobooth Split",
                    FrameCategory.SPLIT
            )
    );

    public static List<FramePreset> all() {
        return PRESETS;
    }

    public static List<FramePreset> byCategory(FrameCategory category) {
        return PRESETS.stream()
                .filter(p -> p.category() == category)
                .toList();
    }

    public static FramePreset find(String id) {
        if (id == null) return null;

        return PRESETS.stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    public static FrameDefinition createDefinition(
            FramePreset preset,
            int photoCount
    ) {
        return createDefinition(
                preset,
                photoCount,
                AppContext.settings().getPrintPaperSize(),
                AppContext.settings().getPrintDpi()
        );
    }

    public static FrameDefinition createDefinition(
            FramePreset preset,
            int photoCount,
            PrintPaperSize paper,
            int dpi
    ) {
        if (preset == null) {
            throw new IllegalArgumentException("Frame preset is null.");
        }

        int count = Math.max(1, photoCount);

        DebugLog.info(
                "Creating frame definition: id="
                        + preset.id()
                        + ", category="
                        + preset.category()
                        + ", photoCount="
                        + count
        );

        FrameDefinition base =
                switch (preset.category()) {
                    case STANDARD ->
                            FrameDefinition.defaultVertical(count);

                    case SPLIT ->
                            FrameDefinition.splitVertical(count);
                };

        PrintPaperSize selectedPaper =
                paper == null
                        ? PrintPaperSize.FOUR_BY_SIX
                        : paper;

        double widthInches;
        double heightInches;

        if (selectedPaper.isCustom()) {
            widthInches =
                    AppContext.settings()
                            .getCustomPaperWidthInches();

            heightInches =
                    AppContext.settings()
                            .getCustomPaperHeightInches();
        } else {
            widthInches =
                    selectedPaper.getWidthInches();

            heightInches =
                    selectedPaper.getHeightInches();
        }

        int width =
                Math.max(
                        1,
                        (int) Math.round(
                                widthInches * dpi
                        )
                );

        int height =
                Math.max(
                        1,
                        (int) Math.round(
                                heightInches * dpi
                        )
                );

        return new FrameDefinition(
                base.getName(),
                base.getLayoutType(),
                width,
                height,
                base.getPlacements()
        );
    }
}
