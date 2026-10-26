package com.phomoria.ui;

import com.phomoria.payment.LegacyPaymentService;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/** Live Midtrans QR payment against the legacy device and transactions tables. */
public final class PaymentDialog extends JDialog {
    private final Runnable paymentSuccess;
    private final JLabel qr = new JLabel("Menghubungkan ke pembayaran...", SwingConstants.CENTER);
    private final JLabel note = new JLabel("", SwingConstants.CENTER);
    private final JLabel qrisCountdown = new JLabel("10:00", SwingConstants.RIGHT);
    private final JButton cancel = new JButton("BATAL");
    private final Timer pollTimer;
    private final Timer countdownTimer;
    private int remainingSeconds;
    private LegacyPaymentService service;
    private LegacyPaymentService.Payment payment;
    private long startedAt;
    private boolean paid, closing, polling;

    public PaymentDialog(JFrame owner, Runnable paymentSuccess) {
        super(owner, "Pembayaran QRIS", true);
        this.paymentSuccess = paymentSuccess;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(500, 600); setLocationRelativeTo(owner);
        setLayout(new BorderLayout(12, 12));
        JLabel title = new JLabel("SCAN QR UNTUK PEMBAYARAN", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        qr.setFont(new Font("SansSerif", Font.PLAIN, 16));
        note.setFont(new Font("SansSerif", Font.BOLD, 16));
        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.add(note, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout()); buttons.add(cancel);
        south.add(buttons, BorderLayout.SOUTH);
        JPanel qrPanel = new JPanel(new BorderLayout(4, 4));
        qrPanel.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        qrisCountdown.setFont(new Font("SansSerif", Font.BOLD, 18));
        qrisCountdown.setForeground(new Color(70, 70, 70));
        qrPanel.add(qrisCountdown, BorderLayout.NORTH);
        qrPanel.add(qr, BorderLayout.CENTER);
        add(title, BorderLayout.NORTH); add(qrPanel, BorderLayout.CENTER); add(south, BorderLayout.SOUTH);
        cancel.addActionListener(e -> closeAndCancel());
        addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { closeAndCancel(); } });
        pollTimer = new Timer(2500, e -> pollStatus());
        countdownTimer = new Timer(1000, e -> {
            remainingSeconds--;
            updateQrisCountdown();
            if (remainingSeconds <= 0) {
                countdownTimer.stop();
                closeAndCancel();
            }
        });
        beginPayment();
    }

    private void beginPayment() {
        cancel.setEnabled(false);
        new SwingWorker<LegacyPaymentService.Payment, Void>() {
            private LegacyPaymentService created;
            @Override protected LegacyPaymentService.Payment doInBackground() throws Exception {
                created = new LegacyPaymentService();
                return created.createPayment();
            }
            @Override protected void done() {
                try {
                    payment = get(); service = created;
                    BufferedImage image = ImageIO.read(URI.create(payment.qrUrl()).toURL());
                    if (image == null) throw new IllegalStateException("Gambar QR tidak dapat dibaca.");
                    Image scaled = image.getScaledInstance(340, 340, Image.SCALE_SMOOTH);
                    qr.setText(null); qr.setIcon(new ImageIcon(scaled));
                    note.setText("Total: " + NumberFormat.getCurrencyInstance(new Locale("id", "ID")).format(payment.amount()) + "  •  Menunggu pembayaran");
                    cancel.setEnabled(true);
                    startedAt = System.currentTimeMillis();
                    remainingSeconds = com.phomoria.app.AppContext.settings().getQrisTimerSeconds();
                    updateQrisCountdown();
                    countdownTimer.start();
                    pollTimer.start();
                } catch (Exception ex) { showError(root(ex)); }
            }
        }.execute();
    }

    private void pollStatus() {
        if (polling || closing || payment == null) return;
        if (System.currentTimeMillis() - startedAt >= com.phomoria.app.AppContext.settings().getQrisTimerSeconds() * 1000L) { closeAndCancel(); return; }
        polling = true;
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception { return service.checkStatus(payment.orderId()); }
            @Override protected void done() {
                polling = false;
                if (closing) return;
                try {
                    String status = get();
                    if ("settlement".equalsIgnoreCase(status) || "capture".equalsIgnoreCase(status)) {
                        paid = true; closing = true; pollTimer.stop(); dispose(); paymentSuccess.run();
                    } else if ("expire".equalsIgnoreCase(status) || "deny".equalsIgnoreCase(status) || "cancel".equalsIgnoreCase(status)) {
                        pollTimer.stop(); showError("Transaksi berakhir dengan status: " + status);
                    }
                } catch (Exception ex) { note.setText("Memeriksa pembayaran... koneksi terganggu."); }
            }
        }.execute();
    }

    private void closeAndCancel() {
        if (closing) return;
        closing = true; pollTimer.stop(); countdownTimer.stop(); cancel.setEnabled(false); dispose();
        if (!paid && payment != null && service != null) new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { service.cancel(payment.orderId()); return null; }
        }.execute();
    }

    private void updateQrisCountdown() {
        int seconds = Math.max(0, remainingSeconds);
        qrisCountdown.setText(String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
        qrisCountdown.setForeground(seconds <= 60 ? new Color(210, 55, 55) : new Color(70, 70, 70));
    }

    private void showError(String message) {
        pollTimer.stop(); countdownTimer.stop(); qr.setIcon(null); qr.setText("PEMBAYARAN TIDAK DAPAT DILANJUTKAN");
        note.setText("<html><div style='text-align:center;width:360px'>" + escape(message) + "</div></html>");
        cancel.setText("TUTUP"); cancel.setEnabled(true);
    }
    private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private static String root(Exception e) {
        Throwable t = e; while ((t instanceof ExecutionException || t.getCause() != null) && t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }
}
