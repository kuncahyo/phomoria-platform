package com.phomoria.app;

import com.phomoria.ui.UpdateScreen;
import com.phomoria.upload.UploadQueueManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                com.phomoria.debug.DebugLog.error(
                        "UNCAUGHT THREAD ERROR thread=" + thread.getName()
                                + ", threadState=" + thread.getState(), error));
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                com.phomoria.debug.DebugLog.info("APPLICATION SHUTDOWN requested."),
                "Phomoria-Shutdown-Trace"));
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName()
                );
            } catch (Exception ignored) {
            }

            AppContext.initialize();
            UploadQueueManager.start();

            ApplicationFrame frame = new ApplicationFrame();
            frame.setVisible(true);
            frame.startupUpdateCheck();
        });
    }
}
