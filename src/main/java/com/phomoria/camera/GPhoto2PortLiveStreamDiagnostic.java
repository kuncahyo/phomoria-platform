package com.phomoria.camera;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v21.5 diagnostic only.
 *
 * Important: the human-readable camera name is never used as the gPhoto2
 * --camera selector. We first run --auto-detect, obtain the current runtime
 * USB/PTP port (for example usb:002,005), and use that port for streaming.
 * The port is runtime data and is NOT saved to Settings.
 */
public final class GPhoto2PortLiveStreamDiagnostic {

    private static final String MSYS2_UCRT64 = "C:\\msys64\\ucrt64.exe";
    private static final String GPHOTO = "gphoto2";
    private static final int STREAM_SECONDS = 5;

    private static final Pattern CAMERA_LINE = Pattern.compile(
            "^\\s*(.+?)\\s+(usb:\\S+)\\s*$"
    );

    private GPhoto2PortLiveStreamDiagnostic() {
    }

    public static void main(String[] args) throws Exception {
        long started = System.currentTimeMillis();

        System.out.println("=== Phomoria v21.5 gPhoto2 port-based live stream diagnostic ===");
        System.out.println("Human-readable camera name is display data only.");
        System.out.println("Runtime USB/PTP port is detected dynamically from --auto-detect.");
        System.out.println();

        Detection detection = detectCamera();
        if (detection == null) {
            throw new IllegalStateException("No gPhoto2 camera detected.");
        }

        System.out.println("Detected camera : " + detection.model());
        System.out.println("Runtime port    : " + detection.port());
        System.out.println();
        System.out.println("Starting: " + GPHOTO + " --port '" + detection.port()
                + "' --stdout --capture-movie=" + STREAM_SECONDS + "s");
        System.out.println();

        Path firstFrame = Files.createTempFile("phomoria-live-first-", ".jpg");
        StreamResult result = stream(detection.port(), firstFrame);

        System.out.println();
        System.out.println("=== RESULT ===");
        System.out.println("Camera model       : " + detection.model());
        System.out.println("Runtime port       : " + detection.port());
        System.out.println("JPEG frames parsed : " + result.frames());
        System.out.println("Elapsed ms         : " + (System.currentTimeMillis() - started));
        System.out.println("First frame saved  : " + firstFrame.toAbsolutePath());
        if (result.firstImage() != null) {
            System.out.println("First frame size   : " + result.firstImage().getWidth()
                    + "x" + result.firstImage().getHeight());
        }
        System.out.println("gPhoto2 exit code  : " + result.exitCode());

        if (result.frames() <= 0 || result.firstImage() == null) {
            throw new IllegalStateException("Live stream produced no decodable JPEG frames.");
        }

        System.out.println("LIVE STREAM TEST SUCCESS.");
    }

    private static Detection detectCamera() throws Exception {
        ProcessResult result = runAndCollect(
                List.of(MSYS2_UCRT64, "-lc", GPHOTO + " --auto-detect 2>&1"),
                15,
                null
        );

        System.out.println("--auto-detect output:");
        System.out.print(result.stdout());
        if (!result.stderr().isBlank()) {
            System.out.println("--auto-detect stderr:");
            System.out.print(result.stderr());
        }

        for (String line : result.stdout().split("\\R")) {
            Matcher matcher = CAMERA_LINE.matcher(line);
            if (matcher.matches()) {
                String model = matcher.group(1).trim();
                String port = matcher.group(2).trim();
                if (!model.isBlank() && !port.isBlank()) {
                    return new Detection(model, port);
                }
            }
        }
        return null;
    }

    private static StreamResult stream(String port, Path firstFrame) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                MSYS2_UCRT64,
                "-lc",
                GPHOTO + " --port '" + port + "' --stdout --capture-movie=" + STREAM_SECONDS + "s"
        );
        pb.redirectErrorStream(false);
        Process process = pb.start();

        ByteArrayOutputStream firstJpeg = new ByteArrayOutputStream();
        int frames = 0;
        BufferedImage firstImage = null;
        byte[] pending = new byte[0];

        try (InputStream stdout = process.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stdout.read(buffer)) != -1) {
                byte[] chunk = new byte[pending.length + read];
                System.arraycopy(pending, 0, chunk, 0, pending.length);
                System.arraycopy(buffer, 0, chunk, pending.length, read);
                pending = chunk;

                while (true) {
                    int soi = findMarker(pending, 0xFF, 0xD8, 0);
                    if (soi < 0) {
                        // Keep one byte in case the marker is split across reads.
                        if (pending.length > 1) {
                            pending = new byte[]{pending[pending.length - 1]};
                        }
                        break;
                    }

                    int eoi = findMarker(pending, 0xFF, 0xD9, soi + 2);
                    if (eoi < 0) {
                        if (soi > 0) {
                            byte[] tail = new byte[pending.length - soi];
                            System.arraycopy(pending, soi, tail, 0, tail.length);
                            pending = tail;
                        }
                        break;
                    }

                    int length = eoi + 2 - soi;
                    byte[] jpeg = new byte[length];
                    System.arraycopy(pending, soi, jpeg, 0, length);
                    frames++;

                    if (firstImage == null) {
                        firstJpeg.write(jpeg);
                        firstImage = ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
                        if (firstImage != null) {
                            Files.write(firstFrame, jpeg);
                            System.out.println("First JPEG frame decoded: "
                                    + firstImage.getWidth() + "x" + firstImage.getHeight());
                        }
                    }

                    byte[] remainder = new byte[pending.length - (eoi + 2)];
                    System.arraycopy(pending, eoi + 2, remainder, 0, remainder.length);
                    pending = remainder;
                }
            }
        }

        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor(10, TimeUnit.SECONDS);
        int exitCode = process.exitValue();

        System.out.println("gPhoto2 stderr:");
        System.out.print(stderr);

        return new StreamResult(frames, firstImage, exitCode);
    }

    private static int findMarker(byte[] data, int first, int second, int start) {
        for (int i = Math.max(0, start); i + 1 < data.length; i++) {
            if ((data[i] & 0xFF) == first && (data[i + 1] & 0xFF) == second) {
                return i;
            }
        }
        return -1;
    }

    private static ProcessResult runAndCollect(List<String> command, long timeoutSeconds, File directory)
            throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (directory != null) {
            pb.directory(directory);
        }
        Process process = pb.start();

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        if (process.isAlive()) {
            process.destroyForcibly();
            throw new IllegalStateException("gPhoto2 command timed out.");
        }
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private record Detection(String model, String port) {
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }

    private record StreamResult(int frames, BufferedImage firstImage, int exitCode) {
    }
}
