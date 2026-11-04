package com.phomoria.ui;

import com.phomoria.app.ApplicationFrame;
import com.phomoria.app.AppContext;
import com.phomoria.theme.ThemeBackgroundPanel;
import com.phomoria.theme.ThemeButton;
import com.phomoria.theme.ThemeConfig;
import com.phomoria.theme.ThemeManager;
import javax.swing.*;
import java.awt.*;

/** Start page whose background and button geometry are controlled by the external theme. */
public final class StartScreen extends JPanel {
    private final ThemeBackgroundPanel background;
    private final ThemeButton pay;
    private final ThemeButton settings;
    private final JButton voucher;

    public StartScreen(ApplicationFrame frame) {
        setLayout(new BorderLayout());
        background = new ThemeBackgroundPanel("start", 0.25f);
        background.setLayout(null);
        pay = new ThemeButton("start-payment", "BAYAR & MULAI FOTO");
        settings = new ThemeButton("start-settings", "SETTINGS");
        voucher = new JButton("GUNAKAN VOUCHER");
        voucher.setFont(new Font("SansSerif", Font.BOLD, 18));
        pay.setFont(new Font("SansSerif", Font.BOLD, 22));
        settings.setFont(new Font("SansSerif", Font.BOLD, 16));
        background.add(pay);
        background.add(settings);
        background.add(voucher);
        add(background, BorderLayout.CENTER);
        pay.addActionListener(e -> {
            AppContext.clearVoucherAuthorization();
            new PaymentDialog(frame, frame::showFrameSelection).setVisible(true);
        });
        voucher.addActionListener(e -> new VoucherDialog(frame, frame::showFrameSelection).setVisible(true));
        settings.addActionListener(e -> frame.showSettings());
        background.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { layoutThemeElements(); }
        });
        layoutThemeElements();
    }

    private void layoutThemeElements() {
        ThemeConfig config = ThemeManager.config();
        double sx = background.getWidth() / (double) config.designWidth;
        double sy = background.getHeight() / (double) config.designHeight;
        ThemeConfig.Element p = config.elements.get("start-payment");
        ThemeConfig.Element s = config.elements.get("start-settings");
        if (p != null) pay.setBounds((int)(p.x*sx), (int)(p.y*sy), Math.max(1,(int)(p.width*sx)), Math.max(1,(int)(p.height*sy)));
        if (s != null) settings.setBounds((int)(s.x*sx), (int)(s.y*sy), Math.max(1,(int)(s.width*sx)), Math.max(1,(int)(s.height*sy)));
        if (p != null) {
            int x = (int)(p.x*sx), y = (int)(p.y*sy);
            int w = Math.max(1, (int)(p.width*sx)), h = Math.max(1, (int)(p.height*sy));
            voucher.setBounds(x, y + h + Math.max(8, (int)(12*sy)), w, Math.max(42, (int)(h*0.72)));
        }
    }
}
