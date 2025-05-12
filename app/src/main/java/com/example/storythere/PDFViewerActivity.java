package com.example.storythere;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.util.List;

public class PDFViewerActivity extends AppCompatActivity {
    private ImageView pdfImageView;
    private TextView pdfTextView;
    private TextView pageNumberText;
    private ImageButton prevPageButton;
    private ImageButton nextPageButton;
    private ZoomLayout zoomLayout;
    private ScrollView scrollView;
    private List<PDFParser.ParsedPage> parsedPages;
    private int currentPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Initialize views
        pdfImageView = findViewById(R.id.pdfImageView);
        pdfTextView = findViewById(R.id.pdfTextView);
        pageNumberText = findViewById(R.id.pageNumberText);
        prevPageButton = findViewById(R.id.prevPageButton);
        nextPageButton = findViewById(R.id.nextPageButton);
        zoomLayout = findViewById(R.id.zoomLayout);
        scrollView = findViewById(R.id.scrollView);

        // Setup page navigation buttons
        prevPageButton.setOnClickListener(v -> goToPreviousPage());
        nextPageButton.setOnClickListener(v -> goToNextPage());

        // Get the PDF URI from the intent
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            Uri pdfUri = intent.getData();
            loadPDF(pdfUri);
        } else {
            Toast.makeText(this, "No PDF file provided", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadPDF(Uri pdfUri) {
        try {
            // Parse the PDF
            parsedPages = PDFParser.parsePDF(this, pdfUri);
            
            if (parsedPages.isEmpty()) {
                Toast.makeText(this, "Error parsing PDF", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // Display first page
            displayPage(0);
            updatePageControls();
            
        } catch (Exception e) {
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void displayPage(int pageNumber) {
        try {
            if (pageNumber >= 0 && pageNumber < parsedPages.size()) {
                PDFParser.ParsedPage page = parsedPages.get(pageNumber);
                
                // Display text
                String text = page.text.trim();
                if (text.isEmpty()) {
                    pdfTextView.setVisibility(View.GONE);
                } else {
                    pdfTextView.setVisibility(View.VISIBLE);
                    pdfTextView.setText(text);
                }
                
                // Display images if any
                if (!page.images.isEmpty()) {
                    pdfImageView.setVisibility(View.VISIBLE);
                    // For now, just display the first image
                    Bitmap image = page.images.get(0);
                    pdfImageView.setImageBitmap(image);
                } else {
                    pdfImageView.setVisibility(View.GONE);
                }
                
                // Reset scroll position
                scrollView.scrollTo(0, 0);
                
                // Update page number
                pageNumberText.setText(String.format("Page %d of %d", pageNumber + 1, parsedPages.size()));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error displaying page: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void updatePageControls() {
        prevPageButton.setEnabled(currentPage > 0);
        nextPageButton.setEnabled(currentPage < parsedPages.size() - 1);
    }

    private void goToPreviousPage() {
        if (currentPage > 0) {
            currentPage--;
            displayPage(currentPage);
            updatePageControls();
        }
    }

    private void goToNextPage() {
        if (currentPage < parsedPages.size() - 1) {
            currentPage++;
            displayPage(currentPage);
            updatePageControls();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 