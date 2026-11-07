package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.cloud.AuthService;
import com.phomoria.cloud.CloudConfig;
import com.phomoria.cloud.CloudConfigManager;
import com.phomoria.cloud.RememberedCredentialsStore;
import com.phomoria.cloud.FrameSyncService;
import com.phomoria.debug.DebugLog;

import javax.swing.*;
import java.awt.*;
import com.phomoria.cloud.DeviceCloudService;

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
        loadRememberedCredentials();
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

                    publish("Login berhasil. Mendaftarkan device...");
                    DebugLog.info(
                            "Login succeeded. Starting device registration."
                    );

                    /*
                     * Device harus didaftarkan setelah login berhasil karena
                     * endpoint register membutuhkan token Sanctum.
                     *
                     * Device baru akan dibuat otomatis oleh server.
                     * Device yang sudah ada akan diperbarui informasinya.
                     */
                    DeviceCloudService deviceCloudService =
                            new DeviceCloudService();

                    deviceCloudService.register();

                    publish("Device terdaftar. Sinkronisasi frame...");
                    DebugLog.info(
                            "Device registration succeeded. Starting frame cache sync."
                    );

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
                        handleRememberedCredentialsAfterLogin(e, p);
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

    private void loadRememberedCredentials() {
        CloudConfig config = CloudConfigManager.getConfig();
        if (!config.isRememberLogin()) {
            return;
        }
        try {
            RememberedCredentialsStore.Credentials saved =
                    RememberedCredentialsStore.load();
            if (saved != null) {
                email.setText(saved.email());
                password.setText(saved.password());
                DebugLog.info("Remembered login credentials loaded securely.");
            }
        } catch (Exception ex) {
            DebugLog.error("Could not load remembered login credentials.", ex);
        }
    }

    private void handleRememberedCredentialsAfterLogin(String loggedInEmail, String loggedInPassword) {
        CloudConfig config = CloudConfigManager.getConfig();
        if (!config.isRememberLogin()) {
            try {
                RememberedCredentialsStore.clear();
            } catch (Exception ex) {
                DebugLog.error("Could not clear remembered login credentials while Remember Login is disabled.", ex);
            }
            return;
        }

        try {
            RememberedCredentialsStore.Credentials saved =
                    RememberedCredentialsStore.load();

            if (saved == null || saved.email().equalsIgnoreCase(loggedInEmail.trim())) {
                RememberedCredentialsStore.save(loggedInEmail, loggedInPassword);
                DebugLog.info("Remembered login credentials updated for the current account.");
                return;
            }

            Object[] options = {"SIMPAN AKUN BARU", "JANGAN GANTI"};
            int choice = JOptionPane.showOptionDialog(
                    this,
                    "Anda berhasil login menggunakan akun berbeda.\n"
                            + "Saat ini terdapat akun lain yang tersimpan di perangkat.\n\n"
                            + "Apakah Anda ingin mengganti akun yang tersimpan dengan akun baru ini?\n\n"
                            + "Jika memilih JANGAN GANTI, sesi saat ini tetap menggunakan akun baru, "
                            + "tetapi kredensial akun lama tetap digunakan untuk pengisian login berikutnya.",
                    "Ganti Akun Tersimpan",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[1]
            );

            if (choice == 0) {
                RememberedCredentialsStore.save(loggedInEmail, loggedInPassword);
                DebugLog.info("Remembered login account replaced by user confirmation.");
            } else {
                DebugLog.info("Previous remembered login account retained by user choice.");
            }
        } catch (Exception ex) {
            DebugLog.error("Could not update remembered login credentials.", ex);
            JOptionPane.showMessageDialog(
                    this,
                    "Login berhasil, tetapi kredensial Remember Login tidak dapat diperbarui secara aman.\n"
                            + ex.getMessage(),
                    "Remember Login",
                    JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        for (Component c : ((JPanel)getComponent(0)).getComponents()) {
            if (c instanceof JButton b) b.setEnabled(enabled);
        }
    }
}
