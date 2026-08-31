package com.phomoria.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class SettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "Phomoria");
    private static final File FILE = new File(DIR, "settings.json");

    private SettingsStore() {}

    public static AppSettings load() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            if (!FILE.exists()) return new AppSettings();
            try (Reader r = new InputStreamReader(new FileInputStream(FILE), StandardCharsets.UTF_8)) {
                AppSettings settings = GSON.fromJson(r, AppSettings.class);
                return settings == null ? new AppSettings() : settings;
            }
        } catch (Exception ex) {
            return new AppSettings();
        }
    }

    public static void save(AppSettings settings) {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(settings, w);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
