package com.example.storythere;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.Layout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.storythere.data.Book;
import com.example.storythere.data.BookRepository;
import android.animation.ObjectAnimator;
import com.turingtechnologies.materialscrollbar.DragScrollBar;
import com.turingtechnologies.materialscrollbar.CustomIndicator;

public class PDFViewerActivity extends AppCompatActivity implements TextSettingsDialog.TextSettingsListener {
    private static final String TAG = "PDFViewerActivity";
    private static final int INITIAL_LOAD_RANGE = 5; // Load 5 pages before and after current
    private static final int LOAD_RANGE = 10; // Load 3 pages in scroll direction
    private static final int POSITION_SAVE_DELAY = 1000; // Save position after 1 second of no scrolling
    
    private RecyclerView pdfRecyclerView;
    private ScaleGestureDetector scaleDetector;
    private float scaleFactor = 1.0f;
    private float lastTouchX;
    private float lastTouchY;
    private float posX = 0;
    private float posY = 0;
    private List<PDFParser.ParsedPage> pages;
    private PDFParser.TextSettings currentSettings;
    private PDFParser pdfParser;
    private ExecutorService executorService;
    private Handler mainHandler;
    private int totalPages = 0;
    private int currentPage = 1;
    private EPUBParser epubParser;
    private boolean isEPUB = false;
    private PDFPageAdapter pdfPageAdapter;
    private PDFPageAdapter.DocumentType documentType;
    
    // MaterialScrollBar
    private DragScrollBar materialScrollBar;
    
    // Position tracking variables
    private Book currentBook;
    private BookRepository bookRepository;
    private Handler positionSaveHandler = new Handler(Looper.getMainLooper());
    private Runnable positionSaveRunnable;
    private boolean isPositionRestored = false;
    
    // Scroll progress UI elements
    private View scrollProgressContainer;
    private TextView pageNumberText;
    private ProgressBar scrollProgressBar;
    private Handler scrollProgressHandler = new Handler(Looper.getMainLooper());
    private Runnable hideProgressRunnable;

    // Only show progress bar if user interacts near the bottom
    private boolean showProgressOnScroll = false;
    


    // For smooth progress animation
    private int lastProgressValue = 0;

    // Animation duration for progress bar
    private static final int PROGRESS_ANIMATION_DURATION = 250;
    private static final int PROGRESS_BAR_ANIMATION_DURATION = 200;

    // Add this field to the class
    private boolean textSettingsAppliedAfterFirstTouch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable hardware acceleration
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
        
        setContentView(R.layout.activity_pdf_viewer);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Initialize position tracking
        bookRepository = new BookRepository(getApplication());
        positionSaveRunnable = new Runnable() {
            @Override
            public void run() {
                saveCurrentPosition();
            }
        };

        // Initialize views
        pdfRecyclerView = findViewById(R.id.pdfRecyclerView);
        pdfRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        pdfRecyclerView.setItemViewCacheSize(10);
        
        // Initialize MaterialScrollBar
        materialScrollBar = findViewById(R.id.materialScrollBar);
        
