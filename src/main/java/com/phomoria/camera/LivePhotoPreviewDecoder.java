package com.phomoria.camera;

import com.phomoria.debug.DebugLog;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Decodes a compact preview copy of the composed MP4 for Swing playback. */
public final class LivePhotoPreviewDecoder {
    private LivePhotoPreviewDecoder() {}

    public static List<BufferedImage> decode(Path mp4, Path workDirectory) throws Exception {
        if (mp4 == null || !Files.isRegularFile(mp4) || Files.size(mp4) <= 0)
            throw new IOException("File Live Photo MP4 tidak tersedia.");
        Files.createDirectories(workDirectory);
        Path log = workDirectory.resolve("ffmpeg-preview.log");
        Path pattern = workDirectory.resolve("preview-%05d.jpg");
        List<String> command = List.of(
                LivePhotoVideoEncoder.resolveFfmpeg().toString(),
                "-hide_banner", "-loglevel", "error", "-y", "-i", mp4.toAbsolutePath().toString(),
                "-vf", "fps=15,scale=720:720:force_original_aspect_ratio=decrease",
                "-q:v", "5", pattern.toString()
        );
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        boolean finished = process.waitFor(Duration.ofSeconds(90).toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Timeout saat menyiapkan preview Live Photo.");
        }
        if (process.exitValue() != 0)
            throw new IOException("FFmpeg gagal mendekode preview Live Photo. "
                    + (Files.exists(log) ? Files.readString(log) : ""));

        List<Path> images;
        try (var stream = Files.list(workDirectory)) {
            images = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("preview-\\d{5}\\.jpg"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
        List<BufferedImage> frames = new ArrayList<>(images.size());
        for (Path image : images) {
            BufferedImage decoded = ImageIO.read(image.toFile());
            if (decoded != null) frames.add(decoded);
        }
        if (frames.isEmpty()) throw new IOException("Tidak ada frame preview yang berhasil didekode.");
        DebugLog.info("Live Photo preview decoded: " + frames.size() + " frames.");
        return frames;
    }
}
