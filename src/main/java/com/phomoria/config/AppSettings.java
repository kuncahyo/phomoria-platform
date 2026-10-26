package com.phomoria.config;

import com.phomoria.effects.PhotoEffectSettings;
import com.phomoria.frame.PrintPaperSize;

public final class AppSettings {
    private String apiServer = "https://phomoria.com/sub";
    private String cameraName = "";
    private int photoSlotCount = 3;
    private int price = 25000;
    private int captureCountdownSeconds = 3;
    private int photoPreviewSeconds = 5;
    private int finalReviewSeconds = 10;
    // Page inactivity timers (seconds).
    private int qrisTimerSeconds = 600;
    private int frameSelectionTimerSeconds = 30;
    private int mainSessionTimerSeconds = 180;
    private int mainExtensionTimerSeconds = 120;
    private int effectTimerSeconds = 30;
    private int resultTimerSeconds = 60;
    private boolean rememberLogin = true;
    private boolean showCancelSession = true;
    private String frameName = "Default Frame";
    private String selectedFrameId = "standard_vertical";
    private boolean mirrorPhoto = true;
    private PrintPaperSize printPaperSize = PrintPaperSize.FOUR_BY_SIX;
    private int printDpi = 300;
    private double customPaperWidthInches = 4.0;
    private double customPaperHeightInches = 6.0;
    private String printerName = "";
    private boolean autoPrint = false;
    private boolean viewPrintButton = true;
    private boolean livePhotoEnabled = false;
    private int livePhotoDurationSeconds = 3;
    private PhotoEffectSettings photoEffectSettings = new PhotoEffectSettings();

    public String getApiServer() { return apiServer; }
    public void setApiServer(String value) { apiServer = value == null || value.isBlank() ? "https://phomoria.com/sub" : value.trim(); }
    public String getCameraName() { return cameraName; }
    public void setCameraName(String value) { cameraName = value == null ? "" : value; }
    public int getPhotoSlotCount() { return Math.max(1, Math.min(12, photoSlotCount)); }
    public void setPhotoSlotCount(int value) { photoSlotCount = Math.max(1, Math.min(12, value)); }
    public int getPrice() { return Math.max(0, price); }
    public void setPrice(int value) { price = Math.max(0, value); }
    public int getCaptureCountdownSeconds() { return Math.max(1, Math.min(30, captureCountdownSeconds)); }
    public void setCaptureCountdownSeconds(int value) { captureCountdownSeconds = Math.max(1, Math.min(30, value)); }
    public int getPhotoPreviewSeconds() { return Math.max(1, Math.min(60, photoPreviewSeconds)); }
    public void setPhotoPreviewSeconds(int value) { photoPreviewSeconds = Math.max(1, Math.min(60, value)); }
    public int getFinalReviewSeconds() { return Math.max(1, Math.min(120, finalReviewSeconds)); }
    public void setFinalReviewSeconds(int value) { finalReviewSeconds = Math.max(1, Math.min(120, value)); }
    public int getQrisTimerSeconds() { return clampTimer(qrisTimerSeconds); }
    public void setQrisTimerSeconds(int value) { qrisTimerSeconds = clampTimer(value); }
    public int getFrameSelectionTimerSeconds() { return clampTimer(frameSelectionTimerSeconds); }
    public void setFrameSelectionTimerSeconds(int value) { frameSelectionTimerSeconds = clampTimer(value); }
    public int getMainSessionTimerSeconds() { return clampTimer(mainSessionTimerSeconds); }
    public void setMainSessionTimerSeconds(int value) { mainSessionTimerSeconds = clampTimer(value); }
    public int getMainExtensionTimerSeconds() { return clampTimer(mainExtensionTimerSeconds); }
    public void setMainExtensionTimerSeconds(int value) { mainExtensionTimerSeconds = clampTimer(value); }
    public int getEffectTimerSeconds() { return clampTimer(effectTimerSeconds); }
    public void setEffectTimerSeconds(int value) { effectTimerSeconds = clampTimer(value); }
    public int getResultTimerSeconds() { return clampTimer(resultTimerSeconds); }
    public void setResultTimerSeconds(int value) { resultTimerSeconds = clampTimer(value); }
    private int clampTimer(int value) { return Math.max(5, Math.min(3600, value)); }
    public boolean isRememberLogin() { return rememberLogin; }
    public void setRememberLogin(boolean value) { rememberLogin = value; }
    public boolean isShowCancelSession() { return showCancelSession; }
    public void setShowCancelSession(boolean value) { showCancelSession = value; }
    public String getFrameName() { return frameName; }
    public void setFrameName(String value) { frameName = value == null ? "Default Frame" : value; }
    public String getSelectedFrameId() { return selectedFrameId; }
    public void setSelectedFrameId(String value) { selectedFrameId = value == null || value.isBlank() ? "standard_vertical" : value; }
    public boolean isMirrorPhoto() { return mirrorPhoto; }
    public void setMirrorPhoto(boolean value) { mirrorPhoto = value; }
    public PrintPaperSize getPrintPaperSize() { if (printPaperSize == null) printPaperSize = PrintPaperSize.FOUR_BY_SIX; return printPaperSize; }
    public void setPrintPaperSize(PrintPaperSize value) { printPaperSize = value == null ? PrintPaperSize.FOUR_BY_SIX : value; }
    public int getPrintDpi() { return Math.max(72, Math.min(600, printDpi)); }
    public void setPrintDpi(int value) { printDpi = Math.max(72, Math.min(600, value)); }
    public double getCustomPaperWidthInches() { return customPaperWidthInches; }
    public void setCustomPaperWidthInches(double value) { customPaperWidthInches = clampPaperDimension(value); }
    public double getCustomPaperHeightInches() { return customPaperHeightInches; }
    public void setCustomPaperHeightInches(double value) { customPaperHeightInches = clampPaperDimension(value); }
    private double clampPaperDimension(double value) { return Math.max(0.5, Math.min(30.0, value)); }
    public String getPrinterName() { return printerName == null ? "" : printerName; }
    public void setPrinterName(String value) { printerName = value == null ? "" : value.trim(); }
    public boolean isAutoPrint() { return autoPrint; }
    public void setAutoPrint(boolean value) { autoPrint = value; }
    public boolean isViewPrintButton() { return viewPrintButton; }
    public void setViewPrintButton(boolean value) { viewPrintButton = value; }
    public boolean isLivePhotoEnabled() { return livePhotoEnabled; }
    public void setLivePhotoEnabled(boolean value) { livePhotoEnabled = value; }
    public int getLivePhotoDurationSeconds() { return Math.max(1, Math.min(10, livePhotoDurationSeconds)); }
    public void setLivePhotoDurationSeconds(int value) { livePhotoDurationSeconds = Math.max(1, Math.min(10, value)); }
    public PhotoEffectSettings getPhotoEffectSettings() { if (photoEffectSettings == null) photoEffectSettings = new PhotoEffectSettings(); return photoEffectSettings; }
    public void setPhotoEffectSettings(PhotoEffectSettings value) { photoEffectSettings = value == null ? new PhotoEffectSettings() : value; }
}
