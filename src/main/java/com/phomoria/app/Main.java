package com.phomoria.app;

import com.phomoria.ui.UpdateScreen;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName()
                );
            } catch (Exception ignored) {
            }

            AppContext.initialize();

            ApplicationFrame frame = new ApplicationFrame();
            frame.setVisible(true);
            frame.startupUpdateCheck();
        });
    }
}
