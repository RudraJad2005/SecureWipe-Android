package com.example.nwipe_android;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestFileManager {
    private static final String TEST_FILE_PREFIX = "nwipe_test_";
    private static final String TEST_FILE_EXTENSION = ".txt";
    private static final String TEST_FILE_CONTENT = "This is a test file created by nwipe-android to verify backend functionality. Safe to delete.";

    public static class TestFileResult {
        public boolean success;
        public String message;
        public File file;

        public TestFileResult(boolean success, String message, File file) {
            this.success = success;
            this.message = message;
            this.file = file;
        }
    }

    public static TestFileResult createTestFile(Context context) {
        try {
            // Check permissions first
            if (!PermissionHandler.checkPermissions(context)) {
                return new TestFileResult(false, "Storage permissions not granted", null);
            }

            // Get safe directory for test files
            File testDir = getTestDirectory(context);
            if (testDir == null) {
                return new TestFileResult(false, "No accessible directory found for test files", null);
            }

            // Create test file with timestamp
            String fileName = TEST_FILE_PREFIX + System.currentTimeMillis() + TEST_FILE_EXTENSION;
            File testFile = new File(testDir, fileName);

            // Write test content
            try (FileOutputStream fos = new FileOutputStream(testFile)) {
                fos.write(TEST_FILE_CONTENT.getBytes());
                fos.flush();
            }

            Log.i("TestFileManager", "Created test file: " + testFile.getAbsolutePath());
            return new TestFileResult(true, "Test file created successfully: " + fileName, testFile);

        } catch (IOException e) {
            Log.e("TestFileManager", "Failed to create test file: " + e.getMessage());
            return new TestFileResult(false, "Failed to create test file: " + e.getMessage(), null);
        } catch (Exception e) {
            Log.e("TestFileManager", "Unexpected error creating test file: " + e.getMessage());
            return new TestFileResult(false, "Unexpected error: " + e.getMessage(), null);
        }
    }

    public static List<File> listTestFiles(Context context) {
        List<File> testFiles = new ArrayList<>();

        try {
            // Get all possible test directories
            List<File> testDirs = getTestDirectories(context);

            for (File dir : testDirs) {
                if (dir != null && dir.exists() && dir.isDirectory()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.getName().startsWith(TEST_FILE_PREFIX) && 
                                file.getName().endsWith(TEST_FILE_EXTENSION)) {
                                testFiles.add(file);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("TestFileManager", "Error listing test files: " + e.getMessage());
        }

        return testFiles;
    }

    public static TestFileResult deleteTestFile(File testFile) {
        try {
            if (testFile == null || !testFile.exists()) {
                return new TestFileResult(false, "File does not exist", testFile);
            }

            // Safety check - only delete our test files
            if (!testFile.getName().startsWith(TEST_FILE_PREFIX)) {
                return new TestFileResult(false, "Safety check failed: Not a test file", testFile);
            }

            boolean deleted = testFile.delete();
            if (deleted) {
                Log.i("TestFileManager", "Deleted test file: " + testFile.getName());
                return new TestFileResult(true, "Test file deleted successfully: " + testFile.getName(), testFile);
            } else {
                return new TestFileResult(false, "Failed to delete file (permission denied?)", testFile);
            }

        } catch (Exception e) {
            Log.e("TestFileManager", "Error deleting test file: " + e.getMessage());
            return new TestFileResult(false, "Error deleting file: " + e.getMessage(), testFile);
        }
    }

    public static TestFileResult deleteAllTestFiles(Context context) {
        List<File> testFiles = listTestFiles(context);
        int deletedCount = 0;
        int failedCount = 0;
        StringBuilder messages = new StringBuilder();

        for (File file : testFiles) {
            TestFileResult result = deleteTestFile(file);
            if (result.success) {
                deletedCount++;
            } else {
                failedCount++;
                messages.append(result.message).append("\n");
            }
        }

        String message = String.format("Deleted %d files", deletedCount);
        if (failedCount > 0) {
            message += String.format(", %d failed:\n%s", failedCount, messages.toString());
        }

        return new TestFileResult(failedCount == 0, message, null);
    }

    private static File getTestDirectory(Context context) {
        // Try external files directory first (most accessible)
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir != null && (externalFilesDir.exists() || externalFilesDir.mkdirs())) {
            return externalFilesDir;
        }

        // Fallback to cache directory
        File cacheDir = context.getCacheDir();
        if (cacheDir != null && (cacheDir.exists() || cacheDir.mkdirs())) {
            return cacheDir;
        }

        // Last resort - internal files directory
        File filesDir = context.getFilesDir();
        if (filesDir != null && (filesDir.exists() || filesDir.mkdirs())) {
            return filesDir;
        }

        return null;
    }

    private static List<File> getTestDirectories(Context context) {
        List<File> dirs = new ArrayList<>();
        
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir != null) dirs.add(externalFilesDir);
        
        File cacheDir = context.getCacheDir();
        if (cacheDir != null) dirs.add(cacheDir);
        
        File filesDir = context.getFilesDir();
        if (filesDir != null) dirs.add(filesDir);
        
        return dirs;
    }

    public static String getTestFileInfo(Context context) {
        List<File> testFiles = listTestFiles(context);
        if (testFiles.isEmpty()) {
            return "No test files found";
        }

        StringBuilder info = new StringBuilder();
        info.append(String.format("Found %d test files:\n", testFiles.size()));
        
        for (File file : testFiles) {
            info.append(String.format("• %s (%.1f KB)\n", 
                file.getName(), 
                file.length() / 1024.0));
        }
        
        return info.toString();
    }
}