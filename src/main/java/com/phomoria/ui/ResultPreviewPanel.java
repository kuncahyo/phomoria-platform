package com.phomoria.ui;

import com.phomoria.effects.PhotoEffect;
import com.phomoria.effects.PhotoEffectProcessor;
import com.phomoria.effects.PhotoEffectSettings;
import com.phomoria.session.PhotoSession;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public final class ResultPreviewPanel extends JPanel {
    public interface EffectListener {
        void effectChanged(PhotoEffect effect);
    }

    private final JLabel imageLabel = new JLabel("", SwingConstants.CENTER);
    private final JComboBox<PhotoEffect> effectBox =
            new JComboBox<>(PhotoEffect.values());

    private final JLabel slotLabel =
            new JLabel("", SwingConstants.CENTER);

    private List<BufferedImage> photos = List.of();
    private int selectedSlot = 0;
    private PhotoEffectSettings effectSettings = new PhotoEffectSettings();
    private EffectListener effectListener;

    public ResultPreviewPanel() {
        setLayout(new BorderLayout(12, 12));
        setBackground(new Color(24, 24, 28));

        imageLabel.setOpaque(true);
        imageLabel.setBackground(Color.BLACK);

        add(imageLabel, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(
                FlowLayout.CENTER,
                12,
                8
        ));
        controls.setOpaque(false);

        slotLabel.setForeground(Color.WHITE);
        effectBox.addActionListener(e -> {
            PhotoEffect effect =
                    (PhotoEffect) effectBox.getSelectedItem();

            if (effect != null) {
                renderCurrent(effect);

                if (effectListener != null) {
                    effectListener.effectChanged(effect);
                }
            }
        });

        controls.add(slotLabel);
        controls.add(new JLabel("Efek:"));
        controls.add(effectBox);

        add(controls, BorderLayout.SOUTH);
    }

    public void setEffectListener(EffectListener listener) {
        this.effectListener = listener;
    }

    public void setEffectSettings(PhotoEffectSettings settings) {
        if (settings != null) {
            effectSettings = settings;
        }

        effectBox.removeAllItems();

        for (PhotoEffect effect : PhotoEffect.values()) {
            if (!effectSettings.isEnabled()
                    || effectSettings.isEffectAllowed(effect)) {
                effectBox.addItem(effect);
            }
        }

        if (effectBox.getItemCount() == 0) {
            effectBox.addItem(PhotoEffect.NORMAL);
        }

        renderCurrent((PhotoEffect) effectBox.getSelectedItem());
    }

    public void setPhotos(List<BufferedImage> photos) {
        this.photos = photos == null ? List.of() : photos;

        if (selectedSlot >= this.photos.size()) {
            selectedSlot = Math.max(0, this.photos.size() - 1);
        }

        renderCurrent((PhotoEffect) effectBox.getSelectedItem());
    }

    public void selectSlot(int index) {
        if (index < 0 || index >= photos.size()) return;

        selectedSlot = index;
        renderCurrent((PhotoEffect) effectBox.getSelectedItem());
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }

    public BufferedImage getProcessedSelectedImage() {
        if (photos.isEmpty()) return null;

        PhotoEffect effect =
                (PhotoEffect) effectBox.getSelectedItem();

        return PhotoEffectProcessor.apply(
                photos.get(selectedSlot),
                effect == null ? PhotoEffect.NORMAL : effect
        );
    }

    private void renderCurrent(PhotoEffect effect) {
        if (photos.isEmpty()) {
            imageLabel.setIcon(null);
            imageLabel.setText("BELUM ADA FOTO");
            slotLabel.setText("");
            return;
        }

        BufferedImage image =
                PhotoEffectProcessor.apply(
                        photos.get(selectedSlot),
                        effect == null
                                ? PhotoEffect.NORMAL
                                : effect
                );

        imageLabel.setText("");
        imageLabel.setIcon(
                new ImageIcon(scale(image, 900, 760))
        );

        slotLabel.setText(
                "FOTO " + (selectedSlot + 1)
                        + " / " + photos.size()
        );

        revalidate();
        repaint();
    }

    private BufferedImage scale(
            BufferedImage image,
            int maxW,
            int maxH
    ) {
        double scale = Math.min(
                maxW / (double) image.getWidth(),
                maxH / (double) image.getHeight()
        );

        scale = Math.min(1.0, scale);

        int w = Math.max(
                1,
                (int) Math.round(image.getWidth() * scale)
        );

        int h = Math.max(
                1,
                (int) Math.round(image.getHeight() * scale)
        );

        BufferedImage result = new BufferedImage(
                w,
                h,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = result.createGraphics();
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();

        return result;
    }
}
