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
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Frame picker with a large preview. Clicking a frame image only stages the
 * choice; the active session frame is changed after the user presses Continue.
 */
public final class FrameSelectionScreen extends JPanel {
    private static final Color BG = new Color(18, 18, 22);
    private static final Color CARD_BG = new Color(30, 30, 36);
    private static final Color ACCENT = new Color(70, 145, 255);

    private final ApplicationFrame frame;
    private final JComboBox<FrameCategory> categoryBox =
            new JComboBox<>(FrameCategory.values());
    private final JPanel presetPanel = new JPanel(new GridLayout(0, 2, 12, 12));
    private final JLabel largePreview = new JLabel("Pilih gambar frame di sebelah kanan", SwingConstants.CENTER);
    private final JLabel selectedName = new JLabel("Belum ada frame dipilih", SwingConstants.CENTER);
    private final JButton continueButton = new JButton("LANJUTKAN");

    private List<CachedFrameItem> cachedFrames = List.of();
    private CachedFrameItem stagedCachedFrame;
    private FramePreset stagedPreset;
    private final List<JPanel> frameCards = new ArrayList<>();
    private JPanel selectedCard;

    public FrameSelectionScreen(ApplicationFrame frame) {
        this.frame = frame;
        setLayout(new BorderLayout(14, 14));
        setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
        setBackground(BG);

        JLabel title = new JLabel("PILIH FRAME", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 30));

        categoryBox.setSelectedItem(FrameCategory.SPLIT);
        categoryBox.addActionListener(e -> refreshPresets());

        JPanel categoryPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        categoryPanel.setOpaque(false);
        JLabel categoryLabel = new JLabel("Kategori:");
        categoryLabel.setForeground(Color.WHITE);
        categoryPanel.add(categoryLabel);
        categoryPanel.add(categoryBox);

