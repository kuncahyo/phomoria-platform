package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.app.ApplicationFrame;
import com.phomoria.cloud.FrameCache;
import com.phomoria.debug.DebugLog;
import com.phomoria.frame.FrameCatalog;
import com.phomoria.frame.FrameDefinition;
import com.phomoria.frame.FramePreset;
import com.phomoria.theme.ThemeBackgroundPanel;
import com.phomoria.theme.ThemeButton;
import com.phomoria.theme.ThemeConfig;
import com.phomoria.theme.ThemeManager;

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
    private static final String DRAG_SCROLL_INSTALLED = "phomoria.frameSelection.dragScrollInstalled";
    private static final int DRAG_THRESHOLD = 7;

    private final ApplicationFrame frame;
    private final JPanel presetPanel = new JPanel(new GridLayout(0, 2, 12, 12));
    private final JLabel largePreview = new JLabel("Pilih gambar frame di sebelah kanan", SwingConstants.CENTER);
    private final JLabel selectedName = new JLabel("Belum ada frame dipilih", SwingConstants.CENTER);
    private final JLabel pageTitle = new JLabel("PILIH FRAME", SwingConstants.CENTER);
    private final JLabel listTitle = new JLabel("PILIH GAMBAR FRAME", SwingConstants.CENTER);
    private final ThemeButton continueButton = new ThemeButton("frame-selection-continue", "LANJUTKAN");
    private final ThemeBackgroundPanel themedBackground = new ThemeBackgroundPanel("frame-selection", 0f);
    private final JPanel previewPanel = new JPanel(new BorderLayout(8, 10));
    private final JPanel listPanel = new JPanel(new BorderLayout(8, 8));
    private final JScrollPane frameScroll = new JScrollPane(presetPanel);

    private List<CachedFrameItem> cachedFrames = List.of();
    private CachedFrameItem stagedCachedFrame;
    private FramePreset stagedPreset;
    private final List<JPanel> frameCards = new ArrayList<>();
    private JPanel selectedCard;
    private ImageIcon selectedPreviewSource;

    // State for touch/mouse drag scrolling inside the frame list.
    private Point dragStartInView;
    private Point dragStartViewportPosition;
    private boolean frameListDragActive;
    private boolean suppressNextFrameClick;

    public FrameSelectionScreen(ApplicationFrame frame) {
        this.frame = frame;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(BG);
        themedBackground.setLayout(null);
        themedBackground.setBorder(null);

        pageTitle.setForeground(Color.WHITE);
        pageTitle.setFont(new Font("SansSerif", Font.BOLD, 30));
        previewPanel.setOpaque(false);
        previewPanel.setBorder(null);
        largePreview.setForeground(new Color(190, 190, 200));
        largePreview.setOpaque(false);
        largePreview.setFont(new Font("SansSerif", Font.PLAIN, 18));
        selectedName.setForeground(Color.WHITE);
        selectedName.setFont(new Font("SansSerif", Font.BOLD, 20));
        selectedName.setHorizontalAlignment(SwingConstants.CENTER);
        selectedName.setOpaque(false);
        previewPanel.add(largePreview, BorderLayout.CENTER);

        presetPanel.setOpaque(false);
        frameScroll.setBorder(null);
        frameScroll.getViewport().setOpaque(false);
        frameScroll.setOpaque(false);
        frameScroll.getVerticalScrollBar().setUnitIncrement(24);
        listPanel.setOpaque(false);
        listPanel.setLayout(new BorderLayout(8, 8));
        listTitle.setForeground(Color.WHITE);
        listTitle.setFont(new Font("SansSerif", Font.BOLD, 17));
        listTitle.setOpaque(false);
        listPanel.add(frameScroll, BorderLayout.CENTER);

        themedBackground.add(pageTitle);
        themedBackground.add(previewPanel);
        themedBackground.add(listPanel);
        themedBackground.add(listTitle);
        themedBackground.add(selectedName);
        continueButton.setFont(new Font("SansSerif", Font.BOLD, 18));
        continueButton.setFocusPainted(false);
        continueButton.setEnabled(false);
        continueButton.addActionListener(e -> confirmSelection());
        themedBackground.add(continueButton);

        themedBackground.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                applyThemeGeometry();
                updateCardGrid();
            }
        });
        frameScroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { updateCardGrid(); }
        });
        previewPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { updateLargePreviewIcon(); }
        });
        add(themedBackground, BorderLayout.CENTER);
        installDragToScroll(frameScroll.getViewport());
        installDragToScroll(presetPanel);
        loadCachedFrames();
        refreshPresets();
        applyThemeGeometry();
    }

    private void applyThemeGeometry() {
        ThemeConfig config = ThemeManager.config();
        if (config == null || themedBackground.getWidth() <= 0 || themedBackground.getHeight() <= 0) return;
        double sx = themedBackground.getWidth() / (double) config.designWidth;
        double sy = themedBackground.getHeight() / (double) config.designHeight;
        setElementBounds(pageTitle, "frame-selection-header", 0, 0, 1920, 90, sx, sy, config);
        setElementBounds(previewPanel, "frame-selection-preview", 70, 175, 820, 790, sx, sy, config);
        setElementBounds(listPanel, "frame-selection-list", 930, 175, 920, 790, sx, sy, config);
        setElementBounds(listTitle, "frame-selection-list-title", 980, 190, 820, 52, sx, sy, config);
        setElementBounds(selectedName, "frame-selection-selected-name", 70, 900, 820, 48, sx, sy, config);
        applyTextStyle(pageTitle, config.elements.get("frame-selection-header"), "PILIH FRAME");
        applyTextStyle(listTitle, config.elements.get("frame-selection-list-title"), "PILIH DESAIN FRAME");
        ThemeConfig.Element selectedNameStyle = config.elements.get("frame-selection-selected-name");
        selectedName.setVisible(selectedNameStyle != null && Boolean.TRUE.equals(selectedNameStyle.visible));
        if (selectedNameStyle != null) selectedName.setForeground(parseColor(selectedNameStyle.foreground, Color.WHITE));
        ThemeConfig.Element button = config.elements.get("frame-selection-continue");
        if (button != null) {
            if (continueButton.getIcon() == null) {
                continueButton.setText(button.text == null || button.text.isBlank() ? "LANJUTKAN" : button.text);
            }
            continueButton.setForeground(parseColor(button.foreground, Color.WHITE));
            continueButton.setVisible(Boolean.TRUE.equals(button.visible));
            continueButton.setBounds(
                (int)Math.round(button.x*sx), (int)Math.round(button.y*sy),
                Math.max(1,(int)Math.round(button.width*sx)), Math.max(1,(int)Math.round(button.height*sy)));
        }
        themedBackground.revalidate();
        themedBackground.repaint();
    }

    private void applyTextStyle(JLabel label, ThemeConfig.Element element, String fallback) {
        if (element == null) return;
        label.setText(element.text == null || element.text.isBlank() ? fallback : element.text);
        label.setVisible(Boolean.TRUE.equals(element.visible));
        label.setForeground(parseColor(element.foreground, Color.WHITE));
    }

    private Color parseColor(String value, Color fallback) {
        try { return value == null ? fallback : Color.decode(value); }
        catch (RuntimeException ex) { return fallback; }
    }

    private void setElementBounds(Component component, String id, int dx, int dy, int dw, int dh,
                                  double sx, double sy, ThemeConfig config) {
        ThemeConfig.Element e = config.elements.get(id);
        int x = e == null ? dx : e.x;
        int y = e == null ? dy : e.y;
        int w = e == null ? dw : e.width;
        int h = e == null ? dh : e.height;
        component.setBounds((int)Math.round(x*sx), (int)Math.round(y*sy),
                Math.max(1,(int)Math.round(w*sx)), Math.max(1,(int)Math.round(h*sy)));
    }

    private void updateCardGrid() {
        int available = Math.max(1, frameScroll.getViewport().getWidth());
        int columns = Math.max(1, Math.min(4, available / 230));
        presetPanel.setLayout(new GridLayout(0, columns, 12, 12));
        presetPanel.revalidate();
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
        selectedPreviewSource = null;
        largePreview.setText("Pilih gambar frame di sebelah kanan");
        selectedName.setText("Belum ada frame dipilih");
        continueButton.setEnabled(false);

        if (!cachedFrames.isEmpty()) {
            DebugLog.info("Frame selection: displaying all device-owned cached frames=" + cachedFrames.size());
            for (CachedFrameItem item : cachedFrames) presetPanel.add(createCachedFrameCard(item));
        } else {
            List<FramePreset> presets = new ArrayList<>();
            for (var category : com.phomoria.frame.FrameCategory.values()) {
                presets.addAll(FrameCatalog.byCategory(category));
            }
            DebugLog.info("No cached cloud frames. Using all local FrameCatalog presets=" + presets.size());
            for (FramePreset preset : presets) presetPanel.add(createPresetCard(preset));
        }
        // Cards are created after the panel's initial listener installation.
        // Install on each new card so its labels and image also receive drag gestures.
        for (Component child : presetPanel.getComponents()) {
            installDragToScroll(child);
        }
        updateCardGrid();
        presetPanel.revalidate();
        presetPanel.repaint();
    }

    private JPanel createCachedFrameCard(CachedFrameItem item) {
        FrameCache.CacheMetadata metadata = item.metadata();
        JPanel card = createCardShell();
        JLabel image = new JLabel(createPngPreview(item.png(), 190, 270));
        image.setOpaque(false);
        image.setHorizontalAlignment(SwingConstants.CENTER);
        image.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel name = createNameLabel(metadata.getName());
        JLabel info = new JLabel(metadata.getWidth() + " × " + metadata.getHeight()
                + "  •  " + metadata.getCategory(), SwingConstants.CENTER);
        applyCardTextStyle(name, "frame-selection-card-name");
        applyCardTextStyle(info, "frame-selection-card-info");
        card.add(name, BorderLayout.NORTH);
        card.add(image, BorderLayout.CENTER);
        card.add(info, BorderLayout.SOUTH);
        addImageSelection(image, card, () -> stageCachedFrame(item, card));
        frameCards.add(card);
        return card;
    }

    private JPanel createPresetCard(FramePreset preset) {
        JPanel card = createCardShell();
        JLabel image = new JLabel(createSimulationPreview(preset, 190, 270));
        image.setOpaque(false);
        image.setHorizontalAlignment(SwingConstants.CENTER);
        image.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel name = createNameLabel(preset.name());
        JLabel info = new JLabel(preset.category().getLabel(), SwingConstants.CENTER);
        applyCardTextStyle(name, "frame-selection-card-name");
        applyCardTextStyle(info, "frame-selection-card-info");
        card.add(name, BorderLayout.NORTH);
        card.add(image, BorderLayout.CENTER);
        card.add(info, BorderLayout.SOUTH);
        addImageSelection(image, card, () -> stagePreset(preset, card));
        frameCards.add(card);
        return card;
    }

    private void applyCardTextStyle(JLabel label, String elementId) {
        ThemeConfig.Element style = ThemeManager.config().elements.get(elementId);
        label.setVisible(style != null && Boolean.TRUE.equals(style.visible));
        if (style != null) label.setForeground(parseColor(style.foreground, Color.WHITE));
    }

    private JPanel createCardShell() {
        JPanel card = new JPanel(new BorderLayout(6, 8));
        card.setOpaque(false);
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
        label.setOpaque(false);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("SansSerif", Font.BOLD, 16));
        return label;
    }

    private void addImageSelection(JLabel image, JPanel card, Runnable selection) {
        image.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (suppressNextFrameClick) {
                    suppressNextFrameClick = false;
                    return;
                }
                selection.run();
            }
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

    /**
     * Installs touch-style vertical drag scrolling on a component and all its
     * descendants. This lets the gesture start on cards, labels, images or
     * empty space within the frame list.
     */
    private void installDragToScroll(Component component) {
        if (component instanceof JComponent swingComponent) {
            if (Boolean.TRUE.equals(swingComponent.getClientProperty(DRAG_SCROLL_INSTALLED))) return;
            swingComponent.putClientProperty(DRAG_SCROLL_INSTALLED, Boolean.TRUE);
        }

        MouseAdapter dragHandler = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                suppressNextFrameClick = false;
                Component source = (Component) e.getSource();
                dragStartInView = SwingUtilities.convertPoint(source, e.getPoint(), presetPanel);
                dragStartViewportPosition = frameScroll.getViewport().getViewPosition();
                frameListDragActive = true;
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (!frameListDragActive || dragStartInView == null
                        || dragStartViewportPosition == null) return;
                Component source = (Component) e.getSource();
                Point currentInView = SwingUtilities.convertPoint(source, e.getPoint(), presetPanel);
                int deltaX = currentInView.x - dragStartInView.x;
                int deltaY = currentInView.y - dragStartInView.y;
                if (!suppressNextFrameClick
                        && Math.abs(deltaX) < DRAG_THRESHOLD
                        && Math.abs(deltaY) < DRAG_THRESHOLD) return;

                // Only vertical gestures scroll the list; horizontal movement is ignored.
                if (Math.abs(deltaY) >= Math.abs(deltaX)) {
                    suppressNextFrameClick = true;
                    JViewport viewport = frameScroll.getViewport();
                    Dimension viewSize = viewport.getViewSize();
                    Dimension extent = viewport.getExtentSize();
                    int maxY = Math.max(0, viewSize.height - extent.height);
                    int targetY = dragStartViewportPosition.y - deltaY;
                    targetY = Math.max(0, Math.min(maxY, targetY));
                    viewport.setViewPosition(new Point(dragStartViewportPosition.x, targetY));
                }
            }

            @Override public void mouseReleased(MouseEvent e) {
                frameListDragActive = false;
                dragStartInView = null;
                dragStartViewportPosition = null;
            }
        };

        component.addMouseListener(dragHandler);
        component.addMouseMotionListener(dragHandler);

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                installDragToScroll(child);
            }
        }
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
        selectedPreviewSource = icon;
        largePreview.setText(null);
        updateLargePreviewIcon();
        selectedName.setText(name);
        ThemeConfig.Element selectedNameStyle = ThemeManager.config().elements.get("frame-selection-selected-name");
        selectedName.setVisible(selectedNameStyle != null && Boolean.TRUE.equals(selectedNameStyle.visible));
        if (selectedNameStyle != null) selectedName.setForeground(parseColor(selectedNameStyle.foreground, Color.WHITE));
        continueButton.setEnabled(true);
    }

    /** Refit the selected artwork into the preview area without distorting its aspect ratio. */
    private void updateLargePreviewIcon() {
        if (selectedPreviewSource == null) return;
        int availableWidth = Math.max(1, previewPanel.getWidth() - 40);
        int availableHeight = Math.max(1, previewPanel.getHeight() - selectedName.getPreferredSize().height - 48);
        int sourceWidth = selectedPreviewSource.getIconWidth();
        int sourceHeight = selectedPreviewSource.getIconHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) return;
        double scale = Math.min((double) availableWidth / sourceWidth, (double) availableHeight / sourceHeight);
        int width = Math.max(1, (int)Math.round(sourceWidth * scale));
        int height = Math.max(1, (int)Math.round(sourceHeight * scale));
        Image scaled = selectedPreviewSource.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
        largePreview.setIcon(new ImageIcon(scaled));
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
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, maxW, maxH);
        g.setComposite(AlphaComposite.SrcOver);
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
