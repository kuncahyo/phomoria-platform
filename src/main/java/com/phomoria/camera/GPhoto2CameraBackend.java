package com.phomoria.camera;

import com.phomoria.debug.DebugLog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * gPhoto2 backend for the current MSYS2 UCRT64 proof-of-concept.
 *
 * Important: this build intentionally launches gPhoto2 through the MSYS2
 * login shell because EOS detection was proven to work there while direct
 * Windows ProcessBuilder execution did not detect the camera.
 */
public final class GPhoto2CameraBackend implements CameraBackend {

    private static final int DETECT_TIMEOUT_SECONDS = 15;
    private static final int CAPTURE_TIMEOUT_SECONDS = 45;

    private final String requestedCameraName;

    public GPhoto2CameraBackend(String cameraName) {
        this.requestedCameraName = normalizeCameraName(cameraName);
    }

    @Override
    public String getId() {
        return "gphoto2:" + requestedCameraName;
    }

    @Override
    public String getDisplayName() {
        String name = requestedCameraName.isBlank()
                ? "gPhoto2 Camera"
                : requestedCameraName;
        return name.endsWith(" [gPhoto2]") ? name : name + " [gPhoto2]";
    }

    @Override
    public boolean isAvailable() {
        if (requestedCameraName.isBlank()) {
            return !detectCameraNames().isEmpty();
        }

        for (String detected : detectCameraNames()) {
            if (detected.equalsIgnoreCase(requestedCameraName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void open() throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "gPhoto2 camera tidak terdeteksi: " + getDisplayName()
            );
        }

        DebugLog.info("gPhoto2 camera ready: " + getDisplayName());
    }

    @Override
    public void close() {
        // gPhoto2 commands are short-lived processes in this diagnostic build.
    }

    @Override
    public BufferedImage capture() throws Exception {
        Path tempDir = Files.createTempDirectory("phomoria-gphoto2-capture-");
        Path expectedFile = tempDir.resolve("capture.jpg");

        try {
            DebugLog.info("gPhoto2 capture started. camera=" + getDisplayName());

            List<String> args = new ArrayList<>();

            if (!requestedCameraName.isBlank()) {
                args.add("--camera");
                args.add(requestedCameraName);
            }

            args.add("--capture-image-and-download");
            args.add("--force-overwrite");
            args.add("--filename");
            args.add("capture.jpg");

            ProcessResult result = runMsysShell(
                    tempDir,
                    CAPTURE_TIMEOUT_SECONDS,
                    args.toArray(String[]::new)
            );

            DebugLog.info(
                    "gPhoto2 capture exit=" + result.exitCode
                            + " stdout=" + quote(result.stdout)
                            + " stderr=" + quote(result.stderr)
            );

            if (result.exitCode != 0) {
                throw new IllegalStateException(
                        "gPhoto2 capture gagal. exit=" + result.exitCode
                                + " stderr=" + result.stderr.trim()
                );
            }

            Path imageFile = expectedFile;

            if (!Files.isRegularFile(imageFile)) {
                imageFile = Files.list(tempDir)
                        .filter(Files::isRegularFile)
                        .filter(GPhoto2CameraBackend::looksLikeImage)
                        .max(Comparator.comparingLong(GPhoto2CameraBackend::safeSize))
                        .orElse(null);
            }

            if (imageFile == null || !Files.isRegularFile(imageFile)) {
                throw new IllegalStateException(
                        "gPhoto2 selesai tetapi file foto tidak ditemukan di " + tempDir
                );
            }

            BufferedImage image = ImageIO.read(imageFile.toFile());

            if (image == null) {
                throw new IllegalStateException(
                        "File hasil gPhoto2 tidak dapat dibaca sebagai gambar: " + imageFile
                );
            }

            DebugLog.info(
                    "gPhoto2 capture decoded: "
                            + image.getWidth() + "x" + image.getHeight()
            );

            return image;

        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Override
    public BufferedImage getLiveImage() {
        throw new UnsupportedOperationException(
                "gPhoto2 live view belum diaktifkan pada capture-test build."
        );
    }

    public static List<String> detectCameraNames() {
        if (!msysBash().isFile()) {
            DebugLog.warn(
                    "gPhoto2 MSYS bash tidak ditemukan: "
                            + msysBash().getAbsolutePath()
            );
            return List.of();
        }

        try {
            ProcessResult result = runMsysShell(
                    null,
                    DETECT_TIMEOUT_SECONDS,
                    "--auto-detect"
            );

            DebugLog.info(
                    "gPhoto2 detect exit=" + result.exitCode
                            + " stdout=" + quote(result.stdout)
                            + " stderr=" + quote(result.stderr)
            );

            if (result.exitCode != 0) {
                return List.of();
            }

            List<String> names = parseCameraNames(result.stdout);
            DebugLog.info("gPhoto2 detected cameras=" + names);
            return names;

        } catch (Exception ex) {
            DebugLog.error("gPhoto2 detection failed.", ex);
            return List.of();
        }
    }

    private static ProcessResult runMsysShell(
            Path workingDirectory,
            int timeoutSeconds,
            String... args
    ) throws Exception {

        File bash = msysBash();

        if (!bash.isFile()) {
            throw new IllegalStateException(
                    "MSYS2 bash tidak ditemukan: " + bash.getAbsolutePath()
            );
        }

        StringBuilder shellCommand = new StringBuilder();

        if (workingDirectory != null) {
            shellCommand
                    .append("cd ")
                    .append(shellQuote(toMsysPath(workingDirectory)))
                    .append(" && ");
        }

        shellCommand.append("/ucrt64/bin/gphoto2.exe");

        for (String arg : args) {
            shellCommand.append(' ').append(shellQuote(arg));
        }

        List<String> command = List.of(
                bash.getAbsolutePath(),
                "--login",
                "-c",
                shellCommand.toString()
        );

        DebugLog.info("gPhoto2 launching MSYS shell: " + command);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("MSYSTEM", "UCRT64");
        builder.environment().put("MSYS2_PATH_TYPE", "inherit");
        builder.environment().put("CHERE_INVOKING", "1");

        return runProcess(builder, timeoutSeconds);
    }

    private static ProcessResult runProcess(
            ProcessBuilder builder,
            int timeoutSeconds
    ) throws Exception {

        Process process = builder.start();

        StreamCollector stdout = new StreamCollector(process.getInputStream());
        StreamCollector stderr = new StreamCollector(process.getErrorStream());

        Thread outThread = new Thread(stdout, "gphoto-capture-stdout");
        Thread errThread = new Thread(stderr, "gphoto-capture-stderr");

        outThread.start();
        errThread.start();

        boolean finished = process.waitFor(
                timeoutSeconds,
                TimeUnit.SECONDS
        );

        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            throw new IllegalStateException(
                    "gPhoto2 process timeout setelah " + timeoutSeconds + " detik"
            );
        }

        outThread.join(2000);
        errThread.join(2000);

        return new ProcessResult(
                process.exitValue(),
                stdout.text(),
                stderr.text()
        );
    }

    private static List<String> parseCameraNames(String output) {
        List<String> names = new ArrayList<>();

        if (output == null || output.isBlank()) {
            return names;
        }

        String[] lines = output.replace("\r", "").split("\n");

        for (String raw : lines) {
            String line = raw.trim();

            if (line.isBlank()
                    || line.equalsIgnoreCase("Model Port")
                    || line.matches("-+")) {
                continue;
            }

            int usbIndex = line.lastIndexOf("usb:");

            if (usbIndex <= 0) {
                continue;
            }

            String name = line.substring(0, usbIndex).trim();
            String port = line.substring(usbIndex).trim();

            if (!name.isBlank() && port.matches("usb:\\d+,\\d+")) {
                names.add(name);
            }
        }

        return names;
    }

    private static File msysBash() {
        String configured = System.getProperty("phomoria.msys2.bash");

        if (configured == null || configured.isBlank()) {
            configured = System.getenv("PHOMORIA_MSYS2_BASH");
        }

        if (configured != null && !configured.isBlank()) {
            return new File(configured.trim());
        }

        return new File("C:\\msys64\\usr\\bin\\bash.exe");
    }

    private static String normalizeCameraName(String value) {
        if (value == null) {
            return "";
        }

        String name = value.trim();
        String suffix = " [gPhoto2]";

        if (name.endsWith(suffix)) {
            name = name.substring(0, name.length() - suffix.length()).trim();
        }

        if (name.startsWith("gphoto2:")) {
            name = name.substring("gphoto2:".length()).trim();
        }

        return name;
    }

    private static String toMsysPath(Path path) {
        String value = path.toAbsolutePath().normalize().toString();

        if (value.length() >= 3
                && Character.isLetter(value.charAt(0))
                && value.charAt(1) == ':'
                && (value.charAt(2) == '\\' || value.charAt(2) == '/')) {

            char drive = Character.toLowerCase(value.charAt(0));
            String rest = value.substring(3).replace('\\', '/');
            return "/" + drive + "/" + rest;
        }

        return value.replace('\\', '/');
    }

    private static String shellQuote(String value) {
        String safe = value == null ? "" : value;
        return "'" + safe.replace("'", "'\\''") + "'";
    }

    private static boolean looksLikeImage(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".png");
    }

    private static long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ex) {
            return -1L;
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }

        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ex) {
            DebugLog.warn(
                    "Temporary gPhoto2 folder tidak dapat dibersihkan: "
                            + ex.getMessage()
            );
        }
    }

    private static String quote(String value) {
        if (value == null) {
            return "<null>";
        }

        return "\""
                + value
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                + "\"";
    }

    private record ProcessResult(
            int exitCode,
            String stdout,
            String stderr
    ) {
    }

    private static final class StreamCollector implements Runnable {

        private final InputStream input;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private StreamCollector(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            try (InputStream in = input) {
                byte[] bytes = new byte[4096];
                int count;

                while ((count = in.read(bytes)) != -1) {
                    buffer.write(bytes, 0, count);
                }
            } catch (Exception ex) {
                DebugLog.warn(
                        "gPhoto2 stream reader failed: " + ex.getMessage()
                );
            }
        }

        private String text() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
