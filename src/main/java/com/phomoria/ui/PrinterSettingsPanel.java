package com.phomoria.ui;

import com.phomoria.config.AppSettings;
import com.phomoria.print.PrinterService;
import javax.print.PrintService;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class PrinterSettingsPanel extends JPanel {
    private final JComboBox<PrinterChoice> printer = new JComboBox<>();
    private final JCheckBox autoPrint;
    private final JCheckBox viewPrintButton;
    private final JLabel status = new JLabel(" ");

    public PrinterSettingsPanel(AppSettings settings) {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("Printer"));
        autoPrint = new JCheckBox("Auto Print", settings.isAutoPrint());
        viewPrintButton = new JCheckBox("View Print Button", settings.isViewPrintButton());

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(printer, BorderLayout.CENTER);
        JButton refresh = new JButton("REFRESH");
        row.add(refresh, BorderLayout.EAST);

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 6, 5, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        add(new JLabel("Pilih Printer"), g);
        g.gridx = 1; g.weightx = 1;
        add(row, g);
        g.gridx = 0; g.gridy = 1; g.gridwidth = 2;
        add(autoPrint, g);
        g.gridy = 2;
        add(viewPrintButton, g);
        g.gridy = 3;
        status.setForeground(new Color(90, 90, 90));
        add(status, g);

        refresh.addActionListener(e -> reload(settings.getPrinterName()));
        reload(settings.getPrinterName());
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

    public void applyTo(AppSettings settings) {
        PrinterChoice selected = (PrinterChoice) printer.getSelectedItem();
        settings.setPrinterName(selected == null ? "" : selected.name);
        settings.setAutoPrint(autoPrint.isSelected());
        settings.setViewPrintButton(viewPrintButton.isSelected());
    }

    private static final class PrinterChoice {
        private final String name, label;
        private PrinterChoice(String name, String label) { this.name = name; this.label = label; }
        @Override public String toString() { return label; }
    }
}
