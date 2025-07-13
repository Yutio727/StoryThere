package com.example.storythere.viewing;

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
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.storythere.R;
import com.example.storythere.adapters.PageAdapter;
import com.example.storythere.listening.AudioReaderActivity;
import com.example.storythere.data.Book;
import com.example.storythere.data.BookRepository;
import android.animation.ObjectAnimator;

import com.example.storythere.parsers.EPUBParser;
import com.example.storythere.parsers.PDFParser;
import com.example.storythere.parsers.TextParser;
import com.turingtechnologies.materialscrollbar.DragScrollBar;

public class ViewerActivity extends AppCompatActivity implements TextSettingsDialog.TextSettingsListener, PageAdapter.TextSelectionCallback {
    private static final String TAG = "ViewerActivity";
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
    private PageAdapter pageAdapter;
    private PageAdapter.DocumentType documentType;
    
    // Text content variables for different document types
    private List<String> txtChunks;
    private List<String> epubTextChunks;
    private List<List<PDFParser.ImageInfo>> epubImages;
    
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
    
    // Text selection tracking
    private String lastSelectedText = "";
    private int lastSelectedPage = -1;
    private int lastSelectedStart = -1;
    private int lastSelectedEnd = -1;

    // Add these fields to the class
    private String cachedSelectedText = "";
    private int cachedSelectedPage = -1;
    private int cachedSelectedStart = -1;
    private int cachedSelectedEnd = -1;

    // Animation duration for progress bar
    private static final int PROGRESS_ANIMATION_DURATION = 250;
    private static final int PROGRESS_BAR_ANIMATION_DURATION = 200;

    // Add this field to the class
    private boolean textSettingsAppliedAfterFirstTouch = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable hardware acceleration
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
        
        setContentView(R.layout.activity_viewer);

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
        
        // Add touch listener to MaterialScrollBar for progress bar cooperation and priority loading
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
                        
