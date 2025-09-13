package com.example.nwipe_android;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Provides cryptographic signing capabilities using Android Keystore
 * for tamper-proof certificate generation and verification.
 */
public class CertificateSigner {
    private static final String TAG = "CertificateSigner";
    private static final String KEY_ALIAS = "SecureWipeCertificateKey";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    
    /**
     * Signs a wipe certificate with device-generated private key
     */
    public static boolean signCertificate(WipeCertificate certificate) {
        try {
            // Ensure key pair exists
            if (!ensureKeyPairExists()) {
                Log.e(TAG, "Failed to create or retrieve key pair");
                return false;
            }
            
            // Get private key from keystore
            PrivateKey privateKey = getPrivateKey();
            if (privateKey == null) {
                Log.e(TAG, "Failed to retrieve private key");
                return false;
            }
            
            // Create signature data from certificate content
            String signatureData = createSignatureData(certificate);
            
            // Generate digital signature
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(signatureData.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signature.sign();
            
            // Generate certificate hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(signatureData.getBytes(StandardCharsets.UTF_8));
            
            // Set signature data on certificate
            certificate.setDigitalSignature(Base64.encodeToString(signatureBytes, Base64.NO_WRAP));
            certificate.setSigningAlgorithm(SIGNATURE_ALGORITHM);
            certificate.setCertificateHash(Base64.encodeToString(hashBytes, Base64.NO_WRAP));
            
            Log.i(TAG, "Certificate signed successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to sign certificate", e);
            return false;
        }
    }
    
    /**
     * Verifies the digital signature of a certificate
     */
    public static boolean verifyCertificate(WipeCertificate certificate) {
        try {
            if (certificate.getDigitalSignature() == null) {
                Log.w(TAG, "Certificate has no digital signature");
                return false;
            }
            
            // Get public key from keystore
            PublicKey publicKey = getPublicKey();
            if (publicKey == null) {
                Log.e(TAG, "Failed to retrieve public key");
                return false;
            }
            
            // Recreate signature data
            String signatureData = createSignatureData(certificate);
            
            // Verify signature
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(signatureData.getBytes(StandardCharsets.UTF_8));
            
            byte[] signatureBytes = Base64.decode(certificate.getDigitalSignature(), Base64.NO_WRAP);
            boolean isValid = signature.verify(signatureBytes);
            
            // Verify certificate hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] computedHash = digest.digest(signatureData.getBytes(StandardCharsets.UTF_8));
            String computedHashString = Base64.encodeToString(computedHash, Base64.NO_WRAP);
            
            boolean hashMatches = computedHashString.equals(certificate.getCertificateHash());
            
            Log.i(TAG, "Certificate verification - Signature valid: " + isValid + ", Hash matches: " + hashMatches);
            return isValid && hashMatches;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to verify certificate", e);
            return false;
        }
    }
    
    /**
     * Creates a deterministic string representation of certificate data for signing
     */
    private static String createSignatureData(WipeCertificate certificate) {
        StringBuilder sb = new StringBuilder();
        
        // Include all immutable certificate data in deterministic order
        sb.append("certificate_id:").append(certificate.getCertificateId()).append("|");
        sb.append("version:").append(certificate.getVersion()).append("|");
        sb.append("timestamp:").append(certificate.getTimestamp()).append("|");
        sb.append("device_info:").append(certificate.getDeviceInfo()).append("|");
        sb.append("app_version:").append(certificate.getAppVersion()).append("|");
        sb.append("target_path:").append(certificate.getTargetPath()).append("|");
        sb.append("total_bytes:").append(certificate.getTotalBytes()).append("|");
        sb.append("total_files:").append(certificate.getTotalFiles()).append("|");
        sb.append("passes_completed:").append(certificate.getPassesCompleted()).append("|");
        sb.append("blanking_enabled:").append(certificate.isBlankingEnabled()).append("|");
        sb.append("verification_enabled:").append(certificate.isVerificationEnabled()).append("|");
        sb.append("wipe_method:").append(certificate.getWipeMethod()).append("|");
        sb.append("duration_millis:").append(certificate.getDurationMillis()).append("|");
        sb.append("average_speed:").append(certificate.getAverageSpeedMBps()).append("|");
        
        if (certificate.getChecksumBefore() != null) {
            sb.append("checksum_before:").append(certificate.getChecksumBefore()).append("|");
        }
        if (certificate.getChecksumAfter() != null) {
            sb.append("checksum_after:").append(certificate.getChecksumAfter()).append("|");
        }
        
        if (certificate.getSecurityWarnings() != null) {
            sb.append("security_warnings:").append(String.join(",", certificate.getSecurityWarnings())).append("|");
        }
        
        return sb.toString();
    }
    
    /**
     * Ensures a key pair exists in the Android Keystore
     */
    private static boolean ensureKeyPairExists() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            
            // Check if key already exists
            if (keyStore.containsAlias(KEY_ALIAS)) {
                Log.d(TAG, "Key pair already exists");
                return true;
            }
            
            // Generate new key pair
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER);
            
            KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setUserAuthenticationRequired(false) // Don't require user authentication for signing
                .build();
            
            keyPairGenerator.initialize(keyGenParameterSpec);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            
            Log.i(TAG, "New key pair generated successfully");
            return keyPair != null;
            
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException | 
                 NoSuchProviderException | InvalidAlgorithmParameterException e) {
            Log.e(TAG, "Failed to ensure key pair exists", e);
            return false;
        }
    }
    
    /**
     * Retrieves the private key from Android Keystore
     */
    private static PrivateKey getPrivateKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            
            return (PrivateKey) keyStore.getKey(KEY_ALIAS, null);
            
        } catch (KeyStoreException | CertificateException | IOException | 
                 NoSuchAlgorithmException | UnrecoverableKeyException e) {
            Log.e(TAG, "Failed to retrieve private key", e);
            return null;
        }
    }
    
    /**
     * Retrieves the public key from Android Keystore
     */
    private static PublicKey getPublicKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            
            return keyStore.getCertificate(KEY_ALIAS).getPublicKey();
            
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to retrieve public key", e);
            return null;
        }
    }
    
    /**
     * Gets a string representation of the public key for verification purposes
     */
    public static String getPublicKeyFingerprint() {
        try {
            PublicKey publicKey = getPublicKey();
            if (publicKey == null) {
                return null;
            }
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] publicKeyBytes = publicKey.getEncoded();
            byte[] hashBytes = digest.digest(publicKeyBytes);
            
            return Base64.encodeToString(hashBytes, Base64.NO_WRAP);
            
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to generate public key fingerprint", e);
            return null;
        }
    }
}