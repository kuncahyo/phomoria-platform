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
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Isolated diagnostic for testing a persistent Live View buffer.
 *
 * Flow:
 *
 *   START gPhoto2 Live View
 *          |
 *          v
 *   warm-up until frames are actually arriving
 *          |
 *          v
 *   keep rolling PRE buffer continuously
 *          |
 *          v
 *   simulated shutter
 *          |
 *          v
 *   keep collecting POST frames
 *          |
 *          v
 *   export PRE and POST JPEG frames
 *
 * The physical DSLR shutter is NOT triggered.
 *
 * This diagnostic intentionally does not touch MainScreen,
 * CameraManager, SettingsScreen, or production capture flow.
 */
public final class GPhoto2LivePhotoWarmupBufferDiagnostic {

    private static final int DETECT_TIMEOUT_SECONDS = 15;

    /*
     * We intentionally use a longer movie than the required Live Photo
     * window. This gives gPhoto2 enough time to start Live View before
     * the simulated shutter is reached.
     */
    private static final int MOVIE_SECONDS = 12;

    /*
     * Once frames are actually arriving, keep this much history.
     */
    private static final double PRE_BUFFER_SECONDS = 1.5;

    /*
     * Collect this much after simulated shutter.
     */
    private static final double POST_BUFFER_SECONDS = 1.5;

    /*
     * Simulated shutter happens after the Live View stream has warmed up
     * and at least this much real frame history is available.
     */
    private static final double MIN_WARMUP_SECONDS = 2.5;

    private static final int POLL_MILLIS = 20;

    private static final int MAX_PRE_FRAMES = 40;
    private static final int MAX_POST_FRAMES = 40;

