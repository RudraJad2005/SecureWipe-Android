package com.example.nwipe_android;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.ArrayList;
import java.util.List;

public class SystemFileAdapter extends RecyclerView.Adapter<SystemFileAdapter.SystemFileViewHolder> {

    public interface OnSystemFileClickListener {
        void onSystemFileClick(SystemFileItem fileItem);
        void onSystemFileMenuClick(SystemFileItem fileItem, View view);
    }

    private List<SystemFileItem> fileItems;
    private OnSystemFileClickListener listener;

    public SystemFileAdapter(OnSystemFileClickListener listener) {
        this.fileItems = new ArrayList<>();
        this.listener = listener;
    }

    public void setFileItems(List<SystemFileItem> fileItems) {
        this.fileItems = fileItems != null ? fileItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addFileItem(SystemFileItem fileItem) {
        if (fileItem != null) {
            fileItems.add(fileItem);
            notifyItemInserted(fileItems.size() - 1);
        }
    }

    public void removeFileItem(SystemFileItem fileItem) {
        int index = fileItems.indexOf(fileItem);
        if (index >= 0) {
            fileItems.remove(index);
            notifyItemRemoved(index);
        }
    }

    public void clear() {
        int size = fileItems.size();
        fileItems.clear();
        notifyItemRangeRemoved(0, size);
    }

    @NonNull
    @Override
    public SystemFileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        try {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.file_item, parent, false);
            return new SystemFileViewHolder(view);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inflate file_item layout: " + e.getMessage(), e);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull SystemFileViewHolder holder, int position) {
        SystemFileItem fileItem = fileItems.get(position);
        holder.bind(fileItem, listener);
    }

    @Override
    public int getItemCount() {
        return fileItems.size();
    }

    public static class SystemFileViewHolder extends RecyclerView.ViewHolder {
        private ImageView fileIcon;
        private TextView fileName;
        private TextView fileDetails;
        private ImageView fileStatusIcon;
        private ChipGroup fileTagsGroup;
        private Chip systemFileChip;
        private Chip protectedFileChip;
    private Chip selectedForWipeChip;
        private MaterialButton fileMenu;

        public SystemFileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileIcon = itemView.findViewById(R.id.file_icon);
            fileName = itemView.findViewById(R.id.file_name);
            fileDetails = itemView.findViewById(R.id.file_details);
            fileStatusIcon = itemView.findViewById(R.id.file_status_icon);
            fileTagsGroup = itemView.findViewById(R.id.file_tags);
            systemFileChip = itemView.findViewById(R.id.system_file_chip);
            protectedFileChip = itemView.findViewById(R.id.protected_file_chip);
            selectedForWipeChip = itemView.findViewById(R.id.selected_for_wipe_chip);
            fileMenu = itemView.findViewById(R.id.file_menu);
        }

