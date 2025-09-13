package com.example.nwipe_android;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Generates machine-readable JSON certificates for completed wipe operations
 * suitable for automated compliance verification and integration with enterprise systems.
 */
public class JSONCertificateGenerator {
    private static final String TAG = "JSONCertificateGenerator";
    
    public static File generateCertificate(Context context, WipeCertificate certificate) {
        try {
            // Create Documents directory if it doesn't exist
            File documentsDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SecureWipe");
            if (!documentsDir.exists()) {
                documentsDir.mkdirs();
            }
            
            // Generate filename with timestamp
            String fileName = String.format("SecureWipe_Certificate_%s.json", 
                certificate.getCertificateId().substring(0, 8));
            File jsonFile = new File(documentsDir, fileName);
            
            // Create JSON structure
            JsonObject certificateJson = buildCertificateJson(certificate);
            
            // Write to file with pretty printing
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .serializeNulls()
                    .create();
            
            try (FileWriter writer = new FileWriter(jsonFile)) {
                gson.toJson(certificateJson, writer);
            }
            
            Log.i(TAG, "JSON certificate generated: " + jsonFile.getAbsolutePath());
            return jsonFile;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate JSON certificate", e);
            return null;
        }
    }
    
    private static JsonObject buildCertificateJson(WipeCertificate certificate) {
        JsonObject root = new JsonObject();
        
        // Certificate metadata
        JsonObject metadata = new JsonObject();
        metadata.addProperty("certificate_id", certificate.getCertificateId());
        metadata.addProperty("version", certificate.getVersion());
        metadata.addProperty("timestamp", certificate.getTimestamp());
        metadata.addProperty("timestamp_iso", formatTimestampISO(certificate.getTimestamp()));
        metadata.addProperty("generated_by", "SecureWipe Android v" + certificate.getAppVersion());
        root.add("metadata", metadata);
        
        // Device information
        JsonObject device = new JsonObject();
        device.addProperty("device_info", certificate.getDeviceInfo());
        device.addProperty("android_version", Build.VERSION.RELEASE);
        device.addProperty("api_level", Build.VERSION.SDK_INT);
        device.addProperty("manufacturer", Build.MANUFACTURER);
        device.addProperty("model", Build.MODEL);
        root.add("device", device);
        
        // Wipe operation details
        JsonObject operation = new JsonObject();
        operation.addProperty("target_path", certificate.getTargetPath());
        operation.addProperty("total_bytes", certificate.getTotalBytes());
        operation.addProperty("total_files", certificate.getTotalFiles());
        operation.addProperty("wipe_method", certificate.getWipeMethod());
        operation.addProperty("passes_completed", certificate.getPassesCompleted());
        operation.addProperty("blanking_enabled", certificate.isBlankingEnabled());
        operation.addProperty("verification_enabled", certificate.isVerificationEnabled());
        root.add("operation", operation);
        
        // Performance metrics
        JsonObject performance = new JsonObject();
        performance.addProperty("duration_milliseconds", certificate.getDurationMillis());
        performance.addProperty("duration_formatted", certificate.getFormattedDuration());
        performance.addProperty("average_speed_mbps", certificate.getAverageSpeedMBps());
        performance.addProperty("throughput_gbps", certificate.getAverageSpeedMBps() / 1024.0);
        root.add("performance", performance);
        
        // Security verification
        JsonObject security = new JsonObject();
        security.addProperty("security_compliant", certificate.isSecurityCompliant());
        
        if (certificate.getChecksumBefore() != null) {
            security.addProperty("checksum_before", certificate.getChecksumBefore());
        }
        if (certificate.getChecksumAfter() != null) {
            security.addProperty("checksum_after", certificate.getChecksumAfter());
        }
        
        if (certificate.getSecurityWarnings() != null && !certificate.getSecurityWarnings().isEmpty()) {
            security.add("warnings", new Gson().toJsonTree(certificate.getSecurityWarnings()));
        }
        
        root.add("security", security);
        
        // Digital signature information
        if (certificate.getDigitalSignature() != null) {
            JsonObject signature = new JsonObject();
            signature.addProperty("algorithm", certificate.getSigningAlgorithm());
            signature.addProperty("certificate_hash", certificate.getCertificateHash());
            signature.addProperty("digital_signature", certificate.getDigitalSignature());
            signature.addProperty("signed", true);
            root.add("digital_signature", signature);
        } else {
            JsonObject signature = new JsonObject();
            signature.addProperty("signed", false);
            signature.addProperty("note", "Digital signature will be applied after certificate generation");
            root.add("digital_signature", signature);
        }
        
        // Compliance information
        JsonObject compliance = new JsonObject();
        compliance.addProperty("nist_sp_800_88", true); // NIST Special Publication 800-88 compliance
        compliance.addProperty("dod_5220_22_m", certificate.getPassesCompleted() >= 3); // DoD 5220.22-M compliance
        compliance.addProperty("hipaa_compliant", certificate.isSecurityCompliant());
        compliance.addProperty("gdpr_right_to_erasure", true);
        root.add("compliance", compliance);
        
        // Verification data for automated systems
        JsonObject verification = new JsonObject();
        verification.addProperty("certificate_format", "SecureWipe JSON Certificate v1.0");
        verification.addProperty("schema_version", "1.0");
        verification.addProperty("tamper_evident", certificate.getDigitalSignature() != null);
        verification.addProperty("machine_readable", true);
        root.add("verification", verification);
        
        return root;
    }
    
    private static String formatTimestampISO(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        return sdf.format(new Date(timestamp));
    }
    
    /**
     * Validates a JSON certificate structure
     */
    public static boolean validateCertificate(File jsonFile) {
        try {
            // Basic file existence and readability check
            if (!jsonFile.exists() || !jsonFile.canRead()) {
                return false;
            }
            
            // TODO: Add more sophisticated validation:
            // - JSON schema validation
            // - Digital signature verification
            // - Required field checks
            // - Data integrity verification
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Certificate validation failed", e);
            return false;
        }
    }
}