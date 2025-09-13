package com.example.nwipe_android;

import android.content.Context;
import android.util.Log;

/**
 * Simple test class to verify storage information functionality
 */
public class StorageTest {
    
    private static final String TAG = "StorageTest";
    
    /**
     * Test the accurate storage info functionality
     */
    public static void testAccurateStorageInfo(Context context) {
        Log.d(TAG, "=== Testing Accurate Storage Information ===");
        
        // Test permission check
        boolean hasPermissions = AccurateStorageManager.hasStoragePermissions(context);
        Log.d(TAG, "Has storage permissions: " + hasPermissions);
        
        // Test storage info retrieval
        AccurateStorageManager.AccurateStorageInfo info = AccurateStorageManager.getAccurateStorageInfo(context);
        Log.d(TAG, "Storage accessible: " + info.isAccessible);
        Log.d(TAG, "Storage path: " + info.storagePath);
        
        if (info.isValid()) {
            Log.d(TAG, "Total storage: " + info.totalFormatted + " (" + info.totalBytes + " bytes)");
            Log.d(TAG, "Used storage: " + info.usedFormatted + " (" + info.usedBytes + " bytes)");
            Log.d(TAG, "Available storage: " + info.availableFormatted + " (" + info.availableBytes + " bytes)");
            Log.d(TAG, "Used percentage: " + info.usedPercentage + "%");
        } else {
            Log.d(TAG, "Storage error: " + info.errorMessage);
            Log.d(TAG, "Has permissions: " + info.hasPermissions);
        }
        
        // Test summary string
        String summary = AccurateStorageManager.getStorageSummary(context);
        Log.d(TAG, "Storage summary: " + summary);
        
        Log.d(TAG, "=== Storage Test Complete ===");
    }
    
    /**
     * Compare old vs new storage manager results
     */
    public static void compareStorageManagers(Context context) {
        Log.d(TAG, "=== Comparing Storage Managers ===");
        
        // Test old StorageManager
        try {
            StorageInfo oldInfo = StorageManager.getStorageInfo(context);
            Log.d(TAG, "Old StorageManager - Valid: " + oldInfo.isValid());
            if (oldInfo.isValid()) {
                Log.d(TAG, "Old - Total: " + oldInfo.getFormattedTotalSize() + ", Available: " + oldInfo.getFormattedAvailableSize());
            } else {
                Log.d(TAG, "Old - Error: " + oldInfo.errorMessage);
            }
        } catch (Exception e) {
            Log.e(TAG, "Old StorageManager failed: " + e.getMessage());
        }
        
        // Test new AccurateStorageManager
        try {
            AccurateStorageManager.AccurateStorageInfo newInfo = AccurateStorageManager.getAccurateStorageInfo(context);
            Log.d(TAG, "New AccurateStorageManager - Valid: " + newInfo.isValid());
            if (newInfo.isValid()) {
                Log.d(TAG, "New - Total: " + newInfo.totalFormatted + ", Available: " + newInfo.availableFormatted);
            } else {
                Log.d(TAG, "New - Error: " + newInfo.errorMessage);
            }
        } catch (Exception e) {
            Log.e(TAG, "New AccurateStorageManager failed: " + e.getMessage());
        }
        
        Log.d(TAG, "=== Comparison Complete ===");
    }
}