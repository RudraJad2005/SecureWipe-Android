package com.example.nwipe_android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.ref.WeakReference;

import static android.content.Intent.ACTION_POWER_CONNECTED;
import static android.content.Intent.ACTION_POWER_DISCONNECTED;

public class PowerBroadcastReceiver extends BroadcastReceiver {
    private WeakReference<MainActivity> activityRef;

    public PowerBroadcastReceiver(MainActivity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        MainActivity mainActivity = activityRef.get();
        
        if (mainActivity == null) {
            Log.w("PowerBroadcastReceiver", "MainActivity reference is null, cannot handle power event");
            return;
        }

        String action = intent.getAction();
        if (action == null) {
            Log.w("PowerBroadcastReceiver", "Received intent with null action");
            return;
        }

        try {
            if (action.equals(ACTION_POWER_DISCONNECTED)) {
                Log.i("PowerBroadcastReceiver", "Power disconnected");
                mainActivity.showPowerDisconnectedMessage();
            } else if (action.equals(ACTION_POWER_CONNECTED)) {
                Log.i("PowerBroadcastReceiver", "Power connected");
                mainActivity.clearPowerDisconnectedMessage();
            }
        } catch (Exception e) {
            Log.e("PowerBroadcastReceiver", "Error handling power event: " + e.getMessage());
        }
    }
}
