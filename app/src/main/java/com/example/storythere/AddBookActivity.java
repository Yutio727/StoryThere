package com.example.storythere;

import static com.example.storythere.R.*;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class AddBookActivity extends AppCompatActivity {
    
    private EditText titleEditText, authorEditText, annotationEditText, fileUrlEditText, imageUrlEditText, fileTypeEditText;
    private Button addBookButton, backButton;
    private FirebaseFirestore db;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_book);
        
        initializeViews();
        setupFirebase();
        setupButtons();
    }
    
    private void initializeViews() {
        titleEditText = findViewById(R.id.edit_text_title);
        authorEditText = findViewById(R.id.edit_text_author);
        annotationEditText = findViewById(R.id.edit_text_annotation);
        fileUrlEditText = findViewById(R.id.edit_text_file_url);
        imageUrlEditText = findViewById(R.id.edit_text_image_url);
        fileTypeEditText = findViewById(R.id.edit_text_file_type);
        addBookButton = findViewById(R.id.button_add_book);
        backButton = findViewById(R.id.button_back);
    }
    
    private void setupFirebase() {
        db = FirebaseFirestore.getInstance();
    }
    
    private void setupButtons() {
        addBookButton.setOnClickListener(v -> addBookToFirebase());
        backButton.setOnClickListener(v -> finish());
    }
    
    private void addBookToFirebase() {
        String title = titleEditText.getText().toString().trim();
        String author = authorEditText.getText().toString().trim();
        String annotation = annotationEditText.getText().toString().trim();
        String fileUrl = fileUrlEditText.getText().toString().trim();
        String imageUrl = imageUrlEditText.getText().toString().trim();
        String fileType = fileTypeEditText.getText().toString().trim();
        
        // Validate input
        if (title.isEmpty() || author.isEmpty() || fileUrl.isEmpty() || fileType.isEmpty()) {
            Toast.makeText(this, R.string.please_fill_in_all_required_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading
        addBookButton.setEnabled(false);
        addBookButton.setText(R.string.adding);
        
        // Create book data
        Map<String, Object> book = new HashMap<>();
        book.put("title", title);
        book.put("author", author);
        book.put("annotation", annotation);
        book.put("fileUrl", fileUrl);
        book.put("image", imageUrl);
        book.put("fileType", fileType);
        
        // Generate document ID (you can modify this logic as needed)
        String documentId = "bookId" + System.currentTimeMillis();
        
        // Add to Firebase
        db.collection("books").document(documentId)
            .set(book)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(this, R.string.book_added_successfully, Toast.LENGTH_LONG).show();
                clearFields();
                addBookButton.setEnabled(true);
                addBookButton.setText(R.string.add_book);
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, getString(R.string.failed_to_add_book) + e.getMessage(), Toast.LENGTH_LONG).show();
                addBookButton.setEnabled(true);
                addBookButton.setText(R.string.add_book);
            });
    }
    
    private void clearFields() {
        titleEditText.setText("");
        authorEditText.setText("");
        annotationEditText.setText("");
        fileUrlEditText.setText("");
        imageUrlEditText.setText("");
        fileTypeEditText.setText("");
    }
} 