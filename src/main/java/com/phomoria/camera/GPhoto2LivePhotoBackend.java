package com.phomoria.camera;

import com.phomoria.debug.DebugLog;

import javax.imageio.ImageIO;
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
 * Production gPhoto2 Live Photo camera backend.
 *
 * Proven sequence used here:
 *
 *   persistent Live View
 *       -> rolling PRE buffer
 *       -> stop Live View process
 *       -> short USB/PTP settling delay
 *       -> physical still capture
 *       -> restart Live View
 *       -> continue POST buffer
 *
 * The physical capture is deliberately NOT attempted while the Live View
 * gPhoto2 process is still active because the EOS 700D diagnostic showed
 * that path failing with an I/O error.
 */
public final class GPhoto2LivePhotoBackend implements CameraBackend {

    private static final int CAPTURE_TIMEOUT_SECONDS = 45;
    private static final int LIVE_SETTLE_MILLIS = 500;
    private static final int VIEWFINDER_SETTLE_MILLIS = 700;

    // If the camera still reports a transient I/O/PTP error immediately after
    // leaving Live View, recover inside this single shutter operation instead
    // of returning an error to CaptureController (which would restart the UI
    // countdown). The delays mirror the proven diagnostic ladder.
    private static final int[] CAPTURE_RETRY_DELAYS_MILLIS = {0, 500, 1000, 2000, 3000};

    private static final double PRE_BUFFER_SECONDS = 1.5;
    private static final double POST_BUFFER_SECONDS = 1.5;

    private final String cameraName;

    private final Object captureLock = new Object();

    private GPhoto2LivePhotoStream stream;
    private GPhoto2LivePhotoBuffer buffer;

    public GPhoto2LivePhotoBackend(String cameraName) {
        this.cameraName = normalizeCameraName(cameraName);
    }

    @Override
    public String getId() {
        return "gphoto2:" + cameraName;
    }

    @Override
    public String getDisplayName() {
        String name = cameraName.isBlank()
                ? "gPhoto2 Camera"
                : cameraName;

        return name.endsWith(" [gPhoto2]")
                ? name
                : name + " [gPhoto2]";
    }

