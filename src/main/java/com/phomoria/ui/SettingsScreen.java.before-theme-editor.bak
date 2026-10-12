package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.app.ApplicationFrame;
import com.phomoria.camera.CameraDevice;
import com.phomoria.camera.CameraManager;
import com.phomoria.config.AppSettings;
import com.phomoria.debug.DebugLog;
import com.phomoria.print.PrinterService;

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
    private final JTextField frameName;
    private final JSpinner slots;
    private final JSpinner price;
    private final JCheckBox remember;
    private final JCheckBox showCancelSession;

    private final MirrorPhotoSettingsPanel mirrorPhotoSettingsPanel;
    private final PhotoEffectSettingsPanel effectSettingsPanel;
    private final PrintPaperSettingsPanel printPaperSettingsPanel;

    public SettingsScreen(
            ApplicationFrame frame,
            Runnable back
    ) {
        this.frame = frame;
        this.back = back;

        AppSettings settings =
                AppContext.settings();

        setLayout(
                new BorderLayout(15, 15)
        );

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

        add(
                title,
                BorderLayout.NORTH
        );

        server =
                new JTextField(
                        settings.getApiServer()
                );

        frameName =
                new JTextField(
                        settings.getFrameName()
                );

        slots =
                new JSpinner(
                        new SpinnerNumberModel(
                                settings.getPhotoSlotCount(),
                                1,
                                12,
                                1
                        )
                );

        price =
                new JSpinner(
                        new SpinnerNumberModel(
                                settings.getPrice(),
                                0,
                                10000000,
                                5000
                        )
                );

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

        setupCameraChooser(settings);

        cameraStatusTimer.setRepeats(true);
        cameraStatusTimer.start();

        JPanel basicForm =
                createBasicForm();

        JPanel settingsPanels =
                new JPanel(
                        new GridLayout(
                                3,
                                1,
                                10,
                                10
                        )
                );

        settingsPanels.add(
                mirrorPhotoSettingsPanel
        );

        settingsPanels.add(
                effectSettingsPanel
        );

        settingsPanels.add(
                printPaperSettingsPanel
        );

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

        scroll.getVerticalScrollBar()
                .setUnitIncrement(16);

        add(
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

        JButton testPrint =
                new JButton(
                        "TEST PRINT"
                );

        JButton cancel =
                new JButton(
                        "BACK"
                );

        bottom.add(cancel);
        bottom.add(testPrint);
        bottom.add(save);

        add(
                bottom,
                BorderLayout.SOUTH
        );

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

        JPanel form =
                new JPanel(
                        new GridBagLayout()
                );

        form.setBorder(
                BorderFactory.createTitledBorder(
                        "Pengaturan Dasar"
                )
        );

        GridBagConstraints g =
                new GridBagConstraints();

        g.insets =
                new Insets(
                        7,
                        7,
                        7,
                        7
                );

        g.fill =
                GridBagConstraints.HORIZONTAL;

        g.weightx = 1;

        int row = 0;

        addRow(
                form,
                g,
                row++,
                "API Server",
                server
        );

        addRow(
                form,
                g,
                row++,
                "Camera",
                createCameraChooser()
        );

        addRow(
                form,
                g,
                row++,
                "Frame Name",
                frameName
        );

        addRow(
                form,
                g,
                row++,
                "Jumlah Slot Foto",
                slots
        );

        addRow(
                form,
                g,
                row++,
                "Harga Sesi",
                price
        );

        g.gridx = 1;
        g.gridy = row++;
        g.weightx = 1;

        form.add(
                remember,
                g
        );

        g.gridy = row;

        form.add(
                showCancelSession,
                g
        );

        return form;
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

        settings.setFrameName(
                frameName.getText()
        );

        settings.setPhotoSlotCount(
                (Integer)
                        slots.getValue()
        );

        settings.setPrice(
                (Integer)
                        price.getValue()
        );

        settings.setRememberLogin(
                remember.isSelected()
        );

        settings.setShowCancelSession(
                showCancelSession.isSelected()
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

        settings.setFrameName(
                frameName.getText()
        );

        settings.setPhotoSlotCount(
                (Integer)
                        slots.getValue()
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

        g.gridx = 0;
        g.gridy = row;
        g.weightx = 0;

        panel.add(
                new JLabel(label),
                g
        );

        g.gridx = 1;
        g.weightx = 1;

        panel.add(
                component,
                g
        );
    }
}
