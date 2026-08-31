package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.app.ApplicationFrame;
import com.phomoria.debug.DebugLog;
import com.phomoria.effects.PhotoEffect;
import com.phomoria.effects.PhotoEffectSession;
import com.phomoria.effects.PhotoEffectSettings;
import com.phomoria.effects.PhotoEffectProcessor;
import com.phomoria.session.PhotoSession;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public final class EffectScreen extends JPanel {
    private final ApplicationFrame frame;
    private final PhotoEffectSettings settings;
    private final PhotoEffectSession effectSession;
    private final JLabel largePreview = new JLabel();
    private final JLabel selectedLabel = new JLabel("FOTO 1", SwingConstants.CENTER);
    private final JPanel photoButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
    private final JPanel effectButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
    private final JRadioButton applyAll = new JRadioButton("Terapkan ke semua foto");
    private int selectedIndex = 0;

    public EffectScreen(ApplicationFrame frame) {
        this.frame = frame;
        PhotoSession session = AppContext.session();
        this.settings = AppContext.settings().getPhotoEffectSettings();
        this.effectSession = new PhotoEffectSession(session.getSlotCount());

        setLayout(new BorderLayout(14, 14));
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        setBackground(new Color(18, 18, 22));

        JLabel title = new JLabel("PILIH EFEK FOTO", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.setOpaque(false);
        largePreview.setHorizontalAlignment(SwingConstants.CENTER);
        largePreview.setVerticalAlignment(SwingConstants.CENTER);
        largePreview.setOpaque(true);
        largePreview.setBackground(Color.BLACK);
        center.add(largePreview, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setOpaque(false);
        selectedLabel.setForeground(Color.WHITE);
        right.add(selectedLabel, BorderLayout.NORTH);
        photoButtons.setOpaque(false);
        right.add(photoButtons, BorderLayout.CENTER);
        center.add(right, BorderLayout.EAST);

        add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setOpaque(false);
        applyAll.setForeground(Color.WHITE);
        applyAll.setOpaque(false);
        applyAll.addActionListener(e -> {
            effectSession.setApplyToAll(applyAll.isSelected());
            renderPreview();
        });
        bottom.add(applyAll, BorderLayout.NORTH);

        effectButtons.setOpaque(false);
        bottom.add(effectButtons, BorderLayout.CENTER);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        nav.setOpaque(false);
        JButton back = new JButton("KEMBALI");
        JButton next = new JButton("SELANJUTNYA");
        back.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Batalkan sesi dan kembali ke halaman awal?",
                    "Batalkan Sesi",
                    JOptionPane.YES_NO_OPTION
            );
            if (choice == JOptionPane.YES_OPTION) {
                DebugLog.warn("EffectScreen cancelled current session.");
                AppContext.newSession();
                frame.showStart();
            }
        });
        next.addActionListener(e -> finishEffects());
        nav.add(back);
        nav.add(next);
        bottom.add(nav, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        buildPhotoButtons();
        buildEffectButtons();
        renderPreview();
    }

    private void buildPhotoButtons() {
        photoButtons.removeAll();
        List<BufferedImage> photos = AppContext.session().getPhotos();
        for (int i = 0; i < photos.size(); i++) {
            final int index = i;
            JButton button = new JButton("FOTO " + (i + 1));
            button.addActionListener(e -> {
                selectedIndex = index;
                renderPreview();
            });
            photoButtons.add(button);
        }
        photoButtons.revalidate();
        photoButtons.repaint();
    }

    private void buildEffectButtons() {
        effectButtons.removeAll();
        for (PhotoEffect effect : PhotoEffect.values()) {
            if (!settings.isEnabled() || settings.isEffectAllowed(effect)) {
                JButton button = new JButton(effect.getLabel());
                button.addActionListener(e -> {
                    if (applyAll.isSelected()) {
                        effectSession.setAllEffect(effect);
                    } else {
                        effectSession.setEffect(selectedIndex, effect);
                    }
                    renderPreview();
                });
                effectButtons.add(button);
            }
        }
        effectButtons.revalidate();
        effectButtons.repaint();
    }

    private void renderPreview() {
        List<BufferedImage> photos = AppContext.session().getPhotos();
        if (photos.isEmpty()) return;
        PhotoEffect effect = effectSession.getEffect(selectedIndex);
        BufferedImage processed = PhotoEffectProcessor.apply(photos.get(selectedIndex), effect);
        largePreview.setIcon(new ImageIcon(scale(processed, 760, 570)));
        selectedLabel.setText(
                "FOTO " + (selectedIndex + 1) + " / " + photos.size()
                        + " — " + effect.getLabel()
        );
        revalidate();
        repaint();
    }

    private void finishEffects() {
        DebugLog.info("EffectScreen finished. applyAll=" + effectSession.isApplyToAll() + ", effects=" + effectSession.getEffects());
        AppContext.setEffectSession(effectSession);
        frame.showFinalResult();
    }

    private BufferedImage scale(BufferedImage image, int maxW, int maxH) {
        double scale = Math.min(maxW/(double)image.getWidth(), maxH/(double)image.getHeight());
        scale = Math.min(1.0, scale);
        int w = Math.max(1, (int)Math.round(image.getWidth()*scale));
        int h = Math.max(1, (int)Math.round(image.getHeight()*scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
