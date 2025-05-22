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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        executorService = Executors.newSingleThreadExecutor();
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
        super.onDestroy();
        if (pdfParser != null) {
            pdfParser.close();
        }
        executorService.shutdown();
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

            // Load first page immediately
            loadPage(1);
            
            // Start loading next pages in background
            loadNextPages();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing PDF: " + e.getMessage());
            Toast.makeText(this, "Failed to load PDF", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadPage(int pageNumber) {
        if (pageNumber < 1 || pageNumber > totalPages) {
            return;
        }

        executorService.execute(() -> {
            PDFParser.ParsedPage page = pdfParser.parsePage(pageNumber, currentSettings);
            if (page != null) {
                mainHandler.post(() -> {
                    pages.set(pageNumber - 1, page);
                    pdfView.setPages(pages);
                });
            }
        });
    }

    private void loadNextPages() {
        if (isLoadingMore) {
            return;
        }

        isLoadingMore = true;
        executorService.execute(() -> {
            int nextPage = currentPage + 1;
            if (nextPage <= totalPages) {
                PDFParser.ParsedPage page = pdfParser.parsePage(nextPage, currentSettings);
                if (page != null) {
                    mainHandler.post(() -> {
                        pages.set(nextPage - 1, page);
                        pdfView.setPages(pages);
                        currentPage = nextPage;
                        isLoadingMore = false;
                        // Continue loading next pages
                        loadNextPages();
                    });
                }
            } else {
                isLoadingMore = false;
            }
        });
    }

    @Override
    public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
        // Check if we're near the bottom of the content
        int scrollViewHeight = scrollView.getHeight();
        int scrollViewChildHeight = scrollView.getChildAt(0).getHeight();
        
        if (scrollViewChildHeight - (scrollY + scrollViewHeight) < scrollViewHeight * 0.5) {
            // We're near the bottom, load more pages
            loadNextPages();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
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
        Log.d(TAG, "Text settings changed - fontSize: " + settings.fontSize + 
                  ", letterSpacing: " + settings.letterSpacing + 
                  ", alignment: " + settings.textAlignment);
        
        currentSettings = settings;
        // Reload all loaded pages with new settings
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i) != null) {
                loadPage(i + 1);
            }
        }
    }
} 