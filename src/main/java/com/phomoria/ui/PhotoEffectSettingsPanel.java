package com.phomoria.ui;

import com.phomoria.effects.PhotoEffect;
import com.phomoria.effects.PhotoEffectSettings;

import javax.swing.*;
import java.awt.*;
import java.util.EnumSet;

public final class PhotoEffectSettingsPanel extends JPanel {
    private final JRadioButton enabled = new JRadioButton("Aktif");
    private final JRadioButton disabled = new JRadioButton("Nonaktif");
    private final JCheckBox sepia = new JCheckBox("Sepia");
    private final JCheckBox negative = new JCheckBox("Negatif");
    private final JCheckBox grayscale = new JCheckBox("Hitam Putih");
    private final JCheckBox warm = new JCheckBox("Warm");
    private final JCheckBox cool = new JCheckBox("Cool");

    public PhotoEffectSettingsPanel(PhotoEffectSettings settings) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createTitledBorder("Efek Foto Setelah Sesi"));

        ButtonGroup group = new ButtonGroup();
        group.add(enabled);
        group.add(disabled);

        PhotoEffectSettings safe = settings == null ? new PhotoEffectSettings() : settings;
        enabled.setSelected(safe.isEnabled());
        disabled.setSelected(!safe.isEnabled());

        sepia.setSelected(safe.isEffectAllowed(PhotoEffect.SEPIA));
        negative.setSelected(safe.isEffectAllowed(PhotoEffect.NEGATIVE));
        grayscale.setSelected(safe.isEffectAllowed(PhotoEffect.GRAYSCALE));
        warm.setSelected(safe.isEffectAllowed(PhotoEffect.WARM));
        cool.setSelected(safe.isEffectAllowed(PhotoEffect.COOL));

        enabled.addActionListener(e -> updateEnabledState());
        disabled.addActionListener(e -> updateEnabledState());

        add(enabled);
        add(disabled);
        add(Box.createVerticalStrut(8));
        add(new JLabel("Efek yang boleh dipilih customer:"));
        add(sepia);
        add(negative);
        add(grayscale);
        add(warm);
        add(cool);

        updateEnabledState();
    }

    private void updateEnabledState() {
        boolean active = enabled.isSelected();
        sepia.setEnabled(active);
        negative.setEnabled(active);
        grayscale.setEnabled(active);
        warm.setEnabled(active);
        cool.setEnabled(active);
    }

    public PhotoEffectSettings getSettings() {
        PhotoEffectSettings settings = new PhotoEffectSettings();
        settings.setEnabled(enabled.isSelected());

        EnumSet<PhotoEffect> effects = EnumSet.of(PhotoEffect.NORMAL);
        if (sepia.isSelected()) effects.add(PhotoEffect.SEPIA);
        if (negative.isSelected()) effects.add(PhotoEffect.NEGATIVE);
        if (grayscale.isSelected()) effects.add(PhotoEffect.GRAYSCALE);
        if (warm.isSelected()) effects.add(PhotoEffect.WARM);
        if (cool.isSelected()) effects.add(PhotoEffect.COOL);
        settings.setEnabledEffects(effects);

        return settings;
    }
}
