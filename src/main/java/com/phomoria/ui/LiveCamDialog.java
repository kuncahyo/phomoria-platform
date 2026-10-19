package com.phomoria.ui;

import com.phomoria.camera.CameraBackend;
import com.phomoria.camera.CameraManager;
import com.phomoria.debug.DebugLog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;

/**
 * A temporary camera preview/capture window for checking framing and focus.
 * Images captured here are intentionally transient and are not added to a
 * PhotoSession, uploaded, printed, or billed.
 */
public final class LiveCamDialog extends JDialog {

    private final String cameraName;
    private final Runnable onClosed;
    private final JLabel imageLabel = new JLabel("Menghubungkan kamera...", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JButton captureButton = new JButton("AMBIL FOTO");
    private final JButton backButton = new JButton("BACK");
    private final Timer previewTimer;
    private final Timer countdownTimer;
    private int countdown = 3;
    private boolean closing;
    private boolean captured;
    private boolean previewRequestRunning;
    private AWTEventListener outsideClickListener;

    public LiveCamDialog(Window owner, String cameraName, Runnable onClosed) {
        super(owner, "PHOMORIA LIVECAM", ModalityType.MODELESS);
        this.cameraName = cameraName;
        this.onClosed = onClosed;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { closeDialog(); }
        });

