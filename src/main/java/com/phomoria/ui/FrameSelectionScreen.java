package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.app.ApplicationFrame;
import com.phomoria.cloud.FrameCache;
import com.phomoria.debug.DebugLog;
import com.phomoria.frame.FrameCatalog;
import com.phomoria.frame.FrameCategory;
import com.phomoria.frame.FrameDefinition;
import com.phomoria.frame.FramePreset;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * V22.8 - Frame selection from the local cloud cache.
 *
 * Cached cloud frames are displayed using their real PNG assets.
 * FrameCatalog remains as a temporary fallback so the selection screen
 * is still usable when no cloud frame has been synchronized yet.
 */
public final class FrameSelectionScreen extends JPanel {
    private final ApplicationFrame frame;
    private final JComboBox<FrameCategory> categoryBox =
            new JComboBox<>(FrameCategory.values());
    private final JPanel presetPanel =
            new JPanel(new GridLayout(0, 2, 16, 16));

    private List<CachedFrameItem> cachedFrames = List.of();

    public FrameSelectionScreen(ApplicationFrame frame) {
        this.frame = frame;

        setLayout(new BorderLayout(16, 16));
        setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        setBackground(new Color(18, 18, 22));

        JLabel title = new JLabel("PILIH FRAME", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 30));

        categoryBox.setSelectedItem(FrameCategory.SPLIT);
        categoryBox.addActionListener(e -> refreshPresets());

