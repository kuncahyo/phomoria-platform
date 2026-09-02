package com.phomoria.camera;

import static com.phomoria.camera.GPhoto2CameraBackend.detectCameraNames;
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
 * Isolated diagnostic for testing the critical Live Photo transition:
 *
 *   Live View
 *      -> rolling PRE buffer
 *      -> physical DSLR still capture
 *      -> POST Live View buffer
 *
 * The test intentionally does NOT modify MainScreen, CameraManager,
 * SettingsScreen, or the production capture flow.
 *
 * IMPORTANT:
 * This diagnostic WILL trigger the physical shutter of the detected camera.
 * Make sure the camera is pointed somewhere safe and ready for a test photo.
 */
public final class GPhoto2LivePhotoPhysicalCaptureDiagnostic {

    private static final int DETECT_TIMEOUT_SECONDS = 15;

    private static final int MOVIE_SECONDS = 15;

    private static final double PRE_BUFFER_SECONDS = 1.5;
    private static final double POST_BUFFER_SECONDS = 1.5;

    private static final double MIN_WARMUP_SECONDS = 2.5;

    private static final int POLL_MILLIS = 20;

    private static final int MAX_PRE_FRAMES = 40;
    private static final int MAX_POST_FRAMES = 40;

    private static final int STILL_CAPTURE_TIMEOUT_SECONDS = 45;