        JPanel header = new JPanel(new BorderLayout(8, 6));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(categoryPanel, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Large selected-frame preview on the left.
        JPanel previewPanel = new JPanel(new BorderLayout(8, 10));
        previewPanel.setBackground(CARD_BG);
        previewPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 80)),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        largePreview.setForeground(new Color(190, 190, 200));
        largePreview.setFont(new Font("SansSerif", Font.PLAIN, 18));
        largePreview.setPreferredSize(new Dimension(520, 620));
        selectedName.setForeground(Color.WHITE);
        selectedName.setFont(new Font("SansSerif", Font.BOLD, 20));
        previewPanel.add(largePreview, BorderLayout.CENTER);
        previewPanel.add(selectedName, BorderLayout.SOUTH);

        // Frame thumbnails on the right. Each thumbnail itself is clickable;
        // there are deliberately no per-card selection buttons.
        presetPanel.setOpaque(false);
        JScrollPane scroll = new JScrollPane(presetPanel);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);

        JPanel listPanel = new JPanel(new BorderLayout(8, 8));
        listPanel.setOpaque(false);
        JLabel listTitle = new JLabel("PILIH GAMBAR FRAME", SwingConstants.CENTER);
        listTitle.setForeground(Color.WHITE);
        listTitle.setFont(new Font("SansSerif", Font.BOLD, 17));
        listPanel.add(listTitle, BorderLayout.NORTH);
        listPanel.add(scroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, previewPanel, listPanel);
        split.setResizeWeight(0.56);
        split.setDividerLocation(0.56);
        split.setBorder(null);
        split.setOpaque(false);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        continueButton.setFont(new Font("SansSerif", Font.BOLD, 18));
        continueButton.setFocusPainted(false);
        continueButton.setEnabled(false);
        continueButton.addActionListener(e -> confirmSelection());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.add(continueButton);
        listPanel.add(footer, BorderLayout.SOUTH);

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
        DebugLog.info("Cloud frame selection cache loaded: " + cachedFrames.size() + " frame(s).");
    }

    private void refreshPresets() {
        presetPanel.removeAll();
        frameCards.clear();
        stagedCachedFrame = null;
        stagedPreset = null;
        selectedCard = null;
        largePreview.setIcon(null);
        largePreview.setText("Pilih gambar frame di sebelah kanan");
        selectedName.setText("Belum ada frame dipilih");
        continueButton.setEnabled(false);

        FrameCategory category = (FrameCategory) categoryBox.getSelectedItem();
        List<CachedFrameItem> matching = cachedFrames.stream()
                .filter(item -> matchesCategory(item.metadata(), category)).toList();

        if (!matching.isEmpty()) {
            DebugLog.info("Frame selection category=" + category + ", cached cloud frames=" + matching.size());
            for (CachedFrameItem item : matching) presetPanel.add(createCachedFrameCard(item));
        } else {
            // Keep the existing FrameCatalog fallback when no cloud frames are cached.
            List<FramePreset> presets = FrameCatalog.byCategory(category);
            DebugLog.info("No cached cloud frames for category=" + category
                    + ". Using FrameCatalog fallback, presets=" + presets.size());
            for (FramePreset preset : presets) presetPanel.add(createPresetCard(preset));
        }
        presetPanel.revalidate();
        presetPanel.repaint();
    }

    private boolean matchesCategory(FrameCache.CacheMetadata metadata, FrameCategory category) {
        return category == null
                || category.name().equalsIgnoreCase(metadata.getCategory())
                || category.getLabel().equalsIgnoreCase(metadata.getCategory());
    }

    private JPanel createCachedFrameCard(CachedFrameItem item) {
        FrameCache.CacheMetadata metadata = item.metadata();
        JPanel card = createCardShell();
        JLabel image = new JLabel(createPngPreview(item.png(), 210, 300));
        image.setHorizontalAlignment(SwingConstants.CENTER);
        image.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel name = createNameLabel(metadata.getName());
        JLabel info = new JLabel(metadata.getWidth() + " × " + metadata.getHeight()
                + "  •  " + metadata.getCategory(), SwingConstants.CENTER);
        info.setForeground(new Color(190, 190, 200));
        card.add(name, BorderLayout.NORTH);
        card.add(image, BorderLayout.CENTER);
        card.add(info, BorderLayout.SOUTH);
        addImageSelection(image, card, () -> stageCachedFrame(item, card));
        frameCards.add(card);
        return card;
    }

    private JPanel createPresetCard(FramePreset preset) {
        JPanel card = createCardShell();
        JLabel image = new JLabel(createSimulationPreview(preset, 210, 300));
        image.setHorizontalAlignment(SwingConstants.CENTER);
        image.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.add(createNameLabel(preset.name()), BorderLayout.NORTH);
        card.add(image, BorderLayout.CENTER);
        card.add(new JLabel(preset.category().getLabel(), SwingConstants.CENTER), BorderLayout.SOUTH);
        addImageSelection(image, card, () -> stagePreset(preset, card));
        frameCards.add(card);
        return card;
    }

    private JPanel createCardShell() {
        JPanel card = new JPanel(new BorderLayout(6, 8));
        card.setBackground(CARD_BG);
        card.setBorder(normalCardBorder());
        return card;
    }

    private Border normalCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 80), 1),
                BorderFactory.createEmptyBorder(9, 9, 9, 9));
    }

    private Border selectedCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT, 3),
                BorderFactory.createEmptyBorder(7, 7, 7, 7));
    }

    private JLabel createNameLabel(String name) {
        JLabel label = new JLabel(name, SwingConstants.CENTER);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("SansSerif", Font.BOLD, 16));
        return label;
    }

    private void addImageSelection(JLabel image, JPanel card, Runnable selection) {
        image.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { selection.run(); }
            @Override public void mouseEntered(MouseEvent e) {
                if (!card.getBorder().equals(selectedCardBorder())) {
                    card.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(125, 125, 145), 2),
                            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
                }
            }
            @Override public void mouseExited(MouseEvent e) {
                updateCardBorders();
            }
        });
    }

    private void stageCachedFrame(CachedFrameItem item, JPanel card) {
        stagedCachedFrame = item;
        selectedCard = card;
        stagedPreset = null;
        showLargePreview(createPngPreview(item.png(), 620, 760), item.metadata().getName());
        updateCardBorders();
    }

    private void stagePreset(FramePreset preset, JPanel card) {
        stagedPreset = preset;
        selectedCard = card;
        stagedCachedFrame = null;
        showLargePreview(createSimulationPreview(preset, 620, 760), preset.name());
        updateCardBorders();
    }

    private void showLargePreview(ImageIcon icon, String name) {
        largePreview.setText(null);
        largePreview.setIcon(icon);
        selectedName.setText(name);
        continueButton.setEnabled(true);
    }

    private void updateCardBorders() {
        for (JPanel card : frameCards) card.setBorder(normalCardBorder());
        if (selectedCard != null) selectedCard.setBorder(selectedCardBorder());
        presetPanel.repaint();
    }

    private void confirmSelection() {
        if (stagedCachedFrame != null) {
            FrameCache.CacheMetadata metadata = stagedCachedFrame.metadata();
            AppContext.settings().setSelectedFrameId(Long.toString(metadata.getId()));
            AppContext.settings().setFrameName(metadata.getName());
            AppContext.saveSettings();
            DebugLog.info("Cloud frame confirmed: id=" + metadata.getId() + " / " + metadata.getName());
        } else if (stagedPreset != null) {
            AppContext.settings().setSelectedFrameId(stagedPreset.id());
            AppContext.settings().setFrameName(stagedPreset.name());
            AppContext.saveSettings();
            DebugLog.info("Fallback frame confirmed: " + stagedPreset.id() + " / " + stagedPreset.name());
        } else {
            return;
        }
        frame.startPaidSession();
    }

    private ImageIcon createPngPreview(Path png, int maxW, int maxH) {
        try {
            BufferedImage source = ImageIO.read(png.toFile());
            if (source == null) return createBrokenPreview("PNG tidak dapat dibaca", maxW, maxH);
            return scaleImage(source, maxW, maxH);
        } catch (IOException | RuntimeException ex) {
            DebugLog.warn("Failed to create frame PNG preview: " + ex.getMessage());
            return createBrokenPreview("Preview gagal", maxW, maxH);
        }
    }

    private ImageIcon createBrokenPreview(String message, int maxW, int maxH) {
        BufferedImage image = new BufferedImage(maxW, maxH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(45, 45, 52));
        g.fillRect(0, 0, maxW, maxH);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(message, Math.max(8, (maxW - metrics.stringWidth(message)) / 2), maxH / 2);
        g.dispose();
        return new ImageIcon(image);
    }

    private ImageIcon createSimulationPreview(FramePreset preset, int maxW, int maxH) {
        FrameDefinition definition = FrameCatalog.createDefinition(
                preset, AppContext.settings().getPhotoSlotCount());
        BufferedImage image = new BufferedImage(maxW, maxH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, maxW, maxH);
        g.setColor(new Color(25, 25, 30));
        g.setStroke(new BasicStroke(Math.max(2f, maxW / 130f)));
        for (var placement : definition.getPlacements()) {
            int x = (int) Math.round(placement.x() * maxW);
            int y = (int) Math.round(placement.y() * maxH);
            int pw = Math.max(2, (int) Math.round(placement.width() * maxW));
            int ph = Math.max(2, (int) Math.round(placement.height() * maxH));
            g.drawRect(x, y, pw, ph);
        }
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(14, maxW / 18)));
        g.drawString(preset.category().getLabel(), 12, maxH - 15);
        g.dispose();
        return new ImageIcon(image);
    }

    private ImageIcon scaleImage(BufferedImage source, int maxW, int maxH) {
        double scale = Math.min((double) maxW / source.getWidth(), (double) maxH / source.getHeight());
        scale = Math.min(1.0, scale);
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage preview = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = preview.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setComposite(AlphaComposite.Src);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return new ImageIcon(preview);
    }

    private record CachedFrameItem(FrameCache.CacheMetadata metadata, Path png) {}
}
