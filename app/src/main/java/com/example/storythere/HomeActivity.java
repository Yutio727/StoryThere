package com.example.storythere;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.bumptech.glide.Glide;
import android.widget.EditText;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;
import com.example.storythere.data.Book;
import com.example.storythere.data.BookRepository;
import androidx.lifecycle.ViewModelProvider;
import com.example.storythere.ui.BookListViewModel;
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
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.List;
import androidx.lifecycle.Observer;

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
        
        setupAdminButton();
        setupRecommendedBooksRecycler();
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
        // Set hint based on locale
        String lang = getResources().getConfiguration().locale.getLanguage();
        if (lang.equals("ru")) {
            searchBar.setHint("Найти лучшую книгу");
        } else {
            searchBar.setHint("Find your awesome book");
        }
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
        public RecommendedBook(String title, String author, String fileUrl, String fileType, String image) {
            this.title = title;
            this.author = author;
            this.fileUrl = fileUrl;
            this.fileType = fileType;
            this.image = image;
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
                  bookList.add(new RecommendedBook(title, author, fileUrl, fileType, image));
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

        if (isDownloading || isCheckingBook) {
            Toast.makeText(this, "Processing in progress...", Toast.LENGTH_SHORT).show();
            return;
        }
        isCheckingBook = true;

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
                    openBookOptionsActivity(Uri.parse(existingBook.getFilePath()), fileType, bookTitle);
                } else {
                    isDownloading = true;
                    Log.d("HomeActivity", "Starting download: " + bookTitle + " by " + bookAuthor + " from " + fileUrl);
                    downloadAndSaveBookFromServer(bookTitle, bookAuthor, fileUrl, fileType, imageUrl);
                }
            }
        };
        viewModel.getAllBooks().observe(this, observer);
    }

    private void downloadAndSaveBookFromServer(String title, String author, String fileUrl, String fileType, String imageUrl) {
        Toast.makeText(this, getString(R.string.downloading), Toast.LENGTH_SHORT).show();
        String fileName = title + "." + fileType;
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fileUrl));
        request.setTitle(title);
        request.setDescription("Downloading book...");
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        long downloadId = dm.enqueue(request);

        new Thread(() -> {
            boolean downloading = true;
            while (downloading) {
                DownloadManager.Query q = new DownloadManager.Query();
                q.setFilterById(downloadId);
                android.database.Cursor cursor = dm.query(q);
                if (cursor != null && cursor.moveToFirst()) {
                    int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        downloading = false;
                        String uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                        runOnUiThread(() -> {
                            isDownloading = false;
                            if (uriString != null) {
                                saveBookAndOpenFromServer(title, author, uriString, fileType, imageUrl);
                            } else {
                                Toast.makeText(this, "Download failed: file not found", Toast.LENGTH_SHORT).show();
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
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    private void saveBookAndOpenFromServer(String title, String author, String localUriString, String fileType, String imageUrl) {
        Book book = new Book(title, author, localUriString, fileType);
        book.setPreviewImagePath(imageUrl);
        bookRepository.insert(book);
        openBookOptionsActivity(Uri.parse(localUriString), fileType, title);
    }

    private void openBookOptionsActivity(Uri fileUri, String fileType, String title) {
        Intent intent = new Intent(this, BookOptionsActivity.class);
        intent.setData(fileUri);
        intent.putExtra("fileType", fileType);
        intent.putExtra("title", title);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }
} 