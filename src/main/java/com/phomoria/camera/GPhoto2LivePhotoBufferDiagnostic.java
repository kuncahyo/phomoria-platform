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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Isolated diagnostic for the next Live Photo stage.
 *
 * Tests:
 *   1. Realtime Live View -> BufferedImage
 *   2. Keep approximately 1.5 seconds of frames before a simulated shutter
 *   3. Keep approximately 1.5 seconds of frames after the simulated shutter
 *   4. Export the selected frames as JPEG files
 *
 * This does NOT modify MainScreen, CameraManager, SettingsScreen,
 * GPhoto2CameraBackend, or the production capture flow.
 *
 * IMPORTANT:
 * The "shutter" in this diagnostic is simulated by Java after the
 * configured pre-buffer period. It does NOT trigger the physical shutter.
 */
public final class GPhoto2LivePhotoBufferDiagnostic {

    private static final int DETECT_TIMEOUT_SECONDS = 15;

    private static final int PRE_SECONDS = 2;
    private static final int POST_SECONDS = 2;

    private static final int PROCESS_SECONDS =
            PRE_SECONDS + POST_SECONDS + 4;

    private static final int POLL_MILLIS = 20;

    private static final int MAX_PRE_FRAMES = 40;
    private static final int MAX_POST_FRAMES = 40;

