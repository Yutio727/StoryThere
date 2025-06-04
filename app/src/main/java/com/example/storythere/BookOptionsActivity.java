package com.example.storythere;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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

public class BookOptionsActivity extends AppCompatActivity {
    private RadioGroup readingModeGroup;
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
        readingModeGroup = findViewById(R.id.readingModeGroup);
        footerButton = findViewById(R.id.footerButton);
        bookCoverImage = findViewById(R.id.bookCoverImage);
        bookAuthorText = findViewById(R.id.bookAuthorText);
        bookReadingTimeText = findViewById(R.id.bookReadingTimeText);

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
                        // Display time of reading/listening if available (assume annotation for now)
                        if (book.getAnnotation() != null && !book.getAnnotation().isEmpty()) {
                            bookReadingTimeText.setText(book.getAnnotation());
                        } else {
                            bookReadingTimeText.setText("");
                        }
                    }
                }
            });
        }

        // Set up radio group listener
        readingModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selectedButton = findViewById(checkedId);
            if (selectedButton != null) {
                if (selectedButton.getId() == R.id.readButton) {
                    footerButton.setText("Start Reading");
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
                        bookReadingTimeText.setText("10 pages");
                    }
                } else {
                    footerButton.setText("Start Listening");
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
                                bookReadingTimeText.setText(formattedTime);
                            } catch (Exception e) {
                                Toast.makeText(this, "Error extracting text from PDF", Toast.LENGTH_SHORT).show();
                                readingModeGroup.check(R.id.readButton);
                            }
                        } else {
                            TextParser.ParsedText parsedText = TextParser.parseText(this, contentUri);
                            int totalDuration = parsedText.content.trim().isEmpty() ? 0 : parsedText.content.trim().split("\\s+").length;
                            String formattedTime = formatTime(totalDuration);
                            bookReadingTimeText.setText(formattedTime);
                        }
                    } else {
                        bookReadingTimeText.setText("");
                    }
                }
            }
        });

        // Set up footer button click listener
        footerButton.setOnClickListener(v -> {
            int selectedId = readingModeGroup.getCheckedRadioButtonId();
            if (selectedId == R.id.readButton) {
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
                            try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
                                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                                if (currentBook != null) {
                                    // Save bitmap to app private storage as covers/{bookId}.jpg
                                    File coversDir = new File(getFilesDir(), "covers");
                                    if (!coversDir.exists()) coversDir.mkdirs();
                                    File coverFile = new File(coversDir, currentBook.getId() + ".jpg");
                                    try (FileOutputStream out = new FileOutputStream(coverFile)) {
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                                    }
                                    // Update previewImagePath to internal file path
                                    currentBook.setPreviewImagePath(coverFile.getAbsolutePath());
                                    bookRepository.update(currentBook);
                                    // Show the new cover
                                    bookCoverImage.setImageBitmap(bitmap);
                                }
                            } catch (Exception e) {
                                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );

        // When displaying the cover, use the file if it exists, otherwise show the standard cover
        if (currentBook != null && currentBook.getPreviewImagePath() != null) {
            File coverFile = new File(currentBook.getPreviewImagePath());
            if (coverFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(coverFile.getAbsolutePath());
                bookCoverImage.setImageBitmap(bitmap);
            } else {
                bookCoverImage.setImageResource(R.drawable.ic_book_placeholder);
            }
        } else {
            bookCoverImage.setImageResource(R.drawable.ic_book_placeholder);
        }

        // Set long click listener on book cover
        bookCoverImage.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Change cover image")
                    .setMessage("Choose new cover from your photos")
                    .setPositiveButton("Choose", (dialog, which) -> openImagePicker())
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        // Enable audio button for PDFs
        if ("pdf".equals(fileType)) {
            RadioButton listenButton = findViewById(R.id.audioButton);
            if (listenButton != null) {
                listenButton.setEnabled(true);
                listenButton.setAlpha(1.0f);
            }
        }
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

    // Helper to format time as mm:ss (copied from AudioReaderActivity)
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
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