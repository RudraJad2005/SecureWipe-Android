package com.example.nwipe_android;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    public interface OnFileClickListener {
        void onFileClick(FileItem fileItem);
        void onFileMenuClick(FileItem fileItem, View view);
    }

    private List<FileItem> fileItems;
    private OnFileClickListener listener;

    public FileAdapter(OnFileClickListener listener) {
        this.fileItems = new ArrayList<>();
        this.listener = listener;
    }

    public void setFileItems(List<FileItem> fileItems) {
        this.fileItems = fileItems != null ? fileItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addFileItem(FileItem fileItem) {
        if (fileItem != null) {
            fileItems.add(fileItem);
            notifyItemInserted(fileItems.size() - 1);
        }
    }

    public void removeFileItem(FileItem fileItem) {
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
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.file_item, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileItem fileItem = fileItems.get(position);
        holder.bind(fileItem, listener);
    }

    @Override
    public int getItemCount() {
        return fileItems.size();
    }

    public static class FileViewHolder extends RecyclerView.ViewHolder {
        private ImageView fileIcon;
        private TextView fileName;
        private TextView fileDetails;
        private ImageView fileMenu;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileIcon = itemView.findViewById(R.id.file_icon);
            fileName = itemView.findViewById(R.id.file_name);
            fileDetails = itemView.findViewById(R.id.file_details);
            fileMenu = itemView.findViewById(R.id.file_menu);
        }

        public void bind(FileItem fileItem, OnFileClickListener listener) {
            // Set file name
            fileName.setText(fileItem.getName());

            // Set file icon based on type
            int iconResource = getFileIconResource(fileItem);
            fileIcon.setImageResource(iconResource);

            // Set file details
            fileDetails.setText(fileItem.getDisplayInfo());

            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFileClick(fileItem);
                }
            });

            fileMenu.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFileMenuClick(fileItem, v);
                }
            });

            // Update accessibility
            itemView.setContentDescription(getContentDescription(fileItem));
        }

        private int getFileIconResource(FileItem fileItem) {
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
                case "txt":
                case "log":
                case "doc":
                case "docx":
                case "pdf":
                    return R.drawable.ic_description_24;
                case "jpg":
                case "jpeg":
                case "png":
                case "gif":
                case "bmp":
                case "webp":
                    return R.drawable.ic_image_file_24;
                case "mp3":
                case "wav":
                case "ogg":
                case "m4a":
                case "flac":
                    return R.drawable.ic_audio_file_24;
                case "mp4":
                case "avi":
                case "mkv":
                case "mov":
                case "webm":
                    return R.drawable.ic_video_file_24;
                case "zip":
                case "rar":
                case "7z":
                case "tar":
                case "gz":
                    return R.drawable.ic_folder_24; // Using folder icon for archives
                case "apk":
                    return R.drawable.ic_file_24; // Generic file for APK
                case "xml":
                case "html":
                case "json":
                case "css":
                case "js":
                    return R.drawable.ic_description_24; // Code files as documents
                case "db":
                case "sqlite":
                    return R.drawable.ic_storage_24; // Database files as storage
                default:
                    return R.drawable.ic_file_24;
            }
        }

        private String getContentDescription(FileItem fileItem) {
            String type = fileItem.getType() == FileItem.ItemType.DIRECTORY ? "Folder" : "File";
            return type + " " + fileItem.getName() + ", " + fileItem.getDisplayInfo();
        }
    }
}