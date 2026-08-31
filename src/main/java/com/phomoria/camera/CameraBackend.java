package com.phomoria.camera;

import java.awt.image.BufferedImage;

public interface CameraBackend {

    String getId();

    String getDisplayName();

    boolean isAvailable();

    void open() throws Exception;

    void close();

    BufferedImage capture() throws Exception;

    default BufferedImage getLiveImage() throws Exception {
        return capture();
    }
}
