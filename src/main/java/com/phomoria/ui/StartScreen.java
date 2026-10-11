package com.phomoria.ui;

import com.phomoria.app.ApplicationFrame;
import com.phomoria.theme.ThemeBackgroundPanel;
import com.phomoria.theme.ThemeButton;

import javax.swing.*;
import java.awt.*;

public final class StartScreen extends JPanel {
    private final ApplicationFrame frame;

    public StartScreen(ApplicationFrame frame) {
        this.frame = frame;
        setLayout(new BorderLayout());

        JPanel background = new ThemeBackgroundPanel("start", 0.25f);
        background.setLayout(new BorderLayout());

        // Posisi vertikal tombol masih mengikuti pengaturan StartScreen lama.
        int BUTTON_POSITION_Y = 300;
        int SETTINGS_GAP = 12;

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        JPanel buttonContainer = new JPanel();
        buttonContainer.setOpaque(false);
        buttonContainer.setLayout(new BoxLayout(buttonContainer, BoxLayout.Y_AXIS));

        ThemeButton pay = new ThemeButton("start-payment", "BAYAR & MULAI FOTO");
        pay.setAlignmentX(Component.CENTER_ALIGNMENT);
        pay.setFont(new Font("SansSerif", Font.BOLD, 22));
        pay.setPreferredSize(new Dimension(300, 60));
        pay.setMinimumSize(new Dimension(300, 60));
        pay.setMaximumSize(new Dimension(300, 60));

        ThemeButton settings = new ThemeButton("start-settings", "SETTINGS");
        settings.setAlignmentX(Component.CENTER_ALIGNMENT);
        settings.setPreferredSize(new Dimension(160, 48));
        settings.setMinimumSize(new Dimension(160, 48));
        settings.setMaximumSize(new Dimension(160, 48));

        buttonContainer.add(pay);
        buttonContainer.add(Box.createVerticalStrut(SETTINGS_GAP));
        buttonContainer.add(settings);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(BUTTON_POSITION_Y, 0, 0, 0);
        center.add(buttonContainer, gbc);

        background.add(center, BorderLayout.CENTER);
        add(background, BorderLayout.CENTER);

        // Aset visual tidak mengubah perilaku tombol.
        pay.addActionListener(e -> new PaymentDialog(frame, frame::showFrameSelection).setVisible(true));
        settings.addActionListener(e -> frame.showSettings());
    }
}
