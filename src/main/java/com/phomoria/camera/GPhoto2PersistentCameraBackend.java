package com.phomoria.camera;

import com.phomoria.debug.DebugLog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Production gPhoto2 backend.
 *
 * The native helper owns ONE persistent libgphoto2 Camera session.
 * Java never starts/stops gphoto2 --capture-movie around a photograph.
 */
public final class GPhoto2PersistentCameraBackend implements CameraBackend {
    private static final int START_TIMEOUT_SECONDS = 20;
    private static final int FRAME_TIMEOUT_SECONDS = 5;
    private static final int CAPTURE_TIMEOUT_SECONDS = 45;

    private final String requestedCameraName;
    private Process process;
    private BufferedReader controlReader;
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

            /*
             * The helper is a native Windows executable built with the
             * MSYS2 UCRT64 toolchain. Start the EXE directly.
             *
             * Do NOT put bash.exe between Java and the helper. A shell layer
             * is unnecessary here and can complicate process ownership and
             * inherited camera-access state.
             */
            ProcessBuilder pb = new ProcessBuilder(
                    helper.toAbsolutePath().normalize().toString(),
                    requestedCameraName
            );

            configureMsys2Environment(pb.environment());

            /*
             * stdout is a binary protocol (FRAME/CAPTURE header + JPEG bytes).
             * Never merge stderr into stdout: native diagnostics must not
             * corrupt the protocol stream.
             */
            pb.redirectErrorStream(false);

            DebugLog.info(
                    "Starting persistent libgphoto2 camera helper directly: "
                            + helper.toAbsolutePath().normalize()
                            + " '" + requestedCameraName + "'"
            );

            process = pb.start();

            InputStream stdout = process.getInputStream();
            InputStream stderr = process.getErrorStream();

            drainStderr(stderr);

            binaryReader = stdout;
            controlReader = new BufferedReader(
                    new InputStreamReader(
                            stdout,
                            StandardCharsets.UTF_8
                    )
            );
            commandWriter = process.getOutputStream();

            long deadline =
                    System.nanoTime()
                            + TimeUnit.SECONDS.toNanos(
                                    START_TIMEOUT_SECONDS
                            );

