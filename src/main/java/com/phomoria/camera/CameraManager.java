package com.phomoria.camera;

import com.github.sarxos.webcam.Webcam;
import com.phomoria.debug.DebugLog;
import static com.phomoria.frame.FrameCatalog.find;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.List;

public final class CameraManager {
    private static Webcam current;

    private CameraManager() {}

    public static List<Webcam> list() {
        List<Webcam> cameras = Webcam.getWebcams();
        DebugLog.info("CameraManager.list -> " + cameras.size() + " camera(s)");
        return cameras;
    }
    
    /**
    * Mencari kamera berdasarkan nama.
    *
    * Pencarian tidak membedakan huruf besar/kecil
    * dan menggunakan pencarian sebagian nama.
    */
   public static Webcam find(String name) {

       if (name == null || name.isBlank()) {

           DebugLog.warn(
                   "CameraManager.find -> "
                           + "empty camera name."
           );

           return null;
       }

       String keyword =
               name.trim().toLowerCase();

       DebugLog.info(
               "CameraManager.find -> searching: "
                       + name
       );

       List<Webcam> cameras = list();

       for (Webcam webcam : cameras) {

           if (webcam == null) {
               continue;
           }

           String cameraName =
                   webcam.getName();

           if (cameraName == null) {
               continue;
           }

           if (cameraName
                   .toLowerCase()
                   .contains(keyword)) {

               DebugLog.info(
                       "CameraManager.find -> matched: "
                               + cameraName
               );

               return webcam;
           }
       }

       DebugLog.warn(
               "CameraManager.find -> "
                       + "no camera matched: "
                       + name
       );

       return null;
   }

    public static Webcam current() {
        return current;
    }

    /**
     * Checks whether the named webcam is currently detectable.
     *
     * This method is intentionally separate from open(), so Settings can
     * poll availability without opening/closing the camera.
     */
    public static boolean isAvailable(
            String name
    ) {
        if (name == null || name.isBlank()) {
            return false;
        }

        try {
            for (Webcam webcam : list()) {
                if (webcam == null) {
                    continue;
                }

                String detectedName =
                        webcam.getName();

                if (detectedName != null
                        && detectedName.equals(name)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            DebugLog.warn(
                    "CameraManager.isAvailable failed: "
                            + ex.getMessage()
            );
        }

        return false;
    }

    /** Opens the exact camera saved in Settings. No silent fallback. */
    public static Webcam openConfigured(String configuredName) {
        if (configuredName == null || configuredName.isBlank()) {
            DebugLog.warn("CameraManager.openConfigured -> no camera configured.");
            return null;
        }

        DebugLog.info("Configured camera requested: " + configuredName);

        Webcam webcam = find(configuredName);
        if (webcam == null) {
            DebugLog.error("Configured camera not found: " + configuredName, null);
            return null;
        }

        open(webcam);
        return current;
    }

    public static void open(Webcam webcam) {
        close();
        current = webcam;

        if (webcam == null) {
            DebugLog.warn("CameraManager.open called with null webcam.");
            return;
        }

        try {
            Dimension[] sizes = webcam.getViewSizes();

            if (sizes.length > 0) {
                Dimension selected = sizes[sizes.length - 1];
                webcam.setViewSize(selected);
                DebugLog.info(
                        "Camera resolution selected="
                                + selected.width + "x" + selected.height
                );
            }

            webcam.open();
            DebugLog.info("Camera opened: " + webcam.getName());
        } catch (Exception ex) {
            current = null;
            DebugLog.error("Camera open failed.", ex);
            throw ex;
        }
    }

    public static BufferedImage capture() {
        BufferedImage image = current == null ? null : current.getImage();

        if (image != null) {
            DebugLog.info(
                    "CameraManager.capture -> "
                            + image.getWidth() + "x" + image.getHeight()
            );
        } else {
            DebugLog.warn("CameraManager.capture -> null");
        }

        return image;
    }

    public static void close() {
        if (current != null) {
            DebugLog.info("Closing camera: " + current.getName());
            try {
                current.close();
            } catch (Exception ex) {
                DebugLog.error("Camera close failed.", ex);
            }
            current = null;
        }
    }
}
