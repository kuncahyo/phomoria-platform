package com.phomoria.ui;

import com.phomoria.app.AppContext;
import com.phomoria.app.ApplicationFrame;
import com.phomoria.cloud.QrCodeGenerator;
import com.phomoria.cloud.UploadService;
import com.phomoria.debug.DebugLog;
import com.phomoria.camera.LivePhotoPreviewDecoder;
import com.phomoria.effects.PhotoEffectSession;
import com.phomoria.frame.PrintPaperSize;
import com.phomoria.session.PhotoSession;
import com.phomoria.upload.UploadQueueEntry;
import com.phomoria.upload.UploadQueueManager;
import com.phomoria.theme.ThemeButton;
import com.phomoria.theme.ThemeBackgroundPanel;
import com.phomoria.theme.ThemeManager;
import com.phomoria.theme.ThemeCountdownTimer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ResultScreen extends JPanel {
    private static final Set<String> AUTO_PRINT_ATTEMPTED = ConcurrentHashMap.newKeySet();
    private final ApplicationFrame frame;
    private final ResultFramePanel framePanel = new ResultFramePanel();
    private final LivePhotoLoadingPanel livePhotoLoadingPanel = new LivePhotoLoadingPanel();
    private final JPanel photoStrip = new JPanel(new GridLayout(0, 1, 8, 8));
    private final JPanel photoContent = new JPanel(new BorderLayout(8, 10));
    private final JPanel gifPreviewPanel = new MediaCardPanel(new BorderLayout(6, 6));
    private final JPanel gifPreviewHolder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    private final ResponsiveImageLabel gifPreviewLabel = new ResponsiveImageLabel("GIF BELUM TERSEDIA");
    private final JLabel gifTitle = new JLabel("ANIMATED GIF", SwingConstants.CENTER);
    private File gifFile;
    private final JLabel uploadStatus = new JLabel("Mempersiapkan hasil...", SwingConstants.CENTER);
    private final ThemeButton qrButton = new ThemeButton("result-qr", "TAMPILKAN QR DOWNLOAD");
    private final ThemeButton printButton = new ThemeButton("result-print", "PRINT");
    private final ThemeButton newSessionButton = new ThemeButton("result-new-session", "NEW SESSION");
    private final JLabel screenTitle = new JLabel("HASIL FOTO", SwingConstants.CENTER);
    private final JLabel photosTitle = new JLabel("FOTO INDIVIDUAL", SwingConstants.CENTER);
    private final JScrollPane photoScroll = new JScrollPane(photoContent);
    private final ThemeBackgroundPanel themeBackground = new ThemeBackgroundPanel("result", 0.18f);
    private final ThemeCountdownTimer pageTimer = new ThemeCountdownTimer("result-timer");

    private File sessionFolder;
    private File renderedFile;
    private String downloadUrl = "";
    private Timer uploadAnimationTimer;
    private Timer uploadStatusPollTimer;
    private String activeUploadSessionId = "";
    private long resultPreparedAt;
    private int uploadDots;

    public ResultScreen(ApplicationFrame frame) {
        this.frame = frame;
        setLayout(new ResultScreenThemeLayout());
        setOpaque(false);
        setBorder(null);
        add(themeBackground, "result-background");

        screenTitle.setForeground(Color.WHITE);
        screenTitle.setFont(new Font("SansSerif", Font.BOLD, 28));
        add(screenTitle, "result-title");

        // Let the themed screen background show through the preview letterbox area.
        framePanel.setOpaque(false);
        framePanel.setBackground(new Color(0, 0, 0, 0));
        add(framePanel, "result-preview");
        livePhotoLoadingPanel.setVisible(false);
        add(livePhotoLoadingPanel, "result-live-photo-loading");

        photosTitle.setForeground(Color.WHITE);
        photosTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        add(photosTitle, "result-photo-list-title");
        // Compact media cards create a consistent gallery rhythm over the themed background.
        photoContent.setOpaque(false);
        photoContent.setBackground(new Color(0, 0, 0, 0));
        photoStrip.setOpaque(false);
        photoStrip.setBackground(new Color(0, 0, 0, 0));
        photoContent.add(photoStrip, BorderLayout.NORTH);
        gifPreviewPanel.setOpaque(false);
        gifPreviewPanel.setBackground(new Color(0, 0, 0, 0));
        gifPreviewPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        gifPreviewPanel.setPreferredSize(new Dimension(380, 300));
        gifPreviewPanel.setMinimumSize(new Dimension(250, 200));
        gifPreviewHolder.setOpaque(false);
        gifPreviewHolder.setBackground(new Color(0, 0, 0, 0));
        gifPreviewHolder.add(gifPreviewPanel);
        gifTitle.setForeground(Color.WHITE);
        gifTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        gifPreviewLabel.setForeground(new Color(230, 230, 235));
        gifPreviewLabel.setOpaque(false);
        gifPreviewLabel.setPreferredSize(new Dimension(220, 130));
        gifPreviewPanel.add(gifTitle, BorderLayout.NORTH);
        gifPreviewPanel.add(gifPreviewLabel, BorderLayout.CENTER);
        photoContent.add(gifPreviewHolder, BorderLayout.SOUTH);

        photoScroll.setBorder(null);
        photoScroll.setOpaque(false);
        photoScroll.getViewport().setOpaque(false);
        photoScroll.getViewport().setBackground(new Color(0, 0, 0, 0));
        photoScroll.getVerticalScrollBar().setOpaque(false);
        photoScroll.getHorizontalScrollBar().setOpaque(false);
        photoScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        photoScroll.getViewport().addChangeListener(e -> updatePhotoGridColumns());
        add(photoScroll, "result-photo-list");
        installDragToScroll(photoScroll.getViewport());
        installDragToScroll(photoContent);
        installDragToScroll(photoStrip);

        uploadStatus.setForeground(new Color(200, 200, 210));
        add(uploadStatus, "result-upload-status");

        qrButton.setEnabled(false);
        qrButton.addActionListener(e -> showQr());
        printButton.addActionListener(e -> printResult(false));
        newSessionButton.addActionListener(e -> {
            pageTimer.stop();
            if (uploadStatusPollTimer != null) {
                uploadStatusPollTimer.stop();
                uploadStatusPollTimer = null;
            }
            activeUploadSessionId = "";
            downloadUrl = "";
            qrButton.setEnabled(false);
            AppContext.newSession();
            frame.showStart();
        });
        add(qrButton, "result-qr");
        add(printButton, "result-print");
        add(newSessionButton, "result-new-session");
        add(pageTimer, "result-timer");
        setComponentZOrder(themeBackground, getComponentCount() - 1);
        printButton.setVisible(AppContext.settings().isViewPrintButton());

        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && !isShowing()) stopPlayback();
        });
        applyResultTheme();
        printButton.setVisible(AppContext.settings().isViewPrintButton());
    }

    private void applyResultTheme() {
        revalidate();
        repaint();
    }

    /** Touchscreen/mouse drag scrolling for the individual-photo column. */
    private void installDragToScroll(Component component) {
        if (component == null || Boolean.TRUE.equals(((JComponent) component).getClientProperty("phomoria.result.dragScroll"))) return;
        ((JComponent) component).putClientProperty("phomoria.result.dragScroll", Boolean.TRUE);
        final Point[] press = {null};
        final Point[] viewportStart = {null};
        final boolean[] dragged = {false};
        component.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                press[0] = SwingUtilities.convertPoint(component, e.getPoint(), photoScroll.getViewport());
                viewportStart[0] = photoScroll.getViewport().getViewPosition();
                dragged[0] = false;
            }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                if (dragged[0]) e.consume();
                press[0] = null;
                viewportStart[0] = null;
            }
        });
        component.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                if (press[0] == null || viewportStart[0] == null) return;
                Point now = SwingUtilities.convertPoint(component, e.getPoint(), photoScroll.getViewport());
                int dy = now.y - press[0].y;
                if (!dragged[0] && Math.abs(dy) < 7) return;
                dragged[0] = true;
                JViewport viewport = photoScroll.getViewport();
                int maxY = Math.max(0, viewport.getViewSize().height - viewport.getExtentSize().height);
                int y = Math.max(0, Math.min(maxY, viewportStart[0].y - dy));
                viewport.setViewPosition(new Point(viewportStart[0].x, y));
                e.consume();
            }
        });
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) if (child instanceof JComponent) installDragToScroll(child);
        }
    }

    public void prepareResult() {
        pageTimer.applyTheme("result-timer");
        pageTimer.start(AppContext.settings().getResultTimerSeconds(), newSessionButton::doClick);
        if (uploadStatusPollTimer != null) {
            uploadStatusPollTimer.stop();
            uploadStatusPollTimer = null;
        }
        PhotoSession session = AppContext.session();
        activeUploadSessionId = session == null ? "" : session.getSessionId();
        resultPreparedAt = System.currentTimeMillis();
        downloadUrl = "";
        qrButton.setEnabled(false);
        List<BufferedImage> originals = session.getPhotos();
        PhotoEffectSession effects = AppContext.effectSession();
        List<BufferedImage> processed = effects.process(originals);

        DebugLog.info(
                "Preparing final result. photos="
                        + originals.size()
                        + ", effects="
                        + effects.getEffects()
        );

        printButton.setVisible(AppContext.settings().isViewPrintButton());
        applyResultTheme();
        printButton.setVisible(AppContext.settings().isViewPrintButton());
        showPhotoStrip(processed);

        try {
            sessionFolder = createSessionFolder(originals, processed);
            createAnimatedGifSafely(processed);
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
            prepareLivePhotoAndUpload(session, resultPreparedAt);
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

    /** Stop playback and release decoded frames when Final Preview is left. */
    public void stopPlayback() {
        framePanel.stopAnimation();
        livePhotoLoadingPanel.setActive(false);
    }

    /**
     * Render the Live Photo MP4 before starting the multipart upload so the
     * desktop uploader can include it. A Live Photo failure must never block
     * the ordinary result, GIF, or original-photo upload.
     */
    private void prepareLivePhotoAndUpload(PhotoSession session, long previewStartedAt) {
        final File currentSessionFolder = sessionFolder;
        final String currentFrameName = AppContext.settings().getFrameName();
        final long currentPreviewStartedAt = previewStartedAt;
        boolean isActiveSession = session != null
                && session.getSessionId().equals(activeUploadSessionId);
        if (!AppContext.settings().isLivePhotoEnabled()
                || session == null
                || session.getLivePhotoClips().isEmpty()
                || currentSessionFolder == null) {
            if (isActiveSession) stopLivePhotoLoading();
            uploadResult(session, currentSessionFolder, currentFrameName, currentPreviewStartedAt);
            return;
        }

        if (isActiveSession) {
            startLivePhotoLoading();
            uploadStatus.setText("MENYIAPKAN LIVE PHOTO...");
        }
        new SwingWorker<List<BufferedImage>, Void>() {
            private Path previewWorkDirectory;

            @Override
            protected List<BufferedImage> doInBackground() throws Exception {
                Path movie = currentSessionFolder.toPath().resolve("live_photo.mp4");
                Path frames = ResultRenderer.renderLivePhotoVideo(session, movie);
                if (frames == null || !Files.isRegularFile(movie)
                        || Files.size(movie) <= 0) {
                    throw new IllegalStateException("MP4 Live Photo tidak berhasil dibuat.");
                }
                previewWorkDirectory = currentSessionFolder.toPath().resolve("live-photo-preview-frames");
                return LivePhotoPreviewDecoder.decode(frames, previewWorkDirectory);
            }

            @Override
            protected void done() {
                cleanupPreviewFrames();
                try {
                    List<BufferedImage> frames = get();
                    if (session.getSessionId().equals(activeUploadSessionId)
                            && isShowing() && !frames.isEmpty()) {
                        framePanel.setAnimationFrames(frames);
                        // The preview is now decoded and its playback timer is running.
                        // Hide only the Live Photo preparation indicator; upload continues independently.
                        stopLivePhotoLoading();
                        DebugLog.info("Live Photo Final Preview playback started.");
                    }
                } catch (Exception ex) {
                    DebugLog.error("Live Photo preparation failed; continuing standard upload.", ex);
                } finally {
                    Path livePhotoFile = currentSessionFolder.toPath().resolve("live_photo.mp4");
                    boolean livePhotoReady = Files.isRegularFile(livePhotoFile);
                    try {
                        livePhotoReady = livePhotoReady && Files.size(livePhotoFile) > 0;
                    } catch (Exception ignored) {
                        livePhotoReady = false;
                    }
                    if (!livePhotoReady
                            && session.getSessionId().equals(activeUploadSessionId)) {
                        stopLivePhotoLoading();
                    }
                    // Always enqueue the captured session/folder, even if the
                    // customer has already moved to a newer session.
                    uploadResult(session, currentSessionFolder, currentFrameName, currentPreviewStartedAt);
                }
            }

            private void cleanupPreviewFrames() {
                if (previewWorkDirectory == null || !Files.exists(previewWorkDirectory)) return;
                try (var stream = Files.walk(previewWorkDirectory)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (Exception ignored) {}
                    });
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void showPhotoStrip(List<BufferedImage> photos) {
        photoStrip.removeAll();

        for (int i = 0; i < photos.size(); i++) {
            JPanel card = new MediaCardPanel(new BorderLayout(5, 7));
            card.setBorder(BorderFactory.createEmptyBorder(8, 8, 7, 8));
            card.setPreferredSize(new Dimension(180, 210));
            card.setMinimumSize(new Dimension(100, 130));

            ResponsiveImageLabel image = new ResponsiveImageLabel("");
            image.setImage(photos.get(i));
            image.setOpaque(false);
            image.setHorizontalAlignment(SwingConstants.CENTER);
            image.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 85), 1));

            JLabel label = new JLabel("FOTO  " + String.format("%02d", i + 1), SwingConstants.CENTER);
            label.setOpaque(false);
            label.setForeground(new Color(250, 250, 252));
            label.setFont(new Font("SansSerif", Font.BOLD, 11));

            card.add(image, BorderLayout.CENTER);
            card.add(label, BorderLayout.SOUTH);
            installDragToScroll(card);
            photoStrip.add(card);
        }

        updatePhotoGridColumns();
        photoStrip.revalidate();
        photoStrip.repaint();
    }

    private void updatePhotoGridColumns() {
        int availableWidth = photoContent.getWidth();
        if (availableWidth <= 0) {
            availableWidth = 480;
        }
        int columns = Math.max(1, Math.min(3, (availableWidth + 8) / 205));
        int rows = Math.max(1, (photoStrip.getComponentCount() + columns - 1) / columns);
        int cardWidth = Math.max(100, (availableWidth - (columns - 1) * 8) / columns);
        int cardHeight = Math.max(145, (int) Math.round(cardWidth * 0.92));
        photoStrip.setLayout(new GridLayout(rows, columns, 8, 8));
        for (Component component : photoStrip.getComponents()) {
            if (component instanceof JComponent card) {
                card.setPreferredSize(new Dimension(cardWidth, cardHeight));
            }
        }
        photoStrip.setPreferredSize(new Dimension(availableWidth, rows * cardHeight + Math.max(0, rows - 1) * 8));
        photoStrip.revalidate();
    }

    private void createAnimatedGifSafely(List<BufferedImage> photos) {
        try {
            if (photos == null || photos.isEmpty() || sessionFolder == null) {
                return;
            }

            gifFile = new File(sessionFolder, "animation.gif");
            GifGenerator.write(photos, gifFile, 800);

            if (gifFile.isFile() && gifFile.length() > 0) {
                int frameCount = gifPreviewLabel.loadAnimatedGif(gifFile);
                gifPreviewLabel.setPreferredSize(new Dimension(360, 260));
                gifPreviewPanel.setPreferredSize(new Dimension(380, 300));
                DebugLog.info("Animated GIF created and decoded for desktop preview: "
                        + gifFile.getAbsolutePath() + ", frames=" + frameCount);
                if (frameCount == 0) {
                    throw new IllegalStateException("GIF berhasil dibuat tetapi tidak memiliki frame yang dapat dibaca.");
                }
            } else {
                throw new IllegalStateException("File animation.gif tidak ditemukan atau kosong.");
            }
        } catch (Exception ex) {
            gifFile = null;
            gifPreviewLabel.clearAnimation();
            gifPreviewLabel.setText("GIF TIDAK DAPAT DITAMPILKAN");
            DebugLog.error("Animated GIF generation/desktop preview failed; continuing photo flow.", ex);
        }
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

    private void uploadResult(PhotoSession session, File folder, String frameName, long previewStartedAt) {
        boolean isActiveSession = session != null
                && session.getSessionId().equals(activeUploadSessionId);
        if (isActiveSession) {
            qrButton.setEnabled(false);
            startUploadAnimation();
        }
        if (session == null || folder == null) {
            if (isActiveSession) {
                stopUploadAnimation();
                uploadStatus.setText("UPLOAD TIDAK DAPAT DIMULAI");
            }
            return;
        }

        final String sessionId = session.getSessionId();
        final File queuedFolder = folder;

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                UploadQueueManager.enqueue(sessionId, frameName, queuedFolder, previewStartedAt);
                return null;
            }

            @Override
            protected void done() {
                // A previous session must never overwrite the active preview's
                // QR, status label, or polling timer.
                if (!sessionId.equals(activeUploadSessionId)) return;
                try {
                    get();
                    DebugLog.info("Session persisted to upload queue. session=" + sessionId);
                    uploadStatus.setText("TERSIMPAN — MENUNGGU UPLOAD");
                    uploadStatusPollTimer = new Timer(2000, event -> {
                        if (!sessionId.equals(activeUploadSessionId)) {
                            ((Timer) event.getSource()).stop();
                            return;
                        }
                        UploadQueueEntry entry = UploadQueueManager.find(sessionId);
                        if (entry == null) return;

                        if (entry.galleryUrl != null && !entry.galleryUrl.isBlank()) {
                            downloadUrl = entry.galleryUrl;
                            qrButton.setEnabled(true);
                        }

                        if ("COMPLETED".equals(entry.status)) {
                            uploadStatus.setText("UPLOAD SELESAI — QR SIAP");
                            ((Timer) event.getSource()).stop();
                            stopUploadAnimation();
                        } else if (!entry.galleryUrl.isBlank()
                                && System.currentTimeMillis() - entry.createdAt >= 60_000L
                                && !"COMPLETED".equalsIgnoreCase(entry.serverStatus)) {
                            uploadStatus.setText("UPLOAD SELESAI SEBAGIAN — QR SIAP");
                            stopUploadAnimation();
                        } else if ("RETRY".equals(entry.status)) {
                            uploadStatus.setText(entry.galleryUrl.isBlank()
                                    ? "UPLOAD TERTUNDA — AKAN DICOBA LAGI"
                                    : "UPLOAD TERTUNDA — QR SUDAH SIAP");
                            stopUploadAnimation();
                        } else if ("RESERVING".equals(entry.status)) {
                            uploadStatus.setText("MENDAFTARKAN SESI...");
                        } else if ("UPLOADING".equals(entry.status)
                                || "RESERVED".equals(entry.status)
                                || "PARTIAL".equals(entry.status)) {
                            uploadStatus.setText("MENGIRIM HASIL...");
                        }
                    });
                    uploadStatusPollTimer.start();
                } catch (Exception ex) {
                    stopUploadAnimation();
                    stopLivePhotoLoading();
                    DebugLog.error("Failed to enqueue session upload.", ex);
                    uploadStatus.setText("GAGAL MENYIMPAN ANTREAN UPLOAD");
                }
            }
        }.execute();
    }

    private void startLivePhotoLoading() {
        livePhotoLoadingPanel.setActive(true);
    }

    private void stopLivePhotoLoading() {
        livePhotoLoadingPanel.setActive(false);
    }

    /**
     * Draws still images and animated GIFs into the available component area.
     * The source image keeps its aspect ratio and is never tied to a fixed pixel size.
     */
    private static final class ResponsiveImageLabel extends JLabel {
        private Image image;
        private List<BufferedImage> animationFrames = List.of();
        private int animationIndex;
        private Timer animationTimer;

        private ResponsiveImageLabel(String text) {
            super(text, SwingConstants.CENTER);
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
        }

        private void setImage(Image image) {
            stopAnimation();
            this.image = image;
            repaint();
        }

        /**
         * Decode every GIF frame explicitly. Relying on Toolkit/ImageIcon animation
         * is unreliable when the image is painted manually by a custom JLabel.
         */
        private int loadAnimatedGif(File file) throws Exception {
            clearAnimation();
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                throw new IllegalStateException("GIF ImageIO reader tidak tersedia.");
            }

            ImageReader reader = readers.next();
            List<BufferedImage> decoded = new ArrayList<>();
            int delayMillis = 800;
            try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
                if (input == null) throw new IllegalStateException("Tidak dapat membuka file GIF.");
                reader.setInput(input, false, false);
                int count = reader.getNumImages(true);
                for (int i = 0; i < count; i++) {
                    BufferedImage frame = reader.read(i);
                    if (frame != null) {
                        decoded.add(frame);
                        if (i == 0) {
                            delayMillis = readGifDelayMillis(reader.getImageMetadata(i));
                        }
                    }
                }
            } finally {
                reader.dispose();
            }

            if (decoded.isEmpty()) return 0;
            this.image = null;
            this.animationFrames = List.copyOf(decoded);
            this.animationIndex = 0;
            setText("");
            if (animationFrames.size() > 1) {
                animationTimer = new Timer(Math.max(40, delayMillis), e -> {
                    animationIndex = (animationIndex + 1) % animationFrames.size();
                    repaint();
                });
                animationTimer.setCoalesce(true);
                animationTimer.start();
            }
            revalidate();
            repaint();
            return animationFrames.size();
        }

        private static int readGifDelayMillis(IIOMetadata metadata) {
            try {
                String format = metadata.getNativeMetadataFormatName();
                if (format == null) return 800;
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                org.w3c.dom.NodeList controls = root.getElementsByTagName("GraphicControlExtension");
                if (controls.getLength() > 0) {
                    org.w3c.dom.Node node = controls.item(0);
                    org.w3c.dom.NamedNodeMap attributes = node.getAttributes();
                    org.w3c.dom.Node delay = attributes.getNamedItem("delayTime");
                    if (delay != null) {
                        int centiseconds = Integer.parseInt(delay.getNodeValue());
                        if (centiseconds > 0) return centiseconds * 10;
                    }
                }
            } catch (Exception ignored) {
                // Keep the safe default delay when optional GIF metadata is absent.
            }
            return 800;
        }

        private void clearAnimation() {
            stopAnimation();
            image = null;
            animationFrames = List.of();
            animationIndex = 0;
            repaint();
        }

        private void stopAnimation() {
            if (animationTimer != null) {
                animationTimer.stop();
                animationTimer = null;
            }
        }

        @Override protected void paintComponent(Graphics graphics) {
            BufferedImage currentFrame = animationFrames.isEmpty()
                    ? null : animationFrames.get(animationIndex);
            Image currentImage = currentFrame != null ? currentFrame : image;
            if (currentImage == null) {
                super.paintComponent(graphics);
                return;
            }

            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int sourceW = currentImage.getWidth(this);
            int sourceH = currentImage.getHeight(this);
            if (sourceW <= 0 || sourceH <= 0 || getWidth() <= 0 || getHeight() <= 0) {
                g2.dispose();
                return;
            }
            double scale = Math.min(getWidth() / (double) sourceW, getHeight() / (double) sourceH);
            int drawW = Math.max(1, (int) Math.round(sourceW * scale));
            int drawH = Math.max(1, (int) Math.round(sourceH * scale));
            int x = (getWidth() - drawW) / 2;
            int y = (getHeight() - drawH) / 2;
            g2.drawImage(currentImage, x, y, drawW, drawH, this);
            g2.dispose();
        }
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
        DebugLog.info(String.format(java.util.Locale.ROOT,
                "Print input: file=%s, image=%dx%d, configuredPaper=%.3fx%.3f in",
                imageFile.getAbsolutePath(), image.getWidth(), image.getHeight(),
                widthInches, heightInches));

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
            PrinterJob job, double widthInches, double heightInches,
            int imageWidth, int imageHeight) {
        // The driver may normalize a requested paper size to its actual
        // physical dimensions. Accept small differences, but reject a
        // materially different media configuration.
        final double maxDimensionDeviation = 0.05; // 5 percent per dimension
        final double maxAspectDeviation = 0.015;   // 1.5 percent aspect ratio

        double requestedWidth = widthInches * 72.0;
        double requestedHeight = heightInches * 72.0;
        Paper requestedPaper = new Paper();
        requestedPaper.setSize(requestedWidth, requestedHeight);
        requestedPaper.setImageableArea(0, 0, requestedWidth, requestedHeight);

        PageFormat requested = job.defaultPage();
        requested.setPaper(requestedPaper);
        requested.setOrientation(imageWidth > imageHeight
                ? PageFormat.LANDSCAPE : PageFormat.PORTRAIT);

        DebugLog.info(String.format(java.util.Locale.ROOT,
                "Print PageFormat REQUESTED: paper=%.2fx%.2f pt, imageable=(%.2f, %.2f, %.2f, %.2f), orientation=%s",
                requested.getWidth(), requested.getHeight(),
                requested.getImageableX(), requested.getImageableY(),
                requested.getImageableWidth(), requested.getImageableHeight(),
                orientationName(requested.getOrientation())));

        PageFormat validated = job.validatePage(requested);
        double actualWidth = validated.getWidth();
        double actualHeight = validated.getHeight();
        double widthDeviation = Math.abs(actualWidth - requested.getWidth()) / requested.getWidth();
        double heightDeviation = Math.abs(actualHeight - requested.getHeight()) / requested.getHeight();
        double requestedRatio = requested.getWidth() / requested.getHeight();
        double actualRatio = actualWidth / actualHeight;
        double aspectDeviation = Math.abs(actualRatio - requestedRatio) / requestedRatio;

        DebugLog.info(String.format(java.util.Locale.ROOT,
                "Print PageFormat VALIDATED: paper=%.2fx%.2f pt, imageable=(%.2f, %.2f, %.2f, %.2f), orientation=%s, delta=(%.2f%%, %.2f%%), aspectDelta=%.2f%%",
                actualWidth, actualHeight,
                validated.getImageableX(), validated.getImageableY(),
                validated.getImageableWidth(), validated.getImageableHeight(),
                orientationName(validated.getOrientation()),
                widthDeviation * 100.0, heightDeviation * 100.0,
                aspectDeviation * 100.0));

        if (widthDeviation > maxDimensionDeviation
                || heightDeviation > maxDimensionDeviation
                || aspectDeviation > maxAspectDeviation) {
            throw new IllegalStateException(String.format(java.util.Locale.ROOT,
                    "Ukuran kertas dari driver berbeda terlalu jauh dari pengaturan Phomoria. "
                            + "Diminta %.2fx%.2f pt, driver %.2fx%.2f pt. "
                            + "Periksa ukuran media di pengaturan printer.",
                    requested.getWidth(), requested.getHeight(), actualWidth, actualHeight));
        }

        // Small driver normalization is authoritative: expand the imageable
        // rectangle to the driver's validated physical sheet dimensions.
        // Do not call validatePage again, because that would restore the old
        // requested imageable rectangle on some printer drivers.
        Paper driverPaper = validated.getPaper();
        driverPaper.setImageableArea(0, 0,
                driverPaper.getWidth(), driverPaper.getHeight());
        PageFormat effective = new PageFormat();
        effective.setPaper(driverPaper);
        effective.setOrientation(validated.getOrientation());

        DebugLog.info(String.format(java.util.Locale.ROOT,
                "Print PageFormat EFFECTIVE: paper=%.2fx%.2f pt, imageable=(%.2f, %.2f, %.2f, %.2f), driverNormalized=true",
                effective.getWidth(), effective.getHeight(),
                effective.getImageableX(), effective.getImageableY(),
                effective.getImageableWidth(), effective.getImageableHeight()));
        return effective;
    }

    private String orientationName(int orientation) {
        switch (orientation) {
            case PageFormat.LANDSCAPE: return "LANDSCAPE";
            case PageFormat.REVERSE_LANDSCAPE: return "REVERSE_LANDSCAPE";
            default: return "PORTRAIT";
        }
    }

    private Printable createPrintable(BufferedImage image) {
        return (graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0) return Printable.NO_SUCH_PAGE;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);

                double availableWidth = pageFormat.getImageableWidth();
                double availableHeight = pageFormat.getImageableHeight();
                // Fill the driver's imageable area. No experimental overscan.
                double scale = Math.max(availableWidth / image.getWidth(),
                        availableHeight / image.getHeight());
                double drawWidth = image.getWidth() * scale;
                double drawHeight = image.getHeight() * scale;

                // Printable Graphics is already translated to the imageable origin.
                // Do not add imageableX/Y again: that creates a right/bottom offset.
                double x = (availableWidth - drawWidth) / 2.0;
                double y = (availableHeight - drawHeight) / 2.0;

                DebugLog.info(String.format(java.util.Locale.ROOT,
                        "Print Printable: image=%dx%d, paper=%.2fx%.2f pt, imageable=(%.2f, %.2f, %.2f, %.2f), draw=(x=%.2f, y=%.2f, w=%.2f, h=%.2f), scale=%.6f, graphicsTransform=%s",
                        image.getWidth(), image.getHeight(),
                        pageFormat.getWidth(), pageFormat.getHeight(),
                        pageFormat.getImageableX(), pageFormat.getImageableY(),
                        availableWidth, availableHeight, x, y,
                        drawWidth, drawHeight, scale, g.getTransform()));

                g.drawImage(image, (int) Math.round(x), (int) Math.round(y),
                        (int) Math.round(drawWidth), (int) Math.round(drawHeight), null);
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

    /** Rounded dark glass card with a soft shadow for gallery media. */
    private static final class MediaCardPanel extends JPanel {
        MediaCardPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.setColor(new Color(0, 0, 0, 34));
            g.fillRoundRect(3, 4, Math.max(0, w - 6), Math.max(0, h - 6), 16, 16);
            g.setColor(new Color(30, 31, 38, 218));
            g.fillRoundRect(0, 0, Math.max(0, w - 4), Math.max(0, h - 4), 16, 16);
            g.setColor(new Color(255, 255, 255, 78));
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(0, 0, Math.max(1, w - 5), Math.max(1, h - 5), 16, 16);
            g.dispose();
            super.paintComponent(graphics);
        }
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
