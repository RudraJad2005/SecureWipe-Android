package com.example.nwipe_android;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SystemFileBrowserManager {
    private static final String TAG = "SystemFileBrowserManager";

    public static class SystemBrowseResult {
        public boolean success;
        public String message;
        public List<SystemFileItem> items;
        public String currentPath;
        public SystemStorageManager.StorageLocation storageLocation;
        public boolean hasPermissionIssues;

        public SystemBrowseResult(boolean success, String message, List<SystemFileItem> items, 
                                String currentPath, SystemStorageManager.StorageLocation storageLocation) {
            this.success = success;
            this.message = message;
            this.items = items != null ? items : new ArrayList<>();
            this.currentPath = currentPath;
            this.storageLocation = storageLocation;
            this.hasPermissionIssues = false;
        }
    }

    public static class SystemDeleteResult {
        public boolean success;
        public String message;
        public SystemFileItem item;
        public boolean wasProtected;

        public SystemDeleteResult(boolean success, String message, SystemFileItem item) {
            this.success = success;
            this.message = message;
            this.item = item;
            this.wasProtected = false;
        }
    }

    public static SystemBrowseResult listSystemDirectory(File directory, Context context) {
        if (directory == null) {
            return new SystemBrowseResult(false, "Directory is null", null, "", null);
        }
        
        if (!directory.exists()) {
            return new SystemBrowseResult(false, "Directory does not exist", null, 
                directory.getAbsolutePath(), null);
        }
        
        if (!directory.isDirectory()) {
            return new SystemBrowseResult(false, "Not a directory", null, 
                directory.getAbsolutePath(), null);
        }
        
        // Check permissions
        if (!directory.canRead()) {
            SystemBrowseResult result = new SystemBrowseResult(false, "No read permission", null, 
                directory.getAbsolutePath(), null);
            result.hasPermissionIssues = true;
            return result;
        }
        
        try {
            File[] files = directory.listFiles();
            if (files == null) {
                SystemBrowseResult result = new SystemBrowseResult(false, "Cannot list directory contents", null, 
                    directory.getAbsolutePath(), null);
                result.hasPermissionIssues = true;
                return result;
            }
            
            List<SystemFileItem> items = new ArrayList<>();
            int hiddenCount = 0;
            int inaccessibleCount = 0;
            
            for (File file : files) {
                try {
                    SystemFileItem item = new SystemFileItem(file);
                    
                    // Skip hidden files unless we're showing them
                    if (item.isHidden()) {
                        hiddenCount++;
                        // For now, skip hidden files - could add option to show them later
                        continue;
                    }
                    
                    // Check if file is accessible
                    if (!file.canRead()) {
                        inaccessibleCount++;
                        continue;
                    }
                    
                    items.add(item);
                } catch (Exception e) {
                    Log.w(TAG, "Error creating SystemFileItem for " + file.getName() + ": " + e.getMessage());
                    inaccessibleCount++;
                }
            }
            
            // Sort items: directories first, then files, both alphabetically
            Collections.sort(items, new Comparator<SystemFileItem>() {
                @Override
                public int compare(SystemFileItem a, SystemFileItem b) {
                    // Directories first
                    if (a.getType() == FileItem.ItemType.DIRECTORY && b.getType() != FileItem.ItemType.DIRECTORY) {
                        return -1;
                    }
                    if (b.getType() == FileItem.ItemType.DIRECTORY && a.getType() != FileItem.ItemType.DIRECTORY) {
                        return 1;
                    }
                    // Then alphabetically (case insensitive)
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            
            // Create message with counts
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(String.format("Found %d items", items.size()));
            
            if (hiddenCount > 0) {
                messageBuilder.append(String.format(" (%d hidden)", hiddenCount));
            }
            
            if (inaccessibleCount > 0) {
                messageBuilder.append(String.format(" (%d inaccessible)", inaccessibleCount));
            }
            
            // Find storage location for this directory
            SystemStorageManager.StorageLocation storageLocation = findStorageLocationForPath(
                directory.getAbsolutePath(), context);
            
            SystemBrowseResult result = new SystemBrowseResult(true, messageBuilder.toString(), 
                items, directory.getAbsolutePath(), storageLocation);
            
            if (inaccessibleCount > 0) {
                result.hasPermissionIssues = true;
            }
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error listing system directory: " + e.getMessage());
            return new SystemBrowseResult(false, "Error listing directory: " + e.getMessage(), 
                null, directory.getAbsolutePath(), null);
        }
    }

    public static SystemBrowseResult listStorageRoots(Context context) {
        List<SystemStorageManager.StorageLocation> storageLocations = 
            SystemStorageManager.getSystemStorageRoots(context);
        
        List<SystemFileItem> items = new ArrayList<>();
        
        for (SystemStorageManager.StorageLocation location : storageLocations) {
            try {
                if (location.isAccessible) {
                    File rootFile = new File(location.path);
                    SystemFileItem item = new SystemFileItem(rootFile);
                    items.add(item);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error creating SystemFileItem for storage root " + location.displayName + ": " + e.getMessage());
            }
        }
        
        String message = String.format("Found %d accessible storage locations", items.size());
        return new SystemBrowseResult(true, message, items, "Storage Roots", null);
    }

    public static SystemBrowseResult listCommonDirectories(Context context) {
        List<SystemStorageManager.CommonDirectory> commonDirs = 
            SystemStorageManager.getCommonDirectories(context);
        
        List<SystemFileItem> items = new ArrayList<>();
        
        for (SystemStorageManager.CommonDirectory commonDir : commonDirs) {
            try {
                File dirFile = new File(commonDir.path);
                if (dirFile.exists() && dirFile.canRead()) {
                    SystemFileItem item = new SystemFileItem(dirFile);
                    items.add(item);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error creating SystemFileItem for common directory " + commonDir.displayName + ": " + e.getMessage());
            }
        }
        
        String message = String.format("Found %d common directories", items.size());
        return new SystemBrowseResult(true, message, items, "Common Directories", null);
    }

    public static SystemDeleteResult deleteSystemFile(SystemFileItem item, Context context) {
        if (item == null) {
            return new SystemDeleteResult(false, "Item is null", null);
        }
        
        if (!item.canDelete()) {
            SystemDeleteResult result = new SystemDeleteResult(false, 
                "No delete permission for " + item.getName(), item);
            result.wasProtected = true;
            return result;
        }
        
        if (!item.isSafeToDelete()) {
            SystemDeleteResult result = new SystemDeleteResult(false, 
                "Item is protected and cannot be deleted: " + item.getName(), item);
            result.wasProtected = true;
            return result;
        }
        
        // Check for special permission requirements
        if (item.requiresSpecialPermission()) {
            SystemDeleteResult result = new SystemDeleteResult(false, 
                "All Files Access permission required to delete: " + item.getName(), item);
            result.wasProtected = true;
            return result;
        }
        
        try {
            boolean deleted = item.delete();
            if (deleted) {
                String message = String.format("Successfully deleted %s", item.getName());
                Log.i(TAG, message);
                return new SystemDeleteResult(true, message, item);
            } else {
                String message = String.format("Failed to delete %s (file system error)", item.getName());
                return new SystemDeleteResult(false, message, item);
            }
        } catch (SecurityException e) {
            String message = String.format("Permission denied deleting %s: %s", item.getName(), e.getMessage());
            Log.e(TAG, message);
            SystemDeleteResult result = new SystemDeleteResult(false, message, item);
            result.wasProtected = true;
            return result;
        } catch (Exception e) {
            String message = String.format("Error deleting %s: %s", item.getName(), e.getMessage());
            Log.e(TAG, message);
            return new SystemDeleteResult(false, message, item);
        }
    }

    public static String getSystemDisplayPath(String fullPath, Context context) {
        if (fullPath == null || fullPath.isEmpty()) {
            return "Storage";
        }
        
        // Check if path is a storage root
        List<SystemStorageManager.StorageLocation> storageLocations = 
            SystemStorageManager.getSystemStorageRoots(context);
        
        for (SystemStorageManager.StorageLocation location : storageLocations) {
            if (fullPath.equals(location.path)) {
                return location.displayName;
            }
            
            if (fullPath.startsWith(location.path)) {
                String relativePath = fullPath.substring(location.path.length());
                if (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }
                
                if (relativePath.isEmpty()) {
                    return location.displayName;
                } else {
                    return location.displayName + "/" + relativePath;
                }
            }
        }
        
        // Fallback to showing last few path components
        String[] parts = fullPath.split("/");
        if (parts.length > 3) {
            return ".../" + parts[parts.length - 2] + "/" + parts[parts.length - 1];
        } else {
            return fullPath;
        }
    }

    private static SystemStorageManager.StorageLocation findStorageLocationForPath(String path, Context context) {
        List<SystemStorageManager.StorageLocation> storageLocations = 
            SystemStorageManager.getSystemStorageRoots(context);
        
        for (SystemStorageManager.StorageLocation location : storageLocations) {
            if (path.startsWith(location.path)) {
                return location;
            }
        }
        
        return null;
    }

    public static boolean isSystemDirectoryAccessible(File directory, Context context) {
        if (directory == null || !directory.exists()) {
            return false;
        }
        
        // Check if we have permission to read this directory
        if (!directory.canRead()) {
            return false;
        }
        
        // For Android 11+, some directories require MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            String path = directory.getAbsolutePath();
            
            // These paths require MANAGE_EXTERNAL_STORAGE
            String[] restrictedPaths = {
                "/Android/data",
                "/Android/obb"
            };
            
            for (String restrictedPath : restrictedPaths) {
                if (path.contains(restrictedPath)) {
                    return Environment.isExternalStorageManager();
                }
            }
        }
        
        return true;
    }

    public static String getPermissionRequirementMessage(File directory) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            String path = directory.getAbsolutePath();
            
            if (path.contains("/Android/data") || path.contains("/Android/obb")) {
                return "All Files Access permission required to browse this directory";
            }
        }
        
        return "Storage permission required";
    }
}