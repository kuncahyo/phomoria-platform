package com.phomoria.camera;

import com.phomoria.debug.DebugLog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Temporary standalone diagnostic.
 * Run this Java file directly from NetBeans; it does not change MainScreen.
 */
public final class GPhoto2CaptureDiagnostic {

    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private GPhoto2CaptureDiagnostic() {
    }

    public static void main(String[] args) {
        DebugLog.info("========== gPhoto2 CAPTURE TEST START ==========");

        try {
            List<String> detected = GPhoto2CameraBackend.detectCameraNames();

            if (detected.isEmpty()) {
                throw new IllegalStateException(
                        "Tidak ada kamera gPhoto2 yang terdeteksi."
                );
            }

            String selected = detected.get(0);
            DebugLog.info("Diagnostic camera selected: " + selected);

            GPhoto2CameraBackend backend =
                    new GPhoto2CameraBackend(selected);

            backend.open();

            long started = System.currentTimeMillis();
            BufferedImage image = backend.capture();
            long elapsed = System.currentTimeMillis() - started;

            if (image == null) {
                throw new IllegalStateException(
                        "Capture selesai tetapi BufferedImage null."
                );
            }

            Path outputDir = Path.of(
                    System.getProperty("user.dir"),
                    "target",
                    "gphoto2-diagnostic"
            );
            Files.createDirectories(outputDir);

            Path output = outputDir.resolve(
                    "capture-"
                            + LocalDateTime.now().format(FILE_TIME)
                            + ".jpg"
            );

            boolean written = ImageIO.write(
                    image,
                    "jpg",
                    output.toFile()
            );

            if (!written) {
                throw new IllegalStateException(
                        "ImageIO tidak menemukan JPG writer."
                );
            }

            DebugLog.info(
                    "CAPTURE TEST SUCCESS. size="
                            + image.getWidth() + "x" + image.getHeight()
                            + " elapsedMs=" + elapsed
            );
            DebugLog.info(
                    "Saved diagnostic image: "
                            + output.toAbsolutePath()
            );
            DebugLog.info("========== gPhoto2 CAPTURE TEST PASS ==========");

        } catch (Exception ex) {
            DebugLog.error("gPhoto2 CAPTURE TEST FAILED.", ex);
            DebugLog.info("========== gPhoto2 CAPTURE TEST FAIL ==========");
            System.exit(1);
        }
    }
}
