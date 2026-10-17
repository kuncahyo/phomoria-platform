package com.phomoria.frame;

import com.phomoria.debug.DebugLog;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public final class FrameRenderer {
    private FrameRenderer() {}

    public static File render(FrameDefinition definition, BufferedImage frameAsset,
                              List<BufferedImage> photos, File output) throws Exception {
        return render(definition, frameAsset, photos, output,
                definition == null ? 1 : definition.getWidth(),
                definition == null ? 1 : definition.getHeight());
    }

    public static File render(FrameDefinition definition, BufferedImage frameAsset,
                              List<BufferedImage> photos, File output,
                              int targetWidth, int targetHeight) throws Exception {
        if (definition == null) throw new IllegalArgumentException("FrameDefinition is null.");
        if (photos == null || photos.isEmpty()) throw new IllegalArgumentException("No photos to render.");
        if (targetWidth <= 0 || targetHeight <= 0) throw new IllegalArgumentException("Target canvas size is invalid.");

        int sourceWidth = definition.getWidth(), sourceHeight = definition.getHeight();
        BufferedImage out = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.Src);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);

            double scale = Math.max(targetWidth / (double) sourceWidth, targetHeight / (double) sourceHeight);
            double drawWidth = sourceWidth * scale, drawHeight = sourceHeight * scale;
            double offsetX = (targetWidth - drawWidth) / 2.0, offsetY = (targetHeight - drawHeight) / 2.0;
            Shape oldClip = g.getClip();
            g.clipRect(0, 0, targetWidth, targetHeight);
            g.translate(offsetX, offsetY);
            g.scale(scale, scale);

            for (FramePlacement placement : definition.getPlacements()) {
                int source = placement.sourceSlotIndex();
                if (source < 0 || source >= photos.size()) {
                    DebugLog.warn("Frame placement skipped. sourceSlotIndex=" + source + ", photoCount=" + photos.size());
                    continue;
                }
                BufferedImage photo = photos.get(source);
                if (photo == null) continue;
                int x = (int) Math.round(placement.x() * sourceWidth);
                int y = (int) Math.round(placement.y() * sourceHeight);
                int w = Math.max(1, (int) Math.round(placement.width() * sourceWidth));
                int h = Math.max(1, (int) Math.round(placement.height() * sourceHeight));
                drawPhoto(g, photo, x, y, w, h, placement.cropToFill(), placement.rotation());
            }

            g.setComposite(AlphaComposite.SrcOver);
            if (frameAsset != null) g.drawImage(frameAsset, 0, 0, sourceWidth, sourceHeight, null);
            else drawFallbackFrame(g, definition);
            g.setClip(oldClip);
        } finally { g.dispose(); }

        if (!ImageIO.write(out, "png", output)) throw new IllegalStateException("PNG writer is not available.");
        DebugLog.info("Frame rendered: " + output.getAbsolutePath() + " source=" + sourceWidth + "x" + sourceHeight
                + " target=" + targetWidth + "x" + targetHeight + " placements=" + definition.getPlacements().size());
        if (Math.abs(sourceWidth / (double) sourceHeight - targetWidth / (double) targetHeight) > 0.005) {
            DebugLog.warn("Paper ratio differs from frame ratio; frame is center-cropped. Check selected paper dimensions/orientation.");
        }
        return output;
    }

    private static void drawPhoto(Graphics2D g, BufferedImage photo, int x, int y, int w, int h,
                                  boolean crop, double rotationDegrees) {
        Graphics2D slot = (Graphics2D) g.create();
        try {
            slot.rotate(Math.toRadians(rotationDegrees), x + w / 2.0, y + h / 2.0);
            slot.clipRect(x, y, w, h);
            double scale = crop
                    ? Math.max(w / (double) photo.getWidth(), h / (double) photo.getHeight())
                    : Math.min(w / (double) photo.getWidth(), h / (double) photo.getHeight());
            int dw = Math.max(1, (int) Math.round(photo.getWidth() * scale));
            int dh = Math.max(1, (int) Math.round(photo.getHeight() * scale));
            int dx = x + (w - dw) / 2, dy = y + (h - dh) / 2;
            slot.drawImage(photo, dx, dy, dw, dh, null);
        } finally { slot.dispose(); }
    }

    private static void drawFallbackFrame(Graphics2D g, FrameDefinition d) {
        g.setColor(new Color(35, 35, 42));
        g.setStroke(new BasicStroke(14));
        g.drawRect(7, 7, d.getWidth() - 14, d.getHeight() - 14);
        g.setFont(new Font("SansSerif", Font.BOLD, 44));
        g.drawString(d.getName(), 55, d.getHeight() - 55);
    }
}
