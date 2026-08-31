package com.phomoria.ui;

import com.phomoria.debug.DebugLog;
import com.phomoria.update.UpdateCheckResult;
import com.phomoria.update.UpdateConfig;
import com.phomoria.update.UpdateDownloader;
import com.phomoria.update.UpdateInfo;
import com.phomoria.update.UpdateInstaller;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

public final class UpdateScreen extends JPanel {

    private final JLabel statusLabel =
            new JLabel("Memeriksa update...");

    private final JLabel versionLabel =
            new JLabel();

    private final JTextArea notes =
            new JTextArea();

    private final JButton actionButton =
            new JButton("DOWNLOAD UPDATE");

    private final JButton continueButton =
            new JButton("LANJUTKAN");

    private UpdateInfo updateInfo;
    private Path downloadedPackage;

    public UpdateScreen() {
        setLayout(new BorderLayout(20, 20));
        setBorder(
                BorderFactory.createEmptyBorder(
                        50, 80, 50, 80
                )
        );

        JLabel title =
                new JLabel("PHOMORIA UPDATE");

        title.setHorizontalAlignment(
                SwingConstants.CENTER
        );
        title.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        30
                )
        );

        statusLabel.setHorizontalAlignment(
                SwingConstants.CENTER
        );
        statusLabel.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        18
                )
        );

        versionLabel.setHorizontalAlignment(
                SwingConstants.CENTER
        );

        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setOpaque(false);
        notes.setFont(
                new Font(
                        "SansSerif",
                        Font.PLAIN,
                        15
                )
        );

        actionButton.setEnabled(false);
        continueButton.setVisible(false);

        JPanel center =
                new JPanel(
                        new BorderLayout(10, 15)
                );

        center.add(
                statusLabel,
                BorderLayout.NORTH
        );
        center.add(
                versionLabel,
                BorderLayout.CENTER
        );
        center.add(
                new JScrollPane(notes),
                BorderLayout.SOUTH
        );

        JPanel bottom =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.CENTER,
                                15,
                                10
                        )
                );

        bottom.add(actionButton);
        bottom.add(continueButton);

        add(title, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        actionButton.addActionListener(
                e -> downloadUpdate()
        );
    }

    public void showChecking() {
        statusLabel.setText(
                "Memeriksa update..."
        );
        versionLabel.setText("");
        notes.setText("");

        actionButton.setVisible(true);
        actionButton.setEnabled(false);

        continueButton.setVisible(false);
    }

    public void showUpToDate(
            Runnable continueAction) {

        statusLabel.setText(
                "Aplikasi sudah versi terbaru."
        );

        versionLabel.setText(
                "Versi "
                        + com.phomoria.update.AppVersion.current()
        );

        actionButton.setVisible(false);

        configureContinueButton(
                "MULAI APLIKASI",
                continueAction
        );
    }

    public void showOptional(
            UpdateInfo info,
            Runnable continueAction) {

        updateInfo = info;

        statusLabel.setText(
                "Update tersedia."
        );

        versionLabel.setText(
                "Versi sekarang: "
                        + com.phomoria.update.AppVersion.current()
                        + "  →  Versi baru: "
                        + info.getVersion()
        );

        notes.setText(
                info.getReleaseNotes() == null
                        ? ""
                        : info.getReleaseNotes()
        );

        actionButton.setVisible(true);
        actionButton.setText(
                "DOWNLOAD UPDATE"
        );
        actionButton.setEnabled(true);

        configureContinueButton(
                "LEWATI",
                continueAction
        );
    }

    public void showRequired(UpdateInfo info) {

        updateInfo = info;

        statusLabel.setText(
                "UPDATE WAJIB"
        );

        versionLabel.setText(
                "Versi "
                        + info.getVersion()
                        + " diperlukan sebelum aplikasi "
                        + "dapat digunakan."
        );

        notes.setText(
                info.getReleaseNotes() == null
                        ? ""
                        : info.getReleaseNotes()
        );

        actionButton.setVisible(true);
        actionButton.setText(
                "DOWNLOAD UPDATE"
        );
        actionButton.setEnabled(true);

        continueButton.setVisible(false);
    }

    private void configureContinueButton(
            String text,
            Runnable action) {

        for (var listener :
                continueButton.getActionListeners()) {
            continueButton.removeActionListener(listener);
        }

        continueButton.setText(text);
        continueButton.setVisible(true);
        continueButton.addActionListener(
                e -> action.run()
        );
    }

    private void downloadUpdate() {

        if (updateInfo == null) {
            return;
        }

        actionButton.setEnabled(false);

        statusLabel.setText(
                "Mengunduh update..."
        );

        DebugLog.info(
                "UpdateScreen: download requested for "
                        + updateInfo.getVersion()
        );

        SwingWorker<Path, Void> worker =
                new SwingWorker<>() {

            @Override
            protected Path doInBackground()
                    throws Exception {

                return new UpdateDownloader()
                        .download(updateInfo);
            }

            @Override
            protected void done() {

                try {
                    downloadedPackage = get();

                    if (UpdateConfig.SIMULATION_MODE) {

                        UpdateInstaller.prepareSimulation(
                                downloadedPackage,
                                updateInfo
                        );

                        showSimulationReady();

                    } else {

                        if (!UpdateInstaller
                                .isPackagedApplication()) {

                            throw new IllegalStateException(
                                    "Production updater "
                                            + "requires packaged application."
                            );
                        }

                        statusLabel.setText(
                                "Menyiapkan updater..."
                        );

                        UpdateInstaller
                                .launchProductionUpdater(
                                        downloadedPackage
                                );

                        statusLabel.setText(
                                "Aplikasi sedang diperbarui..."
                        );

                        actionButton.setVisible(false);
                        continueButton.setVisible(false);

                        SwingUtilities
                                .getWindowAncestor(
                                        UpdateScreen.this
                                )
                                .dispose();

                        System.exit(0);
                    }

                } catch (Exception ex) {

                    DebugLog.error(
                            "Update installation failed.",
                            ex
                    );

                    statusLabel.setText(
                            "Update gagal."
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

    private void showSimulationReady() {

        statusLabel.setText(
                "Paket update siap dipasang."
        );

        versionLabel.setText(
                "SIMULASI — file aplikasi belum diganti."
        );

        actionButton.setVisible(false);

        configureContinueButton(
                "MASUK KE APLIKASI",
                () -> {
                    Window window =
                            SwingUtilities
                                    .getWindowAncestor(this);

                    if (window instanceof
                            com.phomoria.app.ApplicationFrame frame) {

                        frame.finishStartupAfterUpdate();
                    }
                }
        );
    }
}
