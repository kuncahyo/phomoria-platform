# Phomoria v19 — Production Updater Foundation

## What changed

v19 adds a separate-JVM update launcher.

The running Phomoria process never replaces its own JAR.

Flow:

1. Phomoria checks the update server.
2. The update ZIP is downloaded.
3. SHA-256 is verified by `UpdateDownloader`.
4. `UpdateInstaller` starts `UpdateLauncher` as a separate process.
5. Phomoria exits.
6. `UpdateLauncher` waits for the old PID to exit.
7. The ZIP is staged.
8. Existing files that are being replaced are backed up.
9. `app/` from the ZIP is copied into the installation directory.
10. Phomoria is launched again.

## Important development behavior

`UpdateConfig.SIMULATION_MODE` remains `true` while the project is run from NetBeans.

Do NOT set it to false yet.

The production path requires a packaged JAR installation and a release ZIP with this structure:

```text
Phomoria-<version>.zip
└── app/
    ├── Phomoria.jar
    ├── libraries/
    └── ...
```

The next packaging step should create the real Windows distribution and test the updater against a disposable installation directory.

## Safety

The updater is intentionally not connected to Laravel yet.

The current development endpoint remains:

`http://127.0.0.1:8099/update.json`

Do not put credentials or private signing keys in the Java project.
