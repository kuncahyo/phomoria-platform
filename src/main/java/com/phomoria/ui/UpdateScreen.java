package com.phomoria.ui;

import com.phomoria.debug.DebugLog;
import com.phomoria.update.UpdateCheckResult;
import com.phomoria.update.UpdateDownloader;
import com.phomoria.update.UpdateInfo;
import com.phomoria.update.UpdateInstaller;
import com.phomoria.update.UpdateConfig;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

public final class UpdateScreen extends JPanel {
    private final JLabel statusLabel = new JLabel("Memeriksa update...");
    private final JLabel versionLabel = new JLabel();
    private final JTextArea notes = new JTextArea();
    private final JButton actionButton = new JButton("DOWNLOAD UPDATE");
    private final JButton continueButton = new JButton("LANJUTKAN");

    private UpdateInfo updateInfo;
    private Path downloadedPackage;

    public UpdateScreen() {
        setLayout(new BorderLayout(20, 20));
        setBorder(BorderFactory.createEmptyBorder(50, 80, 50, 80));

        JLabel title = new JLabel("PHOMORIA UPDATE");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 30));

        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 18));

        versionLabel.setHorizontalAlignment(SwingConstants.CENTER);

        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setOpaque(false);
        notes.setFont(new Font("SansSerif", Font.PLAIN, 15));

        actionButton.setEnabled(false);
        continueButton.setVisible(false);

        JPanel center = new JPanel(new BorderLayout(10, 15));
        center.add(statusLabel, BorderLayout.NORTH);
        center.add(versionLabel, BorderLayout.CENTER);
        center.add(new JScrollPane(notes), BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        bottom.add(actionButton);
        bottom.add(continueButton);

        add(title, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        actionButton.addActionListener(e -> downloadUpdate());
    }

    public void showChecking() {
        statusLabel.setText("Memeriksa update...");
        versionLabel.setText("");
        notes.setText("");
        actionButton.setEnabled(false);
        continueButton.setVisible(false);
    }

    public void showUpToDate(Runnable continueAction) {
        statusLabel.setText("Aplikasi sudah versi terbaru.");
        versionLabel.setText("Versi " + com.phomoria.update.AppVersion.current());
        actionButton.setVisible(false);
        continueButton.setText("MULAI APLIKASI");
        continueButton.setVisible(true);
        continueButton.addActionListener(e -> continueAction.run());
    }

    public void showOptional(UpdateInfo info, Runnable continueAction) {
        updateInfo = info;
        statusLabel.setText("Update tersedia.");
        versionLabel.setText(
                "Versi sekarang: "
                        + com.phomoria.update.AppVersion.current()
                        + "  →  Versi baru: " + info.getVersion()
        );
        notes.setText(
                info.getReleaseNotes() == null ? "" : info.getReleaseNotes()
        );
        actionButton.setVisible(true);
        actionButton.setText("DOWNLOAD UPDATE");
        actionButton.setEnabled(true);
        continueButton.setText("LEWATI");
        continueButton.setVisible(true);
        continueButton.addActionListener(e -> continueAction.run());
    }

    public void showRequired(UpdateInfo info) {
        updateInfo = info;
        statusLabel.setText("UPDATE WAJIB");
        versionLabel.setText(
                "Versi " + info.getVersion()
                        + " diperlukan sebelum aplikasi dapat digunakan."
        );
        notes.setText(
                info.getReleaseNotes() == null ? "" : info.getReleaseNotes()
        );
        actionButton.setVisible(true);
        actionButton.setText("DOWNLOAD UPDATE");
        actionButton.setEnabled(true);
        continueButton.setVisible(false);
    }

    private void downloadUpdate() {
        actionButton.setEnabled(false);
        statusLabel.setText("Mengunduh update...");
        DebugLog.info(
                "UpdateScreen: download requested for "
                        + updateInfo.getVersion()
        );

        SwingWorker<Path, Void> worker = new SwingWorker<>() {
            @Override
            protected Path doInBackground()
                    throws Exception {
                return new UpdateDownloader().download(updateInfo);
            }

            @Override
            protected void done() {
                try {
                    downloadedPackage = get();

                    UpdateInstaller.prepareSimulation(
                            downloadedPackage,
                            updateInfo
                    );

                    statusLabel.setText(
                            "Update berhasil diunduh."
                    );

                    if (UpdateConfig.SIMULATION_MODE) {
                        actionButton.setText("SIMULASIKAN INSTALL");
                        actionButton.setEnabled(true);
                        actionButton.removeActionListener(
                                actionButton.getActionListeners()[0]
                        );
                        actionButton.addActionListener(
                                e -> finishSimulation()
                        );
                    }
                } catch (Exception ex) {
                    DebugLog.error(
                            "Update download failed.",
                            ex
                    );

                    statusLabel.setText(
                            "Download update gagal."
                    );

                    actionButton.setText(
                            "COBA LAGI"
                    );
                    actionButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void finishSimulation() {
        DebugLog.warn(
                "Mandatory update simulation completed. "
                        + "Continuing without replacing application."
        );

        statusLabel.setText(
                "SIMULASI UPDATE SELESAI"
        );

        versionLabel.setText(
                "Paket tersimpan di: "
                        + downloadedPackage
        );

        actionButton.setVisible(false);

        continueButton.setText("MASUK KE APLIKASI");
        continueButton.setVisible(true);
        continueButton.addActionListener(
                e -> {
                    Window window =
                            SwingUtilities.getWindowAncestor(this);
                    if (window instanceof com.phomoria.app.ApplicationFrame frame) {
                        frame.finishStartupAfterUpdate();
                    }
                }
        );
    }
}
