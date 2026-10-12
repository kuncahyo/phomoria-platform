package com.phomoria.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Dimension;
import java.awt.Point;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads editable theme assets from the application folder, outside the JAR. */
public final class ThemeManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = Path.of(System.getProperty("user.dir"), "themes", "default");
    private static final Path CONFIG = ROOT.resolve("theme.json");
    private static ThemeConfig config;

    private ThemeManager() {}

    public static synchronized ThemeConfig config() {
        if (config == null) config = load();
        return config;
    }

    public static Path root() { return ROOT; }
    public static Path background(String screen) {
        String name = config().screens.getOrDefault(screen, new ThemeConfig.Screen()).background;
        return name == null || name.isBlank() ? null : ROOT.resolve("backgrounds").resolve(name);
    }
    public static Path buttonImage(String id) {
        ThemeConfig.Element element = config().elements.get(id);
        return element == null || element.image == null ? null : ROOT.resolve("buttons").resolve(element.image);
    }

    public static synchronized void save() throws IOException {
        Files.createDirectories(ROOT.resolve("backgrounds"));
        Files.createDirectories(ROOT.resolve("buttons"));
        Files.writeString(CONFIG, GSON.toJson(config()), StandardCharsets.UTF_8);
    }

    public static synchronized void reload() { config = load(); }

    private static ThemeConfig load() {
        try {
            Files.createDirectories(ROOT.resolve("backgrounds"));
            Files.createDirectories(ROOT.resolve("buttons"));
            if (Files.notExists(CONFIG)) {
                ThemeConfig defaults = new ThemeConfig();
                Files.writeString(CONFIG, GSON.toJson(defaults), StandardCharsets.UTF_8);
                return defaults;
            }
            ThemeConfig loaded = GSON.fromJson(Files.readString(CONFIG, StandardCharsets.UTF_8), ThemeConfig.class);
            return loaded == null ? new ThemeConfig() : loaded.withDefaults();
        } catch (Exception ex) {
            System.err.println("[Theme] Gagal membaca theme.json: " + ex.getMessage());
            return new ThemeConfig();
        }
    }

    public static void replaceAsset(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
}
