package com.phomoria.camera;

import com.phomoria.debug.DebugLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Encodes sequential rendered PNG frames into an H.264 MP4 using the bundled FFmpeg. */
public final class LivePhotoVideoEncoder {
    private static final int DEFAULT_FPS = 15;
    private LivePhotoVideoEncoder() {}

    public static Path encode(Path renderedFramesDirectory, Path outputMp4) throws Exception {
        encode(renderedFramesDirectory, outputMp4, DEFAULT_FPS);
        return outputMp4;
    }

    public static void encode(Path renderedFramesDirectory, Path outputMp4, int fps) throws Exception {
        if (renderedFramesDirectory == null || !Files.isDirectory(renderedFramesDirectory))
            throw new IllegalArgumentException("Folder frame render tidak ditemukan.");
        if (outputMp4 == null) throw new IllegalArgumentException("Output MP4 belum ditentukan.");
        int safeFps = Math.max(1, Math.min(30, fps));
        Path ffmpeg = resolveFfmpeg();
        Files.createDirectories(outputMp4.toAbsolutePath().getParent());
        List<String> command = new ArrayList<>();
        command.add(ffmpeg.toString());
        command.add("-hide_banner"); command.add("-loglevel"); command.add("error");
        command.add("-y"); command.add("-framerate"); command.add(Integer.toString(safeFps));
        command.add("-i"); command.add(renderedFramesDirectory.resolve("frame-%05d.png").toString());
        command.add("-an"); command.add("-c:v"); command.add("libx264");
        command.add("-preset"); command.add("veryfast"); command.add("-crf"); command.add("20");
        command.add("-pix_fmt"); command.add("yuv420p"); command.add("-movflags"); command.add("+faststart");
        command.add(outputMp4.toAbsolutePath().toString());
        Path logFile = outputMp4.toAbsolutePath().resolveSibling("live-photo-ffmpeg.log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(logFile.toFile()).start();
        boolean finished = process.waitFor(Duration.ofMinutes(3).toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            Files.deleteIfExists(logFile);
            throw new IOException("FFmpeg timeout saat membuat Live Photo.");
        }
        String output = Files.exists(logFile)
                ? Files.readString(logFile, java.nio.charset.StandardCharsets.UTF_8) : "";
        Files.deleteIfExists(logFile);
        if (process.exitValue() != 0 || !Files.isRegularFile(outputMp4) || Files.size(outputMp4) == 0)
            throw new IOException("FFmpeg gagal membuat MP4 (exit=" + process.exitValue() + "): " + output);
        DebugLog.info("Live Photo MP4 encoded: " + outputMp4 + " size=" + Files.size(outputMp4));
    }

    public static Path resolveFfmpeg() throws IOException {
        List<Path> candidates = new ArrayList<>();
        String appData = System.getenv("APPDATA");
        Path javaHome = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
        // jpackage runtime: java.home is <app>/runtime, so its parent is <app>.
        // In a regular JDK, java.home is not the application directory; retain
        // the working-directory and APPDATA fallbacks for development installs.
        Path appRoot = javaHome.getParent();
        if (appRoot != null) {
            candidates.add(appRoot.resolve("native/ffmpeg/bin/ffmpeg.exe"));
        }
        candidates.add(javaHome.resolve("native/ffmpeg/bin/ffmpeg.exe"));
        candidates.add(Path.of(System.getProperty("user.dir"), "native", "ffmpeg", "bin", "ffmpeg.exe"));
        if (appData != null && !appData.isBlank())
            candidates.add(Path.of(appData, "Phomoria", "native", "ffmpeg", "bin", "ffmpeg.exe"));
        for (Path candidate : candidates) if (Files.isRegularFile(candidate)) return candidate;
        // Development fallback: allow FFmpeg installed and exposed through PATH.
        return Path.of("ffmpeg.exe");
    }
}
