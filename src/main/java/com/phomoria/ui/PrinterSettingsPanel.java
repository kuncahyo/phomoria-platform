package com.phomoria.ui;

import com.phomoria.config.AppSettings;
import com.phomoria.print.PrinterService;
import com.phomoria.print.windows.PrinterProfileId;
import com.phomoria.print.windows.PrinterProfileStore;
import com.phomoria.print.windows.WindowsPrinterProfileEditor;

import javax.print.PrintService;
import javax.swing.*;
import java.awt.*;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PrinterSettingsPanel extends JPanel {
    private final JComboBox<PrinterChoice> printer = new JComboBox<>();
    private final JCheckBox autoPrint;
    private final JCheckBox viewPrintButton;
    private final JLabel status = new JLabel(" ");
    private final JLabel profile1Status = new JLabel(" ");
    private final JLabel profile2Status = new JLabel(" ");

    private final PrinterProfileStore profileStore = new PrinterProfileStore();

    public PrinterSettingsPanel(AppSettings settings) {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("Printer"));

        autoPrint = new JCheckBox("Auto Print", settings.isAutoPrint());
        viewPrintButton = new JCheckBox("View Print Button", settings.isViewPrintButton());

        JPanel printerRow = new JPanel(new BorderLayout(8, 0));
        printerRow.add(printer, BorderLayout.CENTER);
        JButton refresh = new JButton("REFRESH");
        printerRow.add(refresh, BorderLayout.EAST);

        JPanel profile1Row = createProfileRow(
                "Profile 1", PrinterProfileId.PROFILE_1, profile1Status);
        JPanel profile2Row = createProfileRow(
                "Profile 2", PrinterProfileId.PROFILE_2, profile2Status);

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 6, 5, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        add(new JLabel("Pilih Printer"), g);
        g.gridx = 1; g.weightx = 1;
        add(printerRow, g);

        g.gridx = 0; g.gridy = 1; g.gridwidth = 2;
        add(profile1Row, g);
        g.gridy = 2;
        add(profile2Row, g);
        g.gridy = 3;
        add(autoPrint, g);
        g.gridy = 4;
        add(viewPrintButton, g);
        g.gridy = 5;
        status.setForeground(new Color(90, 90, 90));
        add(status, g);

        refresh.addActionListener(e -> {
            reload(getSelectedPrinterName());
            refreshProfileStatuses();
        });

        printer.addActionListener(e -> refreshProfileStatuses());

        reload(settings.getPrinterName());
        refreshProfileStatuses();
    }

    private JPanel createProfileRow(
            String title,
            PrinterProfileId profileId,
            JLabel profileStatus) {

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createTitledBorder(title));

        profileStatus.setForeground(new Color(90, 90, 90));
        row.add(profileStatus, BorderLayout.CENTER);

        JButton configure = new JButton("ATUR " + title.toUpperCase());
        row.add(configure, BorderLayout.EAST);
        configure.addActionListener(e -> configureProfile(profileId, title));
        return row;
    }

    private void reload(String selectedName) {
        List<PrinterChoice> choices = new ArrayList<>();
        choices.add(new PrinterChoice("", "Default Windows Printer"));

        PrintService[] services = PrinterService.listPrinters();
        for (PrintService service : services) {
            choices.add(new PrinterChoice(service.getName(), service.getName()));
        }

        printer.removeAllItems();
        for (PrinterChoice choice : choices) {
            printer.addItem(choice);
        }

        String wanted = selectedName == null ? "" : selectedName;
        boolean found = false;
        for (int i = 0; i < printer.getItemCount(); i++) {
            if (printer.getItemAt(i).name.equals(wanted)) {
                printer.setSelectedIndex(i);
                found = true;
                break;
            }
        }

        if (!found && !wanted.isBlank()) {
            printer.addItem(new PrinterChoice(
                    wanted, wanted + " (tersimpan, tidak terdeteksi)"));
            printer.setSelectedIndex(printer.getItemCount() - 1);
        }

        status.setText(services.length + " printer terdaftar terdeteksi.");
    }

    private String getSelectedPrinterName() {
        PrinterChoice selected = (PrinterChoice) printer.getSelectedItem();
        return selected == null ? "" : selected.name;
    }

    private String resolveSelectedPrinterName() {
        String selectedName = getSelectedPrinterName();
        if (!selectedName.isBlank()) {
            return selectedName;
        }

        PrintService defaultService = PrinterJob.getPrinterJob().getPrintService();
        if (defaultService == null) {
            return null;
        }
        return defaultService.getName();
    }

    private void configureProfile(PrinterProfileId profileId, String profileLabel) {
        String printerName = resolveSelectedPrinterName();
        if (printerName == null || printerName.isBlank()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Tidak ada printer yang dapat digunakan. Pilih printer atau atur Default Windows Printer terlebih dahulu.",
                    "Printer Profile",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Pengaturan " + profileLabel + " akan dibuka menggunakan driver printer:\n\n"
                        + printerName
                        + "\n\nAnda dapat mengubah seluruh opsi yang disediakan driver, lalu simpan dari jendela driver.",
                "Atur " + profileLabel,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE);

        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            WindowsPrinterProfileEditor editor = new WindowsPrinterProfileEditor(profileStore);
            boolean saved = editor.editAndSave(this, printerName, profileId);
            if (saved) {
                refreshProfileStatuses();
                JOptionPane.showMessageDialog(
                        this,
                        profileLabel + " berhasil disimpan untuk:\n" + printerName,
                        "Printer Profile",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Gagal mengatur " + profileLabel + ":\n" + ex.getMessage(),
                    "Printer Profile",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshProfileStatuses() {
        updateProfileStatus(PrinterProfileId.PROFILE_1, profile1Status);
        updateProfileStatus(PrinterProfileId.PROFILE_2, profile2Status);
    }

    private void updateProfileStatus(PrinterProfileId profileId, JLabel label) {
        try {
            Optional<com.phomoria.print.windows.PrinterProfileData> saved =
                    profileStore.load(profileId);
            if (saved.isEmpty()) {
                label.setText("Belum dikonfigurasi");
                return;
            }

            String selectedName = resolveSelectedPrinterName();
            String savedPrinter = saved.get().getPrinterName();
            if (selectedName != null && !selectedName.isBlank()
                    && !savedPrinter.equalsIgnoreCase(selectedName)) {
                label.setText("Tersimpan untuk: " + savedPrinter
                        + " (printer terpilih berbeda)");
            } else {
                label.setText("Tersimpan untuk: " + savedPrinter);
            }
        } catch (IOException ex) {
            label.setText("Data profil bermasalah: " + ex.getMessage());
        }
    }

    public void applyTo(AppSettings settings) {
        PrinterChoice selected = (PrinterChoice) printer.getSelectedItem();
        settings.setPrinterName(selected == null ? "" : selected.name);
        settings.setAutoPrint(autoPrint.isSelected());
        settings.setViewPrintButton(viewPrintButton.isSelected());
    }

    private static final class PrinterChoice {
        private final String name;
        private final String label;

        private PrinterChoice(String name, String label) {
            this.name = name;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