        public void bind(SystemFileItem fileItem, OnSystemFileClickListener listener) {
            // Set clean file name (no prefixes)
            fileName.setText(fileItem.getName());

            // Set file icon based on type
            int iconResource = getFileIconResource(fileItem);
            fileIcon.setImageResource(iconResource);

            // Set file details
            fileDetails.setText(fileItem.getDisplayInfo());

            // Handle status indicators and chips
            setupStatusIndicators(fileItem);
            setupFileChips(fileItem);

            // Apply visual states based on accessibility
            if (!fileItem.canRead()) {
                fileName.setAlpha(0.6f);
                fileDetails.setAlpha(0.6f);
                fileIcon.setAlpha(0.6f);
            } else {
                fileName.setAlpha(1.0f);
                fileDetails.setAlpha(1.0f);
                fileIcon.setAlpha(1.0f);
            }

            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSystemFileClick(fileItem);
                }
            });

            fileMenu.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSystemFileMenuClick(fileItem, v);
                }
            });

            // Update accessibility
            itemView.setContentDescription(getContentDescription(fileItem));
        }
        
        private void setupStatusIndicators(SystemFileItem fileItem) {
            // Show status icon for special conditions
            if (!fileItem.canRead()) {
                fileStatusIcon.setVisibility(View.VISIBLE);
                fileStatusIcon.setImageResource(R.drawable.ic_error_24);
                fileStatusIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), R.color.md_theme_light_error));
            } else if (fileItem.isSystemFile()) {
                fileStatusIcon.setVisibility(View.VISIBLE);
                fileStatusIcon.setImageResource(R.drawable.ic_warning_24);
                fileStatusIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), R.color.md_theme_light_tertiary));
            } else if (fileItem.isHidden()) {
                fileStatusIcon.setVisibility(View.VISIBLE);
                fileStatusIcon.setImageResource(R.drawable.ic_info_24);
                fileStatusIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), R.color.md_theme_light_secondary));
            } else {
                fileStatusIcon.setVisibility(View.GONE);
            }
        }
        
    private void setupFileChips(SystemFileItem fileItem) {
            boolean hasChips = false;
            
            // System file chip
            if (fileItem.isSystemFile()) {
                systemFileChip.setVisibility(View.VISIBLE);
                hasChips = true;
            } else {
                systemFileChip.setVisibility(View.GONE);
            }
            
            // Protected file chip (for files that can't be deleted or written to)
            if (!fileItem.canDelete() || (!fileItem.canWrite() && fileItem.getType() == FileItem.ItemType.FILE)) {
                protectedFileChip.setVisibility(View.VISIBLE);
                hasChips = true;
            } else {
                protectedFileChip.setVisibility(View.GONE);
            }
            
            // Selected for wipe chip (only for directories)
            try {
                if (fileItem.getType() == FileItem.ItemType.DIRECTORY) {
                    boolean selected = TargetPrefs.isFolderSelected(itemView.getContext(), fileItem.getFile().getAbsolutePath());
                    if (selected) {
                        selectedForWipeChip.setVisibility(View.VISIBLE);
                        hasChips = true;
                    } else {
                        selectedForWipeChip.setVisibility(View.GONE);
                    }
                } else {
                    selectedForWipeChip.setVisibility(View.GONE);
                }
            } catch (Exception ignored) { selectedForWipeChip.setVisibility(View.GONE); }

            // Show/hide the entire chip group
            fileTagsGroup.setVisibility(hasChips ? View.VISIBLE : View.GONE);
        }

        private int getFileIconResource(SystemFileItem fileItem) {
            switch (fileItem.getType()) {
                case DIRECTORY:
                    return R.drawable.ic_folder_open_24;
                case FILE:
                    return getFileTypeIconResource(fileItem.getFileExtension());
                default:
                    return R.drawable.ic_file_24;
            }
        }

        private int getFileTypeIconResource(String extension) {
            if (extension == null || extension.isEmpty()) {
                return R.drawable.ic_file_24;
            }

            switch (extension.toLowerCase()) {
                // Text files and documents
                case "txt":
                case "log":
                case "md":
                case "doc":
                case "docx":
                case "pdf":
                case "xls":
                case "xlsx":
                case "ppt":
                case "pptx":
                    return R.drawable.ic_description_24;
                
                // Images
                case "jpg":
                case "jpeg":
                case "png":
                case "gif":
                case "bmp":
                case "webp":
                    return R.drawable.ic_image_file_24;
                
                // Audio
                case "mp3":
                case "wav":
                case "ogg":
                case "m4a":
                case "flac":
                case "aac":
                    return R.drawable.ic_audio_file_24;
                
                // Video
                case "mp4":
                case "avi":
                case "mkv":
                case "mov":
                case "wmv":
                case "flv":
                case "webm":
                    return R.drawable.ic_video_file_24;
                
                // Archives
                case "zip":
                case "rar":
                case "7z":
                case "tar":
                case "gz":
                    return R.drawable.ic_folder_24; // Using folder icon for archives
                
                // Android specific and executables
                case "apk":
                case "dex":
                case "exe":
                case "msi":
                case "deb":
                case "rpm":
                    return R.drawable.ic_file_24; // Generic file for executables
                
                // Code files and config files
                case "java":
                case "kt":
                case "cpp":
                case "c":
                case "py":
                case "js":
                case "html":
                case "css":
                case "xml":
                case "json":
                case "yml":
                case "yaml":
                case "ini":
                case "cfg":
                    return R.drawable.ic_description_24; // Code as documents
                
                // Database
                case "db":
                case "sqlite":
                case "sql":
                    return R.drawable.ic_storage_24; // Database files as storage
                
                default:
                    return R.drawable.ic_file_24;
            }
        }

        private String getContentDescription(SystemFileItem fileItem) {
            StringBuilder description = new StringBuilder();
            
            String type = fileItem.getType() == FileItem.ItemType.DIRECTORY ? "Folder" : "File";
            description.append(type).append(" ").append(fileItem.getName());
            
            if (fileItem.isHidden()) {
                description.append(", Hidden");
            }
            
            if (fileItem.isSystemFile()) {
                description.append(", System file");
            }
            
            if (!fileItem.canRead()) {
                description.append(", No read access");
            }
            
            description.append(", ").append(fileItem.getDisplayInfo());
            
            return description.toString();
        }
    }
}