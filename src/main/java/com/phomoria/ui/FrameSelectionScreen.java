package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.app.ApplicationFrame;
import com.phomoria.debug.DebugLog;
import com.phomoria.frame.FrameCatalog;
import com.phomoria.frame.FrameCategory;
import com.phomoria.frame.FrameDefinition;
import com.phomoria.frame.FramePreset;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class FrameSelectionScreen extends JPanel {
    private final ApplicationFrame frame;
    private final JComboBox<FrameCategory> categoryBox = new JComboBox<>(FrameCategory.values());
    private final JPanel presetPanel = new JPanel(new GridLayout(0, 2, 16, 16));
    private FramePreset selectedPreset;

    public FrameSelectionScreen(ApplicationFrame frame) {
        this.frame = frame;
        setLayout(new BorderLayout(16, 16));
        setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        setBackground(new Color(18, 18, 22));
        JLabel title = new JLabel("PILIH FRAME", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        add(title, BorderLayout.NORTH);
        categoryBox.setSelectedItem(FrameCategory.SPLIT);
        categoryBox.addActionListener(e -> refreshPresets());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        top.setOpaque(false);
        JLabel categoryLabel = new JLabel("Kategori:");
        categoryLabel.setForeground(Color.WHITE);
        top.add(categoryLabel); top.add(categoryBox);
        add(top, BorderLayout.NORTH);
        presetPanel.setOpaque(false);
        add(new JScrollPane(presetPanel) {{ setBorder(null); getViewport().setOpaque(false); setOpaque(false); }}, BorderLayout.CENTER);
        refreshPresets();
    }
    private void refreshPresets() {
        presetPanel.removeAll();
        FrameCategory category = (FrameCategory) categoryBox.getSelectedItem();
        List<FramePreset> presets = FrameCatalog.byCategory(category);
        DebugLog.info("Frame selection category=" + category + ", presets=" + presets.size());
        for (FramePreset preset : presets) presetPanel.add(createPresetCard(preset));
        presetPanel.revalidate(); presetPanel.repaint();
    }
    private JPanel createPresetCard(FramePreset preset) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(new Color(30, 30, 36));
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(70, 70, 80)), BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        JLabel preview = new JLabel(createSimulationPreview(preset));
        preview.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel name = new JLabel(preset.name(), SwingConstants.CENTER);
        name.setForeground(Color.WHITE); name.setFont(new Font("SansSerif", Font.BOLD, 18));
        JButton select = new JButton("GUNAKAN FRAME INI");
        select.addActionListener(e -> selectPreset(preset));
        card.add(preview, BorderLayout.CENTER); card.add(name, BorderLayout.NORTH); card.add(select, BorderLayout.SOUTH);
        return card;
    }
    private ImageIcon createSimulationPreview(FramePreset preset) {
        FrameDefinition definition = FrameCatalog.createDefinition(preset, AppContext.settings().getPhotoSlotCount());
        int w = 260, h = 390;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, w, h);
        g.setColor(new Color(25, 25, 30)); g.setStroke(new BasicStroke(3));
        for (var placement : definition.getPlacements()) {
            int x = (int)Math.round(placement.x()*w), y = (int)Math.round(placement.y()*h);
            int pw = Math.max(2, (int)Math.round(placement.width()*w));
            int ph = Math.max(2, (int)Math.round(placement.height()*h));
            g.drawRect(x, y, pw, ph);
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.drawString(preset.category().getLabel(), 12, h-15);
        g.dispose();
        return new ImageIcon(image);
    }
    private void selectPreset(FramePreset preset) {
        selectedPreset = preset;
        AppContext.settings().setSelectedFrameId(preset.id());
        AppContext.settings().setFrameName(preset.name());
        AppContext.saveSettings();
        DebugLog.info("Frame selected: " + preset.id() + " / " + preset.name());
        JOptionPane.showMessageDialog(this, "Frame dipilih: " + preset.name());
        frame.startPaidSession();
    }
}
