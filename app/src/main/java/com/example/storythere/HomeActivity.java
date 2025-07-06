package com.example.storythere;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import android.widget.EditText;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;
import com.example.storythere.data.Book;
import com.example.storythere.data.BookRepository;
import com.example.storythere.data.Author;
import com.example.storythere.data.AuthorRepository;
import androidx.lifecycle.ViewModelProvider;
import com.example.storythere.ui.BookListViewModel;
import com.example.storythere.ui.AuthorAdapter;
import android.util.Log;
import android.widget.Button;
import com.example.storythere.data.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.storythere.ui.RecommendBookAdapter;
import java.util.ArrayList;
import java.util.List;
import androidx.lifecycle.Observer;
import java.io.File;
import java.io.InputStream;
import java.util.Date;


public class HomeActivity extends AppCompatActivity {
    
    private ImageView iconHome, iconSearch, iconMyBooks, iconProfile;
    private TextView textHome, textSearch, textMyBooks, textProfile;
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
    private AuthorAdapter authorAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        
        initializeViews();
        setupBottomNavigation();
        setSelectedTab(0); // Home is selected

        setupSearchBar();
        
        // Initialize repositories
        bookRepository = new BookRepository(getApplication());
        viewModel = new ViewModelProvider(this).get(BookListViewModel.class);
        userRepository = new UserRepository();
        authorRepository = new AuthorRepository(this);
        
        setupAdminButton();
        setupRecommendedBooksRecycler();
        setupAuthorsRecycler();
    }
    
    private void initializeViews() {
        iconHome = findViewById(R.id.icon_home);
        iconSearch = findViewById(R.id.icon_search);
        iconMyBooks = findViewById(R.id.icon_my_books);
        iconProfile = findViewById(R.id.icon_profile);
        
        textHome = findViewById(R.id.text_home);
        textSearch = findViewById(R.id.text_search);
        textMyBooks = findViewById(R.id.text_my_books);
        textProfile = findViewById(R.id.text_profile);
        
        adminAddBookButton = findViewById(R.id.button_admin_add_book);
    }
    
    private void setupBottomNavigation() {
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            // Already on home, do nothing
        });
        
        findViewById(R.id.nav_search).setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivity(intent);
            finish();
        });
        
        findViewById(R.id.nav_my_books).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
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

    private void setupSearchBar() {
        EditText searchBar = findViewById(R.id.search_bar);
        View searchIcon = findViewById(R.id.search_icon);
        View.OnClickListener listener = v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivity(intent);
        };
        searchBar.setOnClickListener(listener);
        searchIcon.setOnClickListener(listener);
        searchBar.setHint(R.string.find_your_awesome_book);
    }

    private void setupAdminButton() {
        // Check if current user is admin by checking their role in Firestore
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && adminAddBookButton != null) {
            userRepository.getUser(currentUser.getUid(), new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            String userRole = document.getString("role");
                            if ("admin".equals(userRole)) {
                                // Show admin button for admin users
                                adminAddBookButton.setVisibility(View.VISIBLE);
                                adminAddBookButton.setOnClickListener(v -> {
                                    Intent intent = new Intent(HomeActivity.this, AddBookActivity.class);
                                    startActivity(intent);
                                });
                                Log.d("HomeActivity", "Admin button shown for user: " + currentUser.getEmail());
                            } else {
                                // Hide admin button for non-admin users
                                adminAddBookButton.setVisibility(View.GONE);
                                Log.d("HomeActivity", "Admin button hidden for user: " + currentUser.getEmail());
                            }
                        } else {
                            // Document doesn't exist, hide admin button
                            adminAddBookButton.setVisibility(View.GONE);
                            Log.d("HomeActivity", "User document not found, hiding admin button");
                        }
                    } else {
                        // Error getting user data, hide admin button
                        adminAddBookButton.setVisibility(View.GONE);
                        Log.w("HomeActivity", "Error getting user data", task.getException());
                    }
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
        public String title;
        public String author;
        public String fileUrl;
        public String fileType;
        public String image;
        public String annotation;
        public RecommendedBook(String title, String author, String fileUrl, String fileType, String image, String annotation) {
            this.title = title;
            this.author = author;
            this.fileUrl = fileUrl;
            this.fileType = fileType;
            this.image = image;
            this.annotation = annotation;
        }
    }

    private void setupRecommendedBooksRecycler() {
        RecyclerView recyclerView = findViewById(R.id.recycler_recommend_books);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("books")
          .limit(7)
          .get()
          .addOnSuccessListener(querySnapshot -> {
              List<RecommendedBook> bookList = new ArrayList<>();
              for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                  String title = doc.getString("title");
                  String author = doc.getString("author");
                  String fileUrl = doc.getString("fileUrl");
                  String fileType = doc.getString("fileType");
                  String image = doc.getString("image");
                  String annotation = doc.getString("annotation");
                  bookList.add(new RecommendedBook(title, author, fileUrl, fileType, image, annotation));
              }
              RecommendBookAdapter adapter = new RecommendBookAdapter(bookList, book -> {
                  handleRecommendedBookClick(book);
              });
              recyclerView.setAdapter(adapter);
          });
    }

    private void handleRecommendedBookClick(RecommendedBook book) {
        if (book == null) return;
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
            checkDatabaseAndAddIfNeeded(bookTitle, bookAuthor, existingFile.getAbsolutePath(), fileType, imageUrl, annotation);
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
                        openBookOptionsActivity(Uri.parse(existingBook.getFilePath()), fileType, bookTitle, annotation);
                    } else {
                        Log.d("HomeActivity", "Book in database but file missing, will re-download");
                        // File doesn't exist, remove from database and download again
                        bookRepository.delete(existingBook);
                        startDownload(bookTitle, bookAuthor, fileUrl, fileType, imageUrl, annotation);
                    }
                } else {
                    startDownload(bookTitle, bookAuthor, fileUrl, fileType, imageUrl, annotation);
                }
            }
        };
        viewModel.getAllBooks().observe(this, observer);
    }

    private void checkDatabaseAndAddIfNeeded(String title, String author, String filePath, String fileType, String imageUrl, String annotation) {
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
                openBookOptionsActivity(Uri.parse(contentUri), fileType, title, annotation);
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

    private void startDownload(String title, String author, String fileUrl, String fileType, String imageUrl, String annotation) {
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
                                saveBookAndOpenFromServer(title, author, uriString, fileType, imageUrl, annotation);
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

    private void saveBookAndOpenFromServer(String title, String author, String localUriString, String fileType, String imageUrl, String annotation) {
        Book book = new Book(title, author, localUriString, fileType);
        book.setPreviewImagePath(imageUrl);
        book.setAnnotation(annotation);
        bookRepository.insert(book);
        openBookOptionsActivity(Uri.parse(localUriString), fileType, title, annotation);
    }

    private void openBookOptionsActivity(Uri fileUri, String fileType, String title, String annotation) {
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
        intent.putExtra("fileType", fileType);
        intent.putExtra("title", title);
        intent.putExtra("annotation", annotation);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
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
} 