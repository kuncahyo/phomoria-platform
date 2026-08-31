package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.app.ApplicationFrame;
import com.phomoria.cloud.QrCodeGenerator;
import com.phomoria.cloud.UploadService;
import com.phomoria.debug.DebugLog;
import com.phomoria.effects.PhotoEffectSession;
import com.phomoria.session.PhotoSession;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public final class ResultScreen extends JPanel {
    private final ApplicationFrame frame;
    private final ResultFramePanel framePanel = new ResultFramePanel();
    private final JPanel photoStrip = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
    private final JLabel uploadStatus = new JLabel("Mempersiapkan hasil...", SwingConstants.CENTER);
    private final JButton qrButton = new JButton("TAMPILKAN QR DOWNLOAD");
    private final JButton printButton = new JButton("PRINT");
    private File sessionFolder;
    private File renderedFile;
    private String downloadUrl = "";
    private Timer uploadAnimationTimer;
    private int uploadDots;

    public ResultScreen(ApplicationFrame frame) {
        this.frame = frame;
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));
        setBackground(new Color(18, 18, 22));

        JLabel title = new JLabel("HASIL FOTO", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        add(title, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.68);
        split.setBorder(null);
        split.setLeftComponent(framePanel);

        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setBackground(new Color(24, 24, 28));
        JLabel photosTitle = new JLabel("FOTO INDIVIDUAL", SwingConstants.CENTER);
        photosTitle.setForeground(Color.WHITE);
        photosTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        right.add(photosTitle, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(photoStrip);
        scroll.setBorder(null);
        right.add(scroll, BorderLayout.CENTER);
        split.setRightComponent(right);
        add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(10, 8));
        bottom.setOpaque(false);
        uploadStatus.setForeground(new Color(200, 200, 210));
        bottom.add(uploadStatus, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 5));
        buttons.setOpaque(false);
        qrButton.setEnabled(false);
        JButton newSession = new JButton("NEW SESSION");
        qrButton.addActionListener(e -> showQr());
        printButton.addActionListener(e -> printResult());
        newSession.addActionListener(e -> { AppContext.newSession(); frame.showStart(); });
        buttons.add(qrButton);
        buttons.add(printButton);
        buttons.add(newSession);
        bottom.add(buttons, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }

    public void prepareResult() {
        PhotoSession session = AppContext.session();
        List<BufferedImage> originals = session.getPhotos();
        PhotoEffectSession effects = AppContext.effectSession();
        List<BufferedImage> processed = effects.process(originals);

        DebugLog.info("Preparing final result. photos=" + originals.size() + ", effects=" + effects.getEffects());
        showPhotoStrip(processed);

        try {
            sessionFolder = createSessionFolder(originals, processed);
            renderedFile = new File(sessionFolder, "result.png");
            ResultRenderer.render(originals, renderedFile);
            framePanel.setImage(ImageIO.read(renderedFile));
            uploadResult();
        } catch (Exception ex) {
            DebugLog.error("Failed to prepare final result.", ex);
            uploadStatus.setText("GAGAL MENYIAPKAN HASIL: " + ex.getMessage());
        }
    }

    private void showPhotoStrip(List<BufferedImage> photos) {
        photoStrip.removeAll();
        for (int i = 0; i < photos.size(); i++) {
            JPanel card = new JPanel(new BorderLayout(4, 4));
            card.setBackground(new Color(30, 30, 36));
            JLabel image = new JLabel(new ImageIcon(scale(photos.get(i), 220, 260)));
            image.setHorizontalAlignment(SwingConstants.CENTER);
            JLabel label = new JLabel("FOTO " + (i + 1), SwingConstants.CENTER);
            label.setForeground(Color.WHITE);
            card.add(image, BorderLayout.CENTER);
            card.add(label, BorderLayout.SOUTH);
            photoStrip.add(card);
        }
        photoStrip.revalidate();
        photoStrip.repaint();
    }

    private File createSessionFolder(List<BufferedImage> originals, List<BufferedImage> processed) throws Exception {
        File root = new File(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "Phomoria/sessions");
        if (!root.exists() && !root.mkdirs()) throw new IllegalStateException("Tidak dapat membuat folder session.");
        File folder = new File(root, "session-" + System.currentTimeMillis());
        if (!folder.mkdirs()) throw new IllegalStateException("Tidak dapat membuat folder hasil.");
        for (int i = 0; i < originals.size(); i++) {
            ImageIO.write(originals.get(i), "jpg", new File(folder, "photo_" + (i + 1) + ".jpg"));
            ImageIO.write(processed.get(i), "jpg", new File(folder, "processed_" + (i + 1) + ".jpg"));
        }
        return folder;
    }

    private void uploadResult() {
        qrButton.setEnabled(false);
        startUploadAnimation();
        new SwingWorker<String, Void>() {
            protected String doInBackground() throws Exception {
                DebugLog.info("Upload started.");
                return new UploadService().upload(AppContext.settings().getFrameName(), sessionFolder);
            }
            protected void done() {
                stopUploadAnimation();
                try {
                    downloadUrl = get();
                    DebugLog.info("Upload completed. gallery_url=" + downloadUrl);
                    uploadStatus.setText("UPLOAD SELESAI — QR SIAP");
                    qrButton.setEnabled(!downloadUrl.isBlank());
                } catch (Exception ex) {
                    DebugLog.error("Upload failed.", ex);
                    uploadStatus.setText("UPLOAD GAGAL — COBA LAGI");
                }
            }
        }.execute();
    }

    private void startUploadAnimation() {
        uploadDots = 0;
        uploadStatus.setText("UPLOADING");
        uploadAnimationTimer = new Timer(350, e -> {
            uploadDots = (uploadDots + 1) % 4;
            uploadStatus.setText("UPLOADING" + ".".repeat(uploadDots));
        });
        uploadAnimationTimer.start();
    }

    private void stopUploadAnimation() {
        if (uploadAnimationTimer != null) { uploadAnimationTimer.stop(); uploadAnimationTimer = null; }
    }

    private void showQr() {
        if (downloadUrl == null || downloadUrl.isBlank()) return;
        try {
            BufferedImage qr = QrCodeGenerator.generate(downloadUrl, 430);
            JLabel image = new JLabel(new ImageIcon(qr));
            image.setHorizontalAlignment(SwingConstants.CENTER);
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.add(new JLabel("SCAN UNTUK DOWNLOAD", SwingConstants.CENTER), BorderLayout.NORTH);
            panel.add(image, BorderLayout.CENTER);
            panel.add(new JLabel("<html><center>" + downloadUrl + "</center></html>", SwingConstants.CENTER), BorderLayout.SOUTH);
            JOptionPane.showMessageDialog(this, panel, "QR DOWNLOAD", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) { DebugLog.error("QR generation failed.", ex); }
    }

    private void printResult() {
        if (renderedFile == null || !renderedFile.exists()) { JOptionPane.showMessageDialog(this, "Hasil foto belum tersedia."); return; }
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.PRINT)) {
                JOptionPane.showMessageDialog(this, "Fungsi print tidak tersedia."); return;
            }
            Desktop.getDesktop().print(renderedFile);
            uploadStatus.setText("Perintah print dikirim.");
        } catch (Exception ex) {
            DebugLog.error("Print failed.", ex);
            JOptionPane.showMessageDialog(this, "Print gagal: " + ex.getMessage());
        }
    }

    private BufferedImage scale(BufferedImage image, int maxW, int maxH) {
        double scale = Math.min(maxW/(double)image.getWidth(), maxH/(double)image.getHeight());
        int w = Math.max(1, (int)Math.round(image.getWidth()*scale));
        int h = Math.max(1, (int)Math.round(image.getHeight()*scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
