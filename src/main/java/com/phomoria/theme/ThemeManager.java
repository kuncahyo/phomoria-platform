package com.phomoria.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.InputStream;

/** Loads factory backgrounds and stores user theme edits under %APPDATA%\\Phomoria\\themes\\default. */
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

    /** User-selected animated GIF for the Live Photo loading overlay. */
    public static synchronized Path livePhotoLoadingImage() {
        ThemeConfig.Element element = config().elements.get("result-live-photo-loading");
        if (element != null && element.image != null && !element.image.isBlank()) {
            Path custom = ROOT.resolve("animations").resolve(element.image);
            if (Files.isRegularFile(custom)) return custom;
        }

        // Every installation ships with this factory GIF. Extract it to the
        // writable theme folder so Swing ImageIcon can animate it from a Path.
        Path factory = ROOT.resolve("animations").resolve("phomoria-default-live-photo-loading.gif");
        if (!Files.isRegularFile(factory)) {
            try (InputStream in = ThemeManager.class.getResourceAsStream("/animations/live-photo-loading.gif")) {
                if (in != null) {
                    Files.createDirectories(factory.getParent());
                    Files.copy(in, factory, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                System.err.println("[Theme] Gagal menyiapkan GIF loading bawaan: " + ex.getMessage());
            }
        }
        return Files.isRegularFile(factory) ? factory : null;
    }

    /** Optional PNG background for an inactivity timer element. */
    public static Path timerBackground(String id) {
        ThemeConfig.Element element = config().elements.get(id);
        if (element == null || element.timerBackground == null || element.timerBackground.isBlank()) return null;
        Path asset = ROOT.resolve("timers").resolve(element.timerBackground);
        return Files.isRegularFile(asset) ? asset : null;
    }

    /** Button images are exclusively user overrides stored in APPDATA. */
    public static Path buttonImage(String id) {
        ThemeConfig.Element element = config().elements.get(id);
        if (element == null || element.image == null || element.image.isBlank()) return null;
        Path userAsset = ROOT.resolve("buttons").resolve(element.image);
        return Files.isRegularFile(userAsset) ? userAsset : null;
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
        Files.createDirectories(ROOT.resolve("animations"));
        Files.createDirectories(ROOT.resolve("timers"));
        Files.writeString(CONFIG, GSON.toJson(config()), StandardCharsets.UTF_8);
    }

    public static synchronized void reload() { config = load(); }

    public static synchronized void restoreDefaultButton(String id) throws IOException {
        restoreDefaultElement(id);
    }

    /** Restore the code-defined factory state; remove any user PNG override. */
    public static synchronized void restoreDefaultElement(String id) throws IOException {
        ThemeConfig.Element element = config().elements.get(id);
        if (element == null) throw new IOException("Elemen tidak dikenal: " + id);
        ThemeConfig.Element factory = new ThemeConfig().elements.get(id);
        if (factory == null) throw new IOException("Pengaturan bawaan tidak ditemukan: " + id);

        String previousImage = element.image;
        String previousTimerBackground = element.timerBackground;
        element.x = factory.x;
        element.y = factory.y;
        element.width = factory.width;
        element.height = factory.height;
        element.text = factory.text;
        element.foreground = factory.foreground;
        element.visible = factory.visible;
        element.transparent = factory.transparent;
        element.image = factory.image;
        element.timerBackground = factory.timerBackground;
        element.defaultTimerBackground = factory.timerBackground;
        element.defaultImage = factory.image;
        element.defaultText = factory.text;
        element.defaultForeground = factory.foreground;
        element.defaultVisible = factory.visible;
        element.defaultTransparent = factory.transparent;
        element.loadingTextPosition = factory.loadingTextPosition;

        if (previousTimerBackground != null && !previousTimerBackground.isBlank()
                && !previousTimerBackground.equals(factory.timerBackground)) {
            Files.deleteIfExists(ROOT.resolve("timers").resolve(previousTimerBackground));
        }
        if (previousImage != null && !previousImage.isBlank()
                && !previousImage.equals(factory.image)) {
            Path assetDirectory = "result-live-photo-loading".equals(id)
                    ? ROOT.resolve("animations") : ROOT.resolve("buttons");
            Files.deleteIfExists(assetDirectory.resolve(previousImage));
        }
        save();
    }

    private static ThemeConfig load() {
        try {
            Files.createDirectories(ROOT.resolve("backgrounds"));
            Files.createDirectories(ROOT.resolve("buttons"));
            Files.createDirectories(ROOT.resolve("animations"));
            Files.createDirectories(ROOT.resolve("timers"));
            ThemeConfig loaded;
            if (Files.isRegularFile(CONFIG)) {
                loaded = GSON.fromJson(Files.readString(CONFIG, StandardCharsets.UTF_8), ThemeConfig.class);
            } else {
                // Factory layout/text comes from Java definitions. Do not load the old
                // packaged theme.json because it contains legacy PNG button defaults.
                loaded = new ThemeConfig();
            }
            ThemeConfig result = (loaded == null ? new ThemeConfig() : loaded).withDefaults();
            if (Files.notExists(CONFIG)) {
                config = result;
                save();
            }
            return result;
        } catch (Exception ex) {
            System.err.println("[Theme] Gagal membaca tema: " + ex.getMessage());
            return new ThemeConfig();
        }
    }

    public static void replaceAsset(Path source, Path destination) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("File sumber tidak ditemukan: " + source);
        }
        if (destination == null) {
            throw new IOException("Lokasi tujuan aset tidak valid.");
        }

        Files.createDirectories(destination.toAbsolutePath().getParent());

        // Selecting the currently active asset again is a valid no-op. Avoid asking
        // Windows to overwrite a file with itself (which can report it as locked).
        if (Files.exists(destination) && Files.isSameFile(source, destination)) {
            return;
        }

        // Stage the new bytes beside the destination, then replace the destination
        // in one move. Never delete the old asset before the new one is ready.
        Path temporary = Files.createTempFile(
                destination.toAbsolutePath().getParent(),
                destination.getFileName().toString() + ".",
                ".tmp"
        );
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
