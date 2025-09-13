package com.example.nwipe_android;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.app.usage.StorageStatsManager;
import java.util.UUID;
import android.os.storage.StorageVolume;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.List;

/**
 * Improved storage manager that provides accurate storage information
 * and handles permissions properly across different Android versions
 */
public class AccurateStorageManager {
    
    private static final String TAG = "AccurateStorageManager";
    
    public static class AccurateStorageInfo {
        public long totalBytes;
        public long usedBytes;
        public long availableBytes;
        public int usedPercentage;
        public String totalFormatted;
        public String usedFormatted;
        public String availableFormatted;
        public boolean isAccessible;
        public boolean hasPermissions;
        public String errorMessage;
        public String storagePath;
        
        // Enhanced accuracy fields
        public long rawCapacityBytes;
        public String rawCapacityFormatted;
        public String rawCapacityFormattedDecimal;
        public double efficiencyPercentage;
        
        public AccurateStorageInfo() {
            this.isAccessible = false;
            this.hasPermissions = false;
            this.errorMessage = "";
            this.storagePath = "";
        }
        
        public boolean isValid() {
            return isAccessible && totalBytes > 0;
        }
    }
    
    /**
     * Get accurate storage information using multiple fallback methods
     */
    public static AccurateStorageInfo getAccurateStorageInfo(Context context) {
        AccurateStorageInfo info = new AccurateStorageInfo();
        
        try {
            // Check permissions first
            info.hasPermissions = hasStoragePermissions(context);
            
            // Try multiple methods to get storage info, starting with most accurate
            boolean success = false;
            
            // Method 0: StorageStatsManager (API 26+) for the primary external storage UUID
            if (!success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                success = tryStorageStatsManager(context, info);
                if (success) {
                    Log.d(TAG, "Got storage info from StorageStatsManager");
                }
            }

            // Method 1: Try external storage (most accurate for user data)
            if (info.hasPermissions && !success) {
                success = tryExternalStorage(info);
                if (success) {
                    Log.d(TAG, "Got storage info from external storage");
                }
            }
            
            // Method 2: Try primary storage volume (Android 7+)
            if (!success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                success = tryPrimaryStorageVolume(context, info);
                if (success) {
                    Log.d(TAG, "Got storage info from primary storage volume");
                }
            }
            
            // Method 3: Try internal storage (always accessible)
            if (!success) {
                success = tryInternalStorage(info);
                if (success) {
                    Log.d(TAG, "Got storage info from internal storage");
                }
            }
            
            // Method 4: Try data directory (fallback)
            if (!success) {
                success = tryDataDirectory(info);
                if (success) {
                    Log.d(TAG, "Got storage info from data directory");
                }
            }
            
            if (success) {
                // Calculate derived values
                info.usedBytes = info.totalBytes - info.availableBytes;
                if (info.totalBytes > 0) {
                    info.usedPercentage = (int) ((info.usedBytes * 100) / info.totalBytes);
                } else {
                    info.usedPercentage = 0;
                }
                
                // Estimate raw capacity (manufacturer specification)
                info.rawCapacityBytes = estimateRawCapacity(info.totalBytes);
                
                // Calculate efficiency (formatted vs raw capacity)
                if (info.rawCapacityBytes > 0) {
                    info.efficiencyPercentage = (double) info.totalBytes / info.rawCapacityBytes * 100.0;
                } else {
                    info.efficiencyPercentage = 100.0;
                }
                
                // Format sizes (binary - actual system values)
                info.totalFormatted = formatBytes(info.totalBytes);
                info.usedFormatted = formatBytes(info.usedBytes);
                info.availableFormatted = formatBytes(info.availableBytes);
                
                // Format raw capacity (both binary and decimal)
                info.rawCapacityFormatted = formatBytes(info.rawCapacityBytes);
                info.rawCapacityFormattedDecimal = formatBytesDecimal(info.rawCapacityBytes);
                
                info.isAccessible = true;
                
                Log.d(TAG, String.format("Storage info: %s formatted (%s raw), %s used (%d%%), %s available, %.1f%% efficiency", 
                    info.totalFormatted, info.rawCapacityFormattedDecimal, info.usedFormatted, 
                    info.usedPercentage, info.availableFormatted, info.efficiencyPercentage));
                
            } else {
                if (!info.hasPermissions) {
                    info.errorMessage = "Storage permissions required - tap to grant access";
                } else {
                    info.errorMessage = "Unable to access storage information";
                }
                Log.w(TAG, "Failed to get storage info: " + info.errorMessage);
            }
            
        } catch (Exception e) {
            info.errorMessage = "Error accessing storage: " + e.getMessage();
            Log.e(TAG, "Exception getting storage info", e);
        }
        
        return info;
    }

