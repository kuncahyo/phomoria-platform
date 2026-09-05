package com.phomoria.cloud;

/**
 * Manual V22.7 integration test.
 *
 * Run as a normal Java main class.
 */
public final class FrameSyncServiceTest {

    private FrameSyncServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Phomoria V22.7 Frame Sync Test ===");

        CloudConfig config = CloudConfigManager.getConfig();
        DeviceInfo device = DeviceManager.getDevice();

        System.out.println("Server      : " + config.getServer());
        System.out.println("Device UUID : " + device.getUuid());
        System.out.println("Token       : "
                + (config.getToken().isBlank() ? "NOT SET" : "SET"));
        System.out.println();

        if (config.getToken().isBlank()) {
            throw new IllegalStateException(
                    "Cloud token is not set. Login to Phomoria first.");
        }

        FrameSyncService sync = new FrameSyncService();

        System.out.println("[1] First synchronization...");
        FrameSyncService.SyncResult first = sync.sync();
        printResult(first);

        System.out.println();
        System.out.println("[2] Second synchronization...");
        FrameSyncService.SyncResult second = sync.sync();
        printResult(second);

        if (second.count(FrameSyncService.Action.DOWNLOADED) > 0
                || second.count(FrameSyncService.Action.UPDATED) > 0) {
            throw new IllegalStateException(
                    "Second sync downloaded/updated a frame. "
                    + "Expected unchanged cache for identical cloud data.");
        }

        System.out.println();
        System.out.println("=== V22.7 FRAME SYNC TEST: PASS ===");
        System.out.println("Second sync confirmed existing frame cache is reused.");
    }

    private static void printResult(
            FrameSyncService.SyncResult result
    ) {
        System.out.println("Items: " + result.getItems().size());

        for (FrameSyncService.SyncItem item : result.getItems()) {
            System.out.println(
                    "  - ID=" + item.getFrameId()
                    + ", Name=" + item.getName()
                    + ", Action=" + item.getAction()
            );
        }

        System.out.println(
                "  DOWNLOADED=" + result.count(FrameSyncService.Action.DOWNLOADED)
                + ", UPDATED=" + result.count(FrameSyncService.Action.UPDATED)
                + ", UNCHANGED=" + result.count(FrameSyncService.Action.UNCHANGED)
                + ", DELETED=" + result.count(FrameSyncService.Action.DELETED)
        );
    }
}
