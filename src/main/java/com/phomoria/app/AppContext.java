package com.phomoria.app;

import com.phomoria.config.AppSettings;
import com.phomoria.config.SettingsStore;
import com.phomoria.cloud.CloudConfigManager;
import com.phomoria.cloud.DeviceManager;
import com.phomoria.session.PhotoSession;
import com.phomoria.effects.PhotoEffectSession;

public final class AppContext {
    private static AppSettings settings;
    private static PhotoSession session;
    private static PhotoEffectSession effectSession;

    private AppContext() {}

    public static void initialize() {
        CloudConfigManager.load();
        DeviceManager.load();
        settings = SettingsStore.load();

        String cloudServer = CloudConfigManager.getConfig().getServer();
        if (cloudServer != null && !cloudServer.isBlank()
                && !cloudServer.equals("http://127.0.0.1:8000")) {
            settings.setApiServer(cloudServer);
            SettingsStore.save(settings);
        }

        session = new PhotoSession(settings.getPhotoSlotCount());
        effectSession = new PhotoEffectSession(settings.getPhotoSlotCount());
    }

    public static AppSettings settings() {
        return settings;
    }

    public static void saveSettings() {
        SettingsStore.save(settings);
    }

    public static PhotoSession newSession() {
        session = new PhotoSession(settings.getPhotoSlotCount());
        effectSession = new PhotoEffectSession(settings.getPhotoSlotCount());
        return session;
    }

    public static PhotoSession session() {
        return session;
    }

    public static PhotoEffectSession effectSession() {
        if (effectSession == null) {
            effectSession = new PhotoEffectSession(session == null ? settings.getPhotoSlotCount() : session.getSlotCount());
        }
        return effectSession;
    }

    public static void setEffectSession(PhotoEffectSession value) {
        effectSession = value;
    }
}
