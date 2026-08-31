package com.phomoria.frame;

import com.phomoria.debug.DebugLog;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class FrameTemplateLoader {
    private FrameTemplateLoader() {}

    public static BufferedImage loadAsset(String frameName) {
        if (frameName == null || frameName.isBlank()) return null;
        String safe = frameName.trim();
        File[] candidates = {
                new File("Frames", safe),
                new File("Frames", safe + ".png"),
                new File(System.getProperty("user.dir"), "Frames/" + safe + ".png")
        };
        for (File file : candidates) {
            if (!file.isFile()) continue;
            try {
                BufferedImage image = ImageIO.read(file);
                if (image != null) {
                    DebugLog.info("Frame asset loaded: " + file.getAbsolutePath());
                    return image;
                }
            } catch (Exception ex) {
                DebugLog.error("Frame asset load failed: " + file, ex);
            }
        }
        DebugLog.warn("Frame asset not found for: " + frameName + ". Using generated fallback.");
        return null;
    }
}
