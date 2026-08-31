package com.phomoria.debug;

import javax.swing.*;
import java.awt.*;

public final class DebugConsoleDialog extends JDialog {
    private final JTextArea output = new JTextArea();

    public DebugConsoleDialog(JFrame owner) {
        super(owner, "Phomoria Backdoor / Activity Log", false);

        setSize(900, 560);
        setLocationRelativeTo(owner);

        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        output.setBackground(new Color(15, 15, 18));
        output.setForeground(new Color(220, 220, 220));
        output.setLineWrap(false);

        JButton clear = new JButton("CLEAR");
        clear.addActionListener(e -> output.setText(""));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(clear);

        add(new JScrollPane(output), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        DebugLog.attachConsole(this);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                DebugLog.detachConsole(DebugConsoleDialog.this);
            }
        });
    }

    public void append(String line) {
        SwingUtilities.invokeLater(() -> {
            output.append(line);
            output.append(System.lineSeparator());
            output.setCaretPosition(output.getDocument().getLength());
        });
    }
}