            while (System.nanoTime() < deadline) {
                String line = readLineWithDeadline(deadline);
                if (line == null) break;

                if (line.startsWith("READY ")) {
                    opened = true;

                    DebugLog.info(
                            "Persistent libgphoto2 camera ready: "
                                    + line.substring(6)
                    );

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

    private static void configureMsys2Environment(
            java.util.Map<String, String> environment
    ) {
        String ucrtBin = "C:\\msys64\\ucrt64\\bin";
        String usrBin = "C:\\msys64\\usr\\bin";

        String existingPath = environment.get("PATH");

        StringBuilder path = new StringBuilder();
        path.append(ucrtBin);
        path.append(';');
        path.append(usrBin);

        if (existingPath != null && !existingPath.isBlank()) {
            path.append(';');
            path.append(existingPath);
        }

        environment.put("PATH", path.toString());
        environment.put("MSYSTEM", "UCRT64");
        environment.put("MSYS2_PATH_TYPE", "inherit");
        environment.put("CHERE_INVOKING", "1");

        // Direct Windows execution does not inherit the MSYS2 shell's gPhoto2 module search variables.
        // Bind the exact UCRT64 libgphoto2/libgphoto2_port module directories explicitly.
        environment.put("CAMLIBS", "C:\\msys64\\ucrt64\\lib\\libgphoto2\\2.5.34");
        environment.put("IOLIBS", "C:\\msys64\\ucrt64\\lib\\libgphoto2_port\\0.12.2");
        environment.put("GPHOTO2_CACHEDIR", "C:\\Users\\HP\\.gphoto");
    }

    @Override public void close() {
        synchronized (ioLock) {
            opened = false;
            try {
                if (commandWriter != null) {
                    commandWriter.write("QUIT\n".getBytes(StandardCharsets.US_ASCII));
                    commandWriter.flush();
                }
            } catch (Exception ignored) {}
            commandWriter = null;
            controlReader = null;
            binaryReader = null;
            if (process != null) {
                try {
                    process.waitFor(3, TimeUnit.SECONDS);
                } catch (Exception ignored) {}
                if (process.isAlive()) process.destroyForcibly();
                process = null;
            }
        }
    }

    @Override public BufferedImage capture() throws Exception {
        synchronized (ioLock) {
            ensureOpen();
            commandWriter.write("CAPTURE\n".getBytes(StandardCharsets.US_ASCII));
            commandWriter.flush();

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CAPTURE_TIMEOUT_SECONDS);
            while (true) {
                String line = readLineWithDeadline(deadline);
                if (line == null) throw new EOFException("Helper berhenti saat capture.");

                if (line.startsWith("FRAME ")) {
                    int len = parseLength(line);
                    readFully(len); // discard stale/live frame
                    continue;
                }
                if (line.startsWith("CAPTURE ")) {
                    byte[] jpeg = readFully(parseLength(line));
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
                    if (image == null) throw new IOException("Hasil capture bukan JPEG valid.");
                    DebugLog.info("Persistent gPhoto2 capture decoded: " + image.getWidth() + "x" + image.getHeight());
                    return image;
                }
                if (line.startsWith("ERROR ")) throw new IOException(line);
            }
        }
    }

    @Override public BufferedImage getLiveImage() throws Exception {
        synchronized (ioLock) {
            ensureOpen();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(FRAME_TIMEOUT_SECONDS);
            while (true) {
                String line = readLineWithDeadline(deadline);
                if (line == null) throw new EOFException("Helper berhenti saat preview.");

                if (line.startsWith("FRAME ")) {
                    byte[] jpeg = readFully(parseLength(line));
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
                    if (image != null) return image;
                    continue;
                }
                if (line.startsWith("ERROR ")) throw new IOException(line);
            }
        }
    }

    private void drainStderr(InputStream stderr) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stderr, StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        DebugLog.warn("gPhoto2 helper: " + line);
                    }
                }
            } catch (IOException ignored) {
                // Helper shutdown closes stderr normally.
            }
        }, "phomoria-gphoto2-stderr");

        thread.setDaemon(true);
        thread.start();
    }

    private void ensureOpen() throws IOException {
        if (!opened || process == null || !process.isAlive())
            throw new IOException("Persistent libgphoto2 camera belum terbuka.");
    }

    private String readLineWithDeadline(long deadline) throws IOException {
        while (System.nanoTime() < deadline) {
            if (controlReader.ready()) return controlReader.readLine();
            try { Thread.sleep(2); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted.");
            }
        }
        throw new SocketTimeoutException("Timeout membaca response helper.");
    }

    private byte[] readFully(int length) throws IOException {
        if (length < 0 || length > 50_000_000) throw new IOException("Invalid binary length: " + length);
        byte[] data = new byte[length];
        int off = 0;
        while (off < length) {
            int n = binaryReader.read(data, off, length - off);
            if (n < 0) throw new EOFException("EOF membaca frame/capture.");
            off += n;
        }
        return data;
    }

    private static int parseLength(String line) throws IOException {
        String[] p = line.split(" ");
        if (p.length != 2) throw new IOException("Invalid protocol line: " + line);
        try { return Integer.parseInt(p[1]); }
        catch (NumberFormatException e) { throw new IOException("Invalid binary length: " + line, e); }
    }

    public static java.util.List<String> detectCameraNames() {
        // Detection remains short-lived; only the actual camera session is persistent.
        try {
            String bash = System.getProperty("phomoria.msys2.bash");
            if (bash == null || bash.isBlank()) bash = System.getenv("PHOMORIA_MSYS2_BASH");
            if (bash == null || bash.isBlank()) bash = "C:\\msys64\\usr\\bin\\bash.exe";
            ProcessBuilder builder = new ProcessBuilder(
                    bash,
                    "--login",
                    "-c",
                    "/ucrt64/bin/gphoto2.exe --auto-detect"
            );
            builder.environment().put("MSYSTEM", "UCRT64");
            builder.environment().put("MSYS2_PATH_TYPE", "inherit");
            builder.environment().put("CHERE_INVOKING", "1");

            Process p = builder
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(15, TimeUnit.SECONDS);
            java.util.List<String> names = new java.util.ArrayList<>();
            for (String raw : out.replace("\r","").split("\n")) {
                String line = raw.trim();
                int idx = line.lastIndexOf("usb:");
                if (idx > 0) {
                    String name = line.substring(0, idx).trim();
                    String port = line.substring(idx).trim();
                    if (!name.isBlank() && port.matches("usb:\\d+,\\d+")) names.add(name);
                }
            }
            return names;
        } catch (Exception ex) {
            DebugLog.error("gPhoto2 detection failed.", ex);
            return java.util.List.of();
        }
    }

    private static Path helperPath() {
        String configured = System.getProperty("phomoria.gphoto2.helper");
        if (configured == null || configured.isBlank()) configured = System.getenv("PHOMORIA_GPHOTO2_HELPER");
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        return Path.of("tools", "gphoto2", "phomoria-gphoto2-camera.exe");
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String v = s.trim();
        if (v.startsWith("gphoto2:")) v = v.substring(9).trim();
        if (v.endsWith(" [gPhoto2]")) v = v.substring(0, v.length()-10).trim();
        return v;
    }

    private static String toMsysPath(Path p) {
        String v = p.toAbsolutePath().normalize().toString();
        if (v.length() >= 3 && Character.isLetter(v.charAt(0)) && v.charAt(1)==':' &&
                (v.charAt(2)=='\\' || v.charAt(2)=='/'))
            return "/" + Character.toLowerCase(v.charAt(0)) + "/" + v.substring(3).replace('\\','/');
        return v.replace('\\','/');
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static final class SocketTimeoutException extends IOException {
        SocketTimeoutException(String message) { super(message); }
    }
}
