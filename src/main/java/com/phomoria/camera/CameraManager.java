package com.phomoria.camera;

import com.github.sarxos.webcam.Webcam;
import com.phomoria.debug.DebugLog;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CameraManager {

    private static Webcam current;

    private CameraManager() {
    }

    public static List<Webcam> list() {
        try {
            List<Webcam> cameras = Webcam.getWebcams();

            DebugLog.info(
                    "CameraManager.list -> "
                            + cameras.size()
                            + " camera(s)"
            );

            return cameras;

        } catch (Exception ex) {
            DebugLog.error(
                    "CameraManager.list failed.",
                    ex
            );
            return List.of();
        }
    }

    public static List<CameraDevice> listDevices() {
        List<CameraDevice> devices = new ArrayList<>();

        for (Webcam webcam : list()) {
            devices.add(
                    new CameraDevice(
                            webcam.getName(),
                            webcam.getName(),
                            "webcam",
                            true
                    )
            );
        }

        for (String name : GPhoto2CameraBackend.detectCameraNames()) {
            devices.add(
                    new CameraDevice(
                            "gphoto2:" + name,
                            name + " [gPhoto2]",
                            "gphoto2",
                            true
                    )
            );
        }

        DebugLog.info(
                "CameraManager.listDevices -> "
                        + devices.size()
                        + " device(s)"
        );

        return devices;
    }

    public static Webcam find(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        for (Webcam webcam : list()) {
            if (webcam.getName().equalsIgnoreCase(name)
                    || webcam.getName()
                    .toLowerCase()
                    .contains(name.toLowerCase())) {
                return webcam;
            }
        }

        return null;
    }

    public static boolean isAvailable(String name) {
        return find(name) != null;
    }

    public static Webcam openConfigured(String name) {
        Webcam webcam = find(name);

        if (webcam == null) {
            DebugLog.warn(
                    "CameraManager.openConfigured -> camera not found: "
                            + name
            );
            return null;
        }

        open(webcam);
        return current;
    }

    public static void open(Webcam webcam) {
        if (webcam == null) {
            return;
        }

        close();

        Dimension best =
                List.of(webcam.getViewSizes())
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

        if (best != null) {
            webcam.setViewSize(best);
        }

        if (webcam.open()) {
            current = webcam;

            DebugLog.info(
                    "CameraManager.open -> "
                            + webcam.getName()
            );
        } else {
            DebugLog.warn(
                    "CameraManager.open -> failed: "
                            + webcam.getName()
            );
        }
    }

    public static BufferedImage capture() {
        if (current == null) {
            return null;
        }

        return current.getImage();
    }

    public static Webcam current() {
        return current;
    }

    public static void close() {
        if (current != null) {
            try {
                current.close();
            } catch (Exception ignored) {
            }

            current = null;
        }
    }
}
