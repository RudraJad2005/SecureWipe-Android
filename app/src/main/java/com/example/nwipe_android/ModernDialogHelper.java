package com.example.nwipe_android;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Helper class for creating modern, beautiful dialogs with Material Design 3 styling
 */
public class ModernDialogHelper {

    public interface OnConfirmListener {
        void onConfirm();
    }

    public interface OnCancelListener {
        void onCancel();
    }

    /**
     * Creates a modern file operation confirmation dialog
     */
    public static Dialog createFileOperationDialog(Context context, SystemFileItem fileItem, 
            String title, String message, String confirmText, String warningMessage,
            OnConfirmListener confirmListener, OnCancelListener cancelListener) {
        
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_file_operation, null);
        
        // Set up dialog elements
        ImageView dialogIcon = dialogView.findViewById(R.id.dialog_icon);
        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        TextView dialogMessage = dialogView.findViewById(R.id.dialog_message);
        
        // File info elements
        ImageView fileTypeIcon = dialogView.findViewById(R.id.file_type_icon);
        TextView fileNameText = dialogView.findViewById(R.id.file_name_text);
        TextView fileDetailsText = dialogView.findViewById(R.id.file_details_text);
        
        // Warning elements
        MaterialCardView warningCard = dialogView.findViewById(R.id.warning_card);
        TextView warningMessageText = dialogView.findViewById(R.id.warning_message);
        
