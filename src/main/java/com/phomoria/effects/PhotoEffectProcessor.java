package com.phomoria.effects;

import java.awt.image.BufferedImage;

public final class PhotoEffectProcessor {
    private PhotoEffectProcessor() {}

    public static BufferedImage apply(
            BufferedImage source,
            PhotoEffect effect
    ) {
        if (source == null || effect == null || effect == PhotoEffect.NORMAL) {
            return copy(source);
        }

        BufferedImage result = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                int nr = r;
                int ng = g;
                int nb = b;

                switch (effect) {
                    case SEPIA:
                        nr = clamp((int) (0.393 * r + 0.769 * g + 0.189 * b));
                        ng = clamp((int) (0.349 * r + 0.686 * g + 0.168 * b));
                        nb = clamp((int) (0.272 * r + 0.534 * g + 0.131 * b));
                        break;

                    case NEGATIVE:
                        nr = 255 - r;
                        ng = 255 - g;
                        nb = 255 - b;
                        break;

                    case GRAYSCALE:
                        int gray = clamp(
                                (int) (0.299 * r + 0.587 * g + 0.114 * b)
                        );
                        nr = gray;
                        ng = gray;
                        nb = gray;
                        break;

                    case WARM:
                        nr = clamp(r + 22);
                        ng = clamp(g + 8);
                        nb = clamp(b - 12);
                        break;

                    case COOL:
                        nr = clamp(r - 10);
                        ng = clamp(g + 5);
                        nb = clamp(b + 22);
                        break;

                    default:
                        break;
                }

                result.setRGB(
                        x,
                        y,
                        (nr << 16) | (ng << 8) | nb
                );
            }
        }

        return result;
    }

    private static BufferedImage copy(BufferedImage source) {
        if (source == null) return null;

        BufferedImage result = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        java.awt.Graphics2D g = result.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();

        return result;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
