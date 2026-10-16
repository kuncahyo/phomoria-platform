package com.phomoria.print.windows;

import java.util.Arrays;
import java.util.Objects;

/** Immutable raw DEVMODE snapshot returned by the installed Windows printer driver. */
public final class PrinterProfileData {
    private final String printerName;
    private final byte[] devMode;

    public PrinterProfileData(String printerName, byte[] devMode) {
        this.printerName = Objects.requireNonNull(printerName, "printerName");
        this.devMode = Objects.requireNonNull(devMode, "devMode").clone();
        if (this.devMode.length == 0) {
            throw new IllegalArgumentException("DEVMODE data is empty");
        }
    }

    public String getPrinterName() {
        return printerName;
    }

    public byte[] getDevMode() {
        return devMode.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PrinterProfileData that)) return false;
        return printerName.equals(that.printerName) && Arrays.equals(devMode, that.devMode);
    }

    @Override
    public int hashCode() {
        return 31 * printerName.hashCode() + Arrays.hashCode(devMode);
    }
}
