package com.example.nwipe_android;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * RecyclerView adapter for displaying certificate items
 */
public class CertificatesAdapter extends RecyclerView.Adapter<CertificatesAdapter.CertificateViewHolder> {
    
    public interface OnCertificateClickListener {
        void onCertificateClick(CertificatesViewerActivity.CertificateItem certificate, String action);
    }
    
    private final List<CertificatesViewerActivity.CertificateItem> certificates;
    private final OnCertificateClickListener clickListener;
    
    public CertificatesAdapter(List<CertificatesViewerActivity.CertificateItem> certificates, 
                              OnCertificateClickListener clickListener) {
        this.certificates = certificates;
        this.clickListener = clickListener;
    }
    
    @NonNull
    @Override
    public CertificateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_certificate, parent, false);
        return new CertificateViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull CertificateViewHolder holder, int position) {
        CertificatesViewerActivity.CertificateItem certificate = certificates.get(position);
        holder.bind(certificate, clickListener);
    }
    
    @Override
    public int getItemCount() {
        return certificates.size();
    }
    
    static class CertificateViewHolder extends RecyclerView.ViewHolder {
        private final ImageView typeIcon;
        private final TextView certificateName;
        private final TextView certificateType;
        private final TextView certificateDate;
        private final TextView certificateSize;
        private final TextView certificateStatus;
        private final MaterialButton viewButton;
        private final MaterialButton shareButton;
        
        public CertificateViewHolder(@NonNull View itemView) {
            super(itemView);
            typeIcon = itemView.findViewById(R.id.certificate_type_icon);
            certificateName = itemView.findViewById(R.id.certificate_name);
            certificateType = itemView.findViewById(R.id.certificate_type);
            certificateDate = itemView.findViewById(R.id.certificate_date);
            certificateSize = itemView.findViewById(R.id.certificate_size);
            certificateStatus = itemView.findViewById(R.id.certificate_status);
            viewButton = itemView.findViewById(R.id.view_button);
            shareButton = itemView.findViewById(R.id.share_button);
        }
        
        public void bind(CertificatesViewerActivity.CertificateItem certificate, 
                        OnCertificateClickListener clickListener) {
            // Set basic info
            certificateName.setText(certificate.getName());
            certificateType.setText(certificate.getType());
            certificateDate.setText(certificate.getFormattedDate());
            certificateSize.setText("Size: " + certificate.getFormattedSize());
            
            // Set icon based on type
            if (certificate.isPDF()) {
                typeIcon.setImageResource(android.R.drawable.ic_menu_view);
            } else if (certificate.isJSON()) {
                typeIcon.setImageResource(android.R.drawable.ic_menu_edit);
            } else {
                typeIcon.setImageResource(android.R.drawable.ic_menu_info_details);
            }
            
            // Set status (assume all certificates are signed)
            certificateStatus.setText("✓ Signed");
            certificateStatus.setTextColor(0xFF4CAF50); // Green color
            
            // Set click listeners
            viewButton.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onCertificateClick(certificate, "view");
                }
            });
            
            shareButton.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onCertificateClick(certificate, "share");
                }
            });
            
            // Set card click listener for viewing
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onCertificateClick(certificate, "view");
                }
            });
        }
    }
}