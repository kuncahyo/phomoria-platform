package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.cloud.VoucherService;
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
        setSize(430, 220);
        setResizable(false);
        setLocationRelativeTo(owner);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        JLabel title = new JLabel("GUNAKAN VOUCHER", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 19));
        status.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.add(new JLabel("Kode voucher"), BorderLayout.NORTH);
        center.add(codeField, BorderLayout.CENTER);
        center.add(status, BorderLayout.SOUTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.add(submit);
        buttons.add(cancel);
        content.add(title, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

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
