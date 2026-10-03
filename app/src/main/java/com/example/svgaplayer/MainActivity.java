package com.example.svgaplayer;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.documentfile.provider.DocumentFile;

import com.opensource.svgaplayer.SVGACallback;
import com.opensource.svgaplayer.SVGAImageView;
import com.opensource.svgaplayer.SVGAParser;
import com.opensource.svgaplayer.SVGAVideoEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private SVGAImageView svgaImageView;
    private TextView tvFileTitle;
    private TextView tvCounter;
    private View cardPlayerView;
    private View cardFileInfo;
    private View layoutGalleryNav;
    private Button btnViewFolderList;
    private Button btnThemeToggle;

    private SVGAParser svgaParser;
    private SVGAVideoEntity currentVideoEntity;
    private boolean isPlaying = false;

    // Static list so playlist survives theme re-creations
    private static final List<SvgaGalleryItem> galleryList = new ArrayList<>();
    private static int savedCurrentIndex = -1;
    private int currentIndex = -1;

    private ActivityResultLauncher<String> multiFilePickerLauncher;
    private ActivityResultLauncher<Uri> folderPickerLauncher;
    private ActivityResultLauncher<String> createDocumentLauncher;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Fullscreen Dialog components
    private Dialog fullscreenDialog;
    private SVGAImageView fullscreenSvgaView;
    private TextView tvFullscreenTitle;
    private TextView tvFullscreenCounter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make Status Bar transparent
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);

        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        if (insetsController != null) {
            boolean isNightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            insetsController.setAppearanceLightStatusBars(!isNightMode);
        }

        setContentView(R.layout.activity_main);

        // Initialize Views
        svgaImageView = findViewById(R.id.svgaImageView);
        tvFileTitle = findViewById(R.id.tvFileTitle);
        tvCounter = findViewById(R.id.tvCounter);
        cardPlayerView = findViewById(R.id.cardPlayerView);
        cardFileInfo = findViewById(R.id.cardFileInfo);
        layoutGalleryNav = findViewById(R.id.layoutGalleryNav);
        btnViewFolderList = findViewById(R.id.btnViewFolderList);

        Button btnOpenFolder = findViewById(R.id.btnOpenFolder);
        Button btnSelectFiles = findViewById(R.id.btnSelectFiles);
        View btnEditName = findViewById(R.id.btnEditName);
        Button btnFullscreen = findViewById(R.id.btnFullscreen);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);

        Button btnPrev = findViewById(R.id.btnPrev);
        Button btnNext = findViewById(R.id.btnNext);

        // Setup Theme Toggle Button (Dark Mode / Light Mode)
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            btnThemeToggle.setText("☀️ Light");
        } else {
            btnThemeToggle.setText("🌙 Dark");
        }

        btnThemeToggle.setOnClickListener(v -> {
            int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }
        });

        // Initialize SVGA Parser & Callback
        svgaParser = SVGAParser.Companion.shareParser();
        svgaParser.init(this);

        svgaImageView.setCallback(new SVGACallback() {
            @Override
            public void onFinished() {
                mainHandler.post(() -> isPlaying = false);
            }

            @Override
            public void onPause() {
                mainHandler.post(() -> isPlaying = false);
            }

            @Override
            public void onRepeat() {
                // Repeat event
            }

            @Override
            public void onStep(int frame, double percentage) {
                // Frame step event
            }
        });

        // Multi-File SAF Picker Launcher
        multiFilePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        int previousSize = galleryList.size();
                        for (Uri uri : uris) {
                            String name = getFileNameFromUri(uri);
                            galleryList.add(new SvgaGalleryItem(name, uri));
                        }
                        Toast.makeText(MainActivity.this, "Added " + uris.size() + " SVGA file(s) to gallery!", Toast.LENGTH_SHORT).show();
                        loadAndAutoPlay(previousSize);
                    }
                }
        );

        // SAF Folder Picker Launcher
        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                folderUri -> {
                    if (folderUri != null) {
                        scanAndImportFolder(folderUri);
                    }
                }
        );

        // SAF Native File Creator ("Save As to My Files") Launcher
        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"),
                destinationUri -> {
                    if (destinationUri != null && currentIndex >= 0) {
                        saveStreamToDestinationUri(galleryList.get(currentIndex), destinationUri);
                    }
                }
        );

        // Click Listeners
        btnOpenFolder.setOnClickListener(v -> folderPickerLauncher.launch(null));
        btnSelectFiles.setOnClickListener(v -> multiFilePickerLauncher.launch("*/*"));
        btnViewFolderList.setOnClickListener(v -> showFolderExplorerDialog());
        btnEditName.setOnClickListener(v -> showRenameDialogForIndex(currentIndex));
        btnFullscreen.setOnClickListener(v -> openFullscreenPlayer());

        btnPrev.setOnClickListener(v -> {
            if (currentIndex > 0) {
                loadAndAutoPlay(currentIndex - 1);
            } else if (!galleryList.isEmpty()) {
                Toast.makeText(MainActivity.this, "First file reached", Toast.LENGTH_SHORT).show();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentIndex < galleryList.size() - 1) {
                loadAndAutoPlay(currentIndex + 1);
            } else if (!galleryList.isEmpty()) {
                Toast.makeText(MainActivity.this, "Last file reached", Toast.LENGTH_SHORT).show();
            }
        });

        // Gesture Detector for Single Tap (Play/Pause) & Double Tap (Full Screen)
        GestureDetector mainGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                togglePlayPause();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                openFullscreenPlayer();
                return true;
            }
        });

        svgaImageView.setOnTouchListener((v, event) -> mainGestureDetector.onTouchEvent(event));

        // Restore active SVGA file playback across theme recreation
        if (savedCurrentIndex >= 0 && savedCurrentIndex < galleryList.size()) {
            loadAndAutoPlay(savedCurrentIndex);
        }
    }

    private void scanAndImportFolder(@NonNull Uri folderUri) {
        Toast.makeText(this, "Scanning folder...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            DocumentFile folder = DocumentFile.fromTreeUri(this, folderUri);
            List<SvgaGalleryItem> scannedItems = new ArrayList<>();

            if (folder != null && folder.exists() && folder.isDirectory()) {
                DocumentFile[] files = folder.listFiles();
                for (DocumentFile file : files) {
                    if (file.isFile() && file.getName() != null) {
                        String name = file.getName();
                        if (name.toLowerCase().endsWith(".svga") || name.toLowerCase().endsWith(".zip")) {
                            scannedItems.add(new SvgaGalleryItem(name, file));
                        }
                    }
                }
            }

            mainHandler.post(() -> {
                if (!scannedItems.isEmpty()) {
                    galleryList.clear(); // Select ONLY files from this folder!
                    galleryList.addAll(scannedItems);
                    Toast.makeText(MainActivity.this, "Loaded " + scannedItems.size() + " SVGA file(s) from folder!", Toast.LENGTH_LONG).show();
                    loadAndAutoPlay(0);
                    showFolderExplorerDialog();
                } else {
                    Toast.makeText(MainActivity.this, "No .svga files found in this folder", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void showFolderExplorerDialog() {
        if (galleryList.isEmpty()) {
            Toast.makeText(this, "No SVGA files loaded. Please open a folder or select files!", Toast.LENGTH_SHORT).show();
            return;
        }

        FolderExplorerFragment fragment = new FolderExplorerFragment(galleryList, currentIndex, new FolderExplorerFragment.FolderExplorerListener() {
            @Override
            public void onFileSelected(int position) {
                loadAndAutoPlay(position);
            }

            @Override
            public void onRenameRequested(int position) {
                showRenameDialogForIndex(position);
            }
        });
        fragment.show(getSupportFragmentManager(), "FolderExplorerFragment");
    }

    private String getFileNameFromUri(Uri uri) {
        String lastSegment = uri.getLastPathSegment();
        if (lastSegment != null && lastSegment.contains("/")) {
            lastSegment = lastSegment.substring(lastSegment.lastIndexOf('/') + 1);
        }
        return !TextUtils.isEmpty(lastSegment) ? lastSegment : "Selected SVGA";
    }

    private void loadAndAutoPlay(int index) {
        if (index < 0 || index >= galleryList.size()) {
            return;
        }

        if (cardPlayerView != null) cardPlayerView.setVisibility(View.VISIBLE);
        if (cardFileInfo != null) cardFileInfo.setVisibility(View.VISIBLE);
        if (layoutGalleryNav != null) layoutGalleryNav.setVisibility(View.VISIBLE);
        if (btnViewFolderList != null) btnViewFolderList.setVisibility(View.VISIBLE);

        currentIndex = index;
        savedCurrentIndex = index;
        SvgaGalleryItem item = galleryList.get(index);

        tvFileTitle.setText(item.getDisplayName());
        tvCounter.setText("[ " + (currentIndex + 1) + " / " + galleryList.size() + " ]");

        if (tvFullscreenTitle != null && fullscreenDialog != null && fullscreenDialog.isShowing()) {
            tvFullscreenTitle.setText(item.getDisplayName());
            tvFullscreenCounter.setText("[ " + (currentIndex + 1) + " / " + galleryList.size() + " ]");
        }

        if (item.isAsset()) {
            loadAssetAndAutoPlay(item);
        } else if (item.getUri() != null) {
            loadUriAndAutoPlay(item);
        }
    }

    private void loadAssetAndAutoPlay(SvgaGalleryItem item) {
        if (item.getAssetName() == null) return;

        svgaParser.decodeFromAssets(item.getAssetName(), new SVGAParser.ParseCompletion() {
            @Override
            public void onComplete(@NonNull SVGAVideoEntity videoItem) {
                mainHandler.post(() -> {
                    currentVideoEntity = videoItem;
                    svgaImageView.setVideoItem(videoItem);
                    svgaImageView.startAnimation();
                    isPlaying = true;

                    if (fullscreenSvgaView != null && fullscreenDialog != null && fullscreenDialog.isShowing()) {
                        fullscreenSvgaView.setVideoItem(videoItem);
                        fullscreenSvgaView.startAnimation();
                    }
                });
            }

            @Override
            public void onError() {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Failed to load asset SVGA", Toast.LENGTH_SHORT).show());
            }
        }, null);
    }

    private void loadUriAndAutoPlay(SvgaGalleryItem item) {
        if (item.getUri() == null) return;

        try {
            InputStream inputStream = getContentResolver().openInputStream(item.getUri());
            if (inputStream != null) {
                svgaParser.decodeFromInputStream(inputStream, item.getUri().toString(), new SVGAParser.ParseCompletion() {
                    @Override
                    public void onComplete(@NonNull SVGAVideoEntity videoItem) {
                        mainHandler.post(() -> {
                            currentVideoEntity = videoItem;
                            svgaImageView.setVideoItem(videoItem);
                            svgaImageView.startAnimation();
                            isPlaying = true;

                            if (fullscreenSvgaView != null && fullscreenDialog != null && fullscreenDialog.isShowing()) {
                                fullscreenSvgaView.setVideoItem(videoItem);
                                fullscreenSvgaView.startAnimation();
                            }
                        });
                    }

                    @Override
                    public void onError() {
                        mainHandler.post(() -> Toast.makeText(MainActivity.this, "Failed to parse SVGA file", Toast.LENGTH_SHORT).show());
                    }
                }, true, null, null);
            } else {
                Toast.makeText(this, "Cannot open stream for file", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error loading file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showRenameDialogForIndex(int index) {
        if (index < 0 || index >= galleryList.size()) {
            Toast.makeText(this, "No file selected to rename!", Toast.LENGTH_SHORT).show();
            return;
        }

        SvgaGalleryItem item = galleryList.get(index);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("✏️ Edit Name & Save to Phone Storage");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(item.getDisplayName());
        input.setSelection(item.getDisplayName().length());
        builder.setView(input);

        builder.setPositiveButton("Save to Downloads", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!TextUtils.isEmpty(newName)) {
                if (!newName.toLowerCase().endsWith(".svga") && !item.isAsset()) {
                    newName += ".svga";
                }

                boolean savedToStorage = exportSvgaToPublicDownloads(item, newName);

                item.setDisplayName(newName);
                if (index == currentIndex) {
                    tvFileTitle.setText(newName);
                    if (tvFullscreenTitle != null && fullscreenDialog != null && fullscreenDialog.isShowing()) {
                        tvFullscreenTitle.setText(newName);
                    }
                }

                if (savedToStorage) {
                    Toast.makeText(MainActivity.this, "Saved to My Files > Downloads!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "File name updated in app!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNeutralButton("Choose Storage Folder", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!TextUtils.isEmpty(newName)) {
                if (!newName.toLowerCase().endsWith(".svga")) {
                    newName += ".svga";
                }
                item.setDisplayName(newName);
                createDocumentLauncher.launch(newName);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private boolean exportSvgaToPublicDownloads(@NonNull SvgaGalleryItem item, @NonNull String newName) {
        if (!newName.toLowerCase().endsWith(".svga")) {
            newName += ".svga";
        }

        DocumentFile docFile = item.getDocumentFile();
        if (docFile != null && docFile.exists()) {
            boolean renamed = docFile.renameTo(newName);
            if (renamed) {
                return true;
            }
        }

        try {
            InputStream inputStream = null;
            if (item.isAsset() && item.getAssetName() != null) {
                inputStream = getAssets().open(item.getAssetName());
            } else if (item.getUri() != null) {
                inputStream = getContentResolver().openInputStream(item.getUri());
            }

            if (inputStream == null) return false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, newName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri savedUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (savedUri != null) {
                    OutputStream outputStream = getContentResolver().openOutputStream(savedUri);
                    if (outputStream != null) {
                        copyStream(inputStream, outputStream);
                        return true;
                    }
                }
            } else {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }
                File destFile = new File(downloadsDir, newName);
                FileOutputStream outputStream = new FileOutputStream(destFile);
                copyStream(inputStream, outputStream);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void saveStreamToDestinationUri(SvgaGalleryItem item, Uri destUri) {
        try {
            InputStream inputStream = null;
            if (item.isAsset() && item.getAssetName() != null) {
                inputStream = getAssets().open(item.getAssetName());
            } else if (item.getUri() != null) {
                inputStream = getContentResolver().openInputStream(item.getUri());
            }

            if (inputStream != null) {
                OutputStream outputStream = getContentResolver().openOutputStream(destUri);
                if (outputStream != null) {
                    copyStream(inputStream, outputStream);
                    Toast.makeText(this, "Saved file directly to My Files!", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Toast.makeText(this, "Failed to save file to destination", Toast.LENGTH_SHORT).show();
    }

    private void copyStream(InputStream inputStream, OutputStream outputStream) throws Exception {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }

    private void togglePlayPause() {
        if (currentVideoEntity == null) {
            Toast.makeText(this, "Please select .svga files first!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isPlaying) {
            svgaImageView.pauseAnimation();
            isPlaying = false;
            if (fullscreenSvgaView != null && fullscreenDialog != null && fullscreenDialog.isShowing()) {
                fullscreenSvgaView.pauseAnimation();
            }
        } else {
            svgaImageView.startAnimation();
            isPlaying = true;
            if (fullscreenSvgaView != null && fullscreenDialog != null && fullscreenDialog.isShowing()) {
                fullscreenSvgaView.startAnimation();
            }
        }
    }

    private void openFullscreenPlayer() {
        if (currentVideoEntity == null || currentIndex < 0) {
            Toast.makeText(this, "No SVGA animation loaded to play full screen!", Toast.LENGTH_SHORT).show();
            return;
        }

        SvgaGalleryItem currentItem = galleryList.get(currentIndex);

        fullscreenDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        fullscreenDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (fullscreenDialog.getWindow() != null) {
            fullscreenDialog.getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
        }

        RelativeLayout rootLayout = new RelativeLayout(this);
        rootLayout.setBackgroundColor(0xFF000000);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Fullscreen SVGA View
        fullscreenSvgaView = new SVGAImageView(this);
        RelativeLayout.LayoutParams svgaParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        fullscreenSvgaView.setLayoutParams(svgaParams);
        fullscreenSvgaView.setVideoItem(currentVideoEntity);
        fullscreenSvgaView.setLoops(0);
        rootLayout.addView(fullscreenSvgaView);

        // Top Header Bar
        RelativeLayout topBar = new RelativeLayout(this);
        topBar.setBackgroundColor(0x99000000);
        topBar.setPadding(32, 32, 32, 32);
        RelativeLayout.LayoutParams topParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        topParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        topBar.setLayoutParams(topParams);

        tvFullscreenTitle = new TextView(this);
        tvFullscreenTitle.setText(currentItem.getDisplayName());
        tvFullscreenTitle.setTextColor(0xFFFFFFFF);
        tvFullscreenTitle.setTextSize(16);
        RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.addRule(RelativeLayout.ALIGN_PARENT_START);
        titleParams.addRule(RelativeLayout.CENTER_VERTICAL);
        tvFullscreenTitle.setLayoutParams(titleParams);
        topBar.addView(tvFullscreenTitle);

        android.widget.ImageButton btnClose = new android.widget.ImageButton(this);
        btnClose.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        btnClose.setColorFilter(0xFFFFFFFF);
        btnClose.setBackgroundColor(0x44FFFFFF);
        btnClose.setPadding(16, 16, 16, 16);
        RelativeLayout.LayoutParams closeParams = new RelativeLayout.LayoutParams(
                100,
                100
        );
        closeParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        closeParams.addRule(RelativeLayout.CENTER_VERTICAL);
        btnClose.setLayoutParams(closeParams);
        btnClose.setOnClickListener(v -> fullscreenDialog.dismiss());
        topBar.addView(btnClose);

        rootLayout.addView(topBar);

        // Bottom Navigation Bar
        RelativeLayout bottomBar = new RelativeLayout(this);
        bottomBar.setBackgroundColor(0x99000000);
        bottomBar.setPadding(32, 24, 32, 24);
        RelativeLayout.LayoutParams bottomParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bottomParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        bottomBar.setLayoutParams(bottomParams);

        Button btnFsPrev = new Button(this);
        btnFsPrev.setText("◀ Prev");
        btnFsPrev.setTextColor(0xFFFFFFFF);
        btnFsPrev.setBackgroundColor(0x33FFFFFF);
        RelativeLayout.LayoutParams fsPrevParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        fsPrevParams.addRule(RelativeLayout.ALIGN_PARENT_START);
        fsPrevParams.addRule(RelativeLayout.CENTER_VERTICAL);
        btnFsPrev.setLayoutParams(fsPrevParams);
        btnFsPrev.setOnClickListener(v -> {
            if (currentIndex > 0) {
                loadAndAutoPlay(currentIndex - 1);
            }
        });
        bottomBar.addView(btnFsPrev);

        tvFullscreenCounter = new TextView(this);
        tvFullscreenCounter.setText("[ " + (currentIndex + 1) + " / " + galleryList.size() + " ]");
        tvFullscreenCounter.setTextColor(0xFFFFFFFF);
        tvFullscreenCounter.setTextSize(14);
        RelativeLayout.LayoutParams fsCounterParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        fsCounterParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        tvFullscreenCounter.setLayoutParams(fsCounterParams);
        bottomBar.addView(tvFullscreenCounter);

        Button btnFsNext = new Button(this);
        btnFsNext.setText("Next ▶");
        btnFsNext.setTextColor(0xFFFFFFFF);
        btnFsNext.setBackgroundColor(0x33FFFFFF);
        RelativeLayout.LayoutParams fsNextParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        fsNextParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        fsNextParams.addRule(RelativeLayout.CENTER_VERTICAL);
        btnFsNext.setLayoutParams(fsNextParams);
        btnFsNext.setOnClickListener(v -> {
            if (currentIndex < galleryList.size() - 1) {
                loadAndAutoPlay(currentIndex + 1);
            }
        });
        bottomBar.addView(btnFsNext);

        rootLayout.addView(bottomBar);

        // Fullscreen Gesture Detector: Single Tap = Play/Pause, Double Tap = Close Fullscreen
        GestureDetector fsGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                togglePlayPause();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (fullscreenDialog != null && fullscreenDialog.isShowing()) {
                    fullscreenDialog.dismiss();
                }
                return true;
            }
        });

        fullscreenSvgaView.setOnTouchListener((v, event) -> fsGestureDetector.onTouchEvent(event));

        fullscreenDialog.setContentView(rootLayout);
        fullscreenDialog.show();
        fullscreenSvgaView.startAnimation();
        isPlaying = true;
    }
}