    private GPhoto2LivePhotoWarmupBufferDiagnostic() {
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            LiveWindow window =
                    new LiveWindow();

            window.setVisible(true);

            Thread worker =
                    new Thread(
                            () -> runDiagnostic(window),
                            "gphoto2-live-photo-warmup-buffer"
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
                    "=== gPhoto2 Live Photo Warmup Buffer Diagnostic ==="
            );

            log(
                    window,
                    "PRE buffer  = "
                            + PRE_BUFFER_SECONDS
                            + " seconds"
            );

            log(
                    window,
                    "POST buffer = "
                            + POST_BUFFER_SECONDS
                            + " seconds"
            );

            log(
                    window,
                    "Minimum warm-up = "
                            + MIN_WARMUP_SECONDS
                            + " seconds"
            );

            log(
                    window,
                    "Physical DSLR shutter = NOT triggered"
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
                    "PRE frames = "
                            + result.preFrames
            );

            log(
                    window,
                    "POST frames = "
                            + result.postFrames
            );

            log(
                    window,
                    "Total selected = "
                            + result.totalFrames
            );

            log(
                    window,
                    "PRE duration = "
                            + formatSeconds(
                            result.preDurationSeconds
                    )
            );

            log(
                    window,
                    "POST duration = "
                            + formatSeconds(
                            result.postDurationSeconds
                    )
            );

            log(
                    window,
                    "Output folder = "
                            + result.outputDirectory
            );

            if (
                    result.preFrames >= 5
                            && result.postFrames >= 5
            ) {

                log(
                        window,
                        "LIVE PHOTO WARMUP BUFFER SUCCESS"
                );

            } else {

                log(
                        window,
                        "LIVE PHOTO WARMUP BUFFER INCOMPLETE"
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
                    "gPhoto2 warmup buffer diagnostic failed.",
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
                        "phomoria-live-photo-warmup-"
                );

        Path movieFile =
                outputDirectory.resolve(
                        "movie.mjpg"
                );

        try {

            log(
                    window,
                    "Temporary output = "
                            + outputDirectory
            );

            List<String> args =
                    new ArrayList<>();

            args.add("--camera");
            args.add(cameraName);

            args.add(
                    "--capture-movie="
                            + MOVIE_SECONDS
                            + "s"
            );

            args.add("--filename");
            args.add("live-test.mjpg");

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
                            "live-photo-warmup-stdout"
                    );

            Thread errThread =
                    new Thread(
                            stderr,
                            "live-photo-warmup-stderr"
                    );

            outThread.start();
            errThread.start();

            long startedAt =
                    System.nanoTime();

            long deadline =
                    startedAt
                            + TimeUnit.SECONDS.toNanos(
                            MOVIE_SECONDS + 5
                    );

            long filePosition = 0;

            Path actualMovie = null;

            ByteArrayOutputStream pending =
                    new ByteArrayOutputStream();

            /*
             * Rolling frame history.
             *
             * The queue is always maintained by timestamps, not by a
             * fixed number of frames. This is important because the
             * proven EOS 700D Live View rate is around 12.5 FPS but can
             * vary.
             */
            Deque<FrameData> rollingPre =
                    new ArrayDeque<>();

            List<FrameData> postFrames =
                    new ArrayList<>();

            boolean streamWarm =
                    false;

            boolean simulatedShutter =
                    false;

            long shutterAt = 0;

            long postEndAt = 0;

            int decodedTotal = 0;

            long firstFrameAt = 0;

            long lastStats =
                    System.nanoTime();

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

                if (
                        actualMovie != null
                                && Files.exists(
                                actualMovie
                        )
                ) {

                    long size =
                            Files.size(
                                    actualMovie
                            );

                    if (size > filePosition) {

                        byte[] chunk =
                                readRange(
                                        actualMovie,
                                        filePosition,
                                        size
                                );

                        filePosition = size;

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

                            long now =
                                    System.nanoTime();

                            decodedTotal++;

                            if (firstFrameAt == 0) {

                                firstFrameAt =
                                        now;

                                log(
                                        window,
                                        "FIRST FRAME RECEIVED"
                                );
                            }

                            double streamAge =
                                    (
                                            now
                                                    - firstFrameAt
                                    )
                                            / 1_000_000_000.0;

                            FrameData frame =
                                    new FrameData(
                                            decodedTotal,
                                            now,
                                            jpeg,
                                            image
                                    );

                            BufferedImage displayImage =
                                    image;

                            SwingUtilities.invokeLater(
                                    () -> window.setImage(
                                            displayImage
                                    )
                            );

                            /*
                             * Phase 1:
                             * wait until the stream has genuinely warmed
                             * up. During warm-up, we still maintain the
                             * rolling buffer.
                             */
                            if (!streamWarm) {

                                rollingPre.addLast(
                                        frame
                                );

                                trimRollingBuffer(
                                        rollingPre,
                                        now
                                );

                                if (
                                        streamAge
                                                >= MIN_WARMUP_SECONDS
                                        && rollingPre.size()
                                                >= 5
                                ) {

                                    streamWarm =
                                            true;

                                    log(
                                            window,
                                            "LIVE VIEW WARMED UP. "
                                                    + "Rolling buffer="
                                                    + rollingPre.size()
                                                    + " frames"
                                    );

                                    /*
                                     * Simulated shutter occurs on the
                                     * next frame boundary. This guarantees
                                     * that the pre-buffer contains actual
                                     * frames rather than startup time.
                                     */
                                    shutterAt =
                                            now;

                                    simulatedShutter =
                                            true;

                                    postEndAt =
                                            shutterAt
                                                    + (long) (
                                                    POST_BUFFER_SECONDS
                                                            * 1_000_000_000L
                                            );

                                    log(
                                            window,
                                            "=== SIMULATED SHUTTER ==="
                                    );

                                    log(
                                            window,
                                            "PRE buffer at shutter = "
                                                    + rollingPre.size()
                                    );

                                }

                            } else if (
                                    !simulatedShutter
                            ) {

                                /*
                                 * Defensive path. In normal execution
                                 * streamWarm immediately triggers the
                                 * simulated shutter above.
                                 */
                                rollingPre.addLast(
                                        frame
                                );

                                trimRollingBuffer(
                                        rollingPre,
                                        now
                                );

                            } else {

                                /*
                                 * POST phase.
                                 */
                                if (
                                        now
                                                <= postEndAt
                                ) {

                                    postFrames.add(
                                            frame
                                    );
                                }
                            }

                            long current =
                                    System.nanoTime();

                            if (
                                    current
                                            - lastStats
                                            >= TimeUnit.SECONDS.toNanos(
                                            1
                                    )
                            ) {

                                double fps =
                                        firstFrameAt == 0
                                                ? 0
                                                : decodedTotal
                                                / (
                                                (
                                                        current
                                                                - firstFrameAt
                                                )
                                                        / 1_000_000_000.0
                                        );

                                int preCount =
                                        rollingPre.size();

                                int postCount =
                                        postFrames.size();

                                int total =
                                        decodedTotal;

                                SwingUtilities.invokeLater(
                                        () -> window.setStats(
                                                total,
                                                fps,
                                                preCount,
                                                postCount
                                        )
                                );

                                logStatic(
                                        String.format(
                                                Locale.US,
                                                "Decoded=%d FPS=%.1f PRE=%d POST=%d",
                                                total,
                                                fps,
                                                preCount,
                                                postCount
                                        )
                                );

                                lastStats =
                                        current;
                            }
                        }
                    }
                }

                if (
                        simulatedShutter
                                && System.nanoTime()
                                >= postEndAt
                ) {

                    log(
                            window,
                            "POST buffer duration reached."
                    );

                    break;
                }

                if (!process.isAlive()) {

                    /*
                     * gPhoto2 normally stays alive for MOVIE_SECONDS.
                     * If it exits unexpectedly before the required
                     * post-buffer, keep the collected data for diagnosis.
                     */
                    if (
                            !simulatedShutter
                                    || System.nanoTime()
                                    < postEndAt
                    ) {

                        log(
                                window,
                                "gPhoto2 exited before requested "
                                        + "buffer window completed."
                        );
                    }

                    break;
                }

                Thread.sleep(
                        POLL_MILLIS
                );
            }

