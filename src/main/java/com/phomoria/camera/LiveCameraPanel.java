package com.phomoria.camera;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.phomoria.debug.DebugLog;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;

public final class LiveCameraPanel extends JPanel {

    private WebcamPanel webcamPanel;

    public LiveCameraPanel() {
        super(new BorderLayout());
        setBackground(Color.BLACK);
    }

    public void attach(Webcam webcam) {
        removeAll();

        if (webcam == null) {
            showMessage("CAMERA TIDAK TERSEDIA");

            DebugLog.warn(
                    "LiveCameraPanel.attach(): webcam is null."
            );

            return;
        }

        DebugLog.info(
                "LiveCameraPanel.attach(): "
                        + webcam.getName()
        );

        webcamPanel = new WebcamPanel(webcam);
        webcamPanel.setMirrored(true);
        webcamPanel.setFillArea(true);

        add(
                webcamPanel,
                BorderLayout.CENTER
        );

        revalidate();
        repaint();

        DebugLog.info(
                "Live camera view attached."
        );
    }

    public void showCapturedImage(BufferedImage image) {
        removeAll();

        if (image == null) {
            showMessage("NO IMAGE");

            DebugLog.warn(
                    "LiveCameraPanel.showCapturedImage(): "
                            + "image is null."
            );

            return;
        }

        JLabel label = new JLabel();

        label.setHorizontalAlignment(
                SwingConstants.CENTER
        );

        label.setVerticalAlignment(
                SwingConstants.CENTER
        );

        label.setOpaque(true);
        label.setBackground(Color.BLACK);

        ImageIcon icon = new ImageIcon(image);
        label.setIcon(icon);

        add(
                label,
                BorderLayout.CENTER
        );

        revalidate();
        repaint();

        DebugLog.info(
                "Captured image displayed: "
                        + image.getWidth()
                        + "x"
                        + image.getHeight()
        );
    }

    private void showMessage(String message) {
        JLabel label = new JLabel();

        label.setHorizontalAlignment(
                SwingConstants.CENTER
        );

        label.setVerticalAlignment(
                SwingConstants.CENTER
        );

        label.setText(message);
        label.setForeground(Color.WHITE);
        label.setBackground(Color.BLACK);
        label.setOpaque(true);

        label.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        24
                )
        );

        add(
                label,
                BorderLayout.CENTER
        );

        revalidate();
        repaint();
    }

    public void clear() {
        removeAll();

        webcamPanel = null;

        revalidate();
        repaint();

        DebugLog.info(
                "LiveCameraPanel cleared."
        );
    }
}
