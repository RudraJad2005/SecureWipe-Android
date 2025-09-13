package com.example.nwipe_android;

import java.util.List;

public class StorageInfo {
    public long totalBytes;
    public long availableBytes;
    public long usedBytes;
    public List<StorageLocation> locations;
    public boolean hasPermissions;
    public String errorMessage;

    public StorageInfo() {
        this.totalBytes = 0;
        this.availableBytes = 0;
        this.usedBytes = 0;
        this.hasPermissions = false;
        this.errorMessage = "";
    }

    public StorageInfo(long totalBytes, long availableBytes, boolean hasPermissions) {
        this.totalBytes = totalBytes;
        this.availableBytes = availableBytes;
        this.usedBytes = totalBytes - availableBytes;
        this.hasPermissions = hasPermissions;
        this.errorMessage = "";
    }

    public boolean isValid() {
        return errorMessage.isEmpty() && totalBytes > 0;
    }

    public String getFormattedAvailableSize() {
        return formatBytes(availableBytes);
    }

    public String getFormattedTotalSize() {
        return formatBytes(totalBytes);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}