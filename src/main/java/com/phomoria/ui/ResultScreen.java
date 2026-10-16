package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.app.ApplicationFrame;
import com.phomoria.cloud.QrCodeGenerator;
import com.phomoria.cloud.UploadService;
import com.phomoria.debug.DebugLog;
import com.phomoria.effects.PhotoEffectSession;
import com.phomoria.frame.PrintPaperSize;
import com.phomoria.session.PhotoSession;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ResultScreen extends JPanel {
    private static final Set<String> AUTO_PRINT_ATTEMPTED = ConcurrentHashMap.newKeySet();
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
        printButton.addActionListener(e -> printResult(false));
        newSession.addActionListener(e -> {
            AppContext.newSession();
            frame.showStart();
        });

        buttons.add(qrButton);
        if (AppContext.settings().isViewPrintButton()) buttons.add(printButton);
        buttons.add(newSession);
        bottom.add(buttons, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }

    public void prepareResult() {
        PhotoSession session = AppContext.session();
        List<BufferedImage> originals = session.getPhotos();
        PhotoEffectSession effects = AppContext.effectSession();
        List<BufferedImage> processed = effects.process(originals);

        DebugLog.info(
                "Preparing final result. photos="
                        + originals.size()
                        + ", effects="
                        + effects.getEffects()
        );

        showPhotoStrip(processed);

        try {
            sessionFolder = createSessionFolder(originals, processed);
            renderedFile = new File(sessionFolder, "result.png");
            ResultRenderer.render(originals, renderedFile);
            if (!renderedFile.isFile() || renderedFile.length() <= 0) {
                throw new IllegalStateException("File hasil render tidak ditemukan atau kosong.");
            }
            BufferedImage renderedImage = ImageIO.read(renderedFile);
            if (renderedImage == null) {
                throw new IllegalStateException("File hasil render tidak dapat dibaca sebagai gambar.");
            }
            framePanel.setImage(renderedImage);
            uploadResult();
            SwingUtilities.invokeLater(() -> {
                PhotoSession current = AppContext.session();
                if (current == null || !AppContext.settings().isAutoPrint()) return;
                String id = current.getSessionId();
                if (AUTO_PRINT_ATTEMPTED.add(id)) {
                    DebugLog.info("Auto print triggered for session=" + id);
                    printResult(true);
                } else {
                    DebugLog.info("Auto print skipped; already attempted for session=" + id);
                }
            });
        } catch (Exception ex) {
            DebugLog.error("Failed to prepare final result.", ex);
            uploadStatus.setText(
                    "GAGAL MENYIAPKAN HASIL: "
                            + ex.getMessage()
            );
        }
    }

    private void showPhotoStrip(List<BufferedImage> photos) {
        photoStrip.removeAll();

        for (int i = 0; i < photos.size(); i++) {
            JPanel card = new JPanel(new BorderLayout(4, 4));
            card.setBackground(new Color(30, 30, 36));

            JLabel image = new JLabel(
                    new ImageIcon(
                            scale(
                                    photos.get(i),
                                    220,
                                    260
                            )
                    )
            );

            image.setHorizontalAlignment(SwingConstants.CENTER);

            JLabel label = new JLabel(
                    "FOTO " + (i + 1),
                    SwingConstants.CENTER
            );

            label.setForeground(Color.WHITE);

            card.add(image, BorderLayout.CENTER);
            card.add(label, BorderLayout.SOUTH);
            photoStrip.add(card);
        }

        photoStrip.revalidate();
        photoStrip.repaint();
    }

    private File createSessionFolder(
            List<BufferedImage> originals,
            List<BufferedImage> processed
    ) throws Exception {

        File root = new File(
                System.getenv().getOrDefault(
                        "APPDATA",
                        System.getProperty("user.home")
                ),
                "Phomoria/sessions"
        );

        if (!root.exists() && !root.mkdirs()) {
            throw new IllegalStateException(
                    "Tidak dapat membuat folder session."
            );
        }

        File folder = new File(
                root,
                "session-" + System.currentTimeMillis()
        );

        if (!folder.mkdirs()) {
            throw new IllegalStateException(
                    "Tidak dapat membuat folder hasil."
            );
        }

        for (int i = 0; i < originals.size(); i++) {
            ImageIO.write(
                    originals.get(i),
                    "jpg",
                    new File(
                            folder,
                            "photo_" + (i + 1) + ".jpg"
                    )
            );

            ImageIO.write(
                    processed.get(i),
                    "jpg",
                    new File(
                            folder,
                            "processed_" + (i + 1) + ".jpg"
                    )
            );
        }

        return folder;
    }

    private void uploadResult() {
        qrButton.setEnabled(false);
        startUploadAnimation();

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                DebugLog.info("Upload started.");

                return new UploadService().upload(
                        AppContext.settings().getFrameName(),
                        sessionFolder
                );
            }

            @Override
            protected void done() {
                stopUploadAnimation();

                try {
                    downloadUrl = get();

                    DebugLog.info(
                            "Upload completed. gallery_url="
                                    + downloadUrl
                    );

                    uploadStatus.setText(
                            "UPLOAD SELESAI — QR SIAP"
                    );

                    qrButton.setEnabled(
                            !downloadUrl.isBlank()
                    );

                } catch (Exception ex) {
                    DebugLog.error(
                            "Upload failed.",
                            ex
                    );

                    uploadStatus.setText(
                            "UPLOAD GAGAL — COBA LAGI"
                    );
                }
            }
        }.execute();
    }

    private void startUploadAnimation() {
        uploadDots = 0;
        uploadStatus.setText("UPLOADING");

        uploadAnimationTimer = new Timer(
                350,
                e -> {
                    uploadDots =
                            (uploadDots + 1) % 4;

                    uploadStatus.setText(
                            "UPLOADING"
                                    + ".".repeat(uploadDots)
                    );
                }
        );

        uploadAnimationTimer.start();
    }

    private void stopUploadAnimation() {
        if (uploadAnimationTimer != null) {
            uploadAnimationTimer.stop();
            uploadAnimationTimer = null;
        }
    }

    private void showQr() {
        if (downloadUrl == null
                || downloadUrl.isBlank()) {
            return;
        }

        try {
            BufferedImage qr =
                    QrCodeGenerator.generate(
                            downloadUrl,
                            430
                    );

            JLabel image =
                    new JLabel(
                            new ImageIcon(qr)
                    );

            image.setHorizontalAlignment(
                    SwingConstants.CENTER
            );

            JPanel panel =
                    new JPanel(
                            new BorderLayout(
                                    8,
                                    8
                            )
                    );

            panel.add(
                    new JLabel(
                            "SCAN UNTUK DOWNLOAD",
                            SwingConstants.CENTER
                    ),
                    BorderLayout.NORTH
            );

            panel.add(
                    image,
                    BorderLayout.CENTER
            );

            panel.add(
                    new JLabel(
                            "<html><center>"
                                    + downloadUrl
                                    + "</center></html>",
                            SwingConstants.CENTER
                    ),
                    BorderLayout.SOUTH
            );

            JOptionPane.showMessageDialog(
                    this,
                    panel,
                    "QR DOWNLOAD",
                    JOptionPane.PLAIN_MESSAGE
            );

        } catch (Exception ex) {
            DebugLog.error(
                    "QR generation failed.",
                    ex
            );
        }
    }

    /**
     * Silent printing:
     * - never opens the Windows/native print dialog;
     * - uses the configured paper size from Phomoria Settings;
     * - prints exactly one copy;
     * - keeps the printer itself and other driver options under Windows
     *   Control Panel / printer defaults.
     */
    private void printResult() {
        printResult(false);
    }

    private void printResult(boolean automatic) {
        if (renderedFile == null || !renderedFile.isFile() || renderedFile.length() <= 0) {
            if (!automatic) JOptionPane.showMessageDialog(this, "Hasil foto belum tersedia.");
            return;
        }

        if (!automatic && AppContext.settings().isViewPrintButton()) printButton.setEnabled(false);
        uploadStatus.setText(automatic ? "AUTO PRINT..." : "MENCETAK...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground()
                    throws Exception {

                silentPrint(
                        renderedFile,
                        AppContext.settings().getPrintPaperSize(),
                        AppContext.settings().getPrinterName()
                );

                return null;
            }

            @Override
            protected void done() {
                if (AppContext.settings().isViewPrintButton()) printButton.setEnabled(true);

                try {
                    get();

                    uploadStatus.setText(automatic ? "AUTO PRINT SELESAI" : "PRINT SELESAI");

                    DebugLog.info(
                            "Silent print completed."
                    );

                } catch (Exception ex) {
                    DebugLog.error(
                            "Silent print failed.",
                            ex
                    );

                    uploadStatus.setText(
                            "PRINT GAGAL"
                    );

                    if (!automatic) {
                        JOptionPane.showMessageDialog(
                                ResultScreen.this,
                                "Print gagal: " + rootCauseMessage(ex)
                        );
                    }
                }
            }
        }.execute();
    }

    private void silentPrint(
            File imageFile,
            PrintPaperSize configuredSize,
            String printerName
    ) throws Exception {

        BufferedImage image =
                ImageIO.read(imageFile);

        if (image == null) {
            throw new IllegalStateException(
                    "File hasil foto tidak dapat dibaca."
            );
        }

        PrintPaperSize paperSize =
                configuredSize == null
                        ? PrintPaperSize.FOUR_BY_SIX
                        : configuredSize;

        double widthInches;
        double heightInches;

        if (paperSize.isCustom()) {
            widthInches =
                    AppContext.settings()
                            .getCustomPaperWidthInches();

            heightInches =
                    AppContext.settings()
                            .getCustomPaperHeightInches();

        } else {
            widthInches =
                    paperSize.getWidthInches();

            heightInches =
                    paperSize.getHeightInches();
        }

        if (widthInches <= 0
                || heightInches <= 0) {

            throw new IllegalStateException(
                    "Ukuran kertas print tidak valid."
            );
        }

        PrinterJob job =
                PrinterJob.getPrinterJob();

        javax.print.PrintService selectedService =
                com.phomoria.print.PrinterService.resolvePrintService(printerName);
        job.setPrintService(selectedService);
        DebugLog.info("Silent print selected printer=" + selectedService.getName());

        PageFormat pageFormat =
                createPageFormat(
                        job,
                        widthInches,
                        heightInches,
                        image.getWidth(),
                        image.getHeight()
                );

        job.setJobName("Phomoria Photo");

        job.setPrintable(
                createPrintable(image),
                pageFormat
        );

        /*
         * Match PrinterService.testPrint(): use print() without a
         * PrintRequestAttributeSet. Paper size and orientation are
         * supplied through PageFormat. This avoids passing MediaSize,
         * which is not a PrintRequestAttribute.
         */
        DebugLog.info("Silent print submitting job.");
        job.print();
        DebugLog.info("Silent print job submitted successfully.");
    }

    private PageFormat createPageFormat(
            PrinterJob job,
            double widthInches,
            double heightInches,
            int imageWidth,
            int imageHeight
    ) {

        double widthPoints =
                widthInches * 72.0;

        double heightPoints =
                heightInches * 72.0;

        Paper paper = new Paper();

        paper.setSize(
                widthPoints,
                heightPoints
        );

        /*
         * Use the whole configured sheet as the logical print area.
         * The printer driver remains responsible for its physical
         * printable margins.
         */
        paper.setImageableArea(
                0,
                0,
                widthPoints,
                heightPoints
        );

        PageFormat format =
                job.defaultPage();

        format.setPaper(paper);

        if (imageWidth > imageHeight) {
            format.setOrientation(
                    PageFormat.LANDSCAPE
            );
        } else {
            format.setOrientation(
                    PageFormat.PORTRAIT
            );
        }

        return job.validatePage(format);
    }

    private Printable createPrintable(
            BufferedImage image
    ) {

        return (graphics, pageFormat, pageIndex) -> {

            if (pageIndex > 0) {
                return Printable.NO_SUCH_PAGE;
            }

            Graphics2D g =
                    (Graphics2D) graphics.create();

            try {
                g.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC
                );

                g.setRenderingHint(
                        RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY
                );

                double availableWidth =
                        pageFormat.getImageableWidth();

                double availableHeight =
                        pageFormat.getImageableHeight();

                double scale =
                        Math.min(
                                availableWidth
                                        / image.getWidth(),
                                availableHeight
                                        / image.getHeight()
                        );

                double drawWidth =
                        image.getWidth() * scale;

                double drawHeight =
                        image.getHeight() * scale;

                double x =
                        pageFormat.getImageableX()
                                + (availableWidth
                                - drawWidth) / 2.0;

                double y =
                        pageFormat.getImageableY()
                                + (availableHeight
                                - drawHeight) / 2.0;

                g.drawImage(
                        image,
                        (int) Math.round(x),
                        (int) Math.round(y),
                        (int) Math.round(drawWidth),
                        (int) Math.round(drawHeight),
                        null
                );

                return Printable.PAGE_EXISTS;

            } finally {
                g.dispose();
            }
        };
    }

    private String rootCauseMessage(
            Throwable throwable
    ) {

        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message =
                current.getMessage();

        return message == null
                || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    private BufferedImage scale(
            BufferedImage image,
            int maxW,
            int maxH
    ) {

        double scale =
                Math.min(
                        maxW
                                / (double) image.getWidth(),
                        maxH
                                / (double) image.getHeight()
                );

        int w =
                Math.max(
                        1,
                        (int) Math.round(
                                image.getWidth()
                                        * scale
                        )
                );

        int h =
                Math.max(
                        1,
                        (int) Math.round(
                                image.getHeight()
                                        * scale
                        )
                );

        BufferedImage out =
                new BufferedImage(
                        w,
                        h,
                        BufferedImage.TYPE_INT_RGB
                );

        Graphics2D g =
                out.createGraphics();

        g.drawImage(
                image,
                0,
                0,
                w,
                h,
                null
        );

        g.dispose();

        return out;
    }
}
