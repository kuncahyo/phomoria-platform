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
    private String selectedElement;
    /** Prevent loading a selected element into the controls from writing stale values back. */
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
        properties.setBorder(BorderFactory.createTitledBorder("Posisi dan ukuran (kanvas desain)"));
        addField(properties, "X", x); addField(properties, "Y", y);
        addField(properties, "Lebar", width); addField(properties, "Tinggi", height);
        JPanel right = new JPanel(new BorderLayout()); right.add(properties, BorderLayout.NORTH);
        right.add(new JLabel("Seret elemen pada preview untuk mengubah posisi. PNG tombol dapat diganti melalui tombol di atas.", SwingConstants.LEFT), BorderLayout.CENTER);
        add(new JScrollPane(canvas), BorderLayout.CENTER); add(right, BorderLayout.EAST);

        screenChoice.addActionListener(e -> { refreshElements(); canvas.repaint(); });
        elementChoice.addActionListener(e -> selectElement());
        ChangeListenerAdapter listener = new ChangeListenerAdapter(() -> { applySpinners(); canvas.repaint(); });
        x.addChangeListener(listener); y.addChangeListener(listener); width.addChangeListener(listener); height.addChangeListener(listener);
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

    private static void addField(JPanel p, String label, JSpinner field) { p.add(new JLabel(label)); p.add(field); }

    private void refreshElements() {
        String screen = (String) screenChoice.getSelectedItem();
        updatingControls = true;
        try {
            elementChoice.removeAllItems();
            // Element-to-screen mapping is not yet part of ThemeConfig. The current theme
            // defines the two start-screen controls, so list all configured elements there.
            if ("start".equals(screen)) {
                for (String id : ThemeManager.config().elements.keySet()) elementChoice.addItem(id);
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

    private void syncControlsFromElement(ThemeConfig.Element el) {
        updatingControls = true;
        try { x.setValue(el.x); y.setValue(el.y); width.setValue(el.width); height.setValue(el.height); }
        finally { updatingControls = false; }
    }

    private void updateElementActionButtons(JButton transparency, JButton restoreDefault) {
        ThemeConfig.Element el = selectedElement == null ? null : ThemeManager.config().elements.get(selectedElement);
        boolean available = el != null;
        transparency.setEnabled(available);
        restoreDefault.setEnabled(available);
        transparency.setText(available && el.transparent ? "TAMPILKAN TOMBOL" : "JADIKAN TRANSPARAN");
    }

    private void toggleSelectedTransparency() {
        if (selectedElement == null) return;
        ThemeConfig.Element el = ThemeManager.config().elements.get(selectedElement);
        if (el == null) return;
        el.transparent = !el.transparent;
        canvas.repaint();
        try {
            ThemeManager.save();
        } catch (IOException ex) {
            el.transparent = !el.transparent;
            canvas.repaint();
            showError(ex);
        }
    }

    private void restoreSelectedDefault() {
        if (selectedElement == null) return;
        int answer = JOptionPane.showConfirmDialog(this,
                "Kembalikan gambar tombol '" + selectedElement + "' ke gambar bawaan?",
                "Kembalikan Default", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            ThemeManager.restoreDefaultButton(selectedElement);
            canvas.invalidateButtonImage(selectedElement);
            canvas.repaint();
            JOptionPane.showMessageDialog(this, "Gambar tombol berhasil dikembalikan ke default.",
                    "Theme Editor", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            showError(ex);
        }
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
            canvas.repaint();
        } catch (IOException ex) { showError(ex); }
    }

    private void chooseElementImage() {
        if (selectedElement == null) { JOptionPane.showMessageDialog(this, "Pilih elemen tombol terlebih dahulu."); return; }
        JFileChooser chooser = imageChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path source = chooser.getSelectedFile().toPath();
        String filename = selectedElement + ".png";
        try {
            ThemeManager.replaceAsset(source, ThemeManager.root().resolve("buttons").resolve(filename));
            ThemeManager.config().elements.get(selectedElement).image = filename;
            // The asset is replaced at the same path; invalidate the editor preview cache
            // so the newly selected PNG appears immediately without leaving the editor.
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
        try {
            ThemeManager.save();
            JOptionPane.showMessageDialog(this, "Tema berhasil disimpan.\nLokasi: " + ThemeManager.root().resolve("theme.json").toAbsolutePath(), "Theme Editor", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) { showError(ex); }
    }
    private void showError(Exception ex) { JOptionPane.showMessageDialog(this, "Gagal memproses aset tema:\n"+ex.getMessage(), "Theme Editor", JOptionPane.ERROR_MESSAGE); }

    private final class CanvasPanel extends JPanel {
        /** Cached decoded PNGs for fast painting; explicitly invalidated when an asset changes. */
        private final Map<String, BufferedImage> buttonImageCache = new HashMap<>();

        void invalidateButtonImage(String elementId) {
            buttonImageCache.remove(elementId);
        }

        private BufferedImage loadButtonImage(String elementId, Path imagePath) {
            if (buttonImageCache.containsKey(elementId)) return buttonImageCache.get(elementId);
            try {
                BufferedImage image = ImageIO.read(imagePath.toFile());
                if (image != null) buttonImageCache.put(elementId, image);
                return image;
            } catch (IOException ex) {
                return null;
            }
        }

        CanvasPanel() {
            setPreferredSize(new Dimension(960, 540)); setBackground(new Color(35,35,40)); setBorder(new LineBorder(Color.GRAY));
            MouseAdapter mouse = new MouseAdapter() {
                private String draggingElement;
                private Point dragStart;
                private int originalX, originalY;

                @Override public void mousePressed(MouseEvent e) {
                    draggingElement = null; dragStart = null;
                    if (!"start".equals(screenChoice.getSelectedItem())) return;
                    double sx = getWidth() / (double) ThemeManager.config().designWidth;
                    double sy = getHeight() / (double) ThemeManager.config().designHeight;
                    java.util.List<java.util.Map.Entry<String, ThemeConfig.Element>> entries =
                            new java.util.ArrayList<>(ThemeManager.config().elements.entrySet());
                    // Reverse order: the last-painted item is the topmost hit target.
                    for (int i = entries.size() - 1; i >= 0; i--) {
                        var entry = entries.get(i);
                        ThemeConfig.Element el = entry.getValue();
                        if (el == null) continue;
                        int left = (int)Math.round(el.x * sx), top = (int)Math.round(el.y * sy);
                        int w = (int)Math.round(el.width * sx), h = (int)Math.round(el.height * sy);
                        if (e.getX() >= left && e.getX() <= left + w && e.getY() >= top && e.getY() <= top + h) {
                            draggingElement = entry.getKey();
                            elementChoice.setSelectedItem(draggingElement);
                            selectedElement = draggingElement;
                            originalX = el.x; originalY = el.y; dragStart = e.getPoint();
                            repaint();
                            return;
                        }
                    }
                }

                @Override public void mouseDragged(MouseEvent e) {
                    if (dragStart == null || draggingElement == null) return;
                    ThemeConfig.Element el = ThemeManager.config().elements.get(draggingElement);
                    if (el == null) return;
                    double sx = getWidth() / (double) ThemeManager.config().designWidth;
                    double sy = getHeight() / (double) ThemeManager.config().designHeight;
                    // Update only the element captured on mousePressed; do not route drag
                    // changes through the currently selected spinner/listener state.
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
                Path bg=ThemeManager.background(screen);
                if (bg!=null && Files.isRegularFile(bg)) {
                    Image im=new ImageIcon(bg.toString()).getImage();
                    g.drawImage(im,0,0,getWidth(),getHeight(),this);
                }
                if (!"start".equals(screen)) return;
                double sx=getWidth()/(double)ThemeManager.config().designWidth;
                double sy=getHeight()/(double)ThemeManager.config().designHeight;
                // Always render every element on the page. Selection only adds an outline.
                for (java.util.Map.Entry<String, ThemeConfig.Element> entry : ThemeManager.config().elements.entrySet()) {
                    String id = entry.getKey();
                    ThemeConfig.Element el = entry.getValue();
                    if (el == null) continue;
                    Path imagePath = ThemeManager.buttonImage(id);
                    int left=(int)Math.round(el.x*sx), top=(int)Math.round(el.y*sy);
                    int w=(int)Math.round(el.width*sx), h=(int)Math.round(el.height*sy);
                    if (!el.transparent && imagePath!=null && Files.isRegularFile(imagePath)) {
                        // Decode the current file directly through ImageIO. ImageIcon caches
                        // images by URL/path, so replacing a PNG at the same path can leave
                        // the editor showing the old image even after repaint().
                        BufferedImage image = loadButtonImage(id, imagePath);
                        if (image != null) {
                            g.drawImage(image,left,top,w,h,this);
                        } else {
                            g.setColor(new Color(65,65,75)); g.fillRoundRect(left,top,w,h,10,10);
                            g.setColor(Color.WHITE); g.drawString(id,left+8,top+Math.max(18,h/2));
                        }
                    } else if (!el.transparent) {
                        g.setColor(new Color(65,65,75)); g.fillRoundRect(left,top,w,h,10,10);
                        g.setColor(Color.WHITE); g.drawString(id,left+8,top+Math.max(18,h/2));
                    }
                    if (id.equals(selectedElement)) {
                        g.setColor(Color.YELLOW); g.setStroke(new BasicStroke(2)); g.drawRect(left,top,w,h);
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