    private static boolean tryStorageStatsManager(Context context, AccurateStorageInfo info) {
        try {
            StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            StorageStatsManager ssm = (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
            if (sm == null || ssm == null) return false;

            File path = Environment.getExternalStorageDirectory();
            if (path == null || !path.exists()) {
                path = Environment.getDataDirectory();
            }

            UUID uuid;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                uuid = sm.getUuidForPath(path);
            } else {
                uuid = StorageManager.UUID_DEFAULT;
            }

            long total = ssm.getTotalBytes(uuid);
            long free = ssm.getFreeBytes(uuid);
            if (total > 0) {
                info.totalBytes = total;
                info.availableBytes = free;
                info.storagePath = path.getPath();
                return true;
            }
        } catch (SecurityException se) {
            Log.w(TAG, "StorageStatsManager permission issue: " + se.getMessage());
        } catch (Exception e) {
            Log.w(TAG, "Failed to get stats via StorageStatsManager: " + e.getMessage());
        }
        return false;
    }
    
    private static boolean tryExternalStorage(AccurateStorageInfo info) {
        try {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File externalStorage = Environment.getExternalStorageDirectory();
                if (externalStorage != null && externalStorage.exists()) {
                    StatFs stat = new StatFs(externalStorage.getPath());
                    info.totalBytes = stat.getBlockCountLong() * stat.getBlockSizeLong();
                    info.availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
                    info.storagePath = externalStorage.getPath();
                    return info.totalBytes > 0;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get external storage info: " + e.getMessage());
        }
        return false;
    }
    
    private static boolean tryPrimaryStorageVolume(Context context, AccurateStorageInfo info) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                if (storageManager != null) {
                    List<StorageVolume> volumes = storageManager.getStorageVolumes();
                    for (StorageVolume volume : volumes) {
                        if (volume.isPrimary()) {
                            File directory = null;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                directory = volume.getDirectory();
                            }
                            
                            if (directory == null) {
                                // Fallback to external storage directory
                                directory = Environment.getExternalStorageDirectory();
                            }
                            
                            if (directory != null && directory.exists()) {
                                StatFs stat = new StatFs(directory.getPath());
                                info.totalBytes = stat.getBlockCountLong() * stat.getBlockSizeLong();
                                info.availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
                                info.storagePath = directory.getPath();
                                return info.totalBytes > 0;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get primary storage volume info: " + e.getMessage());
        }
        return false;
    }
    
    private static boolean tryInternalStorage(AccurateStorageInfo info) {
        try {
            File internalStorage = Environment.getDataDirectory();
            if (internalStorage != null && internalStorage.exists()) {
                StatFs stat = new StatFs(internalStorage.getPath());
                info.totalBytes = stat.getBlockCountLong() * stat.getBlockSizeLong();
                info.availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
                info.storagePath = internalStorage.getPath();
                return info.totalBytes > 0;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get internal storage info: " + e.getMessage());
        }
        return false;
    }
    
    private static boolean tryDataDirectory(AccurateStorageInfo info) {
        try {
            File rootDir = Environment.getRootDirectory();
            if (rootDir != null && rootDir.exists()) {
                StatFs stat = new StatFs(rootDir.getPath());
                info.totalBytes = stat.getBlockCountLong() * stat.getBlockSizeLong();
                info.availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
                info.storagePath = rootDir.getPath();
                return info.totalBytes > 0;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get data directory info: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Check if we have the necessary storage permissions
     */
    public static boolean hasStoragePermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - Check for MANAGE_EXTERNAL_STORAGE
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6+ - Check for READ_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(context, 
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Pre-Android 6, permissions are granted at install time
            return true;
        }
    }
    
    /**
     * Request storage permissions from the user
     */
    public static void requestStoragePermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - Request MANAGE_EXTERNAL_STORAGE
            try {
                android.content.Intent intent = new android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, PermissionHandler.MANAGE_EXTERNAL_STORAGE_REQUEST);
            } catch (Exception e) {
                // Fallback to general settings
                android.content.Intent intent = new android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                activity.startActivity(intent);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6+ - Request READ_EXTERNAL_STORAGE
            ActivityCompat.requestPermissions(activity, 
                new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 
                PermissionHandler.STORAGE_PERMISSION_REQUEST);
        }
    }
    
    /**
     * Estimate the raw capacity (manufacturer specification) from formatted capacity
     * This attempts to reverse-engineer what the original drive size was
     */
    private static long estimateRawCapacity(long formattedBytes) {
        // Common storage sizes in bytes (manufacturer specification using 1000^n)
        long[] commonSizes = {
            // Common phone storage sizes (decimal GB)
            32L * 1000 * 1000 * 1000,   // 32GB
            64L * 1000 * 1000 * 1000,   // 64GB
            128L * 1000 * 1000 * 1000,  // 128GB
            256L * 1000 * 1000 * 1000,  // 256GB
            512L * 1000 * 1000 * 1000,  // 512GB
            1000L * 1000 * 1000 * 1000, // 1TB
            2000L * 1000 * 1000 * 1000  // 2TB
        };
        
        // Find the closest match (within 15% tolerance)
        long bestMatch = 0;
        double bestTolerance = Double.MAX_VALUE;
        
        for (long rawSize : commonSizes) {
            // Expected formatted size (accounting for ~7-12% formatting overhead)
            long expectedFormatted = (long) (rawSize * 0.88); // Conservative estimate
            double tolerance = Math.abs((double)(formattedBytes - expectedFormatted) / expectedFormatted);
            
            if (tolerance < 0.15 && tolerance < bestTolerance) { // Within 15% tolerance
                bestMatch = rawSize;
                bestTolerance = tolerance;
            }
        }
        
        // If we found a good match, use it
        if (bestMatch > 0) {
            return bestMatch;
        }
        
        // Otherwise, estimate by adding ~12% overhead to formatted size
        return (long) (formattedBytes / 0.88);
    }
    
    /**
     * Format bytes to human readable format (binary - 1KB = 1024 bytes)
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        
        double gb = mb / 1024.0;
        if (gb < 1024) {
            return String.format("%.1f GB", gb);
        }
        
        double tb = gb / 1024.0;
        return String.format("%.1f TB", tb);
    }
    
    /**
     * Format bytes to human readable format (decimal - 1KB = 1000 bytes, like manufacturers)
     */
    private static String formatBytesDecimal(long bytes) {
        if (bytes < 1000) {
            return bytes + " B";
        }
        
        double kb = bytes / 1000.0;
        if (kb < 1000) {
            return String.format("%.1f KB", kb);
        }
        
        double mb = kb / 1000.0;
        if (mb < 1000) {
            return String.format("%.1f MB", mb);
        }
        
        double gb = mb / 1000.0;
        if (gb < 1000) {
            return String.format("%.0f GB", gb); // Round to nearest GB for manufacturer specs
        }
        
        double tb = gb / 1000.0;
        return String.format("%.1f TB", tb);
    }
    
    /**
     * Get a simple storage summary string
     */
    public static String getStorageSummary(Context context) {
        AccurateStorageInfo info = getAccurateStorageInfo(context);
        
        if (info.isValid()) {
            return String.format("Storage: %s / %s (%d%% used)", 
                info.usedFormatted, info.totalFormatted, info.usedPercentage);
        } else {
            return "Storage: " + info.errorMessage;
        }
    }
    
    /**
     * Get detailed storage explanation for user education
     */
    public static String getStorageExplanation(Context context) {
        AccurateStorageInfo info = getAccurateStorageInfo(context);
        
        if (info.isValid() && info.rawCapacityFormattedDecimal != null && 
            !info.rawCapacityFormattedDecimal.equals(info.totalFormatted)) {
            
            return String.format("Your device has %s of storage (manufacturer specification), " +
                "but only %s is available after formatting and system reserves. " +
                "This %.0f%% efficiency is normal due to:\n" +
                "• File system overhead (metadata, formatting)\n" +
                "• System reserved space (updates, recovery)\n" +
                "• Different calculation methods (1000 vs 1024 bytes per unit)",
                info.rawCapacityFormattedDecimal, info.totalFormatted, info.efficiencyPercentage);
        }
        
        return "Storage information calculated from available system data.";
    }
}