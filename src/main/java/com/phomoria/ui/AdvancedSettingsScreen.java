package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.app.ApplicationFrame;
import com.phomoria.config.AppSettings;
import com.phomoria.debug.DebugLog;
import com.phomoria.debug.PerformanceDiagnostics;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class AdvancedSettingsScreen extends JPanel {
    private final ApplicationFrame frame;
    private final Runnable back;
    private final JCheckBox enabled;
    private final JComboBox<String> duration;
    private final JCheckBox cpu;
    private final JCheckBox gc;
    private final DefaultListModel<File> logModel = new DefaultListModel<>();
    private final JList<File> logList = new JList<>(logModel);

    public AdvancedSettingsScreen(ApplicationFrame frame, Runnable back) {
        this.frame = frame;
        this.back = back;
        AppSettings settings = AppContext.settings();
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));

        JLabel title = new JLabel("ADVANCED SETTINGS");
        title.setFont(new Font("SansSerif", Font.BOLD, 25));
        add(title, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel diagnostics = new JPanel();
        diagnostics.setLayout(new BoxLayout(diagnostics, BoxLayout.Y_AXIS));
        diagnostics.setBorder(BorderFactory.createTitledBorder("Performance Diagnostics"));

        enabled = new JCheckBox("Aktifkan pencatatan diagnostik setiap sesi berakhir",
                settings.isPerformanceDiagnosticsEnabled());
        duration = new JComboBox<>(new String[]{"7 hari", "30 hari", "Sampai dimatikan manual"});
        int days = settings.getPerformanceDiagnosticsDurationDays();
        duration.setSelectedIndex(days == 30 ? 1 : days == 0 ? 2 : 0);
        cpu = new JCheckBox("Catat penggunaan CPU (opsional)", settings.isPerformanceDiagnosticsCpuEnabled());
        gc = new JCheckBox("Catat aktivitas Garbage Collector (opsional)", settings.isPerformanceDiagnosticsGcEnabled());

        diagnostics.add(enabled);
        JPanel durationRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        durationRow.add(new JLabel("Masa aktif:"));
        durationRow.add(duration);
        diagnostics.add(durationRow);
        diagnostics.add(cpu);
        diagnostics.add(gc);
        diagnostics.add(new JLabel("Data inti: heap, RAM sistem, Working Set, Private Memory, thread, durasi sesi, Live Photo, dan status upload."));
        diagnostics.add(new JLabel("Snapshot hanya dibuat saat sesi berakhir; tidak ada polling berkala atau forced GC."));
        diagnostics.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(diagnostics);

        JPanel logs = new JPanel(new BorderLayout(8, 8));
        logs.setBorder(BorderFactory.createTitledBorder("Application Logs"));
        logList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                File file = (File) value;
                String label = file.getName() + "  —  " + new java.util.Date(file.lastModified());
                return super.getListCellRendererComponent(list, label, index, selected, focus);
            }
        });
        logs.add(new JScrollPane(logList), BorderLayout.CENTER);
        JButton refresh = new JButton("REFRESH");
        JButton open = new JButton("BUKA DI NOTEPAD");
        JPanel logButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        logButtons.add(refresh);
        logButtons.add(open);
        logs.add(logButtons, BorderLayout.SOUTH);
        logs.setPreferredSize(new Dimension(700, 320));
        logs.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(Box.createVerticalStrut(12));
        content.add(logs);

        add(new JScrollPane(content), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("BACK");
        JButton save = new JButton("SAVE");
        bottom.add(cancel);
        bottom.add(save);
        add(bottom, BorderLayout.SOUTH);

        refresh.addActionListener(e -> refreshLogs());
        open.addActionListener(e -> openSelectedLog());
        cancel.addActionListener(e -> back.run());
        save.addActionListener(e -> saveSettings(settings));
        refreshLogs();
    }

    private void saveSettings(AppSettings settings) {
        settings.setPerformanceDiagnosticsDurationDays(switch (duration.getSelectedIndex()) {
            case 1 -> 30;
            case 2 -> 0;
            default -> 7;
        });
        settings.setPerformanceDiagnosticsCpuEnabled(cpu.isSelected());
        settings.setPerformanceDiagnosticsGcEnabled(gc.isSelected());
        boolean wasEnabled = settings.isPerformanceDiagnosticsEnabled();
        settings.setPerformanceDiagnosticsEnabled(enabled.isSelected());
        if (enabled.isSelected() && wasEnabled) {
            // Preserve the original enable timestamp when only options are changed.
        }
        AppContext.saveSettings();
        DebugLog.info("Performance diagnostics settings saved. enabled=" + enabled.isSelected()
                + ", durationDays=" + settings.getPerformanceDiagnosticsDurationDays());
        JOptionPane.showMessageDialog(this, "Advanced settings tersimpan.", "Phomoria",
                JOptionPane.INFORMATION_MESSAGE);
        back.run();
    }

    private void refreshLogs() {
        logModel.clear();
        File directory = DebugLog.getLogDirectory().toFile();
        if (!directory.isDirectory()) return;
        try (Stream<Path> files = Files.list(directory.toPath())) {
            List<File> logs = files.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(file -> file.getName().matches("phomoria-\\d{4}-\\d{2}-\\d{2}\\.log"))
                    .sorted(Comparator.comparingLong(File::lastModified).reversed())
                    .limit(30).toList();
            logs.forEach(logModel::addElement);
        } catch (Exception ex) {
            DebugLog.error("Could not list application log files.", ex);
        }
    }

    private void openSelectedLog() {
        File file = logList.getSelectedValue();
        if (file == null) {
            JOptionPane.showMessageDialog(this, "Pilih file log terlebih dahulu.", "Application Logs",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            new ProcessBuilder("notepad.exe", file.getAbsolutePath()).start();
        } catch (Exception ex) {
            DebugLog.error("Could not open application log in Notepad.", ex);
            JOptionPane.showMessageDialog(this, "Gagal membuka log: " + ex.getMessage(),
                    "Application Logs", JOptionPane.ERROR_MESSAGE);
        }
    }
}
