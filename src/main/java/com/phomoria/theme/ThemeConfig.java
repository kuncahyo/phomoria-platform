package com.phomoria.theme;

import java.util.LinkedHashMap;
import java.util.Map;

/** Theme JSON model. Coordinates use the 1920x1080 design canvas. */
public final class ThemeConfig {
    public int designWidth = 1920;
    public int designHeight = 1080;
    public Map<String, Screen> screens = new LinkedHashMap<>();
    public Map<String, Element> elements = new LinkedHashMap<>();

    public ThemeConfig() {
        screens.put("start", new Screen("start.png"));
        screens.put("frame-selection", new Screen("frame-selection.png"));
        screens.put("main", new Screen("main.png"));
        screens.put("effect", new Screen("effect.png"));
        screens.put("result", new Screen("result.png"));
        // Dialog canvases use their own real content dimensions, not the 1920x1080 screen.
        screens.put("payment-dialog", new Screen(null, 500, 560, "#F5F5F7"));
        screens.put("voucher-dialog", new Screen(null, 430, 190, "#F5F5F7"));

        // Factory buttons are rendered by Swing/code. PNGs are user overrides only.
        elements.put("start-payment", new Element(null, 810, 650, 300, 70,
                "BAYAR & MULAI FOTO", "#FFFFFF", true));
        elements.put("start-settings", new Element(null, 900, 735, 120, 48,
                "SETTINGS", "#FFFFFF", true));
        elements.put("frame-selection-header", new Element(null, 160, 18, 1600, 75, "PILIH FRAME", "#FFFFFF", true));
        elements.put("frame-selection-preview", new Element(null, 70, 175, 820, 790));
        elements.put("frame-selection-list", new Element(null, 930, 175, 920, 790));
        elements.put("frame-selection-list-title", new Element(null, 980, 190, 820, 52, "PILIH DESAIN FRAME", "#FFFFFF", true));
        elements.put("frame-selection-selected-name", new Element(null, 70, 900, 820, 48, "", "#FFFFFF", false));
        elements.put("frame-selection-card-name", new Element(null, 0, 0, 190, 32, "", "#FFFFFF", false));
        elements.put("frame-selection-card-info", new Element(null, 0, 0, 190, 28, "", "#C8C8D0", false));
        elements.put("frame-selection-continue", new Element(null, 1500, 970, 300, 70, "LANJUTKAN", "#FFFFFF", true));

        elements.put("main-title", new Element(null, 30, 18, 650, 58, "PHOMORIA  •  PHOTO BOOTH", "#FFFFFF", true));
        elements.put("main-status", new Element(null, 950, 18, 940, 58, "", "#78DC96", true));
        elements.put("main-camera", new Element(null, 30, 100, 1260, 850));
        elements.put("main-preview", new Element(null, 1320, 100, 570, 850));
        elements.put("main-counter", new Element(null, 650, 975, 180, 70, "{captured} / {total}", "#FFFFFF", true));
        elements.put("main-retake", new Element(null, 850, 975, 300, 70, "RETAKE FOTO TERPILIH", "#FFFFFF", true));
        elements.put("main-cancel", new Element(null, 1180, 975, 330, 70, "PILIH ULANG FRAME", "#FFFFFF", true));
        elements.put("main-countdown", new Element(null, 800, 360, 320, 320, "", "#FFFFFF", true));

        // Inactivity countdowns. Their numbers are drawn at runtime; PNG is an optional background.
        elements.put("frame-selection-timer", new Element(null, 1740, 24, 130, 130, "30", "#555555", true));
        elements.put("main-timer", new Element(null, 1740, 24, 130, 130, "180", "#555555", true));

        // Final Preview elements. All coordinates use the shared 1920x1080 canvas.
        elements.put("result-title", new Element(null, 160, 12, 1600, 64, "HASIL FOTO", "#FFFFFF", true));
        elements.put("result-preview", new Element(null, 70, 105, 1200, 790));
        Element liveLoading = new Element(null, 70, 105, 1200, 790, "MENYIAPKAN LIVE PHOTO", "#FFFFFF", true);
        liveLoading.loadingTextPosition = "BOTTOM";
        elements.put("result-live-photo-loading", liveLoading);
        elements.put("result-photo-list-title", new Element(null, 1300, 100, 550, 48, "FOTO INDIVIDUAL", "#FFFFFF", true));
        elements.put("result-photo-list", new Element(null, 1300, 158, 550, 772));
        elements.put("result-upload-status", new Element(null, 500, 945, 920, 42, "", "#C8C8D2", true));
        elements.put("result-qr", new Element(null, 470, 1000, 300, 62, "TAMPILKAN QR DOWNLOAD", "#FFFFFF", true));
        elements.put("result-print", new Element(null, 810, 1000, 220, 62, "PRINT", "#FFFFFF", true));
        elements.put("result-new-session", new Element(null, 1070, 1000, 300, 62, "NEW SESSION", "#FFFFFF", true));
        elements.put("effect-timer", new Element(null, 1740, 24, 130, 130, "30", "#555555", true));
        elements.put("result-timer", new Element(null, 1740, 24, 130, 130, "60", "#555555", true));

        // Payment QRIS dialog (500x560 content area).
        elements.put("payment-dialog-title", new Element(null, 20, 12, 460, 36, "SCAN QR UNTUK PEMBAYARAN", "#222222", true));
        elements.put("payment-dialog-qr", new Element(null, 70, 60, 360, 360, "QRIS PREVIEW", "#222222", true));
        elements.put("payment-dialog-countdown", new Element(null, 360, 60, 110, 28, "10:00", "#464646", true));
        elements.put("payment-dialog-voucher", new Element(null, 350, 385, 120, 34, "VOUCHER", "#FFFFFF", true));
        elements.put("payment-dialog-note", new Element(null, 20, 450, 460, 48, "Menunggu pembayaran", "#222222", true));
        elements.put("payment-dialog-cancel", new Element(null, 180, 510, 140, 36, "BATAL", "#FFFFFF", true));

        // Voucher dialog (430x190 content area).
        elements.put("voucher-dialog-title", new Element(null, 15, 12, 400, 32, "GUNAKAN VOUCHER", "#222222", true));
        elements.put("voucher-dialog-code-label", new Element(null, 25, 62, 95, 30, "Kode voucher", "#222222", true));
        elements.put("voucher-dialog-code-field", new Element(null, 125, 62, 280, 30, "", "#222222", true));
        elements.put("voucher-dialog-status", new Element(null, 15, 102, 400, 34, "Masukkan kode voucher untuk memulai sesi foto.", "#222222", true));
        elements.put("voucher-dialog-submit", new Element(null, 105, 148, 105, 32, "SELESAI", "#FFFFFF", true));
        elements.put("voucher-dialog-cancel", new Element(null, 220, 148, 105, 32, "BATAL", "#FFFFFF", true));
    }

