# Phomoria v20 Windows Packaging

## Purpose

v20 creates a self-contained Windows application image using JDK 21 `jpackage`.

The first target is an **application image**, not a production installer.

## Required on the development PC

- JDK 21
- Maven 3.9.x
- Windows
- `java` available in PATH
- `jpackage` available in PATH

The final customer package will bundle a Java runtime, so customers will not need to install JDK 21.

## Step 1 — build application image

From the project root:

```bat
packaging\windows\build-app-image.bat
```

Expected result:

```text
target\
└── Phomoria\
    ├── Phomoria.exe
    └── app\
        ├── phomoria-platform-0.1.0-SNAPSHOT.jar
        └── lib\
```

Run:

```bat
target\Phomoria\Phomoria.exe
```

## Step 2 — test the application image

The application image must behave like the Maven/NetBeans version:

- startup
- update check
- login
- settings
- camera selection
- photo session
- effects
- result
- printer

No source-code changes should be needed for normal application behavior.

## Step 3 — create an EXE installer

After the application image works:

```bat
packaging\windows\build-installer.bat
```

The installer is created under:

```text
target\installer\
```

## Important

Do not switch `UpdateConfig.SIMULATION_MODE` to false yet.

v20 first proves that the application can run as a packaged Windows application.

Only after the packaged application is verified will we test the production updater.

## Git

Do not commit:

```text
target/
```

The generated application image and installer are build artifacts.

They must stay ignored by Git.
