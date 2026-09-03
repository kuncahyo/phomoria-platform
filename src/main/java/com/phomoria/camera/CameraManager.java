package com.phomoria.camera;

import com.github.sarxos.webcam.Webcam;
import com.phomoria.debug.DebugLog;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CameraManager {

    /*
     * One lock protects DSLR detection/opening.
     *
     * SettingsScreen checks camera availability periodically. Without this
     * lock, a short-lived gphoto2 --auto-detect process could overlap with
     * gp_camera_init() in the persistent helper and cause PTP I/O errors.
     */
    private static final Object CAMERA_LOCK = new Object();

    private static Webcam current;
    private static CameraBackend currentBackend;

    /*
     * gPhoto2 detection is deliberately cached. SettingsScreen asks
     * isAvailable() every second; launching a new gphoto2 --auto-detect
     * process from that polling path can contend with the persistent
     * libgphoto2 session when the photo screen opens.
     */
    private static final List<String> cachedGPhoto2Names = new ArrayList<>();

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

        synchronized (CAMERA_LOCK) {
            /*
             * If the persistent DSLR session is already open, do NOT launch
             * another gphoto2 process just to enumerate it. The open session
             * itself proves that the selected camera is available.
             */
            if (currentBackend != null) {
                String displayName = currentBackend.getDisplayName();

                devices.add(
                        new CameraDevice(
                                currentBackend.getId(),
                                displayName,
                                "gphoto2",
                                true
                        )
                );
            } else {
                /*
                 * Detection is performed only when the device list is
                 * explicitly refreshed, never from the 1-second status poll.
                 */
                cachedGPhoto2Names.clear();
                cachedGPhoto2Names.addAll(
                        GPhoto2PersistentCameraBackend.detectCameraNames()
                );

                for (String name : cachedGPhoto2Names) {
                    devices.add(
                            new CameraDevice(
                                    "gphoto2:" + name,
                                    name + " [gPhoto2]",
                                    "gphoto2",
                                    true
                            )
                    );
                }
            }
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
        if (name == null || name.isBlank()) {
            return false;
        }

        if (isGPhoto2Name(name)) {
            synchronized (CAMERA_LOCK) {
                /*
                 * Once persistent backend owns the camera, availability is
                 * known locally. Never run a second gphoto2 autodetect while
                 * the persistent PTP session is active.
                 */
                if (currentBackend != null
                        && currentBackend.getId().equalsIgnoreCase(
                                normalizeGPhoto2Id(name)
                        )) {

                    return true;
                }

                /*
                 * IMPORTANT: never start gphoto2 --auto-detect from this
                 * method. SettingsScreen calls isAvailable() every second.
                 * Use the result of the last explicit device-list refresh.
                 */
                String normalized = normalizeGPhoto2Name(name);
                return cachedGPhoto2Names.stream()
                        .anyMatch(
                                detected -> detected.equalsIgnoreCase(normalized)
                        );
            }
        }

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

    public static CameraBackend openConfiguredBackend(String name)
            throws Exception {

        synchronized (CAMERA_LOCK) {
            close();

            GPhoto2PersistentCameraBackend backend =
                    new GPhoto2PersistentCameraBackend(name);

            /*
             * Hold CAMERA_LOCK during init so SettingsScreen cannot launch
             * gphoto2 --auto-detect concurrently with gp_camera_init().
             */
            backend.open();

            currentBackend = backend;

            DebugLog.info(
                    "CameraManager.openConfiguredBackend -> "
                            + backend.getDisplayName()
            );

            return backend;
        }
    }

    public static void open(Webcam webcam) {
        synchronized (CAMERA_LOCK) {
            close();

            if (webcam == null) {
                return;
            }

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
    }

    public static BufferedImage capture() throws Exception {
        synchronized (CAMERA_LOCK) {
            if (currentBackend != null) {
                return currentBackend.capture();
            }

            if (current != null) {
                return current.getImage();
            }

            return null;
        }
    }

    public static Webcam current() {
        return current;
    }

    public static CameraBackend currentBackend() {
        return currentBackend;
    }

    public static void close() {
        synchronized (CAMERA_LOCK) {
            if (currentBackend != null) {
                try {
                    currentBackend.close();
                } catch (Exception ignored) {
                }

                currentBackend = null;
            }

            if (current != null) {
                try {
                    current.close();
                } catch (Exception ignored) {
                }

                current = null;
            }
        }
    }

    private static boolean isGPhoto2Name(String name) {
        return name.startsWith("gphoto2:")
                || name.endsWith(" [gPhoto2]");
    }

    private static String normalizeGPhoto2Name(String name) {
        String value = name.trim();

        if (value.startsWith("gphoto2:")) {
            value = value.substring("gphoto2:".length()).trim();
        }

        if (value.endsWith(" [gPhoto2]")) {
            value = value.substring(
                    0,
                    value.length() - " [gPhoto2]".length()
            ).trim();
        }

        return value;
    }

    private static String normalizeGPhoto2Id(String name) {
        return "gphoto2:" + normalizeGPhoto2Name(name);
    }
}
