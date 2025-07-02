package com.example.storythere;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;
import com.bumptech.glide.Glide;
import com.example.storythere.data.Book;
import com.example.storythere.data.BookRepository;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.google.android.material.button.MaterialButton;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;
import android.graphics.Typeface;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.app.ProgressDialog;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.view.View;
import android.net.ConnectivityManager;
import android.net.Network;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class BookOptionsActivity extends AppCompatActivity {

    private Button footerButton;
    private Uri contentUri;
    private String fileType;
    private String title;
    private ImageView bookCoverImage;
    private TextView bookAuthorText;
    private TextView bookReadingTimeText;
    private TextView bookEstimatedTimeText;
    private TextView bookAnnotationText;
    private BookRepository bookRepository;
    private Book currentBook;
    private String filePath;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private Uri newCoverUri;
    private MaterialButton btnReadMode;
    private MaterialButton btnListenMode;
    private MaterialButton btnSummarize;
    private MaterialButton stickyBtnReadMode;
    private MaterialButton stickyBtnListenMode;
    private LinearLayout stickyReadingModeButtons;
    private ScrollView scrollView;
    private Toolbar toolbar;
    private boolean isReadModeSelected = true; // Track the selected mode, default to read
    private Animation scaleAnimation;
    private GigaChatService gigaChatService;
    private HuggingFaceService huggingFaceService;
    private ProgressDialog progressDialog;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_options);

        // Setup toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Get data from intent
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            contentUri = intent.getData();
            fileType = intent.getStringExtra("fileType");
            title = intent.getStringExtra("title");
            String annotation = intent.getStringExtra("annotation");

            if (title != null && getSupportActionBar() != null) {
                getSupportActionBar().setTitle(title);
            }
        }

        // Initialize views
        footerButton = findViewById(R.id.footerButton);
        bookCoverImage = findViewById(R.id.bookCoverImage);
        bookAuthorText = findViewById(R.id.bookAuthorText);
        bookReadingTimeText = findViewById(R.id.bookReadingTimeText);
        bookEstimatedTimeText = findViewById(R.id.bookEstimatedTimeText);
        bookAnnotationText = findViewById(R.id.bookAnnotationText);
        btnReadMode = findViewById(R.id.btnReadMode);
        btnListenMode = findViewById(R.id.btnListenMode);
        btnSummarize = findViewById(R.id.btnSummarize);
        stickyBtnReadMode = findViewById(R.id.stickyBtnReadMode);
        stickyBtnListenMode = findViewById(R.id.stickyBtnListenMode);
        stickyReadingModeButtons = findViewById(R.id.stickyReadingModeButtons);
        scrollView = findViewById(R.id.scrollView);
        scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.button_scale);

        // Display annotation from intent if available (after views are initialized)
        if (intent != null) {
            String annotation = intent.getStringExtra("annotation");
            updateAnnotationDisplay(annotation);
        }

        // Get file path from intent
        if (intent != null && intent.getData() != null) {
            filePath = intent.getData().toString();
        }
        // Fetch book info from repository
        bookRepository = new BookRepository(getApplication());
        if (filePath != null) {
            bookRepository.getBookByPath(filePath).observe(this, new Observer<Book>() {
                @Override
                public void onChanged(Book book) {
                    if (book != null) {
                        currentBook = book;
                        bookAuthorText.setText(book.getAuthor() != null ? book.getAuthor() : "Unknown Author");
                        
                        // Display annotation from database if not already set from intent
                        if (book.getAnnotation() != null && !book.getAnnotation().trim().isEmpty() && 
                            (bookAnnotationText.getText() == null || bookAnnotationText.getText().toString().equals(getString(R.string.annotation)))) {
                            updateAnnotationDisplay(book.getAnnotation());
                        } else if (bookAnnotationText.getText() == null || bookAnnotationText.getText().toString().equals(getString(R.string.annotation))) {
                            // No annotation from database either, show default message
                            updateAnnotationDisplay(null);
                        }
                        
                        // Migrate old annotation data to timeOfListen if needed
                        if (book.getTimeOfListen() == null && book.getAnnotation() != null && isCountTimeOfListen(book.getAnnotation())) {
                            // This is old data where annotation contains word count - migrate it
                            book.setTimeOfListen(book.getAnnotation());
                            book.setAnnotation(null); // Clear the old annotation field
                            bookRepository.update(book);
                        }
                        
                        if (book.getPreviewImagePath() != null) {
                            Glide.with(BookOptionsActivity.this)
                                    .load(book.getPreviewImagePath())
                                    .placeholder(R.drawable.ic_book_placeholder)
                                    .into(bookCoverImage);
                        }
                        // Update reading time text with initial stateAdd commentMore actions
                        updateBookReadingTimeText();
                    }
                }
            });
        }

        // Set up initial button state and click listeners
        updateButtonStates(isReadModeSelected);
        setupButtonAnimations();

        // Initialize services
        gigaChatService = new GigaChatService();
        huggingFaceService = new HuggingFaceService();
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.generating_annotation));
        progressDialog.setCancelable(false);

        // Set up summarize button click listener
        btnSummarize.setOnClickListener(v -> {
            v.startAnimation(scaleAnimation);
            performTextSummarization();
        });

        // Set up footer button click listener
        footerButton.setOnClickListener(v -> {
            if (isReadModeSelected) {
                if ("pdf".equals(fileType)) {
                    // Open PDF in PDFViewerActivity
                    Intent pdfIntent = new Intent(this, PDFViewerActivity.class);
                    pdfIntent.setData(contentUri);
                    pdfIntent.putExtra("fileType", fileType);
                    pdfIntent.putExtra("filePath", filePath);
                    pdfIntent.putExtra("title", title);
                    startActivity(pdfIntent);
                } else if ("txt".equals(fileType)) {
                    // Cache parsed .txt content to a file and pass file path to PDFViewerActivity
                    TextParser.ParsedText parsed = TextParser.parseText(this, contentUri);
                    // Use a unique cache file name per book (e.g., by title hash)
                    String safeTitle = (title != null ? title.replaceAll("[^a-zA-Z0-9]", "_") : "book");
                    File cacheFile = new File(getCacheDir(), safeTitle + "_parsed.txt");
                    // Write parsed content to cache file
                    try (FileWriter writer = new FileWriter(cacheFile, false)) {
                        writer.write(parsed.content);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Failed to cache parsed text", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent pdfIntent = new Intent(this, PDFViewerActivity.class);
                    pdfIntent.setData(Uri.fromFile(cacheFile));
                    pdfIntent.putExtra("isRussian", parsed.isRussian);
                    pdfIntent.putExtra("fileType", fileType);
                    pdfIntent.putExtra("filePath", filePath);
                    pdfIntent.putExtra("title", title);
                    startActivity(pdfIntent);
                } else if ("epub".equals(fileType)) {
                    // Open EPUB in PDFViewerActivity
                    Intent epubIntent = new Intent(this, PDFViewerActivity.class);
                    epubIntent.setData(contentUri);
                    epubIntent.putExtra("fileType", fileType);
                    epubIntent.putExtra("filePath", filePath);
                    epubIntent.putExtra("title", title);
                    startActivity(epubIntent);
                } else {
                    // Open reader activity for other file types
                    Intent readerIntent = new Intent(this, ReaderActivity.class);
                    readerIntent.setData(contentUri);
                    readerIntent.putExtra("fileType", fileType);
                    readerIntent.putExtra("filePath", filePath);
                    readerIntent.putExtra("title", title);
                    startActivity(readerIntent);
                }
            } else {
                // Handle listening for both PDF and non-PDF files
                try {
                    Uri textUri;  // Declare here
                    String textContent;
                    boolean isRussian;

                    if ("txt".equals(fileType)) {
                        // Use optimal parsing method from TextParser
                        textUri = contentUri;
                        TextParser.ParsedText parsed = TextParser.parseText(this, contentUri);
                        textContent = parsed.content;
                        isRussian = parsed.isRussian;
                    } else if ("epub".equals(fileType)) {
                        // Cache parsed EPUB content for listening mode
                        String safeTitle = (title != null ? title.replaceAll("[^a-zA-Z0-9]", "_") : "book");
                        File cacheFile = new File(getCacheDir(), safeTitle + "_parsed.txt");
                        Log.d("BookOptionsActivity", "Checking EPUB cache: " + cacheFile.getAbsolutePath());
                        
                        try {
                            if (cacheFile.exists() && cacheFile.length() > 0) {
                                Log.d("BookOptionsActivity", "Found existing cache file, reading from cache");
                                // Read from cache
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(cacheFile)))) {
                                    StringBuilder sb = new StringBuilder();
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        sb.append(line).append("\n");
                                    }
                                    textContent = sb.toString();
                                    isRussian = TextParser.isTextPrimarilyRussian(textContent);
                                    Log.d("BookOptionsActivity", "Successfully read from cache");
                                }
                            } else {
                                Log.d("BookOptionsActivity", "No cache found, parsing EPUB file: " + contentUri.toString());
                                // Parse and cache
                                EPUBParser epubParser = new EPUBParser(this);
                                if (epubParser.parse(contentUri)) {
                                    Log.d("BookOptionsActivity", "EPUB parsing successful");
                                    List<String> epubTextContent = epubParser.getTextContent();
                                    StringBuilder sb = new StringBuilder();
                                    for (String page : epubTextContent) {
                                        sb.append(page).append("\n\n");
                                    }
                                    textContent = sb.toString();
                                    isRussian = TextParser.isTextPrimarilyRussian(textContent);
                                    // Write to cache
                                    try (FileWriter writer = new FileWriter(cacheFile, false)) {
                                        writer.write(textContent);
                                        Log.d("BookOptionsActivity", "Successfully cached EPUB text to: " + cacheFile.getAbsolutePath());
                                    }
                                } else {
                                    throw new Exception("Failed to parse EPUB for listening");
                                }
                            }
                            textUri = Uri.fromFile(cacheFile);
                            Log.d("BookOptionsActivity", "EPUB text URI prepared: " + textUri.toString());
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e("BookOptionsActivity", "Failed to process EPUB text: " + e.getMessage());
                            return;
                        }
                    } else {
                        // For non-txt files, check if parsed text file exists
                        File parsedFile = null;
                        if (currentBook != null && currentBook.getParsedTextPath() != null) {
                            parsedFile = new File(currentBook.getParsedTextPath());
                            if (!parsedFile.exists()) {
                                parsedFile = null;
                            }
                        }
                        if (parsedFile != null) {
                            // Use cached parsed text file
                            textUri = Uri.fromFile(parsedFile);
                            InputStream inputStream = getContentResolver().openInputStream(textUri);
                            StringBuilder textBuilder = new StringBuilder();
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    textBuilder.append(line).append("\n");
                                }
                            }
                            textContent = textBuilder.toString();
                            isRussian = TextParser.isTextPrimarilyRussian(textContent);
                        } else {
                            // Parse and cache text
                            if ("pdf".equals(fileType)) {
                                // Extract text from PDF in parallel
                                StringBuilder allText = new StringBuilder();
                                try {
                                    InputStream baseInputStream = getContentResolver().openInputStream(contentUri);
                                    if (baseInputStream == null) throw new Exception("Failed to open PDF");
                                    PdfReader baseReader = new PdfReader(baseInputStream);
                                    PdfDocument baseDoc = new PdfDocument(baseReader);
                                    int totalPages = baseDoc.getNumberOfPages();
                                    baseDoc.close();
                                    baseReader.close();
                                    baseInputStream.close();
                                    int numThreads = Math.min(4, totalPages);
                                    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
                                    List<Future<String>> futures = new ArrayList<>();
                                    for (int i = 1; i <= totalPages; i++) {
                                        final int pageIndex = i;
                                        futures.add(executor.submit(() -> {
                                            try (InputStream inputStream = getContentResolver().openInputStream(contentUri)) {
                                                PdfReader reader = new PdfReader(inputStream);
                                                PdfDocument doc = new PdfDocument(reader);
                                                String text = PdfTextExtractor.getTextFromPage(
                                                        doc.getPage(pageIndex),
                                                        new SimpleTextExtractionStrategy()
                                                );
                                                doc.close();
                                                reader.close();
                                                return text;
                                            } catch (Exception e) {
                                                return "";
                                            }
                                        }));
                                    }
                                    for (int i = 0; i < totalPages; i++) {
                                        String pageText = futures.get(i).get();
                                        synchronized (allText) {
                                            allText.append(pageText).append("\n");
                                        }
                                    }
                                    executor.shutdown();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Toast.makeText(this, "Error extracting text: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                textContent = allText.toString();
                            } else if ("epub".equals(fileType)) {
                                // Parse EPUB file
                                InputStream inputStream = getContentResolver().openInputStream(contentUri);
                                StringBuilder textBuilder = new StringBuilder();
                                EPUBParser epubParser = new EPUBParser(this);
                                if (epubParser.parse(contentUri)) {
                                    List<String> epubTextContent = epubParser.getTextContent();
                                    for (String text : epubTextContent) {
                                        textBuilder.append(text).append("\n\n");
                                    }
                                } else {
                                    throw new Exception("Failed to parse EPUB file");
                                }
                                textContent = textBuilder.toString();
                            } else {
                                // For other file types
                                InputStream inputStream = getContentResolver().openInputStream(contentUri);
                                StringBuilder textBuilder = new StringBuilder();
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        textBuilder.append(line).append("\n");
                                    }
                                }
                                textContent = textBuilder.toString();
                            }
                            isRussian = TextParser.isTextPrimarilyRussian(textContent);
                            // Save to a txt file and update Book
                            File cacheDir = getCacheDir();
                            String parsedFileName = "parsed_" + (currentBook != null ? currentBook.getId() : System.currentTimeMillis()) + ".txt";
                            File outFile = new File(cacheDir, parsedFileName);
                            try (FileWriter writer = new FileWriter(outFile)) {
                                writer.write(textContent);
                            }
                            textUri = Uri.fromFile(outFile);
                            if (currentBook != null) {
                                currentBook.setParsedTextPath(outFile.getAbsolutePath());
                                bookRepository.update(currentBook);
                            }
                        }
                    }

                    // Open audio reader with the text
                    Intent audioIntent = new Intent(this, AudioReaderActivity.class);
                    audioIntent.setData(textUri);
                    audioIntent.putExtra("fileType", "txt");
                    audioIntent.putExtra("title", title);
                    audioIntent.putExtra("author", currentBook != null ? currentBook.getAuthor() : "Unknown Author");
                    audioIntent.putExtra("is_russian", isRussian);
                    if (currentBook != null && currentBook.getPreviewImagePath() != null) {
                        audioIntent.putExtra("previewImagePath", currentBook.getPreviewImagePath());
                    }
                    startActivity(audioIntent);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Error preparing text for listening", Toast.LENGTH_SHORT).show();
                    // Revert to read mode state on error
                    isReadModeSelected = true;
                    updateButtonStates(true);
                    updateBookReadingTimeText();
                }
            }
        });



        // Register image picker launcher
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            newCoverUri = imageUri;
                            try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
                                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                                bookCoverImage.setImageBitmap(bitmap);
                                if (inputStream != null) {
                                    inputStream.close();
                                }
                            } catch (Exception e) {
                                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                            }
                            // Prompt user to save changesAdd commentMore actions
                            new AlertDialog.Builder(this)
                                    .setTitle("Save Changes")
                                    .setMessage("Do you want to save the new book cover?")
                                    .setPositiveButton("Save", (dialog, which) -> saveBookCover(newCoverUri))
                                    .setNegativeButton("Cancel", (dialog, which) -> newCoverUri = null)
                                    .show();
                        }
                    }
                }
        );

        // Setup book cover click listener
        bookCoverImage.setOnClickListener(v -> showCoverOptionsDialog());

        // Initial update of book info text based on default mode
        updateBookReadingTimeText();
        
        // Setup network callback to monitor internet connectivity
        setupNetworkCallback();
        
        // Setup scroll listener for sticky header
        setupScrollListener();

        // Set click listeners for reading mode buttons
        btnReadMode.setOnClickListener(v -> {
            isReadModeSelected = true;
            updateButtonStates(true);
        });

        btnListenMode.setOnClickListener(v -> {
            isReadModeSelected = false;
            updateButtonStates(false);
        });

        // Set click listeners for sticky reading mode buttons
        stickyBtnReadMode.setOnClickListener(v -> {
            isReadModeSelected = true;
            updateButtonStates(true);
        });

        stickyBtnListenMode.setOnClickListener(v -> {
            isReadModeSelected = false;
            updateButtonStates(false);
        });
    }

    private void showCoverOptionsDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_cover_options, null);
        builder.setView(dialogView);
        android.app.AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);

        MaterialButton btnDialogGenerateCover = dialogView.findViewById(R.id.btnDialogGenerateCover);
        MaterialButton btnDialogPickImage = dialogView.findViewById(R.id.btnDialogPickImage);

        btnDialogGenerateCover.setOnClickListener(v -> {
            dialog.dismiss();
            performBookCoverGeneration();
        });
        btnDialogPickImage.setOnClickListener(v -> {
            dialog.dismiss();
            openImagePicker();
        });

        dialog.show();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        try {
            imagePickerLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to pick images", Toast.LENGTH_SHORT).show();
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
    private void saveBookCover(Uri imageUri) {
        if (currentBook == null || imageUri == null) return;

        // Generate a unique filename using timestamp
        String timestamp = String.valueOf(System.currentTimeMillis());
        File internalFile = new File(getFilesDir(), "cover_" + timestamp + ".jpg");
        
        // Delete old cover image if it exists
        if (currentBook.getPreviewImagePath() != null) {
            File oldCoverFile = new File(currentBook.getPreviewImagePath());
            if (oldCoverFile.exists()) {
                oldCoverFile.delete();
            }
        }

        try (InputStream inputStream = getContentResolver().openInputStream(imageUri);
             FileOutputStream outputStream = new FileOutputStream(internalFile)) {
            if (inputStream != null) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                
                // Update book record with new cover path
                String newPath = internalFile.getAbsolutePath();
                currentBook.setPreviewImagePath(newPath);
                bookRepository.update(currentBook);
                
                // Force reload the image
                Glide.with(this)
                    .load(newPath)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(bookCoverImage);
                
                Toast.makeText(this, "Cover saved", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Error saving cover", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error saving cover: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // Helper method to update button states
    private void updateButtonStates(boolean isReadMode) {
        int blueColor = ContextCompat.getColor(this, R.color.progress_blue);
        int lightBackground = ContextCompat.getColor(this, R.color.background_blue);
        int darkBackground = ContextCompat.getColor(this, R.color.unselected_button_dark);
        int blackText = ContextCompat.getColor(this, R.color.text_black);
        int whiteText = ContextCompat.getColor(this, R.color.white);

        // Check if we're in dark theme
        boolean isDarkTheme = (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (isReadMode) {
            // Update regular buttons
            btnReadMode.setEnabled(false);
            btnListenMode.setEnabled(true);
            btnReadMode.setBackgroundTintList(ColorStateList.valueOf(blueColor));
            btnListenMode.setBackgroundTintList(ColorStateList.valueOf(isDarkTheme ? darkBackground : lightBackground));
            btnReadMode.setTextColor(whiteText);
            btnReadMode.setTypeface(Typeface.create(getResources().getFont(R.font.montserrat), Typeface.BOLD));
            btnReadMode.setIconTintResource(R.color.white);
            btnListenMode.setTextColor(blackText);
            btnListenMode.setTypeface(Typeface.create(getResources().getFont(R.font.montserrat), Typeface.NORMAL));
            btnListenMode.setIconTintResource(R.color.text_black);
            
            // Update sticky buttons
            stickyBtnReadMode.setEnabled(false);
            stickyBtnListenMode.setEnabled(true);
            stickyBtnReadMode.setBackgroundTintList(ColorStateList.valueOf(blueColor));
            stickyBtnListenMode.setBackgroundTintList(ColorStateList.valueOf(isDarkTheme ? darkBackground : lightBackground));
            stickyBtnReadMode.setTextColor(whiteText);
            stickyBtnReadMode.setTypeface(Typeface.create(getResources().getFont(R.font.montserrat), Typeface.BOLD));
            stickyBtnReadMode.setIconTintResource(R.color.white);
            stickyBtnListenMode.setTextColor(blackText);
            stickyBtnListenMode.setTypeface(Typeface.create(getResources().getFont(R.font.montserrat), Typeface.NORMAL));
            stickyBtnListenMode.setIconTintResource(R.color.text_black);
            
            footerButton.setText(R.string.start_reading);
            footerButton.setTypeface(Typeface.create(getResources().getFont(R.font.montserrat), Typeface.BOLD));
        } else {
            // Update regular buttons
            btnReadMode.setEnabled(true);
            btnListenMode.setEnabled(false);
            btnReadMode.setBackgroundTintList(ColorStateList.valueOf(isDarkTheme ? darkBackground : lightBackground));
            btnListenMode.setBackgroundTintList(ColorStateList.valueOf(blueColor));
            btnReadMode.setTextColor(blackText);
            btnReadMode.setTypeface(Typeface.create(getResources().getFont(R.font.montserrat), Typeface.NORMAL));
            btnReadMode.setIconTintResource(R.color.text_black);
            btnListenMode.setTextColor(whiteText);
            btnListenMode.setTypeface(Typeface.create(getResources().getFont(R.font.montserrat), Typeface.BOLD));
            btnListenMode.setIconTintResource(R.color.white);
            
            // Update sticky buttons
            stickyBtnReadMode.setEnabled(true);
            stickyBtnListenMode.setEnabled(false);
            stickyBtnReadMode.setBackgroundTintList(ColorStateList.valueOf(isDarkTheme ? darkBackground : lightBackground));
            stickyBtnListenMode.setBackgroundTintList(ColorStateList.valueOf(blueColor));
            stickyBtnReadMode.setTextColor(blackText);
            stickyBtnReadMode.setTypeface(Typeface.create(getResources().getFont(R.font.montserrat), Typeface.NORMAL));
            stickyBtnReadMode.setIconTintResource(R.color.text_black);
            stickyBtnListenMode.setTextColor(whiteText);
            stickyBtnListenMode.setTypeface(Typeface.create(getResources().getFont(R.font.montserrat), Typeface.BOLD));
            stickyBtnListenMode.setIconTintResource(R.color.white);
            
            footerButton.setText(R.string.start_listening);
            footerButton.setTypeface(Typeface.create(getResources().getFont(R.font.montserrat), Typeface.BOLD));
        }
    }

    // Helper to check if timeOfListen starts with a number (for word/page count)
    private boolean isCountTimeOfListen(String timeOfListen) {
        if (timeOfListen == null) return false;
        try {
            String first = timeOfListen.trim().split(" ")[0];
            Integer.parseInt(first);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Helper method to update the reading time text based on selected mode
    private void updateBookReadingTimeText() {
        if (currentBook != null) {
            int estimatedMinutes = -1;
            if (isReadModeSelected) {
                if ("pdf".equals(fileType)) {
                    String timeOfListen = currentBook.getTimeOfListen();
                    if (isCountTimeOfListen(timeOfListen)) {
                        bookReadingTimeText.setText(timeOfListen);
                        try {
                            int pageCount = Integer.parseInt(timeOfListen.trim().split(" ")[0]);
                            estimatedMinutes = (int) Math.ceil(pageCount * 300.0 / 250.0); // 300 words/page
                        } catch (Exception ignored) {}
                    } else {
                        try {
                            PDFParser pdfParser = new PDFParser(this, contentUri);
                            int totalPages = pdfParser.getPageCount();
                            timeOfListen = totalPages + getString(R.string.pages);
                            bookReadingTimeText.setText(timeOfListen);
                            pdfParser.close();
                            currentBook.setTimeOfListen(timeOfListen);
                            bookRepository.update(currentBook);
                            estimatedMinutes = (int) Math.ceil(totalPages * 300.0 / 250.0);
                        } catch (Exception e) {
                            bookReadingTimeText.setText("");
                        }
                    }
                } else {
                    String timeOfListen = currentBook.getTimeOfListen();
                    if (isCountTimeOfListen(timeOfListen)) {
                        bookReadingTimeText.setText(timeOfListen);
                        try {
                            int wordCount = Integer.parseInt(timeOfListen.trim().split(" ")[0]);
                            estimatedMinutes = (int) Math.ceil(wordCount / 250.0);
                        } catch (Exception ignored) {}
                    } else {
                        try {
                            TextParser.ParsedText parsedText = TextParser.parseText(this, contentUri);
                            int wordCount = parsedText.content.trim().isEmpty() ? 0 : parsedText.content.trim().split("\\s+").length;
                            timeOfListen = wordCount + getString(R.string.words);
                            bookReadingTimeText.setText(timeOfListen);
                            currentBook.setTimeOfListen(timeOfListen);
                            bookRepository.update(currentBook);
                            estimatedMinutes = (int) Math.ceil(wordCount / 250.0);
                        } catch (Exception e) {
                            bookReadingTimeText.setText("");
                        }
                    }
                }
            } else {
                String timeOfListen = currentBook.getTimeOfListen();
                if (isCountTimeOfListen(timeOfListen)) {
                    bookReadingTimeText.setText(timeOfListen);
                    try {
                        int count = Integer.parseInt(timeOfListen.trim().split(" ")[0]);
                        if ("pdf".equals(fileType)) {
                            estimatedMinutes = (int) Math.ceil(count * 300.0 / 250.0);
                        } else {
                            estimatedMinutes = (int) Math.ceil(count / 250.0);
                        }
                    } catch (Exception ignored) {}
                } else {
                    bookReadingTimeText.setText("");
                }
            }
            // Set estimated time text
            if (estimatedMinutes > 0) {
                bookEstimatedTimeText.setText(getString(R.string.estimated_time) + estimatedMinutes + getString(R.string.min));
            } else {
                bookEstimatedTimeText.setText(R.string.estimated_time_uncalc);
            }
        } else {
            bookReadingTimeText.setText("");
            bookEstimatedTimeText.setText(R.string.estimated_time_uncalc);
        }
    }

    private void setupButtonAnimations() {
        btnReadMode.setOnClickListener(v -> {
            if (!isReadModeSelected) {
                btnReadMode.startAnimation(scaleAnimation);
                isReadModeSelected = true;
                updateButtonStates(true);
                updateBookReadingTimeText();
            }
        });

        btnListenMode.setOnClickListener(v -> {
            if (isReadModeSelected) {
                btnListenMode.startAnimation(scaleAnimation);
                isReadModeSelected = false;
                updateButtonStates(false);
                updateBookReadingTimeText();
            }
        });
    }

    private void performTextSummarization() {
        if (contentUri == null) {
            Toast.makeText(this, getString(R.string.no_book_content_available), Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.show();

        // Extract text content based on file type
        new Thread(() -> {
            try {
                String textContent = extractTextContent();
                
                if (textContent == null || textContent.trim().isEmpty()) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, getString(R.string.failed_to_extract_text_content), Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // Call GigaChat API for summarization
                gigaChatService.summarizeText(textContent, new GigaChatService.SummarizationCallback() {
                    @Override
                    public void onSuccess(String summary) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            showSummaryDialog(summary);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(BookOptionsActivity.this, 
                                getString(R.string.failed_to_generate_summary) + ": " + error, 
                                Toast.LENGTH_LONG).show();
                        });
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void performBookCoverGeneration() {
        if (title == null || title.trim().isEmpty()) {
            Toast.makeText(this, "Book title is required to generate cover", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress dialog
        progressDialog.setMessage(getString(R.string.generating_book_cover));
        progressDialog.show();

        // Get annotation from current book if available
        String annotation = null;
        if (currentBook != null) {
            annotation = currentBook.getAnnotation();
        }
        
        // Generate book cover using Hugging Face API
        huggingFaceService.generateBookCover(title, annotation, new HuggingFaceService.BookCoverCallback() {
            @Override
            public void onSuccess(Bitmap coverImage) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    saveGeneratedBookCover(coverImage);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(BookOptionsActivity.this, 
                        getString(R.string.failed_to_generate_book_cover) + ": " + error, 
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void saveGeneratedBookCover(Bitmap coverImage) {
        try {
            // Create a file in the app's private directory
            File coverFile = new File(getFilesDir(), "generated_cover_" + System.currentTimeMillis() + ".jpg");
            
            // Compress and save the bitmap
            FileOutputStream fos = new FileOutputStream(coverFile);
            coverImage.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();

            // Convert file to URI
            Uri coverUri = Uri.fromFile(coverFile);
            
            // Save the cover to the book
            saveBookCover(coverUri);
            
            // Show success message
            Toast.makeText(this, getString(R.string.book_cover_generated), Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            Log.e("BookOptionsActivity", "Error saving generated cover", e);
            Toast.makeText(this, "Error saving cover: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String extractTextContent() {
        try {
            if ("txt".equals(fileType)) {
                // For TXT files, use TextParser
                TextParser.ParsedText parsed = TextParser.parseText(this, contentUri);
                return parsed.content;
            } else if ("epub".equals(fileType)) {
                // For EPUB files, use EPUBParser
                EPUBParser epubParser = new EPUBParser(this);
                if (epubParser.parse(contentUri)) {
                    List<String> epubTextContent = epubParser.getTextContent();
                    StringBuilder sb = new StringBuilder();
                    for (String page : epubTextContent) {
                        sb.append(page).append("\n\n");
                    }
                    return sb.toString();
                }
            } else if ("pdf".equals(fileType)) {
                // For PDF files, extract text
                StringBuilder allText = new StringBuilder();
                try (InputStream inputStream = getContentResolver().openInputStream(contentUri)) {
                    if (inputStream == null) throw new Exception("Failed to open PDF");
                    PdfReader reader = new PdfReader(inputStream);
                    PdfDocument doc = new PdfDocument(reader);
                    int totalPages = doc.getNumberOfPages();
                    
                    for (int i = 1; i <= totalPages; i++) {
                        String text = PdfTextExtractor.getTextFromPage(
                            doc.getPage(i),
                            new SimpleTextExtractionStrategy()
                        );
                        allText.append(text).append("\n");
                    }
                    
                    doc.close();
                    reader.close();
                    return allText.toString();
                }
            }
        } catch (Exception e) {
            Log.e("BookOptionsActivity", "Error extracting text: " + e.getMessage());
        }
        return null;
    }

    private void showSummaryDialog(String summary) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.generated_annotation))
               .setMessage(summary)
               .setPositiveButton("OK", (dialog, which) -> {
                   // Update the annotation display with the generated summary
                   updateAnnotationDisplay(summary);
                   // Save the summary to the database as annotation
                   if (currentBook != null) {
                       currentBook.setAnnotation(summary);
                       bookRepository.update(currentBook);
                       // Hide the button permanently - user can only generate once
                       btnSummarize.setVisibility(View.GONE);
                   }
               })
               .setNegativeButton(getString(R.string.copy), (dialog, which) -> {
                   ClipboardManager clipboard = 
                       (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                   ClipData clip = 
                       ClipData.newPlainText(getString(R.string.generated_annotation), summary);
                   clipboard.setPrimaryClip(clip);
                   Toast.makeText(this, getString(R.string.summary_copied_to_clipboard), Toast.LENGTH_SHORT).show();
               })
               .show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gigaChatService != null) {
            gigaChatService.shutdown();
        }
        
        // Unregister network callback
        if (networkCallback != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            ConnectivityManager connectivityManager = (ConnectivityManager) 
                getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        }
    }

    private void updateAnnotationDisplay(String annotation) {
        boolean hasInternet = NetworkUtils.isInternetAvailable(this);
        
        if (annotation != null && !annotation.trim().isEmpty()) {
            // Annotation exists - show it and hide summarize button
            bookAnnotationText.setText(annotation);
            btnSummarize.setVisibility(View.GONE);
        } else {
            // No annotation - check internet connectivity
            if (hasInternet) {
                // Has internet - show default text and show summarize button
                bookAnnotationText.setText(getString(R.string.no_annotation_found));
                btnSummarize.setVisibility(View.VISIBLE);
            } else {
                // No internet - show no internet message and hide summarize button
                bookAnnotationText.setText(getString(R.string.no_annotation_no_internet));
                btnSummarize.setVisibility(View.GONE);
            }
        }
    }

    private void setupNetworkCallback() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            ConnectivityManager connectivityManager = (ConnectivityManager) 
                getSystemService(Context.CONNECTIVITY_SERVICE);
            
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    runOnUiThread(() -> {
                        // Internet became available, update annotation display
                        if (currentBook != null) {
                            updateAnnotationDisplay(currentBook.getAnnotation());
                        }
                    });
                }
                
                @Override
                public void onLost(Network network) {
                    runOnUiThread(() -> {
                        // Internet became unavailable, update annotation display
                        if (currentBook != null) {
                            updateAnnotationDisplay(currentBook.getAnnotation());
                        }
                    });
                }
            };
            
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        }
    }

    private void setupScrollListener() {
        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            // Get the position of the regular reading mode buttons (not the sticky ones)
            int[] location = new int[2];
            btnReadMode.getLocationInWindow(location);
            int buttonTop = location[1];
            
            // Check if buttons are visible (below toolbar)
            if (buttonTop < toolbar.getHeight()) {
                // Regular buttons are scrolled out of view, show sticky header
                stickyReadingModeButtons.setVisibility(View.VISIBLE);
            } else {
                // Regular buttons are visible, hide sticky header
                stickyReadingModeButtons.setVisibility(View.GONE);
            }
        });
        
        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            // Get the position of the regular reading mode buttons (not the sticky ones)
            int[] location = new int[2];
            btnReadMode.getLocationInWindow(location);
            int buttonTop = location[1];
            
            // Check if buttons are visible (below toolbar)
            if (buttonTop < toolbar.getHeight()) {
                // Regular buttons are scrolled out of view, show sticky header
                stickyReadingModeButtons.setVisibility(View.VISIBLE);
            } else {
                // Regular buttons are visible, hide sticky header
                stickyReadingModeButtons.setVisibility(View.GONE);
            }
        });
    }
} 