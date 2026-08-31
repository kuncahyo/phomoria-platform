package com.phomoria.camera;

public record CameraDevice(
        String id,
        String displayName,
        String backendId,
        boolean available
) {
}
