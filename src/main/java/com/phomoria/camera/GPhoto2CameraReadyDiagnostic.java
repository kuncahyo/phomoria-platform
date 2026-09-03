package com.phomoria.camera;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/**
 * Focused diagnostic for Canon EOS 700D:
 *
 * Live View -> stop -> wait until the camera accepts a harmless PTP/config
 * command consistently -> physical still capture.
 *
 * This does NOT modify production code.
 *
 * The important question is whether a measurable "camera ready" condition
 * can be detected instead of guessing with a fixed sleep or capture retries.
 */
public final class GPhoto2CameraReadyDiagnostic {

    private static final String CAMERA = "Canon EOS 700D";
    private static final int MOVIE_SECONDS = 8;
    private static final long WARMUP_MS = 2500;

    private static final long PROBE_INTERVAL_MS = 250;
    private static final long MAX_READY_WAIT_MS = 15000;
    private static final int REQUIRED_CONSECUTIVE_READY_PROBES = 2;

    private static final int COMMAND_TIMEOUT_SECONDS = 20;

    private GPhoto2CameraReadyDiagnostic() {
    }

    public static void main(String[] args) throws Exception {
        log("=== gPhoto2 CAMERA READY Diagnostic ===");
        log("Camera = " + CAMERA);
        log("Goal = stop Live View, detect READY state, then capture once.");
        log("No capture retries are performed.");
        log("Ready probe = --get-config output, requiring "
                + REQUIRED_CONSECUTIVE_READY_PROBES + " consecutive successful probes.");

        Path out = Files.createTempDirectory("phomoria-camera-ready-");
        Path liveDir = Files.createDirectories(out.resolve("live"));
        Path stillDir = Files.createDirectories(out.resolve("still"));

        log("Output directory = " + out);
        detect();

        LiveProcess live = null;
        try {
            log("=== START LIVE VIEW ===");
            live = new LiveProcess(CAMERA, liveDir);
            live.start();
            sleepMs(WARMUP_MS);

            log("=== STOP LIVE VIEW ===");
            long stopStart = System.nanoTime();
            live.stop();
            long stopMs = elapsedMs(stopStart);
            log("Live View stop lifecycle completed in " + stopMs + " ms");

            log("=== WAIT FOR CAMERA READY ===");
            ReadyResult ready = waitUntilReady();

            log("READY RESULT = " + ready.ready);
            log("READY WAIT = " + ready.waitMs + " ms");
            log("READY PROBES = " + ready.probes);
            log("READY FAILURES = " + ready.failures);

            if (!ready.ready) {
                log("=== FINAL RESULT: NOT READY ===");
                log("Camera never reached the defined ready condition within "
                        + MAX_READY_WAIT_MS + " ms.");
                log("Do not change production code yet; use this log to tune the readiness test.");
                return;
            }

            log("=== PHYSICAL CAPTURE (SINGLE ATTEMPT) ===");
            Path finalFile = stillDir.resolve("final.jpg");
            CommandResult capture = runCommand(
                    Arrays.asList(
                            "--camera", CAMERA,
                            "--capture-image-and-download",
                            "--force-overwrite",
                            "--filename", "final.jpg"),
                    stillDir);

            log("Capture exit=" + capture.exit);
            log("Capture stdout=\"" + compact(capture.stdout) + "\"");
            log("Capture stderr=\"" + compact(capture.stderr) + "\"");

            if (Files.exists(finalFile) && Files.size(finalFile) > 0) {
                var image = ImageIO.read(finalFile.toFile());
                if (image != null) {
                    log("PHYSICAL STILL SUCCESS: "
                            + image.getWidth() + "x" + image.getHeight());
                    log("=== FINAL RESULT: SUCCESS ===");
                    log("The defined READY condition preceded a successful physical capture.");
                } else {
                    log("=== FINAL RESULT: FAILED IMAGE DECODE ===");
                }
            } else {
                log("=== FINAL RESULT: CAPTURE FAILED ===");
                log("Camera was considered READY, but the single capture still failed.");
                log("This means the current ready probe is not sufficient and should not be used in production.");
            }
        } finally {
            if (live != null) {
                live.stop();
            }
        }

        log("Output directory = " + out);
        log("Diagnostic complete.");
    }

    private static ReadyResult waitUntilReady() throws Exception {
        long start = System.nanoTime();
        int probes = 0;
        int failures = 0;
        int consecutiveReady = 0;

        while (elapsedMs(start) < MAX_READY_WAIT_MS) {
            probes++;
            long probeStart = System.nanoTime();
            CommandResult r = runCommand(
                    Arrays.asList("--camera", CAMERA, "--get-config", "output"), null);
            long probeMs = elapsedMs(probeStart);

            boolean ready = r.exit == 0 && hasCurrentValue(r.stdout);
            log("READY PROBE #" + probes
                                + " duration=" + probeMs + " ms"
                                + " exit=" + r.exit
                                + " ready=" + ready
                                + " stdout=\"" + compact(r.stdout) + "\""
                                + " stderr=\"" + compact(r.stderr) + "\"");

            if (ready) {
                consecutiveReady++;
                if (consecutiveReady >= REQUIRED_CONSECUTIVE_READY_PROBES) {
                    return new ReadyResult(true, elapsedMs(start), probes, failures);
                }
            } else {
                failures++;
                consecutiveReady = 0;
            }

            sleepMs(PROBE_INTERVAL_MS);
        }

        return new ReadyResult(false, elapsedMs(start), probes, failures);
    }