    @Override
    public boolean isAvailable() {
        for (String detected : GPhoto2CameraBackend.detectCameraNames()) {
            if (detected.equalsIgnoreCase(cameraName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void open() throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "gPhoto2 camera tidak terdeteksi: "
                            + getDisplayName()
            );
        }

        synchronized (captureLock) {
            if (stream == null) {
                stream = new GPhoto2LivePhotoStream(cameraName);
                buffer = new GPhoto2LivePhotoBuffer(
                        PRE_BUFFER_SECONDS,
                        POST_BUFFER_SECONDS
                );
            }

            buffer.reset();
            stream.start();

            DebugLog.info(
                    "gPhoto2 Live Photo backend opened: "
                            + getDisplayName()
            );
        }
    }

    @Override
    public void close() {
        synchronized (captureLock) {
            if (stream != null) {
                try {
                    stream.stop();
                } catch (Exception ex) {
                    DebugLog.warn(
                            "gPhoto2 Live View close failed: "
                                    + ex.getMessage()
                    );
                }
            }

            stream = null;
            buffer = null;
        }
    }

    @Override
    public BufferedImage getLiveImage() throws Exception {
        synchronized (captureLock) {
            ensureOpen();

            BufferedImage image = stream.latestImage();

            if (image != null && buffer != null) {
                buffer.accept(
                        image,
                        System.nanoTime()
                );
            }

            return image;
        }
    }

    /**
     * Physical capture follows the proven EXIT/RECOVERY order:
     *
     *   stop Live View
     *   -> 500 ms settle
     *   -> physical capture
     *   -> restart Live View
     *
     * The returned image is the full-resolution DSLR still.
     */
    @Override
    public BufferedImage capture() throws Exception {
        synchronized (captureLock) {
            ensureOpen();

            DebugLog.info(
                    "gPhoto2 Live Photo physical capture started. "
                            + "camera=" + getDisplayName()
            );

            stream.stop();

            // Match the proven EXIT/RECOVERY diagnostic sequence:
            // stop Live View -> settle -> explicitly ask Canon/gPhoto2 to
            // leave viewfinder state -> settle -> physical capture.
            Thread.sleep(LIVE_SETTLE_MILLIS);
            attemptLiveViewExitRecovery();

            if (buffer != null) {
                buffer.markShutter();
            }

            BufferedImage still;

            try {
                still = captureStillWithRecovery();
            } finally {
                // Restart Live View regardless of capture result so the UI
                // can recover to the normal preview state.
                try {
                    stream.start();
                } catch (Exception restartError) {
                    DebugLog.error(
                            "gPhoto2 Live View restart failed.",
                            restartError
                    );
                }
            }

            if (still == null) {
                throw new IllegalStateException(
                        "gPhoto2 physical capture menghasilkan gambar null."
                );
            }

            DebugLog.info(
                    "gPhoto2 Live Photo physical capture decoded: "
                            + still.getWidth()
                            + "x"
                            + still.getHeight()
            );

            return still;
        }
    }

    /**
     * Attempts the same explicit Canon Live View exit used by the
     * GPhoto2LiveExitRecoveryDiagnostic. These commands are best-effort:
     * some EOS 700D/gPhoto2 states reject them with an I/O error even when
     * the subsequent physical capture is still successful.
     */
    private void attemptLiveViewExitRecovery() throws Exception {
        DebugLog.info("gPhoto2 Live View recovery: set viewfinder=0.");

        ProcessResult viewfinderOff = runMsysShell(
                null,
                CAPTURE_TIMEOUT_SECONDS,
                "--camera",
                cameraName,
                "--set-config",
                "viewfinder=0"
        );

        DebugLog.info(
                "gPhoto2 Live View recovery viewfinder=0 exit="
                        + viewfinderOff.exitCode
                        + " stdout="
                        + quote(viewfinderOff.stdout)
                        + " stderr="
                        + quote(viewfinderOff.stderr)
        );

        Thread.sleep(VIEWFINDER_SETTLE_MILLIS);

        if (viewfinderOff.exitCode != 0) {
            DebugLog.info(
                    "gPhoto2 Live View recovery: viewfinder=0 failed; "
                            + "trying eosviewfinder=0."
            );

            ProcessResult eosViewfinderOff = runMsysShell(
                    null,
                    CAPTURE_TIMEOUT_SECONDS,
                    "--camera",
                    cameraName,
                    "--set-config",
                    "eosviewfinder=0"
            );

            DebugLog.info(
                    "gPhoto2 Live View recovery eosviewfinder=0 exit="
                            + eosViewfinderOff.exitCode
                            + " stdout="
                            + quote(eosViewfinderOff.stdout)
                            + " stderr="
                            + quote(eosViewfinderOff.stderr)
            );

            Thread.sleep(VIEWFINDER_SETTLE_MILLIS);
        }
    }

    /**
     * Retry the physical shutter inside the same capture operation.
     *
     * This is deliberately different from MainScreen's retry behavior: a
     * transient gPhoto2 I/O error must not restart the visible 3-2-1 countdown.
     * The camera stays out of Live View while these attempts run.
     */
    private BufferedImage captureStillWithRecovery() throws Exception {
        Exception lastError = null;

        for (int attempt = 0; attempt < CAPTURE_RETRY_DELAYS_MILLIS.length; attempt++) {
            int delay = CAPTURE_RETRY_DELAYS_MILLIS[attempt];

            if (delay > 0) {
                DebugLog.info(
                        "gPhoto2 physical capture retry "
                                + (attempt + 1)
                                + "/"
                                + CAPTURE_RETRY_DELAYS_MILLIS.length
                                + " after "
                                + delay
                                + " ms."
                );
                Thread.sleep(delay);
            }

            try {
                BufferedImage image = captureStill();
                DebugLog.info(
                        "gPhoto2 physical capture succeeded on internal attempt "
                                + (attempt + 1)
                );
                return image;
            } catch (Exception ex) {
                lastError = ex;
                DebugLog.warn(
                        "gPhoto2 physical capture internal attempt "
                                + (attempt + 1)
                                + "/"
                                + CAPTURE_RETRY_DELAYS_MILLIS.length
                                + " failed: "
                                + ex.getMessage()
                );
            }
        }

        throw lastError != null
                ? lastError
                : new IllegalStateException(
                        "gPhoto2 physical capture failed without an error."
                );
    }

    private BufferedImage captureStill() throws Exception {
        Path tempDir = Files.createTempDirectory(
                "phomoria-gphoto2-live-still-"
        );
        Path expected = tempDir.resolve("final.jpg");

        try {
            List<String> args = new ArrayList<>();

            args.add("--camera");
            args.add(cameraName);
            args.add("--capture-image-and-download");
            args.add("--force-overwrite");
            args.add("--filename");
            args.add("final.jpg");

            ProcessResult result = runMsysShell(
                    tempDir,
                    CAPTURE_TIMEOUT_SECONDS,
                    args.toArray(String[]::new)
            );

            DebugLog.info(
                    "gPhoto2 Live Photo still exit="
                            + result.exitCode
                            + " stdout="
                            + quote(result.stdout)
                            + " stderr="
                            + quote(result.stderr)
            );

            // Do not trust exit code alone. The diagnostic established that
            // actual file existence/readability is the meaningful success test.
            if (!Files.isRegularFile(expected)) {
                throw new IllegalStateException(
                        "gPhoto2 physical capture selesai tetapi final.jpg "
                                + "tidak ditemukan. stderr="
                                + result.stderr.trim()
                );
            }

            BufferedImage image = ImageIO.read(
                    expected.toFile()
            );

            if (image == null) {
                throw new IllegalStateException(
                        "final.jpg tidak dapat dibaca sebagai gambar."
                );
            }

            return image;

        } finally {
            deleteRecursively(tempDir);
        }
    }

    private void ensureOpen() {
        if (stream == null) {
            throw new IllegalStateException(
                    "gPhoto2 Live Photo backend belum dibuka."
            );
        }
    }

    private static ProcessResult runMsysShell(
            Path workingDirectory,
            int timeoutSeconds,
            String... args
    ) throws Exception {
        String bash = System.getProperty(
                "phomoria.msys2.bash",
                System.getenv().getOrDefault(
                        "PHOMORIA_MSYS2_BASH",
                        "C:\\msys64\\usr\\bin\\bash.exe"
                )
        );

        StringBuilder shell = new StringBuilder();

        if (workingDirectory != null) {
            shell.append("cd ")
                    .append(shellQuote(
                            toMsysPath(
                                    workingDirectory
                                            .toAbsolutePath()
                                            .toString()
                            )
                    ))
                    .append(" && ");
        }

        shell.append("/ucrt64/bin/gphoto2.exe");

        for (String arg : args) {
            shell.append(' ')
                    .append(shellQuote(arg));
        }

        ProcessBuilder builder = new ProcessBuilder(
                bash,
                "--login",
                "-c",
                shell.toString()
        );

        builder.environment().put("MSYSTEM", "UCRT64");
        builder.environment().put("MSYS2_PATH_TYPE", "inherit");
        builder.environment().put("CHERE_INVOKING", "1");

        DebugLog.info(
                "gPhoto2 Live Photo shell: "
                        + builder.command()
        );

        Process process = builder.start();

        ByteArrayOutputStream stdout =
                new ByteArrayOutputStream();
        ByteArrayOutputStream stderr =
                new ByteArrayOutputStream();

        Thread outThread = streamCopy(
                process.getInputStream(),
                stdout
        );

        Thread errThread = streamCopy(
                process.getErrorStream(),
                stderr
        );

        boolean finished = process.waitFor(
                timeoutSeconds,
                TimeUnit.SECONDS
        );

        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            throw new IllegalStateException(
                    "gPhoto2 physical capture timeout setelah "
                            + timeoutSeconds
                            + " detik."
            );
        }

        outThread.join(2000);
        errThread.join(2000);

        return new ProcessResult(
                process.exitValue(),
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private static Thread streamCopy(
            InputStream input,
            ByteArrayOutputStream output
    ) {
        Thread thread = new Thread(
                () -> {
                    try (InputStream in = input) {
                        in.transferTo(output);
                    } catch (Exception ignored) {
                    }
                },
                "gphoto2-live-photo-command-reader"
        );

        thread.setDaemon(true);
        thread.start();

        return thread;
    }

    private static String normalizeCameraName(String value) {
        if (value == null) {
            return "";
        }

        String name = value.trim();

        if (name.endsWith(" [gPhoto2]")) {
            name = name.substring(
                    0,
                    name.length() - " [gPhoto2]".length()
            ).trim();
        }

        if (name.startsWith("gphoto2:")) {
            name = name.substring(
                    "gphoto2:".length()
            ).trim();
        }

        return name;
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

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }

        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder())
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
        return "\""
                + (value == null
                ? "<null>"
                : value
                        .replace("\\", "\\\\")
                        .replace("\r", "\\r")
                        .replace("\n", "\\n"))
                + "\"";
    }

    private record ProcessResult(
            int exitCode,
            String stdout,
            String stderr
    ) {
    }
}
