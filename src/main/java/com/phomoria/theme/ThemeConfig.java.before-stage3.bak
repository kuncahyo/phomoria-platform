package com.phomoria.theme;

import java.util.LinkedHashMap;
import java.util.Map;

/** JSON model. Coordinates and dimensions use the 1920x1080 design canvas. */
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
    }

    ThemeConfig withDefaults() {
        ThemeConfig defaults = new ThemeConfig();
        if (screens == null) screens = defaults.screens;
        else defaults.screens.forEach(screens::putIfAbsent);
        if (elements == null) elements = defaults.elements;
        else defaults.elements.forEach(elements::putIfAbsent);
        if (designWidth <= 0) designWidth = 1920;
        if (designHeight <= 0) designHeight = 1080;
        return this;
    }

    public static final class Screen {
        public String background;
        public Screen() {}
        public Screen(String background) { this.background = background; }
    }

    public static final class Element {
        public String image;
        public int x, y, width, height;
        public Element() {}
        public Element(String image, int x, int y, int width, int height) {
            this.image = image; this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }
}
