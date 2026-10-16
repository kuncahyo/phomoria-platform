package com.phomoria.ui;

import com.phomoria.config.AppSettings;
import com.phomoria.print.PrinterService;
import com.phomoria.print.windows.PrinterProfileData;
import com.phomoria.print.windows.PrinterProfileId;
import com.phomoria.print.windows.PrinterProfileStore;
import com.phomoria.print.windows.WindowsPrinterProfileEditor;
import com.phomoria.print.windows.WindowsProfilePrinter;

import javax.print.PrintService;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
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

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton configure = new JButton("ATUR " + title.toUpperCase());
        JButton test = new JButton("TES CETAK");
        actions.add(configure);
        actions.add(test);
        row.add(actions, BorderLayout.EAST);

        configure.addActionListener(e -> configureProfile(profileId, title));
        test.addActionListener(e -> testPrintProfile(profileId, title, test));
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
        for (PrinterChoice choice : choices) printer.addItem(choice);

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
            printer.addItem(new PrinterChoice(wanted, wanted + " (tersimpan, tidak terdeteksi)"));
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
        if (!selectedName.isBlank()) return selectedName;
        PrintService defaultService = PrinterJob.getPrinterJob().getPrintService();
        return defaultService == null ? null : defaultService.getName();
    }

    private void configureProfile(PrinterProfileId profileId, String profileLabel) {
        String printerName = resolveSelectedPrinterName();
        if (printerName == null || printerName.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Tidak ada printer yang dapat digunakan. Pilih printer atau atur Default Windows Printer terlebih dahulu.",
                    "Printer Profile", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Pengaturan " + profileLabel + " akan dibuka menggunakan driver printer:\n\n"
                        + printerName
                        + "\n\nAnda dapat mengubah opsi driver, lalu simpan dari jendela driver.",
                "Atur " + profileLabel, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) return;

        try {
            WindowsPrinterProfileEditor editor = new WindowsPrinterProfileEditor(profileStore);
            boolean saved = editor.editAndSave(this, printerName, profileId);
            if (saved) {
                refreshProfileStatuses();
                JOptionPane.showMessageDialog(this,
                        profileLabel + " berhasil disimpan untuk:\n" + printerName,
                        "Printer Profile", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this,
                    "Gagal mengatur " + profileLabel + ":\n" + ex.getMessage(),
                    "Printer Profile", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void testPrintProfile(PrinterProfileId profileId, String label, JButton button) {
        String printerName = resolveSelectedPrinterName();
        if (printerName == null || printerName.isBlank()) {
            JOptionPane.showMessageDialog(this, "Pilih printer terlebih dahulu.",
                    "Tes Cetak", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Optional<PrinterProfileData> saved = profileStore.load(profileId);
            if (saved.isEmpty()) {
                JOptionPane.showMessageDialog(this, label + " belum dikonfigurasi. Tekan ATUR terlebih dahulu.",
                        "Tes Cetak", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!saved.get().getPrinterName().equalsIgnoreCase(printerName)) {
                JOptionPane.showMessageDialog(this,
                        label + " tersimpan untuk printer:\n" + saved.get().getPrinterName()
                                + "\n\nPrinter yang dipilih:\n" + printerName
                                + "\n\nPilih printer yang sesuai atau atur ulang profil.",
                        "Printer Tidak Cocok", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Tidak dapat membaca profil:\n" + ex.getMessage(),
                    "Tes Cetak", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Kirim satu halaman tes ke printer berikut?\n\n" + printerName
                        + "\nProfil: " + label
                        + "\n\nPastikan printer siap dan kertas tersedia.",
                "Konfirmasi Tes Cetak", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) return;

        button.setEnabled(false);
        status.setText("Mengirim halaman tes " + label + "...");
        BufferedImage testImage = createTestImage(label, printerName);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                new WindowsProfilePrinter(profileStore).print(printerName, profileId, testImage);
                return null;
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                try {
                    get();
                    status.setText("Perintah tes cetak " + label + " berhasil dikirim.");
                    JOptionPane.showMessageDialog(PrinterSettingsPanel.this,
                            "Perintah tes cetak " + label + " berhasil dikirim ke Windows.\n"
                                    + "Periksa hasil fisik printer untuk memastikan pengaturan driver diterapkan.",
                            "Tes Cetak", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    status.setText("Tes cetak gagal.");
                    JOptionPane.showMessageDialog(PrinterSettingsPanel.this,
                            "Tes cetak gagal:\n" + cause.getMessage(),
                            "Tes Cetak", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private BufferedImage createTestImage(String profileLabel, String printerName) {
        int width = 1200;
        int height = 1800;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(5f));
            g.drawRect(35, 35, width - 70, height - 70);
            g.setFont(new Font("SansSerif", Font.BOLD, 58));
            g.drawString("PHOMORIA", 90, 160);
            g.setFont(new Font("SansSerif", Font.BOLD, 42));
            g.drawString("PRINTER PROFILE TEST", 90, 245);
            g.setFont(new Font("SansSerif", Font.BOLD, 38));
            g.drawString(profileLabel.toUpperCase(), 90, 330);
            g.setFont(new Font("SansSerif", Font.PLAIN, 25));
            g.drawString("Printer: " + printerName, 90, 405);
            g.drawString("Periksa orientasi, ukuran kertas, margin, dan hasil potong.", 90, 455);

            int barY = 560;
            int[] colors = {0x000000, 0x555555, 0xAAAAAA, 0xFFFFFF,
                    0xFF0000, 0x00AA00, 0x0000FF, 0xFFFF00, 0x00FFFF, 0xFF00FF};
            int barWidth = (width - 180) / colors.length;
            for (int i = 0; i < colors.length; i++) {
                g.setColor(new Color(colors[i]));
                g.fillRect(90 + i * barWidth, barY, barWidth, 130);
                g.setColor(Color.BLACK);
                g.drawRect(90 + i * barWidth, barY, barWidth, 130);
            }

            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(3f));
            int sampleTop = 820;
            for (int i = 0; i < 8; i++) {
                int inset = 90 + i * 35;
                g.drawRect(inset, sampleTop + i * 35,
                        width - inset * 2, height - sampleTop - 260 - i * 70);
            }
            g.setFont(new Font("SansSerif", Font.PLAIN, 24));
            g.drawString("Garis tepi: cek apakah terpotong atau bergeser.", 90, height - 135);
            g.drawString("Area tengah: cek ketajaman dan gradasi warna.", 90, height - 95);
        } finally {
            g.dispose();
        }
        return image;
    }

    private void refreshProfileStatuses() {
        updateProfileStatus(PrinterProfileId.PROFILE_1, profile1Status);
        updateProfileStatus(PrinterProfileId.PROFILE_2, profile2Status);
    }

    private void updateProfileStatus(PrinterProfileId profileId, JLabel label) {
        try {
            Optional<PrinterProfileData> saved = profileStore.load(profileId);
            if (saved.isEmpty()) {
                label.setText("Belum dikonfigurasi");
                return;
            }
            String selectedName = resolveSelectedPrinterName();
            String savedPrinter = saved.get().getPrinterName();
            if (selectedName != null && !selectedName.isBlank()
                    && !savedPrinter.equalsIgnoreCase(selectedName)) {
                label.setText("Tersimpan untuk: " + savedPrinter + " (printer terpilih berbeda)");
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
