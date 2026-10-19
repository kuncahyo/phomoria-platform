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
        elements.put("start-payment", new Element("start-payment.png", 810, 650, 300, 70));
        elements.put("start-settings", new Element("start-settings.png", 900, 735, 120, 48));
        elements.put("frame-selection-header", new Element(null, 160, 18, 1600, 75, "PILIH FRAME", "#FFFFFF", true));
        elements.put("frame-selection-preview", new Element(null, 70, 175, 820, 790));
        elements.put("frame-selection-list", new Element(null, 930, 175, 920, 790));
        elements.put("frame-selection-list-title", new Element(null, 980, 190, 820, 52, "PILIH DESAIN FRAME", "#FFFFFF", true));
        elements.put("frame-selection-selected-name", new Element(null, 70, 900, 820, 48, "", "#FFFFFF", false));
        elements.put("frame-selection-card-name", new Element(null, 0, 0, 190, 32, "", "#FFFFFF", false));
        elements.put("frame-selection-card-info", new Element(null, 0, 0, 190, 28, "", "#C8C8D0", false));
        elements.put("frame-selection-continue", new Element("frame-selection-continue.png", 1500, 970, 300, 70, "LANJUTKAN", "#FFFFFF", true));

        // Main / Photo Capture. Camera and preview keep their own aspect-aware painting.
        elements.put("main-title", new Element(null, 30, 18, 650, 58, "PHOMORIA  •  PHOTO BOOTH", "#FFFFFF", true));
        elements.put("main-status", new Element(null, 950, 18, 940, 58, "", "#78DC96", true));
        elements.put("main-camera", new Element(null, 30, 100, 1260, 850));
        elements.put("main-preview", new Element(null, 1320, 100, 570, 850));
        elements.put("main-counter", new Element(null, 650, 975, 180, 70, "{captured} / {total}", "#FFFFFF", true));
        elements.put("main-retake", new Element(null, 850, 975, 300, 70, "RETAKE FOTO TERPILIH", "#FFFFFF", true));
        elements.put("main-cancel", new Element(null, 1180, 975, 330, 70, "PILIH ULANG FRAME", "#FFFFFF", true));
        elements.put("main-countdown", new Element(null, 800, 360, 320, 320, "", "#FFFFFF", true));
    }

    ThemeConfig withDefaults() {
        ThemeConfig defaults = new ThemeConfig();
        if (screens == null) screens = defaults.screens;
        else defaults.screens.forEach(screens::putIfAbsent);
        if (elements == null) elements = defaults.elements;
        else defaults.elements.forEach((id, value) -> elements.putIfAbsent(id, value));
        elements.remove("frame-selection-category");
        if (designWidth <= 0) designWidth = 1920;
        if (designHeight <= 0) designHeight = 1080;
        elements.forEach((id, e) -> {
            if (e == null) return;
            Element factory = defaults.elements.get(id);
            if (e.defaultImage == null || e.defaultImage.isBlank()) e.defaultImage = e.image;
            if (e.defaultTransparent == null) e.defaultTransparent = e.transparent;
            if (e.text == null && factory != null) e.text = factory.text;
            if (e.foreground == null && factory != null) e.foreground = factory.foreground;
            if (e.visible == null) e.visible = factory == null ? Boolean.TRUE : factory.visible;
            if (e.defaultText == null) e.defaultText = e.text;
            if (e.defaultForeground == null) e.defaultForeground = e.foreground;
            if (e.defaultVisible == null) e.defaultVisible = e.visible;
        });
        return this;
    }

    public static final class Screen {
        public String background;
        public Screen() {}
        public Screen(String background) { this.background = background; }
    }

    public static final class Element {
        public String image;
        public String defaultImage;
        public boolean transparent;
        public Boolean defaultTransparent;
        public String text;
        public String defaultText;
        public String foreground;
        public String defaultForeground;
        public Boolean visible = Boolean.TRUE;
        public Boolean defaultVisible;
        public int x, y, width, height;
        public Element() {}
        public Element(String image, int x, int y, int width, int height) {
            this(image, x, y, width, height, null, "#FFFFFF", true);
        }
        public Element(String image, int x, int y, int width, int height,
                       String text, String foreground, boolean visible) {
            this.image = image; this.defaultImage = image; this.defaultTransparent = false;
            this.text = text; this.defaultText = text;
            this.foreground = foreground; this.defaultForeground = foreground;
            this.visible = visible; this.defaultVisible = visible;
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }
}
