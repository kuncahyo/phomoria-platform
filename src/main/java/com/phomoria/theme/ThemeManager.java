package com.phomoria.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    public static synchronized ThemeConfig config() { if (config == null) config = load(); return config; }
    public static Path root() { return ROOT; }
    public static Path background(String screen) {
        ThemeConfig.Screen value = config().screens.get(screen);
        return value == null || value.background == null || value.background.isBlank() ? null : ROOT.resolve("backgrounds").resolve(value.background);
    }
    public static Path buttonImage(String id) {
        ThemeConfig.Element e = config().elements.get(id);
        return e == null || e.image == null || e.image.isBlank() ? null : ROOT.resolve("buttons").resolve(e.image);
    }
    public static synchronized void save() throws IOException {
        Files.createDirectories(ROOT.resolve("backgrounds")); Files.createDirectories(ROOT.resolve("buttons"));
        Files.writeString(CONFIG, GSON.toJson(config()), StandardCharsets.UTF_8);
    }
    public static synchronized void reload() { config = load(); }

    /** Copies the original shipped button image once; later uploads never replace this backup. */
    public static synchronized void ensureDefaultButton(String id) throws IOException {
        ThemeConfig.Element e = config().elements.get(id);
        if (e == null) throw new IOException("Elemen tidak dikenal: " + id);
        if (e.defaultImage == null || e.defaultImage.isBlank()) e.defaultImage = e.image;
        if (e.defaultImage == null || e.defaultImage.isBlank()) throw new IOException("Aset bawaan belum ditentukan.");
        Path current = ROOT.resolve("buttons").resolve(e.image);
        Path defaults = ROOT.resolve("defaults").resolve("buttons");
        Path saved = defaults.resolve(e.defaultImage);
        Files.createDirectories(defaults);
        if (Files.notExists(saved) && Files.isRegularFile(current)) Files.copy(current, saved);
        if (Files.notExists(saved)) throw new IOException("File aset bawaan tidak ditemukan: " + current);
    }
    public static synchronized void restoreDefaultButton(String id) throws IOException {
        ensureDefaultButton(id);
        ThemeConfig.Element e = config().elements.get(id);
        Path source = ROOT.resolve("defaults").resolve("buttons").resolve(e.defaultImage);
        Path destination = ROOT.resolve("buttons").resolve(e.image);
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        e.transparent = false;
        save();
    }
    private static ThemeConfig load() {
        try {
            Files.createDirectories(ROOT.resolve("backgrounds")); Files.createDirectories(ROOT.resolve("buttons"));
            if (Files.notExists(CONFIG)) { ThemeConfig fresh = new ThemeConfig(); config = fresh; save(); return fresh; }
            ThemeConfig loaded = GSON.fromJson(Files.readString(CONFIG, StandardCharsets.UTF_8), ThemeConfig.class);
            return (loaded == null ? new ThemeConfig() : loaded).withDefaults();
        } catch (Exception ex) { System.err.println("[Theme] Gagal membaca theme.json: " + ex.getMessage()); return new ThemeConfig(); }
    }
    public static void replaceAsset(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent()); Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
}
