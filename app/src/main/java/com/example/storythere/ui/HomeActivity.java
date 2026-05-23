package com.example.storythere.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.storythere.R;
import com.example.storythere.api.ApiClient;
import com.example.storythere.api.ApiService;
import com.example.storythere.api.model.ApiAudiobook;
import android.widget.EditText;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;
import com.example.storythere.data.Book;
import com.example.storythere.data.BookRepository;
import com.example.storythere.data.Author;
import com.example.storythere.data.AuthorRepository;
import com.example.storythere.data.RecommendationTrackingRepository;
import com.example.storythere.data.RemoteBook;
import com.example.storythere.data.RemoteBookRepository;
import androidx.lifecycle.ViewModelProvider;
import com.example.storythere.adapters.BookListViewModel;
import com.example.storythere.adapters.AuthorAdapter;
import android.util.Log;
import android.widget.Button;
import com.example.storythere.data.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.storythere.adapters.RecommendBookAdapter;
import java.util.ArrayList;
import java.util.List;
import androidx.lifecycle.Observer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class HomeActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1204;
    
    private ImageView iconHome, iconSearch, iconMyBooks, iconCatalog, iconProfile;
    private TextView textHome, textSearch, textMyBooks, textCatalog, textProfile;
    private String recommendedFileUrl = null;
    private String recommendedFileType = null;
    private String recommendedTitle = null;
    private String recommendedAuthor = null;
    
    // Second book variables
    private String recommendedFileUrl2 = null;
    private String recommendedFileType2 = null;
    private String recommendedTitle2 = null;
    private String recommendedAuthor2 = null;
    
    private BookRepository bookRepository;
    private BookListViewModel viewModel;
    private boolean isDownloading = false;
    private boolean isCheckingBook = false;
    private Button adminAddBookButton;
    private static final String ADMIN_EMAIL = "dima.gurliv@gmail.com";
    private UserRepository userRepository;
    private AuthorRepository authorRepository;
    private RemoteBookRepository remoteBookRepository;
    private RecommendationTrackingRepository trackingRepository;
    private ApiService apiService;
    private AuthorAdapter authorAdapter;
    private boolean bookRecommendationsImpressed = false;
    private boolean audiobookRecommendationsImpressed = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        
        // Check if we're in offline mode
        boolean isOfflineMode = getIntent().getBooleanExtra("offline_mode", false);
        
        if (isOfflineMode) {
            showOfflineMode();
            return;
        }
        
        initializeViews();
        setupBottomNavigation();
        setSelectedTab(0); // Home is selected

        setupSearchBar();
        
        // Initialize repositories
        bookRepository = new BookRepository(getApplication());
        viewModel = new ViewModelProvider(this).get(BookListViewModel.class);
        userRepository = new UserRepository();
        authorRepository = new AuthorRepository(this);
        remoteBookRepository = new RemoteBookRepository(this);
        trackingRepository = new RecommendationTrackingRepository();
        apiService = ApiClient.getApiService();
        
        setupAdminButton();
        setupRecommendedBooksRecycler();
        setupRecommendedAudiobooksRecycler();
        setupAuthorsRecycler();
    }
    
    private void initializeViews() {
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
        
        adminAddBookButton = findViewById(R.id.button_admin_add_book);
    }
    
    private void setupBottomNavigation() {
        // Check if we're in offline mode
        boolean isOfflineMode = getIntent().getBooleanExtra("offline_mode", false);
        
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            // Already on home, do nothing
        });
        
        findViewById(R.id.nav_search).setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            if (isOfflineMode) {
                intent.putExtra("offline_mode", true);
            }
            startActivity(intent);
            finish();
        });
        
        findViewById(R.id.nav_my_books).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            if (isOfflineMode) {
                intent.putExtra("offline_mode", true);
            }
            startActivity(intent);
            finish();
        });

        findViewById(R.id.nav_catalog).setOnClickListener(v -> {
            Intent intent = new Intent(this, CatalogActivity.class);
            if (isOfflineMode) {
                intent.putExtra("offline_mode", true);
            }
            startActivity(intent);
            finish();
        });
        
        findViewById(R.id.nav_profile).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            if (isOfflineMode) {
                intent.putExtra("offline_mode", true);
            }
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

    private void setupSearchBar() {
        EditText searchBar = findViewById(R.id.search_bar);
        View searchIcon = findViewById(R.id.search_icon);
        
        // Check if we're in offline mode
        boolean isOfflineMode = getIntent().getBooleanExtra("offline_mode", false);
        
        View.OnClickListener listener = v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            if (isOfflineMode) {
                intent.putExtra("offline_mode", true);
            }
            startActivity(intent);
        };
        searchBar.setOnClickListener(listener);
        searchIcon.setOnClickListener(listener);
        searchBar.setHint(R.string.find_your_awesome_book);
    }

    private void setupAdminButton() {
        // Check if current user is admin by checking role from backend API
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && adminAddBookButton != null) {
            userRepository.getUser(currentUser.getUid(), new UserRepository.UserCallback() {
                @Override
                public void onSuccess(com.example.storythere.api.model.ApiUser user) {
                    if ("admin".equals(user.role)) {
                        adminAddBookButton.setVisibility(View.VISIBLE);
                        adminAddBookButton.setOnClickListener(v -> {
                            Intent intent = new Intent(HomeActivity.this, AddBookActivity.class);
                            startActivity(intent);
                        });
                        Log.d("HomeActivity", "Admin button shown for user: " + currentUser.getEmail());
                    } else {
                        adminAddBookButton.setVisibility(View.GONE);
                        Log.d("HomeActivity", "Admin button hidden for user: " + currentUser.getEmail());
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    adminAddBookButton.setVisibility(View.GONE);
                    Log.w("HomeActivity", "Error getting user role from API", throwable);
                }
            });
        } else {
            // No user logged in, hide admin button
            if (adminAddBookButton != null) {
                adminAddBookButton.setVisibility(View.GONE);
            }
        }
    }

    public static class RecommendedBook {
        public long id;
        public String title;
        public String author;
        public String fileUrl;
        public String fileType;
        public String image;
        public String annotation;
        public boolean isAudiobook;
        public String audioUrl;
        public String audioType;
        public int durationSeconds;
        public int slotIndex = -1;

        public RecommendedBook(String title, String author, String fileUrl, String fileType, String image, String annotation) {
            this(-1L, title, author, fileUrl, fileType, image, annotation, false, null, null, 0);
        }

        public RecommendedBook(long id, String title, String author, String fileUrl, String fileType, String image,
                               String annotation, boolean isAudiobook, String audioUrl, String audioType,
                               int durationSeconds) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.fileUrl = fileUrl;
            this.fileType = fileType;
            this.image = image;
            this.annotation = annotation;
            this.isAudiobook = isAudiobook;
            this.audioUrl = audioUrl;
            this.audioType = audioType;
            this.durationSeconds = durationSeconds;
        }
    }

    private void setupRecommendedBooksRecycler() {
        RecyclerView recyclerView = findViewById(R.id.recycler_recommend_books);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        RecommendBookAdapter adapter = new RecommendBookAdapter(new ArrayList<>(), HomeActivity.this::handleRecommendedBookClick);
        recyclerView.setAdapter(adapter);

        remoteBookRepository.getRecommendedBooks(7).observe(this, remoteBooks -> {
            if (remoteBooks == null) return;
            List<RecommendedBook> bookList = new ArrayList<>();
            int slotIndex = 0;
            for (RemoteBook book : remoteBooks) {
                RecommendedBook recommendedBook = new RecommendedBook(
                    book.getId(),
                    book.getTitle(),
                    book.getAuthor(),
                    book.getFileUrl(),
                    book.getFileType(),
                    book.getImage(),
                    book.getAnnotation(),
                    false,
                    null,
                    null,
                    0
                );
                recommendedBook.slotIndex = slotIndex++;
                bookList.add(recommendedBook);
            }
            adapter.updateBooks(bookList);
            trackBookRecommendationImpressions(bookList);
        });

        remoteBookRepository.loadRecommendedBooksFromApi(7);
    }

    private void setupRecommendedAudiobooksRecycler() {
        RecyclerView recyclerView = findViewById(R.id.recycler_recommend_audiobooks);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        RecommendBookAdapter adapter = new RecommendBookAdapter(new ArrayList<>(), HomeActivity.this::handleRecommendedBookClick);
        recyclerView.setAdapter(adapter);

        apiService.getRecommendedAudiobooks(7).enqueue(new Callback<List<ApiAudiobook>>() {
            @Override
            public void onResponse(Call<List<ApiAudiobook>> call, Response<List<ApiAudiobook>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w("HomeActivity", "Failed to load audiobook recommendations: " + response.code() + " " + errorBody(response));
                    loadFallbackAudiobooks(adapter);
                    return;
                }

                List<RecommendedBook> audiobookList = mapRecommendedAudiobooks(response.body());
                adapter.updateBooks(audiobookList);
                trackAudiobookRecommendationImpressions(audiobookList);
            }

            @Override
            public void onFailure(Call<List<ApiAudiobook>> call, Throwable t) {
                Log.w("HomeActivity", "Error loading audiobook recommendations", t);
                loadFallbackAudiobooks(adapter);
            }
        });
    }

    private void loadFallbackAudiobooks(RecommendBookAdapter adapter) {
        apiService.getAudiobooks(7, 0).enqueue(new Callback<List<ApiAudiobook>>() {
            @Override
            public void onResponse(Call<List<ApiAudiobook>> call, Response<List<ApiAudiobook>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w("HomeActivity", "Failed to load fallback audiobooks: " + response.code() + " " + errorBody(response));
                    return;
                }
                adapter.updateBooks(mapRecommendedAudiobooks(response.body()));
            }

            @Override
            public void onFailure(Call<List<ApiAudiobook>> call, Throwable t) {
                Log.w("HomeActivity", "Error loading fallback audiobooks", t);
            }
        });
    }

    private List<RecommendedBook> mapRecommendedAudiobooks(List<ApiAudiobook> audiobooks) {
        List<RecommendedBook> audiobookList = new ArrayList<>();
        int slotIndex = 0;
        for (ApiAudiobook audiobook : audiobooks) {
            String audioType = audiobook.audioType != null ? audiobook.audioType : "mp3";
            int durationSeconds = audiobook.durationSeconds != null ? audiobook.durationSeconds : 0;
            RecommendedBook recommendedBook = new RecommendedBook(
                audiobook.id,
                audiobook.title,
                audiobook.author,
                audiobook.audioUrl,
                audioType,
                audiobook.image,
                audiobook.annotation,
                true,
                audiobook.audioUrl,
                audioType,
                durationSeconds
            );
            recommendedBook.slotIndex = slotIndex++;
            audiobookList.add(recommendedBook);
        }
        return audiobookList;
    }

    private void handleRecommendedBookClick(RecommendedBook book) {
        if (book == null) return;
        if (book.isAudiobook) {
            trackingRepository.trackAudiobookEvent(
                book.id,
                RecommendationTrackingRepository.EVENT_CLICK,
                slotIndexOrNull(book),
                null,
                null
            );
            openAudiobookOptionsActivity(book);
            return;
        }
        trackingRepository.trackBookEvent(
            book.id,
            RecommendationTrackingRepository.EVENT_CLICK,
            slotIndexOrNull(book),
            null,
            null
        );
        if (!hasStoragePermission()) {
            requestStoragePermission();
            return;
        }
        String bookTitle = book.title != null ? book.title : "Unknown Title";
        String bookAuthor = book.author != null ? book.author : "Unknown Author";
        String fileUrl = book.fileUrl;
        String fileType = book.fileType;
        String imageUrl = book.image;
        String annotation = book.annotation;

        if (isDownloading || isCheckingBook) {
            Toast.makeText(this, R.string.processing_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        isCheckingBook = true;

        // First, check if the file already exists on the device
        String fileName = bookTitle + "." + fileType;
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File existingFile = new File(downloadsDir, fileName);
        
        if (existingFile.exists()) {
            Log.d("HomeActivity", "File already exists on device: " + existingFile.getAbsolutePath());
            // File exists, check if it's in our database
            checkDatabaseAndAddIfNeeded(book.id, bookTitle, bookAuthor, existingFile.getAbsolutePath(), fileType, imageUrl, annotation);
            return;
        }

        // Use a one-time observer to avoid multiple downloads
        Observer<List<Book>> observer = new Observer<List<Book>>() {
            @Override
            public void onChanged(List<Book> books) {
                viewModel.getAllBooks().removeObserver(this);
                isCheckingBook = false;
                Book existingBook = null;
                for (Book b : books) {
                    if (b.getTitle().equals(bookTitle) && b.getAuthor().equals(bookAuthor)) {
                        existingBook = b;
                        break;
                    }
                }
                if (existingBook != null) {
                    // Check if the file referenced in database actually exists
                    boolean fileExists = false;
                    try {
                        Uri bookUri = Uri.parse(existingBook.getFilePath());
                        if ("content".equals(bookUri.getScheme())) {
                            // Content URI - try to open input stream
                            try (InputStream is = getContentResolver().openInputStream(bookUri)) {
                                fileExists = (is != null);
                            }
                        } else {
                            // File path - check if file exists
                            File dbFile = new File(existingBook.getFilePath());
                            fileExists = dbFile.exists();
                        }
                    } catch (Exception e) {
                        Log.e("HomeActivity", "Error checking file existence: " + e.getMessage());
                        fileExists = false;
                    }
                    
                    if (fileExists) {
                        Log.d("HomeActivity", "Book found in database and file exists: " + existingBook.getFilePath());
                        // Always use the URI stored in the database (which should be content URI)
                        openBookOptionsActivity(book.id, Uri.parse(existingBook.getFilePath()), fileType, bookTitle, annotation);
                    } else {
                        Log.d("HomeActivity", "Book in database but file missing, will re-download");
                        // File doesn't exist, remove from database and download again
                        bookRepository.delete(existingBook);
                        startDownload(book.id, bookTitle, bookAuthor, fileUrl, fileType, imageUrl, annotation);
                    }
                } else {
                    startDownload(book.id, bookTitle, bookAuthor, fileUrl, fileType, imageUrl, annotation);
                }
            }
        };
        viewModel.getAllBooks().observe(this, observer);
    }

    private void checkDatabaseAndAddIfNeeded(long serverBookId, String title, String author, String filePath, String fileType, String imageUrl, String annotation) {
        Observer<List<Book>> observer = new Observer<List<Book>>() {
            @Override
            public void onChanged(List<Book> books) {
                viewModel.getAllBooks().removeObserver(this);
                isCheckingBook = false;
                
                // Convert file path to content URI first
                String contentUri = convertFilePathToContentUri(filePath);
                if (contentUri == null) {
                    Log.e("HomeActivity", "Failed to convert file path to content URI: " + filePath);
                    Toast.makeText(HomeActivity.this, "Error accessing file", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Check if book is already in database
                boolean bookExists = false;
                for (Book b : books) {
                    if (b.getTitle().equals(title) && b.getAuthor().equals(author)) {
                        bookExists = true;
                        // Update file path if it's different (always use content URI)
                        if (!b.getFilePath().equals(contentUri)) {
                            b.setFilePath(contentUri);
                            bookRepository.update(b);
                            Log.d("HomeActivity", "Updated file path for existing book: " + title + " to content URI");
                        }
                        break;
                    }
                }
                
                if (!bookExists) {
                    // Add to database with content URI
                    Book newBook = new Book(title, author, contentUri, fileType);
                    newBook.setPreviewImagePath(imageUrl);
                    newBook.setAnnotation(annotation);
                    bookRepository.insert(newBook);
                    Log.d("HomeActivity", "Added existing file to database: " + title + " with URI: " + contentUri);
                }
                
                // Always open the book with content URI
                openBookOptionsActivity(serverBookId, Uri.parse(contentUri), fileType, title, annotation);
            }
        };
        viewModel.getAllBooks().observe(this, observer);
    }

    private String convertFilePathToContentUri(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                // Use FileProvider to get content URI
                return androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".provider",
                    file
                ).toString();
            }
        } catch (Exception e) {
            Log.e("HomeActivity", "Error converting file path to content URI: " + e.getMessage());
        }
        return null;
    }

    private void startDownload(long serverBookId, String title, String author, String fileUrl, String fileType, String imageUrl, String annotation) {
        isDownloading = true;
        Log.d("HomeActivity", "=== DOWNLOAD START ===");
        Log.d("HomeActivity", "Title: " + title);
        Log.d("HomeActivity", "Author: " + author);
        Log.d("HomeActivity", "File URL: " + fileUrl);
        Log.d("HomeActivity", "File Type: " + fileType);
        Log.d("HomeActivity", "Image URL: " + imageUrl);
        
        Toast.makeText(this, getString(R.string.downloading), Toast.LENGTH_SHORT).show();
        String fileName = title + "." + fileType;
        Log.d("HomeActivity", "File name: " + fileName);
        
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fileUrl));
        request.setTitle(title);
        request.setDescription("Downloading book...");
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        
        // Add headers to make request more like a browser
        request.addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
        request.addRequestHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        request.addRequestHeader("Accept-Language", "en-US,en;q=0.5");
        request.addRequestHeader("Accept-Encoding", "gzip, deflate");
        request.addRequestHeader("Connection", "keep-alive");
        request.addRequestHeader("Upgrade-Insecure-Requests", "1");
        
        Log.d("HomeActivity", "Download request created with headers, enqueueing...");
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        long downloadId = dm.enqueue(request);
        Log.d("HomeActivity", "Download ID: " + downloadId);

        new Thread(() -> {
            boolean downloading = true;
            int checkCount = 0;
            while (downloading) {
                checkCount++;
                Log.d("HomeActivity", "Checking download status (attempt " + checkCount + ") for ID: " + downloadId);
                
                DownloadManager.Query q = new DownloadManager.Query();
                q.setFilterById(downloadId);
                Cursor cursor = dm.query(q);
                
                if (cursor != null && cursor.moveToFirst()) {
                    int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
                    long bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    long totalBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    
                    Log.d("HomeActivity", "Status: " + status + ", Reason: " + reason + ", Downloaded: " + bytesDownloaded + "/" + totalBytes + " bytes");
                    
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        downloading = false;
                        String uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                        Log.d("HomeActivity", "Download successful! Local URI: " + uriString);
                        
                        runOnUiThread(() -> {
                            isDownloading = false;
                            if (uriString != null) {
                                Log.d("HomeActivity", "Saving book to database...");
                                saveBookAndOpenFromServer(serverBookId, title, author, uriString, fileType, imageUrl, annotation);
                            } else {
                                Log.e("HomeActivity", "Download succeeded but local URI is null!");
                                Toast.makeText(this, R.string.download_failed_file_not_found, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        downloading = false;
                        String errorMsg = "Download failed with reason: " + reason;
                        Log.e("HomeActivity", errorMsg);
                        
                        // Log specific error reasons
                        switch (reason) {
                            case DownloadManager.ERROR_CANNOT_RESUME:
                                Log.e("HomeActivity", "ERROR_CANNOT_RESUME");
                                break;
                            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                                Log.e("HomeActivity", "ERROR_DEVICE_NOT_FOUND");
                                break;
                            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                                Log.e("HomeActivity", "ERROR_FILE_ALREADY_EXISTS");
                                break;
                            case DownloadManager.ERROR_FILE_ERROR:
                                Log.e("HomeActivity", "ERROR_FILE_ERROR");
                                break;
                            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                                Log.e("HomeActivity", "ERROR_HTTP_DATA_ERROR");
                                break;
                            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                                Log.e("HomeActivity", "ERROR_INSUFFICIENT_SPACE");
                                break;
                            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                                Log.e("HomeActivity", "ERROR_TOO_MANY_REDIRECTS");
                                break;
                            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                                Log.e("HomeActivity", "ERROR_UNHANDLED_HTTP_CODE");
                                break;
                            case DownloadManager.ERROR_UNKNOWN:
                                Log.e("HomeActivity", "ERROR_UNKNOWN");
                                break;
                            default:
                                Log.e("HomeActivity", "Unknown error reason: " + reason);
                                break;
                        }
                        
                        runOnUiThread(() -> {
                            isDownloading = false;
                            Toast.makeText(this, getString(R.string.download_failed), Toast.LENGTH_SHORT).show();
                        });
                    } else if (status == DownloadManager.STATUS_PAUSED) {
                        Log.d("HomeActivity", "Download paused");
                    } else if (status == DownloadManager.STATUS_PENDING) {
                        Log.d("HomeActivity", "Download pending");
                    } else if (status == DownloadManager.STATUS_RUNNING) {
                        Log.d("HomeActivity", "Download running - " + bytesDownloaded + "/" + totalBytes + " bytes");
                    }
                } else {
                    Log.e("HomeActivity", "Cursor is null or empty for download ID: " + downloadId);
                }
                
                if (cursor != null) cursor.close();
                
                // Stop checking after 60 attempts (30 seconds) to prevent infinite loop
                if (checkCount > 60) {
                    Log.e("HomeActivity", "Download timeout after 60 checks");
                    downloading = false;
                    runOnUiThread(() -> {
                        isDownloading = false;
                        Toast.makeText(this, R.string.download_timeout, Toast.LENGTH_SHORT).show();
                    });
                }
                
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
            Log.d("HomeActivity", "=== DOWNLOAD END ===");
        }).start();
    }

    private void saveBookAndOpenFromServer(long serverBookId, String title, String author, String localUriString, String fileType, String imageUrl, String annotation) {
        Book book = new Book(title, author, localUriString, fileType);
        book.setPreviewImagePath(imageUrl);
        book.setAnnotation(annotation);
        bookRepository.insert(book);
        openBookOptionsActivity(serverBookId, Uri.parse(localUriString), fileType, title, annotation);
    }

    private void openBookOptionsActivity(long serverBookId, Uri fileUri, String fileType, String title, String annotation) {
        // Update the lastOpened timestamp if this book exists in the database
        String filePath = fileUri.toString();
        androidx.lifecycle.Observer<Book> observer = new androidx.lifecycle.Observer<Book>() {
            @Override
            public void onChanged(Book existingBook) {
                // Remove this observer to prevent memory leaks
                viewModel.getBookByPath(filePath).removeObserver(this);
                if (existingBook != null) {
                    // Only update lastOpened if more than 1 second has passed
                    Date now = new Date();
                    if (existingBook.getLastOpened() == null || Math.abs(now.getTime() - existingBook.getLastOpened().getTime()) > 1000) {
                        existingBook.setLastOpened(now);
                        viewModel.update(existingBook);
                        Log.d("HomeActivity", "Updated lastOpened for book: " + title);
                    } else {
                        Log.d("HomeActivity", "Skipped updating lastOpened for book: " + title);
                    }
                }
            }
        };
        viewModel.getBookByPath(filePath).observe(this, observer);
        // Grant permissions for the URI
        try {
            getContentResolver().takePersistableUriPermission(fileUri, 
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            Log.w("HomeActivity", "Could not take persistable permission for URI: " + e.getMessage());
        }
        Intent intent = new Intent(this, BookOptionsActivity.class);
        intent.setData(fileUri);
        intent.putExtra("bookId", serverBookId);
        intent.putExtra("fromRecommendation", true);
        intent.putExtra("fileType", fileType);
        intent.putExtra("title", title);
        intent.putExtra("annotation", annotation);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void openAudiobookOptionsActivity(RecommendedBook book) {
        String audioUrl = book.audioUrl != null ? book.audioUrl : book.fileUrl;
        if (audioUrl == null || audioUrl.trim().isEmpty()) {
            Toast.makeText(this, R.string.download_failed_file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, BookOptionsActivity.class);
        intent.setData(Uri.parse(audioUrl));
        intent.putExtra("isAudiobook", true);
        intent.putExtra("fromRecommendation", true);
        intent.putExtra("audiobookId", book.id);
        intent.putExtra("audioUrl", audioUrl);
        intent.putExtra("audioType", book.audioType != null ? book.audioType : book.fileType);
        intent.putExtra("fileType", book.fileType);
        intent.putExtra("durationSeconds", book.durationSeconds);
        intent.putExtra("title", book.title);
        intent.putExtra("author", book.author);
        intent.putExtra("annotation", book.annotation);
        intent.putExtra("previewImagePath", book.image);
        startActivity(intent);
    }

    private void trackBookRecommendationImpressions(List<RecommendedBook> books) {
        if (bookRecommendationsImpressed || books == null || books.isEmpty()) {
            return;
        }
        bookRecommendationsImpressed = true;
        for (RecommendedBook book : books) {
            trackingRepository.trackBookEvent(
                book.id,
                RecommendationTrackingRepository.EVENT_IMPRESSION,
                slotIndexOrNull(book),
                null,
                null
            );
        }
    }

    private void trackAudiobookRecommendationImpressions(List<RecommendedBook> audiobooks) {
        if (audiobookRecommendationsImpressed || audiobooks == null || audiobooks.isEmpty()) {
            return;
        }
        audiobookRecommendationsImpressed = true;
        for (RecommendedBook audiobook : audiobooks) {
            trackingRepository.trackAudiobookEvent(
                audiobook.id,
                RecommendationTrackingRepository.EVENT_IMPRESSION,
                slotIndexOrNull(audiobook),
                null,
                null
            );
        }
    }

    private Integer slotIndexOrNull(RecommendedBook book) {
        return book != null && book.slotIndex >= 0 ? book.slotIndex : null;
    }

    private String errorBody(Response<?> response) {
        if (response.errorBody() == null) {
            return "";
        }
        try {
            return response.errorBody().string();
        } catch (IOException e) {
            return "";
        }
    }

    private void setupAuthorsRecycler() {
        RecyclerView recyclerView = findViewById(R.id.recycler_authors);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        
        authorAdapter = new AuthorAdapter(this, new ArrayList<>());
        recyclerView.setAdapter(authorAdapter);
        
        // Load authors from Firebase and observe changes
        authorRepository.loadAuthorsFromFirebase();
        authorRepository.getPopularAuthors(10).observe(this, new Observer<List<Author>>() {
            @Override
            public void onChanged(List<Author> authors) {
                if (authors != null) {
                    authorAdapter.updateAuthors(authors);
                }
            }
        });
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
            startActivity(intent);
        } else {
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                PERMISSION_REQUEST_CODE
            );
        }
    }

    private void showOfflineMode() {
        // Initialize views first to avoid NullPointerException
        initializeViews();
        
        // Hide all content views
        View contentContainer = findViewById(R.id.contentContainer);
        if (contentContainer != null) {
            contentContainer.setVisibility(View.GONE);
        }
        
        // Show offline message in center
        TextView offlineMessage = findViewById(R.id.offlineMessage);
        if (offlineMessage != null) {
            offlineMessage.setVisibility(View.VISIBLE);
            offlineMessage.setText(getString(R.string.offline_home_message));
        }
        
        // Setup only toolbar and bottom navigation
        setupBottomNavigation();
        setSelectedTab(0); // Home is selected
        
        // Set toolbar title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.home));
        }
    }
} 
