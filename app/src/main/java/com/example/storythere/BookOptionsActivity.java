package com.example.storythere;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class BookOptionsActivity extends AppCompatActivity {
    private RadioGroup readingModeGroup;
    private Button footerButton;
    private Uri contentUri;
    private String fileType;
    private String title;

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

        // Set up radio group listener
        readingModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selectedButton = findViewById(checkedId);
            if (selectedButton != null) {
                if (selectedButton.getId() == R.id.readButton) {
                    footerButton.setText("Start Reading");
                } else {
                    footerButton.setText("Start Listening");
                }
            }
        });

        // Set up footer button click listener
        footerButton.setOnClickListener(v -> {
            int selectedId = readingModeGroup.getCheckedRadioButtonId();
            if (selectedId == R.id.readButton) {
                // Open reader activity
                Intent readerIntent = new Intent(this, ReaderActivity.class);
                readerIntent.setData(contentUri);
                readerIntent.putExtra("fileType", fileType);
                readerIntent.putExtra("title", title);
                startActivity(readerIntent);
            } else {
                // Parse text and detect language before opening audio reader
                TextParser.ParsedText parsedText = TextParser.parseText(this, contentUri);
                
                // Open audio reader activity
                Intent audioIntent = new Intent(this, AudioReaderActivity.class);
                audioIntent.setData(contentUri);
                audioIntent.putExtra("fileType", fileType);
                audioIntent.putExtra("title", title);
                audioIntent.putExtra("author", "Unknown Author"); // You might want to get this from the book object
                audioIntent.putExtra("is_russian", parsedText.isRussian);
                startActivity(audioIntent);
            }
        });
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