            if (process.isAlive()) {

                process.destroy();

                if (
                        !process.waitFor(
                                2,
                                TimeUnit.SECONDS
                        )
                ) {

                    process.destroyForcibly();
                }
            }

            outThread.join(2000);
            errThread.join(2000);

            /*
             * Copy the rolling PRE buffer at this point. It contains
             * exactly the recent frames around the simulated shutter.
             */
            List<FrameData> preSelected =
                    new ArrayList<>(
                            rollingPre
                    );

            /*
             * Limit POST defensively to the configured duration.
             */
            List<FrameData> postSelected =
                    new ArrayList<>(
                            postFrames
                    );

            Path preDirectory =
                    outputDirectory.resolve(
                            "pre"
                    );

            Path postDirectory =
                    outputDirectory.resolve(
                            "post"
                    );

            Files.createDirectories(
                    preDirectory
            );

            Files.createDirectories(
                    postDirectory
            );

            exportFrames(
                    preDirectory,
                    preSelected,
                    "pre"
            );

            exportFrames(
                    postDirectory,
                    postSelected,
                    "post"
            );

            double preDuration =
                    durationSeconds(
                            preSelected
                    );

            double postDuration =
                    durationSeconds(
                            postSelected
                    );

            log(
                    window,
                    "Exported PRE="
                            + preSelected.size()
                            + " POST="
                            + postSelected.size()
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

            log(
                    window,
                    "Diagnostic files kept at "
                            + outputDirectory
            );

            return new BufferResult(
                    preSelected.size(),
                    postSelected.size(),
                    preSelected.size()
                            + postSelected.size(),
                    preDuration,
                    postDuration,
                    outputDirectory.toString()
            );

        } catch (Exception ex) {

            logStatic(
                    "Diagnostic output retained at: "
                            + outputDirectory
            );

            throw ex;
        }
    }

    private static void trimRollingBuffer(
            Deque<FrameData> frames,
            long newestTime
    ) {

        long minimumTime =
                newestTime
                        - TimeUnit.MILLISECONDS.toNanos(
                        (long) (
                                PRE_BUFFER_SECONDS
                                        * 1000
                        )
                );

        while (
                frames.size() > 1
                        && frames.peekFirst()
                        .timeNanos
                        < minimumTime
        ) {

            frames.removeFirst();
        }

        while (
                frames.size()
                        > MAX_PRE_FRAMES
        ) {

            frames.removeFirst();
        }
    }

    private static void exportFrames(
            Path directory,
            List<FrameData> frames,
            String type
    ) throws Exception {

        int index = 0;

        for (FrameData frame : frames) {

            index++;

            Path file =
                    directory.resolve(
                            String.format(
                                    Locale.US,
                                    "%04d_%s.jpg",
                                    index,
                                    type
                            )
                    );

            Files.write(
                    file,
                    frame.jpeg
            );
        }
    }

    private static double durationSeconds(
            List<FrameData> frames
    ) {

        if (frames.size() < 2) {
            return 0;
        }

        long first =
                frames.get(0).timeNanos;

        long last =
                frames.get(
                        frames.size() - 1
                ).timeNanos;

        return (
                last - first
        )
                / 1_000_000_000.0;
    }

    private static Path findMovieFile(
            Path directory,
            Path requested
    ) {

        if (
                Files.isRegularFile(
                        requested
                )
        ) {

            return requested;
        }

        Path fallback =
                directory.resolve(
                        "movie.mjpg"
                );

        if (
                Files.isRegularFile(
                        fallback
                )
        ) {

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

        if (
                length <= 0
                        || length > Integer.MAX_VALUE
        ) {

            return new byte[0];
        }

        try (
                InputStream input =
                        Files.newInputStream(
                                file
                        )
        ) {

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
                            shellQuote(
                                    arg
                            )
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

        if (
                output == null
                        || output.isBlank()
        ) {

            return names;
        }

        String[] lines =
                output.replace(
                                "\r",
                                ""
                        )
                        .split(
                                "\n"
                        );

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

    private static String formatSeconds(
            double value
    ) {

        return String.format(
                Locale.US,
                "%.3f s",
                value
        );
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
            double preDurationSeconds,
            double postDurationSeconds,
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

            try (
                    InputStream in =
                            input
            ) {

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
                    "Phomoria - gPhoto2 Live Photo Warmup Buffer Diagnostic"
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
                    9
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
                    800
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
