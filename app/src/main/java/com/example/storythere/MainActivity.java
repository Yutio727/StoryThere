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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private BookListViewModel viewModel;
    private BookAdapter adapter;
    
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
        
        RecyclerView recyclerView = findViewById(R.id.bookRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // On click - open the book
        // On long click - show delete options
        adapter = new BookAdapter(this::onBookClick, this::showDeleteBottomSheet);
        recyclerView.setAdapter(adapter);
        
        viewModel = new ViewModelProvider(this).get(BookListViewModel.class);
        viewModel.getAllBooks().observe(this, books -> {
            adapter.setBooks(books);
        });
        
        FloatingActionButton fab = findViewById(R.id.fabAddBook);
        fab.setOnClickListener(v -> checkPermissionsAndImport());
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
    
    private void showDeleteBottomSheet(Book book) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_delete, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        TextView deleteButton = bottomSheetView.findViewById(R.id.deleteButton);
        TextView cancelButton = bottomSheetView.findViewById(R.id.cancelButton);

        deleteButton.setOnClickListener(v -> {
            viewModel.delete(book);
            bottomSheetDialog.dismiss();
            Toast.makeText(this, "Book deleted", Toast.LENGTH_SHORT).show();
        });

        cancelButton.setOnClickListener(v -> bottomSheetDialog.dismiss());

        bottomSheetDialog.show();
    }
    
    private void importBook(Uri uri) {
        // Get file information
        String fileName = getFileName(uri);
        String fileType = getFileType(fileName);
        
        // Create a new Book object
        Book book = new Book(
            fileName,
            "Unknown Author", // You might want to extract this from metadata
            uri.toString(),
            fileType
        );
        
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
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.setData(Uri.parse(book.getFilePath()));
        intent.putExtra("fileType", book.getFileType());
        intent.putExtra("title", book.getTitle());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }
} 