package com.phomoria.ui;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import java.util.List;

/**
 * Writes the processed session photos as a looping animated GIF.
 * Uses the JDK ImageIO GIF writer; no third-party dependency is required.
 */
public final class GifGenerator {
    private GifGenerator() {
    }

    public static void write(List<BufferedImage> frames, File output, int delayMillis)
            throws Exception {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("GIF requires at least one frame.");
        }
        if (output == null) {
            throw new IllegalArgumentException("GIF output file is required.");
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix("gif");
        if (!writers.hasNext()) {
            throw new IllegalStateException("JDK GIF ImageIO writer is unavailable.");
        }

        ImageWriter writer = writers.next();
        int delayCentiseconds = Math.max(2, Math.round(delayMillis / 10.0f));
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);

            for (int index = 0; index < frames.size(); index++) {
                BufferedImage frame = frames.get(index);
                if (frame == null) continue;

                BufferedImage compatible = new BufferedImage(
                        frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D graphics = compatible.createGraphics();
                try {
                    graphics.drawImage(frame, 0, 0, null);
                } finally {
                    graphics.dispose();
                }

                ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(compatible);
                IIOMetadata metadata = writer.getDefaultImageMetadata(type, writer.getDefaultWriteParam());
                configureMetadata(metadata, delayCentiseconds, index == 0);

                writer.writeToSequence(new IIOImage(compatible, null, metadata), writer.getDefaultWriteParam());
                compatible.flush();
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static void configureMetadata(IIOMetadata metadata, int delay, boolean firstFrame)
            throws Exception {
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);

        IIOMetadataNode control = getNode(root, "GraphicControlExtension");
        control.setAttribute("disposalMethod", "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "FALSE");
        control.setAttribute("delayTime", Integer.toString(delay));
        control.setAttribute("transparentColorIndex", "0");

        if (firstFrame) {
            IIOMetadataNode extensions = getNode(root, "ApplicationExtensions");
            IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
            app.setAttribute("applicationID", "NETSCAPE");
            app.setAttribute("authenticationCode", "2.0");
            app.setUserObject(new byte[]{0x1, 0x0, 0x0});
            extensions.appendChild(app);
        }
        metadata.setFromTree(format, root);
    }

    private static IIOMetadataNode getNode(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (name.equals(root.item(i).getNodeName())) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
