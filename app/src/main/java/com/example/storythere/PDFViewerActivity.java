package com.example.storythere;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.MenuItem;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PDFViewerActivity extends AppCompatActivity {
    private ImageView pdfImageView;
    private ZoomLayout zoomLayout;
    private ScrollView scrollView;
    private HorizontalScrollView horizontalScrollView;
    private PdfRenderer renderer;
    private ParcelFileDescriptor fileDescriptor;
    private final int currentPage = 0;

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

        pdfImageView = findViewById(R.id.pdfImageView);
        zoomLayout = findViewById(R.id.zoomLayout);
        scrollView = findViewById(R.id.scrollView);
        horizontalScrollView = findViewById(R.id.horizontalScrollView);

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
            // Create a temporary file to store the PDF
            File tempFile = File.createTempFile("temp_pdf", ".pdf", getCacheDir());
            InputStream inputStream = getContentResolver().openInputStream(pdfUri);
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            
            byte[] buffer = new byte[1024];
            int read;
            while (true) {
                assert inputStream != null;
                if ((read = inputStream.read(buffer)) == -1) break;
                outputStream.write(buffer, 0, read);
            }
            
            inputStream.close();
            outputStream.close();

            // Open the PDF file
            fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fileDescriptor);
            
            // Wait for layout to be measured before displaying the page
            pdfImageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    pdfImageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    displayPage(currentPage);
                }
            });
            
        } catch (Exception e) {
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void displayPage(int pageNumber) {
        try {
            if (pageNumber >= 0 && pageNumber < renderer.getPageCount()) {
                // Open the page
                PdfRenderer.Page page = renderer.openPage(pageNumber);
                
                // Calculate scale to fit screen
                float scale = Math.min(
                    pdfImageView.getWidth() / page.getWidth(),
                    pdfImageView.getHeight() / page.getHeight()
                );
                
                // Ensure scale is at least 1.0f
                scale = Math.max(scale, 1.0f);

                // Create a bitmap for the page
                Bitmap bitmap = Bitmap.createBitmap(
                    (int)(page.getWidth() * scale),
                    (int)(page.getHeight() * scale),
                    Bitmap.Config.ARGB_8888
                );

                // Render the page to the bitmap
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                // Close the page
                page.close();
                
                // Display the bitmap
                pdfImageView.setImageBitmap(bitmap);
                
                // Set the image view size to match the bitmap
                pdfImageView.getLayoutParams().width = bitmap.getWidth();
                pdfImageView.getLayoutParams().height = bitmap.getHeight();
                pdfImageView.requestLayout();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error displaying page: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (renderer != null) {
                renderer.close();
            }
            if (fileDescriptor != null) {
                fileDescriptor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 