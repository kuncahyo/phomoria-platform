package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.cloud.AuthService;
import com.phomoria.cloud.FrameSyncService;
import com.phomoria.debug.DebugLog;

import javax.swing.*;
import java.awt.*;

public final class LoginScreen extends JPanel {
    private final JFrame owner;
    private final Runnable success;
    private final AuthService auth = new AuthService();
    private final JTextField email = new JTextField();
    private final JPasswordField password = new JPasswordField();
    private final JLabel status = new JLabel(" ");

    public LoginScreen(JFrame owner, Runnable success) {
        this.owner = owner;
        this.success = success;
        setLayout(new GridBagLayout());
        setBackground(new Color(20, 20, 24));

        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(35, 45, 35, 45));
        card.setBackground(new Color(34, 34, 40));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 8, 8, 8);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;

        JLabel title = new JLabel("PHOMORIA PLATFORM");
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        title.setForeground(Color.WHITE);
        card.add(title, g);

        JLabel sub = new JLabel("Operator Login");
        sub.setForeground(new Color(180, 180, 190));
        g.gridy++;
        card.add(sub, g);

        g.gridwidth = 1; g.gridy++;
        card.add(label("Email"), g);
        g.gridx = 1; card.add(email, g);

        g.gridx = 0; g.gridy++;
        card.add(label("Password"), g);
        g.gridx = 1; card.add(password, g);

        JButton login = new JButton("LOGIN");
        JButton demo = new JButton("DEMO / PRESENTATION");
        g.gridx = 0; g.gridy++; card.add(login, g);
        g.gridx = 1; card.add(demo, g);

        g.gridx = 0; g.gridy++; g.gridwidth = 2;
        status.setForeground(new Color(230, 190, 80));
        card.add(status, g);

        add(card);

        login.addActionListener(e -> doLogin());
        demo.addActionListener(e -> {
            status.setText("Demo session aktif.");
            success.run();
        });
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.WHITE);
        return l;
    }

    private void doLogin() {
        String e = email.getText().trim();
        String p = new String(password.getPassword());

        if (e.isBlank() || p.isBlank()) {
            status.setText("Email dan password wajib diisi.");
            return;
        }

        status.setText("Menghubungkan ke API...");
        setButtonsEnabled(false);

        new SwingWorker<Boolean, String>() {
            private Exception failure;

            @Override
            protected Boolean doInBackground() {
                try {
                    boolean loggedIn = auth.login(
                            AppContext.settings().getApiServer(),
                            e,
                            p
                    );

                    if (!loggedIn) {
                        return false;
                    }

                    publish("Login berhasil. Sinkronisasi frame...");
                    DebugLog.info("Login succeeded. Starting frame cache sync.");

                    /*
                     * The token is already stored by AuthService.login().
                     * Run sync before entering START so FrameSelectionScreen
                     * immediately sees the latest assignments from cloud.
                     */
                    FrameSyncService syncService = new FrameSyncService();
                    FrameSyncService.SyncResult result = syncService.sync();

                    DebugLog.info(
                            "Login frame sync completed: " +
                            "downloaded=" + result.count(FrameSyncService.Action.DOWNLOADED) +
                            ", updated=" + result.count(FrameSyncService.Action.UPDATED) +
                            ", unchanged=" + result.count(FrameSyncService.Action.UNCHANGED) +
                            ", deleted=" + result.count(FrameSyncService.Action.DELETED)
                    );

                    return true;
                } catch (Exception ex) {
                    failure = ex;
                    DebugLog.error("Login/frame sync failed.", ex);
                    return false;
                }
            }

            @Override
            protected void process(java.util.List<String> messages) {
                if (!messages.isEmpty()) {
                    status.setText(messages.get(messages.size() - 1));
                }
            }

            @Override
            protected void done() {
                setButtonsEnabled(true);

                try {
                    if (get()) {
                        status.setText("Login berhasil. Frame cloud sudah diperbarui.");
                        success.run();
                    } else if (failure != null) {
                        status.setText(
                                "Login berhasil, tetapi sinkronisasi frame gagal: " +
                                failure.getMessage()
                        );
                    } else {
                        status.setText(auth.getLastError());
                    }
                } catch (Exception ex) {
                    status.setText(
                            "Proses login/sinkronisasi gagal: " +
                            ex.getMessage()
                    );
                }
            }
        }.execute();
    }

    private void setButtonsEnabled(boolean enabled) {
        for (Component c : ((JPanel)getComponent(0)).getComponents()) {
            if (c instanceof JButton b) b.setEnabled(enabled);
        }
    }
}
