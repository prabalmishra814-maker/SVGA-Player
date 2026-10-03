package com.example.svgaplayer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

/**
 * Bottom Sheet Fragment displaying in-app folder files list with tap-to-play & rename options.
 */
public class FolderExplorerFragment extends BottomSheetDialogFragment {

    public interface FolderExplorerListener {
        void onFileSelected(int position);
        void onRenameRequested(int position);
    }

    private final List<SvgaGalleryItem> items;
    private final int activeIndex;
    private final FolderExplorerListener listener;

    private FolderFileAdapter adapter;

    public FolderExplorerFragment(@NonNull List<SvgaGalleryItem> items, int activeIndex, @NonNull FolderExplorerListener listener) {
        this.items = items;
        this.activeIndex = activeIndex;
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_folder_explorer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvExplorerTitle = view.findViewById(R.id.tvExplorerTitle);
        ImageButton btnCloseExplorer = view.findViewById(R.id.btnCloseExplorer);
        RecyclerView rvFileList = view.findViewById(R.id.rvFileList);

        tvExplorerTitle.setText("📋 Folder Explorer (" + items.size() + " Files)");
        btnCloseExplorer.setOnClickListener(v -> dismiss());

        rvFileList.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FolderFileAdapter(items, activeIndex, new FolderFileAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position, SvgaGalleryItem item) {
                if (listener != null) {
                    listener.onFileSelected(position);
                }
                dismiss();
            }

            @Override
            public void onRenameClick(int position, SvgaGalleryItem item) {
                if (listener != null) {
                    listener.onRenameRequested(position);
                }
            }
        });
        rvFileList.setAdapter(adapter);
    }

    public void refreshList() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
}
