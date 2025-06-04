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
import com.example.storythere.TextParser;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import com.google.android.material.button.MaterialButton;
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
                        // Update reading time text with initial state
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
                    Uri textUri;
                    String textContent;
                    boolean isRussian;
                    
                    if ("pdf".equals(fileType)) {
                        // Extract text from PDF
                        PDFParser pdfParser = new PDFParser(this, contentUri);
                        StringBuilder allText = new StringBuilder();
                        int totalPages = pdfParser.getPageCount();
                        
                        // Extract text from all pages
                        for (int i = 1; i <= totalPages; i++) {
                            PDFParser.ParsedPage page = pdfParser.parsePage(i, new PDFParser.TextSettings());
                            if (page != null && page.text != null) {
                                allText.append(page.text).append("\n");
                            }
                        }
                        pdfParser.close();
                        
                        textContent = allText.toString();
                        isRussian = TextParser.isTextPrimarilyRussian(textContent);
                        
                        // Save to temporary text file
                        File tempFile = new File(getCacheDir(), "temp_pdf_text.txt");
                        try (FileWriter writer = new FileWriter(tempFile)) {
                            writer.write(textContent);
                        }
                        textUri = Uri.fromFile(tempFile);
                    } else {
                        // For non-PDF files, use the original file
                        textUri = contentUri;
                        TextParser.ParsedText parsedText = TextParser.parseText(this, contentUri);
                        textContent = parsedText.content;
                        isRussian = parsedText.isRussian;
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
                        try {
                            InputStream inputStream = getContentResolver().openInputStream(imageUri);
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            bookCoverImage.setImageBitmap(bitmap);
                            if (inputStream != null) {
                                inputStream.close();
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                        }
                        // Prompt user to save changes
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

        // Save the image to internal storage
        File internalFile = new File(getFilesDir(), currentBook.getFilePath().hashCode() + "_cover.jpg");
        try (InputStream inputStream = getContentResolver().openInputStream(imageUri);
             FileOutputStream outputStream = new FileOutputStream(internalFile)) {
            if (inputStream != null) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                // Update book record with new cover path
                currentBook.setPreviewImagePath(internalFile.getAbsolutePath());
                bookRepository.update(currentBook);
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

    // Helper method to update the reading time text based on selected mode
    private void updateBookReadingTimeText() {
        if (currentBook != null) {
            if (isReadModeSelected) {
                if ("pdf".equals(fileType)) {
                    // For PDFs, show total pages
                    try {
                        PDFParser pdfParser = new PDFParser(this, contentUri);
                        int totalPages = pdfParser.getPageCount();
                        bookReadingTimeText.setText(totalPages + " pages");
                        pdfParser.close();
                    } catch (Exception e) {
                        bookReadingTimeText.setText("");
                    }
                } else {
                    // For non-PDF files, show word count
                    try {
                        TextParser.ParsedText parsedText = TextParser.parseText(this, contentUri);
                        int wordCount = parsedText.content.trim().isEmpty() ? 0 : parsedText.content.trim().split("\\s+").length;
                        bookReadingTimeText.setText(wordCount + " words");
                    } catch (Exception e) {
                        bookReadingTimeText.setText("");
                    }
                }
            } else {
                // Calculate total listening time on the fly
                if (contentUri != null) {
                    if ("pdf".equals(fileType)) {
                        try {
                            // Extract text from PDF
                            PDFParser pdfParser = new PDFParser(this, contentUri);
                            StringBuilder allText = new StringBuilder();
                            int totalPages = pdfParser.getPageCount();

                            // Extract text from all pages
                            for (int i = 1; i <= totalPages; i++) {
                                PDFParser.ParsedPage page = pdfParser.parsePage(i, new PDFParser.TextSettings());
                                if (page != null && page.text != null) {
                                    allText.append(page.text).append("\n");
                                }
                            }
                            pdfParser.close();

                            // Calculate duration based on word count
                            String text = allText.toString().trim();
                            int totalDuration = text.isEmpty() ? 0 : text.split("\\s+").length;
                            String formattedTime = formatTime(totalDuration);
                            bookReadingTimeText.setText(formattedTime); // Removed " words" text
                        } catch (Exception e) {
                            Toast.makeText(this, "Error extracting text from PDF for listening time", Toast.LENGTH_SHORT).show();
                            bookReadingTimeText.setText("");
                        }
                    } else {
                        TextParser.ParsedText parsedText = TextParser.parseText(this, contentUri);
                        int totalDuration = parsedText.content.trim().isEmpty() ? 0 : parsedText.content.trim().split("\\s+").length;
                        String formattedTime = formatTime(totalDuration);
                        bookReadingTimeText.setText(formattedTime); // Removed " words" text
                    }
                } else {
                    bookReadingTimeText.setText("");
                }
            }
        } else {
            bookReadingTimeText.setText("");
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