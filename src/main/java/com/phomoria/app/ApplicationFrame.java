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

import javax.swing.*;
import java.awt.*;

public final class ApplicationFrame extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    public ApplicationFrame() {
        super("Phomoria Platform");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 700));
        setSize(1440, 900);
        setLocationRelativeTo(null);

        addScreen("LOGIN", new LoginScreen(this, this::showStart));
        addScreen("START", new StartScreen(this));
        addScreen("FRAME_SELECTION", new FrameSelectionScreen(this));
        addScreen("MAIN", new JPanel());
        addScreen("RESULT", new ResultScreen(this));
        addScreen("EFFECT", new JPanel());
        addScreen("SETTINGS", new SettingsScreen(this, this::showStart));

        add(root);
        installBackdoorLog();
        DebugLog.info("ApplicationFrame initialized.");
        showLogin();
    }

    private void installBackdoorLog() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F12"), "phomoria-debug-console");

        getRootPane().getActionMap()
                .put("phomoria-debug-console", new AbstractAction() {
                    @Override
                    public void actionPerformed(java.awt.event.ActionEvent e) {
                        DebugConsoleDialog dialog =
                                new DebugConsoleDialog(ApplicationFrame.this);
                        dialog.setVisible(true);
                    }
                });
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
        replace(
                "FRAME_SELECTION",
                new FrameSelectionScreen(this)
        );
        cards.show(root, "FRAME_SELECTION");
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
        replace(
                "SETTINGS",
                new SettingsScreen(this, this::showStart)
        );
        cards.show(root, "SETTINGS");
    }

    private void addScreen(String name, Component component) {
        component.setName(name);
        root.add(component, name);
    }

    private void replace(String name, Component component) {
        Component old = null;

        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) {
                old = c;
                break;
            }
        }

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
