package com.phomoria.update;

import com.phomoria.debug.DebugLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles installation hand-off to the separate UpdateLauncher process.
 *
 * The running Phomoria application never replaces its own files directly.
 */
public final class UpdateInstaller {

    private UpdateInstaller() {
    }

    /**
     * Development/NetBeans mode.
     *
     * The downloaded package is only validated as a file.
     * No application files are replaced.
     */
    public static void prepareSimulation(
            Path packageFile,
            UpdateInfo info
    ) throws IOException {

        if (packageFile == null) {
            throw new IOException(
                    "Downloaded update package path is null."
            );
        }

        if (!Files.isRegularFile(packageFile)) {
            throw new IOException(
                    "Downloaded update package does not exist: "
                            + packageFile
            );
        }

        DebugLog.info(
                "Update package ready: "
                        + packageFile.toAbsolutePath()
        );

        if (info != null) {
            DebugLog.info(
                    "Update package version="
                            + info.getVersion()
            );
        }

        DebugLog.warn(
                "SIMULATION MODE: application files were not replaced."
        );
    }

    /**
     * Returns true when Phomoria is running from a packaged JAR.
     *
     * When running from NetBeans/Maven, the code source normally points
     * to target/classes rather than a JAR, so this returns false.
     */
    public static boolean isPackagedApplication() {

        try {
            Path location = getCodeSourcePath();

            boolean packaged =
                    Files.isRegularFile(location)
                            && location.toString()
                            .toLowerCase()
                            .endsWith(".jar");

            DebugLog.info(
                    "UpdateInstaller.isPackagedApplication -> "
                            + packaged
                            + ", location="
                            + location
            );

            return packaged;

        } catch (IOException ex) {

            DebugLog.warn(
                    "Unable to determine application packaging: "
                            + ex.getMessage()
            );

            return false;
        }
    }

    /**
     * Starts UpdateLauncher as a separate Java process.
     *
     * The current Phomoria process must exit after this method returns.
     */
    public static void launchProductionUpdater(
            Path packageFile
    ) throws IOException {

        if (packageFile == null) {
            throw new IOException(
                    "Update package path is null."
            );
        }

        if (!Files.isRegularFile(packageFile)) {
            throw new IOException(
                    "Update package not found: "
                            + packageFile
            );
        }

        if (!isPackagedApplication()) {
            throw new IOException(
                    "Production updater requires "
                            + "Phomoria to run from a packaged JAR."
            );
        }

        /*
         * getCodeSourcePath() now throws only IOException,
         * which is already declared by this method.
         */
        Path applicationJar = getCodeSourcePath();

        Path installationDirectory =
                applicationJar.getParent();

        if (installationDirectory == null) {
            throw new IOException(
                    "Unable to determine Phomoria installation directory."
            );
        }

        Path javaExecutable =
                findJavaExecutable();

        DebugLog.info(
                "Starting production UpdateLauncher."
        );

        DebugLog.info(
                "Application JAR="
                        + applicationJar
        );

        DebugLog.info(
                "Installation directory="
                        + installationDirectory
        );

        DebugLog.info(
                "Java executable="
                        + javaExecutable
        );

        ProcessBuilder builder =
                new ProcessBuilder(
                        javaExecutable.toString(),

                        "-cp",
                        applicationJar.toString(),

                        UpdateLauncher.class.getName(),

                        "--pid",
                        Long.toString(
                                ProcessHandle.current().pid()
                        ),

                        "--package",
                        packageFile
                                .toAbsolutePath()
                                .toString(),

                        "--install",
                        installationDirectory
                                .toAbsolutePath()
                                .toString(),

                        "--launch",
                        applicationJar
                                .toAbsolutePath()
                                .toString(),

                        "--java",
                        javaExecutable
                                .toAbsolutePath()
                                .toString()
                );

        builder.directory(
                installationDirectory.toFile()
        );

        builder.start();

        DebugLog.info(
                "Production UpdateLauncher started successfully."
        );
    }

    /**
     * Finds the Java executable belonging to the current JVM.
     */
    private static Path findJavaExecutable()
            throws IOException {

        Path javaHome =
                Path.of(
                        System.getProperty("java.home")
                );

        Path javaExe =
                javaHome
                        .resolve("bin")
                        .resolve("java.exe");

        if (Files.isRegularFile(javaExe)) {
            return javaExe;
        }

        Path javaUnix =
                javaHome
                        .resolve("bin")
                        .resolve("java");

        if (Files.isRegularFile(javaUnix)) {
            return javaUnix;
        }

        throw new IOException(
                "Java executable not found in: "
                        + javaHome
        );
    }

    /**
     * Gets the physical location from which this application code
     * is currently running.
     *
     * This method deliberately exposes only IOException to callers.
     */
    private static Path getCodeSourcePath()
            throws IOException {

        try {
            return Path.of(
                    UpdateInstaller.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            ).toAbsolutePath().normalize();

        } catch (Exception ex) {

            throw new IOException(
                    "Unable to determine application code location.",
                    ex
            );
        }
    }
}