package com.example.svgaplayer;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.util.UUID;

/**
 * Model class representing an SVGA file item in the gallery.
 * Supports Uri, Asset, and DocumentFile references for device storage renaming.
 */
public class SvgaGalleryItem {

    private final String id;
    private String displayName;
    private final Uri uri;
    private final boolean isAsset;
    private final String assetName;
    private DocumentFile documentFile;

    public SvgaGalleryItem(@NonNull String displayName, @NonNull Uri uri) {
        this.id = UUID.randomUUID().toString();
        this.displayName = displayName;
        this.uri = uri;
        this.isAsset = false;
        this.assetName = null;
        this.documentFile = null;
    }

    public SvgaGalleryItem(@NonNull String displayName, @NonNull DocumentFile documentFile) {
        this.id = UUID.randomUUID().toString();
        this.displayName = displayName;
        this.uri = documentFile.getUri();
        this.isAsset = false;
        this.assetName = null;
        this.documentFile = documentFile;
    }

    public SvgaGalleryItem(@NonNull String displayName, @NonNull String assetName) {
        this.id = UUID.randomUUID().toString();
        this.displayName = displayName;
        this.uri = null;
        this.isAsset = true;
        this.assetName = assetName;
        this.documentFile = null;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(@NonNull String displayName) {
        this.displayName = displayName;
    }

    @Nullable
    public Uri getUri() {
        return uri;
    }

    public boolean isAsset() {
        return isAsset;
    }

    @Nullable
    public String getAssetName() {
        return assetName;
    }

    @Nullable
    public DocumentFile getDocumentFile() {
        return documentFile;
    }

    public void setDocumentFile(@Nullable DocumentFile documentFile) {
        this.documentFile = documentFile;
    }
}
