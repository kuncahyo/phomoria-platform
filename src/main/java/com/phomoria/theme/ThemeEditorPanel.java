package com.phomoria.theme;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public ThemeEditorPanel(Runnable back) {
        this.back = back;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton background = new JButton("GANTI BACKGROUND PNG/GIF");
        JButton buttonImage = new JButton("GANTI PNG TOMBOL");
        JButton save = new JButton("SIMPAN TEMA");
        JButton backButton = new JButton("KEMBALI KE SETTINGS");
        top.add(new JLabel("Halaman:")); top.add(screenChoice);
        top.add(new JLabel("Elemen:")); top.add(elementChoice);
        top.add(background); top.add(buttonImage); top.add(save); top.add(backButton);
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
        save.addActionListener(e -> saveTheme());
        backButton.addActionListener(e -> back.run());
        refreshElements();
    }

    private static void addField(JPanel p, String label, JSpinner field) { p.add(new JLabel(label)); p.add(field); }
    private void refreshElements() {
        String screen = (String) screenChoice.getSelectedItem();
        elementChoice.removeAllItems();
        if ("start".equals(screen)) {
            elementChoice.addItem("start-payment"); elementChoice.addItem("start-settings");
        }
        if (elementChoice.getItemCount() > 0) elementChoice.setSelectedIndex(0);
        else selectedElement = null;
        canvas.repaint();
    }
    private void selectElement() {
        selectedElement = (String) elementChoice.getSelectedItem();
        ThemeConfig.Element el = selectedElement == null ? null : ThemeManager.config().elements.get(selectedElement);
        if (el != null) {
            x.setValue(el.x); y.setValue(el.y); width.setValue(el.width); height.setValue(el.height);
        }
        canvas.repaint();
    }
    private void applySpinners() {
        if (selectedElement == null) return;
        ThemeConfig.Element el = ThemeManager.config().elements.get(selectedElement);
        if (el == null) return;
        el.x = (Integer)x.getValue(); el.y = (Integer)y.getValue();
        el.width = (Integer)width.getValue(); el.height = (Integer)height.getValue();
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
        try { ThemeManager.save(); JOptionPane.showMessageDialog(this, "Tema berhasil disimpan.", "Theme Editor", JOptionPane.INFORMATION_MESSAGE); }
        catch (IOException ex) { showError(ex); }
    }
    private void showError(Exception ex) { JOptionPane.showMessageDialog(this, "Gagal memproses aset tema:\n"+ex.getMessage(), "Theme Editor", JOptionPane.ERROR_MESSAGE); }

    private final class CanvasPanel extends JPanel {
        private BufferedImage backgroundImage;
        private Image buttonImage;
        private Point dragStart;
        private int originalX, originalY;
        CanvasPanel() {
            setPreferredSize(new Dimension(960, 540)); setBackground(new Color(35,35,40)); setBorder(new LineBorder(Color.GRAY));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragStart = null;
                    String screen = (String) screenChoice.getSelectedItem();
                    if (!"start".equals(screen)) return;

                    double sx = getWidth() / (double) ThemeManager.config().designWidth;
                    double sy = getHeight() / (double) ThemeManager.config().designHeight;

                    // Only the element physically under the pointer may be dragged.
                    java.util.List<java.util.Map.Entry<String, ThemeConfig.Element>> entries =
                            new java.util.ArrayList<>(ThemeManager.config().elements.entrySet());

                    for (int i = entries.size() - 1; i >= 0; i--) {
                        var entry = entries.get(i);
                        ThemeConfig.Element el = entry.getValue();
                        if (el == null) continue;

                        int left = (int) Math.round(el.x * sx);
                        int top = (int) Math.round(el.y * sy);
                        int right = left + (int) Math.round(el.width * sx);
                        int bottom = top + (int) Math.round(el.height * sy);

                        if (e.getX() >= left && e.getX() <= right
                                && e.getY() >= top && e.getY() <= bottom) {
                            elementChoice.setSelectedItem(entry.getKey());
                            selectedElement = entry.getKey();
                            originalX = el.x;
                            originalY = el.y;
                            dragStart = e.getPoint();
                            repaint();
                            return;
                        }
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragStart == null || selectedElement == null) return;
                    double sx = getWidth() / (double) ThemeManager.config().designWidth;
                    double sy = getHeight() / (double) ThemeManager.config().designHeight;
                    x.setValue(originalX + (int) Math.round((e.getX() - dragStart.x) / sx));
                    y.setValue(originalY + (int) Math.round((e.getY() - dragStart.y) / sy));
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragStart = null;
                }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics); Graphics2D g=(Graphics2D)graphics.create();
            try {
                String screen=(String)screenChoice.getSelectedItem(); Path bg=ThemeManager.background(screen);
                if (bg!=null && Files.isRegularFile(bg)) { ImageIcon icon=new ImageIcon(bg.toString()); Image im=icon.getImage(); g.drawImage(im,0,0,getWidth(),getHeight(),this); }
                double sx=getWidth()/(double)ThemeManager.config().designWidth, sy=getHeight()/(double)ThemeManager.config().designHeight;
                if (selectedElement!=null) {
                    ThemeConfig.Element el=ThemeManager.config().elements.get(selectedElement);
                    if (el!=null) {
                        Path p=ThemeManager.buttonImage(selectedElement);
                        if (p!=null && Files.isRegularFile(p)) g.drawImage(new ImageIcon(p.toString()).getImage(),(int)(el.x*sx),(int)(el.y*sy),(int)(el.width*sx),(int)(el.height*sy),this);
                        g.setColor(Color.YELLOW); g.setStroke(new BasicStroke(2)); g.drawRect((int)(el.x*sx),(int)(el.y*sy),(int)(el.width*sx),(int)(el.height*sy));
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
