package com.phomoria.camera;

import com.phomoria.debug.DebugLog;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CameraReconnectController {

    public interface Listener {
        void onWaitingForCamera();
        void onCameraReconnected(CameraBackend backend);
    }

    private static final int RETRY_DELAY_MS = 1500;

    private final String configuredCameraName;
    private final Listener listener;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    private volatile boolean stopped;

    public CameraReconnectController(
            String configuredCameraName,
            Listener listener
    ) {
        this.configuredCameraName = configuredCameraName;
        this.listener = listener;
    }

    public void handleDisconnect() {
        if (stopped) {
            return;
        }

        if (!reconnecting.compareAndSet(false, true)) {
            DebugLog.info("Camera reconnect already in progress.");
            return;
        }

        DebugLog.warn(
                "Camera connection lost. Starting automatic reconnect."
        );

        SwingUtilities.invokeLater(listener::onWaitingForCamera);

        Thread worker = new Thread(
                this::reconnectLoop,
                "phomoria-camera-reconnect"
        );

        worker.setDaemon(true);
        worker.start();
    }

    private void reconnectLoop() {
        try {
            /*
             * Dispose the old persistent libgphoto2 session first.
             * Never reuse a stale backend after USB disconnect.
             */
            CameraManager.close();

            while (!stopped) {
                try {
                    DebugLog.info(
                            "Trying gPhoto2 autodetect for reconnect..."
                    );

                    List<String> detected =
                            GPhoto2PersistentCameraBackend
                                    .detectCameraNames();

                    String detectedName = findConfiguredCamera(detected);

                    if (detectedName == null) {
                        DebugLog.info(
                                "Configured camera not detected yet. "
                                        + "Waiting for reconnect."
                        );

                        sleepBeforeRetry();
                        continue;
                    }

                    DebugLog.info(
                            "Configured camera detected again: "
                                    + detectedName
                    );

                    /*
                     * CameraManager creates a NEW persistent backend,
                     * therefore a NEW libgphoto2 session.
                     */
                    CameraBackend backend =
                            CameraManager.openConfiguredBackend(
                                    detectedName
                            );

                    if (backend == null) {
                        sleepBeforeRetry();
                        continue;
                    }

                    if (stopped) {
                        CameraManager.close();
                        return;
                    }

                    reconnecting.set(false);

                    SwingUtilities.invokeLater(
                            () -> listener.onCameraReconnected(backend)
                    );

                    DebugLog.info(
                            "Camera reconnect completed successfully."
                    );

                    return;

                } catch (Exception ex) {
                    DebugLog.warn(
                            "Camera reconnect attempt failed: "
                                    + ex.getMessage()
                    );

                    CameraManager.close();
                    sleepBeforeRetry();
                }
            }
        } finally {
            reconnecting.set(false);
        }
    }

    private String findConfiguredCamera(List<String> detected) {
        if (detected == null || detected.isEmpty()) {
            return null;
        }

        String wanted = normalize(configuredCameraName);

        for (String name : detected) {
            if (name != null
                    && name.trim().equalsIgnoreCase(wanted)) {
                return name.trim();
            }
        }

        return null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        String result = value.trim();

        if (result.startsWith("gphoto2:")) {
            result = result.substring("gphoto2:".length()).trim();
        }

        if (result.endsWith(" [gPhoto2]")) {
            result = result.substring(
                    0,
                    result.length() - " [gPhoto2]".length()
            ).trim();
        }

        return result;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            stopped = true;
        }
    }

    public void stop() {
        stopped = true;
    }

    public boolean isReconnecting() {
        return reconnecting.get();
    }
}