    private GPhoto2LivePhotoBufferDiagnostic() {
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            LiveWindow window =
                    new LiveWindow();

            window.setVisible(true);

            Thread worker =
                    new Thread(
                            () -> runDiagnostic(window),
                            "gphoto2-live-photo-buffer-diagnostic"
                    );

            worker.setDaemon(true);
            worker.start();
        });
    }

    private static void runDiagnostic(
            LiveWindow window
    ) {

        try {

            log(
                    window,
                    "=== gPhoto2 Live Photo Buffer Diagnostic ==="
            );

            log(
                    window,
                    "Pre-buffer  = "
                            + PRE_SECONDS
                            + " seconds"
            );

            log(
                    window,
                    "Post-buffer = "
                            + POST_SECONDS
                            + " seconds"
            );

            log(
                    window,
                    "NOTE: shutter is SIMULATED by Java."
            );

            log(
                    window,
                    "The physical DSLR shutter is NOT triggered."
            );

            log(
                    window,
                    "Scanning camera..."
            );

            List<String> cameras =
                    detectCameraNames();

            if (cameras.isEmpty()) {

                log(
                        window,
                        "FAIL: No gPhoto2 camera detected."
                );

                return;
            }

            log(
                    window,
                    "Detected camera(s): "
                            + cameras
            );

            String cameraName =
                    cameras.get(0);

            log(
                    window,
                    "Using camera: "
                            + cameraName
            );

            BufferResult result =
                    captureBuffer(
                            cameraName,
                            window
                    );

            log(
                    window,
                    "=== BUFFER RESULT ==="
            );

            log(
                    window,
                    "Pre frames  = "
                            + result.preFrames);

            log(
                    window,
                    "Post frames = "
                            + result.postFrames);

            log(
                    window,
                    "Total selected frames = "
                            + result.totalFrames
            );

            log(
                    window,
                    "Output folder = "
                            + result.outputDirectory
            );

            if (result.preFrames > 0
                    && result.postFrames > 0) {

                log(
                        window,
                        "LIVE PHOTO BUFFER SUCCESS"
                );

            } else {

                log(
                        window,
                        "LIVE PHOTO BUFFER INCOMPLETE"
                );
            }

        } catch (Exception ex) {

            log(
                    window,
                    "FAIL: "
                            + ex.getClass()
                            .getSimpleName()
                            + ": "
                            + ex.getMessage()
            );

            DebugLog.error(
                    "gPhoto2 Live Photo buffer diagnostic failed.",
                    ex
            );
        }
    }

    private static List<String> detectCameraNames()
            throws Exception {

        ProcessResult result =
                runMsysShell(
                        null,
                        DETECT_TIMEOUT_SECONDS,
                        "--auto-detect"
                );

        logStatic(
                "Detection exit="
                        + result.exitCode
                        + " stdout="
                        + quote(result.stdout)
                        + " stderr="
                        + quote(result.stderr)
        );

        if (result.exitCode != 0) {
            return List.of();
        }

        return parseCameraNames(
                result.stdout
        );
    }

    private static BufferResult captureBuffer(
            String cameraName,
            LiveWindow window
    ) throws Exception {

        Path outputDirectory =
                Files.createTempDirectory(
                        "phomoria-live-photo-"
                );

        Path movieFile =
                outputDirectory.resolve(
                        "movie.mjpg"
                );

        try {

            log(
                    window,
                    "Temporary output: "
                            + outputDirectory
            );

            List<String> args =
                    new ArrayList<>();

            args.add("--camera");
            args.add(cameraName);

            args.add(
                    "--capture-movie="
                            + PROCESS_SECONDS
                            + "s"
            );

            args.add(
                    "--filename"
            );

            args.add(
                    "live-test.mjpg"
            );

            ProcessBuilder builder =
                    buildMsysShell(
                            outputDirectory,
                            args.toArray(
                                    String[]::new
                            )
                    );

            log(
                    window,
                    "Starting gPhoto2 Live View..."
            );

            Process process =
                    builder.start();

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
                            "live-photo-stdout"
                    );

            Thread errThread =
                    new Thread(
                            stderr,
                            "live-photo-stderr"
                    );

            outThread.start();
            errThread.start();

            long startedAt =
                    System.nanoTime();

            long shutterAt =
                    startedAt
                            + TimeUnit.SECONDS.toNanos(
                            PRE_SECONDS
                    );

            long finishAt =
                    shutterAt
                            + TimeUnit.SECONDS.toNanos(
                            POST_SECONDS
                    );

            long deadline =
                    startedAt
                            + TimeUnit.SECONDS.toNanos(
                            PROCESS_SECONDS + 5
                    );

            long position = 0;

            boolean shutterReached =
                    false;

            Deque<FrameData> preFrames =
                    new ArrayDeque<>();

            List<FrameData> postFrames =
                    new ArrayList<>();

            ByteArrayOutputStream pending =
                    new ByteArrayOutputStream();

            int decodedTotal = 0;

            long lastStats =
                    System.nanoTime();

            Path actualMovie =
                    null;

            while (
                    System.nanoTime()
                            < deadline
            ) {

                if (actualMovie == null) {

                    actualMovie =
                            findMovieFile(
                                    outputDirectory,
                                    movieFile
                            );
                }

                if (actualMovie != null
                        && Files.exists(actualMovie)) {

                    long size =
                            Files.size(
                                    actualMovie
                            );

                    if (size > position) {

                        byte[] chunk =
                                readRange(
                                        actualMovie,
                                        position,
                                        size
                                );

                        position = size;

                        pending.write(
                                chunk
                        );

                        while (true) {

                            byte[] jpeg =
                                    extractJpeg(
                                            pending
                                    );

                            if (jpeg == null) {
                                break;
                            }

                            BufferedImage image =
                                    ImageIO.read(
                                            new java.io.ByteArrayInputStream(
                                                    jpeg
                                            )
                                    );

                            if (image == null) {
                                continue;
                            }

                            long frameTime =
                                    System.nanoTime();

                            decodedTotal++;

                            FrameData frame =
                                    new FrameData(
                                            decodedTotal,
                                            frameTime,
                                            jpeg,
                                            image
                                    );

                            SwingUtilities.invokeLater(
                                    () -> window.setImage(
                                            image
                                    )
                            );

                            if (!shutterReached) {

                                if (frameTime
                                        < shutterAt) {

                                    preFrames.addLast(
                                            frame
                                    );

                                    while (
                                            preFrames.size()
                                                    > MAX_PRE_FRAMES
                                    ) {

                                        preFrames.removeFirst();
                                    }

                                } else {

                                    shutterReached =
                                            true;

                                    log(
                                            window,
                                            "=== SIMULATED SHUTTER ==="
                                    );

                                    log(
                                            window,
                                            "Frames before shutter = "
                                                    + preFrames.size()
                                    );

                                    /*
                                     * The first frame at/after the
                                     * simulated shutter belongs to
                                     * the post-shutter side.
                                     */
                                    postFrames.add(
                                            frame
                                    );
                                }

                            } else {

                                if (frameTime
                                        <= finishAt) {

                                    postFrames.add(
                                            frame
                                    );
                                }
                            }

                            long now =
                                    System.nanoTime();

                            if (now - lastStats
                                    >= TimeUnit.SECONDS.toNanos(
                                    1
                            )) {

                                int preCount =
                                        preFrames.size();

                                int postCount =
                                        postFrames.size();

                                double elapsed =
                                        (
                                                now
                                                        - startedAt
                                        )
                                                / 1_000_000_000.0;

                                double fps =
                                        elapsed > 0
                                                ? decodedTotal
                                                / elapsed
                                                : 0;

                                logStatic(
                                        String.format(
                                                Locale.US,
                                                "Decoded=%d "
                                                        + "FPS=%.1f "
                                                        + "PRE=%d "
                                                        + "POST=%d",
                                                decodedTotal,
                                                fps,
                                                preCount,
                                                postCount
                                        )
                                );

                                int shownTotal =
                                        decodedTotal;

                                SwingUtilities.invokeLater(
                                        () -> window.setStats(
                                                shownTotal,
                                                fps,
                                                preCount,
                                                postCount
                                        )
                                );

                                lastStats =
                                        now;
                            }
                        }
                    }
                }

                if (System.nanoTime()
                        >= finishAt) {

                    if (process.isAlive()) {
                        log(
                                window,
                                "Post-buffer time reached."
                        );
                    }

                    break;
                }

                if (!process.isAlive()) {
                    break;
                }

                Thread.sleep(
                        POLL_MILLIS
                );
            }

            if (process.isAlive()) {

                process.destroy();

                if (!process.waitFor(
                        2,
                        TimeUnit.SECONDS
                )) {

                    process.destroyForcibly();
                }
            }

            outThread.join(2000);
            errThread.join(2000);

            /*
             * The final file may have received a few bytes after the
             * last polling iteration. We intentionally do not use those
             * frames for timing because the diagnostic's purpose is to
             * verify the pre/post buffer while the stream is active.
             */

            List<FrameData> selected =
                    new ArrayList<>();

            selected.addAll(
                    preFrames
            );

            selected.addAll(
                    postFrames
            );

            Path exported =
                    outputDirectory.resolve(
                            "selected-frames"
                    );

            Files.createDirectories(
                    exported
            );

            int index = 0;

            for (FrameData frame : preFrames) {

                index++;

                Path file =
                        exported.resolve(
                                String.format(
                                        Locale.US,
                                        "%04d_pre.jpg",
                                        index
                                )
                        );

                Files.write(
                        file,
                        frame.jpeg
                );
            }

            index = 0;

            for (FrameData frame : postFrames) {

                index++;

                Path file =
                        exported.resolve(
                                String.format(
                                        Locale.US,
                                        "%04d_post.jpg",
                                        index
                                )
                        );

                Files.write(
                        file,
                        frame.jpeg
                );
            }

            log(
                    window,
                    "Exported "
                            + selected.size()
                            + " JPEG frames."
            );

            log(
                    window,
                    "Decoded total = "
                            + decodedTotal
            );

            log(
                    window,
                    "gPhoto2 stdout = "
                            + quote(
                            stdout.text()
                    )
            );

            log(
                    window,
                    "gPhoto2 stderr = "
                            + quote(
                            stderr.text()
                    )
            );

            /*
             * Keep the temp directory intentionally.
             * This diagnostic is for inspection. The path is printed
             * so the user can inspect selected frames.
             */
            log(
                    window,
                    "Temporary diagnostic files are kept."
            );

            return new BufferResult(
                    preFrames.size(),
                    postFrames.size(),
                    selected.size(),
                    exported.toString()
            );

        } catch (Exception ex) {

            /*
             * Keep output directory on failure as well, because it can
             * contain the MJPEG produced by gPhoto2 and is useful for
             * diagnosing timing/format issues.
             */
            logStatic(
                    "Diagnostic output retained at: "
                            + outputDirectory
            );

            throw ex;
        }
    }

    private static Path findMovieFile(
            Path directory,
            Path requested
    ) {

        if (Files.isRegularFile(
                requested
        )) {

            return requested;
        }

        Path fallback =
                directory.resolve(
                        "movie.mjpg"
                );

        if (Files.isRegularFile(
                fallback
        )) {

            return fallback;
        }

        return null;
    }

    private static byte[] readRange(
            Path file,
            long start,
            long end
    ) throws Exception {

        long length =
                end - start;

        if (length <= 0
                || length > Integer.MAX_VALUE) {

            return new byte[0];
        }

        try (InputStream input =
                     Files.newInputStream(
                             file
                     )) {

            long skipped = 0;

            while (
                    skipped < start
            ) {

                long value =
                        input.skip(
                                start - skipped
                        );

                if (value <= 0) {
                    break;
                }

                skipped += value;
            }

            if (skipped != start) {
                return new byte[0];
            }

            return input.readNBytes(
                    (int) length
            );
        }
    }

    private static byte[] extractJpeg(
            ByteArrayOutputStream pending
    ) {

        byte[] data =
                pending.toByteArray();

        int start = -1;

        for (
                int i = 0;
                i < data.length - 1;
                i++
        ) {

            if (
                    (data[i] & 0xFF)
                            == 0xFF
                            && (data[i + 1] & 0xFF)
                            == 0xD8
            ) {

                start = i;
                break;
            }
        }

        if (start < 0) {

            pending.reset();

            if (
                    data.length > 0
                            && (data[data.length - 1]
                            & 0xFF)
                            == 0xFF
            ) {

                pending.write(
                        0xFF
                );
            }

            return null;
        }

        int end = -1;

        for (
                int i = start + 2;
                i < data.length - 1;
                i++
        ) {

            if (
                    (data[i] & 0xFF)
                            == 0xFF
                            && (data[i + 1] & 0xFF)
                            == 0xD9
            ) {

                end = i + 2;
                break;
            }
        }

        if (end < 0) {

            if (start > 0) {

                byte[] retained =
                        new byte[
                                data.length - start
                        ];

                System.arraycopy(
                        data,
                        start,
                        retained,
                        0,
                        retained.length
                );

                pending.reset();

                pending.write(
                        retained,
                        0,
                        retained.length
                );
            }

            return null;
        }

        byte[] jpeg =
                new byte[
                        end - start
                ];

        System.arraycopy(
                data,
                start,
                jpeg,
                0,
                jpeg.length
        );

        pending.reset();

        if (
                end < data.length
        ) {

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

        java.io.File bash =
                msysBash();

        StringBuilder command =
                new StringBuilder();

        if (workingDirectory != null) {

            command.append(
                    "cd "
            );

            command.append(
                    shellQuote(
                            toMsysPath(
                                    workingDirectory
                            )
                    )
            );

            command.append(
                    " && "
            );
        }

        command.append(
                "/ucrt64/bin/gphoto2.exe"
        );

        for (String arg : args) {

            command.append(' ')
                    .append(
                            shellQuote(arg)
                    );
        }

        List<String> processCommand =
                List.of(
                        bash.getAbsolutePath(),
                        "--login",
                        "-c",
                        command.toString()
                );

        DebugLog.info(
                "gPhoto2 Live Photo launching MSYS shell: "
                        + processCommand
        );

        ProcessBuilder builder =
                new ProcessBuilder(
                        processCommand
                );

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

        return runProcess(
                buildMsysShell(
                        workingDirectory,
                        args
                ),
                timeoutSeconds
        );
    }

    private static ProcessResult runProcess(
            ProcessBuilder builder,
            int timeoutSeconds
    ) throws Exception {

        Process process =
                builder.start();

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
                        "gphoto-process-stdout"
                );

        Thread errThread =
                new Thread(
                        stderr,
                        "gphoto-process-stderr"
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

        List<String> names =
                new ArrayList<>();

        if (output == null
                || output.isBlank()) {

            return names;
        }

        String[] lines =
                output.replace(
                                "\r",
                                ""
                        )
                        .split("\n");

        for (String raw : lines) {

            String line =
                    raw.trim();

            if (
                    line.isBlank()
                            || line.equalsIgnoreCase(
                            "Model Port"
                    )
                            || line.matches(
                            "-+"
                    )
            ) {

                continue;
            }

            int usbIndex =
                    line.lastIndexOf(
                            "usb:"
                    );

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

            if (
                    !name.isBlank()
                            && port.matches(
                            "usb:\\d+,\\d+"
                    )
            ) {

                names.add(
                        name
                );
            }
        }

        return names;
    }

    private static java.io.File msysBash() {

        String configured =
                System.getProperty(
                        "phomoria.msys2.bash"
                );

        if (
                configured == null
                        || configured.isBlank()
        ) {

            configured =
                    System.getenv(
                            "PHOMORIA_MSYS2_BASH"
                    );
        }

        if (
                configured != null
                        && !configured.isBlank()
        ) {

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

        if (
                value.length() >= 3
                        && Character.isLetter(
                        value.charAt(0)
                )
                        && value.charAt(1) == ':'
                        && (
                        value.charAt(2) == '\\'
                                || value.charAt(2) == '/'
                )
        ) {

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

    private static void log(
            LiveWindow window,
            String message
    ) {

        logStatic(
                message
        );

        SwingUtilities.invokeLater(
                () -> window.appendLog(
                        message
                )
        );
    }

    private static void logStatic(
            String message
    ) {

        DebugLog.info(
                message
        );

        System.out.println(
                message
        );
    }

    private record FrameData(
            int sequence,
            long timeNanos,
            byte[] jpeg,
            BufferedImage image
    ) {
    }

    private record BufferResult(
            int preFrames,
            int postFrames,
            int totalFrames,
            String outputDirectory
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

            this.input =
                    input;
        }

        @Override
        public void run() {

            try (InputStream in =
                         input) {

                byte[] bytes =
                        new byte[4096];

                int count;

                while (
                        (count =
                                in.read(bytes))
                                != -1
                ) {

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
                        "Decoded: 0 | FPS: 0.0 | PRE: 0 | POST: 0"
                );

        private final JTextArea log =
                new JTextArea();

        private LiveWindow() {

            super(
                    "Phomoria - gPhoto2 Live Photo Buffer Diagnostic"
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

            imageLabel.setOpaque(
                    true
            );

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

            log.setEditable(
                    false
            );

            log.setRows(
                    8
            );

            bottom.add(
                    new JScrollPane(
                            log
                    ),
                    BorderLayout.CENTER
            );

            add(
                    bottom,
                    BorderLayout.SOUTH
            );

            setSize(
                    1000,
                    780
            );

            setLocationRelativeTo(
                    null
            );
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
                            (int) Math.round(
                                    image.getWidth()
                                            * scale
                            )
                    );

            int height =
                    Math.max(
                            1,
                            (int) Math.round(
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
                int decoded,
                double fps,
                int pre,
                int post
        ) {

            stats.setText(
                    String.format(
                            Locale.US,
                            "Decoded: %d | FPS: %.1f | PRE: %d | POST: %d",
                            decoded,
                            fps,
                            pre,
                            post
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