    private GPhoto2LivePhotoPhysicalCaptureDiagnostic() {
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            LiveWindow window =
                    new LiveWindow();

            window.setVisible(true);

            Thread worker =
                    new Thread(
                            () -> runDiagnostic(window),
                            "gphoto2-live-photo-physical-capture"
                    );

            worker.setDaemon(true);
            worker.start();
        });
    }

    private static void runDiagnostic(
            LiveWindow window
    ) {

        Path outputDirectory = null;

        try {

            log(
                    window,
                    "=== gPhoto2 Live Photo Physical Capture Diagnostic ==="
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
                    "Warm-up     = "
                            + MIN_WARMUP_SECONDS
                            + " seconds"
            );

            log(
                    window,
                    "WARNING: physical DSLR shutter WILL be triggered."
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

            String cameraName =
                    cameras.get(0);

            log(
                    window,
                    "Detected camera: "
                            + cameraName
            );

            outputDirectory =
                    Files.createTempDirectory(
                            "phomoria-live-photo-physical-"
                    );

            log(
                    window,
                    "Output directory = "
                            + outputDirectory
            );

            DiagnosticResult result =
                    runLivePhotoTest(
                            cameraName,
                            outputDirectory,
                            window
                    );

            writeReport(
                    outputDirectory,
                    result
            );

            log(
                    window,
                    "=== FINAL RESULT ==="
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
                    "Still capture = "
                            + (
                            result.stillCaptureSuccess
                                    ? "SUCCESS"
                                    : "FAILED"
                    )
            );

            log(
                    window,
                    "Still size = "
                            + result.stillWidth
                            + "x"
                            + result.stillHeight
            );

            log(
                    window,
                    "Capture elapsed = "
                            + formatMillis(
                            result.captureElapsedMillis
                    )
            );

            log(
                    window,
                    "POST first frame delay from capture finish = "
                            + formatMillis(
                            result.postFirstFrameDelayMillis
                    )
            );

            log(
                    window,
                    "Output directory = "
                            + outputDirectory
            );

            if (
                    result.stillCaptureSuccess
                            && result.preFrames >= 5
                            && result.postFrames >= 5
            ) {

                log(
                        window,
                        "LIVE PHOTO PHYSICAL CAPTURE SUCCESS"
                );

            } else {

                log(
                        window,
                        "LIVE PHOTO PHYSICAL CAPTURE INCOMPLETE"
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

            if (outputDirectory != null) {

                logStatic(
                        "Diagnostic output retained at: "
                                + outputDirectory
                );
            }

            DebugLog.error(
                    "gPhoto2 physical Live Photo diagnostic failed.",
                    ex
            );
        }
    }

    private static DiagnosticResult runLivePhotoTest(
            String cameraName,
            Path outputDirectory,
            LiveWindow window
    ) throws Exception {

        Path movieFile =
                outputDirectory.resolve(
                        "movie.mjpg"
                );

        Path stillDirectory =
                outputDirectory.resolve(
                        "still"
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
                stillDirectory
        );

        Files.createDirectories(
                preDirectory
        );

        Files.createDirectories(
                postDirectory
        );

        ProcessResult liveProcessResult =
                null;

        Deque<FrameData> rollingPre =
                new ArrayDeque<>();

        List<FrameData> postFrames =
                new ArrayList<>();

        ByteArrayOutputStream pending =
                new ByteArrayOutputStream();

        Process liveProcess =
                null;

        StreamCollector liveStdout =
                null;

        StreamCollector liveStderr =
                null;

        Thread liveOutThread =
                null;

        Thread liveErrThread =
                null;

        boolean warm =
                false;

        boolean captureTriggered =
                false;

        boolean captureFinished =
                false;

        long startedAt =
                System.nanoTime();

        long firstFrameAt = 0;

        long lastFrameAt = 0;

        long shutterCommandAt = 0;

        long stillCaptureFinishedAt = 0;

        long postFirstFrameAt = 0;

        long postEndAt = 0;

        long filePosition = 0;

        int decodedTotal = 0;

        int stillWidth = 0;
        int stillHeight = 0;

        long captureElapsedMillis = 0;

        long lastStats =
                System.nanoTime();

        Path actualMovie = null;

        try {

            List<String> liveArgs =
                    new ArrayList<>();

            liveArgs.add("--camera");
            liveArgs.add(cameraName);

            liveArgs.add(
                    "--capture-movie="
                            + MOVIE_SECONDS
                            + "s"
            );

            liveArgs.add("--filename");
            liveArgs.add("live-test.mjpg");

            ProcessBuilder builder =
                    buildMsysShell(
                            outputDirectory,
                            liveArgs.toArray(
                                    String[]::new
                            )
                    );

            log(
                    window,
                    "Starting Live View..."
            );

            liveProcess =
                    builder.start();

            liveStdout =
                    new StreamCollector(
                            liveProcess.getInputStream()
                    );

            liveStderr =
                    new StreamCollector(
                            liveProcess.getErrorStream()
                    );

            liveOutThread =
                    new Thread(
                            liveStdout,
                            "physical-live-stdout"
                    );

            liveErrThread =
                    new Thread(
                            liveStderr,
                            "physical-live-stderr"
                    );

            liveOutThread.start();
            liveErrThread.start();

            long deadline =
                    startedAt
                            + TimeUnit.SECONDS.toNanos(
                            MOVIE_SECONDS + 5
                    );

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

                        filePosition =
                                size;

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
                                        "FIRST LIVE VIEW FRAME"
                                );
                            }

                            lastFrameAt =
                                    now;

                            FrameData frame =
                                    new FrameData(
                                            decodedTotal,
                                            now,
                                            jpeg,
                                            image
                                    );

                            BufferedImage display =
                                    image;

                            SwingUtilities.invokeLater(
                                    () -> window.setImage(
                                            display
                                    )
                            );

                            /*
                             * Before physical capture:
                             * always maintain the rolling PRE buffer.
                             */
                            if (!captureTriggered) {

                                rollingPre.addLast(
                                        frame
                                );

                                trimRollingBuffer(
                                        rollingPre,
                                        now
                                );

                                double age =
                                        (
                                                now
                                                        - firstFrameAt
                                        )
                                                / 1_000_000_000.0;

                                if (
                                        !warm
                                                && age
                                                >= MIN_WARMUP_SECONDS
                                                && rollingPre.size()
                                                >= 5
                                ) {

                                    warm =
                                            true;

                                    log(
                                            window,
                                            "LIVE VIEW WARMED UP."
                                    );

                                    log(
                                            window,
                                            "Rolling PRE buffer = "
                                                    + rollingPre.size()
                                                    + " frames"
                                    );

                                    /*
                                     * IMPORTANT:
                                     * Trigger the physical capture
                                     * OUTSIDE the frame parsing loop so
                                     * Java can continue consuming Live View
                                     * data in its own process.
                                     */
                                    captureTriggered =
                                            true;

                                    shutterCommandAt =
                                            System.nanoTime();

                                    log(
                                            window,
                                            "=== PHYSICAL SHUTTER TRIGGER ==="
                                    );

                                    log(
                                            window,
                                            "Triggering "
                                                    + cameraName
                                                    + "..."
                                    );

                                    StillCaptureResult still =
                                            captureStill(
                                                    cameraName,
                                                    stillDirectory,
                                                    window
                                            );

                                    stillCaptureFinishedAt =
                                            System.nanoTime();

                                    captureElapsedMillis =
                                            (
                                                    stillCaptureFinishedAt
                                                            - shutterCommandAt
                                            )
                                                    / 1_000_000L;

                                    captureFinished =
                                            still.success;

                                    stillWidth =
                                            still.width;

                                    stillHeight =
                                            still.height;

                                    if (captureFinished) {

                                        log(
                                                window,
                                                "PHYSICAL STILL CAPTURE SUCCESS: "
                                                        + still.width
                                                        + "x"
                                                        + still.height
                                        );

                                    } else {

                                        log(
                                                window,
                                                "PHYSICAL STILL CAPTURE FAILED."
                                        );
                                    }

                                    postEndAt =
                                            System.nanoTime()
                                                    + (long) (
                                                    POST_BUFFER_SECONDS
                                                            * 1_000_000_000L
                                            );

                                    /*
                                     * The first frame that arrives after
                                     * the still capture returns will be
                                     * used to measure the Live View gap.
                                     */
                                }

                            } else {

                                if (
                                        captureFinished
                                                && now
                                                <= postEndAt
                                ) {

                                    if (postFirstFrameAt == 0) {

                                        postFirstFrameAt =
                                                now;

                                        long delay =
                                                (
                                                        postFirstFrameAt
                                                                - stillCaptureFinishedAt
                                                )
                                                        / 1_000_000L;

                                        log(
                                                window,
                                                "FIRST POST FRAME after "
                                                        + delay
                                                        + " ms"
                                        );
                                    }

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

                                int shownDecodedTotal = decodedTotal;

                                SwingUtilities.invokeLater(
                                        () -> window.setStats(
                                                shownDecodedTotal,
                                                fps,
                                                preCount,
                                                postCount
                                        )
                                );

                                logStatic(
                                        String.format(
                                                Locale.US,
                                                "Decoded=%d FPS=%.1f PRE=%d POST=%d",
                                                decodedTotal,
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
                        captureFinished
                                && System.nanoTime()
                                >= postEndAt
                ) {

                    log(
                            window,
                            "POST buffer duration reached."
                    );

                    break;
                }

                if (
                        !liveProcess.isAlive()
                ) {

                    log(
                            window,
                            "gPhoto2 Live View process exited."
                    );

                    break;
                }

                Thread.sleep(
                        POLL_MILLIS
                );
            }

            /*
             * Stop the Live View process after the diagnostic window.
             */
            if (
                    liveProcess != null
                            && liveProcess.isAlive()
            ) {

                liveProcess.destroy();

                if (
                        !liveProcess.waitFor(
                                2,
                                TimeUnit.SECONDS
                        )
                ) {

                    liveProcess.destroyForcibly();
                }
            }

            if (liveOutThread != null) {
                liveOutThread.join(2000);
            }

            if (liveErrThread != null) {
                liveErrThread.join(2000);
            }

            /*
             * Export the rolling PRE buffer captured immediately before
             * the physical shutter trigger.
             */
            List<FrameData> preSelected =
                    new ArrayList<>(
                            rollingPre
                    );

            exportFrames(
                    preDirectory,
                    preSelected,
                    "pre"
            );

            exportFrames(
                    postDirectory,
                    postFrames,
                    "post"
            );

            double preDuration =
                    durationSeconds(
                            preSelected
                    );

            double postDuration =
                    durationSeconds(
                            postFrames
                    );

            long postFirstFrameDelay =
                    postFirstFrameAt == 0
                            ? -1
                            : (
                            postFirstFrameAt
                                    - stillCaptureFinishedAt
                    )
                            / 1_000_000L;

            liveProcessResult =
                    new ProcessResult(
                            liveProcess.exitValue(),
                            liveStdout == null
                                    ? ""
                                    : liveStdout.text(),
                            liveStderr == null
                                    ? ""
                                    : liveStderr.text()
                    );

            log(
                    window,
                    "PRE exported = "
                            + preSelected.size()
            );

            log(
                    window,
                    "POST exported = "
                            + postFrames.size()
            );

            log(
                    window,
                    "PRE duration = "
                            + formatSeconds(
                            preDuration
                    )
            );

            log(
                    window,
                    "POST duration = "
                            + formatSeconds(
                            postDuration
                    )
            );

            log(
                    window,
                    "gPhoto2 Live View stderr = "
                            + quote(
                            liveProcessResult.stderr
                    )
            );

            return new DiagnosticResult(
                    preSelected.size(),
                    postFrames.size(),
                    captureFinished,
                    stillWidth,
                    stillHeight,
                    captureElapsedMillis,
                    postFirstFrameDelay,
                    preDuration,
                    postDuration,
                    liveProcessResult.exitCode,
                    liveProcessResult.stdout,
                    liveProcessResult.stderr
            );

        } finally {

            if (
                    liveProcess != null
                            && liveProcess.isAlive()
            ) {

                liveProcess.destroyForcibly();
            }
        }
    }

    private static StillCaptureResult captureStill(
            String cameraName,
            Path stillDirectory,
            LiveWindow window
    ) throws Exception {

        /*
         * This is the same proven still-capture operation already tested
         * independently with the EOS 700D:
         *
         * --capture-image-and-download
         */
        Path target =
                stillDirectory.resolve(
                        "final.jpg"
                );

        List<String> args =
                new ArrayList<>();

        args.add("--camera");
        args.add(cameraName);

        args.add(
                "--capture-image-and-download"
        );

        args.add(
                "--force-overwrite"
        );

        args.add("--filename");
        args.add(
                target.getFileName()
                        .toString()
        );

        log(
                window,
                "Still command = gphoto2 --capture-image-and-download"
        );

        ProcessResult result =
                runMsysShell(
                        stillDirectory,
                        STILL_CAPTURE_TIMEOUT_SECONDS,
                        args.toArray(
                                String[]::new
                        )
                );

        log(
                window,
                "Still capture exit = "
                        + result.exitCode
        );

        log(
                window,
                "Still stdout = "
                        + quote(
                        result.stdout
                )
        );

        log(
                window,
                "Still stderr = "
                        + quote(
                        result.stderr
                )
        );

        if (
                result.exitCode != 0
                        || !Files.isRegularFile(
                        target
                )
        ) {

            return new StillCaptureResult(
                    false,
                    0,
                    0,
                    target.toString()
            );
        }

        BufferedImage image =
                ImageIO.read(
                        target.toFile()
                );

        if (image == null) {

            return new StillCaptureResult(
                    false,
                    0,
                    0,
                    target.toString()
            );
        }

        return new StillCaptureResult(
                true,
                image.getWidth(),
                image.getHeight(),
                target.toString()
        );
    }

    private static void writeReport(
            Path outputDirectory,
            DiagnosticResult result
    ) throws Exception {

        Path report =
                outputDirectory.resolve(
                        "report.txt"
                );

        String text =
                "Phomoria gPhoto2 Live Photo Physical Capture Diagnostic"
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + "PRE frames: "
                        + result.preFrames
                        + System.lineSeparator()
                        + "POST frames: "
                        + result.postFrames
                        + System.lineSeparator()
                        + "PRE duration: "
                        + formatSeconds(
                        result.preDurationSeconds
                )
                        + System.lineSeparator()
                        + "POST duration: "
                        + formatSeconds(
                        result.postDurationSeconds
                )
                        + System.lineSeparator()
                        + "Still success: "
                        + result.stillCaptureSuccess
                        + System.lineSeparator()
                        + "Still size: "
                        + result.stillWidth
                        + "x"
                        + result.stillHeight
                        + System.lineSeparator()
                        + "Still capture elapsed ms: "
                        + result.captureElapsedMillis
                        + System.lineSeparator()
                        + "First POST frame delay ms: "
                        + result.postFirstFrameDelayMillis
                        + System.lineSeparator()
                        + "Live process exit: "
                        + result.liveProcessExit
                        + System.lineSeparator()
                        + "Live stdout: "
                        + result.liveStdout
                        + System.lineSeparator()
                        + "Live stderr: "
                        + result.liveStderr
                        + System.lineSeparator();

        Files.writeString(
                report,
                text,
                StandardCharsets.UTF_8
        );
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
                frames.get(0)
                        .timeNanos;

        long last =
                frames.get(
                        frames.size() - 1
                ).timeNanos;

        return (
                last - first
        )
                / 1_000_000_000.0;
    }

    private static void trimRollingBuffer(
            Deque<FrameData> frames,
            long newestTime
    ) {

        long minimumTime =
                newestTime
                        - (long) (
                        PRE_BUFFER_SECONDS
                                * 1_000_000_000L
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
                "gPhoto2 physical Live Photo shell: "
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

    private static String formatMillis(
            long value
    ) {

        if (value < 0) {
            return "N/A";
        }

        return value + " ms";
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

    private record StillCaptureResult(
            boolean success,
            int width,
            int height,
            String file
    ) {
    }

    private record DiagnosticResult(
            int preFrames,
            int postFrames,
            boolean stillCaptureSuccess,
            int stillWidth,
            int stillHeight,
            long captureElapsedMillis,
            long postFirstFrameDelayMillis,
            double preDurationSeconds,
            double postDurationSeconds,
            int liveProcessExit,
            String liveStdout,
            String liveStderr
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
                        "gPhoto2 process stream reader failed: "
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
                    "Phomoria - gPhoto2 Physical Live Photo Diagnostic"
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
                    10
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
                    820
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
