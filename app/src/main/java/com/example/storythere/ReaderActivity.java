package com.example.storythere;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ReaderActivity extends AppCompatActivity {
    private static final String TAG = "ReaderActivity";
    private WebView webView;
    private ProgressBar progressBar;
    private Uri contentUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        try {
            // Setup toolbar
            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
            }

            webView = findViewById(R.id.webView);
            progressBar = findViewById(R.id.progressBar);

            // Get the book URI from the intent
            Intent intent = getIntent();
            if (intent != null && intent.getData() != null) {
                contentUri = intent.getData();
                String fileType = intent.getStringExtra("fileType");
                String title = intent.getStringExtra("title");
                
                Log.d(TAG, "Opening file: " + contentUri.toString() + " of type: " + fileType);
                
                if (title != null && getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(title);
                }

                // Configure WebView
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setDomStorageEnabled(true);
                webView.getSettings().setSupportZoom(true);
                webView.getSettings().setBuiltInZoomControls(true);
                webView.getSettings().setDisplayZoomControls(false);
                webView.getSettings().setAllowFileAccess(true);
                webView.getSettings().setAllowContentAccess(true);
                
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        progressBar.setVisibility(View.GONE);
                    }
                });

                // Load content based on file type
                loadContent(contentUri, fileType);
            } else {
                Log.e(TAG, "No file URI provided in intent");
                Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing reader: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void loadContent(Uri uri, String fileType) {
        progressBar.setVisibility(View.VISIBLE);
        
        try {
            Log.d(TAG, "Attempting to load content for type: " + fileType);
            
            switch (fileType.toLowerCase()) {
                case "pdf":
                    Log.d(TAG, "Opening PDF file");
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, "application/pdf");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                        finish(); // Close the reader activity since we're using external viewer
                    } catch (Exception e) {
                        Log.e(TAG, "Error opening PDF: " + e.getMessage(), e);
                        Toast.makeText(this, "Error opening PDF. Please install a PDF viewer app.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                    break;
                    
                case "txt":
                case "html":
                case "md":
                    Log.d(TAG, "Reading text file");
                    webView.setVisibility(View.VISIBLE);
                    
                    try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                        if (inputStream != null) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                            StringBuilder stringBuilder = new StringBuilder();
                            String line;
                            
                            while ((line = reader.readLine()) != null) {
                                stringBuilder.append(line).append("<br>");
                            }
                            
                            String content = stringBuilder.toString();
                            String textViewerHtml = "<html><body style='padding: 20px; font-size: 16px; line-height: 1.6;'>" + content + "</body></html>";
                            webView.loadData(textViewerHtml, "text/html", "UTF-8");
                        } else {
                            throw new Exception("Could not open file stream");
                        }
                    }
                    break;
                    
                case "epub":
                case "fb2":
                    Log.d(TAG, "Opening eBook file");
                    webView.setVisibility(View.VISIBLE);
                    
                    // For EPUB and FB2, we'll convert to HTML and display in WebView
                    try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                        if (inputStream != null) {
                            // TODO: Implement EPUB/FB2 to HTML conversion
                            // For now, show a message
                            String ebookMessageHtml = "<html><body style='padding: 20px; text-align: center;'>" +
                                                    "<h2>EPUB/FB2 Support Coming Soon</h2>" +
                                                    "<p>We're working on adding native support for EPUB and FB2 files.</p>" +
                                                    "</body></html>";
                            webView.loadData(ebookMessageHtml, "text/html", "UTF-8");
                        } else {
                            throw new Exception("Could not open file stream");
                        }
                    }
                    break;
                    
                default:
                    Log.d(TAG, "Unsupported file type");
                    webView.setVisibility(View.VISIBLE);
                    
                    String unsupportedMessageHtml = "<html><body style='padding: 20px; text-align: center;'>" +
                                                  "<h2>Unsupported File Type</h2>" +
                                                  "<p>This file type is not currently supported.</p>" +
                                                  "</body></html>";
                    webView.loadData(unsupportedMessageHtml, "text/html", "UTF-8");
                    progressBar.setVisibility(View.GONE);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading content: " + e.getMessage(), e);
            webView.setVisibility(View.VISIBLE);
            
            String errorMessageHtml = "<html><body style='padding: 20px;'>Error loading file: " + e.getMessage() + 
                                    "<br><br>Please try again or contact support.</body></html>";
            webView.loadData(errorMessageHtml, "text/html", "UTF-8");
            progressBar.setVisibility(View.GONE);
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
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
} 