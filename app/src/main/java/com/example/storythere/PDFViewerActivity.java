package com.example.storythere;

import android.content.Intent;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PDFViewerActivity extends AppCompatActivity implements TextSettingsDialog.TextSettingsListener, ScrollView.OnScrollChangeListener {
    private static final String TAG = "PDFViewerActivity";
    private static final int PAGE_LOAD_BATCH_SIZE = 2; // Load 2 pages at a time
    private static final float SCROLL_THRESHOLD_PERCENT = 0.05f; // 1% of total scroll distance
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
        executorService = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());

        // Get PDF URI from intent
        Uri pdfUri = getIntent().getData();
        if (pdfUri != null) {
            Log.d(TAG, "Loading PDF from URI: " + pdfUri);
            loadPDF(pdfUri);
        } else {
            Log.e(TAG, "No PDF URI provided");
            Toast.makeText(this, "No PDF file provided", Toast.LENGTH_SHORT).show();
            finish();
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

            // Load first batch of pages
            loadPageBatch(1, PAGE_LOAD_BATCH_SIZE);

        } catch (Exception e) {
            Log.e(TAG, "Error initializing PDF: " + e.getMessage());
            Toast.makeText(this, "Failed to load PDF", Toast.LENGTH_SHORT).show();
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
        // Reload all loaded pages with new settings
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i) != null) {
                loadPageBatch(i + 1, PAGE_LOAD_BATCH_SIZE);
            }
        }
    }
} 