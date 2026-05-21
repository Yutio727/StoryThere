package com.example.storythere.ui;

import android.Manifest;
import android.app.DownloadManager;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.storythere.R;
import com.example.storythere.adapters.AuthorAdapter;
import com.example.storythere.adapters.BookListViewModel;
import com.example.storythere.adapters.RecommendBookAdapter;
import com.example.storythere.api.ApiClient;
import com.example.storythere.api.ApiService;
import com.example.storythere.api.model.ApiAuthor;
import com.example.storythere.api.model.ApiBook;
import com.example.storythere.data.Author;
import com.example.storythere.data.Book;
import com.example.storythere.data.BookRepository;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CatalogActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1204;
    private static final int TAB_BOOKS = 0;
    private static final int TAB_AUDIOBOOKS = 1;
    private static final int TAB_AUTHORS = 2;
    private static final int PAGE_SIZE = 30;
    private static final int BOOK_GRID_SPACING_DP = 40;
    private static final int AUTHOR_GRID_SPACING_DP = 20;
    private static final int BOOK_RECYCLER_PADDING_START_DP = 32;
    private static final int BOOK_RECYCLER_PADDING_TOP_DP = 16;
    private static final int BOOK_RECYCLER_PADDING_END_DP = 14;
    private static final int BOOK_RECYCLER_PADDING_BOTTOM_DP = 16;
    private static final int AUTHOR_RECYCLER_PADDING_START_DP = 18;
    private static final int AUTHOR_RECYCLER_PADDING_TOP_DP = 16;
    private static final int AUTHOR_RECYCLER_PADDING_END_DP = 20;
    private static final int AUTHOR_RECYCLER_PADDING_BOTTOM_DP = 16;

    private ImageView iconHome, iconSearch, iconMyBooks, iconCatalog, iconProfile;
    private TextView textHome, textSearch, textMyBooks, textCatalog, textProfile;
    private RecyclerView catalogRecyclerView;
    private ProgressBar catalogProgressBar;
    private TextView catalogMessage;
    private TabLayout catalogTabLayout;

    private ApiService apiService;
    private BookRepository bookRepository;
    private BookListViewModel viewModel;
    private RecommendBookAdapter booksAdapter;
    private AuthorAdapter authorsAdapter;
    private GridSpacingItemDecoration gridSpacingItemDecoration;
    private final List<HomeActivity.RecommendedBook> books = new ArrayList<>();
    private final List<Author> authors = new ArrayList<>();

    private int currentTab = TAB_BOOKS;
    private int booksOffset = 0;
    private int authorsOffset = 0;
    private boolean isBooksLoading = false;
    private boolean isAuthorsLoading = false;
    private boolean isDownloading = false;
    private boolean isCheckingBook = false;
    private boolean hasMoreBooks = true;
    private boolean hasMoreAuthors = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalog);

        apiService = ApiClient.getApiService();
        bookRepository = new BookRepository(getApplication());
        viewModel = new ViewModelProvider(this).get(BookListViewModel.class);

        initializeViews();
        setupBottomNavigation();
        setSelectedTab(3);
        setupCatalogTabs();
        setupScrollPaging();

        showBooksTab();
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

        catalogRecyclerView = findViewById(R.id.catalogRecyclerView);
        catalogProgressBar = findViewById(R.id.catalogProgressBar);
        catalogMessage = findViewById(R.id.catalogMessage);
        catalogTabLayout = findViewById(R.id.catalogTabLayout);

        booksAdapter = new RecommendBookAdapter(books, this::handleRecommendedBookClick);
        authorsAdapter = new AuthorAdapter(this, authors);
    }

    private void setupCatalogTabs() {
        catalogTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab == null) {
                    return;
                }
                switch (tab.getPosition()) {
                    case TAB_BOOKS:
                        showBooksTab();
                        break;
                    case TAB_AUDIOBOOKS:
                        showAudiobooksPlaceholder();
                        break;
                    case TAB_AUTHORS:
                        showAuthorsTab();
                        break;
                }
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupScrollPaging() {
        catalogRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0) {
                    return;
                }

                RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
                if (!(manager instanceof GridLayoutManager)) {
                    return;
                }

                GridLayoutManager gridLayoutManager = (GridLayoutManager) manager;
                int total = gridLayoutManager.getItemCount();
                int lastVisible = gridLayoutManager.findLastVisibleItemPosition();
                if (total > 0 && lastVisible >= total - 6) {
                    loadNextPageForCurrentTab();
                }
            }
        });
    }

    private void showBooksTab() {
        currentTab = TAB_BOOKS;
        catalogMessage.setVisibility(View.GONE);
        catalogRecyclerView.setVisibility(View.VISIBLE);
        catalogRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        setRecyclerPadding(
            BOOK_RECYCLER_PADDING_START_DP,
            BOOK_RECYCLER_PADDING_TOP_DP,
            BOOK_RECYCLER_PADDING_END_DP,
            BOOK_RECYCLER_PADDING_BOTTOM_DP
        );
        setGridSpacing(BOOK_GRID_SPACING_DP);
        catalogRecyclerView.setAdapter(booksAdapter);
        booksAdapter.updateBooks(books);
        if (books.isEmpty()) {
            loadBooksPage();
        }
    }

    private void showAuthorsTab() {
        currentTab = TAB_AUTHORS;
        catalogMessage.setVisibility(View.GONE);
        catalogRecyclerView.setVisibility(View.VISIBLE);
        catalogRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        setRecyclerPadding(
            AUTHOR_RECYCLER_PADDING_START_DP,
            AUTHOR_RECYCLER_PADDING_TOP_DP,
            AUTHOR_RECYCLER_PADDING_END_DP,
            AUTHOR_RECYCLER_PADDING_BOTTOM_DP
        );
        setGridSpacing(AUTHOR_GRID_SPACING_DP);
        catalogRecyclerView.setAdapter(authorsAdapter);
        authorsAdapter.updateAuthors(authors);
        if (authors.isEmpty()) {
            loadAuthorsPage();
        }
    }

    private void showAudiobooksPlaceholder() {
        currentTab = TAB_AUDIOBOOKS;
        catalogProgressBar.setVisibility(View.GONE);
        catalogRecyclerView.setVisibility(View.GONE);
        catalogMessage.setText(R.string.audiobooks_catalog_placeholder);
        catalogMessage.setVisibility(View.VISIBLE);
    }

    private void setRecyclerPadding(int startDp, int topDp, int endDp, int bottomDp) {
        catalogRecyclerView.setPadding(
            dpToPx(startDp),
            dpToPx(topDp),
            dpToPx(endDp),
            dpToPx(bottomDp)
        );
    }

    private void setGridSpacing(int spacingDp) {
        if (gridSpacingItemDecoration != null) {
            catalogRecyclerView.removeItemDecoration(gridSpacingItemDecoration);
        }
        int spacingPx = dpToPx(spacingDp);
        gridSpacingItemDecoration = new GridSpacingItemDecoration(spacingPx);
        catalogRecyclerView.addItemDecoration(gridSpacingItemDecoration);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void loadNextPageForCurrentTab() {
        if (currentTab == TAB_BOOKS) {
            loadBooksPage();
        } else if (currentTab == TAB_AUTHORS) {
            loadAuthorsPage();
        }
    }

    private void loadBooksPage() {
        if (isBooksLoading || !hasMoreBooks) {
            return;
        }
        isBooksLoading = true;
        updateLoadingState();

        apiService.getBooks(PAGE_SIZE, booksOffset).enqueue(new Callback<List<ApiBook>>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiBook>> call, @NonNull Response<List<ApiBook>> response) {
                isBooksLoading = false;
                updateLoadingState();
                if (!response.isSuccessful() || response.body() == null) {
                    showErrorIfEmpty(R.string.catalog_load_failed);
                    return;
                }

                List<ApiBook> page = response.body();
                for (ApiBook apiBook : page) {
                    books.add(mapApiBook(apiBook));
                }
                booksOffset += page.size();
                hasMoreBooks = page.size() == PAGE_SIZE;
                booksAdapter.updateBooks(books);
                showEmptyIfNeeded();
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiBook>> call, @NonNull Throwable t) {
                isBooksLoading = false;
                updateLoadingState();
                showErrorIfEmpty(R.string.catalog_load_failed);
            }
        });
    }

    private void loadAuthorsPage() {
        if (isAuthorsLoading || !hasMoreAuthors) {
            return;
        }
        isAuthorsLoading = true;
        updateLoadingState();

        apiService.getAuthors(PAGE_SIZE, authorsOffset).enqueue(new Callback<List<ApiAuthor>>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiAuthor>> call, @NonNull Response<List<ApiAuthor>> response) {
                isAuthorsLoading = false;
                updateLoadingState();
                if (!response.isSuccessful() || response.body() == null) {
                    showErrorIfEmpty(R.string.catalog_load_failed);
                    return;
                }

                List<ApiAuthor> page = response.body();
                for (ApiAuthor apiAuthor : page) {
                    authors.add(mapApiAuthor(apiAuthor));
                }
                authorsOffset += page.size();
                hasMoreAuthors = page.size() == PAGE_SIZE;
                authorsAdapter.updateAuthors(authors);
                showEmptyIfNeeded();
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiAuthor>> call, @NonNull Throwable t) {
                isAuthorsLoading = false;
                updateLoadingState();
                showErrorIfEmpty(R.string.catalog_load_failed);
            }
        });
    }

    private HomeActivity.RecommendedBook mapApiBook(ApiBook apiBook) {
        return new HomeActivity.RecommendedBook(
            apiBook.title,
            apiBook.author,
            apiBook.fileUrl,
            apiBook.fileType,
            apiBook.image,
            apiBook.annotation
        );
    }

    private Author mapApiAuthor(ApiAuthor apiAuthor) {
        Author author = new Author();
        author.setAuthorId(String.valueOf(apiAuthor.id));
        author.setName(apiAuthor.name);
        author.setBiography(apiAuthor.biography);
        author.setBirthDate(apiAuthor.birthdayDate);
        author.setDeathDate(apiAuthor.deathDate);
        author.setNationality(apiAuthor.nationality);
        author.setPhotoUrl(apiAuthor.photoUrl);
        author.setTotalBooks(apiAuthor.totalBooks);
        return author;
    }

    private void updateLoadingState() {
        boolean isLoading = (currentTab == TAB_BOOKS && isBooksLoading) || (currentTab == TAB_AUTHORS && isAuthorsLoading);
        catalogProgressBar.setVisibility(isLoading && currentItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void showEmptyIfNeeded() {
        if (currentTab == TAB_BOOKS && books.isEmpty()) {
            catalogMessage.setText(R.string.catalog_books_empty);
            catalogMessage.setVisibility(View.VISIBLE);
            return;
        }
        if (currentTab == TAB_AUTHORS && authors.isEmpty()) {
            catalogMessage.setText(R.string.catalog_authors_empty);
            catalogMessage.setVisibility(View.VISIBLE);
            return;
        }
        catalogMessage.setVisibility(View.GONE);
    }

    private void showErrorIfEmpty(int messageRes) {
        if (currentItemCount() > 0) {
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
            return;
        }
        catalogRecyclerView.setVisibility(View.GONE);
        catalogMessage.setText(messageRes);
        catalogMessage.setVisibility(View.VISIBLE);
    }

    private int currentItemCount() {
        if (currentTab == TAB_BOOKS) {
            return books.size();
        }
        if (currentTab == TAB_AUTHORS) {
            return authors.size();
        }
        return 0;
    }

    private void handleRecommendedBookClick(HomeActivity.RecommendedBook book) {
        if (book == null) return;
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

        String fileName = bookTitle + "." + fileType;
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File existingFile = new File(downloadsDir, fileName);

        if (existingFile.exists()) {
            checkDatabaseAndAddIfNeeded(bookTitle, bookAuthor, existingFile.getAbsolutePath(), fileType, imageUrl, annotation);
            return;
        }

        Observer<List<Book>> observer = new Observer<List<Book>>() {
            @Override
            public void onChanged(List<Book> localBooks) {
                viewModel.getAllBooks().removeObserver(this);
                isCheckingBook = false;

                Book existingBook = null;
                for (Book localBook : localBooks) {
                    if (localBook.getTitle().equals(bookTitle) && localBook.getAuthor().equals(bookAuthor)) {
                        existingBook = localBook;
                        break;
                    }
                }

                if (existingBook == null) {
                    startDownload(bookTitle, bookAuthor, fileUrl, fileType, imageUrl, annotation);
                    return;
                }

                if (doesBookFileExist(existingBook)) {
                    openBookOptionsActivity(Uri.parse(existingBook.getFilePath()), fileType, bookTitle, annotation);
                } else {
                    bookRepository.delete(existingBook);
                    startDownload(bookTitle, bookAuthor, fileUrl, fileType, imageUrl, annotation);
                }
            }
        };
        viewModel.getAllBooks().observe(this, observer);
    }

    private boolean doesBookFileExist(Book book) {
        try {
            Uri bookUri = Uri.parse(book.getFilePath());
            if ("content".equals(bookUri.getScheme())) {
                try (InputStream is = getContentResolver().openInputStream(bookUri)) {
                    return is != null;
                }
            }
            File dbFile = new File(book.getFilePath());
            return dbFile.exists();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void checkDatabaseAndAddIfNeeded(String title, String author, String filePath, String fileType, String imageUrl, String annotation) {
        Observer<List<Book>> observer = new Observer<List<Book>>() {
            @Override
            public void onChanged(List<Book> localBooks) {
                viewModel.getAllBooks().removeObserver(this);
                isCheckingBook = false;

                String contentUri = convertFilePathToContentUri(filePath);
                if (contentUri == null) {
                    Toast.makeText(CatalogActivity.this, "Error accessing file", Toast.LENGTH_SHORT).show();
                    return;
                }

                boolean bookExists = false;
                for (Book localBook : localBooks) {
                    if (localBook.getTitle().equals(title) && localBook.getAuthor().equals(author)) {
                        bookExists = true;
                        if (!localBook.getFilePath().equals(contentUri)) {
                            localBook.setFilePath(contentUri);
                            bookRepository.update(localBook);
                        }
                        break;
                    }
                }

                if (!bookExists) {
                    Book newBook = new Book(title, author, contentUri, fileType);
                    newBook.setPreviewImagePath(imageUrl);
                    newBook.setAnnotation(annotation);
                    bookRepository.insert(newBook);
                }

                openBookOptionsActivity(Uri.parse(contentUri), fileType, title, annotation);
            }
        };
        viewModel.getAllBooks().observe(this, observer);
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
        } catch (Exception ignored) {
        }
        return null;
    }

    private void startDownload(String title, String author, String fileUrl, String fileType, String imageUrl, String annotation) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            isDownloading = false;
            Toast.makeText(this, R.string.download_failed_file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        isDownloading = true;
        Toast.makeText(this, getString(R.string.downloading), Toast.LENGTH_SHORT).show();

        String fileName = title + "." + fileType;
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fileUrl));
        request.setTitle(title);
        request.setDescription("Downloading book...");
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");

        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        long downloadId = downloadManager.enqueue(request);

        new Thread(() -> {
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
                                saveBookAndOpenFromServer(title, author, uriString, fileType, imageUrl, annotation);
                            } else {
                                Toast.makeText(this, R.string.download_failed_file_not_found, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        downloading = false;
                        runOnUiThread(() -> {
                            isDownloading = false;
                            Toast.makeText(this, getString(R.string.download_failed), Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                if (cursor != null) cursor.close();

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
                    downloading = false;
                    Thread.currentThread().interrupt();
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
        String filePath = fileUri.toString();
        Observer<Book> observer = new Observer<Book>() {
            @Override
            public void onChanged(Book existingBook) {
                viewModel.getBookByPath(filePath).removeObserver(this);
                if (existingBook != null) {
                    Date now = new Date();
                    if (existingBook.getLastOpened() == null || Math.abs(now.getTime() - existingBook.getLastOpened().getTime()) > 1000) {
                        existingBook.setLastOpened(now);
                        viewModel.update(existingBook);
                    }
                }
            }
        };
        viewModel.getBookByPath(filePath).observe(this, observer);

        try {
            getContentResolver().takePersistableUriPermission(fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }

        Intent intent = new Intent(this, BookOptionsActivity.class);
        intent.setData(fileUri);
        intent.putExtra("fileType", fileType);
        intent.putExtra("title", title);
        intent.putExtra("annotation", annotation);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
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

    private void setupBottomNavigation() {
        boolean isOfflineMode = getIntent().getBooleanExtra("offline_mode", false);

        findViewById(R.id.nav_home).setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            if (isOfflineMode) intent.putExtra("offline_mode", true);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.nav_search).setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            if (isOfflineMode) intent.putExtra("offline_mode", true);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.nav_my_books).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            if (isOfflineMode) intent.putExtra("offline_mode", true);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.nav_catalog).setOnClickListener(v -> {
            // Already on catalog, do nothing
        });

        findViewById(R.id.nav_profile).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            if (isOfflineMode) intent.putExtra("offline_mode", true);
            startActivity(intent);
            finish();
        });
    }

    private void setSelectedTab(int selectedIndex) {
        resetAllTabs();
        int selectedColor = getSelectedColor();

        switch (selectedIndex) {
            case 0:
                iconHome.setColorFilter(selectedColor);
                textHome.setTextColor(selectedColor);
                textHome.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_bold));
                break;
            case 1:
                iconSearch.setColorFilter(selectedColor);
                textSearch.setTextColor(selectedColor);
                textSearch.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_bold));
                break;
            case 2:
                iconMyBooks.setColorFilter(selectedColor);
                textMyBooks.setTextColor(selectedColor);
                textMyBooks.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_bold));
                break;
            case 3:
                iconCatalog.setColorFilter(selectedColor);
                textCatalog.setTextColor(selectedColor);
                textCatalog.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_bold));
                break;
            case 4:
                iconProfile.setColorFilter(selectedColor);
                textProfile.setTextColor(selectedColor);
                textProfile.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_bold));
                break;
        }
    }

    private void resetAllTabs() {
        int unselectedColor = getUnselectedColor();
        ImageView[] icons = {iconHome, iconSearch, iconMyBooks, iconCatalog, iconProfile};
        TextView[] labels = {textHome, textSearch, textMyBooks, textCatalog, textProfile};

        for (ImageView icon : icons) {
            icon.setColorFilter(unselectedColor);
        }
        for (TextView label : labels) {
            label.setTextColor(unselectedColor);
            label.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_regular));
        }
    }

    private int getSelectedColor() {
        return isDarkTheme()
            ? ContextCompat.getColor(this, R.color.bottom_nav_selected_dark)
            : ContextCompat.getColor(this, R.color.bottom_nav_selected_light);
    }

    private int getUnselectedColor() {
        return isDarkTheme()
            ? ContextCompat.getColor(this, R.color.bottom_nav_unselected_dark)
            : ContextCompat.getColor(this, R.color.bottom_nav_unselected_light);
    }

    private boolean isDarkTheme() {
        return (getResources().getConfiguration().uiMode &
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private final int spacing;

        GridSpacingItemDecoration(int spacing) {
            this.spacing = spacing;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
            if (!(layoutManager instanceof GridLayoutManager)) {
                return;
            }

            GridLayoutManager gridLayoutManager = (GridLayoutManager) layoutManager;
            int spanCount = gridLayoutManager.getSpanCount();
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            int column = position % spanCount;
            outRect.left = spacing * column / spanCount;
            outRect.right = spacing * (spanCount - 1 - column) / spanCount;
            if (position >= spanCount) {
                outRect.top = spacing;
            }
        }
    }
}
