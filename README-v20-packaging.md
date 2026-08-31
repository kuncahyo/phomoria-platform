# Phomoria v20 — Windows Packaging

v20 introduces the Windows packaging layer.

The project remains a Maven Java 21 Swing application, but the same compiled application can now be assembled into a Windows application image with a bundled Java runtime using `jpackage`.

This branch intentionally does not alter camera, frame, photo-session, effects, printer, or updater logic.

The production updater remains disabled in development:

`UpdateConfig.SIMULATION_MODE = true`

The first acceptance test for v20 is:

1. Build application image.
2. Launch `target\Phomoria\Phomoria.exe`.
3. Verify the normal Phomoria startup flow.
4. Verify the update screen still works.
5. Verify the application does not require NetBeans or Maven at runtime.
6. Only then create the EXE installer.

Generated `target/` content must not be committed to Git.
