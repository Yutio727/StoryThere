package com.example.storythere;

import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.util.List;

public class PDFViewerActivity extends AppCompatActivity implements TextSettingsDialog.TextSettingsListener {
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

        // Initialize scale detector
        scaleDetector = new ScaleGestureDetector(this, new ScaleListener());

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

    private void loadPDF(Uri pdfUri) {
        // Create default text settings
        currentSettings = new PDFParser.TextSettings();
        currentSettings.fontSize = 54.0f;
        currentSettings.letterSpacing = 0.0f;
        currentSettings.textAlignment = Paint.Align.LEFT;
        currentSettings.lineHeight = 1.2f;
        currentSettings.paragraphSpacing = 1.5f;

        Log.d(TAG, "Loading PDF with settings - fontSize: " + currentSettings.fontSize + 
                  ", letterSpacing: " + currentSettings.letterSpacing + 
                  ", alignment: " + currentSettings.textAlignment);

        // Parse PDF with settings
        pages = PDFParser.parsePDF(this, pdfUri, currentSettings);
        if (pages != null && !pages.isEmpty()) {
            pdfView.setPages(pages);
        } else {
            Log.e(TAG, "No pages parsed from PDF");
            Toast.makeText(this, "Failed to load PDF", Toast.LENGTH_SHORT).show();
            finish();
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
        // Reparse PDF with new settings
        Uri pdfUri = getIntent().getData();
        if (pdfUri != null) {
            pages = PDFParser.parsePDF(this, pdfUri, settings);
            if (pages != null && !pages.isEmpty()) {
                pdfView.setPages(pages);
                // Reset scroll position
                scrollView.scrollTo(0, 0);
                horizontalScrollView.scrollTo(0, 0);
            }
        }
    }
} 