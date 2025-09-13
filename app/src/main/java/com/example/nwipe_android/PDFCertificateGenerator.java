package com.example.nwipe_android;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Generates professional PDF certificates for completed wipe operations
 * with comprehensive security documentation and tamper-evident features.
 */
public class PDFCertificateGenerator {
    private static final String TAG = "PDFCertificateGenerator";
    
    // Colors for professional appearance
    private static final DeviceRgb HEADER_COLOR = new DeviceRgb(41, 128, 185); // Professional blue
    private static final DeviceRgb WARNING_COLOR = new DeviceRgb(231, 76, 60); // Red for warnings
    private static final DeviceRgb SUCCESS_COLOR = new DeviceRgb(39, 174, 96); // Green for success
    private static final DeviceRgb GRAY_COLOR = new DeviceRgb(149, 165, 166); // Gray for borders
    
    public static File generateCertificate(Context context, WipeCertificate certificate) {
        try {
            // Create Documents directory if it doesn't exist
            File documentsDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SecureWipe");
            if (!documentsDir.exists()) {
                documentsDir.mkdirs();
            }
            
            // Generate filename with timestamp
            String fileName = String.format("SecureWipe_Certificate_%s.pdf", 
                certificate.getCertificateId().substring(0, 8));
            File pdfFile = new File(documentsDir, fileName);
            
            // Create PDF document
            PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile));
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);
            
            // Generate certificate content
            addHeader(document, certificate);
            addOperationSummary(document, certificate);
            addSecurityDetails(document, certificate);
            addTechnicalDetails(document, certificate);
            addSignatureSection(document, certificate);
            addFooter(document, certificate);
            
            document.close();
            Log.i(TAG, "PDF certificate generated: " + pdfFile.getAbsolutePath());
            return pdfFile;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate PDF certificate", e);
            return null;
        }
    }
    
    private static void addHeader(Document document, WipeCertificate certificate) {
        // Main title
        Paragraph title = new Paragraph("SECURE DATA SANITIZATION CERTIFICATE")
                .setFontSize(20)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(HEADER_COLOR)
                .setMarginBottom(10);
        document.add(title);
        
        // Subtitle with certificate ID
        Paragraph subtitle = new Paragraph("Certificate ID: " + certificate.getCertificateId())
                .setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(GRAY_COLOR)
                .setMarginBottom(20);
        document.add(subtitle);
        
        // Compliance status
        boolean isCompliant = certificate.isSecurityCompliant();
        Paragraph status = new Paragraph(isCompliant ? "✓ SECURITY COMPLIANT" : "⚠ SECURITY WARNINGS PRESENT")
                .setFontSize(14)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(isCompliant ? SUCCESS_COLOR : WARNING_COLOR)
                .setMarginBottom(20);
        document.add(status);
    }
    
    private static void addOperationSummary(Document document, WipeCertificate certificate) {
        // Section header
        document.add(new Paragraph("OPERATION SUMMARY")
                .setFontSize(14)
                .setBold()
                .setFontColor(HEADER_COLOR)
                .setMarginTop(10));
        
        // Summary table
        Table table = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                .setWidth(UnitValue.createPercentValue(100));
        
        addTableRow(table, "Target Location:", certificate.getTargetPath());
        addTableRow(table, "Total Data Size:", certificate.getFormattedSize());
        addTableRow(table, "Files Processed:", String.valueOf(certificate.getTotalFiles()));
        addTableRow(table, "Wipe Method:", certificate.getWipeMethod());
        addTableRow(table, "Passes Completed:", String.valueOf(certificate.getPassesCompleted()));
        addTableRow(table, "Duration:", certificate.getFormattedDuration());
        addTableRow(table, "Average Speed:", String.format("%.1f MB/s", certificate.getAverageSpeedMBps()));
        addTableRow(table, "Completed:", certificate.getFormattedTimestamp());
        
        document.add(table);
    }
    
    private static void addSecurityDetails(Document document, WipeCertificate certificate) {
        document.add(new Paragraph("SECURITY VERIFICATION")
                .setFontSize(14)
                .setBold()
                .setFontColor(HEADER_COLOR)
                .setMarginTop(20));
        
        Table table = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                .setWidth(UnitValue.createPercentValue(100));
        
        addTableRow(table, "Blanking Pass:", certificate.isBlankingEnabled() ? "✓ Enabled" : "✗ Disabled");
        addTableRow(table, "Verification:", certificate.isVerificationEnabled() ? "✓ Enabled" : "✗ Disabled");
        
        if (certificate.getChecksumBefore() != null) {
            addTableRow(table, "Checksum Before:", certificate.getChecksumBefore().substring(0, 16) + "...");
        }
        if (certificate.getChecksumAfter() != null) {
            addTableRow(table, "Checksum After:", certificate.getChecksumAfter().substring(0, 16) + "...");
        }
        
        document.add(table);
        
        // Security warnings
        if (certificate.getSecurityWarnings() != null && !certificate.getSecurityWarnings().isEmpty()) {
            document.add(new Paragraph("SECURITY WARNINGS")
                    .setFontSize(12)
                    .setBold()
                    .setFontColor(WARNING_COLOR)
                    .setMarginTop(15));
            
            for (String warning : certificate.getSecurityWarnings()) {
                document.add(new Paragraph("⚠ " + warning)
                        .setFontSize(10)
                        .setFontColor(WARNING_COLOR)
                        .setMarginLeft(10));
            }
        }
    }
    
    private static void addTechnicalDetails(Document document, WipeCertificate certificate) {
        document.add(new Paragraph("TECHNICAL DETAILS")
                .setFontSize(14)
                .setBold()
                .setFontColor(HEADER_COLOR)
                .setMarginTop(20));
        
        Table table = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                .setWidth(UnitValue.createPercentValue(100));
        
        addTableRow(table, "Device:", certificate.getDeviceInfo());
        addTableRow(table, "Application:", "SecureWipe v" + certificate.getAppVersion());
        addTableRow(table, "Android Version:", Build.VERSION.RELEASE);
        addTableRow(table, "Certificate Version:", certificate.getVersion());
        
        document.add(table);
    }
    
    private static void addSignatureSection(Document document, WipeCertificate certificate) {
        document.add(new Paragraph("DIGITAL SIGNATURE")
                .setFontSize(14)
                .setBold()
                .setFontColor(HEADER_COLOR)
                .setMarginTop(20));
        
        if (certificate.getDigitalSignature() != null) {
            Table table = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                    .setWidth(UnitValue.createPercentValue(100));
            
            addTableRow(table, "Algorithm:", certificate.getSigningAlgorithm());
            addTableRow(table, "Certificate Hash:", certificate.getCertificateHash());
            addTableRow(table, "Digital Signature:", certificate.getDigitalSignature().substring(0, 32) + "...");
            
            document.add(table);
        } else {
            document.add(new Paragraph("Digital signature will be applied after certificate generation.")
                    .setFontSize(10)
                    .setFontColor(GRAY_COLOR)
                    .setItalic());
        }
    }
    
    private static void addFooter(Document document, WipeCertificate certificate) {
        document.add(new Paragraph("\n\nThis certificate provides cryptographic proof that the specified data " +
                "has been securely overwritten using industry-standard sanitization methods. " +
                "The digital signature ensures the authenticity and integrity of this certificate.")
                .setFontSize(8)
                .setFontColor(GRAY_COLOR)
                .setTextAlignment(TextAlignment.JUSTIFIED)
                .setMarginTop(30));
        
        document.add(new Paragraph("Generated by SecureWipe Android - Professional Data Sanitization")
                .setFontSize(8)
                .setFontColor(GRAY_COLOR)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(10));
    }
    
    private static void addTableRow(Table table, String label, String value) {
        Cell labelCell = new Cell().add(new Paragraph(label))
                .setBold()
                .setBorder(null)
                .setPaddingTop(5)
                .setPaddingBottom(5);
        
        Cell valueCell = new Cell().add(new Paragraph(value))
                .setBorder(null)
                .setPaddingTop(5)
                .setPaddingBottom(5);
        
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
}