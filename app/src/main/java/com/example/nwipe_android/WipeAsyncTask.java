package com.example.nwipe_android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

public class WipeAsyncTask extends AsyncTask <WipeJob, WipeJob, WipeJob> {
    private static final int WIPE_BUFFER_SIZE = 4096;
    private static final String WIPE_FILES_PREFIX = "nwipe-android-";

    @SuppressLint("StaticFieldLeak")
    private MainActivity mainActivity = null;
    public WipeJob wipeJob;
    private int lastProgress = -1;

    public WipeAsyncTask() {

    }

    public WipeAsyncTask(MainActivity mainActivity) {
        super();
        this.mainActivity = mainActivity;
    }

    @Override
    protected WipeJob doInBackground(WipeJob... wipeJobs) {
        this.wipeJob = wipeJobs[0];

        this.wipe();

        return this.wipeJob;
    }

    public void wipe() {
        this.deleteWipeFiles();

        while (!wipeJob.isCompleted()) {
            try {
                this.executeWipePass();
            } catch (Exception e) {
                this.deleteWipeFiles();
                wipeJob.errorMessage = String.format("Unknown error while wiping: %s", e.toString());
                this.updateJobStatus();
                return;
            }
            this.deleteWipeFiles();
            this.updateJobStatus();

            if (this.wipeJob.failed() || this.cancelled()) {
                return;
            }
        }
    }

