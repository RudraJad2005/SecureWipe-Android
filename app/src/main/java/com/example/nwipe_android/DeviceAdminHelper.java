package com.example.nwipe_android;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

/**
 * Backend utility to manage Device Admin enabling and optional factory reset.
 * No UI screens are added; call from existing UI when needed.
 */
public class DeviceAdminHelper {

    public static final int REQUEST_ENABLE = 2001;

    public static boolean isEnabled(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, SecureWipeDeviceAdminReceiver.class);
        return dpm != null && dpm.isAdminActive(admin);
    }

    public static void requestEnable(Activity activity) {
        ComponentName admin = new ComponentName(activity, SecureWipeDeviceAdminReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Grant admin to allow secure factory reset after wiping.");
        activity.startActivityForResult(intent, REQUEST_ENABLE);
    }

    public static boolean factoryReset(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(context, SecureWipeDeviceAdminReceiver.class);
            if (dpm == null || !dpm.isAdminActive(admin)) {
                Toast.makeText(context, "Device admin not enabled", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE | DevicePolicyManager.WIPE_RESET_PROTECTION_DATA);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE);
            } else {
                dpm.wipeData(0);
            }
            return true;
        } catch (SecurityException se) {
            Toast.makeText(context, "Factory reset not permitted", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
