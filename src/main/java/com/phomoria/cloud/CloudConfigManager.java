package com.phomoria.cloud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.phomoria.debug.DebugLog;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class CloudConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File ROOT = new File(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "Phomoria");
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

            String server = config.getServer();
            if (isLocalDevelopmentServer(server)) {
                config.setServer(CloudConfig.DEFAULT_SERVER);
                save();
                DebugLog.info("Cloud server migrated to production: " + CloudConfig.DEFAULT_SERVER);
            }
        } catch (Exception ex) {
            DebugLog.error("Failed to load cloud configuration: " + FILE.getAbsolutePath(), ex);
            config = new CloudConfig();
        }
    }

    private static boolean isLocalDevelopmentServer(String server) {
        if (server == null || server.isBlank()) return true;
        String normalized = server.trim().toLowerCase();
        return normalized.equals("http://127.0.0.1:8000")
                || normalized.equals("http://localhost:8000")
                || normalized.equals("http://127.0.0.1")
                || normalized.equals("http://localhost");
    }

    public static synchronized void save() {
        try {
            if (!ROOT.exists()) ROOT.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(getConfig(), writer);
            }
        } catch (Exception ex) {
            DebugLog.error("Failed to save cloud configuration: " + FILE.getAbsolutePath(), ex);
        }
    }
}