        // Add touch listener to MaterialScrollBar for progress bar cooperation
        if (materialScrollBar != null) {
            // Try to detect dragbar usage through touch events
            materialScrollBar.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    Log.d(TAG, "[DRAGBAR] Touch event on dragbar: " + event.getAction());
                    if (event.getAction() == MotionEvent.ACTION_DOWN || 
                        event.getAction() == MotionEvent.ACTION_MOVE) {
                        // Show progress bar when dragbar is touched
                        showProgressOnScroll = true;
                        updateScrollProgress();
                        // Reset the hide timer
                        scrollProgressHandler.removeCallbacks(hideProgressRunnable);
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        // Start timer to hide progress bar after dragbar interaction ends
                        scrollProgressHandler.postDelayed(hideProgressRunnable, 1000);
                    }
                    return false; // Let the dragbar handle the event normally
                }
            });
        }
        
        // Initialize scroll progress UI elements
        scrollProgressContainer = findViewById(R.id.scrollProgressContainer);
        pageNumberText = findViewById(R.id.pageNumberText);
        scrollProgressBar = findViewById(R.id.scrollProgressBar);
        
        // Hide progress bar if user taps it again
        scrollProgressContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (scrollProgressContainer.getVisibility() == View.VISIBLE) {
                    showProgressOnScroll = false;
                    animateHideProgressBar();
                }
            }
        });
        
        // Initialize hide progress runnable
        hideProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (scrollProgressContainer != null) {
                    animateHideProgressBar();
                }
                showProgressOnScroll = false;
            }
        };
        
        // Set up scroll listener for position tracking and progress display
        pdfRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                Log.d(TAG, "[SCROLL] Scroll state changed to: " + newState + " (IDLE=" + RecyclerView.SCROLL_STATE_IDLE + ")");
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // Save position after scrolling stops
                    Log.d(TAG, "[SCROLL] Scrolling stopped, scheduling position save in " + POSITION_SAVE_DELAY + "ms");
                    positionSaveHandler.postDelayed(positionSaveRunnable, POSITION_SAVE_DELAY);
                    // Hide progress indicator after scrolling stops (2 seconds to match dragbar behavior)
                    scrollProgressHandler.postDelayed(hideProgressRunnable, 1000);
                    


                    // --- Pre-parse PDF pages after fast scroll/drag ---
                    if (documentType == PDFPageAdapter.DocumentType.PDF && pdfRecyclerView != null) {
                        LinearLayoutManager layoutManager = (LinearLayoutManager) pdfRecyclerView.getLayoutManager();
                        if (layoutManager != null) {
                            int first = layoutManager.findFirstVisibleItemPosition();
                            int last = layoutManager.findLastVisibleItemPosition();
                            if (first != RecyclerView.NO_POSITION && last != RecyclerView.NO_POSITION) {
                                int window = 2; // Pre-parse 2 pages before and after
                                int start = Math.max(0, first - window);
                                int end = Math.min(totalPages - 1, last + window);
                                forceRenderPages(start, end, first); // targetPosition is first visible
                            }
                        }
                    }
                    // --- End pre-parse logic ---
                } else {
                    // Cancel pending save if user starts scrolling again
                    Log.d(TAG, "[SCROLL] Scrolling started, canceling pending position save");
                    positionSaveHandler.removeCallbacks(positionSaveRunnable);
                    // Cancel pending hide if user starts scrolling again
                    scrollProgressHandler.removeCallbacks(hideProgressRunnable);
                }
            }
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                // Only update progress if it's already showing (from dragbar or bottom touch)
                if (showProgressOnScroll) {
                    updateScrollProgress();
                }
            }
        });
        
        // Set a temporary adapter to prevent "No adapter attached" error
        List<PDFParser.ParsedPage> tempPages = new ArrayList<>();
        PDFParser.TextSettings tempSettings = new PDFParser.TextSettings();
        tempSettings.fontSize = 54.0f;
        tempSettings.textAlignment = Paint.Align.LEFT;
        tempPages.add(new PDFParser.ParsedPage(getString(R.string.loading_content), new ArrayList<>(), 1, 612.0f, 792.0f, tempSettings));
        PDFPageAdapter tempAdapter = new PDFPageAdapter(this, tempPages, PDFPageAdapter.DocumentType.TXT, null, null, null, null, tempSettings);
        pdfRecyclerView.setAdapter(tempAdapter);

        // Initialize scale detector
        scaleDetector = new ScaleGestureDetector(this, new ScaleListener());

        // Initialize thread pool and handler
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        mainHandler = new Handler(Looper.getMainLooper());

        // Get content from intent
        Intent intent = getIntent();
        Uri uri = intent.getData();
        String fileType = intent.getStringExtra("fileType");
        String filePath = intent.getStringExtra("filePath");
        
        // Load book from database for position tracking
        if (filePath != null) {
            Log.d(TAG, "[BOOK_LOAD] Loading book from database with filePath: " + filePath);
            bookRepository.getBookByPath(filePath).observe(this, book -> {
                if (book != null) {
                    currentBook = book;
                    Log.d(TAG, "[BOOK_LOAD] Successfully loaded book: " + book.getTitle() + " with reading position: " + book.getReadingPosition());
                    Log.d(TAG, "[BOOK_LOAD] Book details - ID: " + book.getId() + ", FilePath: " + book.getFilePath() + ", FileType: " + book.getFileType());
                    
                    // Try to restore position now that book is loaded
                    if (!isPositionRestored && pdfPageAdapter != null) {
                        Log.d(TAG, "[BOOK_LOAD] Book loaded, attempting to restore position now");
                        restoreReadingPosition();
                    }
                } else {
                    Log.w(TAG, "[BOOK_LOAD] No book found in database for filePath: " + filePath);
                }
            });
        } else {
            Log.w(TAG, "[BOOK_LOAD] filePath is null, cannot load book from database");
        }
        
        if (uri != null && "txt".equals(fileType)) {
            // Read cached .txt content from file
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                if (inputStream != null) {
                    StringBuilder sb = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    Log.d(TAG, "Loading cached .txt content from file: " + uri);
                    loadTextContent(sb.toString());
                } else {
                    throw new Exception("InputStream null for cache file");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to read cached .txt file: " + e.getMessage(), e);
                Toast.makeText(this, R.string.failed_to_load_cached_text, Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (uri != null) {
            String mimeType = getContentResolver().getType(uri);
            if ((mimeType != null && mimeType.equals("application/epub+zip")) || (mimeType == null && "epub".equalsIgnoreCase(fileType))) {
                Log.d(TAG, "Loading EPUB from URI: " + uri);
                loadEPUB(uri);
            } else {
                Log.d(TAG, "Loading PDF from URI: " + uri);
                loadPDF(uri);
            }
        } else {
            Log.e(TAG, "No content provided");
            Toast.makeText(this, R.string.no_content_provided, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Set up touch listener to detect taps/swipes near the bottom
        pdfRecyclerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    int height = v.getHeight();
                    float y = event.getY();
                    if (y > height * 0.92f) { // bottom 8% of the screen
                        showProgressOnScroll = true;
                        updateScrollProgress();
                        // Reset the hide timer when touching near bottom
                        scrollProgressHandler.removeCallbacks(hideProgressRunnable);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    // Start 2-second timer to hide progress bar after touch interaction ends
                    scrollProgressHandler.postDelayed(hideProgressRunnable, 1000);
                }
                return false; // Let RecyclerView handle the event as usual
            }
        });
    }

    private void updateScrollProgress() {
        if (!showProgressOnScroll) {
            if (scrollProgressContainer != null) animateHideProgressBar();
            return;
        }
        if (scrollProgressContainer == null || pageNumberText == null || scrollProgressBar == null || pages == null || pages.isEmpty()) {
            return;
        }
        LinearLayoutManager layoutManager = (LinearLayoutManager) pdfRecyclerView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
        if (firstVisiblePosition == RecyclerView.NO_POSITION) {
            return;
        }
        int totalPages = pages.size();
        int currentPage = firstVisiblePosition + 1; // Convert to 1-based page numbering
        int progressPercentage = (int) ((float) currentPage / totalPages * 100);
        runOnUiThread(() -> {
            animateShowProgressBar();
            String pageText = String.format("Page %d of %d", currentPage, totalPages);
            pageNumberText.setText(pageText);
            // Animate progress bar smoothly
            if (progressPercentage != lastProgressValue) {
                ObjectAnimator animator = ObjectAnimator.ofInt(scrollProgressBar, "progress", lastProgressValue, progressPercentage);
                animator.setDuration(PROGRESS_BAR_ANIMATION_DURATION);
                animator.start();
                lastProgressValue = progressPercentage;
            }
            scrollProgressHandler.removeCallbacks(hideProgressRunnable);
        });
    }

    @Override
    protected void onDestroy() {
        // Save position before destroying
        saveCurrentPosition();
        
        // Clean up handlers
        if (scrollProgressHandler != null) {
            scrollProgressHandler.removeCallbacks(hideProgressRunnable);
        }
        
        if (pdfParser != null) {
            pdfParser.close();
        }
        executorService.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        // Save position when activity is paused
        saveCurrentPosition();
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Force update of theme colors when configuration changes
        if (pdfRecyclerView != null) {
            pdfRecyclerView.invalidate();
        }
    }

    // Helper to split TXT into pages/chunks (simple implementation, can be improved)
    private List<String> splitTextIntoPages(String text, int charsPerPage) {
        List<String> pages = new ArrayList<>();
        String[] paragraphs = text.split("\\n+"); // Split by one or more newlines
        StringBuilder currentPage = new StringBuilder();
        int currentLength = 0;
        for (String paragraph : paragraphs) {
            int paragraphLength = paragraph.length() + 2; // +2 for the two newlines
            if (currentLength + paragraphLength > charsPerPage && currentLength > 0) {
                pages.add(currentPage.toString().trim());
                currentPage.setLength(0);
                currentLength = 0;
            }
            currentPage.append(paragraph).append("\n\n");
            currentLength += paragraphLength;
        }
        if (currentLength > 0) {
            pages.add(currentPage.toString().trim());
        }
        return pages;
    }

    private void loadTextContent(String textContent) {
        try {
            currentSettings = new PDFParser.TextSettings();
            currentSettings.fontSize = 54.0f;
            currentSettings.letterSpacing = 0.0f;
            currentSettings.textAlignment = Paint.Align.LEFT;
            currentSettings.lineHeight = 1.2f;
            currentSettings.paragraphSpacing = 1.5f;

            // Split TXT into pages (e.g., 2000 chars per page)
            List<String> txtChunks = splitTextIntoPages(textContent, 2000);
            pages = new ArrayList<>();
            for (int i = 0; i < txtChunks.size(); i++) {
                pages.add(new PDFParser.ParsedPage("", new ArrayList<>(), i + 1, 612.0f, 792.0f, currentSettings));
            }
            totalPages = pages.size();
            currentPage = 1;

            // Precompute StaticLayout for each page off the UI thread
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler mainHandler = new Handler(Looper.getMainLooper());
            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(currentSettings.fontSize);
            paint.setLetterSpacing(currentSettings.letterSpacing);
            paint.setTextAlign(Paint.Align.LEFT);
            executor.execute(() -> {
                int width = getResources().getDisplayMetrics().widthPixels - 64;
                for (int i = 0; i < txtChunks.size(); i++) {
                    String text = txtChunks.get(i);
                    Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL;
                    StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                        .setAlignment(align)
                        .setLineSpacing(0, currentSettings.lineHeight)
                        .setIncludePad(true)
                        .build();
                    pages.get(i).text = text;
                    pages.get(i).precomputedLayout = layout;
                }
                mainHandler.post(() -> {
                    pdfPageAdapter = new PDFPageAdapter(PDFViewerActivity.this, pages, PDFPageAdapter.DocumentType.TXT, null, txtChunks, null, null, currentSettings);
                    pdfRecyclerView.getRecycledViewPool().clear();
                    pdfRecyclerView.swapAdapter(pdfPageAdapter, false);
                    pdfPageAdapter.notifyDataSetChanged();
                    setupFirstTouchTextSettingsFix();
                    
                    // Configure MaterialScrollBar
                    if (materialScrollBar != null) {
                            materialScrollBar.setVisibility(View.GONE); // Change to VISIBLE if needed invisible
                            if (isDarkTheme()) {
                                materialScrollBar.setHandleColor(androidx.core.content.ContextCompat.getColor(this, R.color.progress_blue));
                                materialScrollBar.setBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.mtrl_textinput_default_box_stroke_color));
                                materialScrollBar.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
                                materialScrollBar.setHandleOffColor(androidx.core.content.ContextCompat.getColor(this, R.color.handle_off_color));
                            } else {
                                materialScrollBar.setHandleColor(androidx.core.content.ContextCompat.getColor(this, R.color.progress_blue));
                                materialScrollBar.setBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.mtrl_textinput_default_box_stroke_color));
                                materialScrollBar.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
                                materialScrollBar.setHandleOffColor(androidx.core.content.ContextCompat.getColor(this, R.color.handle_off_color));
                            }
//                          --  uncomment to get the indicator of the pages
//                        materialScrollBar.setIndicator(new CustomIndicator(this), true);
                        adaptMaterialScrollBarTheme();
                        updateMaterialScrollBarVisibility();
                    }
                    
                    // Set document type for position restoration
                    documentType = PDFPageAdapter.DocumentType.TXT;
                    
                    Log.d(TAG, "[TXT_LOAD] TXT adapter set successfully:");
                    Log.d(TAG, "[TXT_LOAD] - pages.size: " + pages.size());
                    Log.d(TAG, "[TXT_LOAD] - adapter item count: " + pdfPageAdapter.getItemCount());
                    Log.d(TAG, "[TXT_LOAD] - currentBook: " + (currentBook != null ? currentBook.getTitle() : "null"));
                    
                    // Try to restore position if book is already loaded, otherwise it will be called when book loads
                    if (currentBook != null && !isPositionRestored) {
                        Log.d(TAG, "[TXT_LOAD] Book already loaded, calling restoreReadingPosition()");
                        restoreReadingPosition();
                    } else {
                        Log.d(TAG, "[TXT_LOAD] Book not loaded yet, position restoration will be called when book loads");
                    }
                });
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing text content: " + e.getMessage());
            Toast.makeText(this, R.string.failed_to_load_text_content, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadEPUB(Uri epubUri) {
        try {
            currentSettings = new PDFParser.TextSettings();
            currentSettings.fontSize = 54.0f;
            currentSettings.letterSpacing = 0.0f;
            currentSettings.textAlignment = Paint.Align.LEFT;
            currentSettings.lineHeight = 1.2f;
            currentSettings.paragraphSpacing = 1.5f;

            epubParser = new EPUBParser(this);
            isEPUB = true;
            if (epubParser.parse(epubUri)) {
                List<String> textContent = epubParser.getTextContent();
                List<Bitmap> images = epubParser.getImages();
                List<String> epubPages = new ArrayList<>();
                List<List<PDFParser.ImageInfo>> epubImages = new ArrayList<>();
                int charsPerPage = 1200;
                for (int sectionIdx = 0; sectionIdx < textContent.size(); sectionIdx++) {
                    String section = textContent.get(sectionIdx);
                    // Use improved splitTextIntoPages for EPUB as well
                    List<String> sectionPages = splitTextIntoPages(section, charsPerPage);
                    boolean imageAdded = false;
                    for (int i = 0; i < sectionPages.size(); i++) {
                        epubPages.add(sectionPages.get(i));
                        List<PDFParser.ImageInfo> pageImages = new ArrayList<>();
                        if (!imageAdded && images != null && sectionIdx < images.size()) {
                            Bitmap bitmap = images.get(sectionIdx);
                            pageImages.add(new PDFParser.ImageInfo(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight()));
                            imageAdded = true;
                        }
                        epubImages.add(pageImages);
                    }
                }
                pages = new ArrayList<>();
                for (int i = 0; i < epubPages.size(); i++) {
                    pages.add(new PDFParser.ParsedPage("", new ArrayList<>(), i + 1, 595.0f, 842.0f, currentSettings));
                }
                totalPages = pages.size();
                // Precompute StaticLayout for each EPUB page off the UI thread
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Handler mainHandler = new Handler(Looper.getMainLooper());
                TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                paint.setTextSize(currentSettings.fontSize);
                paint.setLetterSpacing(currentSettings.letterSpacing);
                paint.setTextAlign(Paint.Align.LEFT);
                executor.execute(() -> {
                    int width = getResources().getDisplayMetrics().widthPixels - 64; // 32dp padding each side
                    for (int i = 0; i < epubPages.size(); i++) {
                        String text = epubPages.get(i);
                        Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL;
                        StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                            .setAlignment(align)
                            .setLineSpacing(0, currentSettings.lineHeight)
                            .setIncludePad(true)
                            .build();
                        pages.get(i).text = text;
                        pages.get(i).precomputedLayout = layout;
                    }
                    mainHandler.post(() -> {
                        pdfPageAdapter = new PDFPageAdapter(PDFViewerActivity.this, pages, PDFPageAdapter.DocumentType.EPUB, null, null, epubPages, epubImages, currentSettings);
                        pdfRecyclerView.getRecycledViewPool().clear();
                        pdfRecyclerView.swapAdapter(pdfPageAdapter, false);
                        
                        // Configure MaterialScrollBar
                        if (materialScrollBar != null) {
                                materialScrollBar.setVisibility(View.GONE); // Change to VISIBLE if needed invisible
                                if (isDarkTheme()) {
                                    materialScrollBar.setHandleColor(androidx.core.content.ContextCompat.getColor(this, R.color.progress_blue));
                                    materialScrollBar.setBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.mtrl_textinput_default_box_stroke_color));
                                    materialScrollBar.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
                                    materialScrollBar.setHandleOffColor(androidx.core.content.ContextCompat.getColor(this, R.color.handle_off_color));
                                } else {
                                    materialScrollBar.setHandleColor(androidx.core.content.ContextCompat.getColor(this, R.color.progress_blue));
                                    materialScrollBar.setBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.mtrl_textinput_default_box_stroke_color));
                                    materialScrollBar.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
                                    materialScrollBar.setHandleOffColor(androidx.core.content.ContextCompat.getColor(this, R.color.handle_off_color));
                                }
//                                -- uncomment to get the indicator of the pages
//                            materialScrollBar.setIndicator(new CustomIndicator(this), true);
                            adaptMaterialScrollBarTheme();
                            updateMaterialScrollBarVisibility();
                        }
                        
                        // Set document type for position restoration
                        documentType = PDFPageAdapter.DocumentType.EPUB;
                        
                        Log.d(TAG, "[EPUB_LOAD] EPUB adapter set successfully:");
                        Log.d(TAG, "[EPUB_LOAD] - pages.size: " + pages.size());
                        Log.d(TAG, "[EPUB_LOAD] - adapter item count: " + pdfPageAdapter.getItemCount());
                        Log.d(TAG, "[EPUB_LOAD] - currentBook: " + (currentBook != null ? currentBook.getTitle() : "null"));
                        
                        // Try to restore position if book is already loaded, otherwise it will be called when book loads
                        if (currentBook != null && !isPositionRestored) {
                            Log.d(TAG, "[EPUB_LOAD] Book already loaded, calling restoreReadingPosition()");
                            restoreReadingPosition();
                        } else {
                            Log.d(TAG, "[EPUB_LOAD] Book not loaded yet, position restoration will be called when book loads");
                        }
                        
                        String title = epubParser.getTitle();
                        String author = epubParser.getAuthor();
                        if (title != null && !title.isEmpty()) {
                            getSupportActionBar().setTitle(title);
                            if (author != null && !author.isEmpty()) {
                                getSupportActionBar().setSubtitle(author);
                            }
                        }
                    });
                });
            } else {
                Toast.makeText(this, R.string.failed_to_load_epub, Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading EPUB: " + e.getMessage());
            Toast.makeText(this, R.string.failed_to_load_epub, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadPDF(Uri pdfUri) {
        try {
            currentSettings = new PDFParser.TextSettings();
            currentSettings.fontSize = 54.0f;
            currentSettings.letterSpacing = 0.0f;
            currentSettings.textAlignment = Paint.Align.LEFT;
            currentSettings.lineHeight = 1.2f;
            currentSettings.paragraphSpacing = 1.5f;

            pdfParser = new PDFParser(this, pdfUri);
            totalPages = pdfParser.getPageCount();
            pages = new ArrayList<>(totalPages);
            for (int i = 0; i < totalPages; i++) {
                float pageWidth = 612.0f;
                float pageHeight = 792.0f;
                if (i == 0) {
                    try {
                        com.itextpdf.kernel.pdf.PdfPage firstPage = pdfParser.getPdfDocument().getPage(1);
                        if (firstPage != null) {
                            com.itextpdf.kernel.geom.Rectangle pageSize = firstPage.getPageSize();
                            pageWidth = pageSize.getWidth();
                            pageHeight = pageSize.getHeight();
                }
                    } catch (Exception e) {
                        // ignore
                    }
                }
                pages.add(new PDFParser.ParsedPage("", new ArrayList<>(), i + 1, pageWidth, pageHeight, currentSettings));
            }
            pdfPageAdapter = new PDFPageAdapter(this, pages, PDFPageAdapter.DocumentType.PDF, pdfParser, null, null, null, currentSettings);
            pdfRecyclerView.getRecycledViewPool().clear();
            pdfRecyclerView.swapAdapter(pdfPageAdapter, false);
            
            // Configure MaterialScrollBar
            if (materialScrollBar != null) {
                materialScrollBar.setVisibility(View.VISIBLE); // Change to GONE if needed invisible
                if (isDarkTheme()) {
                    materialScrollBar.setHandleColor(androidx.core.content.ContextCompat.getColor(this, R.color.progress_blue));
                    materialScrollBar.setBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.mtrl_textinput_default_box_stroke_color));
                    materialScrollBar.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
                    materialScrollBar.setHandleOffColor(androidx.core.content.ContextCompat.getColor(this, R.color.handle_off_color));
                } else {
                    materialScrollBar.setHandleColor(androidx.core.content.ContextCompat.getColor(this, R.color.progress_blue));
                    materialScrollBar.setBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.mtrl_textinput_default_box_stroke_color));
                    materialScrollBar.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
                    materialScrollBar.setHandleOffColor(androidx.core.content.ContextCompat.getColor(this, R.color.handle_off_color));
                }
            }
            
            // Set document type for position restoration
            documentType = PDFPageAdapter.DocumentType.PDF;
            
            Log.d(TAG, "[PDF_LOAD] PDF adapter set successfully:");
            Log.d(TAG, "[PDF_LOAD] - totalPages: " + totalPages);
            Log.d(TAG, "[PDF_LOAD] - pages.size: " + pages.size());
            Log.d(TAG, "[PDF_LOAD] - adapter item count: " + pdfPageAdapter.getItemCount());
            Log.d(TAG, "[PDF_LOAD] - currentBook: " + (currentBook != null ? currentBook.getTitle() : "null"));
            
            // Try to restore position if book is already loaded, otherwise it will be called when book loads
            if (currentBook != null && !isPositionRestored) {
                Log.d(TAG, "[PDF_LOAD] Book already loaded, calling restoreReadingPosition()");
                restoreReadingPosition();
            } else {
                Log.d(TAG, "[PDF_LOAD] Book not loaded yet, position restoration will be called when book loads");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing PDF: " + e.getMessage());
            Toast.makeText(this, R.string.failed_to_load_pdf, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void saveCurrentPosition() {
        if (currentBook != null && pdfRecyclerView != null && pdfRecyclerView.getLayoutManager() != null) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) pdfRecyclerView.getLayoutManager();
            int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
            int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
            Log.d(TAG, "[POSITION_SAVE] Attempting to save position:");
            Log.d(TAG, "[POSITION_SAVE] - currentBook: " + (currentBook != null ? currentBook.getTitle() : "null"));
            Log.d(TAG, "[POSITION_SAVE] - pdfRecyclerView: " + (pdfRecyclerView != null ? "not null" : "null"));
            Log.d(TAG, "[POSITION_SAVE] - layoutManager: " + (layoutManager != null ? "not null" : "null"));
            Log.d(TAG, "[POSITION_SAVE] - firstVisiblePosition: " + firstVisiblePosition);
            Log.d(TAG, "[POSITION_SAVE] - lastVisiblePosition: " + lastVisiblePosition);
            Log.d(TAG, "[POSITION_SAVE] - total pages: " + pages.size());
            if (firstVisiblePosition != RecyclerView.NO_POSITION) {
                int targetPosition = firstVisiblePosition;
                View firstView = layoutManager.findViewByPosition(firstVisiblePosition);
                if (firstView != null) {
                    int viewHeight = firstView.getHeight();
                    int offset = Math.abs(firstView.getTop());
                    float ratio = (float) offset / (float) viewHeight;
                    Log.d(TAG, "[POSITION_SAVE] - firstView.getTop(): " + firstView.getTop() + ", viewHeight: " + viewHeight + ", offset: " + offset + ", ratio: " + ratio);
                    if (documentType == PDFPageAdapter.DocumentType.TXT) {
                        // For TXT, use 1/2 page height as threshold
                        if (ratio > 0.5f && firstVisiblePosition + 1 < pages.size()) {
                            targetPosition = firstVisiblePosition + 1;
                            Log.d(TAG, "[POSITION_SAVE][TXT] User scrolled more than 1/4 into next page, targeting: " + targetPosition);
                        } else {
                            Log.d(TAG, "[POSITION_SAVE][TXT] User not far enough into next page, targeting: " + targetPosition);
                        }
                    } else {
                        // For PDF/EPUB, use half-visible logic
                        int visibleHeight = Math.min(firstView.getBottom(), pdfRecyclerView.getHeight()) - Math.max(firstView.getTop(), 0);
                        float visibleRatio = (float) visibleHeight / (float) viewHeight;
                        if (visibleRatio < 0.5f && firstVisiblePosition + 1 < pages.size()) {
                            targetPosition = firstVisiblePosition + 1;
                            Log.d(TAG, "[POSITION_SAVE] Less than half of first page visible, targeting next page: " + targetPosition);
                        } else {
                            Log.d(TAG, "[POSITION_SAVE] More than half of first page visible, targeting: " + targetPosition);
                        }
                    }
                }
                int oldPosition = currentBook.getReadingPosition();
                // Only update if the position has changed
                if (targetPosition != oldPosition) {
                    currentBook.setReadingPosition(targetPosition);
                    currentBook.setLastOpened(new java.util.Date());
                    bookRepository.update(currentBook);
                    Log.d(TAG, "[POSITION_SAVE] Successfully saved position: " + targetPosition + " (was: " + oldPosition + ") for book: " + currentBook.getTitle());
                } else {
                    Log.d(TAG, "[POSITION_SAVE] Position unchanged (" + targetPosition + "), not updating book.");
                }
            } else {
                Log.w(TAG, "[POSITION_SAVE] Failed to save position - NO_POSITION returned");
            }
        } else {
            Log.w(TAG, "[POSITION_SAVE] Cannot save position:");
            Log.w(TAG, "[POSITION_SAVE] - currentBook: " + (currentBook != null ? "not null" : "null"));
            Log.w(TAG, "[POSITION_SAVE] - pdfRecyclerView: " + (pdfRecyclerView != null ? "not null" : "null"));
            Log.w(TAG, "[POSITION_SAVE] - layoutManager: " + (pdfRecyclerView != null && pdfRecyclerView.getLayoutManager() != null ? "not null" : "null"));
        }
    }

    private void restoreReadingPosition() {
        Log.d(TAG, "[POSITION_RESTORE] Attempting to restore position:");
        Log.d(TAG, "[POSITION_RESTORE] - currentBook: " + (currentBook != null ? currentBook.getTitle() : "null"));
        Log.d(TAG, "[POSITION_RESTORE] - isPositionRestored: " + isPositionRestored);
        Log.d(TAG, "[POSITION_RESTORE] - pdfRecyclerView: " + (pdfRecyclerView != null ? "not null" : "null"));
        Log.d(TAG, "[POSITION_RESTORE] - pages.size: " + (pages != null ? pages.size() : "null"));
        
        if (currentBook != null && !isPositionRestored && pdfRecyclerView != null) {
            int savedPosition = currentBook.getReadingPosition();
            Log.d(TAG, "[POSITION_RESTORE] - savedPosition: " + savedPosition);
            Log.d(TAG, "[POSITION_RESTORE] - pages.size: " + pages.size());
            
            if (savedPosition > 0 && savedPosition < pages.size()) {
                Log.d(TAG, "[POSITION_RESTORE] Position is valid, waiting for pages to render around position: " + savedPosition);
                
                // Calculate the range of pages to wait for (3 above and 3 below)
                int startPage = Math.max(0, savedPosition - 3);
                int endPage = Math.min(pages.size() - 1, savedPosition + 3);
                
                Log.d(TAG, "[POSITION_RESTORE] Waiting for pages " + startPage + " to " + endPage + " to be rendered");
                
                // Force render the pages around the target position
                forceRenderPages(startPage, endPage, savedPosition);
            } else {
                Log.w(TAG, "[POSITION_RESTORE] Position is not valid: " + savedPosition + " (pages.size: " + pages.size() + ")");
                isPositionRestored = true;
            }
        } else {
            Log.w(TAG, "[POSITION_RESTORE] Cannot restore position:");
            Log.w(TAG, "[POSITION_RESTORE] - currentBook: " + (currentBook != null ? "not null" : "null"));
            Log.w(TAG, "[POSITION_RESTORE] - isPositionRestored: " + isPositionRestored);
            Log.w(TAG, "[POSITION_RESTORE] - pdfRecyclerView: " + (pdfRecyclerView != null ? "not null" : "null"));
        }
    }

    private void forceRenderPages(int startPage, int endPage, int targetPosition) {
        Log.d(TAG, "[FORCE_RENDER] Starting forced rendering of pages " + startPage + " to " + endPage);
        if (documentType == PDFPageAdapter.DocumentType.PDF && pdfParser != null) {
            // Only scroll to the target position if not yet restored
            mainHandler.post(() -> {
                if (!isPositionRestored) {
                    Log.d(TAG, "[FORCE_RENDER] Scrolling to position: " + targetPosition);
                    scrollToPosition(targetPosition);
                    isPositionRestored = true;
                } else {
                    Log.d(TAG, "[FORCE_RENDER] Not scrolling, already restored position.");
                }
            });
            // 2. Parse the target page first, then the rest in background
            executorService.submit(() -> {
                try {
                    // Parse target page first
                    if (targetPosition >= 0 && targetPosition < pages.size() && (pages.get(targetPosition).text == null || pages.get(targetPosition).text.isEmpty())) {
                        Log.d(TAG, "[FORCE_RENDER] Parsing target page " + (targetPosition + 1));
                        PDFParser.ParsedPage parsed;
                        synchronized (pdfParser) {
                            parsed = pdfParser.parsePage(targetPosition + 1, currentSettings);
                        }
                        if (parsed != null) {
                            pages.set(targetPosition, parsed);
                            Log.d(TAG, "[FORCE_RENDER] Successfully parsed target page " + (targetPosition + 1));
                            mainHandler.post(() -> pdfPageAdapter.notifyItemChanged(targetPosition));
                        }
                    }
                    // Parse before/after pages in background, skipping target
                    // First, before pages
                    for (int i = targetPosition - 1; i >= startPage; i--) {
                        if (i >= 0 && i < pages.size() && (pages.get(i).text == null || pages.get(i).text.isEmpty())) {
                            Log.d(TAG, "[FORCE_RENDER] Parsing before page " + (i + 1));
                            PDFParser.ParsedPage parsed;
                            synchronized (pdfParser) {
                                parsed = pdfParser.parsePage(i + 1, currentSettings);
                            }
                            if (parsed != null) {
                                pages.set(i, parsed);
                                Log.d(TAG, "[FORCE_RENDER] Successfully parsed before page " + (i + 1));
                                int notifyIndex = i;
                                mainHandler.post(() -> pdfPageAdapter.notifyItemChanged(notifyIndex));
                            }
                        }
                    }
                    // Then, after pages
                    for (int i = targetPosition + 1; i <= endPage; i++) {
                        if (i >= 0 && i < pages.size() && (pages.get(i).text == null || pages.get(i).text.isEmpty())) {
                            Log.d(TAG, "[FORCE_RENDER] Parsing after page " + (i + 1));
                            PDFParser.ParsedPage parsed;
                            synchronized (pdfParser) {
                                parsed = pdfParser.parsePage(i + 1, currentSettings);
                            }
                            if (parsed != null) {
                                pages.set(i, parsed);
                                Log.d(TAG, "[FORCE_RENDER] Successfully parsed after page " + (i + 1));
                                int notifyIndex = i;
                                mainHandler.post(() -> pdfPageAdapter.notifyItemChanged(notifyIndex));
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "[FORCE_RENDER] Error parsing pages: " + e.getMessage(), e);
                }
            });
        } else {
            // For EPUB and TXT, pages are pre-rendered, so we can restore immediately
            Log.d(TAG, "[FORCE_RENDER] EPUB/TXT detected, restoring position immediately");
            scrollToPosition(targetPosition);
        }
    }

    private void scrollToPosition(int position) {
        Log.d(TAG, "[SCROLL_TO] Attempting to scroll to position: " + position);
        
        if (pdfRecyclerView != null) {
            pdfRecyclerView.post(() -> {
                LinearLayoutManager layoutManager = (LinearLayoutManager) pdfRecyclerView.getLayoutManager();
                Log.d(TAG, "[SCROLL_TO] In post() callback:");
                Log.d(TAG, "[SCROLL_TO] - layoutManager: " + (layoutManager != null ? "not null" : "null"));
                Log.d(TAG, "[SCROLL_TO] - adapter: " + (pdfRecyclerView.getAdapter() != null ? "not null" : "null"));
                Log.d(TAG, "[SCROLL_TO] - adapter item count: " + (pdfRecyclerView.getAdapter() != null ? pdfRecyclerView.getAdapter().getItemCount() : "N/A"));
                
                if (layoutManager != null) {
                    Log.d(TAG, "[SCROLL_TO] Calling scrollToPositionWithOffset(" + position + ", 0)");
                    layoutManager.scrollToPositionWithOffset(position, 0);
                    Log.d(TAG, "[SCROLL_TO] Successfully scrolled to position: " + position + " for book: " + currentBook.getTitle());
                    isPositionRestored = true;
                } else {
                    Log.e(TAG, "[SCROLL_TO] LayoutManager is null in post() callback");
                }
            });
        } else {
            Log.e(TAG, "[SCROLL_TO] pdfRecyclerView is null");
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Check if touch is in the dragbar area (right edge of screen)
        float screenWidth = getResources().getDisplayMetrics().widthPixels;
        if (event.getX() > screenWidth * 0.9f) { // Right 10% of screen
            Log.d(TAG, "[DRAGBAR] Touch in dragbar area: " + event.getAction() + " at x=" + event.getX());
            if (event.getAction() == MotionEvent.ACTION_DOWN || 
                event.getAction() == MotionEvent.ACTION_MOVE) {
                // Show progress bar when touching dragbar area
                showProgressOnScroll = true;
                updateScrollProgress();
                // Reset the hide timer
                scrollProgressHandler.removeCallbacks(hideProgressRunnable);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                // Start timer to hide progress bar after dragbar interaction ends
                scrollProgressHandler.postDelayed(hideProgressRunnable, 1000);
            }
        }
        
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (pdfParser == null) return false;
        
        scaleDetector.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress()) {
                    float deltaX = event.getX() - lastTouchX;
                    float deltaY = event.getY() - lastTouchY;
                    
                    // Update scroll positions
                    pdfRecyclerView.scrollBy((int)-deltaX, 0);
                    
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (pdfParser == null) return false;
            
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f));
            pdfRecyclerView.invalidate();
            return true;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_pdf_viewer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_text_settings) {
            showTextSettingsDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showTextSettingsDialog() {
        TextSettingsDialog dialog = TextSettingsDialog.newInstance(currentSettings);
        dialog.show(getSupportFragmentManager(), "TextSettingsDialog");
    }

    @Override
    public void onSettingsChanged(PDFParser.TextSettings settings) {
        currentSettings = settings;
        if (pdfParser != null && pdfPageAdapter != null) {
            pdfPageAdapter.setTextSettings(settings);
            pdfPageAdapter.notifyDataSetChanged();
        } else if (pages != null && !pages.isEmpty()) {
            for (int i = 0; i < pages.size(); i++) {
                PDFParser.ParsedPage oldPage = pages.get(i);
                if (oldPage != null) {
                    PDFParser.ParsedPage newPage = new PDFParser.ParsedPage(
                        oldPage.text,
                        oldPage.images,
                        oldPage.pageNumber,
                        oldPage.pageWidth,
                        oldPage.pageHeight,
                        currentSettings
                    );
                    pages.set(i, newPage);
                }
                pdfPageAdapter.notifyItemChanged(i);
            }
        }
    }

    private void animateShowProgressBar() {
        if (scrollProgressContainer.getVisibility() != View.VISIBLE) {
            scrollProgressContainer.setVisibility(View.VISIBLE);
            scrollProgressContainer.setAlpha(0f);
            scrollProgressContainer.setTranslationY(scrollProgressContainer.getHeight());
            scrollProgressContainer.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(PROGRESS_ANIMATION_DURATION)
                    .start();
        }
    }

    private void animateHideProgressBar() {
        if (scrollProgressContainer.getVisibility() == View.VISIBLE) {
            scrollProgressContainer.animate()
                    .alpha(0f)
                    .translationY(scrollProgressContainer.getHeight())
                    .setDuration(PROGRESS_ANIMATION_DURATION)
                    .withEndAction(() -> scrollProgressContainer.setVisibility(View.GONE))
                    .start();
        }
    }

    private boolean isDarkTheme() {
        return (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void adaptMaterialScrollBarTheme() {
        if (materialScrollBar != null) {
            if (isDarkTheme()) {
                materialScrollBar.setHandleColor(androidx.core.content.ContextCompat.getColor(this, R.color.progress_blue));
                materialScrollBar.setBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.mtrl_textinput_default_box_stroke_color));
                materialScrollBar.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
                materialScrollBar.setHandleOffColor(androidx.core.content.ContextCompat.getColor(this, R.color.handle_off_color));
            } else {
                materialScrollBar.setHandleColor(androidx.core.content.ContextCompat.getColor(this, R.color.progress_blue));
                materialScrollBar.setBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.mtrl_textinput_default_box_stroke_color));
                materialScrollBar.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
                materialScrollBar.setHandleOffColor(androidx.core.content.ContextCompat.getColor(this, R.color.handle_off_color));
            }
        }
    }

    private void updateMaterialScrollBarVisibility() {
        if (materialScrollBar != null && pages != null) {
            if (pages.size() <= 2) { // Show message if too few pages
                materialScrollBar.setVisibility(View.VISIBLE); // Still show the bar
                Toast.makeText(this, "Scroll bar is available, but there are too few pages to scroll.", Toast.LENGTH_SHORT).show();
            } else {
                materialScrollBar.setVisibility(View.VISIBLE);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupFirstTouchTextSettingsFix() {
        pdfRecyclerView.setOnTouchListener((v, event) -> {
            if (!textSettingsAppliedAfterFirstTouch) {
                textSettingsAppliedAfterFirstTouch = true;
                // Set default text settings before rebind
                if (currentSettings != null) {
                    currentSettings.lineHeight = currentSettings.lineHeight - 0.01f; // changed by 0.01 lmaoooooooo
                }
                // Re-apply text settings to force rebind
                if (pdfPageAdapter != null && currentSettings != null) {
                    pdfPageAdapter.setTextSettings(currentSettings);
                    pdfPageAdapter.notifyDataSetChanged();
                }
                // Remove this listener so it only happens once
                pdfRecyclerView.setOnTouchListener(null);
            }
            return false; // Let the touch event continue as normal
        });
    }
} 