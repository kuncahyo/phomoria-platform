package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.effects.PhotoEffectSession;
import com.phomoria.frame.FrameDefinition;
import com.phomoria.frame.FrameRenderer;
import com.phomoria.frame.FrameCatalog;
import com.phomoria.frame.FramePreset;
import com.phomoria.frame.FrameTemplateLoader;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public final class ResultRenderer {
    private ResultRenderer() {}

    public static File render(List<BufferedImage> photos, File output) throws Exception {
        PhotoEffectSession effectSession = AppContext.effectSession();
        List<BufferedImage> processed = effectSession.process(photos);
        FramePreset preset =
                FrameCatalog.find(
                        AppContext.settings()
                                .getSelectedFrameId()
                );

        if (preset == null) {
            preset = FrameCatalog.find(
                    "standard_vertical"
            );
        }

        FrameDefinition definition =
                FrameCatalog.createDefinition(
                        preset,
                        photos.size()
                );

        BufferedImage frameAsset =
                FrameTemplateLoader.loadAsset(
                        AppContext.settings()
                                .getFrameName()
                );
        return FrameRenderer.render(definition, frameAsset, processed, output);
    }
}
