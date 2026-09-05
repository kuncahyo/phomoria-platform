package com.phomoria.update;

public final class UpdateConfig {

    /*
     * Production update metadata endpoint.
     * The update.json file will be hosted by Phomoria Cloud.
     */
    public static final String UPDATE_URL =
            "https://phomoria.com/sub/update.json";

    /*
     * Production mode.
     */
    public static final boolean SIMULATION_MODE = false;

    private UpdateConfig() {}
}
