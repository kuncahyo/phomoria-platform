package com.phomoria.ui;

import com.phomoria.app.ApplicationFrame;

import javax.swing.*;
import java.awt.*;

public final class StartScreen extends JPanel {
    private final ApplicationFrame frame;

    public StartScreen(ApplicationFrame frame) {
        this.frame = frame;

        setLayout(new BorderLayout());
        setBackground(new Color(18, 18, 22));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("PHOMORIA");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 54));

        JLabel subtitle = new JLabel("PHOTO BOOTH");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setForeground(new Color(190, 190, 200));
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 25));

        JLabel media = new JLabel(
                "<html><center>VIDEO / GIF PROMO<br><br>"
                        + "Tempat konten promosi akan ditampilkan di sini.</center></html>",
                SwingConstants.CENTER
        );
        media.setPreferredSize(new Dimension(650, 300));
        media.setMaximumSize(new Dimension(650, 300));
        media.setForeground(new Color(150, 150, 160));
        media.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 80)));

        JButton pay = new JButton("BAYAR & MULAI FOTO");
        pay.setAlignmentX(Component.CENTER_ALIGNMENT);
        pay.setFont(new Font("SansSerif", Font.BOLD, 22));
        pay.setPreferredSize(new Dimension(300, 60));
        pay.setMaximumSize(new Dimension(300, 60));

        JButton settings = new JButton("SETTINGS");
        settings.setAlignmentX(Component.CENTER_ALIGNMENT);

        center.add(Box.createVerticalGlue());
        center.add(title);
        center.add(Box.createVerticalStrut(5));
        center.add(subtitle);
        center.add(Box.createVerticalStrut(25));
        center.add(media);
        center.add(Box.createVerticalStrut(25));
        center.add(pay);
        center.add(Box.createVerticalStrut(12));
        center.add(settings);
        center.add(Box.createVerticalGlue());

        add(center, BorderLayout.CENTER);

        pay.addActionListener(e -> new PaymentDialog(
                frame,
                frame::showFrameSelection
        ).setVisible(true));

        settings.addActionListener(
                e -> frame.showSettings()
        );
    }
}
