package com.phomoria.camera;

import com.phomoria.debug.DebugLog;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Diagnostic only.
 *
 * IMPORTANT:
 * This test deliberately does NOT run --auto-detect first.
 * The previous diagnostic proved that auto-detect can intermittently return
 * an empty list after camera operations. Here we test the actual stream path.
 */
public final class GPhoto2LiveStreamDirectDiagnostic {

    private static final String MSYS_BASH =
            "C:\\msys64\\usr\\bin\\bash.exe";

    private static final String GPHOTO2 =
            "/ucrt64/bin/gphoto2.exe";

    private static final String CAMERA_NAME =
            "Canon EOS 700D";

    private static final int TEST_SECONDS = 5;

    private GPhoto2LiveStreamDirectDiagnostic() {
    }

    public static void main(String[] args) {
        DebugLog.info(
                "========== gPhoto2 DIRECT LIVE STREAM TEST START =========="
        );

        Process process = null;

        try {
            String shellCommand =
                    GPHOTO2
                    + " --camera "
                    + shellQuote(CAMERA_NAME)
                    + " --stdout --capture-movie="
                    + TEST_SECONDS
                    + "s";

            DebugLog.info(
                    "gPhoto2 direct stream command=" + shellCommand
            );

            ProcessBuilder builder = new ProcessBuilder(
                    MSYS_BASH,
                    "--login",
                    "-c",
                    shellCommand
            );

            builder.redirectErrorStream(false);

            long start = System.currentTimeMillis();
            process = builder.start();

            final Process liveProcess = process;

            Thread stderrThread = new Thread(() -> {
                try {
                    String stderr = readText(
                            liveProcess.getErrorStream()
                    );

                    if (!stderr.isBlank()) {
                        DebugLog.info(
                                "gPhoto2 stream stderr="
                                + quoteForLog(stderr)
                        );
                    }
                } catch (Exception ex) {
                    DebugLog.warn(
                            "gPhoto2 stderr reader failed: "
                            + ex.getMessage()
                    );
                }
            }, "gphoto2-live-stderr-reader");

            stderrThread.setDaemon(true);
            stderrThread.start();

            int frames = 0;
            long totalBytes = 0;
            byte[] firstFrame = null;

            ByteArrayOutputStream jpeg = null;
            int previous = -1;
            boolean insideJpeg = false;

            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;

                while ((read = input.read(buffer)) != -1) {
                    totalBytes += read;

                    for (int i = 0; i < read; i++) {
                        int current = buffer[i] & 0xFF;

                        if (!insideJpeg) {
                            if (previous == 0xFF && current == 0xD8) {
                                insideJpeg = true;
                                jpeg = new ByteArrayOutputStream();
                                jpeg.write(0xFF);
                                jpeg.write(0xD8);
                            }
                        } else {
                            jpeg.write(current);

                            if (previous == 0xFF && current == 0xD9) {
                                frames++;

                                if (firstFrame == null) {
                                    firstFrame = jpeg.toByteArray();
                                }

                                jpeg = null;
                                insideJpeg = false;
                            }
                        }

                        previous = current;
                    }
                }
            }

            boolean finished =
                    process.waitFor(10, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "gPhoto2 live stream process tidak selesai."
                );
            }

            stderrThread.join(2000);

            int exit = process.exitValue();
            long elapsed = System.currentTimeMillis() - start;

            DebugLog.info(
                    "gPhoto2 direct stream exit="
                    + exit
                    + " bytes="
                    + totalBytes
                    + " jpegFrames="
                    + frames
                    + " elapsedMs="
                    + elapsed
            );

            if (exit != 0) {
                throw new IllegalStateException(
                        "gPhoto2 live stream exit code=" + exit
                );
            }

            if (frames <= 0 || firstFrame == null) {
                throw new IllegalStateException(
                        "gPhoto2 selesai tetapi Java tidak menemukan "
                        + "JPEG frame dari stdout."
                );
            }

            Path outputDir = Paths.get(
                    "target",
                    "gphoto2-diagnostic"
            );

            Files.createDirectories(outputDir);

            String timestamp =
                    LocalDateTime.now().format(
                            DateTimeFormatter.ofPattern(
                                    "yyyyMMdd-HHmmss"
                            )
                    );

            Path firstFrameFile =
                    outputDir.resolve(
                            "live-direct-first-frame-"
                            + timestamp
                            + ".jpg"
                    );

            Files.write(firstFrameFile, firstFrame);

            double fps =
                    frames / Math.max(
                            0.001,
                            elapsed / 1000.0
                    );

            DebugLog.info(
                    "First JPEG frame saved: "
                    + firstFrameFile.toAbsolutePath()
            );

            DebugLog.info(
                    String.format(
                            "DIRECT LIVE STREAM TEST PASS. "
                            + "frames=%d approxFps=%.2f "
                            + "firstFrameBytes=%d totalStreamBytes=%d",
                            frames,
                            fps,
                            firstFrame.length,
                            totalBytes
                    )
            );

            DebugLog.info(
                    "========== gPhoto2 DIRECT LIVE STREAM TEST PASS =========="
            );

        } catch (Exception ex) {
            DebugLog.error(
                    "gPhoto2 DIRECT LIVE STREAM TEST FAILED.",
                    ex
            );

            DebugLog.info(
                    "========== gPhoto2 DIRECT LIVE STREAM TEST FAIL =========="
            );

            throw new RuntimeException(ex);

        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String readText(InputStream input)
            throws Exception {

        return new String(
                input.readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8
        ).replace("\r\n", "\n")
         .trim();
    }

    private static String quoteForLog(String value) {
        if (value == null) {
            return "\"\"";
        }

        String normalized =
                value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\r", "\\r")
                     .replace("\n", "\\n");

        return "\"" + normalized + "\"";
    }
}
