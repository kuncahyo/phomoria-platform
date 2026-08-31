package com.phomoria.ui;

import com.phomoria.cloud.QrCodeGenerator;
import com.phomoria.debug.DebugLog;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class QrResultDialog extends JDialog {

    public QrResultDialog(
            JFrame owner,
            String url
    ) {
        super(owner, "DOWNLOAD HASIL FOTO", true);

        setSize(520, 620);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(12, 12));

        JLabel title = new JLabel(
                "SCAN UNTUK DOWNLOAD",
                SwingConstants.CENTER
        );

        title.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        24
                )
        );

        JLabel qrLabel = new JLabel("", SwingConstants.CENTER);

        JLabel urlLabel = new JLabel(
                "<html><center>" + escape(url) + "</center></html>",
                SwingConstants.CENTER
        );

        JButton close = new JButton("TUTUP");
        close.addActionListener(e -> dispose());

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.add(urlLabel, BorderLayout.CENTER);
        bottom.add(close, BorderLayout.SOUTH);

        add(title, BorderLayout.NORTH);
        add(qrLabel, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        try {
            BufferedImage qr = QrCodeGenerator.generate(url, 380);
            qrLabel.setIcon(new ImageIcon(qr));

            DebugLog.info("QR result generated for URL.");
        } catch (Exception ex) {
            qrLabel.setText("GAGAL MEMBUAT QR");
            DebugLog.error("QR generation failed.", ex);
        }
    }

    private String escape(String text) {
        if (text == null) return "";

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
