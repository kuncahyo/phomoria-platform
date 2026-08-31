package com.phomoria.ui;

import com.phomoria.config.AppSettings;
import com.phomoria.frame.PrintPaperSize;

import javax.swing.*;
import java.awt.*;

public final class PrintPaperSettingsPanel extends JPanel {

    private final JComboBox<PrintPaperSize> paper =
            new JComboBox<>(PrintPaperSize.values());

    private final JSpinner width =
            new JSpinner(
                    new SpinnerNumberModel(
                            4.0,
                            0.5,
                            30.0,
                            0.1
                    )
            );

    private final JSpinner height =
            new JSpinner(
                    new SpinnerNumberModel(
                            6.0,
                            0.5,
                            30.0,
                            0.1
                    )
            );

    private final JSpinner dpi =
            new JSpinner(
                    new SpinnerNumberModel(
                            300,
                            72,
                            600,
                            1
                    )
            );

    private final JLabel customLabel =
            new JLabel("Custom:");

    public PrintPaperSettingsPanel(
            AppSettings settings
    ) {
        setLayout(
                new GridBagLayout()
        );

        setBorder(
                BorderFactory.createTitledBorder(
                        "Kertas Print"
                )
        );

        paper.setSelectedItem(
                settings.getPrintPaperSize()
        );

        width.setValue(
                settings.getCustomPaperWidthInches()
        );

        height.setValue(
                settings.getCustomPaperHeightInches()
        );

        dpi.setValue(
                settings.getPrintDpi()
        );

        GridBagConstraints g =
                new GridBagConstraints();

        g.insets =
                new Insets(5, 5, 5, 5);

        g.anchor =
                GridBagConstraints.WEST;

        g.gridx = 0;
        g.gridy = 0;

        add(
                new JLabel("Ukuran Kertas"),
                g
        );

        g.gridx = 1;

        add(paper, g);

        g.gridx = 0;
        g.gridy = 1;

        add(
                new JLabel("Lebar"),
                g
        );

        g.gridx = 1;

        add(width, g);

        g.gridx = 2;

        add(
                new JLabel("inch"),
                g
        );

        g.gridx = 0;
        g.gridy = 2;

        add(
                new JLabel("Tinggi"),
                g
        );

        g.gridx = 1;

        add(height, g);

        g.gridx = 2;

        add(
                new JLabel("inch"),
                g
        );

        g.gridx = 0;
        g.gridy = 3;

        add(
                new JLabel("Resolusi Render"),
                g
        );

        g.gridx = 1;

        add(dpi, g);

        g.gridx = 2;

        add(
                new JLabel("DPI"),
                g
        );

        g.gridx = 0;
        g.gridy = 4;
        g.gridwidth = 3;

        JLabel note =
                new JLabel(
                        "<html>"
                                + "Utama photobooth: 2×6 dan 4×6 inch. "
                                + "Custom digunakan jika ukuran kertas berbeda."
                                + "</html>"
                );

        note.setForeground(
                new Color(90, 90, 90)
        );

        add(note, g);

        paper.addActionListener(
                e -> updateCustomEnabled()
        );

        updateCustomEnabled();
    }

    private void updateCustomEnabled() {
        boolean custom =
                paper.getSelectedItem()
                        == PrintPaperSize.CUSTOM;

        customLabel.setEnabled(custom);
        width.setEnabled(custom);
        height.setEnabled(custom);
    }

    public void applyTo(
            AppSettings settings
    ) {
        PrintPaperSize selected =
                (PrintPaperSize) paper.getSelectedItem();

        settings.setPrintPaperSize(
                selected
        );

        settings.setCustomPaperWidthInches(
                ((Number) width.getValue())
                        .doubleValue()
        );

        settings.setCustomPaperHeightInches(
                ((Number) height.getValue())
                        .doubleValue()
        );

        settings.setPrintDpi(
                ((Number) dpi.getValue())
                        .intValue()
        );
    }
}
