package com.phomoria.camera;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Focused diagnostic:
 *
 * Live View -> stop movie process -> explicitly try to leave viewfinder/live-view
 * state -> physical capture.
 *
 * This does NOT modify production code.
 */
public final class GPhoto2LiveExitRecoveryDiagnostic {

    private static final int MOVIE_SECONDS = 8;
    private static final double WARMUP_SECONDS = 2.5;
    private static final int COMMAND_TIMEOUT_SECONDS = 20;

    private static final String CAMERA = "Canon EOS 700D";

    private GPhoto2LiveExitRecoveryDiagnostic() {
    }

    public static void main(String[] args) throws Exception {
        log("=== gPhoto2 Live View EXIT/RECOVERY Diagnostic ===");
        log("Camera = " + CAMERA);
        log("Movie = " + MOVIE_SECONDS + "s, warm-up = " + WARMUP_SECONDS + "s");
        log("WARNING: physical DSLR shutter WILL be triggered.");

        Path out = Files.createTempDirectory("phomoria-live-exit-recovery-");
        Path stillDir = Files.createDirectories(out.resolve("still"));

        detect();

        log("Output directory = " + out);

        // First inspect the actual Canon configuration names/values while camera is idle.
        log("=== CAMERA CONFIG INSPECTION ===");
        runAndLog(Arrays.asList("--camera", CAMERA, "--get-config", "viewfinder"));
        runAndLog(Arrays.asList("--camera", CAMERA, "--get-config", "eosviewfinder"));
        runAndLog(Arrays.asList("--camera", CAMERA, "--get-config", "output"));

        LiveProcess live = null;
        try {
            log("=== START LIVE VIEW ===");
            live = new LiveProcess(CAMERA, out.resolve("live"));
            live.start();

            long deadline = System.nanoTime()
                    + secondsToNanos(WARMUP_SECONDS);

            while (System.nanoTime() < deadline) {
                Thread.sleep(100);
            }

            log("=== STOP LIVE VIEW PROCESS ===");
            long stopStart = System.nanoTime();
            live.stop();
            long stopMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - stopStart);
            log("Live process stopped in " + stopMs + " ms");

            // Give the USB/PTP session a short settling period.
            sleepMs(500);

            log("=== EXPLICIT LIVE VIEW EXIT ATTEMPT ===");

            // Modern gphoto2 name first.
            CommandResult viewfinderOff =
                    runAndLog(Arrays.asList(
                            "--camera", CAMERA,
                            "--set-config", "viewfinder=0"));

            sleepMs(700);

            // Some Canon/gPhoto2 versions expose the old name instead.
            if (viewfinderOff.exit != 0) {
                log("viewfinder=0 failed; trying eosviewfinder=0");
                runAndLog(Arrays.asList(
                        "--camera", CAMERA,
                        "--set-config", "eosviewfinder=0"));
                sleepMs(700);
            }

            // Ask the camera for its state after the exit attempt.
            log("=== CONFIG AFTER EXIT ATTEMPT ===");
            runAndLog(Arrays.asList("--camera", CAMERA,
                    "--get-config", "viewfinder"));
            runAndLog(Arrays.asList("--camera", CAMERA,
                    "--get-config", "eosviewfinder"));
            runAndLog(Arrays.asList("--camera", CAMERA,
                    "--get-config", "output"));

            // Try capture with increasing recovery delays.
            int[] delaysMs = {0, 500, 1000, 2000, 3000};
            boolean success = false;
            Path finalFile = stillDir.resolve("final.jpg");

            for (int delay : delaysMs) {
                if (delay > 0) {
                    log("Waiting " + delay + " ms before capture retry...");
                    sleepMs(delay);
                }

                log("=== PHYSICAL CAPTURE ATTEMPT (extraWait=" + delay + " ms) ===");
                CommandResult r = runCommand(
                        Arrays.asList(
                                "--camera", CAMERA,
                                "--capture-image-and-download",
                                "--force-overwrite",
                                "--filename", "final.jpg"),
                        stillDir);

                log("Capture exit=" + r.exit);
                log("Capture stdout=\"" + compact(r.stdout) + "\"");
                log("Capture stderr=\"" + compact(r.stderr) + "\"");

                if (Files.exists(finalFile) && Files.size(finalFile) > 0) {
                    var image = ImageIO.read(finalFile.toFile());
                    if (image != null) {
                        log("PHYSICAL STILL SUCCESS: "
                                + image.getWidth() + "x" + image.getHeight());
                        success = true;
                        break;
                    }
                }

                log("Physical capture attempt failed.");
            }

            if (!success) {
                log("=== FINAL RESULT: FAILED ===");
                log("The camera still did not accept physical capture after explicit "
                        + "viewfinder exit + recovery delays.");
                log("This means we should NOT yet change production architecture.");
            } else {
                log("=== FINAL RESULT: SUCCESS ===");
                log("Explicit Live View exit + physical capture works.");
            }

        } finally {
            if (live != null) {
                live.stop();
            }
        }

        log("Output directory = " + out);
        log("Diagnostic complete.");
    }

    private static void detect() throws Exception {
        CommandResult r = runCommand(
                Arrays.asList("--auto-detect"), null);
        log("Detection exit=" + r.exit);
        log("Detection stdout=\"" + compact(r.stdout) + "\"");
        log("Detection stderr=\"" + compact(r.stderr) + "\"");

        if (!r.stdout.contains("Canon EOS 700D")) {
            throw new IllegalStateException("Canon EOS 700D not detected.");
        }
    }

    private static CommandResult runAndLog(List<String> args) throws Exception {
        CommandResult r = runCommand(args, null);
        log("Command exit=" + r.exit);
        log("stdout=\"" + compact(r.stdout) + "\"");
        log("stderr=\"" + compact(r.stderr) + "\"");
        return r;
    }

    private static CommandResult runCommand(
            List<String> args, Path workingDirectory) throws Exception {

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

        boolean finished = p.waitFor(
                COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
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

    private static Thread streamCopy(
            InputStream in, ByteArrayOutputStream out) {
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

            String shell = "cd " + shellQuote(
                    toMsysPath(dir.toAbsolutePath().toString()))
                    + " && /ucrt64/bin/gphoto2.exe"
                    + " --camera " + shellQuote(camera)
                    + " --capture-movie=" + MOVIE_SECONDS + "s";

            log("Live shell: " + shell);

            ProcessBuilder pb = new ProcessBuilder(
                    bash, "--login", "-c", shell);
            pb.environment().put("MSYSTEM", "UCRT64");
            pb.environment().put("MSYS2_PATH_TYPE", "inherit");
            pb.environment().put("CHERE_INVOKING", "1");

            process = pb.start();

            outThread = streamCopy(
                    process.getInputStream(), new ByteArrayOutputStream());
            errThread = streamCopy(
                    process.getErrorStream(), new ByteArrayOutputStream());
        }

        void stop() throws Exception {
            if (process == null) {
                return;
            }

            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
            }

            if (outThread != null) {
                outThread.join(500);
            }
            if (errThread != null) {
                errThread.join(500);
            }
        }
    }

    private record CommandResult(int exit, String stdout, String stderr) {
    }

    private static long secondsToNanos(double seconds) {
        return Math.round(seconds * 1_000_000_000.0);
    }

    private static void sleepMs(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    private static String compact(String s) {
        return s.replace("\r", "\\r")
                .replace("\n", "\\n")
                .trim();
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
        String line = "[" + java.time.LocalTime.now()
                .withNano((java.time.LocalTime.now().getNano() / 1_000_000) * 1_000_000)
                + "] " + message;
        System.out.println(line);
    }
}
