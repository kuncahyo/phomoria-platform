package com.phomoria.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class PaymentDialog extends JDialog {
    public PaymentDialog(
            JFrame owner,
            Runnable paymentSuccess
    ) {
        super(owner, "Pembayaran", true);

        setSize(520, 620);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(15, 15));

        JLabel title = new JLabel(
                "SCAN QR UNTUK PEMBAYARAN",
                SwingConstants.CENTER
        );
        title.setFont(new Font("SansSerif", Font.BOLD, 22));

        JLabel qr = new JLabel(
                new ImageIcon(createDemoQr()),
                SwingConstants.CENTER
        );

        JLabel note = new JLabel(
                "<html><center>QR pembayaran akan diganti dengan QRIS/Midtrans "
                        + "dari API lama.<br>Untuk presentasi, gunakan tombol "
                        + "\"SIMULASI PEMBAYARAN BERHASIL\".</center></html>",
                SwingConstants.CENTER
        );

        JButton success = new JButton("SIMULASI PEMBAYARAN BERHASIL");
        JButton cancel = new JButton("BATAL");

        JPanel bottom = new JPanel(new FlowLayout());
        bottom.add(cancel);
        bottom.add(success);

        add(title, BorderLayout.NORTH);
        add(qr, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.add(note, BorderLayout.CENTER);
        south.add(bottom, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        success.addActionListener(e -> {
            dispose();
            paymentSuccess.run();
        });

        cancel.addActionListener(e -> dispose());
    }

    private BufferedImage createDemoQr() {
        int size = 330;
        BufferedImage image = new BufferedImage(
                size,
                size,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, size, size);

        g.setColor(Color.BLACK);

        drawFinder(g, 25, 25);
        drawFinder(g, 225, 25);
        drawFinder(g, 25, 225);

        for (int y = 80; y < 300; y += 18) {
            for (int x = 80; x < 250; x += 18) {
                if (((x * 31 + y * 17) % 7) < 3) {
                    g.fillRect(x, y, 10, 10);
                }
            }
        }

        g.dispose();
        return image;
    }

    private void drawFinder(Graphics2D g, int x, int y) {
        g.fillRect(x, y, 80, 80);
        g.setColor(Color.WHITE);
        g.fillRect(x + 12, y + 12, 56, 56);
        g.setColor(Color.BLACK);
        g.fillRect(x + 25, y + 25, 30, 30);
    }
}
