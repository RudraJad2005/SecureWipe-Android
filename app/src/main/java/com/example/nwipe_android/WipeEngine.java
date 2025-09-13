package com.example.nwipe_android;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

/**
 * Core wiping backend that performs multi-pass overwrites on available storage locations.
 * Runs synchronously; supply a CancelToken and Callback to observe progress and stop safely.
 */
public class WipeEngine {

    public interface Callback {
        void onProgress(WipeJob job);
        void onMessage(String message);
    }

    public static class CancelToken {
        private volatile boolean cancelled = false;
        public void cancel() { this.cancelled = true; }
        public boolean isCancelled() { return cancelled; }
    }

    private static final int WIPE_BUFFER_SIZE = 1024 * 1024; // 1MB for efficient wiping
    private static final int PROGRESS_UPDATE_THRESHOLD = 10 * 1024 * 1024; // Update progress every 10MB
    private static final String TAG = "SecureWipe";
    private static final String WIPE_FILES_PREFIX = "nwipe-android-";

    private final Context appContext;
    private final Callback callback;
    private final CancelToken cancelToken;

    public WipeEngine(Context context, Callback callback, CancelToken cancelToken) {
        this.appContext = context.getApplicationContext();
        this.callback = callback;
        this.cancelToken = cancelToken;
    }

    public WipeJob execute(WipeJob job) {
        long wipeStartTime = System.currentTimeMillis();
        Log.i(TAG, "=== STARTING SECURE WIPE OPERATION ===");
        
        if (job.targetPath != null || job.hasSelectedFolders()) {
            Log.i(TAG, "Targeted wipe mode: " + (job.targetName != null ? job.targetName : job.targetPath));
        }
        cleanupWipeFiles(job);

    // Optional pre-wipe deletion of existing content (skip when wiping specific folders)
    if (!job.hasSelectedFolders() && job.deleteExistingFirst && !job.deletionCompleted && !cancelToken.isCancelled()) {
            try {
        Log.i(TAG, "Pre-wipe deletion phase started" + (job.targetPath != null ? (" for target '" + (job.targetName != null ? job.targetName : job.targetPath) + "'") : ""));
        deleteExistingData(job);
                job.deletionCompleted = true;
                Log.i(TAG, "Pre-wipe deletion phase completed");
            } catch (Exception e) {
                Log.w(TAG, "Deletion phase encountered an error: " + e.getMessage());
            }
        }

        // Folder-targeted mode: overwrite only selected folders
        if (job.hasSelectedFolders()) {
            try {
                executeFolderWipe(job);
            } catch (Exception e) {
                job.errorMessage = "Error in folder wipe: " + e.getMessage();
                Log.e(TAG, job.errorMessage);
            }
            return job;
        }

        while (!job.isCompleted()) {
            try {
                Log.i(TAG, "Executing pass " + (job.passes_completed + 1) + (job.isBlankingPass() ? " (blanking)" : ""));
                executeWipePass(job);
            } catch (Exception e) {
                cleanupWipeFiles(job);
                job.errorMessage = "Unknown error while wiping: " + e;
                emit(job);
                break;
            }
            cleanupWipeFiles(job);
            emit(job);

            if (job.failed() || cancelToken.isCancelled()) {
                if (cancelToken.isCancelled()) {
                Log.i(TAG, "Wipe cancelled by user");
            }
                break;
            }
        }
        
        // Security validation: Check total operation time
        long totalElapsedMs = System.currentTimeMillis() - wipeStartTime;
        double totalMB = job.totalBytes / (1024.0 * 1024.0);
        double avgMBPerSec = totalElapsedMs > 0 ? (totalMB * 1000.0 / totalElapsedMs) : 0;
        
        Log.i(TAG, String.format("=== WIPE OPERATION COMPLETE ==="));
        Log.i(TAG, String.format("Total: %.1f MB wiped in %.1f seconds (%.1f MB/s average)", 
            totalMB, totalElapsedMs / 1000.0, avgMBPerSec));
            
        // Security warning for suspiciously fast completion
        if (totalMB > 100 && totalElapsedMs < 5000) { // > 100MB in < 5 seconds
            Log.w(TAG, "⚠️ SECURITY WARNING: Wipe completed suspiciously quickly!");
            Log.w(TAG, "⚠️ This may indicate insufficient data was actually overwritten!");
            job.errorMessage = "WARNING: Wipe may not be secure - completed too quickly";
        }
        
        return job;
    }

