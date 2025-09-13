package com.example.nwipe_android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Foreground service that runs secure wiping using WipeEngine and emits progress broadcasts.
 */
public class WipeService extends Service implements WipeEngine.Callback {

    public static final String ACTION_START = "com.example.nwipe_android.action.START_WIPE";
    public static final String ACTION_CANCEL = "com.example.nwipe_android.action.CANCEL_WIPE";
    public static final String ACTION_PROGRESS = "com.example.nwipe_android.action.WIPE_PROGRESS";
    public static final String EXTRA_PASSES = "passes";
    public static final String EXTRA_VERIFY = "verify";
    public static final String EXTRA_BLANK = "blank";
    public static final String EXTRA_TARGET_PATH = "target_path";
    public static final String EXTRA_TARGET_NAME = "target_name";
    public static final String EXTRA_TARGET_FOLDERS = "target_folders"; // ArrayList<String>

    private static final String CHANNEL_ID = "wipe_channel";
    private static final int NOTIFICATION_ID = 42;
    private static final String TAG = "SecureWipe";

    private Thread worker;
    private WipeEngine.CancelToken cancelToken;
    private int lastLoggedPercent = -1;

    public static Intent createStartIntent(Context context, int passes, boolean verify, boolean blank) {
        Intent i = new Intent(context, WipeService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_PASSES, passes);
        i.putExtra(EXTRA_VERIFY, verify);
        i.putExtra(EXTRA_BLANK, blank);
        return i;
    }

    public static Intent createStartIntent(Context context, int passes, boolean verify, boolean blank, String targetPath, String targetName) {
        Intent i = createStartIntent(context, passes, verify, blank);
        if (targetPath != null) i.putExtra(EXTRA_TARGET_PATH, targetPath);
        if (targetName != null) i.putExtra(EXTRA_TARGET_NAME, targetName);
        return i;
    }

    public static Intent createStartIntent(Context context, int passes, boolean verify, boolean blank, java.util.ArrayList<String> targetFolders, String targetName) {
        Intent i = createStartIntent(context, passes, verify, blank);
        if (targetFolders != null && !targetFolders.isEmpty()) i.putStringArrayListExtra(EXTRA_TARGET_FOLDERS, targetFolders);
        if (targetName != null) i.putExtra(EXTRA_TARGET_NAME, targetName);
        return i;
    }

    public static Intent createCancelIntent(Context context) {
        Intent i = new Intent(context, WipeService.class);
        i.setAction(ACTION_CANCEL);
        return i;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    Log.i(TAG, "WipeService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            Log.i(TAG, "Cancel intent received");
            if (cancelToken != null) cancelToken.cancel();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            if (worker != null && worker.isAlive()) {
                Log.i(TAG, "Wipe already in progress; ignoring duplicate start");
                return START_STICKY;
            }
            int passes = intent.getIntExtra(EXTRA_PASSES, WipeJob.DEFAULT_NUMBER_PASSES);
            boolean verify = intent.getBooleanExtra(EXTRA_VERIFY, WipeJob.DEFAULT_VERIFY);
            boolean blank = intent.getBooleanExtra(EXTRA_BLANK, WipeJob.DEFAULT_BLANK);
            String targetPath = intent.getStringExtra(EXTRA_TARGET_PATH);
            String targetName = intent.getStringExtra(EXTRA_TARGET_NAME);
            java.util.ArrayList<String> targetFolders = intent.getStringArrayListExtra(EXTRA_TARGET_FOLDERS);

            Log.i(TAG, "Starting wipe job: passes=" + passes + ", verify=" + verify + ", blank=" + blank
                + (targetFolders != null && !targetFolders.isEmpty() ? (", folders=" + targetFolders.size())
                : (targetPath != null ? (", target='" + (targetName != null ? targetName : targetPath) + "'") : "")));
            startInForeground();
            cancelToken = new WipeEngine.CancelToken();

            worker = new Thread(() -> {
                WipeJob job = new WipeJob();
                job.number_passes = passes;
                job.verify = verify;
                job.blank = blank;
                job.targetPath = targetPath;
                job.targetName = targetName;
                if (targetFolders != null) job.targetFolders = targetFolders;
                new WipeEngine(getApplicationContext(), this, cancelToken).execute(job);
                // final broadcast to notify completion or failure
                sendProgressBroadcast(job);
                Log.i(TAG, "Wipe finished: status=" + (job.failed() ? ("FAILED: " + job.errorMessage) : "SUCCESS"));
                stopForeground(true);
                stopSelf();
            }, "WipeWorker");
            worker.start();
            return START_STICKY;
        }
        return START_NOT_STICKY;
    }

    private void startInForeground() {
        Intent tapIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(this, 0, tapIntent, flags);

    Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Secure wipe in progress")
                .setContentText("Overwriting storage for secure deletion")
        .setSmallIcon(R.drawable.ic_info_24)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    Log.i(TAG, "Foreground notification started (dataSync)");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Secure Wipe", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onProgress(WipeJob job) {
    sendProgressBroadcast(job);
    // Throttle logs to every 10%
    int pct = job.getCurrentPassPercentageCompletion();
    if (pct >= 0 && (lastLoggedPercent == -1 || pct - lastLoggedPercent >= 10)) {
        Log.d(TAG, "Progress: pass " + (job.passes_completed + 1) + "/" + (job.blank ? job.number_passes + 1 : job.number_passes)
            + (job.verifying ? " (verifying)" : "") + ", " + pct + "%");
        lastLoggedPercent = pct;
    }
    }

    @Override
    public void onMessage(String message) { /* reserved for future */ }

    private void sendProgressBroadcast(WipeJob job) {
    Intent i = new Intent(ACTION_PROGRESS);
    i.setPackage(getPackageName());
        i.putExtra("passes_completed", job.passes_completed);
        i.putExtra("total_bytes", job.totalBytes);
        i.putExtra("wiped_bytes", job.wipedBytes);
        i.putExtra("verifying", job.verifying);
    i.putExtra("error", job.errorMessage);
    i.putExtra("target_path", job.targetPath);
    i.putExtra("target_name", job.targetName);
        sendBroadcast(i);
    }
}
