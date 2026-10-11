package com.phomoria.app;

import com.phomoria.ui.LoginScreen;
import com.phomoria.ui.MainScreen;
import com.phomoria.ui.ResultScreen;
import com.phomoria.ui.EffectScreen;
import com.phomoria.ui.SettingsScreen;
import com.phomoria.ui.StartScreen;
import com.phomoria.debug.DebugConsoleDialog;
import com.phomoria.debug.DebugLog;
import com.phomoria.ui.FrameSelectionScreen;
import com.phomoria.ui.UpdateScreen;
import com.phomoria.update.UpdateCheckResult;
import com.phomoria.update.UpdateConfig;
import com.phomoria.update.UpdateService;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class ApplicationFrame extends JFrame {
    private static final int EXIT_HOT_ZONE_HEIGHT = 35;

    /*
     * hardcode sementara, wajib ubah
     */
    private static final String EXIT_PASSWORD = "pmr123";
    /*
     * hardcode sementara, wajib ubah
     */

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    public ApplicationFrame() {
        super("Phomoria Platform");

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setUndecorated(true);
        setMinimumSize(new Dimension(1100, 700));
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        addScreen("UPDATE", new UpdateScreen());
        addScreen("LOGIN", new LoginScreen(this, this::showStart));
        addScreen("START", new StartScreen(this));
        addScreen("FRAME_SELECTION", new FrameSelectionScreen(this));
        addScreen("MAIN", new JPanel());
        addScreen("RESULT", new ResultScreen(this));
        addScreen("EFFECT", new JPanel());
        addScreen("SETTINGS", new SettingsScreen(this, this::showStart));

        add(root);
        installHiddenExitHotZone();
        installBackdoorLog();

        DebugLog.info("ApplicationFrame initialized.");
    }

    /**
     * Transparent clickable strip over the very top of the application.
     * It is placed in the layered pane so it does NOT reserve layout space
     * and therefore cannot expose the JFrame/content-pane background.
     */
    private void installHiddenExitHotZone() {
        JComponent hotZone = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                // Intentionally paints nothing: this component must remain invisible.
            }
        };

        hotZone.setOpaque(false);
        hotZone.setBackground(new Color(0, 0, 0, 0));
        hotZone.setBorder(null);
        hotZone.setFocusable(false);

        hotZone.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showExitDialog();
            }
        });

        JLayeredPane layeredPane = getLayeredPane();

        Runnable resizeHotZone = () -> {
            hotZone.setBounds(
                    0,
                    0,
                    layeredPane.getWidth(),
                    EXIT_HOT_ZONE_HEIGHT
            );
        };

        layeredPane.add(hotZone, JLayeredPane.PALETTE_LAYER);
        resizeHotZone.run();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeHotZone.run();
            }

            @Override
            public void componentShown(ComponentEvent e) {
                resizeHotZone.run();
            }
        });
    }

    private void showExitDialog() {
        JPasswordField passwordField = new JPasswordField(18);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(
                new JLabel("Apakah anda ingin menutup aplikasi?"),
                BorderLayout.NORTH
        );
        panel.add(passwordField, BorderLayout.CENTER);

        Object[] options = {"OK", "Cancel"};

        int result = JOptionPane.showOptionDialog(
                this,
                panel,
                "Tutup Aplikasi",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (result == JOptionPane.OK_OPTION) {
            String password = new String(passwordField.getPassword());

            /*
             * hardcode sementara, wajib ubah
             */
            boolean valid = EXIT_PASSWORD.equals(password);
            /*
             * hardcode sementara, wajib ubah
             */

            if (valid) {
                DebugLog.info("Application exit authorized.");
                dispose();
                System.exit(0);
            }
        }
    }

    public void startupUpdateCheck() {
        UpdateScreen screen = (UpdateScreen) findScreen("UPDATE");
        if (screen == null) { DebugLog.error("Update screen not found."); showLogin(); return; }
        cards.show(root, "UPDATE"); screen.showChecking();

        SwingWorker<UpdateCheckResult, Void> worker = new SwingWorker<>() {
            private Exception failure;

            @Override protected UpdateCheckResult doInBackground() {
                try {
                    UpdateService service = new UpdateService(UpdateConfig.UPDATE_URL);
                    var info = service.check();
                    return service.evaluate(info);
                } catch (Exception ex) {
                    failure = ex;
                    return null;
                }
            }

            @Override protected void done() {
                try {
                    UpdateCheckResult result = get();
                    if (result == null) {
                        DebugLog.warn("Update check unavailable. Continuing application startup.");
                        showLogin();
                        return;
                    }
                    switch (result.getStatus()) {
                        case UP_TO_DATE -> screen.showUpToDate(ApplicationFrame.this::showLogin);
                        case UPDATE_AVAILABLE -> screen.showOptional(
                                result.getUpdateInfo(),
                                ApplicationFrame.this::showLogin
                        );
                        case UPDATE_REQUIRED -> screen.showRequired(result.getUpdateInfo());
                    }
                } catch (Exception ex) {
                    DebugLog.error("Update startup check failed.", ex);
                    showLogin();
                }
            }
        };
        worker.execute();
    }

    public void finishStartupAfterUpdate() {
        DebugLog.info("Startup update flow finished.");
        showLogin();
    }

    private void installBackdoorLog() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F12"), "phomoria-debug-console");

        getRootPane().getActionMap().put(
                "phomoria-debug-console",
                new AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                        DebugConsoleDialog dialog =
                                new DebugConsoleDialog(ApplicationFrame.this);
                        dialog.setVisible(true);
                    }
                }
        );
    }

    public void showLogin() {
        DebugLog.info("Navigation -> LOGIN");
        cards.show(root, "LOGIN");
    }

    public void showStart() {
        DebugLog.info("Navigation -> START");
        replace("START", new StartScreen(this));
        cards.show(root, "START");
    }

    public void startPaidSession() {
        DebugLog.info("Paid session started.");
        AppContext.newSession();
        replace("MAIN", new MainScreen(this));
        cards.show(root, "MAIN");
    }

    public void showMain() {
        startPaidSession();
    }

    public void showFrameSelection() {
        DebugLog.info("Navigation -> FRAME_SELECTION");
        replace("FRAME_SELECTION", new FrameSelectionScreen(this));
        cards.show(root, "FRAME_SELECTION");
    }

    public void cancelPhotoSession() {
        if (!AppContext.settings().isShowCancelSession()) {
            DebugLog.warn("Cancel photo session ignored because feature is disabled.");
            return;
        }

        DebugLog.info("Photo session cancelled -> Navigation -> FRAME_SELECTION");

        Component main = findScreen("MAIN");

        if (main instanceof MainScreen mainScreen) {
            mainScreen.shutdown();
            root.remove(mainScreen);
        } else if (main != null) {
            root.remove(main);
        }

        AppContext.newSession();
        replace("FRAME_SELECTION", new FrameSelectionScreen(this));
        cards.show(root, "FRAME_SELECTION");

        root.revalidate();
        root.repaint();
    }

    public void showResult() {
        if (AppContext.settings().getPhotoEffectSettings().isEnabled()
                && AppContext.session() != null
                && !AppContext.session().getPhotos().isEmpty()) {
            DebugLog.info("Photo effects enabled -> Navigation -> EFFECT");
            EffectScreen effect = new EffectScreen(this);
            replace("EFFECT", effect);
            cards.show(root, "EFFECT");
            return;
        }

        showFinalResult();
    }

    public void showFinalResult() {
        DebugLog.info("Navigation -> RESULT");
        ResultScreen result = new ResultScreen(this);
        replace("RESULT", result);
        cards.show(root, "RESULT");
        SwingUtilities.invokeLater(result::prepareResult);
    }

    public void showSettings() {
        DebugLog.info("Navigation -> SETTINGS");
        replace("SETTINGS", new SettingsScreen(this, this::showStart));
        cards.show(root, "SETTINGS");
    }

    private void addScreen(String name, Component component) {
        component.setName(name);
        root.add(component, name);
    }

    private Component findScreen(String name) {
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName())) return component;
        }
        return null;
    }

    private void replace(String name, Component component) {
        Component old = findScreen(name);
        if (old != null) {
            if (old instanceof MainScreen) {
                DebugLog.info("Replacing MainScreen -> shutting down old instance.");
                ((MainScreen) old).shutdown();
            }
            root.remove(old);
        }
        addScreen(name, component);
        root.revalidate();
        root.repaint();
    }
}
