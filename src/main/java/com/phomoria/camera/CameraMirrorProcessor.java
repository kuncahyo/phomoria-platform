package com.phomoria.camera;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Applies the operator's photo mirror setting to the captured image.
 *
 * The live camera may be visually mirrored independently by WebcamPanel.
 * This class controls the actual stored/captured result so the saved photo
 * matches the selected setting.
 */
public final class CameraMirrorProcessor {

    private CameraMirrorProcessor() {
    }

    public static BufferedImage process(
            BufferedImage source,
            boolean mirror
    ) {
        if (source == null) {
            return null;
        }

        if (!mirror) {
            return copy(source);
        }

        int width = source.getWidth();
        int height = source.getHeight();

        BufferedImage result =
                new BufferedImage(
                        width,
                        height,
                        BufferedImage.TYPE_INT_RGB
                );

        Graphics2D graphics =
                result.createGraphics();

        graphics.drawImage(
                source,
                width,
                0,
                0,
                height,
                0,
                0,
                width,
                height,
                null
        );

        graphics.dispose();

        return result;
    }

    private static BufferedImage copy(
            BufferedImage source
    ) {
        BufferedImage result =
                new BufferedImage(
                        source.getWidth(),
                        source.getHeight(),
                        BufferedImage.TYPE_INT_RGB
                );

        Graphics2D graphics =
                result.createGraphics();

        graphics.drawImage(
                source,
                0,
                0,
                null
        );

        graphics.dispose();

        return result;
    }
}
