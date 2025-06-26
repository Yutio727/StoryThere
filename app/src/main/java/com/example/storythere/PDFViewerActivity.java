package com.example.storythere;

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
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
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

public class PDFViewerActivity extends AppCompatActivity implements TextSettingsDialog.TextSettingsListener {
    private static final String TAG = "PDFViewerActivity";
    private static final int INITIAL_LOAD_RANGE = 5; // Load 5 pages before and after current
    private static final int LOAD_RANGE = 10; // Load 3 pages in scroll direction
    
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
        pdfRecyclerView = findViewById(R.id.pdfRecyclerView);
        pdfRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        pdfRecyclerView.setItemViewCacheSize(10);

        // Initialize scale detector
        scaleDetector = new ScaleGestureDetector(this, new ScaleListener());

        // Initialize thread pool and handler
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        mainHandler = new Handler(Looper.getMainLooper());

        // Get content from intent
        Intent intent = getIntent();
        Uri uri = intent.getData();
        String fileType = intent.getStringExtra("fileType");
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
                Toast.makeText(this, "Failed to load cached text", Toast.LENGTH_SHORT).show();
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
    }

    @Override
    protected void onDestroy() {
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
                int width = getResources().getDisplayMetrics().widthPixels - 64; // 32dp padding each side
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
                    pdfRecyclerView.setAdapter(pdfPageAdapter);
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
                        pdfRecyclerView.setAdapter(pdfPageAdapter);
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
            pdfRecyclerView.setAdapter(pdfPageAdapter);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing PDF: " + e.getMessage());
            Toast.makeText(this, R.string.failed_to_load_pdf, Toast.LENGTH_SHORT).show();
            finish();
        }
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
} 