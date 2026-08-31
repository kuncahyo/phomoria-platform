package com.phomoria.update;

import com.phomoria.debug.DebugLog;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

/**
 * Runs as a separate JVM process while the main Phomoria process is exiting.
 *
 * Arguments:
 *   --pid <pid>          PID of the old Phomoria process.
 *   --package <zip>      downloaded update ZIP.
 *   --install <dir>      jpackage installation root.
 *   --launch <path>      Phomoria.exe to start after installation.
 *   --java <path>        bundled Java executable.
 *
 * The update ZIP must contain an "app/" directory.
 *
 * The directory is installed into:
 *
 *   <install>/app/
 *
 * The jpackage launcher and bundled runtime are not replaced by a normal
 * application update.
 */
public final class UpdateLauncher {

    private UpdateLauncher() {
    }

    public static void main(String[] args) {
        int exitCode = 1;

        try {
            Arguments a = Arguments.parse(args);

            DebugLog.info("UpdateLauncher started.");
            DebugLog.info("Waiting for PID=" + a.pid);

            waitForProcessToExit(a.pid, 120_000);

            Path installRoot =
                    a.installDir.toAbsolutePath().normalize();

            Path packageFile =
                    a.packageFile.toAbsolutePath().normalize();

            Path launch =
                    a.launch.toAbsolutePath().normalize();

            if (!Files.isRegularFile(packageFile)) {
                throw new IOException(
                        "Update package not found: " + packageFile
                );
            }

            if (!Files.isDirectory(installRoot)) {
                throw new IOException(
                        "Installation root not found: " + installRoot
                );
            }

            validatePackagedLayout(installRoot, launch);

            Path appDirectory = installRoot.resolve("app");
            Path workDir = Files.createTempDirectory(
                    installRoot.getParent(),
                    "phomoria-update-"
            );

            Path stagedApp = workDir.resolve("app");
            Path backupDir = workDir.resolve("backup");

            Files.createDirectories(stagedApp);

            unzipAppDirectory(packageFile, stagedApp);

            if (!hasFiles(stagedApp)) {
                throw new IOException(
                        "Update package contains no app files."
                );
            }

            backupExisting(
                    appDirectory,
                    stagedApp,
                    backupDir
            );

            replaceDirectoryContents(
                    stagedApp,
                    appDirectory
            );

            DebugLog.info(
                    "Update installation completed. "
                            + "Application files replaced in "
                            + appDirectory
            );

            launch(a);

            deleteTree(workDir);
            exitCode = 0;

        } catch (Exception ex) {
            DebugLog.error("UpdateLauncher failed.", ex);
            exitCode = 2;
        }

        System.exit(exitCode);
    }

    private static void validatePackagedLayout(
            Path installRoot,
            Path launch
    ) throws IOException {

        Path appDirectory = installRoot.resolve("app");
        Path runtimeJava = installRoot
                .resolve("runtime")
                .resolve("bin")
                .resolve("java.exe");

        if (!Files.isDirectory(appDirectory)) {
            throw new IOException(
                    "Invalid Phomoria installation: missing app directory: "
                            + appDirectory
            );
        }

        if (!Files.isRegularFile(runtimeJava)) {
            throw new IOException(
                    "Invalid Phomoria installation: bundled Java not found: "
                            + runtimeJava
            );
        }

        if (!Files.isRegularFile(launch)) {
            throw new IOException(
                    "Phomoria launcher not found: " + launch
            );
        }

        Path normalizedLaunch =
                launch.toAbsolutePath().normalize();

        if (!normalizedLaunch.getParent().equals(
                installRoot
        )) {
            throw new IOException(
                    "Phomoria launcher is outside installation root: "
                            + normalizedLaunch
            );
        }
    }

    private static void waitForProcessToExit(
            long pid,
            long timeoutMs
    ) throws InterruptedException {

        if (pid <= 0) {
            return;
        }

        long deadline =
                System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {

            if (!ProcessHandle.of(pid)
                    .map(ProcessHandle::isAlive)
                    .orElse(false)) {
                return;
            }

            Thread.sleep(250);
        }

        throw new IllegalStateException(
                "Timed out waiting for Phomoria process PID=" + pid
        );
    }

    private static void unzipAppDirectory(
            Path zip,
            Path stagedApp
    ) throws IOException {

        try (FileSystem fs =
                     FileSystems.newFileSystem(
                             zip,
                             (ClassLoader) null
                     )) {

            Path root = fs.getPath("/app");

            if (!Files.isDirectory(root)) {
                throw new IOException(
                        "Invalid update package: "
                                + "missing app/ directory."
                );
            }

            copyTree(root, stagedApp);
        }
    }

