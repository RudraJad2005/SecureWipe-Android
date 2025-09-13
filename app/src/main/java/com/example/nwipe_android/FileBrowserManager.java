package com.example.nwipe_android;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileBrowserManager {
    private static final String TAG = "FileBrowserManager";

    public static class BrowseResult {
        public boolean success;
        public String message;
        public List<FileItem> items;
        public String currentPath;

        public BrowseResult(boolean success, String message, List<FileItem> items, String currentPath) {
            this.success = success;
            this.message = message;
            this.items = items != null ? items : new ArrayList<>();
            this.currentPath = currentPath;
        }
    }

    public static class DeleteResult {
        public boolean success;
        public String message;
        public FileItem item;

        public DeleteResult(boolean success, String message, FileItem item) {
            this.success = success;
            this.message = message;
            this.item = item;
        }
    }

    public static List<File> getSafeRootDirectories(Context context) {
        List<File> roots = new ArrayList<>();
        
        // App's external files directory
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir != null && externalFilesDir.exists()) {
            roots.add(externalFilesDir);
        }
        
        // App's cache directory
        File cacheDir = context.getCacheDir();
        if (cacheDir != null && cacheDir.exists()) {
            roots.add(cacheDir);
        }
        
        // App's internal files directory
        File filesDir = context.getFilesDir();
        if (filesDir != null && filesDir.exists()) {
            roots.add(filesDir);
        }
        
        // External cache directory
        File externalCacheDir = context.getExternalCacheDir();
        if (externalCacheDir != null && externalCacheDir.exists()) {
            roots.add(externalCacheDir);
        }
        
        return roots;
    }

    public static BrowseResult listDirectory(File directory) {
        if (directory == null) {
            return new BrowseResult(false, "Directory is null", null, "");
        }
        
        if (!directory.exists()) {
            return new BrowseResult(false, "Directory does not exist", null, directory.getAbsolutePath());
        }
        
        if (!directory.isDirectory()) {
            return new BrowseResult(false, "Not a directory", null, directory.getAbsolutePath());
        }
        
        if (!directory.canRead()) {
            return new BrowseResult(false, "No read permission", null, directory.getAbsolutePath());
        }
        
        try {
            File[] files = directory.listFiles();
            if (files == null) {
                return new BrowseResult(false, "Cannot list directory contents", null, directory.getAbsolutePath());
            }
            
            List<FileItem> items = new ArrayList<>();
            for (File file : files) {
                try {
                    items.add(new FileItem(file));
                } catch (Exception e) {
                    Log.w(TAG, "Error creating FileItem for " + file.getName() + ": " + e.getMessage());
                }
            }
            
            // Sort items: directories first, then files, both alphabetically
            Collections.sort(items, new Comparator<FileItem>() {
                @Override
                public int compare(FileItem a, FileItem b) {
                    // Directories first
                    if (a.getType() == FileItem.ItemType.DIRECTORY && b.getType() != FileItem.ItemType.DIRECTORY) {
                        return -1;
                    }
                    if (b.getType() == FileItem.ItemType.DIRECTORY && a.getType() != FileItem.ItemType.DIRECTORY) {
                        return 1;
                    }
                    // Then alphabetically
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            
            String message = String.format("Found %d items", items.size());
            return new BrowseResult(true, message, items, directory.getAbsolutePath());
            
        } catch (Exception e) {
            Log.e(TAG, "Error listing directory: " + e.getMessage());
            return new BrowseResult(false, "Error listing directory: " + e.getMessage(), null, directory.getAbsolutePath());
        }
    }

    public static BrowseResult listRootDirectories(Context context) {
        List<File> roots = getSafeRootDirectories(context);
        List<FileItem> items = new ArrayList<>();
        
        for (File root : roots) {
            try {
                FileItem item = new FileItem(root);
                items.add(item);
            } catch (Exception e) {
                Log.w(TAG, "Error creating FileItem for root " + root.getName() + ": " + e.getMessage());
            }
        }
        
        String message = String.format("Found %d accessible directories", items.size());
        return new BrowseResult(true, message, items, "App Storage");
    }

    public static DeleteResult deleteItem(FileItem item) {
        if (item == null) {
            return new DeleteResult(false, "Item is null", null);
        }
        
        if (!item.canDelete()) {
            return new DeleteResult(false, "No delete permission for " + item.getName(), item);
        }
        
        if (!item.isSafeToDelete()) {
            return new DeleteResult(false, "Item is not safe to delete: " + item.getName(), item);
        }
        
        try {
            boolean deleted = item.delete();
            if (deleted) {
                String message = String.format("Successfully deleted %s", item.getName());
                Log.i(TAG, message);
                return new DeleteResult(true, message, item);
            } else {
                String message = String.format("Failed to delete %s (unknown reason)", item.getName());
                return new DeleteResult(false, message, item);
            }
        } catch (Exception e) {
            String message = String.format("Error deleting %s: %s", item.getName(), e.getMessage());
            Log.e(TAG, message);
            return new DeleteResult(false, message, item);
        }
    }

    public static String getDisplayPath(String fullPath, Context context) {
        if (fullPath == null || fullPath.isEmpty()) {
            return "App Storage";
        }
        
        // Try to make path relative to app directories for cleaner display
        List<File> roots = getSafeRootDirectories(context);
        for (File root : roots) {
            String rootPath = root.getAbsolutePath();
            if (fullPath.startsWith(rootPath)) {
                String relativePath = fullPath.substring(rootPath.length());
                if (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }
                
                String rootName = getRootDisplayName(root, context);
                if (relativePath.isEmpty()) {
                    return rootName;
                } else {
                    return rootName + "/" + relativePath;
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

    private static String getRootDisplayName(File root, Context context) {
        String path = root.getAbsolutePath();
        
        if (context.getExternalFilesDir(null) != null && path.equals(context.getExternalFilesDir(null).getAbsolutePath())) {
            return "External Files";
        } else if (path.equals(context.getCacheDir().getAbsolutePath())) {
            return "Cache";
        } else if (path.equals(context.getFilesDir().getAbsolutePath())) {
            return "Internal Files";
        } else if (context.getExternalCacheDir() != null && path.equals(context.getExternalCacheDir().getAbsolutePath())) {
            return "External Cache";
        } else {
            return root.getName();
        }
    }

    public static boolean isSafeDirectory(File directory, Context context) {
        if (directory == null) {
            return false;
        }
        
        String path = directory.getAbsolutePath();
        List<File> safeRoots = getSafeRootDirectories(context);
        
        for (File root : safeRoots) {
            if (path.startsWith(root.getAbsolutePath())) {
                return true;
            }
        }
        
        return false;
    }
}