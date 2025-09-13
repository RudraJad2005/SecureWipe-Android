package com.example.nwipe_android;

import android.os.Build;
import android.os.Environment;
import java.io.File;

public class SystemFileItem extends FileItem {
    private boolean isSystemFile;
    private boolean isHidden;
    private String mimeType;

    public SystemFileItem(File file) {
        super(file);
        this.isSystemFile = determineIfSystemFile(file);
        this.isHidden = file.isHidden() || file.getName().startsWith(".");
        this.mimeType = determineMimeType(file);
    }

    private boolean determineIfSystemFile(File file) {
        String path = file.getAbsolutePath();
        
        // System directories that should not be deleted
        String[] systemPaths = {
            "/system",
            "/proc",
            "/dev",
            "/sys",
            "/vendor",
            "/boot",
            "/recovery",
            "/cache/recovery",
            "/data/system",
            "/data/dalvik-cache",
            "/data/app-lib",
            "/data/tombstones"
        };
        
        for (String systemPath : systemPaths) {
            if (path.startsWith(systemPath)) {
                return true;
            }
        }
        
        // Android directory in external storage (contains app data)
        if (path.contains("/Android/data/") && !path.contains(getOwnPackagePath())) {
            return true;
        }
        
        return false;
    }

    private String getOwnPackagePath() {
        // This would be the app's own package directory
        return "com.example.nwipe_android";
    }

    private String determineMimeType(File file) {
        if (getType() == ItemType.DIRECTORY) {
            return "inode/directory";
        }
        
        String extension = getFileExtension().toLowerCase();
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "mp3":
                return "audio/mpeg";
            case "mp4":
                return "video/mp4";
            case "pdf":
                return "application/pdf";
            case "txt":
                return "text/plain";
            case "zip":
                return "application/zip";
            case "apk":
                return "application/vnd.android.package-archive";
            default:
                return "application/octet-stream";
        }
    }

    @Override
    public boolean isSafeToDelete() {
        // Don't allow deletion of system files
        if (isSystemFile) {
            return false;
        }
        
        // Don't allow deletion of critical Android directories
        String path = getFile().getAbsolutePath();
        String[] criticalPaths = {
            "/Android/data",
            "/Android/obb",
            "/.android_secure"
        };
        
        for (String criticalPath : criticalPaths) {
            if (path.endsWith(criticalPath)) {
                return false;
            }
        }
        
        // Check if we have write permission to parent directory
        File parent = getFile().getParentFile();
        if (parent == null || !parent.canWrite()) {
            return false;
        }
        
        // For Android 11+, check if we have MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // Without MANAGE_EXTERNAL_STORAGE, we can only delete files in certain locations
                return isInAllowedLocation(path);
            }
        }
        
        return canDelete();
    }

    private boolean isInAllowedLocation(String path) {
        // Locations where files can be deleted without MANAGE_EXTERNAL_STORAGE
        String[] allowedPaths = {
            "/Download",
            "/Pictures",
            "/Documents",
            "/Music",
            "/Movies",
            "/DCIM"
        };
        
        for (String allowedPath : allowedPaths) {
            if (path.contains(allowedPath)) {
                return true;
            }
        }
        
        return false;
    }

    public boolean isSystemFile() {
        return isSystemFile;
    }

    public boolean isHidden() {
        return isHidden;
    }

    public String getMimeType() {
        return mimeType;
    }

    @Override
    public String getDisplayInfo() {
        StringBuilder info = new StringBuilder();
        info.append(getFormattedSize());
        
        if (getType() == ItemType.FILE) {
            String ext = getFileExtension();
            if (!ext.isEmpty()) {
                info.append(" • ").append(ext.toUpperCase());
            }
        }
        
        info.append(" • ").append(getFormattedDate());
        
        if (isHidden) {
            info.append(" • Hidden");
        }
        
        if (isSystemFile) {
            info.append(" • System");
        }
        
        if (!canRead()) {
            info.append(" • No read access");
        } else if (!canWrite()) {
            info.append(" • Read only");
        }
        
        return info.toString();
    }

    public String getSecurityInfo() {
        StringBuilder info = new StringBuilder();
        
        if (isSystemFile) {
            info.append("⚠️ System file - deletion not recommended\n");
        }
        
        if (isHidden) {
            info.append("👁️ Hidden file\n");
        }
        
        if (!isSafeToDelete()) {
            info.append("🔒 Protected - cannot be deleted\n");
        }
        
        return info.toString();
    }

    public boolean requiresSpecialPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return !Environment.isExternalStorageManager() && !isInAllowedLocation(getFile().getAbsolutePath());
        }
        return false;
    }
}