package com.example.svgaplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter mapping SvgaGalleryItem list to RecyclerView row items in FolderExplorerFragment.
 */
public class FolderFileAdapter extends RecyclerView.Adapter<FolderFileAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position, SvgaGalleryItem item);
        void onRenameClick(int position, SvgaGalleryItem item);
    }

    private final List<SvgaGalleryItem> items;
    private int activeIndex;
    private final OnItemClickListener listener;

    public FolderFileAdapter(@NonNull List<SvgaGalleryItem> items, int activeIndex, @NonNull OnItemClickListener listener) {
        this.items = items;
        this.activeIndex = activeIndex;
        this.listener = listener;
    }

    public void setActiveIndex(int activeIndex) {
        this.activeIndex = activeIndex;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SvgaGalleryItem item = items.get(position);
        holder.tvFileName.setText(item.getDisplayName());

        if (position == activeIndex) {
            holder.tvActiveBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvActiveBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onItemClick(position, item));
        holder.btnRenameFile.setOnClickListener(v -> listener.onRenameClick(position, item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileName;
        TextView tvActiveBadge;
        ImageButton btnRenameFile;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvActiveBadge = itemView.findViewById(R.id.tvActiveBadge);
            btnRenameFile = itemView.findViewById(R.id.btnRenameFile);
        }
    }
}
