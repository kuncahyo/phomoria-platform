package com.phomoria.app;

import com.phomoria.cloud.CloudConfigManager;
import com.phomoria.cloud.CloudFrameSupport;
import com.phomoria.cloud.DeviceManager;
import com.phomoria.config.AppSettings;
import com.phomoria.config.SettingsStore;
import com.phomoria.effects.PhotoEffectSession;
import com.phomoria.session.PhotoSession;

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
        if (cloudServer != null && !cloudServer.isBlank()) {
            settings.setApiServer(cloudServer);
            SettingsStore.save(settings);
        }

        int photoCount = CloudFrameSupport.selectedPhotoCount(
                settings.getPhotoSlotCount()
        );
        session = new PhotoSession(photoCount);
        effectSession = new PhotoEffectSession(photoCount);
    }

    public static AppSettings settings() {
        return settings;
    }

    public static void saveSettings() {
        SettingsStore.save(settings);
    }

    public static PhotoSession newSession() {
        int photoCount = CloudFrameSupport.selectedPhotoCount(
                settings.getPhotoSlotCount()
        );
        session = new PhotoSession(photoCount);
        effectSession = new PhotoEffectSession(photoCount);
        return session;
    }

    public static PhotoSession session() {
        return session;
    }

    public static PhotoEffectSession effectSession() {
        if (effectSession == null) {
            effectSession = new PhotoEffectSession(
                    session == null
                            ? CloudFrameSupport.selectedPhotoCount(settings.getPhotoSlotCount())
                            : session.getSlotCount()
            );
        }
        return effectSession;
    }

    public static void setEffectSession(PhotoEffectSession value) {
        effectSession = value;
    }
}
