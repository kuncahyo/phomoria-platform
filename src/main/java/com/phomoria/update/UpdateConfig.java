package com.phomoria.update;

public final class UpdateConfig {

    /*
     * Development endpoint.
     * Replace with Laravel production endpoint later.
     */
    public static final String UPDATE_URL =
            "http://127.0.0.1:8099/update.json";

    /*
     * Keep true while running from NetBeans.
     *
     * When the final Windows/JAR packaging is ready, set this to false.
     */
    public static final boolean SIMULATION_MODE = false;

    private UpdateConfig() {}
}