                        // Trigger priority loading after dragbar interaction ends
                        Log.d(TAG, "[DRAGBAR] Drag ended, triggering priority loading");
                        triggerPriorityLoadingAfterDrag();
                    }
                    return false; // Let the dragbar handle the event normally
                }
            });
        }
        
        // Initialize scroll progress UI elements
        scrollProgressContainer = findViewById(R.id.scrollProgressContainer);
        pageNumberText = findViewById(R.id.pageNumberText);
        scrollProgressBar = findViewById(R.id.scrollProgressBar);
        
        // Handle touch events on progress bar container to allow dragbar access
        scrollProgressContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Check if touch is in the dragbar area (right 5% of screen)
                float screenWidth = getResources().getDisplayMetrics().widthPixels;
                if (event.getX() > screenWidth * 0.95f) {
                    // Touch is in dragbar area - pass the event to the dragbar
                    if (materialScrollBar != null) {
                        // Convert coordinates to dragbar's coordinate system
                        MotionEvent dragbarEvent = MotionEvent.obtain(event);
                        int[] containerLocation = new int[2];
                        v.getLocationInWindow(containerLocation);
                        int[] dragbarLocation = new int[2];
                        materialScrollBar.getLocationInWindow(dragbarLocation);
                        dragbarEvent.offsetLocation(containerLocation[0] - dragbarLocation[0], containerLocation[1] - dragbarLocation[1]);
                        
                        // Dispatch to dragbar
                        boolean handled = materialScrollBar.dispatchTouchEvent(dragbarEvent);
                        dragbarEvent.recycle();
                        return handled;
                    }
                } else {
                    // Touch is not in dragbar area - handle as normal progress bar interaction
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        // Hide progress bar on tap (outside dragbar area)
                        showProgressOnScroll = false;
                        animateHideProgressBar();
                    }
                }
                return true; // Consume the event
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
                    if (documentType == PageAdapter.DocumentType.PDF && pdfRecyclerView != null) {
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
                // Always update currentPage field to reflect the first visible page (0-based)
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
                    if (firstVisiblePosition != RecyclerView.NO_POSITION) {
                        currentPage = firstVisiblePosition;
                    }
                }
            }
        });
        
        // Set a temporary adapter to prevent "No adapter attached" error
        List<PDFParser.ParsedPage> tempPages = new ArrayList<>();
        PDFParser.TextSettings tempSettings = new PDFParser.TextSettings();
        tempSettings.fontSize = 54.0f;
        tempSettings.textAlignment = Paint.Align.LEFT;
        tempPages.add(new PDFParser.ParsedPage(getString(R.string.loading_content), new ArrayList<>(), 1, 612.0f, 792.0f, tempSettings));
        PageAdapter tempAdapter = new PageAdapter(this, tempPages, PageAdapter.DocumentType.TXT, null, null, null, null, tempSettings);
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
                    Log.d(TAG, "[BOOK_LOAD] Successfully loaded book: " + book.getTitle() + " with viewing position: " + book.getReadingPosition());
                    Log.d(TAG, "[BOOK_LOAD] Book details - ID: " + book.getId() + ", FilePath: " + book.getFilePath() + ", FileType: " + book.getFileType());
                    
                    // Try to restore position now that book is loaded
                    if (!isPositionRestored && pageAdapter != null) {
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
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    int height = v.getHeight();
                    float y = event.getY();
                    if (y > height * 0.9f) { // bottom 10% of the screen
                        showProgressOnScroll = true;
                        updateScrollProgress();
                        // Reset the hide timer when touching near bottom
                        scrollProgressHandler.removeCallbacks(hideProgressRunnable);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    // Start 1-second timer to hide progress bar after touch interaction ends
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
            String pageText = String.format(getString(R.string.page_d_of_d), currentPage, totalPages);
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
            this.txtChunks = splitTextIntoPages(textContent, 2000);
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
                    pageAdapter = new PageAdapter(ViewerActivity.this, pages, PageAdapter.DocumentType.TXT, null, txtChunks, null, null, currentSettings);
                    pageAdapter.setTextSelectionCallback(ViewerActivity.this);
                    pdfRecyclerView.getRecycledViewPool().clear();
                    pdfRecyclerView.swapAdapter(pageAdapter, false);
                    pageAdapter.notifyDataSetChanged();

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
                    documentType = PageAdapter.DocumentType.TXT;
                    
                    Log.d(TAG, "[TXT_LOAD] TXT adapter set successfully:");
                    Log.d(TAG, "[TXT_LOAD] - pages.size: " + pages.size());
                    Log.d(TAG, "[TXT_LOAD] - adapter item count: " + pageAdapter.getItemCount());
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
                this.epubTextChunks = new ArrayList<>();
                this.epubImages = new ArrayList<>();
                int charsPerPage = 1200;
                for (int sectionIdx = 0; sectionIdx < textContent.size(); sectionIdx++) {
                    String section = textContent.get(sectionIdx);
                    // Use improved splitTextIntoPages for EPUB as well
                    List<String> sectionPages = splitTextIntoPages(section, charsPerPage);
                    boolean imageAdded = false;
                    for (int i = 0; i < sectionPages.size(); i++) {
                        this.epubTextChunks.add(sectionPages.get(i));
                        List<PDFParser.ImageInfo> pageImages = new ArrayList<>();
                        if (!imageAdded && images != null && sectionIdx < images.size()) {
                            Bitmap bitmap = images.get(sectionIdx);
                            pageImages.add(new PDFParser.ImageInfo(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight()));
                            imageAdded = true;
                        }
                        this.epubImages.add(pageImages);
                    }
                }
                pages = new ArrayList<>();
                for (int i = 0; i < this.epubTextChunks.size(); i++) {
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
                    for (int i = 0; i < this.epubTextChunks.size(); i++) {
                        String text = this.epubTextChunks.get(i);
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
                                    pageAdapter = new PageAdapter(ViewerActivity.this, pages, PageAdapter.DocumentType.EPUB, null, null, this.epubTextChunks, this.epubImages, currentSettings);
            pageAdapter.setTextSelectionCallback(ViewerActivity.this);
            pdfRecyclerView.getRecycledViewPool().clear();
            pdfRecyclerView.swapAdapter(pageAdapter, false);
                        
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
                        documentType = PageAdapter.DocumentType.EPUB;
                        
                        Log.d(TAG, "[EPUB_LOAD] EPUB adapter set successfully:");
                        Log.d(TAG, "[EPUB_LOAD] - pages.size: " + pages.size());
                        Log.d(TAG, "[EPUB_LOAD] - adapter item count: " + pageAdapter.getItemCount());
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
            pageAdapter = new PageAdapter(this, pages, PageAdapter.DocumentType.PDF, pdfParser, null, null, null, currentSettings);
            pageAdapter.setTextSelectionCallback(ViewerActivity.this);
            pdfRecyclerView.getRecycledViewPool().clear();
            pdfRecyclerView.swapAdapter(pageAdapter, false);
            
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
            documentType = PageAdapter.DocumentType.PDF;
            
            Log.d(TAG, "[PDF_LOAD] PDF adapter set successfully:");
            Log.d(TAG, "[PDF_LOAD] - totalPages: " + totalPages);
            Log.d(TAG, "[PDF_LOAD] - pages.size: " + pages.size());
            Log.d(TAG, "[PDF_LOAD] - adapter item count: " + pageAdapter.getItemCount());
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
                    if (documentType == PageAdapter.DocumentType.TXT) {
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
        if (documentType == PageAdapter.DocumentType.PDF && pdfParser != null) {
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
                            mainHandler.post(() -> {
                                // Check if view type should change after parsing
                                if (pageAdapter.shouldUseTextView(targetPosition)) {
                                    // Force recreation of view holder to switch to TextView
                                    pageAdapter.notifyItemChanged(targetPosition);
                                } else {
                                    // Just update the existing PDFView
                                    pageAdapter.notifyItemChanged(targetPosition);
                                }
                            });
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
                                mainHandler.post(() -> {
                                    // Check if view type should change after parsing
                                    if (pageAdapter.shouldUseTextView(notifyIndex)) {
                                        // Force recreation of view holder to switch to TextView
                                        pageAdapter.notifyItemChanged(notifyIndex);
                                    } else {
                                        // Just update the existing PDFView
                                        pageAdapter.notifyItemChanged(notifyIndex);
                                    }
                                });
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
                                mainHandler.post(() -> {
                                    // Check if view type should change after parsing
                                    if (pageAdapter.shouldUseTextView(notifyIndex)) {
                                        // Force recreation of view holder to switch to TextView
                                        pageAdapter.notifyItemChanged(notifyIndex);
                                    } else {
                                        // Just update the existing PDFView
                                        pageAdapter.notifyItemChanged(notifyIndex);
                                    }
                                });
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
        if (event.getX() > screenWidth * 0.90f) { // Right 10% of screen
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
        } else if (id == R.id.action_listen_selected) {
            launchAudioReaderWithSelectedText();
            return true;
        } else if (id == R.id.action_bookmark) {
            int bookmarkPosition = currentPage;
            if (currentBook != null) {
                long bookId = currentBook.getId();
                android.content.SharedPreferences prefs = getSharedPreferences("bookmarks", MODE_PRIVATE);
                String key = "bookmark_" + bookId;
                Object raw = prefs.getAll().get(key);
                String json;
                if (raw instanceof String) {
                    json = (String) raw;
                } else if (raw instanceof Integer) {
                    // Migrate old int bookmark to JSON array
                    int oldBookmark = (Integer) raw;
                    org.json.JSONArray arrMigrate = new org.json.JSONArray();
                    org.json.JSONObject bookmarkMigrate = new org.json.JSONObject();
                    try {
                        bookmarkMigrate.put("position", oldBookmark);
                        bookmarkMigrate.put("timestamp", System.currentTimeMillis());
                        bookmarkMigrate.put("label", "");
                        arrMigrate.put(bookmarkMigrate);
                    } catch (Exception e) { /* ignore */ }
                    json = arrMigrate.toString();
                    prefs.edit().remove(key).putString(key, json).apply();
                } else {
                    json = "[]";
                }
                org.json.JSONArray arr;
                try {
                    arr = new org.json.JSONArray(json);
                } catch (Exception e) {
                    arr = new org.json.JSONArray();
                }
                org.json.JSONObject bookmark = new org.json.JSONObject();
                try {
                    bookmark.put("position", bookmarkPosition);
                    bookmark.put("timestamp", System.currentTimeMillis());
                    bookmark.put("label", ""); // Optional label, empty for now
                    arr.put(bookmark);
                    prefs.edit().putString(key, arr.toString()).apply();
                    Toast.makeText(this, R.string.bookmark_saved, Toast.LENGTH_SHORT).show();
                    // Logging all bookmarks and the latest bookmark
                    Log.d(TAG, "[BOOKMARKS] All bookmarks for book " + bookId + ": " + arr.toString());
                    Log.d(TAG, "[BOOKMARKS] Latest bookmark: " + bookmark.toString());
                } catch (Exception e) {
                    Toast.makeText(this, R.string.failed_to_save_bookmark, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, R.string.could_not_save_bookmark_no_book_loaded, Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (id == R.id.action_show_bookmarks) {
            if (currentBook != null) {
                long bookId = currentBook.getId();
                android.content.SharedPreferences prefs = getSharedPreferences("bookmarks", MODE_PRIVATE);
                String key = "bookmark_" + bookId;
                Object raw = prefs.getAll().get(key);
                String json;
                if (raw instanceof String) {
                    json = (String) raw;
                } else if (raw instanceof Integer) {
                    // Migrate old int bookmark to JSON array
                    int oldBookmark = (Integer) raw;
                    org.json.JSONArray arrMigrate = new org.json.JSONArray();
                    org.json.JSONObject bookmarkMigrate = new org.json.JSONObject();
                    try {
                        bookmarkMigrate.put("position", oldBookmark);
                        bookmarkMigrate.put("timestamp", System.currentTimeMillis());
                        bookmarkMigrate.put("label", "");
                        arrMigrate.put(bookmarkMigrate);
                    } catch (Exception e) { /* ignore */ }
                    json = arrMigrate.toString();
                    prefs.edit().remove(key).putString(key, json).apply();
                } else {
                    json = "[]";
                }
                org.json.JSONArray arr;
                try {
                    arr = new org.json.JSONArray(json);
                } catch (Exception e) {
                    arr = new org.json.JSONArray();
                }
                // Show bottom sheet with bookmarks
                android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
                android.view.View sheetView = inflater.inflate(R.layout.bottom_sheet_bookmarks, null);
                android.widget.ListView listView = sheetView.findViewById(R.id.bookmark_list);
                java.util.List<com.example.storythere.adapters.BookmarkAdapter.BookmarkItem> bookmarkItems = new java.util.ArrayList<>();
                
                // Create a copy of the JSON array to make it effectively final
                final org.json.JSONArray bookmarksArray = arr;
                
                for (int i = 0; i < bookmarksArray.length(); i++) {
                    org.json.JSONObject bm = bookmarksArray.optJSONObject(i);
                    if (bm != null) {
                        int pos = bm.optInt("position", -1);
                        long ts = bm.optLong("timestamp", 0);
                        String label = bm.optString("label", "");
                        String display = getString(R.string.position) + pos + getString(R.string.time) + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(ts));
                        if (!label.isEmpty()) display += getString(R.string.label) + label;
                        bookmarkItems.add(new com.example.storythere.adapters.BookmarkAdapter.BookmarkItem(display, pos, ts, label));
                    }
                }
                
                com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
                dialog.setContentView(sheetView);
                
                // Create adapter reference that can be used in lambda
                final com.example.storythere.adapters.BookmarkAdapter[] adapterRef = new com.example.storythere.adapters.BookmarkAdapter[1];
                
                adapterRef[0] = new com.example.storythere.adapters.BookmarkAdapter(
                    this, 
                    bookmarkItems,
                    bookmark -> {
                        // Handle bookmark click
                        scrollToBookmarkPosition(bookmark.position);
                        dialog.dismiss();
                    },
                    (bookmark, position) -> {
                        // Handle bookmark delete
                        deleteBookmark(bookId, position, bookmarksArray);
                        bookmarkItems.remove(position);
                        adapterRef[0].notifyDataSetChanged();
                        if (bookmarkItems.isEmpty()) {
                            dialog.dismiss();
                        }
                    }
                );
                listView.setAdapter(adapterRef[0]);
                dialog.show();
            } else {
                Toast.makeText(this, R.string.no_book_loaded, Toast.LENGTH_SHORT).show();
            }
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
        if (pdfParser != null && pageAdapter != null) {
            pageAdapter.setTextSettings(settings);
            pageAdapter.notifyDataSetChanged();
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
                pageAdapter.notifyItemChanged(i);
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
    
    // TextSelectionCallback implementation
    @Override
    public void onTextSelected(String selectedText, int pagePosition, int start, int end) {
        lastSelectedText = selectedText;
        lastSelectedPage = pagePosition;
        lastSelectedStart = start;
        lastSelectedEnd = end;
        // Also update cached selection
        cachedSelectedText = selectedText;
        cachedSelectedPage = pagePosition;
        cachedSelectedStart = start;
        cachedSelectedEnd = end;
        Log.d(TAG, "Text selected on page " + pagePosition + ": '" + selectedText + "' (start: " + start + ", end: " + end + ")");
    }
    
    @Override
    public void onSelectionCleared(int pagePosition) {
        lastSelectedText = "";
        lastSelectedPage = -1;
        lastSelectedStart = -1;
        lastSelectedEnd = -1;
        // Do NOT clear cached selection here
        Log.d(TAG, "Text selection cleared on page " + pagePosition);
    }
    
    private void launchAudioReaderWithSelectedText() {
        String selectionToUse = lastSelectedText;
        int pageToUse = lastSelectedPage;
        int startToUse = lastSelectedStart;
        int endToUse = lastSelectedEnd;
        // If current selection is empty, use cached
        if (selectionToUse == null || selectionToUse.trim().isEmpty() || pageToUse < 0 || startToUse < 0 || endToUse < 0) {
            selectionToUse = cachedSelectedText;
            pageToUse = cachedSelectedPage;
            startToUse = cachedSelectedStart;
            endToUse = cachedSelectedEnd;
        }
        if (selectionToUse == null || selectionToUse.trim().isEmpty() || pageToUse < 0 || startToUse < 0 || endToUse < 0) {
            Toast.makeText(this, R.string.please_select_some_text_first, Toast.LENGTH_SHORT).show();
            return;
        }
        // Temporarily set lastSelected* to cached values for calculation
        int oldPage = lastSelectedPage, oldStart = lastSelectedStart, oldEnd = lastSelectedEnd;
        String oldText = lastSelectedText;
        lastSelectedText = selectionToUse;
        lastSelectedPage = pageToUse;
        lastSelectedStart = startToUse;
        lastSelectedEnd = endToUse;
        int wordPosition = calculateWordPositionFromSelection();
        // Restore old values
        lastSelectedText = oldText;
        lastSelectedPage = oldPage;
        lastSelectedStart = oldStart;
        lastSelectedEnd = oldEnd;
        if (wordPosition == -1) {
            Toast.makeText(this, R.string.could_not_determine_text_position, Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Launching AudioReader with word position: " + wordPosition + " for text: '" + selectionToUse + "'");
        launchAudioReader(wordPosition);
    }
    
    private int calculateWordPositionFromSelection() {
        if (lastSelectedPage < 0 || lastSelectedStart < 0 || lastSelectedEnd < 0) {
            return -1;
        }

        // Get the full text content up to the selected page
        String fullText = getFullTextContent();
        Log.d(TAG, "Getting the full text content up to the selected page.");
        if (fullText == null) {
            return -1;
        }
        
        // Get the text of the current page where selection occurred
        String pageText = getPageText(lastSelectedPage);
        if (pageText == null) {
            return -1;
        }
        
        // Calculate word position
        return calculateWordPosition(fullText, pageText, lastSelectedStart, lastSelectedEnd);
    }
    
    private String getFullTextContent() {
        if (documentType == PageAdapter.DocumentType.TXT && this.txtChunks != null) {
            StringBuilder fullText = new StringBuilder();
            for (String chunk : this.txtChunks) {
                fullText.append(chunk).append("\n\n");
            }
            return fullText.toString();
        } else if (documentType == PageAdapter.DocumentType.EPUB && this.epubTextChunks != null) {
            StringBuilder fullText = new StringBuilder();
            for (String chunk : this.epubTextChunks) {
                fullText.append(chunk).append("\n\n");
            }
            return fullText.toString();
        } else if (documentType == PageAdapter.DocumentType.PDF) {
            File cacheFile = null;
            if (currentBook != null) {
                String parsedTextPath = currentBook.getParsedTextPath();
                if (parsedTextPath == null || parsedTextPath.isEmpty()) {
                    // Generate the parsed text path as in BookOptionsActivity
                    String parsedFileName;
                    long id = currentBook.getId();
                    if (id != 0) {
                        parsedFileName = "parsed_" + id + ".txt";
                    } else {
                        parsedFileName = "parsed_" + System.currentTimeMillis() + ".txt";
                    }
                    cacheFile = new File(getCacheDir(), parsedFileName);
                    currentBook.setParsedTextPath(cacheFile.getAbsolutePath());
                    if (bookRepository != null) {
                        bookRepository.update(currentBook);
                    }
                    Log.d(TAG, "[PDF_FULLTEXT] Generated and set parsedTextPath: " + cacheFile.getAbsolutePath());
                } else {
                    cacheFile = new File(parsedTextPath);
                    Log.d(TAG, "[PDF_FULLTEXT] Using Book parsedTextPath: " + cacheFile.getAbsolutePath() + ", exists: " + cacheFile.exists());
                }
                if (cacheFile.exists()) {
                    try {
                        String textContent = new String(java.nio.file.Files.readAllBytes(cacheFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                        Log.d(TAG, "[PDF_FULLTEXT] Successfully read parsedTextPath, length: " + textContent.length());
                        return textContent;
                    } catch (Exception e) {
                        Log.e(TAG, "[PDF_FULLTEXT] Failed to read parsedTextPath: " + e.getMessage());
                    }
                } else {
                    // If the cache file does not exist, parse the full PDF and cache it (parallel, as in BookOptionsActivity)
                    Log.d(TAG, "[PDF_FULLTEXT] Cache file does not exist, parsing full PDF: " + cacheFile.getAbsolutePath());
                    try {
                        Uri contentUri = Uri.parse(currentBook.getFilePath());
                        InputStream baseInputStream = getContentResolver().openInputStream(contentUri);
                        if (baseInputStream == null) throw new Exception("Failed to open PDF");
                        com.itextpdf.kernel.pdf.PdfReader baseReader = new com.itextpdf.kernel.pdf.PdfReader(baseInputStream);
                        com.itextpdf.kernel.pdf.PdfDocument baseDoc = new com.itextpdf.kernel.pdf.PdfDocument(baseReader);
                        int totalPages = baseDoc.getNumberOfPages();
                        baseDoc.close();
                        baseReader.close();
                        baseInputStream.close();
                        int numThreads = Math.min(4, totalPages);
                        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(numThreads);
                        java.util.List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();
                        for (int i = 1; i <= totalPages; i++) {
                            final int pageIndex = i;
                            futures.add(executor.submit(() -> {
                                try (InputStream inputStream = getContentResolver().openInputStream(contentUri)) {
                                    com.itextpdf.kernel.pdf.PdfReader reader = new com.itextpdf.kernel.pdf.PdfReader(inputStream);
                                    com.itextpdf.kernel.pdf.PdfDocument doc = new com.itextpdf.kernel.pdf.PdfDocument(reader);
                                    String text = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(
                                            doc.getPage(pageIndex),
                                            new com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy()
                                    );
                                    doc.close();
                                    reader.close();
                                    return text;
                                } catch (Exception e) {
                                    return "";
                                }
                            }));
                        }
                        StringBuilder allText = new StringBuilder();
                        for (int i = 0; i < totalPages; i++) {
                            String pageText = futures.get(i).get();
                            synchronized (allText) {
                                allText.append(pageText).append("\n");
                            }
                        }
                        executor.shutdown();
                        String textContent = allText.toString();
                        // Write to cache
                        try (java.io.FileWriter writer = new java.io.FileWriter(cacheFile, false)) {
                            writer.write(textContent);
                            Log.d(TAG, "[PDF_FULLTEXT] Successfully parsed and cached PDF text, length: " + textContent.length());
                        }
                        return textContent;
                    } catch (Exception e) {
                        Log.e(TAG, "[PDF_FULLTEXT] Error parsing and caching PDF: " + e.getMessage());
                    }
                }
            } else {
                Log.w(TAG, "[PDF_FULLTEXT] No Book object available.");
                return null;
            }
            return null;
        }
        return null;
    }
    
    private String getPageText(int pagePosition) {
        if (pagePosition < 0) return null;
        
        if (documentType == PageAdapter.DocumentType.TXT && this.txtChunks != null && pagePosition < this.txtChunks.size()) {
            return this.txtChunks.get(pagePosition);
        } else if (documentType == PageAdapter.DocumentType.EPUB && this.epubTextChunks != null && pagePosition < this.epubTextChunks.size()) {
            return this.epubTextChunks.get(pagePosition);
        } else if (documentType == PageAdapter.DocumentType.PDF && pages != null && pagePosition < pages.size()) {
            PDFParser.ParsedPage page = pages.get(pagePosition);
            return page != null ? page.text : null;
        }
        return null;
    }
    
    private static String stripAllWhitespace(String s) {
        return s.replaceAll("\\s+", "");
    }

    private int calculateWordPosition(String fullText, String pageText, int start, int end) {
        if (fullText == null || pageText == null) return -1;

        // Clean both strings before searching
        String cleanedFullText = PDFParser.cleanText(fullText);
        String cleanedPageText = PDFParser.cleanText(pageText);

        // Get the selected string from the page text
        if (start < 0 || end > cleanedPageText.length() || start >= end) {
            Log.e(TAG, "Invalid selection range for page text");
            return -1;
        }
        String selectedString = cleanedPageText.substring(start, end);
        String strippedFullText = stripAllWhitespace(cleanedFullText);
        String strippedSelectedString = stripAllWhitespace(selectedString);

        // --- DEBUG LOGGING ---
        Log.d(TAG, "[BOOKMARK_DEBUG] selectedString: '" + selectedString + "'");
        Log.d(TAG, "[BOOKMARK_DEBUG] strippedSelectedString: '" + strippedSelectedString + "'");
        Log.d(TAG, "[BOOKMARK_DEBUG] strippedFullText (first 200): '" + strippedFullText.substring(0, Math.min(200, strippedFullText.length())) + "'");

        int selectionStartInFull = strippedFullText.indexOf(strippedSelectedString);
        Log.d(TAG, "[BOOKMARK_DEBUG] selectionStartInFull: " + selectionStartInFull);
        if (selectionStartInFull == -1) {
            Log.e(TAG, "Could not find stripped selected string in stripped full text");
            Log.e(TAG, "[DEBUG] strippedSelectedString length: " + strippedSelectedString.length() + ", strippedFullText length: " + strippedFullText.length());
            Log.e(TAG, "[DEBUG] strippedSelectedString (first 200): " + strippedSelectedString.substring(0, Math.min(200, strippedSelectedString.length())));
            Log.e(TAG, "[DEBUG] strippedFullText (first 200): " + strippedFullText.substring(0, Math.min(200, strippedFullText.length())));
            return -1;
        }

        // Map the stripped index back to the original cleanedFullText
        int nonWsCount = 0;
        int charPosInFull = -1;
        for (int i = 0; i < cleanedFullText.length(); i++) {
            if (!Character.isWhitespace(cleanedFullText.charAt(i))) {
                if (nonWsCount == selectionStartInFull) {
                    charPosInFull = i;
                    break;
                }
                nonWsCount++;
            }
        }
        if (charPosInFull == -1) {
            Log.e(TAG, "Failed to map stripped index back to original text");
            return -1;
        }

        // Now count words up to charPosInFull in cleanedFullText
        String[] fullWords = cleanedFullText.split("\\s+");
        int wordCount = 0;
        int currentPos = 0;
        for (String word : fullWords) {
            int wordStart = cleanedFullText.indexOf(word, currentPos);
            if (wordStart == -1) break;
            if (wordStart >= charPosInFull) break;
            wordCount++;
            currentPos = wordStart + word.length();
        }

        Log.d(TAG, "Calculated word position (whitespace-stripped, selected string): " + wordCount + " (selection: " + start + "-" + end + " in page " + lastSelectedPage + ")");
        return wordCount;
    }
    
    private void launchAudioReader(int wordPosition) {
        try {
            // Get the current book information
            String title = getIntent().getStringExtra("title");
            
            // Parse text content similar to BookOptionsActivity
            String textContent;
            boolean isRussian = false;
            Uri textUri = null;
            
            if (documentType == PageAdapter.DocumentType.TXT) {
                // For TXT files, use the existing parsed content
                textContent = getFullTextContent();
                isRussian = TextParser.isTextPrimarilyRussian(textContent);
                
                // Save to cache file if it doesn't exist
                String safeTitle = (title != null ? title.replaceAll("[^a-zA-Z0-9]", "_") : "book");
                File cacheFile = new File(getCacheDir(), safeTitle + "_audio.txt");
                if (!cacheFile.exists()) {
                    Log.d(TAG, "[CACHE] No existing cached file found.");
                    try (FileWriter writer = new FileWriter(cacheFile, false)) {
                        writer.write(textContent);
                        Log.d(TAG, "[CACHE] Writing text to cached file: " + cacheFile);
                    }
                }
                else{
                    Log.d(TAG, "[CACHE] Found existing cache file: " + cacheFile);
                }
                textUri = Uri.fromFile(cacheFile);
                
            } else if (documentType == PageAdapter.DocumentType.EPUB) {
                // For EPUB files, use the existing parsed content
                textContent = getFullTextContent();
                isRussian = TextParser.isTextPrimarilyRussian(textContent);
                
                // Save to cache file if it doesn't exist
                String safeTitle = (title != null ? title.replaceAll("[^a-zA-Z0-9]", "_") : "book");
                File cacheFile = new File(getCacheDir(), safeTitle + "_audio.txt");
                if (!cacheFile.exists()) {
                    Log.d(TAG, "[CACHE] No existing cached file found.");
                    try (FileWriter writer = new FileWriter(cacheFile, false)) {
                        writer.write(textContent);
                        Log.d(TAG, "[CACHE] Writing text to cached file: " + cacheFile);
                    }
                }
                else{
                    Log.d(TAG, "[CACHE] Found existing cache file: ." + cacheFile);
                }
                textUri = Uri.fromFile(cacheFile);
                
            } else if (documentType == PageAdapter.DocumentType.PDF) {
                // For PDF files, extract text content
                textContent = getFullTextContent();
                isRussian = TextParser.isTextPrimarilyRussian(textContent);
                
                // Save to cache file if it doesn't exist
                String safeTitle = (title != null ? title.replaceAll("[^a-zA-Z0-9]", "_") : "book");
                File cacheFile = new File(getCacheDir(), safeTitle + "_audio.txt");
                if (!cacheFile.exists()) {
                    Log.d(TAG, "[CACHE] No existing cached file found.");
                    try (FileWriter writer = new FileWriter(cacheFile, false)) {
                        writer.write(textContent);
                        Log.d(TAG, "[CACHE] Writing text to cached file: " + cacheFile);
                    }
                }
                else{
                    Log.d(TAG, "[CACHE] Found existing cache file: ." + cacheFile);
                }
                textUri = Uri.fromFile(cacheFile);
            }
            
            if (textUri == null) {
                Toast.makeText(this, R.string.could_not_prepare_text_for_audio_reading, Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Launch AudioReaderActivity
            Intent audioIntent = new Intent(this, AudioReaderActivity.class);
            audioIntent.setData(textUri);
            audioIntent.putExtra("fileType", "txt");
            audioIntent.putExtra("title", title);
            audioIntent.putExtra("author", currentBook != null ? currentBook.getAuthor() : "Unknown Author");
            audioIntent.putExtra("is_russian", isRussian);
            audioIntent.putExtra("start_position", wordPosition); // Pass the calculated word position
            if (currentBook != null && currentBook.getPreviewImagePath() != null) {
                audioIntent.putExtra("previewImagePath", currentBook.getPreviewImagePath());
            }
            
            startActivity(audioIntent);
            
        } catch (Exception e) {
            Log.e(TAG, "Error launching AudioReader: " + e.getMessage(), e);
            Toast.makeText(this, R.string.error_launching_audio_reader, Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, R.string.scroll_bar_is_available_but_there_are_too_few_pages_to_scroll, Toast.LENGTH_SHORT).show();
            } else {
                materialScrollBar.setVisibility(View.VISIBLE);
            }
        }
    }

    private void triggerPriorityLoadingAfterDrag() {
        Log.d(TAG, "[PRIORITY_LOAD] Triggering priority loading after drag");
        if (documentType == PageAdapter.DocumentType.PDF && pdfRecyclerView != null) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) pdfRecyclerView.getLayoutManager();
            if (layoutManager != null) {
                int first = layoutManager.findFirstVisibleItemPosition();
                int last = layoutManager.findLastVisibleItemPosition();
                if (first != RecyclerView.NO_POSITION && last != RecyclerView.NO_POSITION) {
                    int window = 2; // Pre-parse 2 pages before and after
                    int start = Math.max(0, first - window);
                    int end = Math.min(totalPages - 1, last + window);
                    Log.d(TAG, "[PRIORITY_LOAD] Drag ended at position " + first + ", triggering priority loading for pages " + start + " to " + end);
                    forceRenderPages(start, end, first); // targetPosition is first visible
                }
            }
        } else {
            Log.d(TAG, "[PRIORITY_LOAD] Not PDF or RecyclerView not available, skipping priority loading");
        }
    }

    private void scrollToBookmarkPosition(int bookmarkPosition) {
        Log.d(TAG, "[BOOKMARK_SCROLL] Requested bookmarkPosition: " + bookmarkPosition);
        int page = -1;
        if (documentType == PageAdapter.DocumentType.PDF && pages != null && !pages.isEmpty()) {
            page = Math.max(0, Math.min(bookmarkPosition, pages.size() - 1));
            Log.d(TAG, "[BOOKMARK_SCROLL] Scrolling to PDF page " + (page+1) + " for bookmarkPosition " + bookmarkPosition);
            scrollToPosition(page);
//            Toast.makeText(this, getString(R.string.jumped_to_bookmark_page) + (page+1) + ")", Toast.LENGTH_SHORT).show();
        } else if (documentType == PageAdapter.DocumentType.TXT && txtChunks != null) {
            page = Math.max(0, Math.min(bookmarkPosition, txtChunks.size() - 1));
            Log.d(TAG, "[BOOKMARK_SCROLL] Scrolling to TXT page " + (page+1) + " for bookmarkPosition " + bookmarkPosition);
            scrollToPosition(page);
//            Toast.makeText(this, getString(R.string.jumped_to_bookmark_chunk) + (page+1) + ")", Toast.LENGTH_SHORT).show();
        } else if (documentType == PageAdapter.DocumentType.EPUB && epubTextChunks != null) {
            page = Math.max(0, Math.min(bookmarkPosition, epubTextChunks.size() - 1));
            Log.d(TAG, "[BOOKMARK_SCROLL] Scrolling to EPUB page " + (page+1) + " for bookmarkPosition " + bookmarkPosition);
            scrollToPosition(page);
//            Toast.makeText(this, getString(R.string.jumped_to_bookmark_epub_chunk) + (page+1) + ")", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteBookmark(long bookId, int position, org.json.JSONArray bookmarksArray) {
        try {
            // Remove the bookmark from the JSON array
            org.json.JSONArray newArray = new org.json.JSONArray();
            for (int i = 0; i < bookmarksArray.length(); i++) {
                if (i != position) {
                    newArray.put(bookmarksArray.get(i));
                }
            }
            
            // Save the updated bookmarks
            android.content.SharedPreferences prefs = getSharedPreferences("bookmarks", MODE_PRIVATE);
            String key = "bookmark_" + bookId;
            prefs.edit().putString(key, newArray.toString()).apply();
            
            Toast.makeText(this, R.string.bookmark_deleted, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Deleted bookmark at position " + position + " for book " + bookId);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting bookmark", e);
            Toast.makeText(this, "Error deleting bookmark", Toast.LENGTH_SHORT).show();
        }
    }
}