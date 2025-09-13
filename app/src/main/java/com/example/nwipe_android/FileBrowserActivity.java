package com.example.nwipe_android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class FileBrowserActivity extends AppCompatActivity implements FileAdapter.OnFileClickListener {

    private RecyclerView recyclerView;
    private FileAdapter fileAdapter;
    private TextView breadcrumbText;
    private TextView statusText;
    private Button refreshButton;
    
    private File currentDirectory;
    private Stack<File> navigationStack;
    private List<File> rootDirectories;

    public static Intent createIntent(Context context) {
        return new Intent(context, FileBrowserActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);

        initializeViews();
        setupToolbar();
        setupRecyclerView();
        setupClickListeners();
        
        // Initialize navigation
        navigationStack = new Stack<>();
        rootDirectories = FileBrowserManager.getSafeRootDirectories(this);
        
        // Start with root directories
        loadRootDirectories();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.files_recycler_view);
        breadcrumbText = findViewById(R.id.breadcrumb_text);
        statusText = findViewById(R.id.status_text);
        refreshButton = findViewById(R.id.refresh_button);
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
        fileAdapter = new FileAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(fileAdapter);
    }

    private void setupClickListeners() {
        refreshButton.setOnClickListener(v -> refreshCurrentDirectory());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (!navigationStack.isEmpty()) {
            File previousDirectory = navigationStack.pop();
            navigateToDirectory(previousDirectory, false);
        } else if (currentDirectory != null) {
            // Go back to root directories
            loadRootDirectories();
        } else {
            super.onBackPressed();
        }
    }

    private void loadRootDirectories() {
        currentDirectory = null;
        navigationStack.clear();
        
        statusText.setText("Loading directories...");
        breadcrumbText.setText("App Storage");
        
        FileBrowserManager.BrowseResult result = FileBrowserManager.listRootDirectories(this);
        
        if (result.success) {
            fileAdapter.setFileItems(result.items);
            statusText.setText(result.message);
        } else {
            fileAdapter.clear();
            statusText.setText("Error: " + result.message);
            showErrorDialog("Failed to load directories", result.message);
        }
    }

    private void navigateToDirectory(File directory, boolean addToStack) {
        if (directory == null) {
            loadRootDirectories();
            return;
        }

        // Check if directory is safe to access
        if (!FileBrowserManager.isSafeDirectory(directory, this)) {
            showErrorDialog("Access Denied", "Cannot access this directory for security reasons.");
            return;
        }

        statusText.setText("Loading...");
        
        // Add current directory to stack if navigating forward
        if (addToStack && currentDirectory != null) {
            navigationStack.push(currentDirectory);
        }
        
        FileBrowserManager.BrowseResult result = FileBrowserManager.listDirectory(directory);
        
        if (result.success) {
            currentDirectory = directory;
            fileAdapter.setFileItems(result.items);
            updateBreadcrumb(result.currentPath);
            statusText.setText(result.message);
        } else {
            statusText.setText("Error: " + result.message);
            showErrorDialog("Cannot open directory", result.message);
            
            // Remove from stack if we added it
            if (addToStack && !navigationStack.isEmpty()) {
                navigationStack.pop();
            }
        }
    }

    private void updateBreadcrumb(String path) {
        String displayPath = FileBrowserManager.getDisplayPath(path, this);
        breadcrumbText.setText(displayPath);
    }

    private void refreshCurrentDirectory() {
        if (currentDirectory != null) {
            navigateToDirectory(currentDirectory, false);
        } else {
            loadRootDirectories();
        }
    }

    @Override
    public void onFileClick(FileItem fileItem) {
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
    public void onFileMenuClick(FileItem fileItem, View view) {
        showFileContextMenu(fileItem, view);
    }

    private void showFileContextMenu(FileItem fileItem, View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        
        // Add menu items based on file capabilities
        popup.getMenu().add(0, 1, 0, "Info");
        
        if (fileItem.getType() == FileItem.ItemType.DIRECTORY && fileItem.canRead()) {
            popup.getMenu().add(0, 2, 0, "Open");
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
                default:
                    return false;
            }
        });
        
        popup.show();
    }

    private void showFileInfoDialog(FileItem fileItem) {
        StringBuilder info = new StringBuilder();
        info.append("Name: ").append(fileItem.getName()).append("\n");
        info.append("Type: ").append(fileItem.getType() == FileItem.ItemType.DIRECTORY ? "Directory" : "File").append("\n");
        info.append("Size: ").append(fileItem.getFormattedSize()).append("\n");
        info.append("Modified: ").append(fileItem.getFormattedDate()).append("\n");
        info.append("Path: ").append(fileItem.getFile().getAbsolutePath()).append("\n\n");
        
        info.append("Permissions:\n");
        info.append("• Read: ").append(fileItem.canRead() ? "Yes" : "No").append("\n");
        info.append("• Write: ").append(fileItem.canWrite() ? "Yes" : "No").append("\n");
        info.append("• Delete: ").append(fileItem.canDelete() ? "Yes" : "No").append("\n");
        
        if (fileItem.getType() == FileItem.ItemType.FILE) {
            String ext = fileItem.getFileExtension();
            if (!ext.isEmpty()) {
                info.append("• Extension: ").append(ext.toUpperCase()).append("\n");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("File Information")
                .setMessage(info.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void confirmDeleteFile(FileItem fileItem) {
        String itemType = fileItem.getType() == FileItem.ItemType.DIRECTORY ? "directory" : "file";
        String message = String.format(
                "Delete %s '%s'?\n\nThis action cannot be undone.",
                itemType, fileItem.getName()
        );

        new AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage(message)
                .setPositiveButton("Delete", (dialog, which) -> deleteFile(fileItem))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteFile(FileItem fileItem) {
        statusText.setText("Deleting...");
        
        FileBrowserManager.DeleteResult result = FileBrowserManager.deleteItem(fileItem);
        
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
                    statusText.setText("Found " + fileAdapter.getItemCount() + " directories");
                }
            }, 2000);
            
        } else {
            statusText.setText("✗ " + result.message);
            showErrorDialog("Delete Failed", result.message);
            
            // Clear error status after delay
            statusText.postDelayed(() -> {
                if (currentDirectory != null) {
                    statusText.setText("Found " + fileAdapter.getItemCount() + " items");
                } else {
                    statusText.setText("Found " + fileAdapter.getItemCount() + " directories");
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