    protected void deleteWipeFiles() {
        Context context = this.mainActivity.getApplicationContext();
        
        try {
            // Get all wipeable directories and clean up wipe files
            StorageInfo storageInfo = StorageManager.getStorageInfo(context);
            if (storageInfo.locations != null) {
                for (StorageLocation location : storageInfo.locations) {
                    if (location.directory != null && location.directory.exists()) {
                        try {
                            File[] files = location.directory.listFiles();
                            if (files != null) {
                                for (File file : files) {
                                    if (file.getName().startsWith(WipeAsyncTask.WIPE_FILES_PREFIX)) {
                                        // Log.i("WipeAsyncTask", String.format("Deleting old wipe file %s.", file.getName()));
                                        file.delete();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.w("WipeAsyncTask", "Failed to clean up files in " + location.displayName + ": " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("WipeAsyncTask", "Error during wipe file cleanup: " + e.getMessage());
        }
        
        // Also clean up internal files directory (legacy)
        try {
            File filesDir = context.getFilesDir();
            String[] fileNames = filesDir.list();
            if (fileNames != null) {
                for (String fileName : fileNames) {
                    if (fileName.startsWith(WipeAsyncTask.WIPE_FILES_PREFIX)) {
                        context.deleteFile(fileName);
                    }
                }
            }
        } catch (Exception e) {
            Log.w("WipeAsyncTask", "Failed to clean up internal files: " + e.getMessage());
        }
    }

    protected OutputStream getOutputStream(String fileName) throws FileNotFoundException {
        Context context = this.mainActivity.getApplicationContext();
        return context.openFileOutput(fileName, Context.MODE_PRIVATE);
    }

    protected InputStream getInputStream(String fileName) throws FileNotFoundException {
        Context context = this.mainActivity.getApplicationContext();
        return context.openFileInput(fileName);
    }

    protected void updateJobStatus() {
        this.publishProgress(this.wipeJob);
    }

    protected boolean cancelled() {
        return this.isCancelled();
    }

    public void executeWipePass() {
        // Get storage information using StorageManager
        StorageInfo storageInfo = StorageManager.getStorageInfo(this.mainActivity.getApplicationContext());
        
        if (!storageInfo.isValid()) {
            wipeJob.errorMessage = "Cannot access storage: " + storageInfo.errorMessage;
            return;
        }

        if (!storageInfo.hasPermissions) {
            wipeJob.errorMessage = "Storage permissions not granted. Cannot proceed with wiping.";
            return;
        }

        long availableBytesCount = storageInfo.availableBytes;
        // Log.i("MainActivity", String.format("Got %d bytes available for writing.", availableBytesCount));
        this.wipeJob.totalBytes = availableBytesCount;
        this.wipeJob.wipedBytes = 0;
        this.updateJobStatus();

        // Try to wipe multiple storage locations
        boolean wipeSuccessful = false;
        String lastError = "";

        for (StorageLocation location : storageInfo.locations) {
            if (location.isUsable() && !cancelled()) {
                // If a specific target is set, skip non-matching directories
                if (wipeJob.targetPath != null) {
                    try {
                        java.io.File target = new java.io.File(wipeJob.targetPath);
                        if (!target.getCanonicalPath().equals(location.directory.getCanonicalPath())) {
                            continue;
                        }
                    } catch (Exception ignored) { continue; }
                }
                try {
                    wipeStorageLocation(location);
                    wipeSuccessful = true;
                    break; // Successfully wiped one location, that's enough for this pass
                } catch (Exception e) {
                    lastError = e.getMessage();
                    Log.w("WipeAsyncTask", "Failed to wipe location " + location.displayName + ": " + e.getMessage());
                    continue; // Try next location
                }
            }
        }

        if (!wipeSuccessful) {
            wipeJob.errorMessage = "Failed to wipe any storage location. Last error: " + lastError;
            return;
        }
    }

    private void wipeStorageLocation(StorageLocation location) throws Exception {
        String wipeFileName = String.format("%s%d_%s", WIPE_FILES_PREFIX, System.currentTimeMillis(), 
                                           location.type.name().toLowerCase());

        SecureRandom random = new SecureRandom();
        int randomSeed = random.nextInt();

        // Log.i("WipeAsyncTask", "Starting wipe operation on " + location.displayName);
        
        // Create wipe file in the specific location
        File wipeFile = new File(location.directory, wipeFileName);
        
        try (OutputStream fos = new java.io.FileOutputStream(wipeFile)) {
            Random rnd = new Random();
            rnd.setSeed(randomSeed);
            byte[] bytesBuffer = new byte[WIPE_BUFFER_SIZE];

            while (this.wipeJob.wipedBytes < this.wipeJob.totalBytes) {

                long bytesLeftToWrite = this.wipeJob.totalBytes - this.wipeJob.wipedBytes;
                int bytesToWriteCount = WIPE_BUFFER_SIZE;
                if (bytesLeftToWrite < WIPE_BUFFER_SIZE) {
                    // No risk of overflow here since we just verified the size.
                    bytesToWriteCount = (int)bytesLeftToWrite;
                }

                if (!wipeJob.isBlankingPass()) {
                    rnd.nextBytes(bytesBuffer);
                }

                fos.write(bytesBuffer, 0, bytesToWriteCount);

                this.wipeJob.wipedBytes += bytesToWriteCount;
                this.updateJobStatus();

                if (cancelled()) {
                    break;
                }
            }
        } catch (IOException e) {
            // Handling no space left errors at the end of the pass.
            if (e.toString().contains("ENOSP") && wipeJob.getCurrentPassPercentageCompletion() >= WipeJob.MIN_PERCENTAGE_COMPLETION) {
                this.wipeJob.totalBytes = this.wipeJob.wipedBytes;
            } else {
                wipeJob.errorMessage = String.format("Error while wiping: %s", e.toString());
                // Log.e("WipeAsyncTask", wipeJob.errorMessage);
                return;
            }
        }

        this.wipeJob.wipedBytes = 0;
        this.updateJobStatus();

        if (!wipeJob.verify) {
            wipeJob.passes_completed++;
            // Clean up the wipe file
            try {
                if (wipeFile.exists()) {
                    wipeFile.delete();
                }
            } catch (Exception e) {
                Log.w("WipeAsyncTask", "Failed to delete wipe file: " + e.getMessage());
            }
            return;
        }

        this.wipeJob.verifying = true;

        // Log.i("WipeAsyncTask", "Starting verifying operation.");
        try (InputStream fis = new java.io.FileInputStream(wipeFile)) {
            Random rnd = new Random();
            rnd.setSeed(randomSeed);
            byte[] bytesBuffer = new byte[WIPE_BUFFER_SIZE];
            byte[] bytesInputBuffer = new byte[WIPE_BUFFER_SIZE];

            while (this.wipeJob.wipedBytes < this.wipeJob.totalBytes) {

                long bytesLeftToRead = this.wipeJob.totalBytes - this.wipeJob.wipedBytes;
                int bytesToReadCount = WIPE_BUFFER_SIZE;
                if (bytesLeftToRead < WIPE_BUFFER_SIZE) {
                    bytesToReadCount = (int)bytesLeftToRead;
                }

                if (!wipeJob.isBlankingPass()) {
                    rnd.nextBytes(bytesBuffer);
                }

                fis.read(bytesInputBuffer, 0, bytesToReadCount);


                if (!Arrays.equals(Arrays.copyOfRange(bytesBuffer, 0, bytesToReadCount), Arrays.copyOfRange(bytesInputBuffer, 0, bytesToReadCount))) {
                    wipeJob.errorMessage = "Error while verifying wipe file: streams are not the same!";
                    // Log.e("WipeAsyncTask", wipeJob.errorMessage);
                    return;
                }

                this.wipeJob.wipedBytes += bytesToReadCount;
                this.updateJobStatus();

                if (cancelled()) {
                    break;
                }
            }
        } catch (IOException e) {
            wipeJob.errorMessage = String.format("Error while verifying wipe file: %s", e.toString());
            // Log.e("WipeAsyncTask", wipeJob.errorMessage);
            throw new Exception("Verification failed: " + e.getMessage());
        }

        this.wipeJob.verifying = false;
        wipeJob.passes_completed++;
        
        // Clean up the wipe file
        try {
            if (wipeFile.exists()) {
                wipeFile.delete();
            }
        } catch (Exception e) {
            Log.w("WipeAsyncTask", "Failed to delete wipe file after verification: " + e.getMessage());
        }
    }

    protected void onProgressUpdate(WipeJob... wipeJobs) {
        if (lastProgress != -1 && lastProgress == wipeJobs[0].getCurrentPassPercentageCompletion() && !wipeJobs[0].failed()) {
            return;
        }
        lastProgress = wipeJobs[0].getCurrentPassPercentageCompletion();
        this.mainActivity.setWipeProgress(wipeJobs[0]);
    }

    protected void onPostExecute(WipeJob result) {
        this.mainActivity.onWipeFinished(result);
    }

    /**
     * This function is required when mocking the class during
     * unit testing.
     */
    protected long getAvailableBytesCountInternal() {
        return WipeAsyncTask.getAvailableBytesCount(this.mainActivity.getApplicationContext());
    }

    /*
     * Gets the total number of bytes available for writing using StorageManager.
     */
    public static long getAvailableBytesCount(Context context) {
        try {
            StorageInfo storageInfo = StorageManager.getStorageInfo(context);
            if (storageInfo.isValid()) {
                return storageInfo.availableBytes;
            } else {
                // Fallback to internal storage if StorageManager fails
                return getInternalAvailableBytesCount();
            }
        } catch (Exception e) {
            Log.e("WipeAsyncTask", "Error getting available bytes: " + e.getMessage());
            return getInternalAvailableBytesCount();
        }
    }

    /*
     * Gets the total number of bytes using StorageManager.
     */
    public static long getTotalBytesCount(Context context) {
        try {
            StorageInfo storageInfo = StorageManager.getStorageInfo(context);
            if (storageInfo.isValid()) {
                return storageInfo.totalBytes;
            } else {
                // Fallback to internal storage if StorageManager fails
                return getInternalTotalBytesCount();
            }
        } catch (Exception e) {
            Log.e("WipeAsyncTask", "Error getting total bytes: " + e.getMessage());
            return getInternalTotalBytesCount();
        }
    }

    /*
     * Fallback method for internal storage only
     */
    private static long getInternalAvailableBytesCount() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        return availableBlocks * blockSize;
    }

    /*
     * Fallback method for internal storage only
     */
    private static long getInternalTotalBytesCount() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        return totalBlocks * blockSize;
    }

    public static String getTextualAvailableMemory(Context context) {
        long totalBytes = WipeAsyncTask.getAvailableBytesCount(context);
        return formatBytes(totalBytes);
    }

    public static String getTextualTotalMemory(Context context) {
        long totalBytes = WipeAsyncTask.getTotalBytesCount(context);
        return formatBytes(totalBytes);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return String.format("%d bytes", bytes);
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