        imageLabel.setOpaque(true);
        imageLabel.setBackground(Color.BLACK);
        imageLabel.setForeground(Color.WHITE);
        imageLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
        imageLabel.setPreferredSize(new Dimension(960, 540));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 12));
        captureButton.setFont(captureButton.getFont().deriveFont(Font.BOLD, 18f));
        backButton.setFont(backButton.getFont().deriveFont(Font.BOLD, 16f));
        controls.add(captureButton);
        controls.add(backButton);

        JPanel bottom = new JPanel(new BorderLayout(8, 6));
        bottom.setBorder(new EmptyBorder(8, 12, 10, 12));
        bottom.add(statusLabel, BorderLayout.NORTH);
        bottom.add(controls, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(imageLabel, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        captureButton.addActionListener(e -> beginCountdown());
        backButton.addActionListener(e -> returnToPreview());

        previewTimer = new Timer(140, e -> requestPreviewFrame());
        previewTimer.setCoalesce(true);
        countdownTimer = new Timer(1000, e -> countdownTick());

        pack();
        setMinimumSize(new Dimension(720, 460));
        setSize(1000, 680);
        setLocationRelativeTo(owner);
        setAlwaysOnTop(false);
        addOutsideClickListener();
        openCamera();
    }

    private void openCamera() {
        captureButton.setEnabled(false);
        backButton.setEnabled(false);
        statusLabel.setText("Membuka " + cameraName + "...");
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() throws Exception {
                if (isDslr(cameraName)) {
                    CameraBackend backend = CameraManager.openConfiguredBackend(cameraName);
                    return backend != null;
                }
                Object webcam = CameraManager.openConfigured(cameraName);
                return webcam != null;
            }
            @Override protected void done() {
                if (closing) return;
                try {
                    if (get()) {
                        statusLabel.setText("Kamera aktif — " + cameraName);
                        captureButton.setEnabled(true);
                        backButton.setEnabled(true);
                        previewTimer.start();
                    } else {
                        imageLabel.setText("Kamera gagal dibuka. Periksa koneksi dan pilihan kamera.");
                        statusLabel.setText("Kamera tidak tersedia");
                        backButton.setEnabled(true);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Pembukaan kamera dibatalkan.");
                } catch (ExecutionException ex) {
                    DebugLog.error("LIVECAM camera open failed: " + ex.getCause());
                    showError("Gagal membuka kamera: " + rootMessage(ex));
                }
            }
        }.execute();
    }

    private static boolean isDslr(String name) {
        return name != null && name.toLowerCase().contains("[gphoto2]");
    }

    private void requestPreviewFrame() {
        if (closing || captured || previewRequestRunning) return;
        previewRequestRunning = true;
        new SwingWorker<BufferedImage, Void>() {
            @Override protected BufferedImage doInBackground() throws Exception {
                CameraBackend backend = CameraManager.currentBackend();
                if (backend != null) return backend.getLiveImage();
                Object webcam = CameraManager.current();
                if (webcam == null) return null;
                Method getImage = webcam.getClass().getMethod("getImage");
                Object result = getImage.invoke(webcam);
                return result instanceof BufferedImage ? (BufferedImage) result : null;
            }
            @Override protected void done() {
                previewRequestRunning = false;
                if (closing || captured) return;
                try {
                    BufferedImage image = get();
                    if (image != null) showImage(image);
                } catch (Exception ex) {
                    statusLabel.setText("Menunggu gambar dari kamera...");
                }
            }
        }.execute();
    }

    private void beginCountdown() {
        if (closing || captured || !captureButton.isEnabled()) return;
        previewTimer.stop();
        captureButton.setEnabled(false);
        backButton.setEnabled(false);
        countdown = 3;
        statusLabel.setText("Foto diambil dalam " + countdown + "...");
        countdownTimer.start();
    }

    private void countdownTick() {
        countdown--;
        if (countdown > 0) {
            statusLabel.setText("Foto diambil dalam " + countdown + "...");
            return;
        }
        countdownTimer.stop();
        statusLabel.setText("Mengambil foto...");
        new SwingWorker<BufferedImage, Void>() {
            @Override protected BufferedImage doInBackground() throws Exception {
                return CameraManager.capture();
            }
            @Override protected void done() {
                if (closing) return;
                try {
                    BufferedImage photo = get();
                    if (photo == null) throw new IllegalStateException("Kamera tidak menghasilkan gambar.");
                    captured = true;
                    showImage(photo);
                    statusLabel.setText("Foto berhasil diambil — tekan BACK untuk mengulang.");
                    backButton.setEnabled(true);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Pengambilan foto dibatalkan.");
                    backButton.setEnabled(true);
                } catch (ExecutionException | RuntimeException ex) {
                    showError("Gagal mengambil foto: " + rootMessage(ex));
                    captureButton.setEnabled(true);
                    backButton.setEnabled(true);
                    previewTimer.start();
                }
            }
        }.execute();
    }

    private void returnToPreview() {
        if (closing) return;
        if (!captured) {
            closeDialog();
            return;
        }
        captured = false;
        imageLabel.setText("Mengaktifkan live preview...");
        imageLabel.setIcon(null);
        captureButton.setEnabled(true);
        backButton.setEnabled(true);
        statusLabel.setText("Live preview");
        previewTimer.start();
    }

    private void showImage(BufferedImage image) {
        if (image == null) return;
        int w = Math.max(1, imageLabel.getWidth());
        int h = Math.max(1, imageLabel.getHeight());
        double scale = Math.min((double) w / image.getWidth(), (double) h / image.getHeight());
        int sw = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int sh = Math.max(1, (int) Math.round(image.getHeight() * scale));
        Image scaled = image.getScaledInstance(sw, sh, Image.SCALE_SMOOTH);
        imageLabel.setText(null);
        imageLabel.setIcon(new ImageIcon(scaled));
    }

    private void showError(String message) {
        imageLabel.setIcon(null);
        imageLabel.setText(message);
        statusLabel.setText(message);
    }

    private void closeDialog() {
        if (closing) return;
        closing = true;
        previewTimer.stop();
        countdownTimer.stop();
        removeOutsideClickListener();
        captureButton.setEnabled(false);
        backButton.setEnabled(false);
        statusLabel.setText("Menutup kamera...");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                try { CameraManager.close(); }
                catch (Exception ex) { DebugLog.error("LIVECAM camera close failed: " + ex); }
                return null;
            }
            @Override protected void done() {
                dispose();
                if (onClosed != null) onClosed.run();
            }
        }.execute();
    }

    private void addOutsideClickListener() {
        outsideClickListener = event -> {
            if (!(event instanceof MouseEvent) || closing || !isShowing()) return;
            MouseEvent mouse = (MouseEvent) event;
            if (mouse.getID() != MouseEvent.MOUSE_PRESSED) return;
            Component source = mouse.getComponent();
            if (source == null) return;
            Point screenPoint = mouse.getLocationOnScreen();
            if (!getBounds().contains(screenPoint)) closeDialog();
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(outsideClickListener, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void removeOutsideClickListener() {
        if (outsideClickListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickListener);
            outsideClickListener = null;
        }
    }

    private static String rootMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    @Override public void dispose() {
        removeOutsideClickListener();
        super.dispose();
    }
}
