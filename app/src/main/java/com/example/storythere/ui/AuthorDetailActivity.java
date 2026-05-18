package com.example.storythere.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.storythere.R;
import com.example.storythere.api.ApiClient;
import com.example.storythere.api.ApiService;
import com.example.storythere.api.model.ApiBook;
import com.example.storythere.data.Author;
import com.example.storythere.data.AuthorRepository;
import com.example.storythere.data.Book;
import com.example.storythere.data.BookRepository;
import com.example.storythere.adapters.RecommendBookAdapter;
import androidx.lifecycle.Observer;
import java.util.ArrayList;
import java.util.List;
import androidx.lifecycle.ViewModelProvider;
import com.example.storythere.adapters.BookListViewModel;
import android.util.Log;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;
import java.io.File;
import java.io.InputStream;
import java.util.Date;
import android.database.Cursor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthorDetailActivity extends AppCompatActivity {
    
    private ImageView authorImage;
    private TextView authorName;
    private TextView authorBiography;
    private TextView authorLifeSpan;
    private TextView authorNationality;
    private TextView authorBooksCount;
    private RecyclerView booksRecyclerView;
    private RecommendBookAdapter booksAdapter;
    
    private AuthorRepository authorRepository;
    private BookRepository bookRepository;
    private ApiService apiService;
    private String authorId;
    private BookListViewModel viewModel;
    private boolean isDownloading = false;
    private boolean isCheckingBook = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_author_detail);
        
        authorId = getIntent().getStringExtra("authorId");
        if (authorId == null) {
            finish();
            return;
        }
        
        initializeViews();
        setupToolbar();
        setupRecyclerView();
        initializeRepositories();
        loadAuthorData();
        loadAuthorBooks();
    }
    
    private void initializeViews() {
        authorImage = findViewById(R.id.author_image);
        authorName = findViewById(R.id.author_name);
        authorBiography = findViewById(R.id.author_biography);
        authorLifeSpan = findViewById(R.id.author_life_span);
        authorNationality = findViewById(R.id.author_nationality);
        authorBooksCount = findViewById(R.id.author_books_count);
        booksRecyclerView = findViewById(R.id.books_recycler_view);
    }
    
    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
        
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
    
    private void setupRecyclerView() {
        booksAdapter = new RecommendBookAdapter(new ArrayList<>(), book -> {
            handleRecommendedBookClick(book);
        });
        booksRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        booksRecyclerView.setAdapter(booksAdapter);
    }
    
    private void initializeRepositories() {
        authorRepository = new AuthorRepository(this);
        bookRepository = new BookRepository(getApplication());
        viewModel = new ViewModelProvider(this).get(BookListViewModel.class);
        apiService = ApiClient.getApiService();
    }
    
    private void loadAuthorData() {
        authorRepository.getAuthorById(authorId).observe(this, new Observer<Author>() {
            @Override
            public void onChanged(Author author) {
                if (author != null) {
                    displayAuthorInfo(author);
                    // Set toolbar title to author name
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(author.getName());
                    }
                } else {
                    // Load from backend API if not in local database
                    authorRepository.loadAuthorByIdFromFirebase(authorId);
                }
            }
        });
    }
    
    private void displayAuthorInfo(Author author) {
        authorName.setText(author.getName());
        
        if (author.getBiography() != null && !author.getBiography().isEmpty()) {
            authorBiography.setText(author.getBiography());
            authorBiography.setVisibility(View.VISIBLE);
        } else {
            authorBiography.setVisibility(View.GONE);
        }
        
        // Display life span (birthday and death date)
        String birthDate = author.getBirthDate();
        String deathDate = author.getDeathDate();
        
        if (birthDate != null && !birthDate.isEmpty()) {
            String lifeSpanText;
            if (deathDate != null && !deathDate.isEmpty()) {
                lifeSpanText = birthDate + " - " + deathDate;
            } else {
                lifeSpanText = birthDate + " - Present";
            }
            authorLifeSpan.setText(lifeSpanText);
            authorLifeSpan.setVisibility(View.VISIBLE);
        } else {
            authorLifeSpan.setVisibility(View.GONE);
        }
        
        String nationality = author.getNationality();
        if (nationality != null && !nationality.isEmpty()) {
            authorNationality.setText(nationality);
            authorNationality.setVisibility(View.VISIBLE);
        } else {
            authorNationality.setVisibility(View.GONE);
        }
        
        String booksText = author.getTotalBooks() + " " + 
            (author.getTotalBooks() == 1 ? getString(R.string.book) : getString(R.string.books));
        authorBooksCount.setText(booksText);
        
        // Load author image
        if (author.getPhotoUrl() != null && !author.getPhotoUrl().isEmpty()) {
            Glide.with(this)
                .load(author.getPhotoUrl())
                .placeholder(R.drawable.default_author_avatar)
                .error(R.drawable.default_author_avatar)
                .circleCrop()
                .into(authorImage);
        } else {
            authorImage.setImageResource(R.drawable.default_author_avatar);
        }
    }

    private void loadAuthorBooks() {
        long parsedAuthorId;
        try {
            parsedAuthorId = Long.parseLong(authorId);
        } catch (NumberFormatException e) {
            Log.e("AuthorDetailActivity", "Invalid authorId: " + authorId);
            booksAdapter.updateBooks(new ArrayList<>());
            return;
        }

        apiService.getAuthorBooks(parsedAuthorId, 50, 0).enqueue(new Callback<List<ApiBook>>() {
            @Override
            public void onResponse(Call<List<ApiBook>> call, Response<List<ApiBook>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<HomeActivity.RecommendedBook> books = new ArrayList<>();
                    for (ApiBook apiBook : response.body()) {
                        books.add(new HomeActivity.RecommendedBook(
                            apiBook.title,
                            apiBook.author,
                            apiBook.fileUrl,
                            apiBook.fileType,
                            apiBook.image,
                            apiBook.annotation
                        ));
                    }
                    booksAdapter.updateBooks(books);
                } else {
                    Log.w("AuthorDetailActivity", "Failed to load author books from API. code=" + response.code());
                    booksAdapter.updateBooks(new ArrayList<>());
                }
            }

            @Override
            public void onFailure(Call<List<ApiBook>> call, Throwable t) {
                Log.w("AuthorDetailActivity", "Failed to load author books from API", t);
                booksAdapter.updateBooks(new ArrayList<>());
            }
        });
    }
    
    // Book opening functionality (copied from HomeActivity)
    private void handleRecommendedBookClick(HomeActivity.RecommendedBook book) {
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
            Log.d("AuthorDetailActivity", "File already exists on device: " + existingFile.getAbsolutePath());
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
                        Log.e("AuthorDetailActivity", "Error checking file existence: " + e.getMessage());
                        fileExists = false;
                    }
                    
                    if (fileExists) {
                        Log.d("AuthorDetailActivity", "Book found in database and file exists: " + existingBook.getFilePath());
                        // Always use the URI stored in the database (which should be content URI)
                        openBookOptionsActivity(Uri.parse(existingBook.getFilePath()), fileType, bookTitle, annotation);
                    } else {
                        Log.d("AuthorDetailActivity", "Book in database but file missing, will re-download");
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
                    Log.e("AuthorDetailActivity", "Failed to convert file path to content URI: " + filePath);
                    Toast.makeText(AuthorDetailActivity.this, "Error accessing file", Toast.LENGTH_SHORT).show();
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
                            Log.d("AuthorDetailActivity", "Updated file path for existing book: " + title + " to content URI");
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
                    Log.d("AuthorDetailActivity", "Added existing file to database: " + title + " with URI: " + contentUri);
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
            Log.e("AuthorDetailActivity", "Error converting file path to content URI: " + e.getMessage());
        }
        return null;
    }

    private void startDownload(String title, String author, String fileUrl, String fileType, String imageUrl, String annotation) {
        if (isDownloading) {
            Toast.makeText(this, R.string.processing_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }

        isDownloading = true;
        Toast.makeText(this, R.string.downloading, Toast.LENGTH_SHORT).show();

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fileUrl));
        request.setTitle(title);
        request.setDescription("Downloading " + title);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, title + "." + fileType);

        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        long downloadId = downloadManager.enqueue(request);

        // Monitor download progress
        new Thread(() -> {
            boolean downloading = true;
            while (downloading) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                Cursor cursor = downloadManager.query(query);
                
                if (cursor.moveToFirst()) {
                    int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        downloading = false;
                        String uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                        Log.d("AuthorDetailActivity", "Download successful! Local URI: " + uriString);
                        
                        runOnUiThread(() -> {
                            isDownloading = false;
                            if (uriString != null) {
                                Log.d("AuthorDetailActivity", "Saving book to database...");
                                saveBookAndOpenFromServer(title, author, uriString, fileType, imageUrl, annotation);
                            } else {
                                Log.e("AuthorDetailActivity", "Download succeeded but local URI is null!");
                                Toast.makeText(this, R.string.download_failed_file_not_found, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        downloading = false;
                        int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
                        Log.e("AuthorDetailActivity", "Download failed with reason: " + reason);
                        
                        runOnUiThread(() -> {
                            isDownloading = false;
                            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
                cursor.close();
                
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
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
                        Log.d("AuthorDetailActivity", "Updated lastOpened for book: " + title);
                    } else {
                        Log.d("AuthorDetailActivity", "Skipped updating lastOpened for book: " + title);
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
            Log.w("AuthorDetailActivity", "Could not take persistable permission for URI: " + e.getMessage());
        }
        Intent intent = new Intent(this, BookOptionsActivity.class);
        intent.setData(fileUri);
        intent.putExtra("fileType", fileType);
        intent.putExtra("title", title);
        intent.putExtra("annotation", annotation);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }
} 
