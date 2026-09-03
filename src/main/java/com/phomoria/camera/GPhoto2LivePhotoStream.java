package com.phomoria.camera;

import com.phomoria.debug.DebugLog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.RandomAccessFile;
import java.util.concurrent.TimeUnit;

/**
 * Persistent gPhoto2 Live View stream.
 *
 * Reference basis:
 * - EOS 700D Live View was proven through --capture-movie.
 * - The camera writes movie.mjpg even when --filename is supplied.
 * - JPEG frames are decoded from the growing MJPEG file.
 *
 * This class owns exactly one gPhoto2 Live View process.
 */
final class GPhoto2LivePhotoStream {

    private static final int START_TIMEOUT_SECONDS = 15;
    private static final int STOP_TIMEOUT_SECONDS = 3;

    private final String cameraName;

    private final Object lock = new Object();

    private Process process;
    private Thread readerThread;
    private Path outputDirectory;
    private volatile BufferedImage latestImage;

    GPhoto2LivePhotoStream(String cameraName) {
        this.cameraName = cameraName;
    }

    void start() throws Exception {
        synchronized (lock) {
            if (isRunningLocked()) {
                return;
            }

            outputDirectory = Files.createTempDirectory(
                    "phomoria-gphoto2-live-"
            );

            ProcessBuilder builder = buildProcess(outputDirectory);
            DebugLog.info(
                    "gPhoto2 Live View starting: " + builder.command()
            );

            process = builder.start();

            readerThread = new Thread(
                    () -> readMovie(outputDirectory, process),
                    "phomoria-gphoto2-live-reader"
            );
            readerThread.setDaemon(true);
            readerThread.start();
        }

        waitForMovieFile();
    }

    BufferedImage latestImage() {
        return latestImage;
    }

    boolean isRunning() {
        synchronized (lock) {
            return isRunningLocked();
        }
    }

    void stop() throws Exception {
        Process processToStop;
        Thread readerToStop;

        synchronized (lock) {
            processToStop = process;
            readerToStop = readerThread;
        }

        if (processToStop == null) {
            return;
        }

        DebugLog.info("Stopping gPhoto2 Live View process.");

        if (processToStop.isAlive()) {
            processToStop.destroy();

            if (!processToStop.waitFor(
                    STOP_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            )) {
                processToStop.destroyForcibly();
                processToStop.waitFor(2, TimeUnit.SECONDS);
            }
        }

        if (readerToStop != null) {
            readerToStop.interrupt();
            readerToStop.join(1500);
        }

        synchronized (lock) {
            process = null;
            readerThread = null;
        }

        DebugLog.info("gPhoto2 Live View process stopped.");
    }

    private boolean isRunningLocked() {
        return process != null && process.isAlive();
    }

    private ProcessBuilder buildProcess(Path directory) {
        String bash = System.getProperty(
                "phomoria.msys2.bash",
                System.getenv().getOrDefault(
                        "PHOMORIA_MSYS2_BASH",
                        "C:\\msys64\\usr\\bin\\bash.exe"
                )
        );

        String msysDirectory = toMsysPath(
                directory.toAbsolutePath().toString()
        );

        String shell =
                "cd " + shellQuote(msysDirectory)
                        + " && exec /ucrt64/bin/gphoto2.exe"
                        + " --camera " + shellQuote(cameraName)
                        + " --capture-movie=30s"
                        + " --filename live-test.mjpg";

        ProcessBuilder builder = new ProcessBuilder(
                bash,
                "--login",
                "-c",
                shell
        );

        builder.environment().put("MSYSTEM", "UCRT64");
        builder.environment().put("MSYS2_PATH_TYPE", "inherit");
        builder.environment().put("CHERE_INVOKING", "1");

        return builder;
    }

    private void waitForMovieFile() throws Exception {
        Path directory = outputDirectory;
        Path movie = directory.resolve("movie.mjpg");

        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(START_TIMEOUT_SECONDS);

        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(movie)) {
                return;
            }

            Process current;
            synchronized (lock) {
                current = process;
            }

            if (current == null || !current.isAlive()) {
                throw new IllegalStateException(
                        "gPhoto2 Live View berhenti sebelum movie.mjpg tersedia."
                );
            }

            Thread.sleep(50);
        }

        throw new IllegalStateException(
                "gPhoto2 Live View tidak menghasilkan movie.mjpg dalam "
                        + START_TIMEOUT_SECONDS + " detik."
        );
    }

    private void readMovie(Path directory, Process liveProcess) {
        Path movie = directory.resolve("movie.mjpg");
        long position = 0;
        ByteArrayOutputStream pending = new ByteArrayOutputStream();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (Files.isRegularFile(movie)) {
                    long size = Files.size(movie);

                    if (size > position) {
                        byte[] chunk = readRange(movie, position, size);
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
                                latestImage = image;
                            }
                        }
                    }
                }

                if (!liveProcess.isAlive()) {
                    // Read a final tail after gPhoto2 exits.
                    if (Files.isRegularFile(movie)) {
                        long size = Files.size(movie);
                        if (size > position) {
                            byte[] chunk = readRange(movie, position, size);
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
                                    latestImage = image;
                                }
                            }
                        }
                    }
                    return;
                }

                Thread.sleep(20);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            DebugLog.warn(
                    "gPhoto2 Live View reader stopped: "
                            + ex.getMessage()
            );
        }
    }

    private static byte[] readRange(
            Path file,
            long start,
            long end
    ) throws Exception {
        int length = Math.toIntExact(end - start);
        byte[] bytes = new byte[length];

        try (RandomAccessFile raf = new RandomAccessFile(
                file.toFile(),
                "r"
        )) {
            raf.seek(start);
            raf.readFully(bytes);
        }

        return bytes;
    }

    private static byte[] extractJpeg(
            ByteArrayOutputStream pending
    ) {
        byte[] data = pending.toByteArray();

        int start = findMarker(data, 0, 0xFF, 0xD8);
        if (start < 0) {
            if (data.length > 1024 * 1024) {
                pending.reset();
            }
            return null;
        }

        int end = findMarker(data, start + 2, 0xFF, 0xD9);
        if (end < 0) {
            if (start > 0) {
                pending.reset();
                pending.writeBytes(java.util.Arrays.copyOfRange(
                        data,
                        start,
                        data.length
                ));
            }
            return null;
        }

        int endExclusive = end + 2;
        byte[] jpeg = java.util.Arrays.copyOfRange(
                data,
                start,
                endExclusive
        );

        pending.reset();
        if (endExclusive < data.length) {
            pending.writeBytes(
                    java.util.Arrays.copyOfRange(
                            data,
                            endExclusive,
                            data.length
                    )
            );
        }

        return jpeg;
    }

    private static int findMarker(
            byte[] data,
            int from,
            int first,
            int second
    ) {
        for (int i = Math.max(0, from); i + 1 < data.length; i++) {
            if ((data[i] & 0xFF) == first
                    && (data[i + 1] & 0xFF) == second) {
                return i;
            }
        }
        return -1;
    }

    private static String toMsysPath(String windowsPath) {
        String p = windowsPath.replace('\\', '/');

        if (p.length() >= 2 && p.charAt(1) == ':') {
            return "/"
                    + Character.toLowerCase(p.charAt(0))
                    + p.substring(2);
        }

        return p;
    }

    private static String shellQuote(String value) {
        return "'"
                + value.replace("'", "'\"'\"'")
                + "'";
    }
}
