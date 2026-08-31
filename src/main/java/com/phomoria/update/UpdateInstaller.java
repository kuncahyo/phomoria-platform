package com.phomoria.update;

import com.phomoria.debug.DebugLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Hands a downloaded update package to a separate updater process.
 *
 * The running Phomoria process never replaces its own application files.
 */
public final class UpdateInstaller {

    private UpdateInstaller() {
    }

    public static void prepareSimulation(
            Path packageFile,
            UpdateInfo info
    ) throws IOException {

        if (packageFile == null) {
            throw new IOException("Downloaded update package path is null.");
        }

        if (!Files.isRegularFile(packageFile)) {
            throw new IOException(
                    "Downloaded update package does not exist: " + packageFile
            );
        }

        DebugLog.info(
                "Update package ready: "
                        + packageFile.toAbsolutePath()
        );

        if (info != null) {
            DebugLog.info(
                    "Update package version=" + info.getVersion()
            );
        }

        DebugLog.warn(
                "SIMULATION MODE: application files were not replaced."
        );
    }

    /**
     * True when the application is running from a packaged application image.
     *
     * In a jpackage image the application JAR is normally:
     *
     *   <install-root>/app/<application>.jar
     *
     * Therefore the installation root is two parents above the JAR.
     */
    public static boolean isPackagedApplication() {
        try {
            Path applicationJar = getCodeSourcePath();

            boolean packaged =
                    Files.isRegularFile(applicationJar)
                            && applicationJar.toString()
                            .toLowerCase()
                            .endsWith(".jar")
                            && isPackagedLayout(applicationJar);

            DebugLog.info(
                    "UpdateInstaller.isPackagedApplication -> "
                            + packaged
                            + ", codeSource="
                            + applicationJar
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
     * Starts UpdateLauncher as a separate JVM process.
     *
     * The current Phomoria process must terminate after this method returns.
     */
    public static void launchProductionUpdater(
            Path packageFile
    ) throws IOException {

        if (packageFile == null) {
            throw new IOException("Update package path is null.");
        }

        if (!Files.isRegularFile(packageFile)) {
            throw new IOException(
                    "Update package not found: " + packageFile
            );
        }

        Path applicationJar = getCodeSourcePath();

        if (!isPackagedLayout(applicationJar)) {
            throw new IOException(
                    "Production updater requires a jpackage application image. "
                            + "Expected <install-root>\\app\\<application>.jar "
                            + "and <install-root>\\Phomoria.exe."
            );
        }

        Path appDirectory = applicationJar.getParent();
        Path installationDirectory = appDirectory == null
                ? null
                : appDirectory.getParent();

        if (installationDirectory == null) {
            throw new IOException(
                    "Unable to determine Phomoria installation directory."
            );
        }

        Path launcher = findApplicationLauncher(installationDirectory);
        Path javaExecutable = findJavaExecutable();

        DebugLog.info("Starting production UpdateLauncher.");
        DebugLog.info("Application JAR=" + applicationJar);
        DebugLog.info(
                "Installation root="
                        + installationDirectory
        );
        DebugLog.info("Application directory=" + appDirectory);
        DebugLog.info("Launcher=" + launcher);
        DebugLog.info("Java executable=" + javaExecutable);

        ProcessBuilder builder = new ProcessBuilder(
                javaExecutable.toString(),

                "-cp",
                applicationJar.toString(),

                UpdateLauncher.class.getName(),

                "--pid",
                Long.toString(ProcessHandle.current().pid()),

                "--package",
                packageFile.toAbsolutePath().toString(),

                "--install",
                installationDirectory.toAbsolutePath().toString(),

                "--launch",
                launcher.toAbsolutePath().toString(),

                "--java",
                javaExecutable.toAbsolutePath().toString()
        );

        builder.directory(installationDirectory.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();

        DebugLog.info(
                "Production UpdateLauncher started. pid="
                        + process.pid()
        );
    }

    private static boolean isPackagedLayout(Path applicationJar) {

        if (applicationJar == null) {
            DebugLog.warn(
                    "Packaged layout check failed: applicationJar is null."
            );
            return false;
        }

        DebugLog.info(
                "Packaged layout check: applicationJar="
                        + applicationJar
        );

        boolean jarExists =
                Files.isRegularFile(applicationJar);

        DebugLog.info(
                "Packaged layout check: applicationJar exists="
                        + jarExists
        );

        if (!jarExists) {
            return false;
        }

        Path appDirectory =
                applicationJar.getParent();

        if (appDirectory == null) {
            DebugLog.warn(
                    "Packaged layout check failed: app directory is null."
            );
            return false;
        }

        boolean appDirectoryCorrect =
                "app".equalsIgnoreCase(
                        appDirectory.getFileName().toString()
                );

        DebugLog.info(
                "Packaged layout check: app directory name correct="
                        + appDirectoryCorrect
        );

        if (!appDirectoryCorrect) {
            return false;
        }

        Path root =
                appDirectory.getParent();

        if (root == null || !Files.isDirectory(root)) {
            DebugLog.warn(
                    "Packaged layout check failed: installation root invalid."
            );
            return false;
        }

        Path launcher =
                root.resolve("Phomoria.exe");

        boolean launcherExists =
                Files.isRegularFile(launcher);

        DebugLog.info(
                "Packaged layout check: launcher="
                        + launcher
                        + ", exists="
                        + launcherExists
        );

        /*
         * Do NOT require runtime\bin\java.exe here.
         *
         * The current jpackage image contains the Java runtime,
         * but java.exe is not necessarily present in this layout.
         *
         * The updater will determine the JVM executable separately.
         */
        boolean packaged =
                launcherExists;

        DebugLog.info(
                "Packaged layout check result="
                        + packaged
        );

        return packaged;
    }

    private static Path findApplicationLauncher(
            Path installationDirectory
    ) throws IOException {

        Path launcher = installationDirectory.resolve("Phomoria.exe");

        if (!Files.isRegularFile(launcher)) {
            throw new IOException(
                    "Phomoria.exe not found in installation root: "
                            + installationDirectory
            );
        }

        return launcher;
    }

    /**
     * Finds the Java executable belonging to the current JVM.
     *
     * In a jpackage image this resolves to:
     * <install-root>/runtime/bin/java.exe
     */
    private static Path findJavaExecutable()
            throws IOException {

        /*
         * For the current production-update test, use the JVM
         * that is running Phomoria.
         *
         * The jpackage runtime in this build is a runtime image
         * without java.exe, so we must not assume:
         *
         *     <install-root>\runtime\bin\java.exe
         */

        Path javaHome = Path.of(
                System.getProperty("java.home")
        ).toAbsolutePath().normalize();

        DebugLog.info(
                "Searching Java executable. java.home="
                        + javaHome
        );

        Path javaExe = javaHome
                .resolve("bin")
                .resolve("java.exe");

        if (Files.isRegularFile(javaExe)) {

            DebugLog.info(
                    "Java executable found: "
                            + javaExe
            );

            return javaExe;
        }

        Path javaUnix = javaHome
                .resolve("bin")
                .resolve("java");

        if (Files.isRegularFile(javaUnix)) {

            DebugLog.info(
                    "Java executable found: "
                            + javaUnix
            );

            return javaUnix;
        }

        /*
         * On Windows, java.home may point to the JRE/runtime
         * directory rather than the JDK root. Try its parent.
         */
        Path parent = javaHome.getParent();

        if (parent != null) {

            Path parentJavaExe = parent
                    .resolve("bin")
                    .resolve("java.exe");

            if (Files.isRegularFile(parentJavaExe)) {

                DebugLog.info(
                        "Java executable found from parent java.home: "
                                + parentJavaExe
                );

                return parentJavaExe;
            }
        }

        throw new IOException(
                "Java executable not found. "
                        + "java.home=" + javaHome
        );
    }

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
