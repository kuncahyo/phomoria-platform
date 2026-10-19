package com.phomoria.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads factory assets and stores user edits under %APPDATA%\Phomoria\themes\default. */
public final class ThemeManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DEFAULT_ROOT = Path.of(System.getProperty("user.dir"), "themes", "default");
    private static final Path ROOT = userThemeRoot();
    private static final Path CONFIG = ROOT.resolve("theme.json");
    private static ThemeConfig config;

    private ThemeManager() {}

    private static Path userThemeRoot() {
        String appData = System.getenv("APPDATA");
        Path roaming = (appData == null || appData.isBlank())
                ? Path.of(System.getProperty("user.home"), "AppData", "Roaming") : Path.of(appData);
        return roaming.resolve("Phomoria").resolve("themes").resolve("default");
    }

    public static synchronized ThemeConfig config() { if (config == null) config = load(); return config; }
    public static Path root() { return ROOT; }
    public static Path defaultRoot() { return DEFAULT_ROOT; }

    public static Path background(String screen) {
        ThemeConfig.Screen value = config().screens.get(screen);
        if (value == null || value.background == null || value.background.isBlank()) return null;
        return resolveUserOrDefault("backgrounds", value.background);
    }

    public static Path buttonImage(String id) {
        ThemeConfig.Element element = config().elements.get(id);
        if (element == null || element.image == null || element.image.isBlank()) return null;
        return resolveUserOrDefault("buttons", element.image);
    }

    private static Path resolveUserOrDefault(String category, String filename) {
        Path userAsset = ROOT.resolve(category).resolve(filename);
        if (Files.isRegularFile(userAsset)) return userAsset;
        Path factoryAsset = DEFAULT_ROOT.resolve(category).resolve(filename);
        return Files.isRegularFile(factoryAsset) ? factoryAsset : userAsset;
    }

    public static synchronized void save() throws IOException {
        Files.createDirectories(ROOT.resolve("backgrounds"));
        Files.createDirectories(ROOT.resolve("buttons"));
        Files.writeString(CONFIG, GSON.toJson(config()), StandardCharsets.UTF_8);
    }
    public static synchronized void reload() { config = load(); }

    /** Restore factory geometry for every element; restore PNG only when one exists. */
    public static synchronized void restoreDefaultButton(String id) throws IOException {
        restoreDefaultElement(id);
    }

    /** Restore geometry, text, color, visibility and (when present) image for one element. */
    public static synchronized void restoreDefaultElement(String id) throws IOException {
        ThemeConfig.Element element = config().elements.get(id);
        if (element == null) throw new IOException("Elemen tidak dikenal: " + id);
        ThemeConfig defaults = new ThemeConfig();
        ThemeConfig.Element factory = defaults.elements.get(id);
        if (factory == null) throw new IOException("Pengaturan bawaan tidak ditemukan: " + id);

        element.x = factory.x;
        element.y = factory.y;
        element.width = factory.width;
        element.height = factory.height;
        element.text = factory.text;
        element.foreground = factory.foreground;
        element.visible = factory.visible;
        element.transparent = factory.transparent;
        element.defaultText = factory.text;
        element.defaultForeground = factory.foreground;
        element.defaultVisible = factory.visible;
        element.defaultTransparent = factory.transparent;

        if (factory.image != null && !factory.image.isBlank()) {
            Path factoryAsset = DEFAULT_ROOT.resolve("buttons").resolve(factory.image);
            if (!Files.isRegularFile(factoryAsset)) {
                throw new IOException("File aset bawaan tidak ditemukan: " + factoryAsset);
            }
            if (element.image != null && !element.image.isBlank()) {
                Files.deleteIfExists(ROOT.resolve("buttons").resolve(element.image));
            }
            element.image = factory.image;
            element.defaultImage = factory.image;
        } else {
            element.image = null;
            element.defaultImage = null;
        }
        save();
    }

    private static ThemeConfig load() {
        try {
            Files.createDirectories(ROOT.resolve("backgrounds"));
            Files.createDirectories(ROOT.resolve("buttons"));
            ThemeConfig loaded;
            if (Files.isRegularFile(CONFIG)) loaded = GSON.fromJson(Files.readString(CONFIG, StandardCharsets.UTF_8), ThemeConfig.class);
            else {
                Path factoryConfig = DEFAULT_ROOT.resolve("theme.json");
                loaded = Files.isRegularFile(factoryConfig) ? GSON.fromJson(Files.readString(factoryConfig, StandardCharsets.UTF_8), ThemeConfig.class) : new ThemeConfig();
            }
            ThemeConfig result = (loaded == null ? new ThemeConfig() : loaded).withDefaults();
            if (Files.notExists(CONFIG)) { config = result; save(); }
            return result;
        } catch (Exception ex) {
            System.err.println("[Theme] Gagal membaca tema: " + ex.getMessage());
            return new ThemeConfig();
        }
    }

    public static void replaceAsset(Path source, Path destination) throws IOException {
        if (source == null || !Files.isRegularFile(source)) throw new IOException("File sumber tidak ditemukan: " + source);
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
}
