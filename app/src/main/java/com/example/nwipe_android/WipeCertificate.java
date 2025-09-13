package com.example.nwipe_android;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Data model for wipe certificates containing all security-relevant information
 * about a completed data sanitization operation.
 */
public class WipeCertificate {
    // Certificate metadata
    private final String certificateId;
    private final String version;
    private final long timestamp;
    private final String deviceInfo;
    private final String appVersion;
    
    // Wipe operation details
    private final String targetPath;
    private final long totalBytes;
    private final int totalFiles;
    private final int passesCompleted;
    private final boolean blankingEnabled;
    private final boolean verificationEnabled;
    private final String wipeMethod;
    
    // Security metrics
    private final long durationMillis;
    private final double averageSpeedMBps;
    private final String checksumBefore;
    private final String checksumAfter;
    private final List<String> securityWarnings;
    private final List<String> complianceStandards;
    
    // Digital signature data
    private String digitalSignature;
    private String signingAlgorithm;
    private String certificateHash;
    private String publicKeyFingerprint;
    
    public WipeCertificate(Builder builder) {
        this.certificateId = UUID.randomUUID().toString();
        this.version = "1.0";
        this.timestamp = System.currentTimeMillis();
        this.deviceInfo = builder.deviceInfo;
        this.appVersion = builder.appVersion;
        
        this.targetPath = builder.targetPath;
        this.totalBytes = builder.totalBytes;
        this.totalFiles = builder.totalFiles;
        this.passesCompleted = builder.passesCompleted;
        this.blankingEnabled = builder.blankingEnabled;
        this.verificationEnabled = builder.verificationEnabled;
        this.wipeMethod = builder.wipeMethod;
        
        this.durationMillis = builder.durationMillis;
        this.averageSpeedMBps = builder.averageSpeedMBps;
        this.checksumBefore = builder.checksumBefore;
        this.checksumAfter = builder.checksumAfter;
        this.securityWarnings = builder.securityWarnings;
        this.complianceStandards = builder.complianceStandards;
    }
    
    // Getters
    public String getCertificateId() { return certificateId; }
    public String getVersion() { return version; }
    public long getTimestamp() { return timestamp; }
    public String getDeviceInfo() { return deviceInfo; }
    public String getAppVersion() { return appVersion; }
    
    public String getTargetPath() { return targetPath; }
    public long getTotalBytes() { return totalBytes; }
    public int getTotalFiles() { return totalFiles; }
    public int getPassesCompleted() { return passesCompleted; }
    public boolean isBlankingEnabled() { return blankingEnabled; }
    public boolean isVerificationEnabled() { return verificationEnabled; }
    public String getWipeMethod() { return wipeMethod; }
    
    public long getDurationMillis() { return durationMillis; }
    public double getAverageSpeedMBps() { return averageSpeedMBps; }
    public String getChecksumBefore() { return checksumBefore; }
    public String getChecksumAfter() { return checksumAfter; }
    public List<String> getSecurityWarnings() { return securityWarnings; }
    public List<String> getComplianceStandards() { return complianceStandards; }
    
    public String getDigitalSignature() { return digitalSignature; }
    public String getSigningAlgorithm() { return signingAlgorithm; }
    public String getCertificateHash() { return certificateHash; }
    public String getPublicKeyFingerprint() { return publicKeyFingerprint; }
    
    // Setters for signature data (set after signing)
    public void setDigitalSignature(String digitalSignature) { this.digitalSignature = digitalSignature; }
    public void setSigningAlgorithm(String signingAlgorithm) { this.signingAlgorithm = signingAlgorithm; }
    public void setCertificateHash(String certificateHash) { this.certificateHash = certificateHash; }
    public void setPublicKeyFingerprint(String publicKeyFingerprint) { this.publicKeyFingerprint = publicKeyFingerprint; }
    
    // Utility methods
    public String getFormattedTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US);
        return sdf.format(new Date(timestamp));
    }
    
    public String getFormattedSize() {
        if (totalBytes < 1024) return totalBytes + " B";
        if (totalBytes < 1024 * 1024) return String.format("%.1f KB", totalBytes / 1024.0);
        if (totalBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", totalBytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", totalBytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    public String getFormattedDuration() {
        long seconds = durationMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60);
        } else {
            return String.format("%d:%02d", minutes, seconds % 60);
        }
    }
    
    public boolean isSecurityCompliant() {
        return securityWarnings == null || securityWarnings.isEmpty();
    }
    
    // Builder pattern for easy construction
    public static class Builder {
        private String deviceInfo;
        private String appVersion;
        private String targetPath;
        private long totalBytes;
        private int totalFiles;
        private int passesCompleted;
        private boolean blankingEnabled;
        private boolean verificationEnabled;
        private String wipeMethod;
        private long durationMillis;
        private double averageSpeedMBps;
        private String checksumBefore;
        private String checksumAfter;
        private List<String> securityWarnings;
        private List<String> complianceStandards;
        
        public Builder setDeviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; return this; }
        public Builder setAppVersion(String appVersion) { this.appVersion = appVersion; return this; }
        public Builder setTargetPath(String targetPath) { this.targetPath = targetPath; return this; }
        public Builder setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; return this; }
        public Builder setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; return this; }
        public Builder setPassesCompleted(int passesCompleted) { this.passesCompleted = passesCompleted; return this; }
        public Builder setBlankingEnabled(boolean blankingEnabled) { this.blankingEnabled = blankingEnabled; return this; }
        public Builder setVerificationEnabled(boolean verificationEnabled) { this.verificationEnabled = verificationEnabled; return this; }
        public Builder setWipeMethod(String wipeMethod) { this.wipeMethod = wipeMethod; return this; }
        public Builder setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; return this; }
        public Builder setAverageSpeedMBps(double averageSpeedMBps) { this.averageSpeedMBps = averageSpeedMBps; return this; }
        public Builder setChecksumBefore(String checksumBefore) { this.checksumBefore = checksumBefore; return this; }
        public Builder setChecksumAfter(String checksumAfter) { this.checksumAfter = checksumAfter; return this; }
        public Builder setSecurityWarnings(List<String> securityWarnings) { this.securityWarnings = securityWarnings; return this; }
        public Builder setComplianceStandards(List<String> complianceStandards) { this.complianceStandards = complianceStandards; return this; }
        
        public WipeCertificate build() {
            return new WipeCertificate(this);
        }
    }
}