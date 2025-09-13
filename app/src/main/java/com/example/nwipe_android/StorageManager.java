package com.example.nwipe_android;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StorageManager {

    public static StorageInfo getStorageInfo(Context context) {
        StorageInfo info = new StorageInfo();
        
        try {
            // Check permissions first
            info.hasPermissions = hasStoragePermissions(context);
            
            if (!info.hasPermissions) {
                info.errorMessage = "Storage permissions not granted. Please grant storage access to continue.";
                return info;
            }

            // Get wipeable directories
            List<StorageLocation> locations = getWipeableDirectories(context);
            info.locations = locations;

            // Use primary external storage for main calculation to avoid duplication
            long totalBytes = 0;
            long availableBytes = 0;
            boolean foundPrimaryStorage = false;

            // First try to get external storage stats (most accurate)
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                try {
                    File externalStorage = Environment.getExternalStorageDirectory();
                    StatFs stat = new StatFs(externalStorage.getPath());
                    long blockSize = stat.getBlockSizeLong();
                    long totalBlocks = stat.getBlockCountLong();
                    long availableBlocks = stat.getAvailableBlocksLong();
                    
                    totalBytes = totalBlocks * blockSize;
                    availableBytes = availableBlocks * blockSize;
                    foundPrimaryStorage = true;
                } catch (Exception e) {
                    Log.w("StorageManager", "Failed to get external storage stats: " + e.getMessage());
                }
            }

            // Fallback to internal storage if external not available
            if (!foundPrimaryStorage) {
                try {
                    File dataDir = Environment.getDataDirectory();
                    StatFs stat = new StatFs(dataDir.getPath());
                    long blockSize = stat.getBlockSizeLong();
                    long totalBlocks = stat.getBlockCountLong();
                    long availableBlocks = stat.getAvailableBlocksLong();
                    
                    totalBytes = totalBlocks * blockSize;
                    availableBytes = availableBlocks * blockSize;
                } catch (Exception e) {
                    Log.e("StorageManager", "Failed to get internal storage stats: " + e.getMessage());
                }
            }

            info.totalBytes = totalBytes;
            info.availableBytes = availableBytes;
            info.usedBytes = totalBytes - availableBytes;

            if (totalBytes == 0) {
                info.errorMessage = "No accessible storage locations found. Check permissions and storage availability.";
            }

        } catch (Exception e) {
            info.errorMessage = "Error accessing storage: " + e.getMessage();
        }

        return info;
    }

    public static List<StorageLocation> getWipeableDirectories(Context context) {
        List<StorageLocation> locations = new ArrayList<>();

        try {
            // External storage (primary)
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File externalStorage = Environment.getExternalStorageDirectory();
                if (externalStorage != null && externalStorage.exists()) {
                    StatFs stat = new StatFs(externalStorage.getPath());
                    long availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
                    locations.add(new StorageLocation(
                        externalStorage,
                        availableBytes,
                        "External Storage",
                        externalStorage.canWrite(),
                        StorageLocation.StorageType.EXTERNAL
                    ));
                }
            }

            // Add removable/secondary roots discovered via SystemStorageManager (e.g., SD card, USB OTG)
            try {
                java.util.List<SystemStorageManager.StorageLocation> roots = SystemStorageManager.getSystemStorageRoots(context);
                for (SystemStorageManager.StorageLocation root : roots) {
                    if (root == null || root.path == null) continue;
                    File dir = new File(root.path);
                    if (!dir.exists() || !dir.canWrite()) continue;
                    // Avoid duplicating the primary external path
                    boolean duplicate = false;
                    for (StorageLocation existing : locations) {
                        if (existing.directory != null && existing.directory.getAbsolutePath().equals(dir.getAbsolutePath())) {
                            duplicate = true; break;
                        }
                    }
                    if (duplicate) continue;

                    StatFs stat = new StatFs(dir.getPath());
                    long availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
                    String name = root.displayName != null ? root.displayName : "External Storage";
                    locations.add(new StorageLocation(
                        dir,
                        availableBytes,
                        name,
                        true,
                        StorageLocation.StorageType.EXTERNAL
                    ));
                }
            } catch (Exception ignored) {}

            // App external files directory
            File appExternalDir = context.getExternalFilesDir(null);
            if (appExternalDir != null && appExternalDir.exists()) {
                StatFs stat = new StatFs(appExternalDir.getPath());
                long availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
                locations.add(new StorageLocation(
                    appExternalDir,
                    availableBytes,
                    "App External Files",
                    appExternalDir.canWrite(),
                    StorageLocation.StorageType.EXTERNAL
                ));
            }

            // Cache directory
            File cacheDir = context.getCacheDir();
            if (cacheDir != null && cacheDir.exists()) {
                StatFs stat = new StatFs(cacheDir.getPath());
                long availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
                locations.add(new StorageLocation(
                    cacheDir,
                    availableBytes,
                    "Cache Directory",
                    cacheDir.canWrite(),
                    StorageLocation.StorageType.CACHE
                ));
            }

            // Internal files directory (fallback)
            File filesDir = context.getFilesDir();
            if (filesDir != null && filesDir.exists()) {
                StatFs stat = new StatFs(filesDir.getPath());
                long availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
                locations.add(new StorageLocation(
                    filesDir,
                    availableBytes,
                    "Internal Files",
                    filesDir.canWrite(),
                    StorageLocation.StorageType.INTERNAL
                ));
            }

        } catch (Exception e) {
            // If all else fails, add internal files as last resort
            File filesDir = context.getFilesDir();
            if (filesDir != null) {
                locations.add(new StorageLocation(
                    filesDir,
                    0,
                    "Internal Files (Limited)",
                    false,
                    StorageLocation.StorageType.INTERNAL
                ));
            }
        }

        return locations;
    }

    public static boolean hasStoragePermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires MANAGE_EXTERNAL_STORAGE
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6+ requires runtime permissions
            return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                   == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) 
                   == PackageManager.PERMISSION_GRANTED;
        } else {
            // Pre-Android 6, permissions are granted at install time
            return true;
        }
    }

    public static void requestStoragePermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - need to request MANAGE_EXTERNAL_STORAGE
            // This requires special handling through Settings
            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            android.net.Uri uri = android.net.Uri.fromParts("package", activity.getPackageName(), null);
            intent.setData(uri);
            activity.startActivity(intent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6+ - request runtime permissions
            ActivityCompat.requestPermissions(activity, 
                new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }, 
                PermissionHandler.STORAGE_PERMISSION_REQUEST);
        }
    }
}