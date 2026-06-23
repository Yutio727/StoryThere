package com.example.storythere.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
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

import com.example.storythere.R;
import com.example.storythere.api.ApiClient;
import com.example.storythere.api.ApiService;
import com.example.storythere.api.model.ApiUserAudiobook;
import com.example.storythere.api.model.ApiUserBook;
import com.example.storythere.api.model.TrackingWriteResponse;
import com.example.storythere.api.model.UserLibraryStateRequest;
import com.example.storythere.data.Book;
import com.example.storythere.adapters.BookAdapter;
import com.example.storythere.adapters.BookListViewModel;
import com.example.storythere.listening.AudiobookPlayerActivity;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int SERVER_LIBRARY_PAGE_SIZE = 100;
    private static final String AUDIO_TYPE_MP3 = "mp3";
    private static final String AUDIO_TYPE_WAV = "wav";
    private static final String MIME_AUDIO_MPEG = "audio/mpeg";
    private static final String MIME_AUDIO_WAV = "audio/wav";
    private static final String MIME_AUDIO_X_WAV = "audio/x-wav";
    private BookListViewModel viewModel;
    private BookAdapter adapter;
    private List<Book> allBooks = new ArrayList<>();
    private String currentTab;
    private boolean isSelectionMode = false;
    private TextView toolbarTitle;
    private LinearLayout selectionModeButtons;
    private FloatingActionButton fabAddBook;
    private ApiService apiService;
    private boolean isDownloading = false;
    private boolean isServerLibraryLoading = false;
    private Book pendingBookToOpen;
    private boolean pendingImportAfterPermission = false;
    
    // Bottom navigation views
    private ImageView iconHome, iconSearch, iconMyBooks, iconCatalog, iconProfile;
    private TextView textHome, textSearch, textMyBooks, textCatalog, textProfile;
    
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
                        importFile(uri);
                    }
                } else {
                    // Single file selected
                    Uri uri = data.getData();
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri, 
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        importFile(uri);
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
                    handleStoragePermissionGranted();
                } else {
                    clearPendingStorageAction();
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
        recyclerView.setHasFixedSize(true);
        recyclerView.setClipToPadding(false);
        recyclerView.addItemDecoration(new BookCardItemDecoration(
            getResources().getDimensionPixelSize(R.dimen.book_card_vertical_spacing)
        ));
        
        // Initialize adapter with selection callback
        adapter = new BookAdapter(
            this::onBookClick,
            this::onSelectionChanged
        );
        recyclerView.setAdapter(adapter);
        
        viewModel = new ViewModelProvider(this).get(BookListViewModel.class);
        apiService = ApiClient.getApiService();
        viewModel.getAllBooks().observe(this, books -> {
            Log.d("MainActivity", "Book list updated - Total books: " + books.size());
            
            // Check permissions for each book and remove invalid ones
            List<Book> validBooks = new ArrayList<>();
            for (Book book : books) {
                boolean isValid = doesBookFileExist(book);
                if (isValid || isServerBackedLibraryItem(book)) {
                    validBooks.add(book);
                } else {
                    Log.w("MainActivity", "Removing invalid local-only book from database: " + book.getTitle());
                    viewModel.delete(book);
                }
            }
            allBooks = validBooks;
            Log.d("MainActivity", "Valid books after filtering: " + validBooks.size() + " (sorted by lastOpened DESC)");
            if (!validBooks.isEmpty()) {
                Log.d("MainActivity", "First book in list: " + validBooks.get(0).getTitle() + " (lastOpened: " + validBooks.get(0).getLastOpened() + ")");
            }
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
        
        // Check if we're in offline mode
        boolean isOfflineMode = getIntent().getBooleanExtra("offline_mode", false);
        
        if (isOfflineMode) {
            Log.d("MainActivity", "Running in offline mode - skipping authentication check");
            return; // Skip authentication check in offline mode
        }
        
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // User is not authenticated, clear cache if needed and go to Login
            Log.d("MainActivity", "User session invalid. Redirecting to Login.");
            // TODO: Clear any user cache here if you store user info
            startActivity(new Intent(this, Login.class));
            finish();
            ActivityTransitions.applyFadeOpen(this);
        } else {
            // Log the user's token for debug (remove in production)
            user.getIdToken(false).addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    Log.d("MainActivity", "User session valid.");
                } else {
                    Log.w("MainActivity", "Failed to get user token.");
                }
            });
            loadServerLibraryBooks(0);
            loadServerLibraryAudiobooks(0);
        }
    }
    
    private void checkPermissionsAndImport() {
        pendingBookToOpen = null;
        pendingImportAfterPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                manageStorageLauncher.launch(intent);
            } else {
                pendingImportAfterPermission = false;
                openFilePicker();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
            } else {
                pendingImportAfterPermission = false;
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
            "text/markdown",        // .md
            MIME_AUDIO_MPEG,        // .mp3
            MIME_AUDIO_WAV,         // .wav
            MIME_AUDIO_X_WAV        // .wav
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
                handleStoragePermissionGranted();
            } else {
                clearPendingStorageAction();
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
    
    private void importFile(Uri uri) {
        String fileName = getFileName(uri);
        String mimeType = getContentResolver().getType(uri);
        String fileType = normalizeImportedFileType(fileName, mimeType);
        String filePath = uri.toString();
        boolean isAudioFile = isSupportedAudioFile(fileType, mimeType);

        Log.d("MainActivity", "Checking for existing file with path: " + filePath);

        // Check if file already exists in the current list
        Book existingBook = null;
        for (Book book : allBooks) {
            if (filePath.equals(book.getFilePath())) {
                existingBook = book;
                break;
            }
        }
        
        if (existingBook != null) {
            // File already exists, open it instead of creating duplicate
            Log.d("MainActivity", "File already exists: " + fileName + " (ID: " + existingBook.getId() + "), opening existing file");
            Toast.makeText(this, getString(R.string.book_already_exists), Toast.LENGTH_SHORT).show();
            openExistingBook(existingBook);
        } else {
            // File doesn't exist, create new library item
            Log.d("MainActivity", "Adding new file: " + fileName + " with path: " + filePath);
            Book book = new Book(
                fileName,
                getString(R.string.unknown_author),
                filePath,
                fileType
            );
            if (isAudioFile) {
                book.setAudiobook(true);
                book.setAudioType(fileType);
                book.setRemoteAudioUrl(filePath);
                book.setDurationSeconds(readAudioDurationSeconds(uri));
                book.setPlaybackPositionMs(0L);
            }

            viewModel.insert(book);
            Toast.makeText(this, getString(R.string.book_imported) + fileName, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openExistingBook(Book book) {
        if (book.isAudiobook()) {
            openServerBackedAudiobook(book);
            return;
        }

        // Only update lastOpened if more than 1 second has passed
        Date now = new Date();
        if (book.getLastOpened() == null || Math.abs(now.getTime() - book.getLastOpened().getTime()) > 1000) {
            book.setLastOpened(now);
            viewModel.update(book);
            Log.d("MainActivity", "Updated lastOpened for existing book: " + book.getTitle() + " (ID: " + book.getId() + ")");
        } else {
            Log.d("MainActivity", "Skipped updating lastOpened for existing book: " + book.getTitle() + " (ID: " + book.getId() + ")");
        }
        Intent intent = new Intent(this, BookOptionsActivity.class);
        intent.setData(Uri.parse(book.getFilePath()));
        intent.putExtra("fileType", book.getFileType());
        intent.putExtra("title", book.getTitle());
        intent.putExtra("annotation", book.getAnnotation());
        intent.putExtra("license", book.getLicense());
        if (book.getServerBookId() > 0) {
            intent.putExtra("bookId", book.getServerBookId());
            intent.putExtra("fromRecommendation", false);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
        ActivityTransitions.applyFadeOpen(this);
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
        if (fileName == null) {
            return "";
        }
        int extensionIndex = fileName.lastIndexOf(".");
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(extensionIndex + 1).toLowerCase(Locale.US);
    }

    private String normalizeImportedFileType(String fileName, String mimeType) {
        String fileType = getFileType(fileName);
        if (AUDIO_TYPE_MP3.equals(fileType) || AUDIO_TYPE_WAV.equals(fileType)) {
            return fileType;
        }
        if (MIME_AUDIO_MPEG.equals(mimeType)) {
            return AUDIO_TYPE_MP3;
        }
        if (MIME_AUDIO_WAV.equals(mimeType) || MIME_AUDIO_X_WAV.equals(mimeType)) {
            return AUDIO_TYPE_WAV;
        }
        return fileType;
    }

    private boolean isSupportedAudioFile(String fileType, String mimeType) {
        return AUDIO_TYPE_MP3.equals(fileType)
            || AUDIO_TYPE_WAV.equals(fileType)
            || MIME_AUDIO_MPEG.equals(mimeType)
            || MIME_AUDIO_WAV.equals(mimeType)
            || MIME_AUDIO_X_WAV.equals(mimeType);
    }

    private int readAudioDurationSeconds(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationMs == null || durationMs.trim().isEmpty()) {
                return 0;
            }
            return (int) Math.max(0L, Long.parseLong(durationMs) / 1000L);
        } catch (RuntimeException e) {
            Log.w("MainActivity", "Failed to read audio duration: " + uri, e);
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (Exception e) {
                Log.w("MainActivity", "Failed to release metadata retriever", e);
            }
        }
    }
    
    private void onBookClick(Book book) {
        if (book == null) {
            return;
        }
        if (book.isAudiobook()) {
            openServerBackedAudiobook(book);
            return;
        }
        if (!hasStoragePermission()) {
            pendingBookToOpen = book;
            pendingImportAfterPermission = false;
            requestStoragePermission();
            return;
        }
        if (isServerBackedBook(book) && !doesBookFileExist(book)) {
            openServerBackedBook(book);
            return;
        }
        if (!doesBookFileExist(book)) {
            Toast.makeText(this, R.string.download_failed_file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        // Only update lastOpened if more than 1 second has passed
        Date now = new Date();
        if (book.getLastOpened() == null || Math.abs(now.getTime() - book.getLastOpened().getTime()) > 1000) {
            book.setLastOpened(now);
            viewModel.update(book);
            Log.d("MainActivity", "Updated lastOpened for book: " + book.getTitle() + " (ID: " + book.getId() + ")");
        } else {
            Log.d("MainActivity", "Skipped updating lastOpened for book: " + book.getTitle() + " (ID: " + book.getId() + ")");
        }
        Intent intent = new Intent(this, BookOptionsActivity.class);
        intent.setData(Uri.parse(book.getFilePath()));
        intent.putExtra("fileType", book.getFileType());
        intent.putExtra("title", book.getTitle());
        intent.putExtra("annotation", book.getAnnotation());
        intent.putExtra("license", book.getLicense());
        if (book.getServerBookId() > 0) {
            intent.putExtra("bookId", book.getServerBookId());
            intent.putExtra("fromRecommendation", false);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
        ActivityTransitions.applyFadeOpen(this);
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            manageStorageLauncher.launch(intent);
        } else {
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                PERMISSION_REQUEST_CODE
            );
        }
    }

    private void handleStoragePermissionGranted() {
        Book bookToOpen = pendingBookToOpen;
        boolean shouldImport = pendingImportAfterPermission;
        clearPendingStorageAction();
        if (bookToOpen != null) {
            onBookClick(bookToOpen);
        } else if (shouldImport) {
            openFilePicker();
        }
    }

    private void clearPendingStorageAction() {
        pendingBookToOpen = null;
        pendingImportAfterPermission = false;
    }

    private void loadServerLibraryBooks(int offset) {
        if (apiService == null || isServerLibraryLoading) {
            return;
        }
        isServerLibraryLoading = true;
        apiService.getMyBooks(SERVER_LIBRARY_PAGE_SIZE, offset).enqueue(new Callback<List<ApiUserBook>>() {
            @Override
            public void onResponse(Call<List<ApiUserBook>> call, Response<List<ApiUserBook>> response) {
                isServerLibraryLoading = false;
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w("MainActivity", "Failed to load user books: " + response.code());
                    return;
                }

                List<ApiUserBook> serverBooks = response.body();
                syncServerBooksIntoRoom(serverBooks);
                if (serverBooks.size() == SERVER_LIBRARY_PAGE_SIZE) {
                    loadServerLibraryBooks(offset + serverBooks.size());
                }
            }

            @Override
            public void onFailure(Call<List<ApiUserBook>> call, Throwable t) {
                isServerLibraryLoading = false;
                Log.w("MainActivity", "Error loading user books", t);
            }
        });
    }

    private void loadServerLibraryAudiobooks(int offset) {
        if (apiService == null) {
            return;
        }
        apiService.getMyAudiobooks(SERVER_LIBRARY_PAGE_SIZE, offset).enqueue(new Callback<List<ApiUserAudiobook>>() {
            @Override
            public void onResponse(Call<List<ApiUserAudiobook>> call, Response<List<ApiUserAudiobook>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w("MainActivity", "Failed to load user audiobooks: " + response.code());
                    return;
                }

                List<ApiUserAudiobook> serverAudiobooks = response.body();
                syncServerAudiobooksIntoRoom(serverAudiobooks);
                if (serverAudiobooks.size() == SERVER_LIBRARY_PAGE_SIZE) {
                    loadServerLibraryAudiobooks(offset + serverAudiobooks.size());
                }
            }

            @Override
            public void onFailure(Call<List<ApiUserAudiobook>> call, Throwable t) {
                Log.w("MainActivity", "Error loading user audiobooks", t);
            }
        });
    }

    private void syncServerBooksIntoRoom(List<ApiUserBook> serverBooks) {
        if (serverBooks == null || serverBooks.isEmpty()) {
            return;
        }
        for (ApiUserBook apiBook : serverBooks) {
            if (apiBook == null || apiBook.id <= 0) {
                continue;
            }
            Book localBook = findLocalBookForServerBook(apiBook.id, apiBook.title, apiBook.author);
            if (localBook == null) {
                localBook = new Book(
                    safeText(apiBook.title, "Unknown Title"),
                    safeText(apiBook.author, getString(R.string.unknown_author)),
                    null,
                    safeText(apiBook.fileType, "")
                );
                mergeServerBookFields(localBook, apiBook);
                viewModel.insert(localBook);
            } else {
                mergeServerBookFields(localBook, apiBook);
                viewModel.update(localBook);
            }
        }
    }

    private void syncServerAudiobooksIntoRoom(List<ApiUserAudiobook> serverAudiobooks) {
        if (serverAudiobooks == null || serverAudiobooks.isEmpty()) {
            return;
        }
        for (ApiUserAudiobook apiAudiobook : serverAudiobooks) {
            if (apiAudiobook == null || apiAudiobook.id <= 0) {
                continue;
            }
            Book localAudiobook = findLocalAudiobookForServerAudiobook(apiAudiobook.id, apiAudiobook.title, apiAudiobook.author);
            if (localAudiobook == null) {
                localAudiobook = new Book(
                    safeText(apiAudiobook.title, "Unknown Title"),
                    safeText(apiAudiobook.author, getString(R.string.unknown_author)),
                    apiAudiobook.audioUrl,
                    safeText(apiAudiobook.audioType, "mp3")
                );
                mergeServerAudiobookFields(localAudiobook, apiAudiobook);
                viewModel.insert(localAudiobook);
            } else {
                mergeServerAudiobookFields(localAudiobook, apiAudiobook);
                viewModel.update(localAudiobook);
            }
        }
    }

    private Book findLocalBookForServerBook(long serverBookId, String title, String author) {
        for (Book book : allBooks) {
            if (!book.isAudiobook() && book.getServerBookId() == serverBookId) {
                return book;
            }
        }
        for (Book book : allBooks) {
            if (!book.isAudiobook()
                && book.getServerBookId() <= 0
                && safeEquals(book.getTitle(), title)
                && safeEquals(book.getAuthor(), author)) {
                return book;
            }
        }
        return null;
    }

    private Book findLocalAudiobookForServerAudiobook(long serverAudiobookId, String title, String author) {
        for (Book book : allBooks) {
            if (book.isAudiobook() && book.getServerAudiobookId() == serverAudiobookId) {
                return book;
            }
        }
        for (Book book : allBooks) {
            if (book.isAudiobook()
                && book.getServerAudiobookId() <= 0
                && safeEquals(book.getTitle(), title)
                && safeEquals(book.getAuthor(), author)) {
                return book;
            }
        }
        return null;
    }

    private void mergeServerBookFields(Book book, ApiUserBook apiBook) {
        book.setAudiobook(false);
        book.setServerBookId(apiBook.id);
        book.setRemoteFileUrl(apiBook.fileUrl);
        book.setServerProgress(clampProgress(apiBook.progress));
        book.setFavourite(apiBook.isFavourite);
        if (apiBook.isAlreadyRead || apiBook.progress >= 100.0) {
            book.setAlreadyRead(true);
        }
        book.setTitle(safeText(apiBook.title, book.getTitle()));
        book.setAuthor(safeText(apiBook.author, book.getAuthor()));
        book.setFileType(safeText(apiBook.fileType, book.getFileType()));
        book.setAnnotation(apiBook.annotation);
        book.setLicense(apiBook.license);
        book.setPreviewImagePath(apiBook.image);
        book.setImage(apiBook.image);
        if (apiBook.authorID != null) {
            book.setAuthorId(String.valueOf(apiBook.authorID));
        }
        Date lastOpenedAt = parseServerDate(apiBook.lastOpenedAt);
        if (lastOpenedAt != null) {
            book.setLastOpened(lastOpenedAt);
        }
    }

    private void mergeServerAudiobookFields(Book book, ApiUserAudiobook apiAudiobook) {
        book.setAudiobook(true);
        book.setServerAudiobookId(apiAudiobook.id);
        book.setRemoteAudioUrl(apiAudiobook.audioUrl);
        book.setFilePath(apiAudiobook.audioUrl);
        book.setAudioType(safeText(apiAudiobook.audioType, "mp3"));
        book.setFileType(safeText(apiAudiobook.audioType, "mp3"));
        book.setDictor(apiAudiobook.dictor);
        int durationSeconds = apiAudiobook.durationSeconds != null ? apiAudiobook.durationSeconds : 0;
        double serverProgress = clampProgress(apiAudiobook.progress);
        long playbackPositionMs = Math.max(0L, apiAudiobook.playbackPositionMs);
        if (playbackPositionMs <= 0L && durationSeconds > 0 && serverProgress > 0.0) {
            playbackPositionMs = Math.round((serverProgress / 100.0) * durationSeconds * 1000.0);
        }
        book.setDurationSeconds(durationSeconds);
        book.setPlaybackPositionMs(playbackPositionMs);
        book.setServerProgress(serverProgress);
        book.setFavourite(apiAudiobook.isFavourite);
        book.setTitle(safeText(apiAudiobook.title, book.getTitle()));
        book.setAuthor(safeText(apiAudiobook.author, book.getAuthor()));
        book.setAnnotation(apiAudiobook.annotation);
        book.setLicense(apiAudiobook.license);
        book.setPreviewImagePath(apiAudiobook.image);
        book.setImage(apiAudiobook.image);
        if (apiAudiobook.authorID != null) {
            book.setAuthorId(String.valueOf(apiAudiobook.authorID));
        }
        Date lastOpenedAt = parseServerDate(apiAudiobook.lastOpenedAt);
        if (lastOpenedAt != null) {
            book.setLastOpened(lastOpenedAt);
        }
        if (apiAudiobook.isAlreadyRead
            || apiAudiobook.progress >= 100.0
            || apiAudiobook.completedAt != null) {
            book.setAlreadyRead(true);
        }
    }

    private void openServerBackedAudiobook(Book book) {
        String audioUrl = book.getRemoteAudioUrl();
        if (audioUrl == null || audioUrl.trim().isEmpty()) {
            audioUrl = book.getFilePath();
        }
        if (audioUrl == null || audioUrl.trim().isEmpty()) {
            Toast.makeText(this, R.string.download_failed_file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        book.setLastOpened(new Date());
        viewModel.update(book);

        Intent intent = new Intent(this, BookOptionsActivity.class);
        intent.setData(Uri.parse(audioUrl));
        intent.putExtra("isAudiobook", true);
        intent.putExtra("fromRecommendation", false);
        intent.putExtra("localBookId", book.getId());
        intent.putExtra("audiobookId", book.getServerAudiobookId());
        intent.putExtra("audioUrl", audioUrl);
        intent.putExtra("audioType", safeText(book.getAudioType(), book.getFileType()));
        intent.putExtra("fileType", safeText(book.getAudioType(), book.getFileType()));
        intent.putExtra("durationSeconds", book.getDurationSeconds());
        intent.putExtra("title", book.getTitle());
        intent.putExtra("author", book.getAuthor());
        intent.putExtra("dictor", book.getDictor());
        intent.putExtra("annotation", book.getAnnotation());
        intent.putExtra("license", book.getLicense());
        intent.putExtra("previewImagePath", book.getPreviewImagePath());
        intent.putExtra(AudiobookPlayerActivity.EXTRA_START_POSITION_MS, safeLongToInt(book.getPlaybackPositionMs()));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
        ActivityTransitions.applyFadeOpen(this);
    }

    private void openServerBackedBook(Book book) {
        if (isDownloading) {
            Toast.makeText(this, R.string.processing_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }

        String existingDownloadPath = findDownloadedServerBookPath(book);
        if (existingDownloadPath != null) {
            attachExistingFileAndOpen(book, existingDownloadPath);
            return;
        }

        startServerBookDownload(book);
    }

    private String findDownloadedServerBookPath(Book book) {
        String fileName = buildDownloadFileName(book);
        if (fileName == null) {
            return null;
        }
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File existingFile = new File(downloadsDir, fileName);
        return existingFile.exists() ? existingFile.getAbsolutePath() : null;
    }

    private String buildDownloadFileName(Book book) {
        if (book == null || book.getTitle() == null || book.getTitle().trim().isEmpty()
            || book.getFileType() == null || book.getFileType().trim().isEmpty()) {
            return null;
        }
        return book.getTitle() + "." + book.getFileType();
    }

    private void attachExistingFileAndOpen(Book book, String filePath) {
        String contentUri = convertFilePathToContentUri(filePath);
        if (contentUri == null) {
            Toast.makeText(this, R.string.download_failed_file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        book.setFilePath(contentUri);
        book.setLastOpened(new Date());
        viewModel.update(book);
        openExistingBook(book);
    }

    private void startServerBookDownload(Book book) {
        String remoteFileUrl = book.getRemoteFileUrl();
        String fileName = buildDownloadFileName(book);
        if (remoteFileUrl == null || remoteFileUrl.trim().isEmpty() || fileName == null) {
            Toast.makeText(this, R.string.download_failed_file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        isDownloading = true;
        Toast.makeText(this, getString(R.string.downloading), Toast.LENGTH_SHORT).show();

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(remoteFileUrl));
        request.setTitle(book.getTitle());
        request.setDescription("Downloading book...");
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");

        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        long downloadId = downloadManager.enqueue(request);

        new Thread(() -> waitForServerBookDownload(downloadManager, downloadId, book)).start();
    }

    private void waitForServerBookDownload(DownloadManager downloadManager, long downloadId, Book book) {
        boolean downloading = true;
        int checkCount = 0;
        while (downloading) {
            checkCount++;
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            Cursor cursor = downloadManager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    downloading = false;
                    String uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    runOnUiThread(() -> {
                        isDownloading = false;
                        if (uriString != null) {
                            book.setFilePath(uriString);
                            book.setLastOpened(new Date());
                            viewModel.update(book);
                            openExistingBook(book);
                        } else {
                            Toast.makeText(this, R.string.download_failed_file_not_found, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else if (status == DownloadManager.STATUS_FAILED) {
                    downloading = false;
                    runOnUiThread(() -> {
                        isDownloading = false;
                        Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            }
            if (cursor != null) {
                cursor.close();
            }
            if (checkCount > 60) {
                downloading = false;
                runOnUiThread(() -> {
                    isDownloading = false;
                    Toast.makeText(this, R.string.download_timeout, Toast.LENGTH_SHORT).show();
                });
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                downloading = false;
            }
        }
    }

    private boolean doesBookFileExist(Book book) {
        try {
            if (book == null || book.getFilePath() == null || book.getFilePath().trim().isEmpty()) {
                return false;
            }
            Uri uri = Uri.parse(book.getFilePath());
            if ("content".equals(uri.getScheme())) {
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    return is != null;
                }
            }
            if ("file".equals(uri.getScheme())) {
                return uri.getPath() != null && new File(uri.getPath()).exists();
            }
            File file = new File(book.getFilePath());
            if (!file.exists()) {
                return false;
            }
            String contentUri = convertFilePathToContentUri(file.getAbsolutePath());
            if (contentUri != null && !contentUri.equals(book.getFilePath())) {
                book.setFilePath(contentUri);
                viewModel.update(book);
            }
            return true;
        } catch (Exception e) {
            Log.e("MainActivity", "Error validating book file: " + e.getMessage());
            return false;
        }
    }

    private String convertFilePathToContentUri(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                return androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".provider",
                    file
                ).toString();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to convert file path to content URI: " + e.getMessage());
        }
        return null;
    }

    private boolean isServerBackedBook(Book book) {
        return book != null && book.getServerBookId() > 0;
    }

    private boolean isServerBackedLibraryItem(Book book) {
        return book != null && (book.getServerBookId() > 0 || book.getServerAudiobookId() > 0);
    }

    private double clampProgress(double progress) {
        return Math.max(0.0, Math.min(100.0, progress));
    }

    private String safeText(String value, String fallback) {
        return value != null && !value.trim().isEmpty() ? value : fallback;
    }

    private boolean safeEquals(String left, String right) {
        return left != null && right != null && left.equals(right);
    }

    private int safeLongToInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private Date parseServerDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        List<String> patterns = new ArrayList<>();
        patterns.add("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        patterns.add("yyyy-MM-dd'T'HH:mm:ssXXX");
        patterns.add("yyyy-MM-dd'T'HH:mm:ss");
        patterns.add("yyyy-MM-dd HH:mm:ss");
        for (String pattern : patterns) {
            try {
                return new SimpleDateFormat(pattern, Locale.US).parse(normalized);
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    private String getAnnotationWithTime(Book book) {
        String readingStats = book.getReadingStats();
        if (readingStats == null || readingStats.trim().isEmpty()) return "";
        String result = readingStats;
        int estimatedMinutes = -1;
        try {
            int count = Integer.parseInt(readingStats.trim().split(" ")[0]);
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
            result = readingStats + separator + estimatedMinutes + min;
        }
        return result;
    }

    private void filterBooksByTab(String tab) {
        List<Book> filteredBooks = new ArrayList<>();
        for (Book book : allBooks) {
            if (tab.equals(getString(R.string.reading))) {
                if (!book.isAlreadyRead()) {
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
            bottomSheetDialog.dismiss();
            exitSelectionMode();
            deleteSelectedBooks(selectedBooks);
        });

        btnAddToFavourite.setOnClickListener(v -> {
            List<Book> selectedBooks = adapter.getSelectedBooks();
            boolean isRemoving = currentTab.equals(getString(R.string.favourite));
            bottomSheetDialog.dismiss();
            exitSelectionMode();
            updateFavouriteForSelectedBooks(selectedBooks, !isRemoving, isRemoving);
        });

        bottomSheetDialog.show();
    }

    private void updateFavouriteForSelectedBooks(
        List<Book> selectedBooks,
        boolean targetFavourite,
        boolean isRemoving
    ) {
        if (selectedBooks == null || selectedBooks.isEmpty()) {
            return;
        }

        final int[] pendingCount = {selectedBooks.size()};
        final int[] updatedCount = {0};
        final int[] failedCount = {0};

        for (Book book : selectedBooks) {
            updateRemoteFavouriteIfNeeded(book, targetFavourite, success -> {
                if (success) {
                    book.setFavourite(targetFavourite);
                    viewModel.update(book);
                    updatedCount[0]++;
                } else {
                    failedCount[0]++;
                }

                pendingCount[0]--;
                if (pendingCount[0] == 0) {
                    showFavouriteResultToast(updatedCount[0], failedCount[0], isRemoving);
                }
            });
        }
    }

    private void updateRemoteFavouriteIfNeeded(
        Book book,
        boolean targetFavourite,
        FavouriteLibraryItemCallback callback
    ) {
        if (book == null) {
            callback.onComplete(false);
            return;
        }

        if (book.isAudiobook() && book.getServerAudiobookId() > 0) {
            updateRemoteAudiobookFavourite(book, book.getServerAudiobookId(), targetFavourite, callback);
            return;
        }

        if (!book.isAudiobook() && book.getServerBookId() > 0) {
            updateRemoteBookFavourite(book, book.getServerBookId(), targetFavourite, callback);
            return;
        }

        callback.onComplete(true);
    }

    private void updateRemoteBookFavourite(
        Book book,
        long serverBookId,
        boolean targetFavourite,
        FavouriteLibraryItemCallback callback
    ) {
        if (apiService == null) {
            callback.onComplete(false);
            return;
        }

        apiService.updateMyBookLibraryState(
            serverBookId,
            buildBookLibraryStateRequest(book, targetFavourite)
        ).enqueue(new Callback<TrackingWriteResponse>() {
            @Override
            public void onResponse(Call<TrackingWriteResponse> call, Response<TrackingWriteResponse> response) {
                if (!response.isSuccessful()) {
                    Log.w("MainActivity", "Failed to update server book favourite " + serverBookId + ": " + response.code());
                }
                callback.onComplete(response.isSuccessful());
            }

            @Override
            public void onFailure(Call<TrackingWriteResponse> call, Throwable t) {
                Log.w("MainActivity", "Error updating server book favourite " + serverBookId, t);
                callback.onComplete(false);
            }
        });
    }

    private void updateRemoteAudiobookFavourite(
        Book book,
        long serverAudiobookId,
        boolean targetFavourite,
        FavouriteLibraryItemCallback callback
    ) {
        if (apiService == null) {
            callback.onComplete(false);
            return;
        }

        apiService.updateMyAudiobookLibraryState(
            serverAudiobookId,
            buildAudiobookLibraryStateRequest(book, targetFavourite)
        ).enqueue(new Callback<TrackingWriteResponse>() {
            @Override
            public void onResponse(Call<TrackingWriteResponse> call, Response<TrackingWriteResponse> response) {
                if (!response.isSuccessful()) {
                    Log.w("MainActivity", "Failed to update server audiobook favourite " + serverAudiobookId + ": " + response.code());
                }
                callback.onComplete(response.isSuccessful());
            }

            @Override
            public void onFailure(Call<TrackingWriteResponse> call, Throwable t) {
                Log.w("MainActivity", "Error updating server audiobook favourite " + serverAudiobookId, t);
                callback.onComplete(false);
            }
        });
    }

    private UserLibraryStateRequest buildBookLibraryStateRequest(Book book, boolean targetFavourite) {
        if (targetFavourite && book != null && book.isAlreadyRead()) {
            return new UserLibraryStateRequest(true, true);
        }
        return new UserLibraryStateRequest(targetFavourite);
    }

    private UserLibraryStateRequest buildAudiobookLibraryStateRequest(Book book, boolean targetFavourite) {
        if (targetFavourite && book != null && book.isAlreadyRead()) {
            return new UserLibraryStateRequest(true, true);
        }
        return new UserLibraryStateRequest(targetFavourite);
    }

    private void showFavouriteResultToast(int updatedCount, int failedCount, boolean isRemoving) {
        if (failedCount == 0) {
            Toast.makeText(this,
                isRemoving ? getString(R.string.books_removed_from_favourites) : getString(R.string.books_added_to_favourites),
                Toast.LENGTH_SHORT).show();
        } else if (updatedCount > 0) {
            Toast.makeText(this, R.string.books_favourite_update_partial_failed, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.books_favourite_update_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void deleteSelectedBooks(List<Book> selectedBooks) {
        if (selectedBooks == null || selectedBooks.isEmpty()) {
            return;
        }

        final int[] pendingCount = {selectedBooks.size()};
        final int[] deletedCount = {0};
        final int[] failedCount = {0};

        for (Book book : selectedBooks) {
            deleteRemoteLibraryItemIfNeeded(book, success -> {
                if (success) {
                    viewModel.delete(book);
                    deletedCount[0]++;
                } else {
                    failedCount[0]++;
                }

                pendingCount[0]--;
                if (pendingCount[0] == 0) {
                    showDeleteResultToast(deletedCount[0], failedCount[0]);
                }
            });
        }
    }

    private void deleteRemoteLibraryItemIfNeeded(Book book, DeleteLibraryItemCallback callback) {
        if (book == null) {
            callback.onComplete(false);
            return;
        }

        if (book.isAudiobook() && book.getServerAudiobookId() > 0) {
            deleteRemoteAudiobook(book.getServerAudiobookId(), callback);
            return;
        }

        if (!book.isAudiobook() && book.getServerBookId() > 0) {
            deleteRemoteBook(book.getServerBookId(), callback);
            return;
        }

        callback.onComplete(true);
    }

    private void deleteRemoteBook(long serverBookId, DeleteLibraryItemCallback callback) {
        if (apiService == null) {
            callback.onComplete(false);
            return;
        }

        apiService.deleteMyBook(serverBookId).enqueue(new Callback<TrackingWriteResponse>() {
            @Override
            public void onResponse(Call<TrackingWriteResponse> call, Response<TrackingWriteResponse> response) {
                if (!response.isSuccessful()) {
                    Log.w("MainActivity", "Failed to delete server book " + serverBookId + ": " + response.code());
                }
                callback.onComplete(response.isSuccessful());
            }

            @Override
            public void onFailure(Call<TrackingWriteResponse> call, Throwable t) {
                Log.w("MainActivity", "Error deleting server book " + serverBookId, t);
                callback.onComplete(false);
            }
        });
    }

    private void deleteRemoteAudiobook(long serverAudiobookId, DeleteLibraryItemCallback callback) {
        if (apiService == null) {
            callback.onComplete(false);
            return;
        }

        apiService.deleteMyAudiobook(serverAudiobookId).enqueue(new Callback<TrackingWriteResponse>() {
            @Override
            public void onResponse(Call<TrackingWriteResponse> call, Response<TrackingWriteResponse> response) {
                if (!response.isSuccessful()) {
                    Log.w("MainActivity", "Failed to delete server audiobook " + serverAudiobookId + ": " + response.code());
                }
                callback.onComplete(response.isSuccessful());
            }

            @Override
            public void onFailure(Call<TrackingWriteResponse> call, Throwable t) {
                Log.w("MainActivity", "Error deleting server audiobook " + serverAudiobookId, t);
                callback.onComplete(false);
            }
        });
    }

    private void showDeleteResultToast(int deletedCount, int failedCount) {
        if (failedCount == 0) {
            Toast.makeText(this, R.string.books_deleted, Toast.LENGTH_SHORT).show();
        } else if (deletedCount > 0) {
            Toast.makeText(this, R.string.books_delete_partial_failed, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.books_delete_failed, Toast.LENGTH_LONG).show();
        }
    }

    private interface DeleteLibraryItemCallback {
        void onComplete(boolean success);
    }

    private interface FavouriteLibraryItemCallback {
        void onComplete(boolean success);
    }

    private void initializeBottomNavigationViews() {
        iconHome = findViewById(R.id.icon_home);
        iconSearch = findViewById(R.id.icon_search);
        iconMyBooks = findViewById(R.id.icon_my_books);
        iconCatalog = findViewById(R.id.icon_catalog);
        iconProfile = findViewById(R.id.icon_profile);
        
        textHome = findViewById(R.id.text_home);
        textSearch = findViewById(R.id.text_search);
        textMyBooks = findViewById(R.id.text_my_books);
        textCatalog = findViewById(R.id.text_catalog);
        textProfile = findViewById(R.id.text_profile);
    }

    private void setupBottomNavigation() {
        // Check if we're in offline mode
        boolean isOfflineMode = getIntent().getBooleanExtra("offline_mode", false);
        
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            if (isOfflineMode) {
                intent.putExtra("offline_mode", true);
            }
            startActivity(intent);
            finish();
            ActivityTransitions.applyTabOpen(this, 3, 0);
        });
        
        findViewById(R.id.nav_search).setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            if (isOfflineMode) {
                intent.putExtra("offline_mode", true);
            }
            startActivity(intent);
            finish();
            ActivityTransitions.applySlideLeftOpen(this);
        });
        
        findViewById(R.id.nav_my_books).setOnClickListener(v -> {
            // Already on My Books, do nothing
        });

        findViewById(R.id.nav_catalog).setOnClickListener(v -> {
            Intent intent = new Intent(this, CatalogActivity.class);
            if (isOfflineMode) {
                intent.putExtra("offline_mode", true);
            }
            startActivity(intent);
            finish();
            ActivityTransitions.applyTabOpen(this, 3, 1);
        });
        
        findViewById(R.id.nav_profile).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            if (isOfflineMode) {
                intent.putExtra("offline_mode", true);
            }
            startActivity(intent);
            finish();
            ActivityTransitions.applySlideRightOpen(this);
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
            case 3: // Catalog
                iconCatalog.setColorFilter(selectedColor);
                textCatalog.setTextColor(selectedColor);
                textCatalog.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_bold));
                break;
            case 4: // Profile
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
        iconCatalog.setColorFilter(unselectedColor);
        iconProfile.setColorFilter(unselectedColor);
        
        // Reset all texts to default color and normal weight
        textHome.setTextColor(unselectedColor);
        textSearch.setTextColor(unselectedColor);
        textMyBooks.setTextColor(unselectedColor);
        textCatalog.setTextColor(unselectedColor);
        textProfile.setTextColor(unselectedColor);
        
        textHome.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_regular));
        textSearch.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_regular));
        textMyBooks.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_regular));
        textCatalog.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_regular));
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

    private static class BookCardItemDecoration extends RecyclerView.ItemDecoration {
        private final int verticalSpacing;

        BookCardItemDecoration(int verticalSpacing) {
            this.verticalSpacing = verticalSpacing;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            outRect.top = position == 0 ? verticalSpacing : verticalSpacing / 2;
            outRect.bottom = verticalSpacing / 2;
        }
    }
}
