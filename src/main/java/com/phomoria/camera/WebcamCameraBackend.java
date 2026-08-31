package com.phomoria.camera;

import com.github.sarxos.webcam.Webcam;
import com.phomoria.debug.DebugLog;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.List;

public final class WebcamCameraBackend
        implements CameraBackend {

    private final Webcam webcam;

    public WebcamCameraBackend(
            Webcam webcam
    ) {
        this.webcam = webcam;
    }

    @Override
    public String getId() {
        return "webcam:" + webcam.getName();
    }

    @Override
    public String getDisplayName() {
        return webcam.getName();
    }

    @Override
    public boolean isAvailable() {
        try {
            return webcam != null
                    && !webcam.getName().isBlank();
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void open() {

        CameraManager.open(webcam);
    }

    @Override
    public void close() {

        CameraManager.close();
    }

    @Override
    public BufferedImage capture() {

        return CameraManager.capture();
    }

    public Webcam getWebcam() {
        return webcam;
    }

    public static Dimension selectBestResolution(
            Dimension[] sizes
    ) {
        if (sizes == null || sizes.length == 0) {
            return null;
        }

        return List.of(sizes)
                .stream()
                .filter(
                        d -> d != null
                                && d.width > 0
                                && d.height > 0
                )
                .max(
                        Comparator.comparingLong(
                                d -> (long) d.width * d.height
                        )
                )
                .orElse(null);
    }
}