    private void executeFolderWipe(WipeJob job) throws Exception {
        java.util.List<File> roots = new java.util.ArrayList<>();
        for (String path : job.targetFolders) {
            if (path == null) continue;
            File f = new File(path);
            if (f.exists() && f.canWrite()) roots.add(f);
            else Log.w(TAG, "Skipping inaccessible folder: " + path);
        }
        if (roots.isEmpty()) {
            job.errorMessage = "No accessible selected folders";
            return;
        }

        // Compute total bytes across all files in selected folders
        long total = 0;
        Log.i(TAG, "Scanning selected folders for files to wipe:");
        for (File root : roots) {
            long folderSize = folderBytes(root);
            total += folderSize;
            Log.i(TAG, String.format("  📁 %s: %.1f MB (%d bytes)", 
                root.getAbsolutePath(), folderSize / (1024.0 * 1024.0), folderSize));
        }
        
        job.totalBytes = total;
        job.wipedBytes = 0;
        
        Log.i(TAG, String.format("Total data to wipe: %.1f MB across %d folders", 
            total / (1024.0 * 1024.0), roots.size()));
            
        if (total == 0) {
            Log.w(TAG, "⚠️ No files found to wipe in selected folders!");
            job.errorMessage = "No data found to wipe in selected folders";
            return;
        }
        
        emit(job);

        SecureRandom secureRandom = new SecureRandom();
        int passesTotal = job.blank ? job.number_passes + 1 : job.number_passes;
        Log.i(TAG, "Starting folder wipe: " + passesTotal + " passes over " + roots.size() + " folders");
        
        for (int passIndex = 0; passIndex < passesTotal; passIndex++) {
            if (cancelToken.isCancelled()) break;
            boolean isBlanking = (job.blank && passIndex == job.number_passes);
            Log.i(TAG, "=== FOLDER WIPE PASS " + (passIndex + 1) + "/" + passesTotal + (isBlanking ? " (BLANKING)" : " (RANDOM DATA)") + " ===");

            int seed = secureRandom.nextInt();
            for (File root : roots) {
                if (cancelToken.isCancelled()) break;
                Log.i(TAG, "Overwriting files in: " + root.getAbsolutePath());
                overwriteFolder(job, root, seed, !isBlanking);
            }

            if (job.verify && !cancelToken.isCancelled()) {
                job.verifying = true;
                for (File root : roots) {
                    if (cancelToken.isCancelled()) break;
                    verifyFolder(job, root, seed, !isBlanking);
                }
                job.verifying = false;
            }

            job.passes_completed++;
        }

        // After all passes, create junk files to fill remaining space in each selected folder's parent
        if (!cancelToken.isCancelled()) {
            Log.i(TAG, "Creating junk files to fill remaining space in selected folders...");
            for (File root : roots) {
                if (cancelToken.isCancelled()) break;
                try {
                    createJunkFilesInDirectory(job, root.getParentFile() != null ? root.getParentFile() : root);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to create junk files in " + root.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }

        // After all passes, delete selected files and junk files (best effort)
        for (File root : roots) {
            if (cancelToken.isCancelled()) break;
            deleteRecursively(root, root);
        }
        
        // Clean up any junk files we created
        cleanupWipeFiles(job);
    }

    private long folderBytes(File root) {
        long sum = 0;
        try {
            if (root.isFile()) return root.length();
            File[] list = root.listFiles();
            if (list == null) return 0;
            for (File f : list) {
                if (cancelToken.isCancelled()) break;
                sum += folderBytes(f);
            }
        } catch (Exception ignored) {}
        return sum;
    }

    private void overwriteFolder(WipeJob job, File root, int seed, boolean random) {
        if (cancelToken.isCancelled()) return;
        try {
            if (root.isFile()) {
                overwriteFile(job, root, seed, random);
                return;
            }
            File[] list = root.listFiles();
            if (list == null) return;
            for (File f : list) {
                if (cancelToken.isCancelled()) break;
                if (f.isDirectory()) {
                    overwriteFolder(job, f, seed, random);
                } else {
                    overwriteFile(job, f, seed, random);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Overwrite folder error on " + root.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    private void verifyFolder(WipeJob job, File root, int seed, boolean random) {
        if (cancelToken.isCancelled()) return;
        try {
            if (root.isFile()) {
                verifyFile(job, root, seed, random);
                return;
            }
            File[] list = root.listFiles();
            if (list == null) return;
            for (File f : list) {
                if (cancelToken.isCancelled()) break;
                if (f.isDirectory()) {
                    verifyFolder(job, f, seed, random);
                } else {
                    verifyFile(job, f, seed, random);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Verify folder error on " + root.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    private void overwriteFile(WipeJob job, File file, int seed, boolean random) {
        if (!file.canWrite()) return;
        
        long fileSize = file.length();
        if (fileSize == 0) return; // Skip empty files
        
        long startTime = System.currentTimeMillis();
        Log.d(TAG, "Overwriting: " + file.getAbsolutePath() + " (" + (fileSize / 1024) + " KB)");
        
        byte[] buffer = new byte[WIPE_BUFFER_SIZE];
        java.util.Random rnd = new java.util.Random();
        rnd.setSeed(seed);
        long remaining = fileSize;
        long bytesWritten = 0;
        long lastProgressUpdate = 0;
        
        try (OutputStream os = new java.io.FileOutputStream(file, false)) {
            while (remaining > 0) {
                if (cancelToken.isCancelled()) break;
                
                int toWrite = (int) Math.min(buffer.length, remaining);
                if (random) {
                    rnd.nextBytes(buffer);
                } else {
                    java.util.Arrays.fill(buffer, (byte)0);
                }
                
                // Force write to disk (critical for security)
                os.write(buffer, 0, toWrite);
                os.flush();
                
                remaining -= toWrite;
                bytesWritten += toWrite;
                job.wipedBytes = Math.min(job.totalBytes, job.wipedBytes + toWrite);
                
                // Update progress less frequently for performance
                if (bytesWritten - lastProgressUpdate >= PROGRESS_UPDATE_THRESHOLD) {
                    emit(job);
                    lastProgressUpdate = bytesWritten;
                }
            }
            
            // Force sync to disk (CRITICAL for secure wiping)
            try {
                if (os instanceof java.io.FileOutputStream) {
                    ((java.io.FileOutputStream) os).getFD().sync();
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to sync file to disk: " + e.getMessage());
            }
            
            long elapsedMs = System.currentTimeMillis() - startTime;
            double mbWritten = bytesWritten / (1024.0 * 1024.0);
            double mbPerSec = elapsedMs > 0 ? (mbWritten * 1000.0 / elapsedMs) : 0;
            
            Log.i(TAG, String.format("✓ Overwrite complete: %s (%.1f MB in %d ms, %.1f MB/s)", 
                file.getAbsolutePath(), mbWritten, elapsedMs, mbPerSec));
                
            // Security validation: Ensure reasonable write speed (not too fast = fake)
            if (mbWritten > 1.0 && mbPerSec > 500.0) { // Suspiciously fast > 500 MB/s
                Log.w(TAG, "⚠️ WARNING: Extremely fast write speed detected - may indicate insufficient wiping!");
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Overwrite failed for file " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    private void verifyFile(WipeJob job, File file, int seed, boolean random) {
        if (!file.canRead()) return;
        byte[] expected = new byte[WIPE_BUFFER_SIZE];
        byte[] actual = new byte[WIPE_BUFFER_SIZE];
        java.util.Random rnd = new java.util.Random();
        rnd.setSeed(seed);
        long remaining = file.length();
        try (InputStream is = new java.io.FileInputStream(file)) {
            while (remaining > 0) {
                if (cancelToken.isCancelled()) break;
                int toRead = (int) Math.min(actual.length, remaining);
                if (random) rnd.nextBytes(expected); else java.util.Arrays.fill(expected, (byte)0);
                int read = is.read(actual, 0, toRead);
                if (read != toRead) break; // short read
                if (!java.util.Arrays.equals(java.util.Arrays.copyOfRange(expected, 0, toRead), java.util.Arrays.copyOfRange(actual, 0, toRead))) {
                    job.errorMessage = "Verification mismatch for file: " + file.getAbsolutePath();
                    Log.e(TAG, job.errorMessage);
                    return;
                }
                remaining -= toRead;
                job.wipedBytes = Math.min(job.totalBytes, job.wipedBytes + toRead);
                emit(job);
            }
        } catch (IOException e) {
            Log.w(TAG, "Verify failed for file " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    private void createJunkFilesInDirectory(WipeJob job, File directory) throws Exception {
        if (!directory.canWrite()) return;
        
        // Get available space in this directory
        long freeSpace = directory.getFreeSpace();
        if (freeSpace <= 0) return;
        
        Log.i(TAG, "Creating junk files to fill " + (freeSpace / (1024 * 1024)) + " MB in " + directory.getAbsolutePath());
        
        SecureRandom secureRandom = new SecureRandom();
        String wipeFileName = String.format("%s%d_folder_junk", WIPE_FILES_PREFIX, System.currentTimeMillis());
        File junkFile = new File(directory, wipeFileName);
        
        try (OutputStream os = new java.io.FileOutputStream(junkFile)) {
            Random rnd = new Random();
            rnd.setSeed(secureRandom.nextInt());
            byte[] buffer = new byte[WIPE_BUFFER_SIZE];
            
            long written = 0;
            long targetSize = Math.min(freeSpace - (1024 * 1024), freeSpace * 95 / 100); // Leave some space
            
            while (written < targetSize && !cancelToken.isCancelled()) {
                int toWrite = (int) Math.min(buffer.length, targetSize - written);
                
                if (job.isBlankingPass()) {
                    Arrays.fill(buffer, 0, toWrite, (byte) 0);
                } else {
                    rnd.nextBytes(buffer);
                }
                
                os.write(buffer, 0, toWrite);
                written += toWrite;
                
                // Update progress (this is extra work beyond the original file overwrites)
                emit(job);
            }
            
            Log.i(TAG, "Created junk file: " + junkFile.getAbsolutePath() + " (" + (written / (1024 * 1024)) + " MB)");
        } catch (IOException e) {
            Log.w(TAG, "Failed to create junk file in " + directory.getAbsolutePath() + ": " + e.getMessage());
            // Try to clean up partial file
            try {
                if (junkFile.exists()) junkFile.delete();
            } catch (Exception ignored) {}
        }
    }

    private void deleteExistingData(WipeJob job) {
        try {
            StorageInfo storageInfo = StorageManager.getStorageInfo(appContext);
            if (storageInfo.locations == null) return;

            for (StorageLocation location : storageInfo.locations) {
                if (cancelToken.isCancelled()) return;
                if (!location.isUsable()) continue;
                File dir = location.directory;
                if (dir == null || !dir.exists() || !dir.canWrite()) continue;

                // If a specific target is set, skip other locations
                if (job.targetPath != null) {
                    try {
                        File target = new File(job.targetPath);
                        if (!sameFile(target, dir)) {
                            continue;
                        }
                    } catch (Exception ignored) { continue; }
                }

                Log.i(TAG, "Deleting existing content on: " + location.displayName + " (" + dir.getAbsolutePath() + ")");
                // Skip protected roots when on external shared storage (Android/)
                deleteRecursively(dir, dir);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error during deletion: " + e.getMessage());
        }
    }

    private void deleteRecursively(File root, File file) {
        if (cancelToken.isCancelled()) return;
        try {
            // Include Android directory as requested; deletion may still be limited by platform

            if (file.isDirectory()) {
                // If this is a top-level folder under the selected root, log it for visibility
                try {
                    File parent = file.getParentFile();
                    if (parent != null && sameFile(root, parent)) {
                        Log.d(TAG, "Scanning folder: " + file.getAbsolutePath());
                    }
                } catch (Exception ignored) {}
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursively(root, child);
                        if (cancelToken.isCancelled()) return;
                    }
                }
            }

            // Don’t delete the root directory itself, only its contents
            if (!file.equals(root)) {
                boolean ok = file.delete();
                if (ok) {
                    Log.i(TAG, "Deleted: " + file.getAbsolutePath());
                } else if (file.exists()) {
                    Log.w(TAG, "Could not delete: " + file.getAbsolutePath());
                }
            }
        } catch (SecurityException se) {
            Log.w(TAG, "No permission to delete: " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "Failed to delete: " + file.getAbsolutePath() + " - " + e.getMessage());
        }
    }

    private void executeWipePass(WipeJob job) throws Exception {
    StorageInfo storageInfo = StorageManager.getStorageInfo(appContext);
        if (!storageInfo.isValid()) {
            job.errorMessage = "Cannot access storage: " + storageInfo.errorMessage;
        Log.e(TAG, job.errorMessage);
            return;
        }
        if (!storageInfo.hasPermissions) {
            job.errorMessage = "Storage permissions not granted. Cannot proceed with wiping.";
        Log.e(TAG, job.errorMessage);
            return;
        }

        job.totalBytes = storageInfo.availableBytes;
        job.wipedBytes = 0;
        emit(job);

        boolean wipedAny = false;
        String lastError = "";

        if (job.targetPath != null) {
            Log.i(TAG, "Restricting wipe to target path: " + job.targetPath);
        }

        for (StorageLocation location : storageInfo.locations) {
            if (cancelToken.isCancelled()) break;
            if (location.isUsable()) {
                // Restrict to target when provided
                if (job.targetPath != null) {
                    try {
                        File target = new File(job.targetPath);
                        if (!sameFile(target, location.directory)) {
                            Log.d(TAG, "Skipping non-target: " + location.displayName + " (" + location.directory.getAbsolutePath() + ")");
                            continue;
                        }
                    } catch (Exception ignored) { continue; }
                }
                try {
            Log.i(TAG, "Wiping location: " + location.displayName + " (" + location.directory.getAbsolutePath() + ")");
                    wipeLocation(job, location);
                    wipedAny = true;
                    break; // one location is sufficient per pass
                } catch (Exception e) {
                    lastError = e.getMessage();
            Log.w(TAG, "Failed to wipe location " + location.displayName + ": " + e.getMessage());
                }
            }
        }

        if (!wipedAny) {
            job.errorMessage = "Failed to wipe any storage location. Last error: " + lastError;
        Log.e(TAG, job.errorMessage);
        }
    }

    private void wipeLocation(WipeJob job, StorageLocation location) throws Exception {
        String wipeFileName = String.format("%s%d_%s", WIPE_FILES_PREFIX, System.currentTimeMillis(),
                location.type.name().toLowerCase());

        SecureRandom secureRandom = new SecureRandom();
        int randomSeed = secureRandom.nextInt();

    File wipeFile = new File(location.directory, wipeFileName);
    Log.i(TAG, "Creating wipe file: " + wipeFile.getAbsolutePath());

        try (OutputStream os = new java.io.FileOutputStream(wipeFile)) {
            Random rnd = new Random();
            rnd.setSeed(randomSeed);
            byte[] buffer = new byte[WIPE_BUFFER_SIZE];

            while (job.wipedBytes < job.totalBytes) {
                if (cancelToken.isCancelled()) break;

                long remaining = job.totalBytes - job.wipedBytes;
                int toWrite = (int)Math.min(WIPE_BUFFER_SIZE, remaining);

                if (!job.isBlankingPass()) rnd.nextBytes(buffer);
                os.write(buffer, 0, toWrite);

                job.wipedBytes += toWrite;
                emit(job);
                // Log every ~100MB written to avoid spam
                if (job.wipedBytes % (100L * 1024L * 1024L) < WIPE_BUFFER_SIZE) {
                    Log.i(TAG, "Progress: " + (job.wipedBytes / (1024 * 1024)) + " MB / " + 
                        (job.totalBytes / (1024 * 1024)) + " MB (" + 
                        String.format("%.1f", job.getCurrentPassPercentageCompletion()) + "%)");
                }
            }
        } catch (IOException e) {
            // Treat no space left as success if we reached MIN_PERCENTAGE_COMPLETION
            String msg = e.toString();
            if ((msg.contains("ENOSP") || msg.contains("No space") || msg.contains("ENOSPC"))
                    && job.getCurrentPassPercentageCompletion() >= WipeJob.MIN_PERCENTAGE_COMPLETION) {
                job.totalBytes = job.wipedBytes;
                Log.i(TAG, "No space left near end; counting pass as complete at " + job.getCurrentPassPercentageCompletion() + "%");
            } else {
                job.errorMessage = "Error while wiping: " + e;
                Log.e(TAG, job.errorMessage);
                return;
            }
        }

        job.wipedBytes = 0;
        emit(job);

        if (!job.verify) {
            job.passes_completed++;
            safeDelete(wipeFile);
            return;
        }

    job.verifying = true;
    Log.i(TAG, "Verifying pass data");
        try (InputStream is = new java.io.FileInputStream(wipeFile)) {
            Random rnd = new Random();
            rnd.setSeed(randomSeed);
            byte[] expected = new byte[WIPE_BUFFER_SIZE];
            byte[] actual = new byte[WIPE_BUFFER_SIZE];

            while (job.wipedBytes < job.totalBytes) {
                if (cancelToken.isCancelled()) break;

                long remaining = job.totalBytes - job.wipedBytes;
                int toRead = (int)Math.min(WIPE_BUFFER_SIZE, remaining);
                if (!job.isBlankingPass()) rnd.nextBytes(expected);

                int read = is.read(actual, 0, toRead);
                if (read != toRead) {
                    job.errorMessage = "Error while verifying wipe file: short read";
                    return;
                }

                if (!Arrays.equals(Arrays.copyOfRange(expected, 0, toRead), Arrays.copyOfRange(actual, 0, toRead))) {
                    job.errorMessage = "Error while verifying wipe file: streams are not the same!";
                    Log.e(TAG, job.errorMessage);
                    return;
                }

                job.wipedBytes += toRead;
                emit(job);
            }
        } catch (IOException e) {
            job.errorMessage = "Error while verifying wipe file: " + e;
            Log.e(TAG, job.errorMessage);
            return;
        } finally {
            job.verifying = false;
        }

        job.passes_completed++;
        safeDelete(wipeFile);
        Log.i(TAG, "Pass completed. Total passes completed: " + job.passes_completed);
    }

    private void cleanupWipeFiles(WipeJob job) {
    try {
            StorageInfo storageInfo = StorageManager.getStorageInfo(appContext);
            if (storageInfo.locations != null) {
                for (StorageLocation location : storageInfo.locations) {
                    if (location.directory != null && location.directory.exists()) {
                        // Limit to target path when applicable
                        if (job != null && job.targetPath != null) {
                            try {
                                File target = new File(job.targetPath);
                                if (!sameFile(target, location.directory)) {
                                    continue;
                                }
                            } catch (Exception ignored) { continue; }
                        }
                        File[] files = location.directory.listFiles();
                        if (files == null) continue;
                        for (File f : files) {
                            if (f.getName().startsWith(WIPE_FILES_PREFIX)) {
                                // best-effort delete
                                //noinspection ResultOfMethodCallIgnored
                                f.delete();
                Log.d(TAG, "Cleanup: deleted " + f.getAbsolutePath());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        Log.w(TAG, "Error cleaning wipe files: " + e.getMessage());
        }

        try {
            File filesDir = appContext.getFilesDir();
            String[] names = filesDir.list();
            if (names != null) {
                for (String name : names) {
                    if (name.startsWith(WIPE_FILES_PREFIX)) {
                        //noinspection ResultOfMethodCallIgnored
                        new File(filesDir, name).delete();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error cleaning internal wipe files: " + e.getMessage());
        }
    }

    private void safeDelete(File file) {
        try {
            if (file != null && file.exists()) {
                String path = file.getAbsolutePath();
                boolean ok = file.delete();
                if (ok) {
                    Log.i(TAG, "Deleted temp wipe file: " + path);
                } else if (file.exists()) {
                    Log.w(TAG, "Could not delete temp wipe file: " + path);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to delete wipe file: " + e.getMessage());
        }
    }

    private void emit(WipeJob job) {
        if (callback != null) callback.onProgress(job);
    }

    private boolean sameFile(File a, File b) {
        if (a == null || b == null) return false;
        try {
            String ca = a.getCanonicalPath();
            String cb = b.getCanonicalPath();
            return ca.equals(cb);
        } catch (IOException e) {
            return a.getAbsolutePath().equals(b.getAbsolutePath());
        }
    }
}
