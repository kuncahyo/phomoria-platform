# Phomoria Platform — v17 Camera Backend Foundation

## Fixed

Settings camera status is now live:
- checks the selected camera every 1 second;
- changes to "Kamera tidak terdeteksi / USB terputus" when the device
  disappears;
- changes back to "Kamera tersedia" when it returns;
- the polling timer stops when SettingsScreen is removed.

## Camera architecture

Added:
- CameraBackend.java
- CameraDevice.java
- WebcamCameraBackend.java
- CanonEdsdkBackend.java
- CanonEdsdkBridge.java

The Canon classes are deliberately a native-bridge boundary. They do NOT
claim EOS 700D support until the real Canon EDSDK package is supplied and
wired in. This avoids the false "camera available" state.

Canon EDSDK is the correct direction for direct USB Canon control; EOS 700D
is listed in Canon's EDSDK compatibility information. A native bridge is
required because the SDK is not a normal Java Webcam Capture device.

The existing Webcam Capture path is unchanged.
LiveCameraPanel.java is unchanged.
MainScreen.java is unchanged in this revision.

Next implementation step:
1. Obtain the Canon EDSDK package under its Canon developer/license terms.
2. Inspect the supplied Windows native libraries and headers/examples.
3. Implement the native bridge for discovery, open, live view and capture.
4. Connect CameraManager to CameraBackend so MainScreen no longer depends
   directly on Webcam for the camera source.
