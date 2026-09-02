package com.phomoria.camera;

import com.phomoria.debug.DebugLog;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Temporary standalone diagnostic for EOS live preview through the same
 * MSYS2 shell route used by the capture test. This does not touch MainScreen.
 */
public final class GPhoto2LiveDiagnostic {

    private static final int CAPTURE_SECONDS = 5;
    private static final int TIMEOUT_SECONDS = 15;

    private GPhoto2LiveDiagnostic() {
    }

    public static void main(String[] args) {
        DebugLog.info("========== gPhoto2 LIVE TEST START ==========");

        Path output = Path.of(
                System.getProperty("user.dir"),
                "target",
                "gphoto2-diagnostic",
                "live-test.mjpg"
        );

        try {
            Files.createDirectories(output.getParent());

            List<String> detected = GPhoto2CameraBackend.detectCameraNames();
            if (detected.isEmpty()) {
                throw new IllegalStateException(
                        "Tidak ada kamera gPhoto2 yang terdeteksi."
                );
            }

            String camera = detected.get(0);
            DebugLog.info("Live diagnostic camera selected: " + camera);

            String command = "gphoto2 --camera "
                    + shellQuote(camera)
                    + " --capture-movie="
                    + CAPTURE_SECONDS
                    + "s --filename "
                    + shellQuote("live-test.mjpg");

            String msysOutput = toMsysPath(output);
            String shell = "cd " + shellQuote(toMsysPath(output.getParent()))
                    + " && /ucrt64/bin/gphoto2.exe --camera "
                    + shellQuote(camera)
                    + " --capture-movie="
                    + CAPTURE_SECONDS
                    + "s --filename 'live-test.mjpg'";

            DebugLog.info("gPhoto2 live command=" + command);
            DebugLog.info("gPhoto2 live output=" + msysOutput);

            ProcessBuilder pb = new ProcessBuilder(
                    "C:\\msys64\\usr\\bin\\bash.exe",
                    "--login",
                    "-c",
                    shell
            );
            pb.environment().put("MSYSTEM", "UCRT64");
            pb.environment().put("MSYS2_PATH_TYPE", "inherit");
            pb.environment().put("CHERE_INVOKING", "1");

            Process process = pb.start();
            StreamDrainer stdout = new StreamDrainer(process.getInputStream());
            StreamDrainer stderr = new StreamDrainer(process.getErrorStream());
            Thread outThread = new Thread(stdout, "gphoto-live-stdout");
            Thread errThread = new Thread(stderr, "gphoto-live-stderr");
            outThread.start();
            errThread.start();

            boolean finished = process.waitFor(
                    TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                throw new IllegalStateException(
                        "gPhoto2 live process timeout setelah "
                                + TIMEOUT_SECONDS + " detik"
                );
            }

            outThread.join(2000);
            errThread.join(2000);

            DebugLog.info(
                    "gPhoto2 live exit=" + process.exitValue()
                            + " stdout=" + quote(stdout.text())
                            + " stderr=" + quote(stderr.text())
            );

            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "gPhoto2 live capture gagal. exit="
                                + process.exitValue()
                                + " stderr=" + stderr.text().trim()
                );
            }

            if (!Files.isRegularFile(output)) {
                throw new IllegalStateException(
                        "gPhoto2 selesai tetapi file live preview tidak ditemukan: "
                                + output.toAbsolutePath()
                );
            }

            long size = Files.size(output);
            int jpegFrames = countJpegFrames(output);

            DebugLog.info(
                    "LIVE TEST RESULT. fileSize=" + size
                            + " bytes jpegFrames=" + jpegFrames
            );

            if (size <= 0 || jpegFrames < 2) {
                throw new IllegalStateException(
                        "File live preview terbentuk tetapi frame JPEG tidak cukup."
                );
            }

            DebugLog.info(
                    "Saved live preview: " + output.toAbsolutePath()
            );
            DebugLog.info("========== gPhoto2 LIVE TEST PASS ==========");

        } catch (Exception ex) {
            DebugLog.error("gPhoto2 LIVE TEST FAILED.", ex);
            DebugLog.info("========== gPhoto2 LIVE TEST FAIL ==========");
            System.exit(1);
        }
    }

    private static int countJpegFrames(Path file) throws Exception {
        int frames = 0;
        int previous = -1;

        try (InputStream in = Files.newInputStream(file)) {
            int current;
            while ((current = in.read()) != -1) {
                if (previous == 0xFF && current == 0xD8) {
                    frames++;
                }
                previous = current;
            }
        }

        return frames;
    }

    private static String toMsysPath(Path path) {
        String value = path.toAbsolutePath().normalize().toString();
        if (value.length() >= 3
                && Character.isLetter(value.charAt(0))
                && value.charAt(1) == ':'
                && (value.charAt(2) == '\\' || value.charAt(2) == '/')) {
            char drive = Character.toLowerCase(value.charAt(0));
            return "/" + drive + "/" + value.substring(3).replace('\\', '/');
        }
        return value.replace('\\', '/');
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String quote(String value) {
        return "\""
                + value.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                + "\"";
    }

    private static final class StreamDrainer implements Runnable {
        private final InputStream input;
        private final StringBuilder text = new StringBuilder();

        private StreamDrainer(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            try (InputStream in = input) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    text.append(new String(buffer, 0, count));
                }
            } catch (Exception ex) {
                DebugLog.warn("gPhoto2 live stream reader failed: " + ex.getMessage());
            }
        }

        private String text() {
            return text.toString();
        }
    }
}
