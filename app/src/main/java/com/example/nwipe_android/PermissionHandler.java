package com.example.nwipe_android;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionHandler {
    public static final int STORAGE_PERMISSION_REQUEST = 1001;
    public static final int MANAGE_EXTERNAL_STORAGE_REQUEST = 1002;

    public static boolean checkPermissions(Context context) {
        return StorageManager.hasStoragePermissions(context);
    }

    public static boolean checkSystemStoragePermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return checkPermissions(context);
        }
    }

    public static void requestPermissions(Activity activity) {
        StorageManager.requestStoragePermissions(activity);
    }

    public static void requestSystemStoragePermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestManageExternalStoragePermission(activity);
        } else {
            requestPermissions(activity);
        }
    }

    public static void requestManageExternalStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST);
            } catch (Exception e) {
                // Fallback to general manage all files settings
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                activity.startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST);
            }
        }
    }

    public static boolean handlePermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0) {
                // Check if all requested permissions were granted
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public static String getPermissionDeniedMessage(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return "Storage access is required for wiping. Please go to Settings > Apps > " + 
                   getAppName(context) + " > Permissions and enable 'All files access'.";
        } else {
            return "Storage permissions are required for wiping. Please grant storage access in the permission dialog.";
        }
    }

    public static String getSystemStoragePermissionDeniedMessage(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return "All Files Access permission is required for full storage browsing. " +
                   "Please go to Settings > Apps > " + getAppName(context) + 
                   " > Permissions and enable 'All files access'.";
        } else {
            return getPermissionDeniedMessage(context);
        }
    }

    public static String getPermissionRationaleMessage() {
        return "This app needs storage access to securely wipe your device's storage. " +
               "Without this permission, the app cannot function properly.";
    }

    public static String getSystemStoragePermissionRationaleMessage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return "This app needs 'All Files Access' permission to browse and manage all files on your device. " +
                   "This allows you to select specific files and folders for deletion during testing. " +
                   "Without this permission, file browsing will be limited to certain directories.";
        } else {
            return getPermissionRationaleMessage();
        }
    }

    private static String getAppName(Context context) {
        try {
            return context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
        } catch (Exception e) {
            return "nwipe-android";
        }
    }

    public static boolean shouldShowRequestPermissionRationale(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                   ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        return false;
    }

    public static boolean handleSystemStoragePermissionResult(int requestCode) {
        if (requestCode == MANAGE_EXTERNAL_STORAGE_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return Environment.isExternalStorageManager();
            }
        }
        return false;
    }

    public static String getSystemStoragePermissionStatus(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                return "All Files Access: Granted ✓";
            } else {
                return "All Files Access: Required for full browsing";
            }
        } else {
            if (checkPermissions(context)) {
                return "Storage Access: Granted ✓";
            } else {
                return "Storage Access: Required";
            }
        }
    }

    public static boolean hasFullStorageAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return checkPermissions(context);
        }
    }

    public static String getStorageAccessLevelDescription(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                return "Full system storage access - can browse and modify all accessible files";
            } else if (checkPermissions(context)) {
                return "Limited storage access - can browse media and download directories";
            } else {
                return "No storage access - file browsing unavailable";
            }
        } else {
            if (checkPermissions(context)) {
                return "Storage access granted - can browse and modify accessible files";
            } else {
                return "No storage access - file browsing unavailable";
            }
        }
    }
}