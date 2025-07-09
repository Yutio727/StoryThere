package com.example.storythere.viewing;

import android.annotation.SuppressLint;
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

import com.example.storythere.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ReaderActivity extends AppCompatActivity {
    private static final String TAG = "ReaderActivity";
    private WebView webView;
    private ProgressBar progressBar;
    private Uri contentUri;

    @SuppressLint("SetJavaScriptEnabled")
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
                String filePath = intent.getStringExtra("filePath");
                
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
                loadContent(contentUri, fileType, filePath);
            } else {
                Log.e(TAG, "No file URI provided in intent");
                Toast.makeText(this, R.string.no_file_selected, Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.error_initializing_reader) + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void loadContent(Uri uri, String fileType, String filePath) {
        progressBar.setVisibility(View.VISIBLE);
        
        try {
            Log.d(TAG, "Attempting to load content for type: " + fileType);
            
            switch (fileType.toLowerCase()) {
                case "pdf":
                    Log.d(TAG, "Opening PDF file");
                    try {
                        Intent intent = new Intent(this, ViewerActivity.class);
                        intent.setData(uri);
                        intent.putExtra("filePath", filePath);
                        startActivity(intent);
                        finish(); // Close the reader activity since we're using ViewerActivity
                    } catch (Exception e) {
                        Log.e(TAG, "Error opening PDF: " + e.getMessage(), e);
                        Toast.makeText(this, R.string.error_opening_pdf, Toast.LENGTH_LONG).show();
                        finish();
                    }
                    break;
                    
                case "txt":
                    Log.d(TAG, "Redirecting TXT file to ViewerActivity");
                    Intent pdfIntent = new Intent(this, ViewerActivity.class);
                    pdfIntent.setData(uri);
                    pdfIntent.putExtra("fileType", fileType);
                    pdfIntent.putExtra("title", getIntent().getStringExtra("title"));
                    pdfIntent.putExtra("filePath", filePath);
                    startActivity(pdfIntent);
                    finish();
                    break;
                    
                case "html":
                case "md":
                    Log.d(TAG, "Reading HTML/MD file");
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
                    Log.d(TAG, "Opening EPUB file");
                    try {
                        // Launch ViewerActivity with the EPUB URI
                        Intent intent = new Intent(this, ViewerActivity.class);
                        intent.setData(uri);
                        intent.putExtra("filePath", filePath);
                        startActivity(intent);
                        finish(); // Close the reader activity
                    } catch (Exception e) {
                        Log.e(TAG, "Error opening EPUB file: " + e.getMessage(), e);
                        Toast.makeText(this, R.string.error_reading_epub_file, Toast.LENGTH_LONG).show();
                        finish();
                    }
                    break;
                    
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
                                                    getString(R.string.p_we_re_working_on_adding_native_support_for_epub_and_fb2_files_p) +
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
                                                  getString(R.string.p_this_file_type_is_not_currently_supported_p) +
                                                  "</body></html>";
                    webView.loadData(unsupportedMessageHtml, "text/html", "UTF-8");
                    progressBar.setVisibility(View.GONE);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading content: " + e.getMessage(), e);
            webView.setVisibility(View.VISIBLE);
            
            String errorMessageHtml = "<html><body style='padding: 20px;'>Error loading file: " + e.getMessage() + 
                                    getString(R.string.br_br_please_try_again_or_contact_support_body_html);
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