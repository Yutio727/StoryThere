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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import androidx.core.content.res.ResourcesCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.io.File;
import java.io.InputStream;


public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private BookListViewModel viewModel;
    private BookAdapter adapter;
    private List<Book> allBooks = new ArrayList<>();
    private String currentTab;
    private boolean isSelectionMode = false;
    private TextView toolbarTitle;
    private LinearLayout selectionModeButtons;
    private FloatingActionButton fabAddBook;
    
    // Bottom navigation views
    private ImageView iconHome, iconSearch, iconMyBooks, iconProfile;
    private TextView textHome, textSearch, textMyBooks, textProfile;
    
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
                    Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_SHORT).show();
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
        
        // Initialize bottom navigation views
        initializeBottomNavigationViews();
        
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
            // Check permissions for each book and remove invalid ones
            List<Book> validBooks = new ArrayList<>();
            for (Book book : books) {
                try {
                    Uri uri = Uri.parse(book.getFilePath());
                    boolean isValid = false;
                    
                    if ("content".equals(uri.getScheme())) {
                        // Content URI - try to open input stream
                        try (InputStream is = getContentResolver().openInputStream(uri)) {
                            isValid = (is != null);
                        }
                    } else {
                        // File path - check if file exists
                        File file = new File(book.getFilePath());
                        if (file.exists()) {
                            // Convert to content URI for better compatibility
                            try {
                                String contentUri = androidx.core.content.FileProvider.getUriForFile(
                                    MainActivity.this,
                                    getApplicationContext().getPackageName() + ".provider",
                                    file
                                ).toString();
                                // Update the book with content URI
                                book.setFilePath(contentUri);
                                viewModel.update(book);
                                isValid = true;
                                Log.d("MainActivity", "Updated book path to content URI: " + book.getTitle());
                            } catch (Exception e) {
                                Log.e("MainActivity", "Failed to convert file path to content URI: " + e.getMessage());
                                isValid = false;
                            }
                        } else {
                            isValid = false;
                        }
                    }
                    
                    if (isValid) {
                        validBooks.add(book);
                    } else {
                        // If file is not accessible, delete it from database
                        Log.w("MainActivity", "Removing invalid book from database: " + book.getTitle());
                        viewModel.delete(book);
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "Error validating book " + book.getTitle() + ": " + e.getMessage());
                    // If file is not accessible, delete it from database
                    viewModel.delete(book);
                }
            }
            allBooks = validBooks;
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
        // Set initial tab
        currentTab = getString(R.string.reading);
        filterBooksByTab(currentTab);
        
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = Objects.requireNonNull(tab.getText()).toString();
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
        
        // Setup bottom navigation
        setupBottomNavigation();
        setSelectedTab(2); // My Books is selected
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // User is not authenticated, clear cache if needed and go to Login
            Log.d("MainActivity", "User session invalid. Redirecting to Login.");
            // TODO: Clear any user cache here if you store user info
            startActivity(new Intent(this, Login.class));
            finish();
        } else {
            // Log the user's token for debug (remove in production)
            user.getIdToken(false).addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    String token = task.getResult().getToken();
                    Log.d("MainActivity", "User session valid. Token: " + token);
                } else {
                    Log.w("MainActivity", "Failed to get user token.");
                }
            });
        }
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
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    // Helper to format time as mm:ss (copied from AudioReaderActivity)
    @SuppressLint("DefaultLocale")
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    private void importBook(Uri uri) {
        String fileName = getFileName(uri);
        String fileType = getFileType(fileName);

        // Create a new Book object with minimal info
        Book book = new Book(
            fileName,
            getString(R.string.unknown_author),
            uri.toString(),
            fileType
        );
        // Do not set annotation or parse anything

        // Save the book to the database
        viewModel.insert(book);
        Toast.makeText(this, getString(R.string.book_imported) + fileName, Toast.LENGTH_SHORT).show();
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
        Intent intent = new Intent(this, BookOptionsActivity.class);
        intent.setData(Uri.parse(book.getFilePath()));
        intent.putExtra("fileType", book.getFileType());
        intent.putExtra("title", book.getTitle());
        intent.putExtra("annotation", book.getAnnotation());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private String getAnnotationWithTime(Book book) {
        String timeOfListen = book.getTimeOfListen();
        if (timeOfListen == null || timeOfListen.trim().isEmpty()) return "";
        String result = timeOfListen;
        int estimatedMinutes = -1;
        try {
            int count = Integer.parseInt(timeOfListen.trim().split(" ")[0]);
            if (book.getFileType().equals("pdf")) {
                estimatedMinutes = (int) Math.ceil(count * 300.0 / 250.0);
            } else {
                estimatedMinutes = (int) Math.ceil(count / 250.0);
            }
        } catch (Exception ignored) {}
        if (estimatedMinutes > 0) {
            // Use translations for separator and min
            String separator = " | ";
            String min = getString(R.string.min);
            result = timeOfListen + separator + estimatedMinutes + min;
        }
        return result;
    }

    private void filterBooksByTab(String tab) {
        List<Book> filteredBooks = new ArrayList<>();
        for (Book book : allBooks) {
            if (tab.equals(getString(R.string.reading))) {
                if (!book.isFavourite() && !book.isAlreadyRead()) {
                    filteredBooks.add(book);
                }
            } else if (tab.equals(getString(R.string.favourite))) {
                if (book.isFavourite()) {
                    filteredBooks.add(book);
                }
            } else if (tab.equals(getString(R.string.already_read))) {
                if (book.isAlreadyRead()) {
                    filteredBooks.add(book);
                }
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

    @SuppressLint("SetTextI18n")
    private void updateToolbarTitle(int selectedCount) {
        if (isSelectionMode) {
            toolbarTitle.setText(selectedCount + getString(R.string.selected));
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

        // Update button text and icon based on current tab
        if (currentTab.equals(getString(R.string.favourite))) {
            btnAddToFavourite.setText(R.string.remove_from_favourite);
            btnAddToFavourite.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_menu_close_clear_cancel, 0, 0, 0);
        } else {
            btnAddToFavourite.setText(R.string.add_to_favourite);
            btnAddToFavourite.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_star_blue, 0, 0, 0);
        }

        btnDelete.setOnClickListener(v -> {
            List<Book> selectedBooks = adapter.getSelectedBooks();
            for (Book book : selectedBooks) {
                viewModel.delete(book);
            }
            bottomSheetDialog.dismiss();
            exitSelectionMode();
            Toast.makeText(this, R.string.books_deleted, Toast.LENGTH_SHORT).show();
        });

        btnAddToFavourite.setOnClickListener(v -> {
            List<Book> selectedBooks = adapter.getSelectedBooks();
            boolean isRemoving = currentTab.equals(getString(R.string.favourite));
            
            for (Book book : selectedBooks) {
                book.setFavourite(!isRemoving);
                viewModel.update(book);
            }
            bottomSheetDialog.dismiss();
            exitSelectionMode();
            Toast.makeText(this, 
                isRemoving ? getString(R.string.books_removed_from_favourites) : getString(R.string.books_added_to_favourites),
                Toast.LENGTH_SHORT).show();
        });

        bottomSheetDialog.show();
    }

    private void initializeBottomNavigationViews() {
        iconHome = findViewById(R.id.icon_home);
        iconSearch = findViewById(R.id.icon_search);
        iconMyBooks = findViewById(R.id.icon_my_books);
        iconProfile = findViewById(R.id.icon_profile);
        
        textHome = findViewById(R.id.text_home);
        textSearch = findViewById(R.id.text_search);
        textMyBooks = findViewById(R.id.text_my_books);
        textProfile = findViewById(R.id.text_profile);
    }

    private void setupBottomNavigation() {
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            startActivity(intent);
            finish();
        });
        
        findViewById(R.id.nav_search).setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivity(intent);
            finish();
        });
        
        findViewById(R.id.nav_my_books).setOnClickListener(v -> {
            // Already on My Books, do nothing
        });
        
        findViewById(R.id.nav_profile).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void setSelectedTab(int selectedIndex) {
        // Reset all icons and texts
        resetAllTabs();
        
        // Get theme-appropriate colors
        int selectedColor = getSelectedColor();
        int unselectedColor = getUnselectedColor();
        
        // Set selected tab
        switch (selectedIndex) {
            case 0: // Home
                iconHome.setColorFilter(selectedColor);
                textHome.setTextColor(selectedColor);
                textHome.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_bold));
                break;
            case 1: // Search
                iconSearch.setColorFilter(selectedColor);
                textSearch.setTextColor(selectedColor);
                textSearch.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_bold));
                break;
            case 2: // My Books
                iconMyBooks.setColorFilter(selectedColor);
                textMyBooks.setTextColor(selectedColor);
                textMyBooks.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_bold));
                break;
            case 3: // Profile
                iconProfile.setColorFilter(selectedColor);
                textProfile.setTextColor(selectedColor);
                textProfile.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_bold));
                break;
        }
    }
    
    private void resetAllTabs() {
        // Get theme-appropriate unselected color
        int unselectedColor = getUnselectedColor();
        
        // Reset all icons to unselected color
        iconHome.setColorFilter(unselectedColor);
        iconSearch.setColorFilter(unselectedColor);
        iconMyBooks.setColorFilter(unselectedColor);
        iconProfile.setColorFilter(unselectedColor);
        
        // Reset all texts to default color and normal weight
        textHome.setTextColor(unselectedColor);
        textSearch.setTextColor(unselectedColor);
        textMyBooks.setTextColor(unselectedColor);
        textProfile.setTextColor(unselectedColor);
        
        textHome.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_regular));
        textSearch.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_regular));
        textMyBooks.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_regular));
        textProfile.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_regular));
    }
    
    private int getSelectedColor() {
        // Check if we're in dark mode
        if (isDarkTheme()) {
            return ContextCompat.getColor(this, R.color.bottom_nav_selected_dark);
        } else {
            return ContextCompat.getColor(this, R.color.bottom_nav_selected_light);
        }
    }
    
    private int getUnselectedColor() {
        // Check if we're in dark mode
        if (isDarkTheme()) {
            return ContextCompat.getColor(this, R.color.bottom_nav_unselected_dark);
        } else {
            return ContextCompat.getColor(this, R.color.bottom_nav_unselected_light);
        }
    }
    
    private boolean isDarkTheme() {
        return (getResources().getConfiguration().uiMode & 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
} 