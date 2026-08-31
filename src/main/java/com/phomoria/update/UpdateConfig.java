package com.phomoria.update;

public final class UpdateConfig {
    /*
     * Development endpoint.
     *
     * Nanti ketika Laravel sudah siap, cukup ubah URL ini
     * ke endpoint API produksi. Jangan menaruh logic Laravel
     * di dalam UI atau MainScreen.
     */
    public static final String UPDATE_URL =
            "http://127.0.0.1:8099/update.json";

    /*
     * true = mode simulasi lokal.
     *
     * Dalam mode produksi harus false. Saat false, mandatory update
     * tidak boleh dilewati setelah paket berhasil diunduh tetapi
     * installer produksi belum berhasil.
     */
    public static final boolean SIMULATION_MODE = true;

    private UpdateConfig() {}
}
