package com.phomoria.camera;

import com.phomoria.debug.DebugLog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public final class GPhoto2PersistentCameraBackend implements CameraBackend {
    private static final int START_TIMEOUT_SECONDS = 20;
    private static final int FRAME_TIMEOUT_SECONDS = 5;
    private static final int CAPTURE_TIMEOUT_SECONDS = 45;

    private final String requestedCameraName;
    private Process process;
    private OutputStream commandWriter;
    private InputStream binaryReader;
    private final Object ioLock = new Object();
    private volatile boolean opened;

    public GPhoto2PersistentCameraBackend(String cameraName) {
        this.requestedCameraName = normalize(cameraName);
    }

    @Override public String getId() { return "gphoto2:" + requestedCameraName; }

    @Override public String getDisplayName() {
        return (requestedCameraName.isBlank() ? "gPhoto2 Camera" : requestedCameraName) + " [gPhoto2]";
    }

    @Override public boolean isAvailable() {
        return detectCameraNames().stream().anyMatch(n -> n.equalsIgnoreCase(requestedCameraName));
    }

    @Override public void open() throws Exception {
        synchronized (ioLock) {
            if (opened) return;
            Path helper = helperPath();
            if (!Files.isRegularFile(helper)) {
                throw new FileNotFoundException("Native gPhoto2 helper tidak ditemukan: " + helper);
            }

            ProcessBuilder pb = new ProcessBuilder(
                    helper.toAbsolutePath().normalize().toString(),
                    requestedCameraName
            );
            configureRuntimeEnvironment(pb.environment(), helper);
            pb.redirectErrorStream(false);

            DebugLog.info("Starting persistent libgphoto2 camera helper directly: "
                    + helper.toAbsolutePath().normalize() + " '" + requestedCameraName + "'");

            process = pb.start();

            InputStream stdout = process.getInputStream();
            InputStream stderr = process.getErrorStream();
            drainStderr(stderr);
            binaryReader = stdout;
            commandWriter = process.getOutputStream();

            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(START_TIMEOUT_SECONDS);

            while (System.nanoTime() < deadline) {
                String line = readLineWithDeadline(deadline);
                if (line == null) break;

                if (line.startsWith("READY ")) {
                    opened = true;
                    DebugLog.info("Persistent libgphoto2 camera ready: " + line.substring(6));
                    return;
                }

                if (line.startsWith("ERROR ")) {
                    throw new IllegalStateException(line);
                }
            }

            close();
            throw new IllegalStateException(
                    "Persistent libgphoto2 helper tidak mengirim READY."
            );
        }
    }

    /**
     * Configure the portable runtime.
     *
     * The runtime is resolved relative to the helper itself, so development
     * and packaged layouts are both supported:
     *
     * tools/gphoto2/runtime/bin/helper.exe
     * native/gphoto2/bin/helper.exe
     */
    private static void configureRuntimeEnvironment(
            java.util.Map<String, String> environment,
            Path helper) {

        Path helperDir = helper.toAbsolutePath().normalize().getParent();
        Path runtime;

        if (helperDir != null
                && helperDir.getFileName() != null
                && helperDir.getFileName().toString().equalsIgnoreCase("bin")) {
            runtime = helperDir.getParent();
        } else {
            runtime = helperDir;
        }

        if (runtime == null) {
            runtime = Path.of(".").toAbsolutePath().normalize();
        }

        Path bin = runtime.resolve("bin");
        Path lib = runtime.resolve("lib");
        Path share = runtime.resolve("share");

        String existingPath = environment.get("PATH");

        environment.put(
                "PATH",
                bin + File.pathSeparator
                        + (existingPath == null ? "" : existingPath)
        );

        environment.remove("MSYSTEM");
        environment.remove("MSYS2_PATH_TYPE");
        environment.remove("CHERE_INVOKING");

        environment.put(
                "PHOMORIA_GPHOTO2_RUNTIME",
                runtime.toString()
        );

        environment.put(
                "CAMLIBS",
                lib.resolve("libgphoto2")
                        .resolve("2.5.34")
                        .toString()
        );

        environment.put(
                "IOLIBS",
                lib.resolve("libgphoto2_port")
                        .resolve("0.12.2")
                        .toString()
        );

        String appData = System.getenv("APPDATA");

        if (appData == null || appData.isBlank()) {
            appData = System.getProperty("user.home");
        }

        environment.put(
                "GPHOTO2_CACHEDIR",
                Paths.get(appData, "Phomoria", ".gphoto").toString()
        );

        environment.put(
                "GPHOTO2_DATADIR",
                share.resolve("libgphoto2").toString()
        );
    }

    @Override public void close() {
        synchronized (ioLock) {
            opened = false;

            try {
                if (commandWriter != null) {
                    commandWriter.write(
                            "QUIT\n".getBytes(StandardCharsets.US_ASCII)
                    );
                    commandWriter.flush();
                }
            } catch (Exception ignored) { }

            commandWriter = null;
            binaryReader = null;

            if (process != null) {
                try {
                    process.waitFor(3, TimeUnit.SECONDS);
                } catch (Exception ignored) { }

                if (process.isAlive()) {
                    process.destroyForcibly();
                }

                process = null;
            }
        }
    }

    @Override public BufferedImage capture() throws Exception {
        synchronized (ioLock) {
            ensureOpen();

            commandWriter.write(
                    "CAPTURE\n".getBytes(StandardCharsets.US_ASCII)
            );
            commandWriter.flush();

            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(CAPTURE_TIMEOUT_SECONDS);

            while (true) {
                String line = readLineWithDeadline(deadline);

                if (line == null) {
                    throw new EOFException("Helper berhenti saat capture.");
                }

                if (line.startsWith("FRAME ")) {
                    readFully(parseLength(line));
                    continue;
                }

                if (line.startsWith("CAPTURE ")) {
                    byte[] jpeg = readFully(parseLength(line));

                    BufferedImage image = ImageIO.read(
                            new ByteArrayInputStream(jpeg)
                    );

                    if (image == null) {
                        throw new IOException("Hasil capture bukan JPEG valid.");
                    }

                    DebugLog.info(
                            "Persistent gPhoto2 capture decoded: "
                                    + image.getWidth() + "x" + image.getHeight()
                    );

                    return image;
                }

                if (line.startsWith("ERROR ")) {
                    throw new IOException(line);
                }
            }
        }
    }

    @Override public BufferedImage getLiveImage() throws Exception {
        synchronized (ioLock) {
            ensureOpen();

            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(FRAME_TIMEOUT_SECONDS);

            while (true) {
                String line = readLineWithDeadline(deadline);

                if (line == null) {
                    throw new EOFException("Helper berhenti saat preview.");
                }

                if (line.startsWith("FRAME ")) {
                    byte[] jpeg = readFully(parseLength(line));

                    BufferedImage image = ImageIO.read(
                            new ByteArrayInputStream(jpeg)
                    );

                    if (image != null) {
                        return image;
                    }

                    DebugLog.warn(
                            "gPhoto2 returned a non-decodable preview frame; "
                                    + "waiting for next frame."
                    );

                    continue;
                }

                if (line.startsWith("ERROR ")) {
                    throw new IOException(line);
                }
            }
        }
    }

    public static boolean isConnectionFailure(Throwable error) {
        Throwable current = error;

        while (current != null) {
            if (current instanceof EOFException) {
                return true;
            }

            if (current instanceof IOException) {
                String message = current.getMessage();

                if (message == null) {
                    return true;
                }

                String text = message.toLowerCase();

                if (text.contains("error preview")
                        || text.contains("preview connection")
                        || text.contains("eof")
                        || text.contains("helper berhenti")
                        || text.contains("persistent libgphoto2 camera belum terbuka")
                        || text.contains("broken pipe")
                        || text.contains("pipe")
                        || text.contains("forcibly closed")) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }

    private void drainStderr(InputStream stderr) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(
                                         stderr,
                                         StandardCharsets.UTF_8))) {

                String line;

                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        DebugLog.warn("gPhoto2 helper: " + line);
                    }
                }

            } catch (IOException ignored) { }
        }, "phomoria-gphoto2-stderr");

        thread.setDaemon(true);
        thread.start();
    }

    private void ensureOpen() throws IOException {
        if (!opened || process == null || !process.isAlive()) {
            throw new IOException(
                    "Persistent libgphoto2 camera belum terbuka."
            );
        }
    }

    private String readLineWithDeadline(long deadline) throws IOException {
        StringBuilder line = new StringBuilder();

        while (System.nanoTime() < deadline) {
            int value = binaryReader.read();

            if (value < 0) {
                throw new EOFException("EOF membaca header helper.");
            }

            if (value == '\n') {
                return line.toString();
            }

            if (value != '\r') {
                line.append((char) value);
            }

            if (line.length() > 1024) {
                throw new IOException("Protocol header terlalu panjang.");
            }
        }

        throw new IOException("Timeout membaca response helper.");
    }

    private byte[] readFully(int length) throws IOException {
        if (length < 0 || length > 50_000_000) {
            throw new IOException("Invalid binary length: " + length);
        }

        byte[] data = new byte[length];
        int off = 0;

        while (off < length) {
            int n = binaryReader.read(
                    data,
                    off,
                    length - off
            );

            if (n < 0) {
                throw new EOFException("EOF membaca frame/capture.");
            }

            off += n;
        }

        return data;
    }

    private static int parseLength(String line) throws IOException {
        String[] p = line.split(" ");

        if (p.length != 2) {
            throw new IOException("Invalid protocol line: " + line);
        }

        try {
            return Integer.parseInt(p[1]);
        } catch (NumberFormatException e) {
            throw new IOException(
                    "Invalid binary length: " + line,
                    e
            );
        }
    }

    /**
     * Detect cameras using the bundled native helper.
     *
     * This no longer calls MSYS2 bash or gphoto2.exe.
     */
    public static java.util.List<String> detectCameraNames() {
        java.util.List<String> result = new java.util.ArrayList<>();

        Path helper = helperPath();

        if (!Files.isRegularFile(helper)) {
            DebugLog.warn(
                    "Portable gPhoto2 helper tidak ditemukan: "
                            + helper.toAbsolutePath()
            );
            return result;
        }

        Process process = null;

        try {
            ProcessBuilder builder = new ProcessBuilder(
                    helper.toAbsolutePath().normalize().toString(),
                    "DETECT"
            );

            configureRuntimeEnvironment(
                    builder.environment(),
                    helper
            );

            builder.redirectErrorStream(false);

            DebugLog.info(
                    "Detecting cameras using portable gPhoto2 helper: "
                            + helper.toAbsolutePath()
            );

            process = builder.start();

            final Process detectProcess = process;

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader =
                             new BufferedReader(
                                     new InputStreamReader(
                                             detectProcess.getErrorStream(),
                                             StandardCharsets.UTF_8))) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            DebugLog.warn(
                                    "gPhoto2 detect stderr: " + line
                            );
                        }
                    }

                } catch (IOException ignored) { }
            }, "phomoria-gphoto2-detect-stderr");

            stderrThread.setDaemon(true);
            stderrThread.start();

            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(
                                         process.getInputStream(),
                                         StandardCharsets.UTF_8))) {

                String line;

                while ((line = reader.readLine()) != null) {
                    DebugLog.info("gPhoto2 detect: " + line);

                    if (!line.startsWith("CAMERA\t")) {
                        continue;
                    }

                    String[] parts = line.split("\t", 3);

                    if (parts.length >= 2) {
                        String model = parts[1].trim();

                        if (!model.isEmpty()
                                && !result.contains(model)) {
                            result.add(model);
                        }
                    }
                }
            }

            int exitCode = process.waitFor();

            DebugLog.info(
                    "Portable gPhoto2 detection finished: "
                            + "exitCode=" + exitCode
                            + ", cameras=" + result.size()
            );

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            DebugLog.warn(
                    "Portable gPhoto2 camera detection interrupted: "
                            + ex.getMessage()
            );

        } catch (Exception ex) {
            DebugLog.warn(
                    "Portable gPhoto2 camera detection failed: "
                            + ex.getMessage()
            );

        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }

        return result;
    }

    private static Path helperPath() {
        String configured =
                System.getProperty("phomoria.gphoto2.helper");

        if (configured == null || configured.isBlank()) {
            configured =
                    System.getenv("PHOMORIA_GPHOTO2_HELPER");
        }

        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }

        String appDir = System.getProperty("phomoria.app.dir");

        if (appDir == null || appDir.isBlank()) {
            appDir = System.getenv("PHOMORIA_APP_DIR");
        }

        if (appDir != null && !appDir.isBlank()) {
            Path root = Path.of(appDir)
                    .toAbsolutePath()
                    .normalize();

            Path packaged = root
                    .resolve("native")
                    .resolve("gphoto2")
                    .resolve("bin")
                    .resolve("phomoria-gphoto2-camera.exe");

            if (Files.isRegularFile(packaged)) {
                return packaged;
            }
        }

        Path localRuntime = Path.of(
                "tools",
                "gphoto2",
                "runtime",
                "bin",
                "phomoria-gphoto2-camera.exe"
        );

        if (Files.isRegularFile(localRuntime)) {
            return localRuntime;
        }

        Path packaged = Path.of(
                "native",
                "gphoto2",
                "bin",
                "phomoria-gphoto2-camera.exe"
        );

        if (Files.isRegularFile(packaged)) {
            return packaged;
        }

        return Path.of(
                "tools",
                "gphoto2",
                "phomoria-gphoto2-camera.exe"
        );
    }

    private static String normalize(String s) {
        if (s == null) return "";

        String v = s.trim();

        if (v.startsWith("gphoto2:")) {
            v = v.substring(9).trim();
        }

        if (v.endsWith(" [gPhoto2]")) {
            v = v.substring(0, v.length() - 10).trim();
        }

        return v;
    }
}
