package com.example.storythere.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.storythere.R;
import com.example.storythere.api.ApiClient;
import com.example.storythere.api.ApiService;
import com.example.storythere.api.model.ApiAudiobook;
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
import java.util.Locale;
import java.util.Objects;
import android.database.Cursor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthorDetailActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1204;
    
    private ImageView authorImage;
    private TextView authorName;
    private TextView authorBiography;
    private TextView authorLifeSpan;
    private TextView authorNationality;
    private TextView authorBooksCount;
    private TextView booksSectionTitle;
    private TextView audiobooksSectionTitle;
    private RecyclerView booksRecyclerView;
    private RecyclerView audiobooksRecyclerView;
    private RecommendBookAdapter booksAdapter;
    private RecommendBookAdapter audiobooksAdapter;
    
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
        loadAuthorAudiobooks();
    }
    
    private void initializeViews() {
        authorImage = findViewById(R.id.author_image);
        authorName = findViewById(R.id.author_name);
        authorBiography = findViewById(R.id.author_biography);
        authorLifeSpan = findViewById(R.id.author_life_span);
        authorNationality = findViewById(R.id.author_nationality);
        authorBooksCount = findViewById(R.id.author_books_count);
        booksSectionTitle = findViewById(R.id.books_section_title);
        audiobooksSectionTitle = findViewById(R.id.audiobooks_section_title);
        booksRecyclerView = findViewById(R.id.books_recycler_view);
        audiobooksRecyclerView = findViewById(R.id.audiobooks_recycler_view);
        setBooksSectionVisible(false);
        setAudiobooksSectionVisible(false);
    }
    
    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
        
        toolbar.setNavigationOnClickListener(v -> closeWithFade());
    }
    
    private void setupRecyclerView() {
        booksAdapter = new RecommendBookAdapter(new ArrayList<>(), book -> {
            handleRecommendedBookClick(book);
        });
        booksRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        booksRecyclerView.setAdapter(booksAdapter);

        audiobooksAdapter = new RecommendBookAdapter(new ArrayList<>(), book -> {
            handleRecommendedBookClick(book);
        });
        audiobooksRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        audiobooksRecyclerView.setAdapter(audiobooksAdapter);
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
        
        String booksText = author.getTotalBooks() + " " + getString(R.string.books_and_audiobooks);
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
            setBooksSectionVisible(false);
            return;
        }

        apiService.getAuthorBooks(parsedAuthorId, 50, 0).enqueue(new Callback<List<ApiBook>>() {
            @Override
            public void onResponse(Call<List<ApiBook>> call, Response<List<ApiBook>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<HomeActivity.RecommendedBook> books = new ArrayList<>();
                    for (ApiBook apiBook : response.body()) {
                        books.add(new HomeActivity.RecommendedBook(
                            apiBook.id,
                            safeText(apiBook.title, "Unknown Title"),
                            safeText(apiBook.author, "Unknown Author"),
                            apiBook.fileUrl,
                            safeFileType(apiBook.fileType, apiBook.fileUrl),
                            apiBook.image,
                            apiBook.annotation,
                            false,
                            null,
                            null,
                            0
                        ));
                    }
                    booksAdapter.updateBooks(books);
                    setBooksSectionVisible(!books.isEmpty());
                } else {
                    Log.w("AuthorDetailActivity", "Failed to load author books from API. code=" + response.code());
                    booksAdapter.updateBooks(new ArrayList<>());
                    setBooksSectionVisible(false);
                }
            }

            @Override
            public void onFailure(Call<List<ApiBook>> call, Throwable t) {
                Log.w("AuthorDetailActivity", "Failed to load author books from API", t);
                booksAdapter.updateBooks(new ArrayList<>());
                setBooksSectionVisible(false);
            }
        });
    }

    private void loadAuthorAudiobooks() {
        long parsedAuthorId;
        try {
            parsedAuthorId = Long.parseLong(authorId);
        } catch (NumberFormatException e) {
            Log.e("AuthorDetailActivity", "Invalid authorId: " + authorId);
            audiobooksAdapter.updateBooks(new ArrayList<>());
            setAudiobooksSectionVisible(false);
            return;
        }

        apiService.getAuthorAudiobooks(parsedAuthorId, 50, 0).enqueue(new Callback<List<ApiAudiobook>>() {
            @Override
            public void onResponse(Call<List<ApiAudiobook>> call, Response<List<ApiAudiobook>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<HomeActivity.RecommendedBook> audiobooks = new ArrayList<>();
                    for (ApiAudiobook audiobook : response.body()) {
                        String audioType = audiobook.audioType != null ? audiobook.audioType : "mp3";
                        int durationSeconds = audiobook.durationSeconds != null ? audiobook.durationSeconds : 0;
                        audiobooks.add(new HomeActivity.RecommendedBook(
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
                            audiobook.dictor,
                            durationSeconds
                        ));
                    }
                    audiobooksAdapter.updateBooks(audiobooks);
                    setAudiobooksSectionVisible(!audiobooks.isEmpty());
                } else {
                    Log.w("AuthorDetailActivity", "Failed to load author audiobooks from API. code=" + response.code());
                    audiobooksAdapter.updateBooks(new ArrayList<>());
                    setAudiobooksSectionVisible(false);
                }
            }

            @Override
            public void onFailure(Call<List<ApiAudiobook>> call, Throwable t) {
                Log.w("AuthorDetailActivity", "Failed to load author audiobooks from API", t);
                audiobooksAdapter.updateBooks(new ArrayList<>());
                setAudiobooksSectionVisible(false);
            }
        });
    }

    private void setBooksSectionVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        booksSectionTitle.setVisibility(visibility);
        booksRecyclerView.setVisibility(visibility);
    }

    private void setAudiobooksSectionVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        audiobooksSectionTitle.setVisibility(visibility);
        audiobooksRecyclerView.setVisibility(visibility);
    }
    
    // Book opening functionality (copied from HomeActivity)
    private void handleRecommendedBookClick(HomeActivity.RecommendedBook book) {
        if (book == null) return;
        if (book.isAudiobook) {
            openAudiobookOptionsActivity(book);
            return;
        }
        if (!hasStoragePermission()) {
            requestStoragePermission();
            return;
        }
        String bookTitle = book.title != null ? book.title : "Unknown Title";
        String bookAuthor = book.author != null ? book.author : "Unknown Author";
        String fileUrl = book.fileUrl;
        String fileType = safeFileType(book.fileType, fileUrl);
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
            checkDatabaseAndAddIfNeeded(book.id, bookTitle, bookAuthor, existingFile.getAbsolutePath(), fileType, imageUrl, annotation);
            return;
        }

        // Use a one-time observer to avoid multiple downloads
        Observer<List<Book>> observer = new Observer<List<Book>>() {
            @Override
            public void onChanged(List<Book> books) {
                viewModel.getAllBooks().removeObserver(this);
                isCheckingBook = false;
                Book existingBook = findMatchingLocalBook(books, book.id, bookTitle, bookAuthor);
                if (existingBook != null) {
                    if (doesBookFileExist(existingBook)) {
                        Log.d("AuthorDetailActivity", "Book found in database and file exists: " + existingBook.getFilePath());
                        // Always use the URI stored in the database (which should be content URI)
                        openBookOptionsActivity(book.id, Uri.parse(existingBook.getFilePath()), fileType, bookTitle, annotation);
                    } else {
                        Log.d("AuthorDetailActivity", "Book in database but file missing, will re-download");
                        if (shouldDeleteMissingLocalBook(existingBook)) {
                            bookRepository.delete(existingBook);
                        }
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
                    Log.e("AuthorDetailActivity", "Failed to convert file path to content URI: " + filePath);
                    Toast.makeText(AuthorDetailActivity.this, "Error accessing file", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                Book existingBook = findMatchingLocalBook(books, serverBookId, title, author);
                
                if (existingBook != null) {
                    updateLocalBookFromAuthor(existingBook, serverBookId, title, author, contentUri, fileType, imageUrl, annotation);
                    bookRepository.update(existingBook);
                    Log.d("AuthorDetailActivity", "Updated existing book from author detail: " + title + " to content URI");
                } else {
                    // Add to database with content URI
                    Book newBook = new Book(title, author, contentUri, fileType);
                    updateLocalBookFromAuthor(newBook, serverBookId, title, author, contentUri, fileType, imageUrl, annotation);
                    bookRepository.insert(newBook);
                    Log.d("AuthorDetailActivity", "Added existing file to database: " + title + " with URI: " + contentUri);
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
            Log.e("AuthorDetailActivity", "Error converting file path to content URI: " + e.getMessage());
        }
        return null;
    }

    private boolean doesBookFileExist(Book book) {
        if (book == null || book.getFilePath() == null || book.getFilePath().trim().isEmpty()) {
            return false;
        }
        try {
            Uri bookUri = Uri.parse(book.getFilePath());
            if ("content".equals(bookUri.getScheme())) {
                try (InputStream is = getContentResolver().openInputStream(bookUri)) {
                    return is != null;
                }
            }
            File dbFile = new File(book.getFilePath());
            return dbFile.exists();
        } catch (Exception e) {
            Log.e("AuthorDetailActivity", "Error checking file existence: " + e.getMessage());
            return false;
        }
    }

    private boolean shouldDeleteMissingLocalBook(Book book) {
        return book != null
            && book.getServerBookId() <= 0
            && !book.isAlreadyRead()
            && book.getFilePath() != null
            && !book.getFilePath().trim().isEmpty();
    }

    private Book findMatchingLocalBook(List<Book> books, long serverBookId, String title, String author) {
        if (books == null) {
            return null;
        }
        if (serverBookId > 0) {
            for (Book book : books) {
                if (book != null && !book.isAudiobook() && book.getServerBookId() == serverBookId) {
                    return book;
                }
            }
        }
        for (Book book : books) {
            if (book != null
                && !book.isAudiobook()
                && safeEquals(book.getTitle(), title)
                && safeEquals(book.getAuthor(), author)) {
                return book;
            }
        }
        return null;
    }

    private void updateLocalBookFromAuthor(
        Book book,
        long serverBookId,
        String title,
        String author,
        String filePath,
        String fileType,
        String imageUrl,
        String annotation
    ) {
        book.setAudiobook(false);
        if (serverBookId > 0) {
            book.setServerBookId(serverBookId);
        }
        book.setTitle(safeText(title, book.getTitle()));
        book.setAuthor(safeText(author, book.getAuthor()));
        book.setFilePath(filePath);
        book.setFileType(safeText(fileType, book.getFileType()));
        book.setPreviewImagePath(imageUrl);
        book.setImage(imageUrl);
        book.setAnnotation(annotation);
        book.setLastOpened(new Date());
    }

    private boolean safeEquals(String first, String second) {
        return Objects.equals(normalizeText(first), normalizeText(second));
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String safeFileType(String fileType, String fileUrl) {
        if (fileType != null && !fileType.trim().isEmpty()) {
            return fileType.trim().toLowerCase(Locale.US);
        }
        if (fileUrl == null) {
            return "";
        }
        int queryIndex = fileUrl.indexOf('?');
        String cleanUrl = queryIndex >= 0 ? fileUrl.substring(0, queryIndex) : fileUrl;
        int dotIndex = cleanUrl.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == cleanUrl.length() - 1) {
            return "";
        }
        return cleanUrl.substring(dotIndex + 1).toLowerCase(Locale.US);
    }

    private void startDownload(long serverBookId, String title, String author, String fileUrl, String fileType, String imageUrl, String annotation) {
        if (isDownloading) {
            Toast.makeText(this, R.string.processing_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            isDownloading = false;
            Toast.makeText(this, R.string.download_failed_file_not_found, Toast.LENGTH_SHORT).show();
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
                                saveBookAndOpenFromServer(serverBookId, title, author, uriString, fileType, imageUrl, annotation);
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

    private void saveBookAndOpenFromServer(long serverBookId, String title, String author, String localUriString, String fileType, String imageUrl, String annotation) {
        Observer<List<Book>> observer = new Observer<List<Book>>() {
            @Override
            public void onChanged(List<Book> books) {
                viewModel.getAllBooks().removeObserver(this);
                Book existingBook = findMatchingLocalBook(books, serverBookId, title, author);
                if (existingBook != null) {
                    updateLocalBookFromAuthor(existingBook, serverBookId, title, author, localUriString, fileType, imageUrl, annotation);
                    bookRepository.update(existingBook);
                } else {
                    Book book = new Book(title, author, localUriString, fileType);
                    updateLocalBookFromAuthor(book, serverBookId, title, author, localUriString, fileType, imageUrl, annotation);
                    bookRepository.insert(book);
                }
                openBookOptionsActivity(serverBookId, Uri.parse(localUriString), fileType, title, annotation);
            }
        };
        viewModel.getAllBooks().observe(this, observer);
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
        intent.putExtra("bookId", serverBookId);
        intent.putExtra("fromRecommendation", false);
        intent.putExtra("fileType", fileType);
        intent.putExtra("title", title);
        intent.putExtra("annotation", annotation);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
        ActivityTransitions.applyFadeOpen(this);
    }

    private void openAudiobookOptionsActivity(HomeActivity.RecommendedBook book) {
        String audioUrl = book.audioUrl != null ? book.audioUrl : book.fileUrl;
        if (audioUrl == null || audioUrl.trim().isEmpty()) {
            Toast.makeText(this, R.string.download_failed_file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, BookOptionsActivity.class);
        intent.setData(Uri.parse(audioUrl));
        intent.putExtra("isAudiobook", true);
        intent.putExtra("audiobookId", book.id);
        intent.putExtra("audioUrl", audioUrl);
        intent.putExtra("audioType", book.audioType != null ? book.audioType : book.fileType);
        intent.putExtra("fileType", book.fileType);
        intent.putExtra("durationSeconds", book.durationSeconds);
        intent.putExtra("title", book.title);
        intent.putExtra("author", book.author);
        intent.putExtra("dictor", book.dictor);
        intent.putExtra("annotation", book.annotation);
        intent.putExtra("previewImagePath", book.image);
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
            startActivity(intent);
        } else {
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onBackPressed() {
        closeWithFade();
    }

    private void closeWithFade() {
        finish();
        ActivityTransitions.applyFadeClose(this);
    }
}
