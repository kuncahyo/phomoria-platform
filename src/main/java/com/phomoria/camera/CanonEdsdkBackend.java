package com.phomoria.camera;

import com.phomoria.debug.DebugLog;

import java.awt.image.BufferedImage;

/**
 * Canon EOS backend boundary.
 *
 * This class deliberately does NOT pretend that EOS 700D is a normal
 * Webcam Capture device. Canon EDSDK is a native Windows SDK and must be
 * supplied/licensed separately. The actual native bridge will be connected
 * here once the Canon EDSDK package is available to the project.
 *
 * The important architectural point is that the rest of Phomoria will
 * eventually use CameraBackend rather than knowing whether the source is
 * a webcam or Canon DSLR.
 */
public final class CanonEdsdkBackend
        implements CameraBackend {

    private final String cameraName;

    public CanonEdsdkBackend(
            String cameraName
    ) {
        this.cameraName =
                cameraName == null || cameraName.isBlank()
                        ? "Canon EOS"
                        : cameraName;
    }

    @Override
    public String getId() {
        return "canon-edsdk:" + cameraName;
    }

    @Override
    public String getDisplayName() {
        return cameraName;
    }

    @Override
    public boolean isAvailable() {
        /*
         * Do not return true merely because the name is configured.
         *
         * The native EDSDK bridge must report a real connected camera.
         */
        return CanonEdsdkBridge.isAvailable();
    }

    @Override
    public void open() throws Exception {

        DebugLog.info(
                "Canon EDSDK open requested: "
                        + cameraName
        );

        CanonEdsdkBridge.open(cameraName);
    }

    @Override
    public void close() {

        CanonEdsdkBridge.close();
    }

    @Override
    public BufferedImage capture()
            throws Exception {

        return CanonEdsdkBridge.capture();
    }

    @Override
    public BufferedImage getLiveImage()
            throws Exception {

        return CanonEdsdkBridge.getLiveImage();
    }
}
