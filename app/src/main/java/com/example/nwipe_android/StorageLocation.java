package com.example.nwipe_android;

import java.io.File;

public class StorageLocation {
    public enum StorageType {
        INTERNAL, EXTERNAL, CACHE
    }

    public File directory;
    public long availableBytes;
    public String displayName;
    public boolean accessible;
    public StorageType type;

    public StorageLocation(File directory, long availableBytes, String displayName, boolean accessible, StorageType type) {
        this.directory = directory;
        this.availableBytes = availableBytes;
        this.displayName = displayName;
        this.accessible = accessible;
        this.type = type;
    }

    public boolean isUsable() {
        return accessible && directory != null && directory.exists() && directory.canWrite();
    }

    public String getFormattedSize() {
        if (availableBytes < 1024) {
            return availableBytes + " B";
        } else if (availableBytes < 1024 * 1024) {
            return String.format("%.1f KB", availableBytes / 1024.0);
        } else if (availableBytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", availableBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", availableBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}