    private static boolean hasCurrentValue(String stdout) {
        String s = stdout == null ? "" : stdout;
        return s.contains("Current:") || s.contains("Current :");
    }

    private static void detect() throws Exception {
        CommandResult r = runCommand(Arrays.asList("--auto-detect"), null);
        log("Detection exit=" + r.exit);
        log("Detection stdout=\"" + compact(r.stdout) + "\"");
        log("Detection stderr=\"" + compact(r.stderr) + "\"");
        if (!r.stdout.contains(CAMERA)) {
            throw new IllegalStateException(CAMERA + " not detected.");
        }
    }

    private static CommandResult runCommand(List<String> args, Path workingDirectory) throws Exception {
        String bash = System.getProperty(
                "phomoria.msys2.bash",
                System.getenv().getOrDefault(
                        "PHOMORIA_MSYS2_BASH",
                        "C:\\msys64\\usr\\bin\\bash.exe"));

        StringBuilder shell = new StringBuilder();
        if (workingDirectory != null) {
            shell.append("cd ").append(shellQuote(toMsysPath(
                    workingDirectory.toAbsolutePath().toString()))).append(" && ");
        }
        shell.append("/ucrt64/bin/gphoto2.exe");
        for (String arg : args) {
            shell.append(' ').append(shellQuote(arg));
        }

        log("gPhoto2 shell: " + shell);

        ProcessBuilder pb = new ProcessBuilder(
                bash, "--login", "-c", shell.toString());
        pb.environment().put("MSYSTEM", "UCRT64");
        pb.environment().put("MSYS2_PATH_TYPE", "inherit");
        pb.environment().put("CHERE_INVOKING", "1");

        Process p = pb.start();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread outThread = streamCopy(p.getInputStream(), stdout);
        Thread errThread = streamCopy(p.getErrorStream(), stderr);

        if (!p.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("gPhoto2 command timed out: " + args);
        }

        outThread.join(1000);
        errThread.join(1000);

        return new CommandResult(
                p.exitValue(),
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private static Thread streamCopy(InputStream in, ByteArrayOutputStream out) {
        Thread t = new Thread(() -> {
            try (InputStream input = in) {
                input.transferTo(out);
            } catch (IOException ignored) {
            }
        });
        t.start();
        return t;
    }

    private static final class LiveProcess {
        private final String camera;
        private final Path dir;
        private Process process;
        private Thread outThread;
        private Thread errThread;

        LiveProcess(String camera, Path dir) throws IOException {
            this.camera = camera;
            this.dir = Files.createDirectories(dir);
        }

        void start() throws Exception {
            String bash = System.getProperty(
                    "phomoria.msys2.bash",
                    System.getenv().getOrDefault(
                            "PHOMORIA_MSYS2_BASH",
                            "C:\\msys64\\usr\\bin\\bash.exe"));

            String shell = "cd " + shellQuote(toMsysPath(dir.toAbsolutePath().toString()))
                    + " && exec /ucrt64/bin/gphoto2.exe"
                    + " --camera " + shellQuote(camera)
                    + " --capture-movie=" + MOVIE_SECONDS + "s";

            log("Live shell: " + shell);

            ProcessBuilder pb = new ProcessBuilder(bash, "--login", "-c", shell);
            pb.environment().put("MSYSTEM", "UCRT64");
            pb.environment().put("MSYS2_PATH_TYPE", "inherit");
            pb.environment().put("CHERE_INVOKING", "1");

            process = pb.start();
            outThread = streamCopy(process.getInputStream(), new ByteArrayOutputStream());
            errThread = streamCopy(process.getErrorStream(), new ByteArrayOutputStream());
        }

        void stop() throws Exception {
            if (process == null) {
                return;
            }

            if (process.isAlive()) {
                long start = System.nanoTime();
                log("Live process alive before destroy = true");
                process.destroy();
                log("Live process destroy() sent");
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    log("Live process did not exit after graceful destroy; forcing termination");
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
                log("Live process exited after " + elapsedMs(start) + " ms");
            } else {
                log("Live process already exited before stop()");
            }

            if (outThread != null) {
                outThread.join(1500);
            }
            if (errThread != null) {
                errThread.join(1500);
            }
            log("Live process reader threads joined");
        }
    }

    private record ReadyResult(boolean ready, long waitMs, int probes, int failures) {
    }

    private record CommandResult(int exit, String stdout, String stderr) {
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static void sleepMs(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    private static String compact(String s) {
        return s.replace("\r", "\\r").replace("\n", "\\n").trim();
    }

    private static String toMsysPath(String windowsPath) {
        String p = windowsPath.replace('\\', '/');
        if (p.length() >= 2 && p.charAt(1) == ':') {
            return "/" + Character.toLowerCase(p.charAt(0)) + p.substring(2);
        }
        return p;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static void log(String message) {
        java.time.LocalTime now = java.time.LocalTime.now();
        now = now.withNano((now.getNano() / 1_000_000) * 1_000_000);
        System.out.println("[" + now + "] " + message);
    }
}
