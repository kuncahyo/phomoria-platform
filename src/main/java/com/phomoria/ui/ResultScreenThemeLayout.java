package com.phomoria.ui;

import com.phomoria.theme.ThemeButton;
import com.phomoria.theme.ThemeConfig;
import com.phomoria.theme.ThemeManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JLabel;

/** Responsive design-canvas layout for Final Preview / Result Screen. */
public final class ResultScreenThemeLayout implements LayoutManager2 {
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
        double sx = parent.getWidth() / (double)Math.max(1, config.designWidth);
        double sy = parent.getHeight() / (double)Math.max(1, config.designHeight);
        for (Map.Entry<String, Component> entry : components.entrySet()) {
            String id = entry.getKey();
            Component component = entry.getValue();
            if ("result-background".equals(id)) {
                component.setBounds(0, 0, parent.getWidth(), parent.getHeight());
                component.setVisible(true);
                continue;
            }
            ThemeConfig.Element element = config.elements.get(id);
            if (element == null) {
                // The Live Photo loader is a runtime overlay, not a theme-editor
                // element. Give it the exact preview bounds so its animation can
                // actually paint above the preview on every screen size.
                if ("result-live-photo-loading".equals(id)) {
                    Component preview = components.get("result-preview");
                    if (preview != null) {
                        component.setBounds(preview.getBounds());
                        component.setVisible(component.isVisible());
                    } else {
                        component.setBounds(0, 0, parent.getWidth(), parent.getHeight());
                    }
                }
                continue;
            }
            int x = (int)Math.round(element.x * sx);
            int y = (int)Math.round(element.y * sy);
            int w = Math.max(1, (int)Math.round(element.width * sx));
            int h = Math.max(1, (int)Math.round(element.height * sy));
            component.setBounds(new Rectangle(x, y, w, h));
            // Loading visibility is runtime state (Live Photo rendering), not a static theme property.
            if (!"result-live-photo-loading".equals(id)) {
                component.setVisible(Boolean.TRUE.equals(element.visible));
            }
            applyStyle(component, id, element, sy);
            if (component instanceof com.phomoria.theme.ThemeCountdownTimer timer) timer.applyTheme(id);
        }
    }

    private static void applyStyle(Component component, String id, ThemeConfig.Element element, double sy) {
        Color color;
        try { color = element.foreground == null ? Color.WHITE : Color.decode(element.foreground); }
        catch (RuntimeException ex) { color = Color.WHITE; }
        if (component instanceof JLabel label) {
            label.setForeground(color);
            if (element.text != null && !element.text.isBlank()) label.setText(element.text);
            float size = (float)Math.max(10, Math.min(48, element.height * sy * 0.42));
            label.setFont(label.getFont().deriveFont(Font.BOLD, size));
        } else if (component instanceof ThemeButton button) {
            button.applyTheme(element);
            float size = (float)Math.max(10, Math.min(32, element.height * sy * 0.30));
            button.setFont(button.getFont().deriveFont(Font.BOLD, size));
        }
    }
}
