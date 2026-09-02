package com.phomoria.camera;

import com.phomoria.debug.DebugLog;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Isolated diagnostic for testing whether Canon EOS Live View frames
 * can be delivered to Java as BufferedImage without touching MainScreen
 * or CameraManager's production capture flow.
 *
 * IMPORTANT:
 * This diagnostic intentionally uses the same MSYS2 shell mechanism that
 * is already proven to detect the EOS 700D in the current application.
 */
public final class GPhoto2LiveFrameDiagnostic {

    private static final int DETECT_TIMEOUT_SECONDS = 15;
    private static final int LIVE_TIMEOUT_SECONDS = 20;

    private GPhoto2LiveFrameDiagnostic() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LiveWindow window = new LiveWindow();
            window.setVisible(true);

            Thread worker = new Thread(
                    () -> runDiagnostic(window),
                    "gphoto2-live-diagnostic"
            );
            worker.setDaemon(true);
            worker.start();
        });
    }

    private static void runDiagnostic(LiveWindow window) {
        try {
            log(window, "=== gPhoto2 Live Frame Diagnostic ===");
            log(window, "Scanning camera...");

            List<String> cameras = detectCameraNames();

            if (cameras.isEmpty()) {
                log(window, "FAIL: No gPhoto2 camera detected.");
                return;
            }

            log(window, "Detected camera(s): " + cameras);

            String cameraName = cameras.get(0);

            log(window, "Using camera: " + cameraName);
            log(window, "Starting Live View for " + LIVE_TIMEOUT_SECONDS + " seconds...");
            log(window, "The first frame may take a moment.");

            ProcessResult result = startLiveCapture(cameraName, window);

            log(window, "Live process exited. exit=" + result.exitCode);
            log(window, "stdout=" + quote(result.stdout));
            log(window, "stderr=" + quote(result.stderr));

        } catch (Exception ex) {
            log(window, "FAIL: " + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
            DebugLog.error("gPhoto2 live frame diagnostic failed.", ex);
        }
    }

    private static List<String> detectCameraNames() throws Exception {
        ProcessResult result = runMsysShell(
                null,
                DETECT_TIMEOUT_SECONDS,
                "--auto-detect"
        );

        logStatic(
                "Detection exit=" + result.exitCode
                        + " stdout=" + quote(result.stdout)
                        + " stderr=" + quote(result.stderr)
        );

        if (result.exitCode != 0) {
            return List.of();
        }

        return parseCameraNames(result.stdout);
    }

    private static ProcessResult startLiveCapture(
            String cameraName,
            LiveWindow window
    ) throws Exception {

        FilePair files = createTempFiles();

        try {
            /*
             * We deliberately use the same proven gPhoto2 operation:
             *
             *   --capture-movie
             *
             * Earlier testing established that EOS 700D produces dozens
             * of Live View frames with this operation.
             *
             * The important difference here is that the generated MJPEG
             * file is tailed while gPhoto2 is still running. This lets Java
             * attempt realtime JPEG extraction instead of waiting for the
             * whole movie to finish.
             */
            List<String> args = new ArrayList<>();

            args.add("--camera");
            args.add(cameraName);

            args.add("--capture-movie=20s");
            args.add("--filename");
            args.add(files.mjpegFile.getFileName().toString());

            ProcessBuilder builder = buildMsysShell(
                    files.directory,
                    args.toArray(String[]::new)
            );

            DebugLog.info("Starting gPhoto2 Live View process.");

            Process process = builder.start();

            StreamCollector stdout = new StreamCollector(process.getInputStream());
            StreamCollector stderr = new StreamCollector(process.getErrorStream());

            Thread outThread = new Thread(stdout, "gphoto-live-stdout");
            Thread errThread = new Thread(stderr, "gphoto-live-stderr");

            outThread.start();
            errThread.start();

            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(LIVE_TIMEOUT_SECONDS);

            long position = 0;
            int frames = 0;
            long firstFrameAt = 0;
            long lastReport = System.nanoTime();

            /*
             * Some gPhoto2 versions/cameras ignore --filename for
             * capture-movie and create movie.mjpg. Therefore monitor both
             * the requested filename and movie.mjpg.
             */
            Path actualFile = null;

            ByteArrayOutputStream pending = new ByteArrayOutputStream();

            while (System.nanoTime() < deadline) {

                if (actualFile == null) {
                    actualFile = findMovieFile(files.directory, files.mjpegFile);
                }

                if (actualFile != null && Files.exists(actualFile)) {
                    long size = Files.size(actualFile);

                    if (size > position) {
                        byte[] chunk = readRange(
                                actualFile,
                                position,
                                size
                        );

                        position = size;
                        pending.write(chunk);

                        while (true) {
                            byte[] jpeg = extractJpeg(pending);

                            if (jpeg == null) {
                                break;
                            }

                            BufferedImage image = ImageIO.read(
                                    new java.io.ByteArrayInputStream(jpeg)
                            );

                            if (image != null) {
                                frames++;

                                if (firstFrameAt == 0) {
                                    firstFrameAt = System.nanoTime();
                                }

                                BufferedImage displayImage = image;

                                SwingUtilities.invokeLater(
                                        () -> window.setImage(displayImage)
                                );

                                long now = System.nanoTime();

                                if (now - lastReport
                                        >= TimeUnit.SECONDS.toNanos(1)) {

                                    double elapsed =
                                            (now - firstFrameAt)
                                                    / 1_000_000_000.0;

                                    double fps = elapsed > 0
                                            ? frames / elapsed
                                            : 0;

                                    logStatic(
                                            "Frames=" + frames
                                                    + " FPS="
                                                    + String.format(
                                                    java.util.Locale.US,
                                                    "%.1f",
                                                    fps
                                            )
                                                    + " latest="
                                                    + image.getWidth()
                                                    + "x"
                                                    + image.getHeight()
                                    );

                                    int shownFrames = frames;

                                    SwingUtilities.invokeLater(
                                            () -> window.setStats(
                                                    shownFrames,
                                                    fps
                                            )
                                    );

                                    lastReport = now;
                                }
                            }
                        }
                    }
                }

                if (!process.isAlive()) {
                    break;
                }

                Thread.sleep(20);
            }

            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }

            outThread.join(2000);
            errThread.join(2000);

            if (frames == 0) {
                logStatic(
                        "NO JPEG FRAME decoded from the growing movie file."
                );
            } else {
                logStatic(
                        "LIVE FRAME SUCCESS. decodedFrames=" + frames
                );
            }

            return new ProcessResult(
                    process.exitValue(),
                    stdout.text(),
                    stderr.text()
            );

        } finally {
            deleteRecursively(files.directory);
        }
    }

    private static FilePair createTempFiles() throws Exception {
        Path directory = Files.createTempDirectory(
                "phomoria-gphoto2-live-"
        );

        return new FilePair(
                directory,
                directory.resolve("live-test.mjpg")
        );
    }

    private static Path findMovieFile(
            Path directory,
            Path requested
    ) {
        if (Files.isRegularFile(requested)) {
            return requested;
        }

        Path fallback = directory.resolve("movie.mjpg");

        if (Files.isRegularFile(fallback)) {
            return fallback;
        }

        return null;
    }

    private static byte[] readRange(
            Path file,
            long start,
            long end
    ) throws Exception {

        long length = end - start;

        if (length <= 0 || length > Integer.MAX_VALUE) {
            return new byte[0];
        }

        try (var input = Files.newInputStream(file)) {
            long skipped = 0;

            while (skipped < start) {
                long value = input.skip(start - skipped);

                if (value <= 0) {
                    break;
                }

                skipped += value;
            }

            if (skipped != start) {
                return new byte[0];
            }

            byte[] data = input.readNBytes((int) length);
            return data;
        }
    }

    /**
     * Extracts one complete JPEG from a growing MJPEG byte buffer.
     * JPEG boundaries are identified by SOI FFD8 and EOI FFD9 markers.
     */
    private static byte[] extractJpeg(
            ByteArrayOutputStream pending
    ) {

        byte[] data = pending.toByteArray();

        int start = -1;

        for (int i = 0; i < data.length - 1; i++) {
            if ((data[i] & 0xFF) == 0xFF
                    && (data[i + 1] & 0xFF) == 0xD8) {
                start = i;
                break;
            }
        }

        if (start < 0) {
            /*
             * Keep only a possible trailing 0xFF so a marker split across
             * two file reads is not lost.
             */
            if (data.length > 1) {
                pending.reset();

                if ((data[data.length - 1] & 0xFF) == 0xFF) {
                    pending.write(0xFF);
                }
            }

            return null;
        }

        int end = -1;

        for (int i = start + 2; i < data.length - 1; i++) {
            if ((data[i] & 0xFF) == 0xFF
                    && (data[i + 1] & 0xFF) == 0xD9) {
                end = i + 2;
                break;
            }
        }

        if (end < 0) {
            /*
             * Drop garbage before SOI but retain the incomplete JPEG.
             */
            if (start > 0) {
                ByteArrayOutputStream retained =
                        new ByteArrayOutputStream();

                retained.write(
                        data,
                        start,
                        data.length - start
                );

                pending.reset();

                byte[] retainedBytes = retained.toByteArray();
                pending.write(
                        retainedBytes,
                        0,
                        retainedBytes.length
                );
            }

            return null;
        }

        byte[] jpeg = new byte[end - start];
        System.arraycopy(
                data,
                start,
                jpeg,
                0,
                jpeg.length
        );

        pending.reset();

        if (end < data.length) {
            pending.write(
                    data,
                    end,
                    data.length - end
            );
        }

        return jpeg;
    }

    private static ProcessBuilder buildMsysShell(
            Path workingDirectory,
            String... args
    ) {

        java.io.File bash = msysBash();

        StringBuilder shellCommand = new StringBuilder();

        if (workingDirectory != null) {
            shellCommand
                    .append("cd ")
                    .append(shellQuote(toMsysPath(workingDirectory)))
                    .append(" && ");
        }

        shellCommand.append("/ucrt64/bin/gphoto2.exe");

        for (String arg : args) {
            shellCommand
                    .append(' ')
                    .append(shellQuote(arg));
        }

        List<String> command = List.of(
                bash.getAbsolutePath(),
                "--login",
                "-c",
                shellCommand.toString()
        );

        DebugLog.info(
                "gPhoto2 Live View launching MSYS shell: "
                        + command
        );

        ProcessBuilder builder =
                new ProcessBuilder(command);

        builder.environment().put(
                "MSYSTEM",
                "UCRT64"
        );

        builder.environment().put(
                "MSYS2_PATH_TYPE",
                "inherit"
        );

        builder.environment().put(
                "CHERE_INVOKING",
                "1"
        );

        return builder;
    }

    private static ProcessResult runMsysShell(
            Path workingDirectory,
            int timeoutSeconds,
            String... args
    ) throws Exception {

        ProcessBuilder builder =
                buildMsysShell(
                        workingDirectory,
                        args
                );

        return runProcess(
                builder,
                timeoutSeconds
        );
    }

    private static ProcessResult runProcess(
            ProcessBuilder builder,
            int timeoutSeconds
    ) throws Exception {

        Process process = builder.start();

        StreamCollector stdout =
                new StreamCollector(
                        process.getInputStream()
                );

        StreamCollector stderr =
                new StreamCollector(
                        process.getErrorStream()
                );

        Thread outThread =
                new Thread(
                        stdout,
                        "gphoto-detect-stdout"
                );

        Thread errThread =
                new Thread(
                        stderr,
                        "gphoto-detect-stderr"
                );

        outThread.start();
        errThread.start();

        boolean finished =
                process.waitFor(
                        timeoutSeconds,
                        TimeUnit.SECONDS
                );

        if (!finished) {
            process.destroyForcibly();
            process.waitFor(
                    2,
                    TimeUnit.SECONDS
            );

            throw new IllegalStateException(
                    "gPhoto2 process timeout setelah "
                            + timeoutSeconds
                            + " detik"
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

    private static List<String> parseCameraNames(
            String output
    ) {

        List<String> names = new ArrayList<>();

        if (output == null || output.isBlank()) {
            return names;
        }

        String[] lines =
                output.replace("\r", "")
                        .split("\n");

        for (String raw : lines) {

            String line = raw.trim();

            if (line.isBlank()
                    || line.equalsIgnoreCase("Model Port")
                    || line.matches("-+")) {
                continue;
            }

            int usbIndex =
                    line.lastIndexOf("usb:");

            if (usbIndex <= 0) {
                continue;
            }

            String name =
                    line.substring(
                            0,
                            usbIndex
                    ).trim();

            String port =
                    line.substring(
                            usbIndex
                    ).trim();

            if (!name.isBlank()
                    && port.matches(
                    "usb:\\d+,\\d+"
            )) {
                names.add(name);
            }
        }

        return names;
    }

    private static java.io.File msysBash() {

        String configured =
                System.getProperty(
                        "phomoria.msys2.bash"
                );

        if (configured == null
                || configured.isBlank()) {

            configured =
                    System.getenv(
                            "PHOMORIA_MSYS2_BASH"
                    );
        }

        if (configured != null
                && !configured.isBlank()) {

            return new java.io.File(
                    configured.trim()
            );
        }

        return new java.io.File(
                "C:\\msys64\\usr\\bin\\bash.exe"
        );
    }

    private static String toMsysPath(
            Path path
    ) {

        String value =
                path.toAbsolutePath()
                        .normalize()
                        .toString();

        if (value.length() >= 3
                && Character.isLetter(
                value.charAt(0)
        )
                && value.charAt(1) == ':'
                && (value.charAt(2) == '\\'
                || value.charAt(2) == '/')) {

            char drive =
                    Character.toLowerCase(
                            value.charAt(0)
                    );

            String rest =
                    value.substring(3)
                            .replace(
                                    '\\',
                                    '/'
                            );

            return "/"
                    + drive
                    + "/"
                    + rest;
        }

        return value.replace(
                '\\',
                '/'
        );
    }

    private static String shellQuote(
            String value
    ) {

        String safe =
                value == null
                        ? ""
                        : value;

        return "'"
                + safe.replace(
                "'",
                "'\\''"
        )
                + "'";
    }

    private static void deleteRecursively(
            Path root
    ) {

        if (root == null
                || !Files.exists(root)) {
            return;
        }

        try (var stream =
                     Files.walk(root)) {

            stream.sorted(
                            java.util.Comparator.reverseOrder()
                    )
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(
                                            path
                                    );
                                } catch (Exception ignored) {
                                }
                            }
                    );

        } catch (Exception ex) {
            DebugLog.warn(
                    "Temporary gPhoto2 folder "
                            + "tidak dapat dibersihkan: "
                            + ex.getMessage()
            );
        }
    }

    private static String quote(
            String value
    ) {

        if (value == null) {
            return "<null>";
        }

        return "\""
                + value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\n",
                        "\\n"
                )
                + "\"";
    }

    private static void logStatic(
            String message
    ) {
        DebugLog.info(message);
        System.out.println(message);
    }

    private static void log(
            LiveWindow window,
            String message
    ) {
        logStatic(message);

        SwingUtilities.invokeLater(
                () -> window.appendLog(message)
        );
    }

    private record FilePair(
            Path directory,
            Path mjpegFile
    ) {
    }

    private record ProcessResult(
            int exitCode,
            String stdout,
            String stderr
    ) {
    }

    private static final class StreamCollector
            implements Runnable {

        private final InputStream input;

        private final ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        private StreamCollector(
                InputStream input
        ) {
            this.input = input;
        }

        @Override
        public void run() {

            try (InputStream in = input) {

                byte[] bytes =
                        new byte[4096];

                int count;

                while ((count =
                        in.read(bytes)) != -1) {

                    buffer.write(
                            bytes,
                            0,
                            count
                    );
                }

            } catch (Exception ex) {

                DebugLog.warn(
                        "gPhoto2 stream reader failed: "
                                + ex.getMessage()
                );
            }
        }

        private String text() {
            return buffer.toString(
                    StandardCharsets.UTF_8
            );
        }
    }

    private static final class LiveWindow
            extends JFrame {

        private final JLabel imageLabel =
                new JLabel(
                        "Menunggu Live View...",
                        SwingConstants.CENTER
                );

        private final JLabel stats =
                new JLabel(
                        "Frames: 0 | FPS: 0.0"
                );

        private final JTextArea log =
                new JTextArea();

        private LiveWindow() {

            super(
                    "Phomoria - gPhoto2 Live Frame Diagnostic"
            );

            setDefaultCloseOperation(
                    JFrame.DISPOSE_ON_CLOSE
            );

            setLayout(
                    new BorderLayout(
                            8,
                            8
                    )
            );

            imageLabel.setPreferredSize(
                    new Dimension(
                            960,
                            540
                    )
            );

            imageLabel.setBackground(
                    Color.BLACK
            );

            imageLabel.setOpaque(true);

            add(
                    imageLabel,
                    BorderLayout.CENTER
            );

            JPanel bottom =
                    new JPanel(
                            new BorderLayout(
                                    8,
                                    8
                            )
                    );

            bottom.add(
                    stats,
                    BorderLayout.NORTH
            );

            log.setEditable(false);
            log.setRows(7);

            bottom.add(
                    new JScrollPane(log),
                    BorderLayout.CENTER
            );

            add(
                    bottom,
                    BorderLayout.SOUTH
            );

            setSize(
                    1000,
                    760
            );

            setLocationRelativeTo(null);
        }

        private void setImage(
                BufferedImage image
        ) {

            if (image == null) {
                return;
            }

            int maxWidth =
                    Math.max(
                            1,
                            imageLabel.getWidth()
                    );

            int maxHeight =
                    Math.max(
                            1,
                            imageLabel.getHeight()
                    );

            double scale =
                    Math.min(
                            maxWidth
                                    / (double)
                                    image.getWidth(),
                            maxHeight
                                    / (double)
                                    image.getHeight()
                    );

            scale =
                    Math.min(
                            1.0,
                            scale
                    );

            int width =
                    Math.max(
                            1,
                            (int)
                                    Math.round(
                                            image.getWidth()
                                                    * scale
                                    )
                    );

            int height =
                    Math.max(
                            1,
                            (int)
                                    Math.round(
                                            image.getHeight()
                                                    * scale
                                    )
                    );

            Image scaled =
                    image.getScaledInstance(
                            width,
                            height,
                            Image.SCALE_FAST
                    );

            imageLabel.setIcon(
                    new ImageIcon(
                            scaled
                    )
            );

            imageLabel.setText("");
        }

        private void setStats(
                int frames,
                double fps
        ) {

            stats.setText(
                    String.format(
                            java.util.Locale.US,
                            "Frames: %d | FPS: %.1f",
                            frames,
                            fps
                    )
            );
        }

        private void appendLog(
                String message
        ) {

            log.append(
                    message
                            + System.lineSeparator()
            );

            log.setCaretPosition(
                    log.getDocument()
                            .getLength()
            );
        }
    }
}
