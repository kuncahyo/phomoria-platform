package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.app.ApplicationFrame;
import com.phomoria.camera.CameraDevice;
import com.phomoria.camera.CameraManager;
import com.phomoria.config.AppSettings;
import com.phomoria.cloud.CloudConfig;
import com.phomoria.cloud.CloudConfigManager;
import com.phomoria.cloud.RememberedCredentialsStore;
import com.phomoria.debug.DebugLog;
import com.phomoria.print.PrinterService;
import com.phomoria.theme.ThemeEditorPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class SettingsScreen extends JPanel {

    private final ApplicationFrame frame;
    private final Runnable back;

    private final JComboBox<String> cameraDisplay =
            new JComboBox<>();

    private final JButton chooseCamera =
            new JButton("PILIH KAMERA");

    private final JButton refreshCameras =
            new JButton("REFRESH");

    private final JLabel cameraStatus =
            new JLabel(" ");

    private final Timer cameraStatusTimer =
            new Timer(
                    1000,
                    e -> refreshCameraStatus()
            );

    private final JTextField server;
    private final JSpinner captureCountdown;
    private final JSpinner photoPreview;
    private final JSpinner finalReview;
    private final JSpinner qrisTimer;
    private final JSpinner frameSelectionTimer;
    private final JSpinner mainSessionTimer;
    private final JSpinner mainExtensionTimer;
    private final JSpinner effectTimer;
    private final JSpinner resultTimer;
    private final JCheckBox remember;
    private final JCheckBox showCancelSession;
    private final JCheckBox livePhotoEnabled;
    private final JSpinner livePhotoDuration;

    private final MirrorPhotoSettingsPanel mirrorPhotoSettingsPanel;
    private final PhotoEffectSettingsPanel effectSettingsPanel;
    private final PrintPaperSettingsPanel printPaperSettingsPanel;
    private final PrinterSettingsPanel printerSettingsPanel;

    public SettingsScreen(
            ApplicationFrame frame,
            Runnable back
    ) {
        this.frame = frame;
        this.back = back;

        AppSettings settings =
                AppContext.settings();

        setLayout(new CardLayout());
        JPanel settingsView = new JPanel(new BorderLayout(15, 15));

        setBorder(
                BorderFactory.createEmptyBorder(
                        25,
                        35,
                        25,
                        35
                )
        );

        JLabel title =
                new JLabel(
                        "PHOMORIA SETTINGS"
                );

        title.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        26
                )
        );

        settingsView.add(
                title,
                BorderLayout.NORTH
        );

        server =
                new JTextField(
                        settings.getApiServer()
                );

        server.setPreferredSize(new Dimension(520, 32));

        captureCountdown = new JSpinner(
                new SpinnerNumberModel(
                        settings.getCaptureCountdownSeconds(), 1, 30, 1
                )
        );

        photoPreview = new JSpinner(
                new SpinnerNumberModel(
                        settings.getPhotoPreviewSeconds(), 1, 60, 1
                )
        );

        finalReview = new JSpinner(
                new SpinnerNumberModel(
                        settings.getFinalReviewSeconds(), 1, 120, 1
                )
        );

        qrisTimer = new JSpinner(new SpinnerNumberModel(settings.getQrisTimerSeconds(), 5, 3600, 5));
        frameSelectionTimer = new JSpinner(new SpinnerNumberModel(settings.getFrameSelectionTimerSeconds(), 5, 3600, 5));
        mainSessionTimer = new JSpinner(new SpinnerNumberModel(settings.getMainSessionTimerSeconds(), 5, 3600, 5));
        mainExtensionTimer = new JSpinner(new SpinnerNumberModel(settings.getMainExtensionTimerSeconds(), 5, 3600, 5));
        effectTimer = new JSpinner(new SpinnerNumberModel(settings.getEffectTimerSeconds(), 5, 3600, 5));
        resultTimer = new JSpinner(new SpinnerNumberModel(settings.getResultTimerSeconds(), 5, 3600, 5));

        remember =
                new JCheckBox(
                        "Remember login",
                        settings.isRememberLogin()
                );

        showCancelSession =
                new JCheckBox(
                        "Tampilkan tombol PILIH ULANG FRAME",
                        settings.isShowCancelSession()
                );

        livePhotoEnabled = new JCheckBox(
                "Aktifkan Live Photo (webcam)", settings.isLivePhotoEnabled());
        livePhotoDuration = new JSpinner(new SpinnerNumberModel(
                settings.getLivePhotoDurationSeconds(), 1, 10, 1));

        mirrorPhotoSettingsPanel =
                new MirrorPhotoSettingsPanel(
                        settings
                );

        effectSettingsPanel =
                new PhotoEffectSettingsPanel(
                        settings.getPhotoEffectSettings()
                );

        printPaperSettingsPanel =
                new PrintPaperSettingsPanel(
                        settings
                );

        printerSettingsPanel = new PrinterSettingsPanel(settings);
        setupCameraChooser(settings);

        cameraStatusTimer.setRepeats(true);
        cameraStatusTimer.start();

        JPanel basicForm =
                createBasicForm();

        JPanel settingsPanels = new JPanel();
        settingsPanels.setLayout(new BoxLayout(settingsPanels, BoxLayout.Y_AXIS));
        addCompactPanel(settingsPanels, mirrorPhotoSettingsPanel);
        addCompactPanel(settingsPanels, effectSettingsPanel);
        addCompactPanel(settingsPanels, printPaperSettingsPanel);
        addCompactPanel(settingsPanels, printerSettingsPanel);

        JPanel content =
                new JPanel(
                        new BorderLayout(
                                12,
                                12
                        )
                );

        content.add(
                basicForm,
                BorderLayout.NORTH
        );

        content.add(
                settingsPanels,
                BorderLayout.CENTER
        );

        JScrollPane scroll =
                new JScrollPane(
                        content
                );

        scroll.setBorder(
                BorderFactory.createEmptyBorder()
        );

        scroll.getVerticalScrollBar().setUnitIncrement(24);
        installTouchDragScrolling(content, scroll);

        settingsView.add(
                scroll,
                BorderLayout.CENTER
        );

        JPanel bottom =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT
                        )
                );

        JButton save =
                new JButton(
                        "SAVE SETTINGS"
                );
        JButton themeEditorButton = new JButton("THEME EDITOR");

        JButton testPrint =
                new JButton(
                        "TEST PRINT"
                );

        JButton cancel =
                new JButton(
                        "BACK"
                );

        bottom.add(cancel);
        bottom.add(themeEditorButton);
        bottom.add(testPrint);
        bottom.add(save);

        settingsView.add(bottom, BorderLayout.SOUTH);
        add(settingsView, "SETTINGS");
        ThemeEditorPanel themeEditorPanel = new ThemeEditorPanel(() -> {
            ((CardLayout) getLayout()).show(this, "SETTINGS");
            if (!cameraStatusTimer.isRunning()) cameraStatusTimer.start();
        });
        add(themeEditorPanel, "THEME_EDITOR");
        ((CardLayout) getLayout()).show(this, "SETTINGS");
        themeEditorButton.addActionListener(e -> {
            cameraStatusTimer.stop();
            ((CardLayout) getLayout()).show(this, "THEME_EDITOR");
        });

        cancel.addActionListener(
                e -> back.run()
        );

        testPrint.addActionListener(
                e -> testPrint(settings)
        );

        save.addActionListener(
                e -> saveSettings(settings)
        );

        DebugLog.info(
                "SettingsScreen initialized."
        );
    }

    private void refreshCameraStatus() {

        String selected =
                (String) cameraDisplay.getSelectedItem();

        if (selected == null
                || selected.isBlank()) {

            updateCameraStatus(null);
            return;
        }

        boolean available =
                CameraManager.isAvailable(
                        selected
                );

        if (available) {

            cameraStatus.setText(
                    "● Kamera tersedia"
            );

            cameraStatus.setForeground(
                    new Color(40, 140, 70)
            );

        } else {

            cameraStatus.setText(
                    "● Kamera tidak terdeteksi / USB terputus"
            );

            cameraStatus.setForeground(
                    new Color(180, 50, 50)
            );
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();

        if (!cameraStatusTimer.isRunning()) {
            cameraStatusTimer.start();
        }

        DebugLog.info(
                "SettingsScreen camera status monitor started."
        );
    }

    @Override
    public void removeNotify() {

        cameraStatusTimer.stop();

        DebugLog.info(
                "SettingsScreen camera status monitor stopped."
        );

        super.removeNotify();
    }

    private JPanel createBasicForm() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createTitledBorder("Pengaturan Dasar"));

        JPanel connection = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;

        addRow(connection, g, 0, "API Server", server);
        addRow(connection, g, 1, "Camera", createCameraChooser());
        connection.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(connection);

        JPanel primaryTimers = new JPanel(new GridLayout(0, 3, 12, 8));
        primaryTimers.setBorder(BorderFactory.createTitledBorder("Timer Alur Foto"));
        primaryTimers.add(timerField("Countdown sebelum foto (detik)", captureCountdown));
        primaryTimers.add(timerField("Preview setiap foto (detik)", photoPreview));
        primaryTimers.add(timerField("Review akhir / retake (detik)", finalReview));
        primaryTimers.add(timerField("Pilih frame (detik)", frameSelectionTimer));
        primaryTimers.add(timerField("Efek foto (detik)", effectTimer));
        primaryTimers.add(timerField("Preview final (detik)", resultTimer));
        primaryTimers.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(Box.createVerticalStrut(8));
        form.add(primaryTimers);

        JPanel additional = new JPanel(new GridLayout(0, 3, 12, 8));
        additional.setBorder(BorderFactory.createTitledBorder("Timer Pembayaran & Sesi"));
        additional.add(timerField("QRIS (detik)", qrisTimer));
        additional.add(timerField("Sesi foto utama (detik)", mainSessionTimer));
        additional.add(timerField("Tambahan sesi foto (detik)", mainExtensionTimer));
        additional.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(Box.createVerticalStrut(8));
        form.add(additional);

        JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        options.setBorder(BorderFactory.createTitledBorder("Fitur Aplikasi"));
        remember.setText("Remember Login (simpan email dan password secara aman)");
        remember.setAlignmentX(Component.LEFT_ALIGNMENT);
        showCancelSession.setAlignmentX(Component.LEFT_ALIGNMENT);
        livePhotoEnabled.setAlignmentX(Component.LEFT_ALIGNMENT);
        options.add(remember);
        options.add(Box.createVerticalStrut(5));
        options.add(showCancelSession);
        options.add(Box.createVerticalStrut(5));
        options.add(livePhotoEnabled);

        JPanel livePhotoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        livePhotoRow.add(new JLabel("Durasi Live Photo sebelum shutter (detik):"));
        livePhotoRow.add(livePhotoDuration);
        livePhotoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        options.add(livePhotoRow);
        options.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(Box.createVerticalStrut(8));
        form.add(options);

        return form;
    }

    private JPanel timerField(String label, JSpinner spinner) {
        JPanel field = new JPanel();
        field.setLayout(new BoxLayout(field, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(label);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        spinner.setPreferredSize(new Dimension(150, 32));
        spinner.setMaximumSize(new Dimension(150, 32));
        spinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.add(title);
        field.add(Box.createVerticalStrut(4));
        field.add(spinner);
        field.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        return field;
    }

    private void addCompactPanel(JPanel parent, JComponent panel) {
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension preferred = panel.getPreferredSize();
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        parent.add(Box.createVerticalStrut(8));
        parent.add(panel);
    }

    private void installTouchDragScrolling(Component component, JScrollPane scroll) {
        if (component instanceof JPanel || component instanceof JLabel) {
            final int[] startY = {-1};
            final int[] startValue = {0};
            java.awt.event.MouseAdapter drag = new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    startY[0] = e.getYOnScreen();
                    startValue[0] = scroll.getVerticalScrollBar().getValue();
                }

                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    if (startY[0] >= 0) {
                        int delta = startY[0] - e.getYOnScreen();
                        scroll.getVerticalScrollBar().setValue(startValue[0] + delta);
                    }
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    startY[0] = -1;
                }
            };
            component.addMouseListener(drag);
            component.addMouseMotionListener(drag);
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                installTouchDragScrolling(child, scroll);
            }
        }
    }

    private JPanel createCameraChooser() {

        JPanel panel =
                new JPanel(
                        new BorderLayout(
                                6,
                                5
                        )
                );

        JPanel buttons =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                5,
                                0
                        )
                );

        buttons.add(
                chooseCamera
        );

        buttons.add(
                refreshCameras
        );

        JButton liveCam = new JButton("LIVECAM");
        liveCam.addActionListener(e -> openLiveCam());
        buttons.add(liveCam);

        JPanel statusPanel =
                new JPanel(
                        new BorderLayout()
                );

        statusPanel.add(
                cameraStatus,
                BorderLayout.WEST
        );

        JPanel right =
                new JPanel(
                        new BorderLayout()
                );

        right.add(
                cameraDisplay,
                BorderLayout.CENTER
        );

        right.add(
                buttons,
                BorderLayout.SOUTH
        );

        panel.add(
                right,
                BorderLayout.CENTER
        );

        panel.add(
                statusPanel,
                BorderLayout.SOUTH
        );

        return panel;
    }

    private void openLiveCam() {
        String selected = (String) cameraDisplay.getSelectedItem();
        if (selected == null || selected.isBlank()) {
            selected = AppContext.settings().getCameraName();
        }
        if (selected == null || selected.isBlank()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Pilih kamera terlebih dahulu pada pengaturan kamera.",
                    "LIVECAM",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        cameraStatusTimer.stop();
        LiveCamDialog dialog = new LiveCamDialog(frame, selected, () -> {
            if (isDisplayable() && !cameraStatusTimer.isRunning()) {
                cameraStatusTimer.start();
            }
            refreshCameraStatus();
        });
        dialog.setVisible(true);
    }

    private void setupCameraChooser(
            AppSettings settings
    ) {

        cameraDisplay.setEditable(false);

        refreshCameras.addActionListener(
                e -> reloadCameraList()
        );

        chooseCamera.addActionListener(
                e -> showCameraSelectionDialog()
        );

        reloadCameraList();

        String configured =
                settings.getCameraName();

        if (configured != null
                && !configured.isBlank()) {

            cameraDisplay.setSelectedItem(
                    configured
            );

            updateCameraStatus(
                    configured
            );
        }
    }

    /**
     * Loads both normal UVC webcams and cameras detected by gPhoto2.
     *
     * The display name is intentionally stored directly in AppSettings so
     * existing settings files remain compatible.
     */
    private void reloadCameraList() {

        String configured =
                AppContext.settings()
                        .getCameraName();

        List<String> names =
                new ArrayList<>();

        try {

            List<CameraDevice> devices =
                    CameraManager.listDevices();

            for (CameraDevice device : devices) {

                if (device == null) {
                    continue;
                }

                String name =
                        device.displayName();

                if (name != null
                        && !name.isBlank()
                        && !names.contains(name)) {

                    names.add(name);
                }
            }

            cameraDisplay.removeAllItems();

            for (String name : names) {
                cameraDisplay.addItem(name);
            }

            if (configured != null
                    && !configured.isBlank()) {

                boolean exists =
                        names.contains(
                                configured
                        );

                if (!exists) {
                    cameraDisplay.addItem(
                            configured
                    );
                }

                cameraDisplay.setSelectedItem(
                        configured
                );

                updateCameraStatus(
                        configured
                );

            } else if (!names.isEmpty()) {

                cameraDisplay.setSelectedIndex(
                        0
                );

                updateCameraStatus(
                        names.get(0)
                );

            } else {

                cameraStatus.setText(
                        "● Tidak ada kamera terdeteksi"
                );

                cameraStatus.setForeground(
                        new Color(180, 50, 50)
                );
            }

            DebugLog.info(
                    "Settings camera device list refreshed. "
                            + "count="
                            + names.size()
            );

        } catch (Exception ex) {

            cameraDisplay.removeAllItems();

            if (configured != null
                    && !configured.isBlank()) {

                cameraDisplay.addItem(
                        configured
                );

                cameraDisplay.setSelectedItem(
                        configured
                );
            }

            cameraStatus.setText(
                    "● Gagal membaca kamera"
            );

            cameraStatus.setForeground(
                    new Color(180, 50, 50)
            );

            DebugLog.error(
                    "Settings camera device list failed.",
                    ex
            );

            JOptionPane.showMessageDialog(
                    this,
                    "Gagal membaca kamera:\n"
                            + ex.getMessage(),
                    "Camera",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void showCameraSelectionDialog() {

        List<CameraDevice> devices;

        try {

            devices =
                    CameraManager.listDevices();

        } catch (Exception ex) {

            DebugLog.error(
                    "Could not scan camera devices for selection dialog.",
                    ex
            );

            JOptionPane.showMessageDialog(
                    this,
                    "Gagal membaca kamera:\n"
                            + ex.getMessage(),
                    "Camera",
                    JOptionPane.ERROR_MESSAGE
            );

            return;
        }

        if (devices.isEmpty()) {

            JOptionPane.showMessageDialog(
                    this,
                    "Tidak ada kamera yang terdeteksi.",
                    "Pilih Kamera",
                    JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        List<String> deviceNames =
                new ArrayList<>();

        for (CameraDevice device : devices) {

            if (device == null) {
                continue;
            }

            String name =
                    device.displayName();

            if (name != null
                    && !name.isBlank()
                    && !deviceNames.contains(name)) {

                deviceNames.add(name);
            }
        }

        if (deviceNames.isEmpty()) {

            JOptionPane.showMessageDialog(
                    this,
                    "Tidak ada nama kamera yang valid.",
                    "Pilih Kamera",
                    JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        String[] names =
                deviceNames.toArray(
                        String[]::new
                );

        String currentName =
                (String) cameraDisplay
                        .getSelectedItem();

        int initial =
                0;

        if (currentName != null) {

            for (int i = 0;
                 i < names.length;
                 i++) {

                if (currentName.equals(
                        names[i]
                )) {

                    initial = i;
                    break;
                }
            }
        }

        JList<String> cameraList =
                new JList<>(
                        names
                );

        cameraList.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );

        cameraList.setSelectedIndex(
                initial
        );

        cameraList.setVisibleRowCount(
                Math.min(
                        8,
                        names.length
                )
        );

        JScrollPane listScroll =
                new JScrollPane(
                        cameraList
                );

        listScroll.setPreferredSize(
                new Dimension(
                        420,
                        Math.min(
                                300,
                                Math.max(
                                        120,
                                        names.length * 30 + 20
                                )
                        )
                )
        );

        JPanel message =
                new JPanel(
                        new BorderLayout(
                                5,
                                5
                        )
                );

        message.add(
                new JLabel(
                        "Pilih kamera yang akan digunakan:"
                ),
                BorderLayout.NORTH
        );

        JLabel hint =
                new JLabel(
                        "<html>"
                                + "Webcam/UVC dan kamera DSLR yang "
                                + "terdeteksi melalui gPhoto2 akan muncul "
                                + "di daftar ini."
                                + "</html>"
                );

        hint.setForeground(
                new Color(90, 90, 90)
        );

        message.add(
                hint,
                BorderLayout.SOUTH
        );

        JPanel dialogPanel =
                new JPanel(
                        new BorderLayout(
                                10,
                                10
                        )
                );

        dialogPanel.add(
                message,
                BorderLayout.NORTH
        );

        dialogPanel.add(
                listScroll,
                BorderLayout.CENTER
        );

        int result =
                JOptionPane.showConfirmDialog(
                        this,
                        dialogPanel,
                        "Pilih Kamera",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                );

        if (result
                != JOptionPane.OK_OPTION) {

            return;
        }

        String selected =
                cameraList.getSelectedValue();

        if (selected == null
                || selected.isBlank()) {

            return;
        }

        cameraDisplay.setSelectedItem(
                selected
        );

        updateCameraStatus(
                selected
        );

        DebugLog.info(
                "Camera selected in Settings: "
                        + selected
        );
    }

    private void updateCameraStatus(
            String cameraName
    ) {

        if (cameraName == null
                || cameraName.isBlank()) {

            cameraStatus.setText(
                    "● Belum memilih kamera"
            );

            cameraStatus.setForeground(
                    new Color(180, 120, 30)
            );

            return;
        }

        boolean available =
                CameraManager.isAvailable(
                        cameraName
                );

        if (available) {

            cameraStatus.setText(
                    "● Kamera tersedia"
            );

            cameraStatus.setForeground(
                    new Color(40, 140, 70)
            );

        } else {

            cameraStatus.setText(
                    "● Kamera tidak sedang terdeteksi"
            );

            cameraStatus.setForeground(
                    new Color(180, 50, 50)
            );
        }
    }

    private void saveSettings(
            AppSettings settings
    ) {

        settings.setApiServer(
                server.getText()
        );

        settings.setCameraName(
                (String)
                        cameraDisplay
                                .getSelectedItem()
        );

settings.setPrice(
                (Integer)
                        price.getValue()
        );

        settings.setCaptureCountdownSeconds(
                (Integer) captureCountdown.getValue()
        );

        settings.setPhotoPreviewSeconds(
                (Integer) photoPreview.getValue()
        );

        settings.setFinalReviewSeconds(
                (Integer) finalReview.getValue()
        );

        settings.setQrisTimerSeconds((Integer) qrisTimer.getValue());
        settings.setFrameSelectionTimerSeconds((Integer) frameSelectionTimer.getValue());
        settings.setMainSessionTimerSeconds((Integer) mainSessionTimer.getValue());
        settings.setMainExtensionTimerSeconds((Integer) mainExtensionTimer.getValue());
        settings.setEffectTimerSeconds((Integer) effectTimer.getValue());
        settings.setResultTimerSeconds((Integer) resultTimer.getValue());

        settings.setRememberLogin(remember.isSelected());
        CloudConfig cloudConfig = CloudConfigManager.getConfig();
        cloudConfig.setRememberLogin(remember.isSelected());
        CloudConfigManager.save();
        if (!remember.isSelected()) {
            try {
                RememberedCredentialsStore.clear();
            } catch (java.io.IOException ex) {
                DebugLog.error("Could not clear remembered login credentials.", ex);
                JOptionPane.showMessageDialog(
                        this,
                        "Remember Login berhasil dinonaktifkan, tetapi kredensial tersimpan gagal dihapus.\n"
                                + ex.getMessage(),
                        "Remember Login",
                        JOptionPane.WARNING_MESSAGE
                );
            }
        }

        settings.setShowCancelSession(
                showCancelSession.isSelected()
        );

        settings.setLivePhotoEnabled(livePhotoEnabled.isSelected());
        settings.setLivePhotoDurationSeconds((Integer) livePhotoDuration.getValue());

        settings.setPhotoEffectSettings(
                effectSettingsPanel.getSettings()
        );

        mirrorPhotoSettingsPanel.applyTo(
                settings
        );

        printPaperSettingsPanel.applyTo(
                settings
        );
        printerSettingsPanel.applyTo(settings);

        AppContext.saveSettings();

        DebugLog.info(
                "All Settings saved. camera="
                        + settings.getCameraName()
        );

        JOptionPane.showMessageDialog(
                this,
                "Settings tersimpan.",
                "Phomoria",
                JOptionPane.INFORMATION_MESSAGE
        );

        back.run();
    }

    private void testPrint(
            AppSettings settings
    ) {

        // Apply current UI values before testing.
        settings.setApiServer(
                server.getText()
        );

        settings.setCameraName(
                (String)
                        cameraDisplay
                                .getSelectedItem()
        );

settings.setPrice(
                (Integer)
                        price.getValue()
        );

        settings.setRememberLogin(
                remember.isSelected()
        );

        settings.setPhotoEffectSettings(
                effectSettingsPanel.getSettings()
        );

        mirrorPhotoSettingsPanel.applyTo(
                settings
        );

        printPaperSettingsPanel.applyTo(
                settings
        );
        printerSettingsPanel.applyTo(settings);

        try {

            new PrinterService()
                    .testPrint(
                            settings
                    );

            JOptionPane.showMessageDialog(
                    this,
                    "Perintah test print dikirim.",
                    "Printer",
                    JOptionPane.INFORMATION_MESSAGE
            );

        } catch (Exception ex) {

            DebugLog.error(
                    "Test print failed.",
                    ex
            );

            JOptionPane.showMessageDialog(
                    this,
                    "Test print gagal:\n"
                            + ex.getMessage(),
                    "Printer",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void addRow(
            JPanel panel,
            GridBagConstraints g,
            int row,
            String label,
            Component component
    ) {
        JPanel field = new JPanel();
        field.setLayout(new BoxLayout(field, BoxLayout.Y_AXIS));
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (component instanceof JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension preferred = component.getPreferredSize();
        int height = Math.max(28, preferred.height);
        int width = component instanceof JTextField ? 520
                : component instanceof JSpinner ? Math.max(150, preferred.width)
                : 650;
        component.setPreferredSize(new Dimension(width, height));
        component.setMaximumSize(new Dimension(width, height));
        field.add(fieldLabel);
        field.add(Box.createVerticalStrut(5));
        field.add(component);
        field.setMaximumSize(new Dimension(680, height + 28));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);

        g.gridx = 0;
        g.gridy = row;
        g.gridwidth = 2;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, g);
        g.gridwidth = 1;
    }
}
