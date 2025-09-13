package com.example.nwipe_android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class SystemFileBrowserActivity extends AppCompatActivity implements SystemFileAdapter.OnSystemFileClickListener {

    private RecyclerView recyclerView;
    private SystemFileAdapter fileAdapter;

    private TextView statusText;
    private TextView storageInfoText;
    private Button backButton;
    private Button refreshButton;
    private View permissionStatusCard;
    private TextView permissionStatusText;
    private Button grantPermissionButton;
    
    private File currentDirectory;
    private Stack<File> navigationStack;
    private List<SystemStorageManager.StorageLocation> storageLocations;
    private SystemStorageManager.StorageLocation currentStorageLocation;

    public static final String EXTRA_ROOT_PATH = "extra_root_path";
    public static final String EXTRA_RESTRICT_TO_ROOT = "extra_restrict_to_root";

    public static Intent createIntent(Context context) {
        return new Intent(context, SystemFileBrowserActivity.class);
    }

    public static Intent createIntent(Context context, String rootPath, boolean restrictToRoot) {
        Intent i = new Intent(context, SystemFileBrowserActivity.class);
        if (rootPath != null) i.putExtra(EXTRA_ROOT_PATH, rootPath);
        i.putExtra(EXTRA_RESTRICT_TO_ROOT, restrictToRoot);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_system_file_browser);

            initializeViews();
            setupToolbar();
            setupRecyclerView();
            setupClickListeners();
            
            // Initialize navigation
            navigationStack = new Stack<>();
            
            // Initialize storage locations with error handling
            try {
                storageLocations = SystemStorageManager.getSystemStorageRoots(this);
            } catch (Exception e) {
                e.printStackTrace();
                storageLocations = new ArrayList<>(); // Fallback to empty list
                Toast.makeText(this, "Warning: Could not load storage locations", Toast.LENGTH_SHORT).show();
            }
            
            // Check permissions and load initial content
            checkPermissionsAndLoad();

            // If a root path is provided, navigate there
            String rootPath = getIntent().getStringExtra(EXTRA_ROOT_PATH);
            if (rootPath != null && SystemStorageManager.isPathAccessible(rootPath)) {
                File root = new File(rootPath);
                navigateToDirectory(root, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // If there's an error during initialization, show a simple error and finish
            Toast.makeText(this, "Error initializing file browser: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.files_recycler_view);
        statusText = findViewById(R.id.status_text);
        storageInfoText = findViewById(R.id.storage_info_text);
        backButton = findViewById(R.id.back_button);
        refreshButton = findViewById(R.id.refresh_button);
        permissionStatusCard = findViewById(R.id.permission_status_card);
        permissionStatusText = findViewById(R.id.permission_status_text);
        grantPermissionButton = findViewById(R.id.grant_permission_button);
        
        // Verify that all critical views were found
        if (recyclerView == null) {
            throw new RuntimeException("RecyclerView not found in layout");
        }
        if (statusText == null) {
            throw new RuntimeException("Status text not found in layout");
        }
        if (backButton == null) {
            throw new RuntimeException("Back button not found in layout");
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }

    private void setupRecyclerView() {
        try {
            fileAdapter = new SystemFileAdapter(this);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(fileAdapter);
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup RecyclerView: " + e.getMessage(), e);
        }
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> navigateBack());
        refreshButton.setOnClickListener(v -> refreshCurrentDirectory());
        grantPermissionButton.setOnClickListener(v -> requestSystemPermissions());
        
        // Quick access buttons - with null checks
        View storageRootsButton = findViewById(R.id.storage_roots_button);
        if (storageRootsButton != null) {
            storageRootsButton.setOnClickListener(v -> loadStorageRoots());
        }
        
        View downloadsButton = findViewById(R.id.downloads_button);
        if (downloadsButton != null) {
            downloadsButton.setOnClickListener(v -> navigateToCommonDirectory("Downloads"));
        }
        
        View picturesButton = findViewById(R.id.pictures_button);
        if (picturesButton != null) {
            picturesButton.setOnClickListener(v -> navigateToCommonDirectory("Pictures"));
        }
        
        View documentsButton = findViewById(R.id.documents_button);
        if (documentsButton != null) {
            documentsButton.setOnClickListener(v -> navigateToCommonDirectory("Documents"));
        }
    }

    private void checkPermissionsAndLoad() {
        updatePermissionStatus();
        
        if (PermissionHandler.checkSystemStoragePermissions(this)) {
            loadStorageRoots();
            statusText.setText("Tap folders to browse, use ⋮ menu or toolbar to select folders for wiping");
        } else {
            showLimitedAccessMode();
        }
    }

    private void updatePermissionStatus() {
        String statusMessage = PermissionHandler.getSystemStoragePermissionStatus(this);
        permissionStatusText.setText(statusMessage);
        
        boolean hasFullAccess = PermissionHandler.hasFullStorageAccess(this);
        if (hasFullAccess) {
            permissionStatusCard.setVisibility(View.GONE);
        } else {
            permissionStatusCard.setVisibility(View.VISIBLE);
            grantPermissionButton.setVisibility(View.VISIBLE);
        }
    }

    private void requestSystemPermissions() {
        new AlertDialog.Builder(this)
            .setTitle("All Files Access Required")
            .setMessage(PermissionHandler.getSystemStoragePermissionRationaleMessage())
            .setPositiveButton("Grant Permission", (dialog, which) -> {
                PermissionHandler.requestSystemStoragePermissions(this);
            })
            .setNegativeButton("Continue with Limited Access", (dialog, which) -> {
                showLimitedAccessMode();
            })
            .show();
    }

    private void showLimitedAccessMode() {
        // Load what we can access without full permissions
        loadStorageRoots();
        
        statusText.setText("Limited access mode - some directories may not be accessible");
        Toast.makeText(this, "Running in limited access mode. Grant 'All Files Access' for full browsing.", 
            Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if permissions changed while we were away
        updatePermissionStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PermissionHandler.MANAGE_EXTERNAL_STORAGE_REQUEST) {
            if (PermissionHandler.handleSystemStoragePermissionResult(requestCode)) {
                // Permission granted
                updatePermissionStatus();
                loadStorageRoots();
                Toast.makeText(this, "All Files Access granted! Full browsing now available.", 
                    Toast.LENGTH_SHORT).show();
            } else {
                // Permission denied
                showLimitedAccessMode();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_system_file_browser, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem selectItem = menu.findItem(R.id.action_select_current_folder);
        MenuItem unselectItem = menu.findItem(R.id.action_unselect_current_folder);
        
        if (currentDirectory != null && currentDirectory.isDirectory()) {
            String path = currentDirectory.getAbsolutePath();
            boolean isSelected = TargetPrefs.isFolderSelected(this, path);
            
            selectItem.setVisible(!isSelected);
            unselectItem.setVisible(isSelected);
        } else {
            selectItem.setVisible(false);
            unselectItem.setVisible(false);
        }
        
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.action_select_current_folder) {
            selectCurrentFolder();
            return true;
        } else if (item.getItemId() == R.id.action_unselect_current_folder) {
            unselectCurrentFolder();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void selectCurrentFolder() {
        if (currentDirectory != null) {
            String path = currentDirectory.getAbsolutePath();
            TargetPrefs.addFolder(this, path);
            Toast.makeText(this, "Added folder to selection", Toast.LENGTH_SHORT).show();
            invalidateOptionsMenu();
            refreshCurrentDirectory();
        }
    }

    private void unselectCurrentFolder() {
        if (currentDirectory != null) {
            String path = currentDirectory.getAbsolutePath();
            TargetPrefs.removeFolder(this, path);
            Toast.makeText(this, "Removed folder from selection", Toast.LENGTH_SHORT).show();
            invalidateOptionsMenu();
            refreshCurrentDirectory();
        }
    }

    @Override
    public void onBackPressed() {
        navigateBack();
    }

    private void navigateBack() {
        if (!navigationStack.isEmpty()) {
            File previousDirectory = navigationStack.pop();
            navigateToDirectory(previousDirectory, false);
        } else if (currentDirectory != null) {
            // Go back to storage roots
            loadStorageRoots();
        } else {
            super.onBackPressed();
        }
    }

    private void loadStorageRoots() {
        currentDirectory = null;
        currentStorageLocation = null;
        navigationStack.clear();
        
        statusText.setText("Loading storage locations...");
        // Clear breadcrumb for storage roots view
        updateBreadcrumb(null);
        storageInfoText.setText("");
        
        SystemFileBrowserManager.SystemBrowseResult result = 
            SystemFileBrowserManager.listStorageRoots(this);
        
        if (result.success) {
            fileAdapter.setFileItems(result.items);
            statusText.setText(result.message);
        } else {
            fileAdapter.clear();
            statusText.setText("Error: " + result.message);
            showErrorDialog("Failed to load storage locations", result.message);
        }
        
        invalidateOptionsMenu();
    }

    private void navigateToDirectory(File directory, boolean addToStack) {
        if (directory == null) {
            loadStorageRoots();
            return;
        }

        // Check if directory is accessible
        if (!SystemFileBrowserManager.isSystemDirectoryAccessible(directory, this)) {
            String message = SystemFileBrowserManager.getPermissionRequirementMessage(directory);
            showErrorDialog("Access Denied", message);
            return;
        }

        // Respect restrict-to-root option
        boolean restrict = getIntent().getBooleanExtra(EXTRA_RESTRICT_TO_ROOT, false);
        String rootPath = getIntent().getStringExtra(EXTRA_ROOT_PATH);
        if (restrict && rootPath != null) {
            try {
                String target = new File(rootPath).getCanonicalPath();
                String dest = directory.getCanonicalPath();
                if (!dest.startsWith(target)) {
                    // Block navigation outside root
                    showErrorDialog("Restricted", "Cannot leave selected storage root");
                    return;
                }
            } catch (Exception ignored) {}
        }

        statusText.setText("Loading...");
        
        // Add current directory to stack if navigating forward
        if (addToStack && currentDirectory != null) {
            navigationStack.push(currentDirectory);
        }
        
        SystemFileBrowserManager.SystemBrowseResult result = 
            SystemFileBrowserManager.listSystemDirectory(directory, this);
        
        if (result.success) {
            currentDirectory = directory;
            currentStorageLocation = result.storageLocation;
            fileAdapter.setFileItems(result.items);
            updateBreadcrumb(result.currentPath);
            updateStorageInfo(result.storageLocation);
            statusText.setText(result.message);
            
            if (result.hasPermissionIssues) {
                statusText.setText(result.message + " (some items may be hidden due to permissions)");
            }
        } else {
            statusText.setText("Error: " + result.message);
            showErrorDialog("Cannot open directory", result.message);
            
            // Remove from stack if we added it
            if (addToStack && !navigationStack.isEmpty()) {
                navigationStack.pop();
            }
        }
        
        invalidateOptionsMenu();
    }

    private void navigateToCommonDirectory(String directoryName) {
        List<SystemStorageManager.CommonDirectory> commonDirs = 
            SystemStorageManager.getCommonDirectories(this);
        
        for (SystemStorageManager.CommonDirectory commonDir : commonDirs) {
            if (commonDir.displayName.equals(directoryName)) {
                File dir = new File(commonDir.path);
                if (dir.exists() && dir.canRead()) {
                    navigateToDirectory(dir, true);
                    return;
                } else {
                    showErrorDialog("Directory Not Accessible", 
                        "Cannot access " + directoryName + " directory");
                    return;
                }
            }
        }
        
        showErrorDialog("Directory Not Found", 
            directoryName + " directory not found on this device");
    }

    private void updateBreadcrumb(String path) {
        try {
            LinearLayout breadcrumbLayout = findViewById(R.id.breadcrumb_layout);
            if (breadcrumbLayout == null) {
                return; // Layout not found, skip breadcrumb update
            }
            
            // Clear existing breadcrumb items except the root
            try {
                int childCount = breadcrumbLayout.getChildCount();
                if (childCount > 1) {
                    breadcrumbLayout.removeViews(1, childCount - 1);
                }
            } catch (Exception e) {
                // If clearing fails, just continue
                e.printStackTrace();
            }
            
            if (path == null || path.isEmpty()) {
                return;
            }
            
            // Split path into components with additional safety
            String displayPath = null;
            try {
                displayPath = SystemFileBrowserManager.getSystemDisplayPath(path, this);
            } catch (Exception e) {
                // If display path fails, use the original path
                displayPath = path;
            }
            
            if (displayPath == null || displayPath.isEmpty()) {
                return;
            }
            
            String[] pathComponents = displayPath.split("/");
            
            for (int i = 1; i < pathComponents.length; i++) {
                String component = pathComponents[i].trim();
                if (component.isEmpty()) continue;
                
                try {
                    // Add separator
                    addBreadcrumbSeparator(breadcrumbLayout);
                    
                    // Add path component chip
                    addBreadcrumbChip(breadcrumbLayout, component, i == pathComponents.length - 1);
                } catch (Exception e) {
                    // If individual component fails, continue with next
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            // If breadcrumb update fails, just continue without it
            e.printStackTrace();
        }
    }
    
    private void addBreadcrumbSeparator(LinearLayout breadcrumbLayout) {
        try {
            ImageView separator = new ImageView(this);
            separator.setImageResource(R.drawable.ic_chevron_right_24);
            separator.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.md_theme_light_onPrimaryContainer));
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.breadcrumb_separator_size),
                getResources().getDimensionPixelSize(R.dimen.breadcrumb_separator_size)
            );
            params.setMargins(
                getResources().getDimensionPixelSize(R.dimen.breadcrumb_separator_margin),
                0,
                getResources().getDimensionPixelSize(R.dimen.breadcrumb_separator_margin),
                0
            );
            separator.setLayoutParams(params);
            
            breadcrumbLayout.addView(separator);
        } catch (Exception e) {
            // Fallback to simple text separator
            TextView separator = new TextView(this);
            separator.setText(" > ");
            separator.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.md_theme_light_onPrimaryContainer));
            breadcrumbLayout.addView(separator);
        }
    }
    
    private void addBreadcrumbChip(LinearLayout breadcrumbLayout, String text, boolean isLast) {
        try {
            // Create chip with proper Material context
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(
                new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Widget_Material3_Chip_Assist)
            );
            chip.setText(text);
            chip.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.md_theme_light_onPrimaryContainer));
            
            if (isLast) {
                // Current directory - use primary container style
                chip.setChipBackgroundColorResource(R.color.md_theme_light_primaryContainer);
                chip.setClickable(false);
            } else {
                // Parent directory - use surface variant style and make clickable
                chip.setChipBackgroundColorResource(R.color.md_theme_light_surfaceVariant);
                chip.setClickable(true);
                chip.setOnClickListener(v -> {
                    // Navigate to this breadcrumb level
                    navigateToBreadcrumbLevel(text);
                });
            }
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            chip.setLayoutParams(params);
            
            breadcrumbLayout.addView(chip);
        } catch (Exception e) {
            // Fallback to simple text view if chip creation fails
            TextView textView = new TextView(this);
            textView.setText(text);
            textView.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.md_theme_light_onPrimaryContainer));
            textView.setPadding(16, 8, 16, 8);
            breadcrumbLayout.addView(textView);
        }
    }
    
    private void navigateToBreadcrumbLevel(String levelName) {
        // This would need to be implemented based on the navigation stack
        // For now, just refresh current directory
        refreshCurrentDirectory();
    }

    private void updateStorageInfo(SystemStorageManager.StorageLocation storageLocation) {
        if (storageLocation != null) {
            String info = String.format("%s free / %s total", 
                storageLocation.getFormattedFreeSpace(), 
                storageLocation.getFormattedTotalSpace());
            storageInfoText.setText(info);
        } else {
            storageInfoText.setText("");
        }
    }

    private void refreshCurrentDirectory() {
        if (currentDirectory != null) {
            navigateToDirectory(currentDirectory, false);
        } else {
            loadStorageRoots();
        }
    }

    @Override
    public void onSystemFileClick(SystemFileItem fileItem) {
        if (fileItem.getType() == FileItem.ItemType.DIRECTORY) {
            if (fileItem.canRead()) {
                navigateToDirectory(fileItem.getFile(), true);
            } else {
                showErrorDialog("Access Denied", "Cannot read directory: " + fileItem.getName());
            }
        } else {
            // For files, show file info dialog
            showFileInfoDialog(fileItem);
        }
    }

    @Override
    public void onSystemFileMenuClick(SystemFileItem fileItem, View view) {
        showFileContextMenu(fileItem, view);
    }

    private void showFileContextMenu(SystemFileItem fileItem, View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        
        // Add menu items based on file capabilities
        popup.getMenu().add(0, 1, 0, "Info");
        
        if (fileItem.getType() == FileItem.ItemType.DIRECTORY && fileItem.canRead()) {
            popup.getMenu().add(0, 2, 0, "Open");
            boolean selected = TargetPrefs.isFolderSelected(this, fileItem.getFile().getAbsolutePath());
            popup.getMenu().add(0, 4, 0, selected ? "Unselect for wipe" : "Select for wipe");
        }
        
        if (fileItem.isSafeToDelete()) {
            popup.getMenu().add(0, 3, 0, "Delete");
        }
        
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: // Info
                    showFileInfoDialog(fileItem);
                    return true;
                case 2: // Open
                    if (fileItem.getType() == FileItem.ItemType.DIRECTORY) {
                        navigateToDirectory(fileItem.getFile(), true);
                    }
                    return true;
                case 3: // Delete
                    confirmDeleteFile(fileItem);
                    return true;
                case 4: // Toggle select
                    String p = fileItem.getFile().getAbsolutePath();
                    if (TargetPrefs.isFolderSelected(this, p)) {
                        TargetPrefs.removeFolder(this, p);
                        Toast.makeText(this, "Removed from selection", Toast.LENGTH_SHORT).show();
                    } else {
                        TargetPrefs.addFolder(this, p);
                        Toast.makeText(this, "Added to selection", Toast.LENGTH_SHORT).show();
                    }
                    // Refresh chips
                    refreshCurrentDirectory();
                    return true;
                default:
                    return false;
            }
        });
        
        popup.show();
    }

    private void showFileInfoDialog(SystemFileItem fileItem) {
        ModernDialogHelper.createFileInfoDialog(this, fileItem).show();
    }

    private void confirmDeleteFile(SystemFileItem fileItem) {
        String itemType = fileItem.getType() == FileItem.ItemType.DIRECTORY ? "directory" : "file";
        String title = "Delete " + itemType.substring(0, 1).toUpperCase() + itemType.substring(1);
        
        StringBuilder message = new StringBuilder();
        message.append("Are you sure you want to delete this ").append(itemType).append("?");
        
        if (fileItem.getType() == FileItem.ItemType.DIRECTORY) {
            message.append("\n\nThis will permanently delete the directory and all its contents.");
        } else {
            message.append("\n\nThis action cannot be undone.");
        }
        
        String warningMessage = null;
        if (fileItem.isSystemFile()) {
            warningMessage = "This is a system file and may be protected or critical to system operation.";
        }
        
        ModernDialogHelper.createFileOperationDialog(
            this, 
            fileItem, 
            title, 
            message.toString(), 
            "Delete", 
            warningMessage,
            () -> deleteFile(fileItem),
            null
        ).show();
    }

    private void deleteFile(SystemFileItem fileItem) {
        statusText.setText("Deleting...");
        
        SystemFileBrowserManager.SystemDeleteResult result = 
            SystemFileBrowserManager.deleteSystemFile(fileItem, this);
        
        if (result.success) {
            statusText.setText("✓ " + result.message);
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
            
            // Remove from adapter
            fileAdapter.removeFileItem(fileItem);
            
            // Clear status after delay
            statusText.postDelayed(() -> {
                if (currentDirectory != null) {
                    statusText.setText("Found " + fileAdapter.getItemCount() + " items");
                } else {
                    statusText.setText("Found " + fileAdapter.getItemCount() + " storage locations");
                }
            }, 2000);
            
        } else {
            statusText.setText("✗ " + result.message);
            
            if (result.wasProtected) {
                showErrorDialog("Delete Failed - Protected File", result.message);
            } else {
                showErrorDialog("Delete Failed", result.message);
            }
            
            // Clear error status after delay
            statusText.postDelayed(() -> {
                if (currentDirectory != null) {
                    statusText.setText("Found " + fileAdapter.getItemCount() + " items");
                } else {
                    statusText.setText("Found " + fileAdapter.getItemCount() + " storage locations");
                }
            }, 3000);
        }
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}