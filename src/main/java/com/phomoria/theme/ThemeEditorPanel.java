package com.phomoria.theme;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Theme editor embedded inside Settings. Canvas uses the theme's design coordinates. */
public final class ThemeEditorPanel extends JPanel {
    private static final String[] SCREENS = {"start", "frame-selection", "main", "effect", "result"};
    private final Runnable back;
    private final JComboBox<String> screenChoice = new JComboBox<>(SCREENS);
    private final JComboBox<String> elementChoice = new JComboBox<>();
    private final CanvasPanel canvas = new CanvasPanel();
    private final JSpinner x = new JSpinner(new SpinnerNumberModel(0, -5000, 10000, 1));
    private final JSpinner y = new JSpinner(new SpinnerNumberModel(0, -5000, 10000, 1));
    private final JSpinner width = new JSpinner(new SpinnerNumberModel(300, 1, 10000, 1));
    private final JSpinner height = new JSpinner(new SpinnerNumberModel(70, 1, 10000, 1));
    private final JTextField elementText = new JTextField(16);
    private final JButton textColor = new JButton("PILIH WARNA TEKS");
    private final JCheckBox elementVisible = new JCheckBox("Tampilkan elemen");
    private String selectedElement;
    private boolean updatingControls;

    public ThemeEditorPanel(Runnable back) {
        this.back = back;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton background = new JButton("GANTI BACKGROUND PNG/GIF");
        JButton buttonImage = new JButton("GANTI PNG TOMBOL");
        JButton transparency = new JButton("JADIKAN TRANSPARAN");
        JButton restoreDefault = new JButton("DEFAULT");
        JButton save = new JButton("SIMPAN TEMA");
        JButton backButton = new JButton("KEMBALI KE SETTINGS");
        top.add(new JLabel("Halaman:")); top.add(screenChoice);
        top.add(new JLabel("Elemen:")); top.add(elementChoice);
        top.add(background); top.add(buttonImage); top.add(transparency); top.add(restoreDefault); top.add(save); top.add(backButton);
        add(top, BorderLayout.NORTH);

        JPanel properties = new JPanel(new GridLayout(0, 2, 6, 6));
        properties.setBorder(BorderFactory.createTitledBorder("Tampilan elemen"));
        addField(properties, "X", x); addField(properties, "Y", y);
        addField(properties, "Lebar", width); addField(properties, "Tinggi", height);
        addField(properties, "Teks", elementText);
        addField(properties, "Warna teks", textColor);
        properties.add(new JLabel("Visibilitas")); properties.add(elementVisible);
        JPanel right = new JPanel(new BorderLayout()); right.add(properties, BorderLayout.NORTH);
        right.add(new JLabel("Seret elemen pada preview untuk mengubah posisi. PNG tombol dapat diganti melalui tombol di atas.", SwingConstants.LEFT), BorderLayout.CENTER);
        add(new JScrollPane(canvas), BorderLayout.CENTER); add(right, BorderLayout.EAST);

        screenChoice.addActionListener(e -> { refreshElements(); canvas.repaint(); });
        elementChoice.addActionListener(e -> selectElement());
        ChangeListenerAdapter listener = new ChangeListenerAdapter(() -> { applySpinners(); canvas.repaint(); });
        x.addChangeListener(listener); y.addChangeListener(listener); width.addChangeListener(listener); height.addChangeListener(listener);
        elementText.addActionListener(e -> applyTextAndStyle());
        elementText.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { applyTextAndStyle(); }
        });
        textColor.addActionListener(e -> chooseTextColor());
        elementVisible.addActionListener(e -> applyTextAndStyle());
        background.addActionListener(e -> chooseBackground());
        buttonImage.addActionListener(e -> chooseElementImage());
        transparency.addActionListener(e -> {
            toggleSelectedTransparency();
            updateElementActionButtons(transparency, restoreDefault);
        });
        restoreDefault.addActionListener(e -> {
            restoreSelectedDefault();
            updateElementActionButtons(transparency, restoreDefault);
        });
        save.addActionListener(e -> saveTheme());
        backButton.addActionListener(e -> back.run());
        refreshElements();
        updateElementActionButtons(transparency, restoreDefault);
        elementChoice.addActionListener(e -> updateElementActionButtons(transparency, restoreDefault));
    }

    private static void addField(JPanel p, String label, JSpinner field) {
        p.add(new JLabel(label));
        p.add(field);
    }

    private static void addField(JPanel p, String label, JButton field) {
        p.add(new JLabel(label));
        p.add(field);
    }

    private static void addField(JPanel p, String label, JTextField field) {
        p.add(new JLabel(label));
        p.add(field);
    }

    private void refreshElements() {
        String screen = (String) screenChoice.getSelectedItem();
        updatingControls = true;
        try {
            elementChoice.removeAllItems();
            if (screen != null) {
                String prefix = screen + "-";
                for (String id : ThemeManager.config().elements.keySet()) {
                    if (id.startsWith(prefix)) elementChoice.addItem(id);
                }
            }
            selectedElement = null;
            if (elementChoice.getItemCount() > 0) elementChoice.setSelectedIndex(0);
        } finally { updatingControls = false; }
        selectElement();
        canvas.repaint();
    }

    private void selectElement() {
        if (updatingControls) return;
        selectedElement = (String) elementChoice.getSelectedItem();
        ThemeConfig.Element el = selectedElement == null ? null : ThemeManager.config().elements.get(selectedElement);
        updatingControls = true;
        try {
            if (el != null) {
                x.setValue(el.x); y.setValue(el.y); width.setValue(el.width); height.setValue(el.height);
                boolean dynamicText = selectedElement.endsWith("card-name")
                        || selectedElement.endsWith("card-info")
                        || selectedElement.endsWith("selected-name")
                        || selectedElement.equals("main-status")
                        || selectedElement.equals("main-counter")
                        || selectedElement.equals("main-countdown");
                elementText.setEditable(!dynamicText);
                elementText.setToolTipText(dynamicText
                        ? "Teks atau angka berasal dari status sesi; atur tampil/sembunyi dan warnanya di sini."
                        : "Ubah teks label elemen ini.");
                elementText.setText(dynamicText ? "(otomatis dari data frame)" : (el.text == null ? "" : el.text));
                elementVisible.setSelected(Boolean.TRUE.equals(el.visible));
                updateColorButton(el.foreground);
            }
        } finally { updatingControls = false; }
        canvas.repaint();
    }

    private void applySpinners() {
        if (updatingControls || selectedElement == null) return;
        ThemeConfig.Element el = ThemeManager.config().elements.get(selectedElement);
        if (el == null) return;
        el.x = (Integer)x.getValue(); el.y = (Integer)y.getValue();
        el.width = (Integer)width.getValue(); el.height = (Integer)height.getValue();
    }

    private void applyTextAndStyle() {
        if (updatingControls || selectedElement == null) return;
        ThemeConfig.Element el = ThemeManager.config().elements.get(selectedElement);
        if (el == null) return;
        if (elementText.isEditable()) el.text = elementText.getText();
        el.visible = elementVisible.isSelected();
        canvas.repaint();
    }

    private void chooseTextColor() {
        if (selectedElement == null) return;
        ThemeConfig.Element el = ThemeManager.config().elements.get(selectedElement);
        if (el == null) return;
        Color initial = parseColor(el.foreground, Color.WHITE);
        Color chosen = JColorChooser.showDialog(this, "Pilih warna teks", initial);
        if (chosen == null) return;
        el.foreground = String.format("#%02X%02X%02X", chosen.getRed(), chosen.getGreen(), chosen.getBlue());
        updateColorButton(el.foreground);
        canvas.repaint();
    }

    private void updateColorButton(String hex) {
        Color c = parseColor(hex, Color.WHITE);
        textColor.setBackground(c);
        textColor.setForeground((c.getRed() + c.getGreen() + c.getBlue()) < 360 ? Color.WHITE : Color.BLACK);
        textColor.setOpaque(true);
    }

    private static Color parseColor(String value, Color fallback) {
        try { return value == null ? fallback : Color.decode(value); }
        catch (RuntimeException ex) { return fallback; }
    }

    private void syncControlsFromElement(ThemeConfig.Element el) {
        updatingControls = true;
        try { x.setValue(el.x); y.setValue(el.y); width.setValue(el.width); height.setValue(el.height); }
        finally { updatingControls = false; }
    }

    private void updateElementActionButtons(JButton transparency, JButton restoreDefault) {
        ThemeConfig.Element el = selectedElement == null ? null : ThemeManager.config().elements.get(selectedElement);
        boolean available = el != null;
        // Transparency applies to every element's visual rendering, including native
        // Java buttons that do not have a user-selected PNG asset.
        transparency.setEnabled(available);
        restoreDefault.setEnabled(available);
        transparency.setText(available && el.transparent ? "TAMPILKAN GAMBAR" : "SEMBUNYIKAN GAMBAR");
    }

    private void toggleSelectedTransparency() {
        if (selectedElement == null) return;
        ThemeConfig.Element el = ThemeManager.config().elements.get(selectedElement);
        if (el == null) return;
        el.transparent = !el.transparent;
        canvas.repaint();
        try { ThemeManager.save(); }
        catch (IOException ex) { el.transparent = !el.transparent; canvas.repaint(); showError(ex); }
    }

    private void restoreSelectedDefault() {
        if (selectedElement == null) return;
        int answer = JOptionPane.showConfirmDialog(this,
                "Kembalikan teks, warna, visibilitas, posisi, ukuran, dan aset elemen '" + selectedElement + "' ke pengaturan bawaan?",
                "Kembalikan Default", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            ThemeManager.restoreDefaultElement(selectedElement);
            ThemeConfig.Element restored = ThemeManager.config().elements.get(selectedElement);
            syncControlsFromElement(restored);
            updatingControls = true;
            try {
                elementText.setText(restored.text == null ? "" : restored.text);
                elementVisible.setSelected(Boolean.TRUE.equals(restored.visible));
                updateColorButton(restored.foreground);
            } finally { updatingControls = false; }
            canvas.invalidateButtonImage(selectedElement);
            canvas.repaint();
            JOptionPane.showMessageDialog(this, "Elemen berhasil dikembalikan ke pengaturan default.",
                    "Theme Editor", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) { showError(ex); }
    }

    private void chooseBackground() {
        JFileChooser chooser = imageChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path source = chooser.getSelectedFile().toPath();
        String screen = (String)screenChoice.getSelectedItem();
        String ext = extension(source); if (!ext.equals("png") && !ext.equals("gif")) ext = "png";
        String filename = screen + "." + ext;
        try {
            ThemeManager.replaceAsset(source, ThemeManager.root().resolve("backgrounds").resolve(filename));
            ThemeManager.config().screens.get(screen).background = filename;
            ThemeManager.save();
            canvas.invalidateBackgroundImage();
            canvas.repaint();
        } catch (IOException ex) { showError(ex); }
    }

    private void chooseElementImage() {
        if (selectedElement == null) { JOptionPane.showMessageDialog(this, "Pilih elemen terlebih dahulu."); return; }
        boolean imageButton = selectedElement.equals("start-payment")
                || selectedElement.equals("start-settings")
                || selectedElement.equals("frame-selection-continue")
                || selectedElement.equals("main-retake")
                || selectedElement.equals("main-cancel")
                || selectedElement.equals("result-qr")
                || selectedElement.equals("result-print")
                || selectedElement.equals("result-new-session");
        if (!imageButton) {
            JOptionPane.showMessageDialog(this, "Elemen ini berupa area atau teks. Gunakan pengaturan background halaman atau pilih elemen tombol untuk mengganti PNG.");
            return;
        }
        JFileChooser chooser = imageChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path source = chooser.getSelectedFile().toPath();
        String filename = selectedElement + ".png";
        try {
            ThemeManager.replaceAsset(source, ThemeManager.root().resolve("buttons").resolve(filename));
            ThemeConfig.Element element = ThemeManager.config().elements.get(selectedElement);
            element.image = filename;
            element.transparent = false;
            ThemeManager.save();
            canvas.invalidateButtonImage(selectedElement);
            canvas.repaint();
        } catch (IOException ex) { showError(ex); }
    }

    private JFileChooser imageChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Gambar PNG/GIF", "png", "gif"));
        return chooser;
    }
    private static String extension(Path p) { String n=p.getFileName().toString(); int i=n.lastIndexOf('.'); return i<0?"png":n.substring(i+1).toLowerCase(); }

    private void saveTheme() {
        applySpinners();
        applyTextAndStyle();
        try {
            ThemeManager.save();
            JOptionPane.showMessageDialog(this, "Tema berhasil disimpan.\nLokasi: " + ThemeManager.root().resolve("theme.json").toAbsolutePath(), "Theme Editor", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) { showError(ex); }
    }
    private void showError(Exception ex) { JOptionPane.showMessageDialog(this, "Gagal memproses aset tema:\n"+ex.getMessage(), "Theme Editor", JOptionPane.ERROR_MESSAGE); }

    private final class CanvasPanel extends JPanel {
        private final Map<String, BufferedImage> buttonImageCache = new HashMap<>();
        private ImageIcon backgroundIcon;
        private Path cachedBackgroundPath;
        private long cachedBackgroundModified = -1L;
        private long cachedBackgroundSize = -1L;

        void invalidateButtonImage(String elementId) { buttonImageCache.remove(elementId); }

        void invalidateBackgroundImage() {
            backgroundIcon = null;
            cachedBackgroundPath = null;
            cachedBackgroundModified = -1L;
            cachedBackgroundSize = -1L;
        }

        private ImageIcon loadBackgroundImage(Path path) {
            if (path == null || !Files.isRegularFile(path)) {
                invalidateBackgroundImage();
                return null;
            }
            try {
                long modified = Files.getLastModifiedTime(path).toMillis();
                long size = Files.size(path);
                if (backgroundIcon == null
                        || !path.equals(cachedBackgroundPath)
                        || modified != cachedBackgroundModified
                        || size != cachedBackgroundSize) {
                    backgroundIcon = new ImageIcon(Files.readAllBytes(path));
                    cachedBackgroundPath = path;
                    cachedBackgroundModified = modified;
                    cachedBackgroundSize = size;
                }
                return backgroundIcon;
            } catch (IOException ex) {
                invalidateBackgroundImage();
                return null;
            }
        }

        private BufferedImage loadButtonImage(String elementId, Path imagePath) {
            if (buttonImageCache.containsKey(elementId)) return buttonImageCache.get(elementId);
            try {
                BufferedImage image = ImageIO.read(imagePath.toFile());
                if (image != null) buttonImageCache.put(elementId, image);
                return image;
            } catch (IOException ex) { return null; }
        }

        CanvasPanel() {
            setPreferredSize(new Dimension(960, 540)); setBackground(new Color(35,35,40)); setBorder(new LineBorder(Color.GRAY));
            MouseAdapter mouse = new MouseAdapter() {
                private String draggingElement;
                private Point dragStart;
                private int originalX, originalY;

                @Override public void mousePressed(MouseEvent e) {
                    draggingElement = null; dragStart = null;
                    String screen = (String) screenChoice.getSelectedItem();
                    if (screen == null) return;
                    double sx = getWidth() / (double) ThemeManager.config().designWidth;
                    double sy = getHeight() / (double) ThemeManager.config().designHeight;
                    java.util.List<java.util.Map.Entry<String, ThemeConfig.Element>> entries =
                            new java.util.ArrayList<>(ThemeManager.config().elements.entrySet());
                    for (int i = entries.size() - 1; i >= 0; i--) {
                        var entry = entries.get(i);
                        if (!entry.getKey().startsWith(screen + "-")) continue;
                        ThemeConfig.Element el = entry.getValue();
                        if (el == null) continue;
                        int left = (int)Math.round(el.x * sx), top = (int)Math.round(el.y * sy);
                        int w = (int)Math.round(el.width * sx), h = (int)Math.round(el.height * sy);
                        if (e.getX() >= left && e.getX() <= left + w && e.getY() >= top && e.getY() <= top + h) {
                            draggingElement = entry.getKey();
                            elementChoice.setSelectedItem(draggingElement);
                            selectedElement = draggingElement;
                            originalX = el.x; originalY = el.y; dragStart = e.getPoint();
                            repaint(); return;
                        }
                    }
                }

                @Override public void mouseDragged(MouseEvent e) {
                    if (dragStart == null || draggingElement == null) return;
                    ThemeConfig.Element el = ThemeManager.config().elements.get(draggingElement);
                    if (el == null) return;
                    double sx = getWidth() / (double) ThemeManager.config().designWidth;
                    double sy = getHeight() / (double) ThemeManager.config().designHeight;
                    el.x = originalX + (int)Math.round((e.getX() - dragStart.x) / sx);
                    el.y = originalY + (int)Math.round((e.getY() - dragStart.y) / sy);
                    syncControlsFromElement(el);
                    repaint();
                }

                @Override public void mouseReleased(MouseEvent e) { dragStart = null; draggingElement = null; }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g=(Graphics2D)graphics.create();
            try {
                String screen=(String)screenChoice.getSelectedItem();
                Path bg = ThemeManager.background(screen);
                ImageIcon background = loadBackgroundImage(bg);
                if (background != null && background.getImage() != null) {
                    g.drawImage(background.getImage(), 0, 0, getWidth(), getHeight(), this);
                }
                double sx=getWidth()/(double)ThemeManager.config().designWidth;
                double sy=getHeight()/(double)ThemeManager.config().designHeight;
                for (java.util.Map.Entry<String, ThemeConfig.Element> entry : ThemeManager.config().elements.entrySet()) {
                    String id = entry.getKey();
                    if (!id.startsWith(screen + "-")) continue;
                    ThemeConfig.Element el = entry.getValue();
                    if (el == null) continue;
                    Path imagePath = ThemeManager.buttonImage(id);
                    int left=(int)Math.round(el.x*sx), top=(int)Math.round(el.y*sy);
                    int w=(int)Math.round(el.width*sx), h=(int)Math.round(el.height*sy);
                    if (el.visible && !el.transparent && imagePath!=null && Files.isRegularFile(imagePath)) {
                        BufferedImage image = loadButtonImage(id, imagePath);
                        if (image != null) {
                            g.drawImage(image,left,top,w,h,this);
                        } else {
                            g.setColor(new Color(65,65,75)); g.fillRoundRect(left,top,w,h,10,10);
                            g.setColor(Color.WHITE); g.drawString(id,left+8,top+Math.max(18,h/2));
                        }
                    } else if (el.visible && !el.transparent) {
                        g.setColor(new Color(65,65,75,150)); g.fillRoundRect(left,top,w,h,10,10);
                        g.setColor(parseColor(el.foreground, Color.WHITE));
                        String label = (el.text == null || el.text.isBlank()) ? id : el.text;
                        g.drawString(label,left+8,top+Math.max(18,h/2));
                    }
                }
            } finally { g.dispose(); }
        }
    }

    private static final class ChangeListenerAdapter implements javax.swing.event.ChangeListener {
        private final Runnable action; ChangeListenerAdapter(Runnable action){this.action=action;}
        @Override public void stateChanged(javax.swing.event.ChangeEvent e){action.run();}
    }
}
