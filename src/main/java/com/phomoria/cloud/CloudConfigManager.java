package com.phomoria.cloud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class CloudConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final File ROOT = new File(
            System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
            "Phomoria"
    );
    private static final File FILE = new File(ROOT, "cloud.json");

    private static CloudConfig config;

    private CloudConfigManager() {}

    public static synchronized CloudConfig getConfig() {
        if (config == null) load();
        return config;
    }

    public static synchronized void load() {
        try {
            if (!ROOT.exists()) ROOT.mkdirs();

            if (!FILE.exists()) {
                config = new CloudConfig();
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                config = GSON.fromJson(reader, CloudConfig.class);
            }

            if (config == null) config = new CloudConfig();
        } catch (Exception ex) {
            config = new CloudConfig();
        }
    }

    public static synchronized void save() {
        try {
            if (!ROOT.exists()) ROOT.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(getConfig(), writer);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
