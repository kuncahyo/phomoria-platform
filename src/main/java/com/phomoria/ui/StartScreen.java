package com.phomoria.ui;

import com.phomoria.app.ApplicationFrame;
import com.phomoria.app.AppContext;
import com.phomoria.theme.ThemeBackgroundPanel;
import com.phomoria.theme.ThemeButton;
import com.phomoria.theme.ThemeConfig;
import com.phomoria.theme.ThemeManager;
import com.phomoria.print.PrintWarningManager;
import javax.swing.*;
import java.awt.*;

/** Start page whose background and button geometry are controlled by the external theme. */
public final class StartScreen extends JPanel {
    private final ThemeBackgroundPanel background;
    private final ThemeButton pay;
    private final ThemeButton settings;
    private final Timer printWarningTimer;

    public StartScreen(ApplicationFrame frame) {
        setLayout(new BorderLayout());
        background = new ThemeBackgroundPanel("start", 0.25f);
        background.setLayout(null);
        pay = new ThemeButton("start-payment", "BAYAR & MULAI FOTO");
        settings = new ThemeButton("start-settings", "SETTINGS");
        pay.setFont(new Font("SansSerif", Font.BOLD, 22));
        settings.setFont(new Font("SansSerif", Font.BOLD, 16));
        background.add(pay);
        background.add(settings);
        add(background, BorderLayout.CENTER);
        pay.addActionListener(e -> {
            AppContext.clearVoucherAuthorization();
            new PaymentDialog(frame, frame::showFrameSelection).setVisible(true);
        });
        settings.addActionListener(e -> frame.showSettings());
        background.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { layoutThemeElements(); }
        });
        layoutThemeElements();
        printWarningTimer = new Timer(1000, e -> {
            if (!isShowing()) return;
            if (!PrintWarningManager.isReadyToShow()) return;
            ((Timer) e.getSource()).stop();
            PrintWarningManager.PendingWarning warning = PrintWarningManager.consumeUnprinted();
            if (warning != null) {
                String message = "Sepertinya sesi foto ini belum berhasil dicetak.\n\n"
                        + "Tidak perlu khawatir, hasil foto Anda tetap tersimpan. "
                        + "Jika Anda merasa ini sebuah masalah, segera laporkan kepada admin "
                        + "dengan menyertakan waktu sesi foto dan kontak yang bisa dihubungi.\n\n"
                        + "Waktu sesi foto: " + warning.getFormattedTime() + "\n"
                        + "ID sesi: " + warning.getSessionId();
                JOptionPane.showMessageDialog(this, message, "Informasi Sesi Foto",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
        printWarningTimer.setRepeats(true);
        printWarningTimer.start();
    }

    @Override
    public void removeNotify() {
        if (printWarningTimer != null) printWarningTimer.stop();
        super.removeNotify();
    }

    private void layoutThemeElements() {
        ThemeConfig config = ThemeManager.config();
        double sx = background.getWidth() / (double) config.designWidth;
        double sy = background.getHeight() / (double) config.designHeight;
        ThemeConfig.Element p = config.elements.get("start-payment");
        ThemeConfig.Element s = config.elements.get("start-settings");
        if (p != null) pay.setBounds((int)(p.x*sx), (int)(p.y*sy), Math.max(1,(int)(p.width*sx)), Math.max(1,(int)(p.height*sy)));
        if (s != null) settings.setBounds((int)(s.x*sx), (int)(s.y*sy), Math.max(1,(int)(s.width*sx)), Math.max(1,(int)(s.height*sy)));
    }
}
