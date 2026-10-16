package com.phomoria.print.windows;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;

/**
 * Sends a single raster image to a Windows printer using the saved driver's
 * DEVMODE. This class is intentionally separate from the production print path.
 */
public final class WindowsProfilePrinter {
    private static final int HORZRES = 8;
    private static final int VERTRES = 10;
    private static final int DIB_RGB_COLORS = 0;
    private static final int SRCCOPY = 0x00CC0020;
    private static final int BI_RGB = 0;
    private static final int BITS_PER_PIXEL = 24;

    private final PrinterProfileStore profileStore;

    public WindowsProfilePrinter(PrinterProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    public void print(String printerName, PrinterProfileId profileId, BufferedImage image)
            throws IOException {
        if (!isWindows()) {
            throw new IOException("Tes cetak profil hanya tersedia di Windows.");
        }
        if (printerName == null || printerName.isBlank()) {
            throw new IOException("Nama printer belum ditentukan.");
        }
        if (image == null) {
            throw new IOException("Gambar tes tidak tersedia.");
        }

        Optional<PrinterProfileData> optional = profileStore.load(profileId);
        if (optional.isEmpty()) {
            throw new IOException(profileId + " belum dikonfigurasi.");
        }

        PrinterProfileData profile = optional.get();
        if (!profile.getPrinterName().equalsIgnoreCase(printerName)) {
            throw new IOException("Profil " + profileId + " tersimpan untuk printer \""
                    + profile.getPrinterName() + "\", sedangkan printer terpilih adalah \""
                    + printerName + "\". Atur ulang profil untuk printer yang dipilih.");
        }

        byte[] devMode = profile.getDevMode();
        Memory devModeMemory = new Memory(devMode.length);
        devModeMemory.write(0, devMode, 0, devMode.length);

        Pointer dc = GdiApi.INSTANCE.CreateDC(
                new WString("WINSPOOL"), new WString(printerName), null, devModeMemory);
        if (dc == null) {
            throw nativeError("CreateDC");
        }

        boolean documentStarted = false;
        try {
            int width = GdiApi.INSTANCE.GetDeviceCaps(dc, HORZRES);
            int height = GdiApi.INSTANCE.GetDeviceCaps(dc, VERTRES);
            if (width <= 0 || height <= 0) {
                throw nativeError("GetDeviceCaps");
            }

            Dib dib = createDib(image);
            DocInfo docInfo = new DocInfo();
            docInfo.cbSize = docInfo.size();
            docInfo.lpszDocName = new WString("Phomoria - Test " + profileId.name());
            docInfo.lpszOutput = null;
            docInfo.lpszDatatype = null;
            docInfo.fwType = 0;
            docInfo.write();

            int jobId = GdiApi.INSTANCE.StartDoc(dc, docInfo);
            if (jobId <= 0) {
                throw nativeError("StartDoc");
            }
            documentStarted = true;

            if (GdiApi.INSTANCE.StartPage(dc) <= 0) {
                throw nativeError("StartPage");
            }

            double scale = Math.min((double) width / image.getWidth(),
                    (double) height / image.getHeight());
            int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int x = (width - drawWidth) / 2;
            int y = (height - drawHeight) / 2;

            int copied = GdiApi.INSTANCE.StretchDIBits(
                    dc,
                    x, y, drawWidth, drawHeight,
                    0, 0, image.getWidth(), image.getHeight(),
                    dib.pixels, dib.info, DIB_RGB_COLORS, SRCCOPY);

            if (copied == 0 || copied == 0xFFFFFFFF) {
                throw nativeError("StretchDIBits");
            }
            if (GdiApi.INSTANCE.EndPage(dc) <= 0) {
                throw nativeError("EndPage");
            }
            if (GdiApi.INSTANCE.EndDoc(dc) <= 0) {
                throw nativeError("EndDoc");
            }
            documentStarted = false;
        } finally {
            if (documentStarted) {
                GdiApi.INSTANCE.AbortDoc(dc);
            }
            GdiApi.INSTANCE.DeleteDC(dc);
        }
    }

    private static Dib createDib(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int stride = ((width * BITS_PER_PIXEL + 31) / 32) * 4;
        int imageBytes = stride * height;

        Memory pixels = new Memory(imageBytes);
        byte[] row = new byte[stride];

        // Negative DIB height means top-down rows.
        for (int y = 0; y < height; y++) {
            java.util.Arrays.fill(row, (byte) 0);
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int offset = x * 3;
                row[offset] = (byte) (rgb & 0xFF);          // B
                row[offset + 1] = (byte) ((rgb >>> 8) & 0xFF); // G
                row[offset + 2] = (byte) ((rgb >>> 16) & 0xFF); // R
            }
            pixels.write((long) y * stride, row, 0, stride);
        }

        Memory info = new Memory(44); // BITMAPINFOHEADER + one RGBQUAD
        info.clear();
        info.setInt(0, 40);                 // biSize
        info.setInt(4, width);              // biWidth
        info.setInt(8, -height);            // biHeight (top-down)
        info.setShort(12, (short) 1);       // biPlanes
        info.setShort(14, (short) 24);      // biBitCount
        info.setInt(16, BI_RGB);            // biCompression
        info.setInt(20, imageBytes);        // biSizeImage
        info.setInt(24, 0);                 // biXPelsPerMeter
        info.setInt(28, 0);                 // biYPelsPerMeter
        info.setInt(32, 0);                 // biClrUsed
        info.setInt(36, 0);                 // biClrImportant
        return new Dib(pixels, info);
    }

    private static IOException nativeError(String operation) {
        int code = Native.getLastError();
        return new IOException(operation + " gagal. Windows error code: " + code);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .contains("win");
    }

    private record Dib(Memory pixels, Memory info) { }

    @Structure.FieldOrder({"cbSize", "lpszDocName", "lpszOutput", "lpszDatatype", "fwType"})
    public static class DocInfo extends Structure {
        public int cbSize;
        public WString lpszDocName;
        public WString lpszOutput;
        public WString lpszDatatype;
        public int fwType;
    }

    private interface GdiApi extends StdCallLibrary {
        GdiApi INSTANCE = Native.load("gdi32", GdiApi.class, W32APIOptions.UNICODE_OPTIONS);

        Pointer CreateDC(WString driver, WString device, WString output, Pointer initData);
        int GetDeviceCaps(Pointer hdc, int index);
        int StartDoc(Pointer hdc, DocInfo docInfo);
        int StartPage(Pointer hdc);
        int EndPage(Pointer hdc);
        int EndDoc(Pointer hdc);
        int AbortDoc(Pointer hdc);
        boolean DeleteDC(Pointer hdc);
        int StretchDIBits(Pointer hdc,
                          int xDest, int yDest, int destWidth, int destHeight,
                          int xSrc, int ySrc, int srcWidth, int srcHeight,
                          Pointer bits, Pointer bitmapInfo, int usage, int rasterOperation);
    }
}