        JPanel header = new JPanel(new BorderLayout(10, 8));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);

        JPanel categoryPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        categoryPanel.setOpaque(false);
        JLabel categoryLabel = new JLabel("Kategori:");
        categoryLabel.setForeground(Color.WHITE);
        categoryPanel.add(categoryLabel);
        categoryPanel.add(categoryBox);
        header.add(categoryPanel, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);

        presetPanel.setOpaque(false);
        JScrollPane scroll = new JScrollPane(presetPanel);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        loadCachedFrames();
        refreshPresets();
    }

    private void loadCachedFrames() {
        List<CachedFrameItem> result = new ArrayList<>();

        try {
            for (Long id : FrameCache.listCachedFrameIds()) {
                FrameCache.CacheMetadata metadata = FrameCache.readMetadata(id);
                Path png = FrameCache.getPng(id);

                if (metadata == null || png == null) {
                    DebugLog.warn("Skipping incomplete cached frame: id=" + id);
                    continue;
                }

                if (!"ACTIVE".equalsIgnoreCase(metadata.getStatus())) {
                    DebugLog.info("Skipping inactive cached frame: id=" + id
                            + ", status=" + metadata.getStatus());
                    continue;
                }

                result.add(new CachedFrameItem(metadata, png));
            }
        } catch (IOException ex) {
            DebugLog.warn("Failed to load cached frames: " + ex.getMessage());
        }

        result.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                a.metadata().getName(), b.metadata().getName()));

        cachedFrames = List.copyOf(result);

        DebugLog.info("Cloud frame selection cache loaded: "
                + cachedFrames.size() + " frame(s).");
    }

    private void refreshPresets() {
        presetPanel.removeAll();

        FrameCategory category = (FrameCategory) categoryBox.getSelectedItem();
        List<CachedFrameItem> matching = cachedFrames.stream()
                .filter(item -> matchesCategory(item.metadata(), category))
                .toList();

        if (!matching.isEmpty()) {
            DebugLog.info("Frame selection category=" + category
                    + ", cached cloud frames=" + matching.size());

            for (CachedFrameItem item : matching) {
                presetPanel.add(createCachedFrameCard(item));
            }
        } else {
            // Temporary compatibility fallback until cloud sync has populated the cache.
            List<FramePreset> presets = FrameCatalog.byCategory(category);
            DebugLog.info("No cached cloud frames for category=" + category
                    + ". Using FrameCatalog fallback, presets=" + presets.size());

            for (FramePreset preset : presets) {
                presetPanel.add(createPresetCard(preset));
            }
        }

        presetPanel.revalidate();
        presetPanel.repaint();
    }

    private boolean matchesCategory(
            FrameCache.CacheMetadata metadata,
            FrameCategory category
    ) {
        if (category == null) {
            return true;
        }

        return category.name().equalsIgnoreCase(metadata.getCategory())
                || category.getLabel().equalsIgnoreCase(metadata.getCategory());
    }

    private JPanel createCachedFrameCard(CachedFrameItem item) {
        FrameCache.CacheMetadata metadata = item.metadata();

        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(new Color(30, 30, 36));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 80)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JLabel preview = new JLabel(createPngPreview(item.png()));
        preview.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel name = new JLabel(metadata.getName(), SwingConstants.CENTER);
        name.setForeground(Color.WHITE);
        name.setFont(new Font("SansSerif", Font.BOLD, 18));

        JLabel info = new JLabel(
                metadata.getWidth() + " × " + metadata.getHeight()
                        + "  •  " + metadata.getCategory(),
                SwingConstants.CENTER);
        info.setForeground(new Color(190, 190, 200));

        JButton select = new JButton("GUNAKAN FRAME INI");
        select.addActionListener(e -> selectCachedFrame(item));

        JPanel north = new JPanel(new BorderLayout(4, 2));
        north.setOpaque(false);
        north.add(name, BorderLayout.NORTH);
        north.add(info, BorderLayout.SOUTH);

        card.add(north, BorderLayout.NORTH);
        card.add(preview, BorderLayout.CENTER);
        card.add(select, BorderLayout.SOUTH);

        return card;
    }

    private ImageIcon createPngPreview(Path png) {
        try {
            BufferedImage source = ImageIO.read(png.toFile());
            if (source == null) {
                return createBrokenPreview("PNG tidak dapat dibaca");
            }

            int maxW = 260;
            int maxH = 390;
            double scale = Math.min(
                    (double) maxW / source.getWidth(),
                    (double) maxH / source.getHeight());
            scale = Math.min(1.0, scale);

            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

            BufferedImage preview = new BufferedImage(
                    width,
                    height,
                    BufferedImage.TYPE_INT_ARGB);

            Graphics2D g = preview.createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.drawImage(source, 0, 0, width, height, null);
            g.dispose();

            return new ImageIcon(preview);
        } catch (IOException | RuntimeException ex) {
            DebugLog.warn("Failed to create frame PNG preview: " + ex.getMessage());
            return createBrokenPreview("Preview gagal");
        }
    }

    private ImageIcon createBrokenPreview(String message) {
        BufferedImage image = new BufferedImage(
                260, 390, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(45, 45, 52));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        FontMetrics metrics = g.getFontMetrics();
        int x = (image.getWidth() - metrics.stringWidth(message)) / 2;
        g.drawString(message, Math.max(8, x), image.getHeight() / 2);
        g.dispose();
        return new ImageIcon(image);
    }

    private JPanel createPresetCard(FramePreset preset) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(new Color(30, 30, 36));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 80)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JLabel preview = new JLabel(createSimulationPreview(preset));
        preview.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel name = new JLabel(preset.name(), SwingConstants.CENTER);
        name.setForeground(Color.WHITE);
        name.setFont(new Font("SansSerif", Font.BOLD, 18));

        JButton select = new JButton("GUNAKAN FRAME INI");
        select.addActionListener(e -> selectPreset(preset));

        card.add(preview, BorderLayout.CENTER);
        card.add(name, BorderLayout.NORTH);
        card.add(select, BorderLayout.SOUTH);
        return card;
    }

    private ImageIcon createSimulationPreview(FramePreset preset) {
        FrameDefinition definition = FrameCatalog.createDefinition(
                preset,
                AppContext.settings().getPhotoSlotCount());

        int w = 260;
        int h = 390;
        BufferedImage image = new BufferedImage(
                w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(25, 25, 30));
        g.setStroke(new BasicStroke(3));

        for (var placement : definition.getPlacements()) {
            int x = (int) Math.round(placement.x() * w);
            int y = (int) Math.round(placement.y() * h);
            int pw = Math.max(2, (int) Math.round(placement.width() * w));
            int ph = Math.max(2, (int) Math.round(placement.height() * h));
            g.drawRect(x, y, pw, ph);
        }

        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.drawString(preset.category().getLabel(), 12, h - 15);
        g.dispose();
        return new ImageIcon(image);
    }

    private void selectCachedFrame(CachedFrameItem item) {
        FrameCache.CacheMetadata metadata = item.metadata();

        AppContext.settings().setSelectedFrameId(Long.toString(metadata.getId()));
        AppContext.settings().setFrameName(metadata.getName());
        AppContext.saveSettings();

        DebugLog.info("Cloud frame selected: id=" + metadata.getId()
                + " / " + metadata.getName());

        JOptionPane.showMessageDialog(
                this,
                "Frame dipilih: " + metadata.getName());

        frame.startPaidSession();
    }

    private void selectPreset(FramePreset preset) {
        AppContext.settings().setSelectedFrameId(preset.id());
        AppContext.settings().setFrameName(preset.name());
        AppContext.saveSettings();

        DebugLog.info("Fallback frame selected: " + preset.id()
                + " / " + preset.name());

        JOptionPane.showMessageDialog(
                this,
                "Frame dipilih: " + preset.name());

        frame.startPaidSession();
    }

    private record CachedFrameItem(
            FrameCache.CacheMetadata metadata,
            Path png
    ) {
    }
}
