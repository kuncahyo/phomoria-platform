package com.phomoria.cloud;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Manual V22.6 integration test.
 *
 * Jalankan dari IDE sebagai Java Application.
 * Test ini tidak mengubah UI atau cache aplikasi.
 */
public final class FrameCloudServiceTest {

    private FrameCloudServiceTest() {
    }

    public static void main(String[] args) {
        System.out.println("=== Phomoria V22.6 Frame Cloud Test ===");

        try {
            CloudConfig config = CloudConfigManager.getConfig();
            DeviceInfo device = DeviceManager.getDevice();

            System.out.println("Server      : " + config.getServer());
            System.out.println("Device UUID : " + device.getUuid());
            System.out.println("Token       : " +
                    (config.getToken().isBlank() ? "NOT SET" : "SET"));

            if (config.getToken().isBlank()) {
                throw new IllegalStateException(
                        "Token cloud belum tersedia. Login terlebih dahulu."
                );
            }

            if (device.getUuid().isBlank()) {
                throw new IllegalStateException(
                        "Device UUID belum tersedia."
                );
            }

            FrameCloudService service = new FrameCloudService(config, device.getUuid());

            System.out.println();
            System.out.println("[1] Fetch assigned frames...");

            List<FrameCloudService.CloudFrame> frames =
                    service.fetchAssignedFrames();

            System.out.println("Frames received: " + frames.size());

            for (FrameCloudService.CloudFrame frame : frames) {
                System.out.println("  - ID       : " + frame.getId());
                System.out.println("    Name     : " + frame.getName());
                System.out.println("    Category : " + frame.getCategory());
                System.out.println("    Version  : " + frame.getVersion());
                System.out.println("    SHA-256  : " + frame.getSha256());
                System.out.println("    Size     : " +
                        frame.getWidth() + "x" + frame.getHeight());
                System.out.println("    Status   : " + frame.getStatus());
            }

            if (frames.isEmpty()) {
                throw new IllegalStateException(
                        "API berhasil tetapi tidak ada frame yang di-assign ke device."
                );
            }

            FrameCloudService.CloudFrame frame = frames.get(0);

            System.out.println();
            System.out.println("[2] Download first assigned frame...");
            System.out.println("Frame ID: " + frame.getId());

            byte[] png = service.downloadFrame(frame.getId());

            System.out.println("Downloaded bytes: " + png.length);

            if (png.length == 0) {
                throw new IllegalStateException("File PNG hasil download kosong.");
            }

            Path output = Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "phomoria-v22.6-frame-" + frame.getId() + ".png"
            );

            Files.write(output, png);

            System.out.println("Saved test file: " + output.toAbsolutePath());
            System.out.println();
            System.out.println("=== V22.6 FRAME CLOUD TEST: PASS ===");

        } catch (Exception ex) {
            System.err.println();
            System.err.println("=== V22.6 FRAME CLOUD TEST: FAIL ===");
            System.err.println(ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
