package com.example.nwipe_android;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileItem {
    public enum ItemType {
        FILE, DIRECTORY, UNKNOWN
    }

    private File file;
    private String name;
    private long size;
    private ItemType type;
    private boolean canRead;
    private boolean canWrite;
    private boolean canDelete;
    private long lastModified;

    public FileItem(File file) {
        this.file = file;
        this.name = file.getName();
        this.size = file.length();
        this.lastModified = file.lastModified();
        
        // Determine type
        if (file.isDirectory()) {
            this.type = ItemType.DIRECTORY;
            // For directories, size represents number of items
            try {
                File[] children = file.listFiles();
                this.size = children != null ? children.length : 0;
            } catch (Exception e) {
                this.size = 0;
            }
        } else if (file.isFile()) {
            this.type = ItemType.FILE;
        } else {
            this.type = ItemType.UNKNOWN;
        }
        
        // Check permissions
        this.canRead = file.canRead();
        this.canWrite = file.canWrite();
        this.canDelete = file.canWrite() && file.getParentFile() != null && file.getParentFile().canWrite();
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public ItemType getType() {
        return type;
    }

    public boolean canRead() {
        return canRead;
    }

    public boolean canWrite() {
        return canWrite;
    }

    public boolean canDelete() {
        return canDelete;
    }

    public String getFormattedSize() {
        if (type == ItemType.DIRECTORY) {
            return size == 1 ? "1 item" : size + " items";
        } else {
            return formatBytes(size);
        }
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(lastModified));
    }

    public String getFileExtension() {
        if (type != ItemType.FILE) {
            return "";
        }
        
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0 && lastDot < name.length() - 1) {
            return name.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    public String getDisplayInfo() {
        StringBuilder info = new StringBuilder();
        info.append(getFormattedSize());
        
        if (type == ItemType.FILE) {
            String ext = getFileExtension();
            if (!ext.isEmpty()) {
                info.append(" • ").append(ext.toUpperCase());
            }
        }
        
        info.append(" • ").append(getFormattedDate());
        
        if (!canRead) {
            info.append(" • No read access");
        } else if (!canWrite) {
            info.append(" • Read only");
        }
        
        return info.toString();
    }

    public boolean delete() {
        if (!canDelete) {
            return false;
        }
        
        try {
            if (type == ItemType.DIRECTORY) {
                return deleteDirectory(file);
            } else {
                return file.delete();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean deleteDirectory(File dir) {
        try {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    FileItem childItem = new FileItem(child);
                    if (!childItem.delete()) {
                        return false;
                    }
                }
            }
            return dir.delete();
        } catch (Exception e) {
            return false;
        }
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

    public boolean isSafeToDelete() {
        // Only allow deletion of files in app directories
        String path = file.getAbsolutePath();
        return canDelete && (
            path.contains("/Android/data/") ||
            path.contains("/cache/") ||
            path.contains("/files/") ||
            name.startsWith("nwipe_") ||
            name.startsWith("test_")
        );
    }
}