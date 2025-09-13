package com.example.nwipe_android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity to view and manage generated wipe certificates
 */
public class CertificatesViewerActivity extends AppCompatActivity {
    private static final String TAG = "CertificatesViewer";
    
    private RecyclerView recyclerView;
    private LinearLayout emptyStateLayout;
    private CertificatesAdapter adapter;
    private List<CertificateItem> certificates;
    private MaterialButton refreshButton;
    private MaterialButton openFolderButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_certificates_viewer);
        
        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("Certificates");
            }
        }
        
        initViews();
        setupRecyclerView();
        loadCertificates();
    }
    
    private void initViews() {
        recyclerView = findViewById(R.id.certificates_recycler_view);
        emptyStateLayout = findViewById(R.id.empty_state_layout);
        refreshButton = findViewById(R.id.refresh_button);
        openFolderButton = findViewById(R.id.open_folder_button);
        
        refreshButton.setOnClickListener(v -> loadCertificates());
        openFolderButton.setOnClickListener(v -> openCertificatesFolder());
    }
    
    private void setupRecyclerView() {
        certificates = new ArrayList<>();
        adapter = new CertificatesAdapter(certificates, this::onCertificateClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }
    
    private void loadCertificates() {
        certificates.clear();
        
        // Check Documents/SecureWipe directory
        File documentsDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SecureWipe");
        
        if (documentsDir.exists() && documentsDir.isDirectory()) {
            File[] files = documentsDir.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".pdf") || name.toLowerCase().endsWith(".json"));
            
            if (files != null) {
                // Sort files by last modified date (newest first)
                Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                
                for (File file : files) {
                    certificates.add(new CertificateItem(file));
                }
            }
        }
        
        // Update UI
        if (certificates.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
        }
        
        adapter.notifyDataSetChanged();
        
        Log.i(TAG, "Loaded " + certificates.size() + " certificates from " + documentsDir.getAbsolutePath());
    }
    
    private void onCertificateClick(CertificateItem certificate, String action) {
        switch (action) {
            case "view":
                viewCertificate(certificate);
                break;
            case "share":
                shareCertificate(certificate);
                break;
        }
    }
    
    private void viewCertificate(CertificateItem certificate) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri fileUri = FileProvider.getUriForFile(this, 
                getApplicationContext().getPackageName() + ".fileprovider", 
                certificate.getFile());
            
            String mimeType = getMimeType(certificate.getFile());
            intent.setDataAndType(fileUri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "No app available to view " + certificate.getType() + " files", 
                    Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to view certificate", e);
            Toast.makeText(this, "Failed to open certificate: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    private void shareCertificate(CertificateItem certificate) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            Uri fileUri = FileProvider.getUriForFile(this, 
                getApplicationContext().getPackageName() + ".fileprovider", 
                certificate.getFile());
            
            String mimeType = getMimeType(certificate.getFile());
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "SecureWipe Certificate");
            shareIntent.putExtra(Intent.EXTRA_TEXT, 
                "SecureWipe certificate generated on " + certificate.getFormattedDate());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, "Share Certificate"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to share certificate", e);
            Toast.makeText(this, "Failed to share certificate: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openCertificatesFolder() {
        try {
            File documentsDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SecureWipe");
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri folderUri = FileProvider.getUriForFile(this, 
                getApplicationContext().getPackageName() + ".fileprovider", 
                documentsDir);
            intent.setDataAndType(folderUri, "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // Fallback: try to open with file manager
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments%2FSecureWipe"));
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Certificates are saved in Documents/SecureWipe folder", 
                        Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open certificates folder", e);
            Toast.makeText(this, "Certificates are saved in Documents/SecureWipe folder", 
                Toast.LENGTH_LONG).show();
        }
    }
    
    private String getMimeType(File file) {
        String extension = getFileExtension(file.getName());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mimeType != null ? mimeType : "*/*";
    }
    
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1).toLowerCase() : "";
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
    
    /**
     * Data class for certificate items
     */
    public static class CertificateItem {
        private final File file;
        private final String name;
        private final String type;
        private final long size;
        private final long lastModified;
        
        public CertificateItem(File file) {
            this.file = file;
            this.name = file.getName();
            this.size = file.length();
            this.lastModified = file.lastModified();
            
            String extension = getFileExtension(file.getName()).toUpperCase();
            switch (extension) {
                case "PDF":
                    this.type = "PDF Certificate";
                    break;
                case "JSON":
                    this.type = "JSON Certificate";
                    break;
                default:
                    this.type = "Certificate";
                    break;
            }
        }
        
        private String getFileExtension(String fileName) {
            int lastDotIndex = fileName.lastIndexOf('.');
            return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1) : "";
        }
        
        public File getFile() { return file; }
        public String getName() { return name; }
        public String getType() { return type; }
        public long getSize() { return size; }
        public long getLastModified() { return lastModified; }
        
        public String getFormattedSize() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
        
        public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            return sdf.format(new Date(lastModified));
        }
        
        public boolean isPDF() {
            return name.toLowerCase().endsWith(".pdf");
        }
        
        public boolean isJSON() {
            return name.toLowerCase().endsWith(".json");
        }
    }
}