    ThemeConfig withDefaults() {
        ThemeConfig defaults = new ThemeConfig();
        if (screens == null) screens = defaults.screens;
        else defaults.screens.forEach(screens::putIfAbsent);
        if (elements == null) elements = defaults.elements;
        else defaults.elements.forEach((id, value) -> elements.putIfAbsent(id, value));
        elements.remove("frame-selection-category");
        // Migrate the old thin loading-bar bounds to the full Result preview overlay.
        Element loader = elements.get("result-live-photo-loading");
        Element preview = elements.get("result-preview");
        if (loader != null && preview != null
                && loader.x == 70 && loader.y == 910
                && loader.width == 1200 && loader.height == 70) {
            loader.x = preview.x;
            loader.y = preview.y;
            loader.width = preview.width;
            loader.height = preview.height;
        }
        if (designWidth <= 0) designWidth = 1920;
        if (designHeight <= 0) designHeight = 1080;
        screens.forEach((id, screen) -> {
            if (screen == null) return;
            Screen factory = defaults.screens.get(id);
            if (screen.designWidth <= 0) screen.designWidth = factory == null ? designWidth : factory.designWidth;
            if (screen.designHeight <= 0) screen.designHeight = factory == null ? designHeight : factory.designHeight;
            if (screen.backgroundColor == null || screen.backgroundColor.isBlank()) {
                screen.backgroundColor = factory == null ? "#121216" : factory.backgroundColor;
            }
        });
        elements.forEach((id, e) -> {
            if (e == null) return;
            Element factory = defaults.elements.get(id);
            if (e.defaultImage == null || e.defaultImage.isBlank()) e.defaultImage = e.image;
            if (e.defaultTimerBackground == null) e.defaultTimerBackground = e.timerBackground;
            if (e.defaultBackground == null) e.defaultBackground = e.background;
            if (e.defaultFontSize == null) e.defaultFontSize = e.fontSize;
            if (e.defaultTransparent == null) e.defaultTransparent = e.transparent;
            if (e.text == null && factory != null) e.text = factory.text;
            if (e.foreground == null && factory != null) e.foreground = factory.foreground;
            if (e.visible == null) e.visible = factory == null ? Boolean.TRUE : factory.visible;
            if (e.defaultText == null) e.defaultText = e.text;
            if (e.defaultForeground == null) e.defaultForeground = e.foreground;
            if (e.defaultVisible == null) e.defaultVisible = e.visible;
            if ("result-live-photo-loading".equals(id)
                    && (e.loadingTextPosition == null || e.loadingTextPosition.isBlank())) {
                e.loadingTextPosition = "BOTTOM";
            }
        });
        return this;
    }

    public static final class Screen {
        public String background;
        public int designWidth = 1920;
        public int designHeight = 1080;
        public String backgroundColor = "#121216";
        public Screen() {}
        public Screen(String background) { this(background, 1920, 1080, "#121216"); }
        public Screen(String background, int designWidth, int designHeight, String backgroundColor) {
            this.background = background;
            this.designWidth = designWidth;
            this.designHeight = designHeight;
            this.backgroundColor = backgroundColor;
        }
    }

    public static final class Element {
        public String image;
        public String background;
        public String defaultBackground;
        public int fontSize;
        public Integer defaultFontSize;
        public String defaultImage;
        public String timerBackground;
        public String defaultTimerBackground;
        public boolean transparent;
        public Boolean defaultTransparent;
        public String text;
        public String defaultText;
        public String foreground;
        public String defaultForeground;
        public Boolean visible = Boolean.TRUE;
        public Boolean defaultVisible;
        /** Placement of Live Photo loading text relative to its custom GIF: TOP, BOTTOM, LEFT, RIGHT, NONE. */
        public String loadingTextPosition = "BOTTOM";
        public int x, y, width, height;
        public Element() {}
        public Element(String image, int x, int y, int width, int height) {
            this(image, x, y, width, height, null, "#FFFFFF", true);
        }
        public Element(String image, int x, int y, int width, int height,
                       String text, String foreground, boolean visible) {
            this.image = image; this.defaultImage = image; this.defaultTransparent = false;
            this.background = null; this.defaultBackground = null;
            this.fontSize = 0; this.defaultFontSize = 0;
            this.text = text; this.defaultText = text;
            this.foreground = foreground; this.defaultForeground = foreground;
            this.visible = visible; this.defaultVisible = visible;
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }
}
