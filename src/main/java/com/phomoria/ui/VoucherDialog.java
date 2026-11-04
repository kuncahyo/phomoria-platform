package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.cloud.VoucherService;
import com.phomoria.theme.ThemeDialogSupport;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;

public final class VoucherDialog extends JDialog {
    private final JTextField codeField = new JTextField(22);
    private final JButton submit = new JButton("SELESAI");
    private final JButton cancel = new JButton("BATAL");
    private final JLabel status = new JLabel("Masukkan kode voucher untuk memulai sesi foto.", SwingConstants.CENTER);

    public VoucherDialog(JFrame owner, Runnable authorized) {
        super(owner, "Gunakan Voucher", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = ThemeDialogSupport.createPanel("voucher-dialog");
        ThemeDialogSupport.add(content, "voucher-dialog-title", new JLabel("", SwingConstants.CENTER),
                "GUNAKAN VOUCHER", 15, 12, 400, 32, true);
        ThemeDialogSupport.add(content, "voucher-dialog-code-label", new JLabel("Kode voucher", SwingConstants.LEFT),
                "Kode voucher", 25, 62, 95, 30, true);
        ThemeDialogSupport.add(content, "voucher-dialog-code-field", codeField,
                "", 125, 62, 280, 30, false);
        ThemeDialogSupport.add(content, "voucher-dialog-status", status,
                "Masukkan kode voucher untuk memulai sesi foto.", 15, 102, 400, 34, false);
        ThemeDialogSupport.add(content, "voucher-dialog-submit", submit,
                "SELESAI", 105, 148, 105, 32, true);
        ThemeDialogSupport.add(content, "voucher-dialog-cancel", cancel,
                "BATAL", 220, 148, 105, 32, true);
        setContentPane(content);
        setSize(430, 220);
        setResizable(false);
        setLocationRelativeTo(owner);

        submit.addActionListener(e -> authorize(authorized));
        cancel.addActionListener(e -> dispose());
        codeField.addActionListener(e -> authorize(authorized));
        getRootPane().setDefaultButton(submit);
    }

    private void authorize(Runnable authorized) {
        String code = codeField.getText() == null ? "" : codeField.getText().trim();
        if (code.isEmpty()) {
            status.setText("Kode voucher wajib diisi.");
            codeField.requestFocusInWindow();
            return;
        }

        setControlsEnabled(false);
        status.setText("Memeriksa voucher...");
        new SwingWorker<VoucherService.Authorization, Void>() {
            @Override protected VoucherService.Authorization doInBackground() throws Exception {
                return new VoucherService().authorize(code);
            }

            @Override protected void done() {
                try {
                    VoucherService.Authorization result = get();
                    AppContext.setVoucherAuthorizationToken(result.token());
                    dispose();
                    authorized.run();
                } catch (Exception ex) {
                    Throwable cause = ex;
                    while ((cause instanceof ExecutionException || cause.getCause() != null)
                            && cause.getCause() != null) cause = cause.getCause();
                    status.setText("<html><div style='text-align:center'>"
                            + escape(cause.getMessage() == null ? "Voucher gagal diverifikasi." : cause.getMessage())
                            + "</div></html>");
                    setControlsEnabled(true);
                    codeField.selectAll();
                    codeField.requestFocusInWindow();
                }
            }
        }.execute();
    }

    private void setControlsEnabled(boolean enabled) {
        submit.setEnabled(enabled);
        cancel.setEnabled(enabled);
        codeField.setEnabled(enabled);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
