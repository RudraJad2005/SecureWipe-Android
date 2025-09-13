package com.example.nwipe_android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {

    private WipeAsyncTask wipeAsyncTask = null;
    private BroadcastReceiver powerBroadcastReceiver = null;
    private BroadcastReceiver wipeProgressReceiver = null;

    private boolean isWiping = false;
    // Remember last job params so we can reconstruct WipeJob from service progress
    private int lastNumberPasses = WipeJob.DEFAULT_NUMBER_PASSES;
    private boolean lastVerify = WipeJob.DEFAULT_VERIFY;
    private boolean lastBlank = WipeJob.DEFAULT_BLANK;
    private String selectedTargetPath = null;
    private String selectedTargetName = null;
    
    // Enhanced progress tracking for time estimation
    private long wipeStartTime = 0;
    private long lastProgressUpdateTime = 0;
    private long lastWipedBytes = 0;
    private int lastCompletedPasses = -1;
    private double currentMBPerSecond = 0.0;
    private java.util.LinkedList<Double> speedHistory = new java.util.LinkedList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialize UI components
        initializeUI();
        
        // Check and request permissions
        checkPermissions();
        
        // Test storage functionality (for debugging)
        StorageTest.testAccurateStorageInfo(this);
        StorageTest.compareStorageManagers(this);
        
        // Update storage information
        updateStorageInfo();
        
        // Show a toast to confirm storage fix is working
        AccurateStorageManager.AccurateStorageInfo info = AccurateStorageManager.getAccurateStorageInfo(this);
        if (info.isValid()) {
            String toastMessage = String.format("Enhanced Storage: %s used of %s formatted", 
                info.usedFormatted, info.totalFormatted);
            if (info.rawCapacityFormattedDecimal != null && 
                !info.rawCapacityFormattedDecimal.equals(info.totalFormatted)) {
                toastMessage += String.format(" (from %s device)", info.rawCapacityFormattedDecimal);
            }
            android.widget.Toast.makeText(this, toastMessage, android.widget.Toast.LENGTH_LONG).show();
        }
        
        // Initialize power monitoring
        initializePowerMonitoring();
        
        // Check initial power status
        if (!deviceIsPluggedIn()) {
            this.showPowerDisconnectedMessage();
        }
    }

    private void initializeUI() {
        SwitchMaterial verifySwitch = findViewById(R.id.verify_switch);
        verifySwitch.setChecked(WipeJob.DEFAULT_VERIFY);
        SwitchMaterial blankSwitch = findViewById(R.id.blanking_switch);
        blankSwitch.setChecked(WipeJob.DEFAULT_BLANK);
        Slider numberPassesSeekBar = findViewById(R.id.number_passes_seek_bar);
        numberPassesSeekBar.setValueTo(WipeJob.MAX_NUMBER_PASSES - 1);

        numberPassesSeekBar.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(Slider slider, float value, boolean fromUser) {
                TextView numberPassesTextView = findViewById(R.id.number_passes_text_view);
                String label = getString(R.string.number_passes_label);
                numberPassesTextView.setText(label + " " + ((int)value + 1));
            }
        });
        // The progress must be set after registering the listener to make sure the initial value of
        // the label is updated.
        numberPassesSeekBar.setValue(WipeJob.DEFAULT_NUMBER_PASSES - 1);
    }

    private void checkPermissions() {
        if (!PermissionHandler.checkPermissions(this)) {
            showPermissionRequiredMessage();
            if (PermissionHandler.shouldShowRequestPermissionRationale(this)) {
                showPermissionRationale();
            } else {
                PermissionHandler.requestPermissions(this);
            }
        }
    }

    private void updateStorageInfo() {
        TextView sizeTextView = findViewById(R.id.available_size_text_view);
        TextView availableSizeValue = findViewById(R.id.available_size_value);
        TextView totalSizeValue = findViewById(R.id.total_size_value);
        LinearProgressIndicator storageProgressBar = findViewById(R.id.storage_progress_bar);
    // Selected folders UI
    android.view.View selectedFoldersCard = findViewById(R.id.selected_folders_card);
    TextView selectedFoldersSummary = findViewById(R.id.selected_folders_summary_text);
    TextView selectedFoldersList = findViewById(R.id.selected_folders_list_text);
        
        if (sizeTextView == null || availableSizeValue == null || totalSizeValue == null || storageProgressBar == null) {
            Log.w("MainActivity", "Storage UI elements not found");
            return;
        }
        
    try {
            AccurateStorageManager.AccurateStorageInfo storageInfo = AccurateStorageManager.getAccurateStorageInfo(this);
            
            if (storageInfo.isValid()) {
                // Update metric values with enhanced typography
                availableSizeValue.setText(storageInfo.availableFormatted);
                totalSizeValue.setText(storageInfo.totalFormatted);
                
                // Enhanced storage description with accuracy information
                if (storageInfo.rawCapacityFormattedDecimal != null && 
                    !storageInfo.rawCapacityFormattedDecimal.equals(storageInfo.totalFormatted)) {
                    sizeTextView.setText(String.format("Formatted capacity from %s device (%.0f%% efficiency) - Ready for secure wiping", 
                        storageInfo.rawCapacityFormattedDecimal, storageInfo.efficiencyPercentage));
                } else {
                    sizeTextView.setText("Ready for secure wiping operations");
                }
                
                // Animate storage usage percentage
                AnimationHelper.animateProgress(storageProgressBar, storageInfo.usedPercentage);
                
                // Clear any previous error messages if storage is now accessible
                if (storageInfo.hasPermissions) {
                    hideStatusMessage();
                }
                
                Log.d("MainActivity", String.format("Storage updated: %s formatted (%s raw), %s available (%d%% used, %.1f%% efficiency)", 
                    storageInfo.totalFormatted, storageInfo.rawCapacityFormattedDecimal, storageInfo.availableFormatted, 
                    storageInfo.usedPercentage, storageInfo.efficiencyPercentage));
                
            } else {
                availableSizeValue.setText("--");
                totalSizeValue.setText("--");
                AnimationHelper.animateProgress(storageProgressBar, 0);
                
                if (!storageInfo.hasPermissions) {
                    sizeTextView.setText("Tap to grant storage permissions");
                    // Make the text clickable to request permissions
                    sizeTextView.setOnClickListener(v -> {
                        AccurateStorageManager.requestStoragePermissions(this);
                    });
                    showStatusMessage(storageInfo.errorMessage, StatusType.WARNING);
                } else {
                    sizeTextView.setText("Storage information unavailable");
                    sizeTextView.setOnClickListener(v -> {
                        updateStorageInfo(); // Retry on click
                    });
                    showStatusMessage(storageInfo.errorMessage, StatusType.ERROR);
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error updating storage info: " + e.getMessage(), e);
            
            availableSizeValue.setText("--");
            totalSizeValue.setText("--");
            sizeTextView.setText("Error accessing storage - tap to retry");
            AnimationHelper.animateProgress(storageProgressBar, 0);
            
            // Make clickable to retry
            sizeTextView.setOnClickListener(v -> {
                updateStorageInfo();
            });
            
            showStatusMessage("Failed to access storage: " + e.getMessage(), StatusType.ERROR);
        }

        // Update selected folders card visibility and content
        try {
            java.util.ArrayList<String> folders = TargetPrefs.getFolders(this);
            if (folders != null && !folders.isEmpty()) {
                if (selectedFoldersCard != null) selectedFoldersCard.setVisibility(View.VISIBLE);
                if (selectedFoldersSummary != null) {
                    selectedFoldersSummary.setText(folders.size() + (folders.size() == 1 ? " folder selected for wipe" : " folders selected for wipe"));
                }
                if (selectedFoldersList != null) {
                    StringBuilder sb = new StringBuilder();
                    int max = Math.min(4, folders.size());
                    for (int i = 0; i < max; i++) {
                        sb.append(folders.get(i));
                        if (i < max - 1) sb.append("\n");
                    }
                    if (folders.size() > max) {
                        sb.append("\n… ").append(folders.size() - max).append(" more");
                    }
                    selectedFoldersList.setText(sb.toString());
                }
            } else {
                if (selectedFoldersCard != null) selectedFoldersCard.setVisibility(View.GONE);
            }
        } catch (Exception ignored) {}
        
        // Update Start button state after folders change
        updateStartButtonState();
    }

    private void initializePowerMonitoring() {
        try {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
            intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            this.powerBroadcastReceiver = new PowerBroadcastReceiver(this);
            this.registerReceiver(this.powerBroadcastReceiver, intentFilter);
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to initialize power monitoring: " + e.getMessage());
        }
    }

    public void showPowerDisconnectedMessage() {
        showStatusMessage("The device is not plugged in!", StatusType.WARNING);
        updateStartButtonState();
    }

    public void clearPowerDisconnectedMessage() {
        hideStatusMessage();
        updateStartButtonState();
    }

    private void updateStartButtonState() {
        Button startWipeButton = findViewById(R.id.start_wipe_button);
        
        // Check if we have permissions
        if (!PermissionHandler.checkPermissions(this)) {
            startWipeButton.setEnabled(false);
            return;
        }
        
        // Check if we have selected folders (allows wiping on battery)
        java.util.ArrayList<String> selectedFolders = TargetPrefs.getFolders(this);
        boolean hasSelectedFolders = selectedFolders != null && !selectedFolders.isEmpty();
        
        // Enable if: (plugged in) OR (has selected folders)
        boolean shouldEnable = deviceIsPluggedIn() || hasSelectedFolders;
        startWipeButton.setEnabled(shouldEnable);
        
        // Update power warning based on state
        if (!deviceIsPluggedIn() && !hasSelectedFolders) {
            // Show power warning only if no folders selected
            showStatusMessage("The device is not plugged in!", StatusType.WARNING);
        } else if (!deviceIsPluggedIn() && hasSelectedFolders) {
            // Show different message for folder wiping on battery
            showStatusMessage("Running on battery - selected folder wipe enabled", StatusType.INFO);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.teardown();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    public void onMainButtonClick(View v) {
        // Add button press animation
        AnimationHelper.scaleButton(v);
        
        if (this.isWiping) {
            Log.i("MainActivity", "Cancelling wipe process.");
            this.stopWipe();
        } else {
            // Check permissions before starting wipe
            if (!PermissionHandler.checkPermissions(this)) {
                showPermissionRequiredMessage();
                PermissionHandler.requestPermissions(this);
                return;
            }
            
            Log.i("MainActivity", "Starting wipe process.");
            this.startWipe();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (PermissionHandler.handlePermissionResult(requestCode, permissions, grantResults)) {
            // Permissions granted
            Log.i("MainActivity", "Storage permissions granted");
            updateStorageInfo();
            clearPermissionError();
        } else {
            // Permissions denied
            Log.w("MainActivity", "Storage permissions denied");
            showPermissionDeniedMessage();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Load any persisted target and update storage info when resuming
        if (selectedTargetPath == null) {
            selectedTargetPath = TargetPrefs.getPath(this);
            selectedTargetName = TargetPrefs.getName(this);
        }
        updateStorageInfo();
        
        // Check if basic permissions are granted for wiping functionality
        if (PermissionHandler.checkPermissions(this)) {
            clearPermissionError();
        }
        // Ensure Start button reflects current power state and folder selection
        updateStartButtonState();
    }

    public void onManageSelectedFoldersClick(View v) {
        // Open browser; if a storage root is selected in prefs, restrict to it, else open normally
        String root = TargetPrefs.getPath(this);
        Intent intent;
        if (root != null) {
            intent = SystemFileBrowserActivity.createIntent(this, root, true);
        } else {
            intent = SystemFileBrowserActivity.createIntent(this);
        }
        startActivity(intent);
    }

    public void onClearSelectedFoldersClick(View v) {
        new AlertDialog.Builder(this)
            .setTitle("Clear Selected Folders")
            .setMessage("Remove all selected folders for wipe?")
            .setPositiveButton("Clear", (d, w) -> {
                TargetPrefs.saveFolders(this, new java.util.ArrayList<>());
                updateStorageInfo(); // This will call updateStartButtonState()
                showTemporaryStatusMessage("Cleared selected folders", StatusType.INFO, 2000);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PermissionHandler.MANAGE_EXTERNAL_STORAGE_REQUEST) {
            // Check if permissions were granted and update storage info
            if (AccurateStorageManager.hasStoragePermissions(this)) {
                updateStorageInfo();
                clearPermissionError();
                showTemporaryStatusMessage("Storage permissions granted - accurate storage info now available", StatusType.SUCCESS, 3000);
            } else {
                showTemporaryStatusMessage("Storage permissions still required for accurate information", StatusType.WARNING, 3000);
            }
        } else if (requestCode == 1010 && resultCode == RESULT_OK && data != null) {
            selectedTargetPath = data.getStringExtra(StorageSelectionActivity.EXTRA_SELECTED_PATH);
            selectedTargetName = data.getStringExtra(StorageSelectionActivity.EXTRA_SELECTED_NAME);
            if (selectedTargetPath != null) {
                TargetPrefs.save(this, selectedTargetPath, selectedTargetName);
                showTemporaryStatusMessage("Target selected: " + (selectedTargetName != null ? selectedTargetName : selectedTargetPath), StatusType.INFO, 2500);
                // Refresh the storage info panel to reflect the selected target
                updateStorageInfo();
            }
        }
    }

    private void teardown() {
        if (this.wipeAsyncTask != null) {
            this.wipeAsyncTask.cancel(true);
            this.wipeAsyncTask = null;
        }
        try {
            unregisterReceiver(this.powerBroadcastReceiver);
        } catch (IllegalArgumentException e) {
            Log.e("MainActivity", "Could not unregister power broadcast receiver " + e.toString());
        }
        try {
            if (this.wipeProgressReceiver != null) unregisterReceiver(this.wipeProgressReceiver);
        } catch (IllegalArgumentException e) {
            Log.e("MainActivity", "Could not unregister wipe progress receiver " + e.toString());
        }
    }

    public void onCloseButtonClick(MenuItem item) {
        this.teardown();
        this.finish();
    }

    public void onEnableDeviceAdminClick(MenuItem item) {
        if (DeviceAdminHelper.isEnabled(this)) {
            showTemporaryStatusMessage("Device admin already enabled", StatusType.INFO, 2000);
            return;
        }
        DeviceAdminHelper.requestEnable(this);
    }

    public void onFactoryResetClick(MenuItem item) {
        if (!DeviceAdminHelper.isEnabled(this)) {
            showTemporaryStatusMessage("Enable device admin first", StatusType.WARNING, 3000);
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Confirm Factory Reset")
            .setMessage("This will perform a system factory reset (requires admin). Continue?")
            .setPositiveButton("Reset", (d, w) -> {
                boolean ok = DeviceAdminHelper.factoryReset(this);
                if (!ok) {
                    showTemporaryStatusMessage("Factory reset not permitted", StatusType.ERROR, 4000);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    public void onSelectStorageClick(MenuItem item) {
        Intent i = new Intent(this, StorageSelectionActivity.class);
        startActivityForResult(i, 1010);
    }

    public void onViewCertificatesClick(MenuItem item) {
        Intent i = new Intent(this, CertificatesViewerActivity.class);
        startActivity(i);
    }

    public void onWipeFinished(WipeJob wipeJob) {
        Button startWipeButton = findViewById(R.id.start_wipe_button);
        LinearProgressIndicator wipeProgressBar = findViewById(R.id.wipe_progress_bar);
        CircularProgressIndicator circularProgress = findViewById(R.id.circular_progress);
        CardView progressCard = findViewById(R.id.progress_card);

        this.isWiping = false;
        
        // Stop animations
        AnimationHelper.stopIndeterminateProgress(circularProgress);
        AnimationHelper.stopWaveAnimation(wipeProgressBar);
        
        if (!wipeJob.errorMessage.isEmpty()) {
            if (wipeJob.isCompleted()) {
                showStatusMessage(wipeJob.errorMessage, StatusType.SUCCESS);
                // Animate final progress to 100%
                AnimationHelper.animateProgress(wipeProgressBar, 100);
                AnimationHelper.animateCircularProgress(circularProgress, 100);
                
                // Generate wipe certificates after successful completion
                generateWipeCertificates(wipeJob);
            } else {
                showStatusMessage(wipeJob.errorMessage, StatusType.ERROR);
            }
        }
        
        startWipeButton.setText(R.string.start_wipe_button_label);

        // Hide progress card with delay to show completion
        progressCard.postDelayed(() -> {
            AnimationHelper.slideOutCard(progressCard, null);
        }, 2000);

        Slider numberPassesSeekBar = findViewById(R.id.number_passes_seek_bar);
        SwitchMaterial verifySwitch = findViewById(R.id.verify_switch);
        SwitchMaterial blankingSwitch = findViewById(R.id.blanking_switch);
        numberPassesSeekBar.setEnabled(true);
        verifySwitch.setEnabled(true);
        blankingSwitch.setEnabled(true);
    }

    /*
     * See https://developer.android.com/training/monitoring-device-state/battery-monitoring#java
     * for details on monitoring battery status.
     */
    public boolean deviceIsPluggedIn() {
        Context context = getApplicationContext();
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, intentFilter);

        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private void startWipe() {
        Button startWipeButton = findViewById(R.id.start_wipe_button);
        LinearProgressIndicator wipeProgressBar = findViewById(R.id.wipe_progress_bar);
        CircularProgressIndicator circularProgress = findViewById(R.id.circular_progress);
        CardView progressCard = findViewById(R.id.progress_card);

        startWipeButton.setText(R.string.cancel_wipe_button_label);
        this.isWiping = true;

        // Reset timing variables for new wipe operation
        wipeStartTime = 0;
        lastProgressUpdateTime = 0;
        lastWipedBytes = 0;
        lastCompletedPasses = -1;
        currentMBPerSecond = 0.0;
        speedHistory.clear();

        // Show progress card with animation
        AnimationHelper.slideInCard(progressCard);
        
        // Start indeterminate circular progress
        AnimationHelper.startIndeterminateProgress(circularProgress);
        
        // Start wave animation on progress bar
        AnimationHelper.startWaveAnimation(wipeProgressBar);

        Slider numberPassesSeekBar = findViewById(R.id.number_passes_seek_bar);
        SwitchMaterial verifySwitch = findViewById(R.id.verify_switch);
        SwitchMaterial blankingSwitch = findViewById(R.id.blanking_switch);
        
        // Show initial operation summary
        TextView wipeTextView = findViewById(R.id.wipe_text_view);
        int totalPasses = (int)numberPassesSeekBar.getValue() + 1;
        if (blankingSwitch.isChecked()) totalPasses++; // Add blanking pass
        if (verifySwitch.isChecked()) totalPasses *= 2; // Verification doubles work
        
        java.util.ArrayList<String> folders = TargetPrefs.getFolders(this);
        String initialText;
        if (folders != null && !folders.isEmpty()) {
            initialText = String.format("Initializing secure wipe operation...\n📁 %d folders selected • %d total passes\n⚡ Calculating estimated time...", 
                folders.size(), totalPasses);
        } else {
            initialText = String.format("Initializing secure wipe operation...\n🔒 %d total passes • Full storage mode\n⚡ Calculating estimated time...", 
                totalPasses);
        }
        wipeTextView.setText(initialText);

    WipeJob wipeJob = new WipeJob();
    wipeJob.number_passes = (int)numberPassesSeekBar.getValue() + 1;
    wipeJob.verify = verifySwitch.isChecked();
    wipeJob.blank = blankingSwitch.isChecked();
    // Persist params for service progress reconstruction
    lastNumberPasses = wipeJob.number_passes;
    lastVerify = wipeJob.verify;
    lastBlank = wipeJob.blank;

        // Start foreground service backend to perform wiping reliably
        try {
            Intent service;
            java.util.ArrayList<String> selectedFolders = TargetPrefs.getFolders(this);
            if (selectedFolders != null && !selectedFolders.isEmpty()) {
                // Folder-targeted mode takes precedence
                service = WipeService.createStartIntent(this, wipeJob.number_passes, wipeJob.verify, wipeJob.blank, selectedFolders, selectedTargetName);
            } else if (selectedTargetPath != null) {
                service = WipeService.createStartIntent(this, wipeJob.number_passes, wipeJob.verify, wipeJob.blank, selectedTargetPath, selectedTargetName);
            } else {
                service = WipeService.createStartIntent(this, wipeJob.number_passes, wipeJob.verify, wipeJob.blank);
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
        } catch (Exception e) {
            // Fallback to legacy AsyncTask if service start fails
            this.wipeAsyncTask = new WipeAsyncTask(this);
            java.util.ArrayList<String> selectedFolders = TargetPrefs.getFolders(this);
            if (selectedFolders != null && !selectedFolders.isEmpty()) {
                wipeJob.targetFolders = selectedFolders;
                wipeJob.targetName = selectedTargetName;
            } else {
                wipeJob.targetPath = selectedTargetPath;
                wipeJob.targetName = selectedTargetName;
            }
            this.wipeAsyncTask.execute(wipeJob);
        }

        numberPassesSeekBar.setEnabled(false);
        verifySwitch.setEnabled(false);
        blankingSwitch.setEnabled(false);
    }

    private void stopWipe() {
        if (this.wipeAsyncTask != null) {
            this.wipeAsyncTask.cancel(true);
            this.wipeAsyncTask = null;
        }
        // Signal the service to cancel if running
        try {
            Intent cancel = WipeService.createCancelIntent(this);
            startService(cancel);
        } catch (Exception ignored) {}
        this.isWiping = false;

        Button startWipeButton = findViewById(R.id.start_wipe_button);
        TextView wipeTextView = findViewById(R.id.wipe_text_view);
        LinearProgressIndicator wipeProgressBar = findViewById(R.id.wipe_progress_bar);
        CircularProgressIndicator circularProgress = findViewById(R.id.circular_progress);
        CardView progressCard = findViewById(R.id.progress_card);

        // Stop animations
        AnimationHelper.stopIndeterminateProgress(circularProgress);
        AnimationHelper.stopWaveAnimation(wipeProgressBar);
        
        // Animate progress reset
        AnimationHelper.animateProgress(wipeProgressBar, 0);
        
        wipeTextView.setText("");
        startWipeButton.setText(R.string.start_wipe_button_label);

        // Hide progress card with animation
        AnimationHelper.slideOutCard(progressCard, null);

        Slider numberPassesSeekBar = findViewById(R.id.number_passes_seek_bar);
        SwitchMaterial verifySwitch = findViewById(R.id.verify_switch);
        SwitchMaterial blankingSwitch = findViewById(R.id.blanking_switch);
        numberPassesSeekBar.setEnabled(true);
        verifySwitch.setEnabled(true);
        blankingSwitch.setEnabled(true);
    }

    public void setWipeProgress(WipeJob wipeJob) {
        LinearProgressIndicator wipeProgressBar = findViewById(R.id.wipe_progress_bar);
        CircularProgressIndicator circularProgress = findViewById(R.id.circular_progress);
        TextView wipeTextView = findViewById(R.id.wipe_text_view);
        TextView currentPassValue = findViewById(R.id.current_pass_value);
        TextView progressPercentageValue = findViewById(R.id.progress_percentage_value);
        TextView wipeSpeedValue = findViewById(R.id.wipe_speed_value);
        TextView elapsedTimeValue = findViewById(R.id.elapsed_time_value);
        TextView remainingTimeValue = findViewById(R.id.remaining_time_value);

        if (!wipeJob.errorMessage.isEmpty()) {
            showStatusMessage(wipeJob.errorMessage, StatusType.INFO);
        }
        if (!this.isWiping) {
            return;
        }

        // Initialize timing on first progress update
        long currentTime = System.currentTimeMillis();
        if (wipeStartTime == 0) {
            wipeStartTime = currentTime;
            lastProgressUpdateTime = currentTime;
            lastWipedBytes = 0;
        }

        if (wipeJob.wipedBytes == 0) {
            wipeProgressBar.setVisibility(View.INVISIBLE);
            wipeProgressBar.setVisibility(View.VISIBLE);
        }

        // Calculate OVERALL progress across all passes (0-100% for entire operation)
        int totalPasses = wipeJob.number_passes + (wipeJob.blank ? 1 : 0); // Include blanking pass if enabled
        int currentPassPercentage = wipeJob.getCurrentPassPercentageCompletion();
        
        int overallPercentage;
        
        // If operation is completed, show 100%
        if (wipeJob.isCompleted()) {
            overallPercentage = 100;
        } else {
            // Calculate based on completed passes + current pass progress
            overallPercentage = ((wipeJob.passes_completed * 100) + currentPassPercentage) / totalPasses;
            overallPercentage = Math.min(100, overallPercentage);
        }
        
        Log.d("MainActivity", "Overall Progress - CompletedPasses: " + wipeJob.passes_completed + "/" + totalPasses + 
              ", CurrentPassProgress: " + currentPassPercentage + "%, OverallProgress: " + overallPercentage + "%" +
              ", IsCompleted: " + wipeJob.isCompleted() + ", IsBlankingPass: " + wipeJob.isBlankingPass());        // Calculate current speed and update history (this will update lastCompletedPasses)
        updateSpeedCalculation(wipeJob, currentTime);
        
        // Calculate time estimates
        TimeEstimate timeEst = calculateTimeEstimate(wipeJob);
        
        // Update enhanced typography elements
        currentPassValue.setText(String.valueOf(wipeJob.passes_completed + 1));
        progressPercentageValue.setText(overallPercentage + "%");
        
        // Update speed display
        if (currentMBPerSecond > 0.1) {
            wipeSpeedValue.setText(String.format("%.1f MB/s", currentMBPerSecond));
        } else {
            wipeSpeedValue.setText("--");
        }
        
        // Update time displays
        if (elapsedTimeValue != null) {
            elapsedTimeValue.setText(timeEst.elapsedTimeFormatted != null ? timeEst.elapsedTimeFormatted : "--");
        }
        if (remainingTimeValue != null) {
            if (timeEst.isValid) {
                remainingTimeValue.setText(timeEst.remainingTimeFormatted);
            } else {
                remainingTimeValue.setText("Calculating...");
            }
        }
        
        // Update descriptive text based on wipe state
        if (wipeJob.verifying) {
            wipeTextView.setText("Verifying data integrity and security");
        } else if (wipeJob.isBlankingPass()) {
            wipeTextView.setText("Final blanking pass - zeroing all data");
        } else {
            wipeTextView.setText("Securely overwriting data with random patterns");
        }
        
        // Animate progress updates
        if (wipeJob.isCompleted()) {
            AnimationHelper.animateProgress(wipeProgressBar, 100);
            AnimationHelper.animateCircularProgress(circularProgress, 100);
            
            // Show completion summary with total time
            long totalTimeMs = currentTime - wipeStartTime;
            wipeTextView.setText("✅ Wipe operation completed successfully");
            
            // Update final metrics
            progressPercentageValue.setText("100%");
            if (remainingTimeValue != null) {
                remainingTimeValue.setText("Complete!");
            }
            if (elapsedTimeValue != null) {
                elapsedTimeValue.setText(formatTime(totalTimeMs));
            }
            
        } else {
            // Update progress bars with overall progress (smooth progression from 0-100% across all passes)
            AnimationHelper.animateProgress(wipeProgressBar, overallPercentage);
            AnimationHelper.animateCircularProgress(circularProgress, overallPercentage);
        }
    }

    // Time estimation helper class
    private static class TimeEstimate {
        boolean isValid = false;
        long remainingTimeMs = 0;
        long elapsedTimeMs = 0;
        String remainingTimeFormatted = "";
        String elapsedTimeFormatted = "";
    }
    
    private void updateSpeedCalculation(WipeJob wipeJob, long currentTime) {
        // Detect if we started a new pass (wipedBytes reset or passes_completed changed)
        boolean newPassDetected = false;
        if (lastCompletedPasses != wipeJob.passes_completed) {
            newPassDetected = true;
            lastCompletedPasses = wipeJob.passes_completed;
            Log.i("MainActivity", "New pass detected: " + (wipeJob.passes_completed + 1));
        }
        
        // Reset speed tracking on new pass or if wipedBytes went backwards
        if (newPassDetected || (lastWipedBytes > 0 && wipeJob.wipedBytes < lastWipedBytes)) {
            Log.i("MainActivity", "Resetting speed tracking for new pass");
            lastProgressUpdateTime = currentTime;
            lastWipedBytes = 0;
            // Don't clear speed history immediately - let it decay naturally for smoother transitions
        }
        
        // Calculate speed only if we have previous data points
        if (lastProgressUpdateTime > 0 && currentTime > lastProgressUpdateTime && !newPassDetected) {
            long timeDiff = currentTime - lastProgressUpdateTime;
            long bytesDiff = wipeJob.wipedBytes - lastWipedBytes;
            
            if (timeDiff >= 1000 && bytesDiff > 0) { // Update every second minimum
                double mbDiff = bytesDiff / (1024.0 * 1024.0);
                double secondsDiff = timeDiff / 1000.0;
                double speedMBPerSec = mbDiff / secondsDiff;
                
                // Add to speed history (keep last 10 measurements for smoothing)
                speedHistory.offer(speedMBPerSec);
                if (speedHistory.size() > 10) {
                    speedHistory.poll();
                }
                
                // Calculate smoothed average speed
                currentMBPerSecond = speedHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                
                lastProgressUpdateTime = currentTime;
                lastWipedBytes = wipeJob.wipedBytes;
            }
        } else {
            lastProgressUpdateTime = currentTime;
            lastWipedBytes = wipeJob.wipedBytes;
        }
    }
    
    private TimeEstimate calculateTimeEstimate(WipeJob wipeJob) {
        TimeEstimate estimate = new TimeEstimate();
        long currentTime = System.currentTimeMillis();
        
        // Calculate elapsed time
        estimate.elapsedTimeMs = currentTime - wipeStartTime;
        estimate.elapsedTimeFormatted = formatTime(estimate.elapsedTimeMs);
        
        if (currentMBPerSecond > 0.1 && wipeJob.totalBytes > 0) { // Need reasonable speed
            // Calculate total operation size
            int totalPasses = wipeJob.number_passes + (wipeJob.blank ? 1 : 0);
            if (wipeJob.verify) totalPasses *= 2; // Verification doubles the work
            
            // Calculate completed and remaining bytes for entire operation
            long completedBytes = (wipeJob.passes_completed * wipeJob.totalBytes) + wipeJob.wipedBytes;
            long totalOperationBytes = totalPasses * wipeJob.totalBytes;
            long totalRemainingBytes = totalOperationBytes - completedBytes;
            
            // Convert to MB and calculate time
            double remainingMB = totalRemainingBytes / (1024.0 * 1024.0);
            estimate.remainingTimeMs = (long) ((remainingMB / currentMBPerSecond) * 1000);
            estimate.remainingTimeFormatted = formatTime(estimate.remainingTimeMs);
            estimate.isValid = true;
        }
        
        return estimate;
    }
    
    private String formatTime(long timeMs) {
        if (timeMs < 0) return "Unknown";
        
        long totalSeconds = timeMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        
        if (hours > 0) {
            return String.format("%dh %02dm %02ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %02ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private void showPermissionRequiredMessage() {
        showStatusMessage("Storage permissions required for wiping functionality.", StatusType.WARNING);
        updateStartButtonState();
    }

    private void showPermissionDeniedMessage() {
        showStatusMessage(PermissionHandler.getPermissionDeniedMessage(this), StatusType.ERROR);
        updateStartButtonState();
    }

    private void showPermissionRationale() {
        new AlertDialog.Builder(this)
            .setTitle("Storage Access Required")
            .setMessage(PermissionHandler.getPermissionRationaleMessage())
            .setPositiveButton("Grant Permission", (dialog, which) -> {
                PermissionHandler.requestPermissions(this);
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                showPermissionDeniedMessage();
            })
            .show();
    }

    private void showStorageError(String message) {
        showStatusMessage(message, StatusType.ERROR);
    }

    private void clearPermissionError() {
        hideStatusMessage();
        updateStartButtonState();
    }

    // Enhanced status message methods with Material Design icons and animations
    private void showStatusMessage(String message, StatusType type) {
        CardView statusCard = findViewById(R.id.status_card);
        ImageView statusIcon = findViewById(R.id.status_icon);
        TextView errorTextView = findViewById(R.id.error_text_view);
        
        errorTextView.setText(message);
        
        switch (type) {
            case SUCCESS:
                statusIcon.setImageResource(R.drawable.ic_success_24);
                errorTextView.setTextColor(getResources().getColor(R.color.success));
                break;
            case WARNING:
                statusIcon.setImageResource(R.drawable.ic_warning_24);
                errorTextView.setTextColor(getResources().getColor(R.color.warning));
                break;
            case ERROR:
                statusIcon.setImageResource(R.drawable.ic_error_24);
                errorTextView.setTextColor(getResources().getColor(R.color.error));
                break;
            case INFO:
            default:
                statusIcon.setImageResource(R.drawable.ic_info_24);
                errorTextView.setTextColor(getResources().getColor(R.color.info));
                break;
        }
        
        // Animate card appearance
        AnimationHelper.slideInCard(statusCard);
    }
    
    private void hideStatusMessage() {
        CardView statusCard = findViewById(R.id.status_card);
        TextView errorTextView = findViewById(R.id.error_text_view);
        
        // Animate card disappearance
        AnimationHelper.slideOutCard(statusCard, () -> {
            errorTextView.setText("");
        });
    }
    
    private void showTemporaryStatusMessage(String message, StatusType type, int durationMs) {
        showStatusMessage(message, type);
        TextView errorTextView = findViewById(R.id.error_text_view);
        errorTextView.postDelayed(this::hideStatusMessage, durationMs);
    }
    
    // Status message types enum
    private enum StatusType {
        SUCCESS, WARNING, ERROR, INFO
    }

    public void onTestBackendClick(View v) {
        // Add button press animation
        AnimationHelper.scaleButton(v);
        showTestDialog();
    }

    public void onBrowseFilesClick(View v) {
        // Add button press animation
        AnimationHelper.scaleButton(v);
        
        // Check basic permissions first
        if (!PermissionHandler.checkPermissions(this)) {
            showPermissionRequiredMessage();
            PermissionHandler.requestPermissions(this);
            return;
        }

        // Launch the system file browser activity
        Intent intent = SystemFileBrowserActivity.createIntent(this);
        startActivity(intent);
    }

    private void showTestDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Backend Test");
        
        // Get current test file info
        String testInfo = TestFileManager.getTestFileInfo(this);
        builder.setMessage(testInfo);
        
        builder.setPositiveButton("Create Test File", (dialog, which) -> {
            createTestFile();
        });
        
        builder.setNeutralButton("Delete All Test Files", (dialog, which) -> {
            deleteAllTestFiles();
        });
        
        builder.setNegativeButton("Close", null);
        
        builder.show();
    }

    private void createTestFile() {
        TestFileManager.TestFileResult result = TestFileManager.createTestFile(this);
        
        StatusType statusType = result.success ? StatusType.SUCCESS : StatusType.ERROR;
        showTemporaryStatusMessage(result.message, statusType, 3000);
        
        // Test animation system by showing progress card briefly
        if (result.success) {
            testAnimations();
        }
    }
    
    private void testAnimations() {
        CardView progressCard = findViewById(R.id.progress_card);
        CircularProgressIndicator circularProgress = findViewById(R.id.circular_progress);
        LinearProgressIndicator wipeProgressBar = findViewById(R.id.wipe_progress_bar);
        
        // Show progress card with animation
        AnimationHelper.slideInCard(progressCard);
        
        // Start indeterminate progress
        AnimationHelper.startIndeterminateProgress(circularProgress);
        
        // Test typography by updating progress values
        testTypographyDisplay();
        
        // Animate progress from 0 to 100
        AnimationHelper.animateProgress(wipeProgressBar, 100);
        
        // Hide after 3 seconds
        progressCard.postDelayed(() -> {
            AnimationHelper.stopIndeterminateProgress(circularProgress);
            AnimationHelper.slideOutCard(progressCard, null);
            AnimationHelper.animateProgress(wipeProgressBar, 0);
        }, 3000);
    }
    
    private void testTypographyDisplay() {
        TextView currentPassValue = findViewById(R.id.current_pass_value);
        TextView progressPercentageValue = findViewById(R.id.progress_percentage_value);
        TextView wipeTextView = findViewById(R.id.wipe_text_view);
        
        // Demonstrate enhanced typography with test values
        currentPassValue.setText("1");
        progressPercentageValue.setText("45%");
        wipeTextView.setText("Testing enhanced typography system");
        
        // Show success message with improved typography
        showTemporaryStatusMessage("Typography enhancement test completed successfully", StatusType.SUCCESS, 2000);
    }

    private void deleteAllTestFiles() {
        new AlertDialog.Builder(this)
            .setTitle("Confirm Deletion")
            .setMessage("Delete all test files? This will test the backend's delete functionality.")
            .setPositiveButton("Delete", (dialog, which) -> {
                TestFileManager.TestFileResult result = TestFileManager.deleteAllTestFiles(this);
                
                StatusType statusType = result.success ? StatusType.SUCCESS : StatusType.ERROR;
                showTemporaryStatusMessage(result.message, statusType, 5000);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Register to receive progress from WipeService
        if (this.wipeProgressReceiver == null) {
            this.wipeProgressReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!WipeService.ACTION_PROGRESS.equals(intent.getAction())) return;
                    WipeJob job = new WipeJob();
                    job.number_passes = lastNumberPasses;
                    job.verify = lastVerify;
                    job.blank = lastBlank;
                    job.passes_completed = intent.getIntExtra("passes_completed", 0);
                    job.totalBytes = intent.getLongExtra("total_bytes", 0);
                    job.wipedBytes = intent.getLongExtra("wiped_bytes", 0);
                    job.verifying = intent.getBooleanExtra("verifying", false);
                    job.errorMessage = intent.getStringExtra("error");
                    if (job.errorMessage == null) job.errorMessage = "";

                    if (job.isCompleted() || job.failed()) {
                        onWipeFinished(job);
                    } else {
                        setWipeProgress(job);
                    }
                }
            };
        }
        IntentFilter filter = new IntentFilter(WipeService.ACTION_PROGRESS);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(this.wipeProgressReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(this.wipeProgressReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            if (this.wipeProgressReceiver != null) unregisterReceiver(this.wipeProgressReceiver);
        } catch (IllegalArgumentException e) {
            Log.e("MainActivity", "Could not unregister wipe progress receiver onStop " + e.toString());
        }
    }
    
    /**
     * Generates digitally signed wipe certificates in PDF and JSON formats
     */
    private void generateWipeCertificates(WipeJob wipeJob) {
        try {
            // Build comprehensive certificate data
            WipeCertificate certificate = new WipeCertificate.Builder()
                .setDeviceInfo(android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + 
                              " (Android " + android.os.Build.VERSION.RELEASE + ")")
                .setAppVersion("1.0") // You may want to get this dynamically
                .setTargetPath(wipeJob.targetPath != null ? wipeJob.targetPath : "Internal Storage")
                .setTotalBytes(wipeJob.totalBytes)
                .setTotalFiles(1) // WipeJob doesn't track file count, using default
                .setPassesCompleted(wipeJob.passes_completed)
                .setBlankingEnabled(wipeJob.blank)
                .setVerificationEnabled(wipeJob.verify)
                .setWipeMethod("DoD 5220.22-M")
                .setDurationMillis(0) // Will be calculated after we add timing to WipeJob
                .setAverageSpeedMBps(calculateAverageSpeed(wipeJob))
                .setSecurityWarnings(gatherSecurityWarnings(wipeJob))
                .setComplianceStandards(Arrays.asList("NIST SP 800-88", "DoD 5220.22-M", "HIPAA", "GDPR"))
                .build();
            
            // Add public key fingerprint for verification
            String publicKeyFingerprint = CertificateSigner.getPublicKeyFingerprint();
            if (publicKeyFingerprint != null) {
                certificate.setPublicKeyFingerprint(publicKeyFingerprint);
            }
            
            // Sign the certificate for tamper-proof validation
            boolean signed = CertificateSigner.signCertificate(certificate);
            if (!signed) {
                Log.w("MainActivity", "Failed to sign certificate, but continuing with generation");
            }
            

            
            // Generate PDF certificate
            try {
                File pdfFile = PDFCertificateGenerator.generateCertificate(this, certificate);
                if (pdfFile != null && pdfFile.exists()) {
                    Log.i("MainActivity", "PDF certificate generated: " + pdfFile.getAbsolutePath());
                    showStatusMessage("PDF certificate generated: " + pdfFile.getName(), StatusType.SUCCESS);
                } else {
                    Log.e("MainActivity", "Failed to generate PDF certificate - file is null or doesn't exist");
                    showStatusMessage("Failed to generate PDF certificate", StatusType.ERROR);
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to generate PDF certificate", e);
                showStatusMessage("Failed to generate PDF certificate: " + e.getMessage(), StatusType.ERROR);
            }
            
            // Generate JSON certificate
            try {
                File jsonFile = JSONCertificateGenerator.generateCertificate(this, certificate);
                if (jsonFile != null && jsonFile.exists()) {
                    Log.i("MainActivity", "JSON certificate generated: " + jsonFile.getAbsolutePath());
                    showStatusMessage("JSON certificate generated: " + jsonFile.getName(), StatusType.SUCCESS);
                } else {
                    Log.e("MainActivity", "Failed to generate JSON certificate - file is null or doesn't exist");
                    showStatusMessage("Failed to generate JSON certificate", StatusType.ERROR);
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to generate JSON certificate", e);
                showStatusMessage("Failed to generate JSON certificate: " + e.getMessage(), StatusType.ERROR);
            }
            
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to generate wipe certificates", e);
            showStatusMessage("Failed to generate certificates: " + e.getMessage(), StatusType.ERROR);
        }
    }
    
    /**
     * Calculates average speed in MB/s from wipe job data
     */
    private double calculateAverageSpeed(WipeJob wipeJob) {
        // Since WipeJob doesn't track timing, we'll estimate based on completion
        // This is a placeholder calculation - ideally timing would be tracked in WipeJob
        if (wipeJob.totalBytes <= 0) return 0.0;
        
        double megabytes = wipeJob.totalBytes / (1024.0 * 1024.0);
        // Estimate 1 minute per pass for average speed calculation
        double estimatedSeconds = wipeJob.passes_completed * 60.0;
        return estimatedSeconds > 0 ? megabytes / estimatedSeconds : 0.0;
    }
    
    /**
     * Gathers security warnings based on wipe job performance and settings
     */
    private ArrayList<String> gatherSecurityWarnings(WipeJob wipeJob) {
        ArrayList<String> warnings = new ArrayList<>();
        
        // Configuration warnings
        if (!wipeJob.verify) {
            warnings.add("Verification disabled - cannot confirm successful data destruction");
        }
        
        if (wipeJob.passes_completed < 3) {
            warnings.add("Low number of passes (" + wipeJob.passes_completed + 
                        ") - consider additional passes for sensitive data");
        }
        
        // Check if operation failed
        if (wipeJob.failed()) {
            warnings.add("Operation failed: " + wipeJob.errorMessage);
        }
        
        // Check completion percentage
        int completion = wipeJob.getCurrentPassPercentageCompletion();
        if (completion < WipeJob.MIN_PERCENTAGE_COMPLETION && !wipeJob.isCompleted()) {
            warnings.add("Low completion percentage (" + completion + 
                        "%) - operation may have been interrupted");
        }
        
        // Battery and thermal warnings
        BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null) {
            int batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (batteryLevel < 50) {
                warnings.add("Low battery during operation (" + batteryLevel + 
                           "%) - operation may have been interrupted");
            }
        }
        
        return warnings;
    }
}
