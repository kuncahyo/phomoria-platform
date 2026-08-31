package com.phomoria.cloud;

public final class DeviceInfo {
    private String uuid = "";
    private String computerName = "";
    private String windowsUser = "";
    private String operatingSystem = "";
    private String javaVersion = "";

    public String getUuid() { return uuid == null ? "" : uuid; }
    public void setUuid(String uuid) { this.uuid = uuid == null ? "" : uuid; }

    public String getComputerName() { return computerName == null ? "" : computerName; }
    public void setComputerName(String value) { computerName = value == null ? "" : value; }

    public String getWindowsUser() { return windowsUser == null ? "" : windowsUser; }
    public void setWindowsUser(String value) { windowsUser = value == null ? "" : value; }

    public String getOperatingSystem() { return operatingSystem == null ? "" : operatingSystem; }
    public void setOperatingSystem(String value) { operatingSystem = value == null ? "" : value; }

    public String getJavaVersion() { return javaVersion == null ? "" : javaVersion; }
    public void setJavaVersion(String value) { javaVersion = value == null ? "" : value; }
}
