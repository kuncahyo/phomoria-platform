package com.phomoria.print.windows;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;

import java.awt.Component;
import java.io.IOException;
import java.util.Optional;

/** Opens the installed driver's native Document Properties dialog and captures its DEVMODE. */
public final class WindowsPrinterProfileEditor {
    private static final int DM_OUT_BUFFER = 0x00000002;
    private static final int DM_IN_BUFFER = 0x00000008;
    private static final int DM_IN_PROMPT = 0x00000004;

    private final PrinterProfileStore store;

    public WindowsPrinterProfileEditor() {
        this(new PrinterProfileStore());
    }

    public WindowsPrinterProfileEditor(PrinterProfileStore store) {
        this.store = store;
    }

    public boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Shows the actual driver dialog. If a profile already exists for this printer,
     * its DEVMODE is used as the starting point; otherwise the driver's current defaults are used.
     * Returns false when the user cancels the dialog.
     */
    public boolean editAndSave(Component owner, String printerName, PrinterProfileId profileId)
            throws IOException {
        if (!isSupported()) throw new IOException("Printer profiles can only be edited on Windows.");
        if (printerName == null || printerName.isBlank()) {
            throw new IllegalArgumentException("Printer name is required.");
        }

        PointerByReference handleRef = new PointerByReference();
        WString device = new WString(printerName);
        WinspoolApi api;
        try {
            api = WinspoolApi.INSTANCE;
        } catch (UnsatisfiedLinkError ex) {
            throw new IOException("Windows Print Spooler API could not be loaded.", ex);
        }

        if (!api.OpenPrinter(device, handleRef, null)) {
            throw new IOException("OpenPrinter failed for: " + printerName
                    + " (Windows error " + com.sun.jna.Native.getLastError() + ")");
        }

        Pointer printer = handleRef.getValue();
        try {
            int required = api.DocumentProperties(null, printer, device, null, null, 0);
            if (required <= 0 || required > 1024 * 1024) {
                throw new IOException("Driver returned an invalid DEVMODE size: " + required);
            }

            Memory input = new Memory(required);
            Optional<PrinterProfileData> saved = store.load(profileId);
            if (saved.isPresent() && saved.get().getPrinterName().equalsIgnoreCase(printerName)) {
                byte[] bytes = saved.get().getDevMode();
                if (bytes.length != required) {
                    throw new IOException("Saved profile size differs from the current driver DEVMODE. "
                            + "Recreate this profile after a driver update.");
                }
                input.write(0, bytes, 0, bytes.length);
            } else {
                int initialized = api.DocumentProperties(null, printer, device, input, null, DM_OUT_BUFFER);
                if (initialized != 1) {
                    throw new IOException("Could not read the printer driver's default DEVMODE.");
                }
            }

            Memory output = new Memory(required);
            int result = api.DocumentProperties(null, printer, device, output, input,
                    DM_IN_PROMPT | DM_IN_BUFFER | DM_OUT_BUFFER);
            if (result == 2) return false; // IDCANCEL
            if (result != 1) { // IDOK is the documented success result
                throw new IOException("The printer driver settings dialog failed (result " + result + ").");
            }

            byte[] captured = output.getByteArray(0, required);
            store.save(profileId, new PrinterProfileData(printerName, captured));
            return true;
        } finally {
            api.ClosePrinter(printer);
        }
    }

    /** Returns the saved raw driver data, if the selected profile exists. */
    public Optional<PrinterProfileData> load(PrinterProfileId profileId) throws IOException {
        return store.load(profileId);
    }
}
