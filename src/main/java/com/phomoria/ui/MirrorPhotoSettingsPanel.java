package com.phomoria.ui;

import com.phomoria.config.AppSettings;

import javax.swing.*;
import java.awt.*;

public final class MirrorPhotoSettingsPanel extends JPanel {

    private final JRadioButton mirror =
            new JRadioButton("Mirror");

    private final JRadioButton normal =
            new JRadioButton("Normal");

    public MirrorPhotoSettingsPanel(
            AppSettings settings
    ) {
        setLayout(
                new FlowLayout(
                        FlowLayout.LEFT,
                        8,
                        4
                )
        );

        setBorder(
                BorderFactory.createTitledBorder(
                        "Orientasi Foto"
                )
        );

        ButtonGroup group =
                new ButtonGroup();

        group.add(mirror);
        group.add(normal);

        mirror.setSelected(
                settings.isMirrorPhoto()
        );

        normal.setSelected(
                !settings.isMirrorPhoto()
        );

        add(mirror);
        add(normal);
    }

    public void applyTo(
            AppSettings settings
    ) {
        settings.setMirrorPhoto(
                mirror.isSelected()
        );
    }
}
