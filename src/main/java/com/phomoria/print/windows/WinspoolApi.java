package com.phomoria.print.windows;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.ptr.PointerByReference;

/** Minimal Unicode Winspool bindings used to edit and capture driver DEVMODE data. */
interface WinspoolApi extends StdCallLibrary {
    WinspoolApi INSTANCE = Native.load("Winspool.drv", WinspoolApi.class, W32APIOptions.UNICODE_OPTIONS);

    boolean OpenPrinter(WString printerName, PointerByReference printerHandle, Pointer defaults);
    boolean ClosePrinter(Pointer printerHandle);
    int DocumentProperties(Pointer hwnd, Pointer printerHandle, WString deviceName,
                           Pointer devModeOutput, Pointer devModeInput, int mode);
}