        // Buttons
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancel_button);
        MaterialButton confirmButton = dialogView.findViewById(R.id.confirm_button);
        
        // Set content
        dialogTitle.setText(title);
        dialogMessage.setText(message);
        confirmButton.setText(confirmText);
        
        // Set file information
        fileNameText.setText(fileItem.getName());
        String details = String.format("%s • %s", 
            fileItem.getType() == FileItem.ItemType.DIRECTORY ? "Directory" : "File",
            fileItem.getFormattedSize());
        fileDetailsText.setText(details);
        
        // Set appropriate icon based on file type
        if (fileItem.getType() == FileItem.ItemType.DIRECTORY) {
            fileTypeIcon.setImageResource(R.drawable.ic_folder_24);
        } else {
            String extension = fileItem.getFileExtension().toLowerCase();
            if (extension.matches("jpg|jpeg|png|gif|bmp|webp")) {
                fileTypeIcon.setImageResource(R.drawable.ic_image_file_24);
            } else if (extension.matches("mp3|wav|ogg|m4a|flac")) {
                fileTypeIcon.setImageResource(R.drawable.ic_audio_file_24);
            } else if (extension.matches("mp4|avi|mkv|mov|wmv")) {
                fileTypeIcon.setImageResource(R.drawable.ic_video_file_24);
            } else {
                fileTypeIcon.setImageResource(R.drawable.ic_file_24);
            }
        }
        
        // Show warning if applicable
        if (warningMessage != null && !warningMessage.isEmpty()) {
            warningCard.setVisibility(View.VISIBLE);
            warningMessageText.setText(warningMessage);
            dialogIcon.setImageResource(R.drawable.ic_warning_24);
        } else {
            warningCard.setVisibility(View.GONE);
            dialogIcon.setImageResource(R.drawable.ic_info_24);
        }
        
        // Create dialog
        Dialog dialog = new MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create();
        
        // Set up button listeners
        cancelButton.setOnClickListener(v -> {
            dialog.dismiss();
            if (cancelListener != null) {
                cancelListener.onCancel();
            }
        });
        
        confirmButton.setOnClickListener(v -> {
            dialog.dismiss();
            if (confirmListener != null) {
                confirmListener.onConfirm();
            }
        });
        
        return dialog;
    }

    /**
     * Creates a modern file information dialog
     */
    public static Dialog createFileInfoDialog(Context context, SystemFileItem fileItem) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_file_info, null);
        
        // File header elements
        ImageView fileIconLarge = dialogView.findViewById(R.id.file_icon_large);
        TextView fileNameLarge = dialogView.findViewById(R.id.file_name_large);
        TextView fileTypeText = dialogView.findViewById(R.id.file_type_text);
        
        // Basic information elements
        TextView fileSizeText = dialogView.findViewById(R.id.file_size_text);
        TextView fileDateText = dialogView.findViewById(R.id.file_date_text);
        TextView filePathText = dialogView.findViewById(R.id.file_path_text);
        
        // Permission elements
        ImageView readPermissionIcon = dialogView.findViewById(R.id.read_permission_icon);
        TextView readPermissionText = dialogView.findViewById(R.id.read_permission_text);
        ImageView writePermissionIcon = dialogView.findViewById(R.id.write_permission_icon);
        TextView writePermissionText = dialogView.findViewById(R.id.write_permission_text);
        ImageView deletePermissionIcon = dialogView.findViewById(R.id.delete_permission_icon);
        TextView deletePermissionText = dialogView.findViewById(R.id.delete_permission_text);
        
        // Security elements
        MaterialCardView securityInfoCard = dialogView.findViewById(R.id.security_info_card);
        TextView securityInfoText = dialogView.findViewById(R.id.security_info_text);
        
        // Close button
        MaterialButton closeButton = dialogView.findViewById(R.id.close_button);
        
        // Set file header information
        fileNameLarge.setText(fileItem.getName());
        
        if (fileItem.getType() == FileItem.ItemType.DIRECTORY) {
            fileTypeText.setText("Directory");
            fileIconLarge.setImageResource(R.drawable.ic_folder_24);
        } else {
            String extension = fileItem.getFileExtension();
            if (!extension.isEmpty()) {
                fileTypeText.setText(extension.toUpperCase() + " File");
            } else {
                fileTypeText.setText("File");
            }
            
            // Set appropriate icon
            String ext = extension.toLowerCase();
            if (ext.matches("jpg|jpeg|png|gif|bmp|webp")) {
                fileIconLarge.setImageResource(R.drawable.ic_image_file_24);
            } else if (ext.matches("mp3|wav|ogg|m4a|flac")) {
                fileIconLarge.setImageResource(R.drawable.ic_audio_file_24);
            } else if (ext.matches("mp4|avi|mkv|mov|wmv")) {
                fileIconLarge.setImageResource(R.drawable.ic_video_file_24);
            } else {
                fileIconLarge.setImageResource(R.drawable.ic_file_24);
            }
        }
        
        // Set basic information
        fileSizeText.setText(fileItem.getFormattedSize());
        fileDateText.setText(fileItem.getFormattedDate());
        filePathText.setText(fileItem.getFile().getAbsolutePath());
        
        // Set permissions
        setPermissionInfo(readPermissionIcon, readPermissionText, fileItem.canRead());
        setPermissionInfo(writePermissionIcon, writePermissionText, fileItem.canWrite());
        setPermissionInfo(deletePermissionIcon, deletePermissionText, fileItem.canDelete());
        
        // Set security information if available
        String securityInfo = fileItem.getSecurityInfo();
        if (securityInfo != null && !securityInfo.isEmpty()) {
            securityInfoCard.setVisibility(View.VISIBLE);
            securityInfoText.setText(securityInfo);
        } else {
            securityInfoCard.setVisibility(View.GONE);
        }
        
        // Create dialog
        Dialog dialog = new MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create();
        
        // Set up close button
        closeButton.setOnClickListener(v -> dialog.dismiss());
        
        return dialog;
    }
    
    private static void setPermissionInfo(ImageView icon, TextView text, boolean hasPermission) {
        if (hasPermission) {
            icon.setImageResource(R.drawable.ic_success_24);
            text.setText("Yes");
            text.setTextColor(text.getContext().getColor(R.color.md_theme_light_primary));
        } else {
            icon.setImageResource(R.drawable.ic_error_24);
            text.setText("No");
            text.setTextColor(text.getContext().getColor(R.color.md_theme_light_error));
        }
    }
}