    private static void backupExisting(
            Path existingApp,
            Path stagedApp,
            Path backupDir
    ) throws IOException {

        Files.createDirectories(backupDir);

        try (var stream = Files.walk(stagedApp)) {

            stream
                    .filter(Files::isRegularFile)
                    .forEach(source -> {

                        try {
                            Path relative =
                                    stagedApp.relativize(source);

                            Path existing =
                                    existingApp.resolve(relative);

                            if (Files.exists(existing)) {

                                Path backup =
                                        backupDir.resolve(relative);

                                Files.createDirectories(
                                        backup.getParent()
                                );

                                Files.copy(
                                        existing,
                                        backup,
                                        StandardCopyOption
                                                .REPLACE_EXISTING
                                );
                            }

                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
    }

    /**
     * Replaces the contents of the existing app directory.
     *
     * Files not present in the new package are removed so that stale
     * application files cannot remain after an update.
     */
    private static void replaceDirectoryContents(
            Path stagedApp,
            Path targetApp
    ) throws IOException {

        Path oldAppBackup = targetApp.resolveSibling(
                targetApp.getFileName() + ".old"
        );

        deleteTree(oldAppBackup);

        if (Files.exists(targetApp)) {
            Files.move(
                    targetApp,
                    oldAppBackup,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }

        try {
            copyTree(stagedApp, targetApp);
            deleteTree(oldAppBackup);

        } catch (IOException ex) {
            deleteTree(targetApp);

            if (Files.exists(oldAppBackup)) {
                Files.move(
                        oldAppBackup,
                        targetApp,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            throw ex;
        }
    }

    private static void copyTree(
            Path source,
            Path target
    ) throws IOException {

        try (var stream = Files.walk(source)) {

            stream.forEach(path -> {

                try {
                    Path relative =
                            source.relativize(path);

                    Path destination =
                            target.resolve(relative);

                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);

                    } else {
                        Files.createDirectories(
                                destination.getParent()
                        );

                        Files.copy(
                                path,
                                destination,
                                StandardCopyOption
                                        .REPLACE_EXISTING
                        );
                    }

                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
    }

    private static boolean hasFiles(
            Path dir
    ) throws IOException {

        try (var stream = Files.walk(dir)) {
            return stream.anyMatch(
                    Files::isRegularFile
            );
        }
    }

    private static void launch(
            Arguments a
    ) throws IOException {

        String launch =
                a.launch.toString().toLowerCase();

        if (launch.endsWith(".jar")) {

            String java =
                    a.javaExecutable == null
                            ? "java"
                            : a.javaExecutable.toString();

            new ProcessBuilder(
                    java,
                    "-jar",
                    a.launch.toString()
            )
                    .directory(a.installDir.toFile())
                    .start();

        } else {

            new ProcessBuilder(
                    a.launch.toString()
            )
                    .directory(a.installDir.toFile())
                    .start();
        }
    }

    private static void deleteTree(
            Path root
    ) {

        try {

            if (!Files.exists(root)) {
                return;
            }

            try (var stream = Files.walk(root)) {

                stream
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {

                            try {
                                Files.deleteIfExists(path);

                            } catch (IOException ignored) {
                                // Best effort cleanup.
                            }
                        });
            }

        } catch (IOException ignored) {
            // Best effort cleanup.
        }
    }

    private static final class Arguments {

        long pid;
        Path packageFile;
        Path installDir;
        Path launch;
        Path javaExecutable;

        static Arguments parse(
                String[] args
        ) {

            Arguments a = new Arguments();

            for (int i = 0; i < args.length; i++) {

                switch (args[i]) {

                    case "--pid" ->
                            a.pid = Long.parseLong(
                                    next(
                                            args,
                                            ++i,
                                            "--pid"
                                    )
                            );

                    case "--package" ->
                            a.packageFile = Path.of(
                                    next(
                                            args,
                                            ++i,
                                            "--package"
                                    )
                            );

                    case "--install" ->
                            a.installDir = Path.of(
                                    next(
                                            args,
                                            ++i,
                                            "--install"
                                    )
                            );

                    case "--launch" ->
                            a.launch = Path.of(
                                    next(
                                            args,
                                            ++i,
                                            "--launch"
                                    )
                            );

                    case "--java" ->
                            a.javaExecutable = Path.of(
                                    next(
                                            args,
                                            ++i,
                                            "--java"
                                    )
                            );

                    default ->
                            throw new IllegalArgumentException(
                                    "Unknown argument: "
                                            + args[i]
                            );
                }
            }

            if (a.packageFile == null
                    || a.installDir == null
                    || a.launch == null) {

                throw new IllegalArgumentException(
                        "Required arguments: "
                                + "--package --install --launch"
                );
            }

            return a;
        }

        private static String next(
                String[] args,
                int index,
                String option
        ) {

            if (index >= args.length) {
                throw new IllegalArgumentException(
                        "Missing value for " + option
                );
            }

            return args[index];
        }
    }
}
