package com.phomoria.update;

public final class AppVersion {
    private static final String VERSION = "0.2.0";

    private AppVersion() {}

    public static String current() {
        return VERSION;
    }
}
