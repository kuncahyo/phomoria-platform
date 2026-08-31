package com.phomoria.update;

import com.phomoria.debug.DebugLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UpdateInstaller {
    private UpdateInstaller() {}

    /**
     * Development-safe installation boundary.
     *
     * We deliberately do NOT replace the running application from inside
     * the Java UI. A production installer/updater must run as a separate
     * process after Phomoria exits.
     */
    public static void prepareSimulation(Path packageFile, UpdateInfo info)
            throws IOException {

        if (packageFile == null || !Files.exists(packageFile)) {
            throw new IOException("Downloaded update package does not exist.");
        }

        DebugLog.info(
                "Update package ready for installation: " + packageFile
        );

        DebugLog.warn(
                "SIMULATION MODE: application files were not replaced."
        );
        DebugLog.warn(
                "Production installer will be a separate updater process."
        );
    }
}
