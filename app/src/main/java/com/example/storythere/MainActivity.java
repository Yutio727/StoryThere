package com.example.storythere;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.storythere.data.Book;
import com.example.storythere.ui.BookAdapter;
import com.example.storythere.ui.BookListViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.example.storythere.TextParser;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private BookListViewModel viewModel;
    private BookAdapter adapter;
    private List<Book> allBooks = new ArrayList<>();
    private String currentTab = "Reading";
    private boolean isSelectionMode = false;
    private TextView toolbarTitle;
    private LinearLayout selectionModeButtons;
    private FloatingActionButton fabAddBook;
    
    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    // Multiple files selected
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        Uri uri = clipData.getItemAt(i).getUri();
                        getContentResolver().takePersistableUriPermission(uri, 
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        importBook(uri);
                    }
                } else {
                    // Single file selected
                    Uri uri = data.getData();
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri, 
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        importBook(uri);
                    }
                }
            }
        }
    );

    private final ActivityResultLauncher<Intent> manageStorageLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    openFilePicker();
                } else {
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    );
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize views
        toolbarTitle = findViewById(R.id.toolbarTitle);
        selectionModeButtons = findViewById(R.id.selectionModeButtons);
        fabAddBook = findViewById(R.id.fabAddBook);
        
        RecyclerView recyclerView = findViewById(R.id.bookRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // Initialize adapter with selection callback
        adapter = new BookAdapter(
            this::onBookClick,
            this::onSelectionChanged
        );
        recyclerView.setAdapter(adapter);
        
        viewModel = new ViewModelProvider(this).get(BookListViewModel.class);
        viewModel.getAllBooks().observe(this, books -> {
            allBooks = books;
            filterBooksByTab(currentTab);
        });
        
        // Setup selection mode buttons
        ImageButton btnCancelSelection = findViewById(R.id.btnCancelSelection);
        ImageButton btnMoreOptions = findViewById(R.id.btnMoreOptions);
        
        btnCancelSelection.setOnClickListener(v -> exitSelectionMode());
        btnMoreOptions.setOnClickListener(v -> showSelectionOptionsBottomSheet());
        
        fabAddBook.setOnClickListener(v -> checkPermissionsAndImport());

        // Setup TabLayout
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getText().toString();
                filterBooksByTab(currentTab);
                if (isSelectionMode) {
                    exitSelectionMode();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }
    
    private void checkPermissionsAndImport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                manageStorageLauncher.launch(intent);
            } else {
                openFilePicker();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
            } else {
                openFilePicker();
            }
        }
    }
    
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        
        // Set MIME types for supported file formats.  add .doc .rtf later ?
        String[] mimeTypes = {
            "application/pdf",      // .pdf
            "text/plain",           // .txt
            "application/epub+zip", // .epub
            "application/x-fictionbook+xml", // .fb2
            "text/html",            // .html, .htm
            "text/markdown"         // .md
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        filePickerLauncher.launch(intent);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    // Helper to format time as mm:ss (copied from AudioReaderActivity)
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    private void importBook(Uri uri) {
        // Get file information
        String fileName = getFileName(uri);
        String fileType = getFileType(fileName);

        // Calculate listening time (same as AudioReaderActivity)
        com.example.storythere.TextParser.ParsedText parsedText = com.example.storythere.TextParser.parseText(this, uri);
        int totalDuration = parsedText.content.trim().isEmpty() ? 0 : parsedText.content.trim().split("\\s+").length;
        String formattedTime = formatTime(totalDuration);

        // Create a new Book object
        Book book = new Book(
            fileName,
            "Unknown Author", // You might want to extract this from metadata
            uri.toString(),
            fileType
        );
        book.setAnnotation(formattedTime); // Store listening time in annotation

        // Save the book to the database
        viewModel.insert(book);
        Toast.makeText(this, "Book imported: " + fileName, Toast.LENGTH_SHORT).show();
    }
    
    @SuppressLint("Range")
    private String getFileName(Uri uri) {
        String result = null;
        if (Objects.equals(uri.getScheme(), "content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            assert result != null;
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
    
    private String getFileType(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
    
    private void onBookClick(Book book) {
        Intent intent;
        if (book.getFileType().equalsIgnoreCase("pdf")) {
            intent = new Intent(this, PDFViewerActivity.class);
        } else {
            intent = new Intent(this, BookOptionsActivity.class);
        }
        intent.setData(Uri.parse(book.getFilePath()));
        intent.putExtra("fileType", book.getFileType());
        intent.putExtra("title", book.getTitle());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void filterBooksByTab(String tab) {
        List<Book> filteredBooks = new ArrayList<>();
        for (Book book : allBooks) {
            switch (tab) {
                case "Reading":
                    if (!book.isFavourite() && !book.isAlreadyRead()) {
                        filteredBooks.add(book);
                    }
                    break;
                case "Favourite":
                    if (book.isFavourite()) {
                        filteredBooks.add(book);
                    }
                    break;
                case "Already Read":
                    if (book.isAlreadyRead()) {
                        filteredBooks.add(book);
                    }
                    break;
            }
        }
        adapter.setBooks(filteredBooks);
    }

    private void onSelectionChanged(int selectedCount) {
        if (selectedCount > 0 && !isSelectionMode) {
            enterSelectionMode();
        } else if (selectedCount == 0 && isSelectionMode) {
            exitSelectionMode();
        }
        updateToolbarTitle(selectedCount);
    }

    private void enterSelectionMode() {
        isSelectionMode = true;
        selectionModeButtons.setVisibility(View.VISIBLE);
        fabAddBook.setVisibility(View.GONE);
        adapter.setSelectionMode(true);
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        selectionModeButtons.setVisibility(View.GONE);
        fabAddBook.setVisibility(View.VISIBLE);
        adapter.setSelectionMode(false);
        adapter.clearSelection();
        updateToolbarTitle(0);
    }

    private void updateToolbarTitle(int selectedCount) {
        if (isSelectionMode) {
            toolbarTitle.setText(selectedCount + " selected");
        } else {
            toolbarTitle.setText("StoryThere");
        }
    }

    private void showSelectionOptionsBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        @SuppressLint("InflateParams") View bottomSheetView = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_selection_options, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        TextView btnDelete = bottomSheetView.findViewById(R.id.btnDelete);
        TextView btnAddToFavourite = bottomSheetView.findViewById(R.id.btnAddToFavourite);

        btnDelete.setOnClickListener(v -> {
            List<Book> selectedBooks = adapter.getSelectedBooks();
            for (Book book : selectedBooks) {
                viewModel.delete(book);
            }
            bottomSheetDialog.dismiss();
            exitSelectionMode();
            Toast.makeText(this, "Books deleted", Toast.LENGTH_SHORT).show();
        });

        btnAddToFavourite.setOnClickListener(v -> {
            List<Book> selectedBooks = adapter.getSelectedBooks();
            for (Book book : selectedBooks) {
                book.setFavourite(true);
                viewModel.update(book);
            }
            bottomSheetDialog.dismiss();
            exitSelectionMode();
            Toast.makeText(this, "Books added to favourites", Toast.LENGTH_SHORT).show();
        });

        bottomSheetDialog.show();
    }
} 