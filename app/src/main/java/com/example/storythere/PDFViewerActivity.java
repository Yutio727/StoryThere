package com.example.storythere;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PDFViewerActivity extends AppCompatActivity implements TextSettingsDialog.TextSettingsListener, ScrollView.OnScrollChangeListener {
    private static final String TAG = "PDFViewerActivity";
    private static final int PAGE_LOAD_BATCH_SIZE = 5; // Load 2 pages at a time
    private static final float SCROLL_THRESHOLD_PERCENT = 0.1f; // distance to load
    private static final long SCROLL_DEBOUNCE_DELAY = 0; // milliseconds
    
    private PDFView pdfView;
    private ScrollView scrollView;
    private HorizontalScrollView horizontalScrollView;
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
    private boolean isLoadingMore = false;
    private boolean isDestroyed = false;
    private boolean isFirstLoad = true;
    private Handler scrollHandler = new Handler(Looper.getMainLooper());
    private Runnable scrollRunnable;
    private int lastScrollY = 0;
    private EPUBParser epubParser;
    private boolean isEPUB = false;

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

        // Initialize views
        pdfView = findViewById(R.id.pdfView);
        scrollView = findViewById(R.id.scrollView);
        horizontalScrollView = findViewById(R.id.horizontalScrollView);
        
        // Set scroll listener
        scrollView.setOnScrollChangeListener(this);

        // Initialize scale detector
        scaleDetector = new ScaleGestureDetector(this, new ScaleListener());

        // Initialize thread pool and handler
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        mainHandler = new Handler(Looper.getMainLooper());

        // Get content from intent
        Intent intent = getIntent();
        String textContent = intent.getStringExtra("textContent");
        if (textContent != null) {
            Log.d(TAG, "Loading text content");
            loadTextContent(textContent);
        } else {
            // Get URI from intent
            Uri uri = intent.getData();
            if (uri != null) {
                String mimeType = getContentResolver().getType(uri);
                if (mimeType != null && mimeType.equals("application/epub+zip")) {
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
        }
    }

    @Override
    protected void onDestroy() {
        if (scrollRunnable != null) {
            scrollHandler.removeCallbacks(scrollRunnable);
        }
        isDestroyed = true;
        if (pdfParser != null) {
            pdfParser.close();
        }
        executorService.shutdown();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Force update of theme colors when configuration changes
        if (pdfView != null) {
            pdfView.invalidate();
        }
    }

    private void loadPDF(Uri pdfUri) {
        try {
            // Create default text settings
            currentSettings = new PDFParser.TextSettings();
            currentSettings.fontSize = 54.0f;
            currentSettings.letterSpacing = 0.0f;
            currentSettings.textAlignment = Paint.Align.LEFT;
            currentSettings.lineHeight = 1.2f;
            currentSettings.paragraphSpacing = 1.5f;

            // Initialize PDF parser
            pdfParser = new PDFParser(this, pdfUri);
            totalPages = pdfParser.getPageCount();
            pages = new ArrayList<>(totalPages);
            
            // Initialize the list with nulls
            for (int i = 0; i < totalPages; i++) {
                pages.add(null);
            }

            // Check file size and show message if images will be skipped
            try (InputStream inputStream = getContentResolver().openInputStream(pdfUri)) {
                if (inputStream != null && inputStream.available() > 3 * 1024 * 1024) {
                    Toast.makeText(this, R.string.images_disabled_for_large_pdf_files, Toast.LENGTH_LONG).show();
                }
            }

            // Load first batch of pages
            loadPageBatch(1, PAGE_LOAD_BATCH_SIZE);

        } catch (Exception e) {
            Log.e(TAG, "Error initializing PDF: " + e.getMessage());
            Toast.makeText(this, R.string.failed_to_load_pdf, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadPageBatch(int startPage, int count) {
        if (isLoadingMore || isDestroyed || startPage > totalPages) {
            return;
        }

        isLoadingMore = true;
        executorService.execute(() -> {
            if (isDestroyed) {
                isLoadingMore = false;
                return;
            }

            int endPage = Math.min(startPage + count - 1, totalPages);
            List<PDFParser.ParsedPage> loadedPages = new ArrayList<>();
            
            for (int pageNum = startPage; pageNum <= endPage; pageNum++) {
                if (isDestroyed) break;

                if (pages.get(pageNum - 1) != null) {
                    continue;
                }
                PDFParser.ParsedPage page = pdfParser.parsePage(pageNum, currentSettings);
                if (page != null) {
                    loadedPages.add(page);
                }
            }

            if (!isDestroyed && !loadedPages.isEmpty()) {
                mainHandler.post(() -> {
                    if (!isDestroyed) {
                        for (PDFParser.ParsedPage page : loadedPages) {
                            pages.set(page.pageNumber - 1, page);
                        }
                        pdfView.setPages(pages);
                        currentPage = endPage;
                        isLoadingMore = false;
                        
                        // If this was the first load, start loading next batch
                        if (isFirstLoad) {
                            isFirstLoad = false;
                            loadPageBatch(endPage + 1, PAGE_LOAD_BATCH_SIZE);
                        }
                    }
                });
            } else {
                isLoadingMore = false;
            }
        });
    }

    @Override
    public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
        if (isDestroyed) return;
        
        // Cancel any pending scroll updates
        if (scrollRunnable != null) {
            scrollHandler.removeCallbacks(scrollRunnable);
        }
        
        // Store current scroll position
        lastScrollY = scrollY;
        
        // Create new runnable for delayed scroll handling
        scrollRunnable = new Runnable() {
            @Override
            public void run() {
                // Check if we're near the bottom of the content
                int scrollViewHeight = scrollView.getHeight();
                int scrollViewChildHeight = scrollView.getChildAt(0).getHeight();
                
                // Calculate total scrollable distance
                int totalScrollDistance = scrollViewChildHeight - scrollViewHeight;
                if (totalScrollDistance <= 0) return;
                
                // Calculate how far we've scrolled as a percentage
                float scrollPercentage = (float)lastScrollY / totalScrollDistance;
                
                // If we've scrolled more than 1%, load next batch
                if (scrollPercentage >= SCROLL_THRESHOLD_PERCENT) {
                    loadPageBatch(currentPage + 1, PAGE_LOAD_BATCH_SIZE);
                }
            }
        };
        
        // Post the scroll handling with delay
        scrollHandler.postDelayed(scrollRunnable, SCROLL_DEBOUNCE_DELAY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isDestroyed) return false;
        
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
                    horizontalScrollView.scrollBy((int)-deltaX, 0);
                    scrollView.scrollBy(0, (int)-deltaY);
                    
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
            if (isDestroyed) return false;
            
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f));
            pdfView.setScale(scaleFactor);
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
        if (isDestroyed) return;
        
        Log.d(TAG, "Text settings changed - fontSize: " + settings.fontSize + 
                  ", letterSpacing: " + settings.letterSpacing + 
                  ", alignment: " + settings.textAlignment);
        
        currentSettings = settings;

        // Check if we're displaying text content (no pdfParser)
        if (pdfParser == null && pages != null && !pages.isEmpty()) {
            // For text content, recreate the single page with new settings
            PDFParser.ParsedPage page = pages.get(0);
            PDFParser.ParsedPage newPage = new PDFParser.ParsedPage(
                page.text,           // text
                page.images,         // images
                page.pageNumber,     // pageNumber
                page.pageWidth,      // pageWidth
                page.pageHeight,     // pageHeight
                currentSettings      // new textSettings
            );
            pages.set(0, newPage);
            pdfView.setPages(pages);
        } else {
            // For PDF content, reload all loaded pages with new settings
            for (int i = 0; i < pages.size(); i++) {
                if (pages.get(i) != null) {
                    loadPageBatch(i + 1, PAGE_LOAD_BATCH_SIZE);
                }
            }
        }
    }

    private void loadTextContent(String textContent) {
        try {
            // Create default text settings
            currentSettings = new PDFParser.TextSettings();
            currentSettings.fontSize = 54.0f;
            currentSettings.letterSpacing = 0.0f;
            currentSettings.textAlignment = Paint.Align.LEFT;
            currentSettings.lineHeight = 1.2f;
            currentSettings.paragraphSpacing = 1.5f;

            // Create a single page with the text content
            List<PDFParser.ImageInfo> images = new ArrayList<>(); // No images for text files
            PDFParser.ParsedPage page = new PDFParser.ParsedPage(
                textContent,           // text
                images,               // images
                1,                    // pageNumber
                612.0f,              // pageWidth (Standard US Letter width in points)
                792.0f,              // pageHeight (Standard US Letter height in points)
                currentSettings       // textSettings
            );

            // Initialize pages list with single page
            pages = new ArrayList<>();
            pages.add(page);
            totalPages = 1;
            currentPage = 1;

            // Display the page
            pdfView.setPages(pages);

        } catch (Exception e) {
            Log.e(TAG, "Error initializing text content: " + e.getMessage());
            Toast.makeText(this, R.string.failed_to_load_text_content, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadEPUB(Uri epubUri) {
        try {
            // Create default text settings
            currentSettings = new PDFParser.TextSettings();
            currentSettings.fontSize = 54.0f;
            currentSettings.letterSpacing = 0.0f;
            currentSettings.textAlignment = Paint.Align.LEFT;
            currentSettings.lineHeight = 1.2f;
            currentSettings.paragraphSpacing = 1.5f;

            // Initialize EPUB parser
            epubParser = new EPUBParser(this);
            isEPUB = true;
            
            if (epubParser.parse(epubUri)) {
                List<String> textContent = epubParser.getTextContent();
                List<Bitmap> images = epubParser.getImages();
                
                // Convert EPUB content to PDFView format
                pages = new ArrayList<>();
                for (int i = 0; i < textContent.size(); i++) {
                    String text = textContent.get(i);
                    List<PDFParser.ImageInfo> pageImages = new ArrayList<>();
                    if (i < images.size()) {
                        Bitmap bitmap = images.get(i);
                        // Create ImageInfo with default position and dimensions
                        // We'll place images at the top of the page with their natural size
                        PDFParser.ImageInfo imageInfo = new PDFParser.ImageInfo(
                            bitmap,
                            0, // x position
                            0, // y position
                            bitmap.getWidth(), // width
                            bitmap.getHeight() // height
                        );
                        pageImages.add(imageInfo);
                    }
                    
                    // Create ParsedPage with all required arguments
                    PDFParser.ParsedPage page = new PDFParser.ParsedPage(
                        text,                    // text content
                        pageImages,              // images
                        i + 1,                   // page number
                        595.0f,                  // page width (A4 width in points)
                        842.0f,                  // page height (A4 height in points)
                        currentSettings          // text settings
                    );
                    pages.add(page);
                }
                
                totalPages = pages.size();
                pdfView.setPages(pages);
                
                // Set title and author in toolbar
                String title = epubParser.getTitle();
                String author = epubParser.getAuthor();
                if (title != null && !title.isEmpty()) {
                    getSupportActionBar().setTitle(title);
                    if (author != null && !author.isEmpty()) {
                        getSupportActionBar().setSubtitle(author);
                    }
                }
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
} 