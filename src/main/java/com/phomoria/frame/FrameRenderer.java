package com.phomoria.frame;

import com.phomoria.debug.DebugLog;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public final class FrameRenderer {
    private FrameRenderer() {}

    public static File render(
            FrameDefinition definition,
            BufferedImage frameAsset,
            List<BufferedImage> photos,
            File output
    ) throws Exception {
        if (definition == null) {
            throw new IllegalArgumentException("FrameDefinition is null.");
        }
        if (photos == null || photos.isEmpty()) {
            throw new IllegalArgumentException("No photos to render.");
        }

        BufferedImage out = new BufferedImage(
                definition.getWidth(),
                definition.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g = out.createGraphics();

        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
        );
        g.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
        );

        // Start from an opaque white canvas.
        g.setComposite(AlphaComposite.Src);
        g.setColor(Color.WHITE);
        g.fillRect(
                0,
                0,
                out.getWidth(),
                out.getHeight()
        );

        // Draw photos into the detected frame holes.
        for (FramePlacement placement : definition.getPlacements()) {
            int source = placement.sourceSlotIndex();

            if (source < 0 || source >= photos.size()) {
                DebugLog.warn(
                        "Frame placement skipped. sourceSlotIndex="
                                + source
                                + ", photoCount="
                                + photos.size()
                );
                continue;
            }

            BufferedImage photo = photos.get(source);

            if (photo == null) {
                continue;
            }

            int x = (int) Math.round(
                    placement.x() * out.getWidth()
            );
            int y = (int) Math.round(
                    placement.y() * out.getHeight()
            );
            int w = Math.max(
                    1,
                    (int) Math.round(
                            placement.width()
                                    * out.getWidth()
                    )
            );
            int h = Math.max(
                    1,
                    (int) Math.round(
                            placement.height()
                                    * out.getHeight()
                    )
            );

            drawPhoto(
                    g,
                    photo,
                    x,
                    y,
                    w,
                    h,
                    placement.cropToFill()
            );
        }

        /*
         * IMPORTANT:
         *
         * The uploaded PNG contains transparent holes.
         *
         * AlphaComposite.Src would replace the pixels underneath with
         * transparent pixels from the PNG, making the photo holes appear
         * black/empty in the saved result.
         *
         * SrcOver preserves the photos underneath wherever the PNG is
         * transparent, while drawing the artwork normally where it is
         * opaque.
         */
        if (frameAsset != null) {
            g.setComposite(AlphaComposite.SrcOver);

            g.drawImage(
                    frameAsset,
                    0,
                    0,
                    out.getWidth(),
                    out.getHeight(),
                    null
            );
        } else {
            g.setComposite(AlphaComposite.SrcOver);
            drawFallbackFrame(g, definition);
        }

        g.dispose();

        ImageIO.write(
                out,
                "png",
                output
        );

        DebugLog.info(
                "Frame rendered: "
                        + output.getAbsolutePath()
                        + " layout="
                        + definition.getLayoutType()
                        + " placements="
                        + definition.getPlacements().size()
        );

        return output;
    }

    private static void drawPhoto(
            Graphics2D g,
            BufferedImage photo,
            int x,
            int y,
            int w,
            int h,
            boolean crop
    ) {
        double scale = crop
                ? Math.max(
                        w / (double) photo.getWidth(),
                        h / (double) photo.getHeight()
                )
                : Math.min(
                        w / (double) photo.getWidth(),
                        h / (double) photo.getHeight()
                );

        int dw = Math.max(
                1,
                (int) Math.round(
                        photo.getWidth() * scale
                )
        );

        int dh = Math.max(
                1,
                (int) Math.round(
                        photo.getHeight() * scale
                )
        );

        int dx =
                x + (w - dw) / 2;

        int dy =
                y + (h - dh) / 2;

        Shape old = g.getClip();

        g.clipRect(
                x,
                y,
                w,
                h
        );

        g.drawImage(
                photo,
                dx,
                dy,
                dw,
                dh,
                null
        );

        g.setClip(old);
    }

    private static void drawFallbackFrame(
            Graphics2D g,
            FrameDefinition d
    ) {
        g.setColor(
                new Color(35, 35, 42)
        );

        g.setStroke(
                new BasicStroke(14)
        );

        g.drawRect(
                7,
                7,
                d.getWidth() - 14,
                d.getHeight() - 14
        );

        g.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        44
                )
        );

        g.drawString(
                d.getName(),
                55,
                d.getHeight() - 55
        );
    }
}
