package com.example.nwipe_android;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class SystemStorageManager {
    private static final String TAG = "SystemStorageManager";

    public static class StorageLocation {
        public String displayName;
        public String path;
        public long totalSpace;
        public long freeSpace;
        public boolean isRemovable;
        public boolean isAccessible;
        public boolean isPrimary;

        public StorageLocation(String displayName, String path, boolean isRemovable, boolean isPrimary) {
            this.displayName = displayName;
            this.path = path;
            this.isRemovable = isRemovable;
            this.isPrimary = isPrimary;
            
            File file = new File(path);
            this.isAccessible = file.exists() && file.canRead();
            
            if (isAccessible) {
                this.totalSpace = file.getTotalSpace();
                this.freeSpace = file.getFreeSpace();
            } else {
                this.totalSpace = 0;
                this.freeSpace = 0;
            }
        }

        public String getFormattedTotalSpace() {
            return formatBytes(totalSpace);
        }

        public String getFormattedFreeSpace() {
            return formatBytes(freeSpace);
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

    public static class CommonDirectory {
        public String displayName;
        public String path;
        public String icon;

        public CommonDirectory(String displayName, String path, String icon) {
            this.displayName = displayName;
            this.path = path;
            this.icon = icon;
        }
    }

    public static List<StorageLocation> getSystemStorageRoots(Context context) {
        List<StorageLocation> storageLocations = new ArrayList<>();

        try {
            // Primary external storage (usually internal storage)
            File primaryExternal = Environment.getExternalStorageDirectory();
            if (primaryExternal != null) {
                storageLocations.add(new StorageLocation(
                    "Internal Storage",
                    primaryExternal.getAbsolutePath(),
                    false,
                    true
                ));
            }

            // Get all storage volumes using StorageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                List<StorageVolume> volumes = storageManager.getStorageVolumes();
                
                for (StorageVolume volume : volumes) {
                    try {
                        String path = getStorageVolumePath(volume);
                        if (path != null && !path.equals(primaryExternal.getAbsolutePath())) {
                            String displayName = volume.getDescription(context);
                            if (displayName == null || displayName.isEmpty()) {
                                displayName = volume.isRemovable() ? "SD Card" : "External Storage";
                            }
                            
                            storageLocations.add(new StorageLocation(
                                displayName,
                                path,
                                volume.isRemovable(),
                                volume.isPrimary()
                            ));
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error processing storage volume: " + e.getMessage());
                    }
                }
            } else {
                // Fallback for older Android versions
                addLegacyStorageLocations(storageLocations, primaryExternal);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting system storage roots: " + e.getMessage());
            
            // Fallback to basic external storage
            File fallback = Environment.getExternalStorageDirectory();
            if (fallback != null) {
                storageLocations.add(new StorageLocation(
                    "Storage",
                    fallback.getAbsolutePath(),
                    false,
                    true
                ));
            }
        }

        return storageLocations;
    }

    private static String getStorageVolumePath(StorageVolume volume) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                File directory = volume.getDirectory();
                return directory != null ? directory.getAbsolutePath() : null;
            } else {
                // Use reflection for older versions
                Method getPathMethod = StorageVolume.class.getMethod("getPath");
                return (String) getPathMethod.invoke(volume);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not get storage volume path: " + e.getMessage());
            return null;
        }
    }

    private static void addLegacyStorageLocations(List<StorageLocation> storageLocations, File primaryExternal) {
        // Try common SD card mount points for older Android versions
        String[] possiblePaths = {
            "/storage/sdcard1",
            "/storage/extSdCard",
            "/storage/external_SD",
            "/mnt/external_sd",
            "/mnt/sdcard/external_sd"
        };

        for (String path : possiblePaths) {
            File sdCard = new File(path);
            if (sdCard.exists() && sdCard.canRead() && !path.equals(primaryExternal.getAbsolutePath())) {
                storageLocations.add(new StorageLocation(
                    "SD Card",
                    path,
                    true,
                    false
                ));
                break; // Only add the first accessible SD card path
            }
        }
    }

    public static List<CommonDirectory> getCommonDirectories(Context context) {
        List<CommonDirectory> commonDirs = new ArrayList<>();

        try {
            // Get primary external storage directory
            File externalStorage = Environment.getExternalStorageDirectory();
            if (externalStorage != null && externalStorage.exists()) {
                String basePath = externalStorage.getAbsolutePath();

                // Add common directories
                addCommonDirectory(commonDirs, "Downloads", basePath + "/Download", "📥");
                addCommonDirectory(commonDirs, "Pictures", basePath + "/Pictures", "🖼️");
                addCommonDirectory(commonDirs, "Camera", basePath + "/DCIM/Camera", "📷");
                addCommonDirectory(commonDirs, "Documents", basePath + "/Documents", "📄");
                addCommonDirectory(commonDirs, "Music", basePath + "/Music", "🎵");
                addCommonDirectory(commonDirs, "Videos", basePath + "/Movies", "🎬");
                addCommonDirectory(commonDirs, "WhatsApp", basePath + "/WhatsApp", "💬");
                addCommonDirectory(commonDirs, "Bluetooth", basePath + "/bluetooth", "📶");

                // Also try standard Environment directories
                addEnvironmentDirectory(commonDirs, "Downloads", Environment.DIRECTORY_DOWNLOADS, "📥");
                addEnvironmentDirectory(commonDirs, "Pictures", Environment.DIRECTORY_PICTURES, "🖼️");
                addEnvironmentDirectory(commonDirs, "Documents", Environment.DIRECTORY_DOCUMENTS, "📄");
                addEnvironmentDirectory(commonDirs, "Music", Environment.DIRECTORY_MUSIC, "🎵");
                addEnvironmentDirectory(commonDirs, "Movies", Environment.DIRECTORY_MOVIES, "🎬");
                addEnvironmentDirectory(commonDirs, "DCIM", Environment.DIRECTORY_DCIM, "📷");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting common directories: " + e.getMessage());
        }

        return commonDirs;
    }

    private static void addCommonDirectory(List<CommonDirectory> commonDirs, String name, String path, String icon) {
        File dir = new File(path);
        if (dir.exists() && dir.canRead()) {
            // Check if we already have this directory (avoid duplicates)
            for (CommonDirectory existing : commonDirs) {
                if (existing.path.equals(path)) {
                    return;
                }
            }
            commonDirs.add(new CommonDirectory(name, path, icon));
        }
    }

    private static void addEnvironmentDirectory(List<CommonDirectory> commonDirs, String name, String type, String icon) {
        try {
            File dir = Environment.getExternalStoragePublicDirectory(type);
            if (dir != null && dir.exists() && dir.canRead()) {
                String path = dir.getAbsolutePath();
                
                // Check if we already have this directory (avoid duplicates)
                for (CommonDirectory existing : commonDirs) {
                    if (existing.path.equals(path)) {
                        return;
                    }
                }
                commonDirs.add(new CommonDirectory(name, path, icon));
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not add environment directory " + type + ": " + e.getMessage());
        }
    }

    public static boolean isSystemStorageAccessible(Context context) {
        try {
            // Check if we have MANAGE_EXTERNAL_STORAGE permission (Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return Environment.isExternalStorageManager();
            } else {
                // For older versions, check basic external storage permissions
                return PermissionHandler.checkPermissions(context);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking system storage accessibility: " + e.getMessage());
            return false;
        }
    }

    public static String getStorageAccessibilityMessage(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                return "Full storage access granted";
            } else {
                return "All Files Access permission required for full storage browsing";
            }
        } else {
            if (PermissionHandler.checkPermissions(context)) {
                return "Storage access granted";
            } else {
                return "Storage permissions required";
            }
        }
    }

    public static File getRootDirectory() {
        // Return the root of external storage
        return Environment.getExternalStorageDirectory();
    }

    public static boolean isPathAccessible(String path) {
        try {
            File file = new File(path);
            return file.exists() && file.canRead();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isPathWritable(String path) {
        try {
            File file = new File(path);
            return file.exists() && file.canWrite();
        } catch (Exception e) {
            return false;
        }
    }
}