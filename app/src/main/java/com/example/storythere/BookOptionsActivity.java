package com.example.storythere;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

public class BookOptionsActivity extends AppCompatActivity {

    private Button footerButton;
    private Uri contentUri;
    private String fileType;
    private String title;
    private ImageView bookCoverImage;
    private TextView bookAuthorText;
    private TextView bookReadingTimeText;
    private TextView bookEstimatedTimeText;
    private BookRepository bookRepository;
    private Book currentBook;
    private String filePath;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private Uri newCoverUri;
    private MaterialButton btnReadMode;
    private MaterialButton btnListenMode;
    private boolean isReadModeSelected = true; // Track the selected mode, default to read
    private Animation scaleAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_options);

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
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
        btnReadMode = findViewById(R.id.btnReadMode);
        btnListenMode = findViewById(R.id.btnListenMode);
        scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.button_scale);

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


        // Set up footer button click listener
        footerButton.setOnClickListener(v -> {
            if (isReadModeSelected) {
                if ("pdf".equals(fileType)) {
                    // Open PDF viewer activity
                    Intent pdfIntent = new Intent(this, PDFViewerActivity.class);
                    pdfIntent.setData(contentUri);
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
                    pdfIntent.putExtra("title", title);
                    startActivity(pdfIntent);
                } else {
                    // Open reader activity for other file types
                    Intent readerIntent = new Intent(this, ReaderActivity.class);
                    readerIntent.setData(contentUri);
                    readerIntent.putExtra("fileType", fileType);
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

        // Setup book cover click listenerAdd commentMore actions
        bookCoverImage.setOnClickListener(v -> openImagePicker());

        // Initial update of book info text based on default mode
        updateBookReadingTimeText();
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
            footerButton.setText(R.string.start_reading);
            footerButton.setTypeface(Typeface.create(getResources().getFont(R.font.montserrat), Typeface.BOLD));
        } else {
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
            footerButton.setText(R.string.start_listening);
            footerButton.setTypeface(Typeface.create(getResources().getFont(R.font.montserrat), Typeface.BOLD));
        }
    }

    // Helper to check if annotation starts with a number (for word/page count)
    private boolean isCountAnnotation(String annotation) {
        if (annotation == null) return false;
        try {
            String first = annotation.trim().split(" ")[0];
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
                    String annotation = currentBook.getAnnotation();
                    if (isCountAnnotation(annotation)) {
                        bookReadingTimeText.setText(annotation);
                        try {
                            int pageCount = Integer.parseInt(annotation.trim().split(" ")[0]);
                            estimatedMinutes = (int) Math.ceil(pageCount * 300.0 / 250.0); // 300 words/page
                        } catch (Exception ignored) {}
                    } else {
                        try {
                            PDFParser pdfParser = new PDFParser(this, contentUri);
                            int totalPages = pdfParser.getPageCount();
                            annotation = totalPages + getString(R.string.pages);
                            bookReadingTimeText.setText(annotation);
                            pdfParser.close();
                            currentBook.setAnnotation(annotation);
                            bookRepository.update(currentBook);
                            estimatedMinutes = (int) Math.ceil(totalPages * 300.0 / 250.0);
                        } catch (Exception e) {
                            bookReadingTimeText.setText("");
                        }
                    }
                } else {
                    String annotation = currentBook.getAnnotation();
                    if (isCountAnnotation(annotation)) {
                        bookReadingTimeText.setText(annotation);
                        try {
                            int wordCount = Integer.parseInt(annotation.trim().split(" ")[0]);
                            estimatedMinutes = (int) Math.ceil(wordCount / 250.0);
                        } catch (Exception ignored) {}
                    } else {
                        try {
                            TextParser.ParsedText parsedText = TextParser.parseText(this, contentUri);
                            int wordCount = parsedText.content.trim().isEmpty() ? 0 : parsedText.content.trim().split("\\s+").length;
                            annotation = wordCount + getString(R.string.words);
                            bookReadingTimeText.setText(annotation);
                            currentBook.setAnnotation(annotation);
                            bookRepository.update(currentBook);
                            estimatedMinutes = (int) Math.ceil(wordCount / 250.0);
                        } catch (Exception e) {
                            bookReadingTimeText.setText("");
                        }
                    }
                }
            } else {
                String annotation = currentBook.getAnnotation();
                if (isCountAnnotation(annotation)) {
                    bookReadingTimeText.setText(annotation);
                    try {
                        int count = Integer.parseInt(annotation.trim().split(" ")[0]);
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
} 