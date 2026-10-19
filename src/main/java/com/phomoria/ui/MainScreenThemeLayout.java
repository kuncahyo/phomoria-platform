package com.phomoria.ui;

import com.phomoria.theme.ThemeConfig;
import com.phomoria.theme.ThemeManager;
import com.phomoria.theme.ThemeButton;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JLabel;

/** Responsive 1920x1080 design-canvas layout for Main / Photo Capture. */
public final class MainScreenThemeLayout implements LayoutManager2 {
    private final Map<String, Component> components = new LinkedHashMap<>();

    @Override public void addLayoutComponent(Component component, Object constraints) {
        if (!(constraints instanceof String id)) throw new IllegalArgumentException("Theme element id is required");
        components.put(id, component);
    }
    @Override public void addLayoutComponent(String name, Component component) { addLayoutComponent(component, name); }
    @Override public void removeLayoutComponent(Component component) { components.values().removeIf(value -> value == component); }
    @Override public Dimension preferredLayoutSize(Container parent) { return new Dimension(1280, 720); }
    @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(800, 450); }
    @Override public Dimension maximumLayoutSize(Container target) { return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE); }
    @Override public float getLayoutAlignmentX(Container target) { return 0.5f; }
    @Override public float getLayoutAlignmentY(Container target) { return 0.5f; }
    @Override public void invalidateLayout(Container target) {}

    @Override public void layoutContainer(Container parent) {
        ThemeConfig config = ThemeManager.config();
        double sx = parent.getWidth() / (double) Math.max(1, config.designWidth);
        double sy = parent.getHeight() / (double) Math.max(1, config.designHeight);
        for (Map.Entry<String, Component> entry : components.entrySet()) {
            String id = entry.getKey();
            Component component = entry.getValue();
            ThemeConfig.Element element = config.elements.get(id);
            if ("main-background".equals(id)) {
                component.setBounds(0, 0, parent.getWidth(), parent.getHeight());
                component.setVisible(true);
                continue;
            }
            if (element == null) continue;
            int x = (int) Math.round(element.x * sx);
            int y = (int) Math.round(element.y * sy);
            int w = Math.max(1, (int) Math.round(element.width * sx));
            int h = Math.max(1, (int) Math.round(element.height * sy));
            component.setBounds(new Rectangle(x, y, w, h));
            component.setVisible(Boolean.TRUE.equals(element.visible));
            applyStyle(component, id, element, sy);
        }
        Component countdown = components.get("main-camera");
        if (countdown instanceof java.awt.Container cameraLayer) {
            for (Component child : cameraLayer.getComponents()) {
                if (child instanceof CountdownOverlay overlay) {
                    overlay.setThemeElement(config.elements.get("main-countdown"));
                    overlay.setDesignSize(config.designWidth, config.designHeight);
                }
            }
        }
    }

    private static void applyStyle(Component component, String id, ThemeConfig.Element element, double scaleY) {
        java.awt.Color color = parseColor(element.foreground, java.awt.Color.WHITE);
        if (component instanceof JLabel label) {
            label.setForeground(color);
            if ("main-title".equals(id) && element.text != null && !element.text.isBlank()) label.setText(element.text);
            float size = (float) Math.max(10, Math.min(48, element.height * scaleY * 0.42));
            label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD, size));
        } else if (component instanceof ThemeButton button) {
            button.applyTheme(element);
            float size = (float) Math.max(10, Math.min(32, element.height * scaleY * 0.30));
            button.setFont(button.getFont().deriveFont(java.awt.Font.BOLD, size));
        }
    }

    private static java.awt.Color parseColor(String value, java.awt.Color fallback) {
        try { return value == null ? fallback : java.awt.Color.decode(value); }
        catch (RuntimeException ex) { return fallback; }
    }
}
