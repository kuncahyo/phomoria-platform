package com.phomoria.print.windows;

/** The two locally stored Windows printer-driver profiles. */
public enum PrinterProfileId {
    PROFILE_1("profile1"),
    PROFILE_2("profile2");

    private final String fileKey;

    PrinterProfileId(String fileKey) {
        this.fileKey = fileKey;
    }

    String fileKey() {
        return fileKey;
    }
}
