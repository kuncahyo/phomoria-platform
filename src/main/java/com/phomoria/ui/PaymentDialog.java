package com.phomoria.ui;

import com.phomoria.payment.LegacyPaymentService;
import com.phomoria.theme.ThemeDialogSupport;
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
    private final JButton voucher = new JButton("VOUCHER");
    private final Timer pollTimer;
    private final Timer countdownTimer;
    private Timer expiryGraceTimer;
    private int remainingSeconds;
    private LegacyPaymentService service;
    private LegacyPaymentService.Payment payment;
    private long startedAt;
    private boolean paid, closing, polling, expirationPending;

    public PaymentDialog(JFrame owner, Runnable paymentSuccess) {
        super(owner, "Pembayaran QRIS", true);
        this.paymentSuccess = paymentSuccess;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        JPanel content = ThemeDialogSupport.createPanel("payment-dialog");
        ThemeDialogSupport.add(content, "payment-dialog-title", new JLabel("", SwingConstants.CENTER),
                "SCAN QR UNTUK PEMBAYARAN", 20, 12, 460, 36, true);
        ThemeDialogSupport.add(content, "payment-dialog-qr", qr,
                "QRIS", 70, 60, 360, 360, false);
        ThemeDialogSupport.add(content, "payment-dialog-countdown", qrisCountdown,
                "10:00", 360, 60, 110, 28, false);
        ThemeDialogSupport.add(content, "payment-dialog-voucher", voucher,
                "VOUCHER", 350, 385, 120, 34, true);
        ThemeDialogSupport.add(content, "payment-dialog-note", note,
                "Menunggu pembayaran", 20, 450, 460, 48, false);
        ThemeDialogSupport.add(content, "payment-dialog-cancel", cancel,
                "BATAL", 180, 510, 140, 36, true);
        voucher.setFocusable(false);
        setContentPane(content);
        setSize(500, 600);
        setLocationRelativeTo(owner);
        cancel.addActionListener(e -> closeAndCancel());
        voucher.addActionListener(e -> openVoucherDialog(owner));
        addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { closeAndCancel(); } });
        pollTimer = new Timer(2500, e -> pollStatus());
        countdownTimer = new Timer(1000, e -> handleCountdownTick());
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

    private void openVoucherDialog(JFrame owner) {
        if (closing || paid) return;
        voucher.setEnabled(false);
        VoucherDialog dialog = new VoucherDialog(owner, this::completeWithVoucher);
        dialog.setVisible(true);
        if (!closing && !paid) voucher.setEnabled(true);
    }

    private void completeWithVoucher() {
        if (closing || paid) return;
        closing = true;
        stopPaymentTimers();
        dispose();

        // Voucher authorization replaces this QRIS transaction.
        if (payment != null && service != null) new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                service.cancel(payment.orderId());
                return null;
            }
        }.execute();

        paymentSuccess.run();
    }

    private void pollStatus() {
        if (polling || closing || payment == null) return;
        polling = true;
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception { return service.checkStatus(payment.orderId()); }
            @Override protected void done() {
                polling = false;
                if (closing) return;
                try {
                    String status = get();
                    if ("settlement".equalsIgnoreCase(status) || "capture".equalsIgnoreCase(status)) {
                        paid = true;
                        closing = true;
                        stopPaymentTimers();
                        dispose();
                        paymentSuccess.run();
                    } else if ("expire".equalsIgnoreCase(status) || "deny".equalsIgnoreCase(status) || "cancel".equalsIgnoreCase(status)) {
                        stopPaymentTimers();
                        if (expirationPending) {
                            closeAfterExpiry();
                        } else {
                            showError("Transaksi berakhir dengan status: " + status);
                        }
                    } else if (expirationPending) {
                        note.setText("Memastikan status pembayaran terakhir...");
                    }
                } catch (Exception ex) { note.setText("Memeriksa pembayaran... koneksi terganggu."); }
            }
        }.execute();
    }

    private void closeAndCancel() {
        if (closing) return;
        closing = true;
        stopPaymentTimers();
        cancel.setEnabled(false);
        dispose();
        if (!paid && payment != null && service != null) new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { service.cancel(payment.orderId()); return null; }
        }.execute();
    }

    private void stopPaymentTimers() {
        pollTimer.stop();
        countdownTimer.stop();
        if (expiryGraceTimer != null) {
            expiryGraceTimer.stop();
            expiryGraceTimer = null;
        }
    }

    /**
     * The visual QR timer is not proof that a payment failed. Give Midtrans
     * time to publish a payment made at the deadline and keep checking status.
     */
    private void beginFinalPaymentCheck() {
        if (closing || expirationPending || payment == null) return;
        expirationPending = true;
        countdownTimer.stop();
        qrisCountdown.setText("00:00");
        qrisCountdown.setForeground(new Color(210, 55, 55));
        note.setText("Waktu QR habis. Memastikan pembayaran terakhir...");
        cancel.setEnabled(true);
        pollTimer.setDelay(1000);
        pollTimer.setInitialDelay(0);
        pollTimer.start();
        pollStatus();

        expiryGraceTimer = new Timer(15000, e -> closeAfterExpiry());
        expiryGraceTimer.setRepeats(false);
        expiryGraceTimer.start();
    }

    private void closeAfterExpiry() {
        if (closing || paid) return;
        closing = true;
        stopPaymentTimers();
        dispose();
        // Do not send a cancellation when the final server status is unknown:
        // a late settlement must never be cancelled by this UI timeout.
    }

    private void handleCountdownTick() {
        remainingSeconds--;
        updateQrisCountdown();
        if (remainingSeconds <= 0) {
            beginFinalPaymentCheck();
        }
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
