package com.phomoria.cloud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.UUID;
import com.phomoria.debug.DebugLog;

public final class DeviceManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final File ROOT = new File(
            System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
            "Phomoria"
    );
    private static final File FILE = new File(ROOT, "device.json");

    private static DeviceInfo device;

    private DeviceManager() {}

    public static synchronized DeviceInfo getDevice() {
        if (device == null) load();
        return device;
    }

    public static synchronized void load() {
        try {
            if (!ROOT.exists()) ROOT.mkdirs();

            if (!FILE.exists()) {
                create();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                device = GSON.fromJson(reader, DeviceInfo.class);
            }

            if (device == null || device.getUuid().isBlank()) create();
        } catch (Exception ex) {
            DebugLog.error("Failed to load device information: " + FILE.getAbsolutePath(), ex);
            create();
        }
    }

    private static void create() {
        device = new DeviceInfo();
        device.setUuid(UUID.randomUUID().toString());
        device.setComputerName(System.getenv("COMPUTERNAME"));
        device.setWindowsUser(System.getProperty("user.name"));
        device.setOperatingSystem(System.getProperty("os.name"));
        device.setJavaVersion(System.getProperty("java.version"));
        save();
    }

    public static synchronized void save() {
        try {
            if (!ROOT.exists()) ROOT.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(getDevice(), writer);
            }
        } catch (Exception ex) {
            DebugLog.error("Failed to save device information: " + FILE.getAbsolutePath(), ex);
        }
    }
}
