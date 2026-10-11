package com.phomoria.ui;

import com.phomoria.app.ApplicationFrame;

import javax.swing.*;
import java.awt.*;

public final class StartScreen extends JPanel {
    private final ApplicationFrame frame;

    public StartScreen(ApplicationFrame frame) {
        this.frame = frame;

        setLayout(new BorderLayout());

        BackgroundPanel background = new BackgroundPanel();
        background.setLayout(new BorderLayout());

        // ========================================
        // PENGATURAN POSISI TOMBOL
        // ========================================

        // Semakin besar angka, semakin ke bawah.
        // Semakin kecil angka, semakin ke atas.
        int BUTTON_POSITION_Y = 300;

        // Jarak antara tombol pembayaran dan Settings.
        int SETTINGS_GAP = 12;

        // ========================================

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        JPanel buttonContainer = new JPanel();
        buttonContainer.setOpaque(false);
        buttonContainer.setLayout(
                new BoxLayout(buttonContainer, BoxLayout.Y_AXIS)
        );

        JButton pay = new JButton("BAYAR & MULAI FOTO");
        pay.setAlignmentX(Component.CENTER_ALIGNMENT);
        pay.setFont(new Font("SansSerif", Font.BOLD, 22));
        pay.setPreferredSize(new Dimension(300, 60));
        pay.setMinimumSize(new Dimension(300, 60));
        pay.setMaximumSize(new Dimension(300, 60));

        JButton settings = new JButton("SETTINGS");
        settings.setAlignmentX(Component.CENTER_ALIGNMENT);

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

        pay.addActionListener(e -> new PaymentDialog(
                frame,
                frame::showFrameSelection
        ).setVisible(true));

        settings.addActionListener(e -> frame.showSettings());
    }

    /**
     * Full-screen animated GIF background.
     * The GIF is loaded from the application resources so it also works
     * when the application is packaged in a JAR.
     */
    private static final class BackgroundPanel extends JPanel {
        private final Image backgroundImage;

        private BackgroundPanel() {
            setOpaque(true);

            java.net.URL gifUrl =
                    StartScreen.class.getResource("/media/dash.gif");

            if (gifUrl == null) {
                throw new IllegalStateException(
                        "GIF background tidak ditemukan: /media/dash.gif"
                );
            }

            // ImageIcon loads the GIF and preserves its animation.
            backgroundImage = new ImageIcon(gifUrl).getImage();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            int panelWidth = getWidth();
            int panelHeight = getHeight();

            if (panelWidth <= 0 || panelHeight <= 0) {
                return;
            }

            int imageWidth = backgroundImage.getWidth(this);
            int imageHeight = backgroundImage.getHeight(this);

            if (imageWidth <= 0 || imageHeight <= 0) {
                return;
            }

            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                // Cover: fill the entire window while preserving image ratio.
                double scale = Math.max(
                        (double) panelWidth / imageWidth,
                        (double) panelHeight / imageHeight
                );

                int drawWidth = (int) Math.ceil(imageWidth * scale);
                int drawHeight = (int) Math.ceil(imageHeight * scale);
                int x = (panelWidth - drawWidth) / 2;
                int y = (panelHeight - drawHeight) / 2;

                // Passing this panel as ImageObserver keeps GIF frames updating.
                g2.drawImage(
                        backgroundImage,
                        x,
                        y,
                        drawWidth,
                        drawHeight,
                        this
                );

                // Subtle dark overlay helps foreground text remain readable.
                g2.setColor(new Color(0, 0, 0, 65));
                g2.fillRect(0, 0, panelWidth, panelHeight);
            } finally {
                g2.dispose();
            }
        }
    }
}
