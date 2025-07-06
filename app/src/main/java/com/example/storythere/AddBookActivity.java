package com.example.storythere;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import androidx.annotation.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.example.storythere.data.Author;
import com.example.storythere.ui.AuthorSuggestionsAdapter;

public class AddBookActivity extends AppCompatActivity {
    
    private EditText titleEditText, authorEditText, annotationEditText, fileUrlEditText, imageUrlEditText, fileTypeEditText;
    private Button addBookButton, backButton;
    private RecyclerView authorSuggestionsRecyclerView;
    private AuthorSuggestionsAdapter authorSuggestionsAdapter;
    private FirebaseFirestore db;
    private ScheduledExecutorService scheduler;
    private String selectedAuthorId = null;
    private String selectedAuthorName = null;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_book);
        
        initializeViews();
        setupFirebase();
        setupAuthorSearch();
        setupButtons();
        scheduler = Executors.newScheduledThreadPool(1);
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
        authorSuggestionsRecyclerView = findViewById(R.id.author_suggestions_recycler);
    }
    
    private void setupFirebase() {
        db = FirebaseFirestore.getInstance();
    }
    
    private void setupAuthorSearch() {
        // Setup RecyclerView for author suggestions
        authorSuggestionsAdapter = new AuthorSuggestionsAdapter(new ArrayList<>(), author -> {
            selectedAuthorId = author.getAuthorId();
            selectedAuthorName = author.getName();
            authorEditText.setText(author.getName());
            authorSuggestionsRecyclerView.setVisibility(View.GONE);
        });
        
        authorSuggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        authorSuggestionsRecyclerView.setAdapter(authorSuggestionsAdapter);
        
        // Setup text watcher with debouncing
        authorEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                if (query.length() >= 2) {
                    // Cancel previous search and schedule new one with delay
                    scheduler.schedule(() -> searchAuthors(query), 500, TimeUnit.MILLISECONDS);
                } else {
                    authorSuggestionsRecyclerView.setVisibility(View.GONE);
                    selectedAuthorId = null;
                    selectedAuthorName = null;
                }
            }
        });
    }
    
    private void searchAuthors(String query) {
        // Convert query to lowercase for case-insensitive search
        String searchQuery = query.toLowerCase().trim();
        
        db.collection("authors")
            .get()
            .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<Author> authors = new ArrayList<>();
                        for (DocumentSnapshot document : task.getResult()) {
                            Author author = document.toObject(Author.class);
                            if (author != null) {
                                author.setAuthorId(document.getId());
                                
                                // Check if author name contains the search query (case-insensitive)
                                String authorName = author.getName();
                                if (authorName != null && authorName.toLowerCase().contains(searchQuery)) {
                                    authors.add(author);
                                }
                            }
                        }
                        
                        // Sort results by relevance (exact matches first, then partial matches)
                        authors.sort((a1, a2) -> {
                            String name1 = a1.getName().toLowerCase();
                            String name2 = a2.getName().toLowerCase();
                            
                            // Check if name starts with query (higher priority)
                            boolean startsWith1 = name1.startsWith(searchQuery);
                            boolean startsWith2 = name2.startsWith(searchQuery);
                            
                            if (startsWith1 && !startsWith2) return -1;
                            if (!startsWith1 && startsWith2) return 1;
                            
                            // If both start with query or neither does, sort alphabetically
                            return name1.compareTo(name2);
                        });
                        
                        // Limit results to 5
                        if (authors.size() > 5) {
                            authors = authors.subList(0, 5);
                        }
                        
                        if (!authors.isEmpty()) {
                            authorSuggestionsAdapter.updateAuthors(authors);
                            authorSuggestionsRecyclerView.setVisibility(View.VISIBLE);
                        } else {
                            authorSuggestionsRecyclerView.setVisibility(View.GONE);
                        }
                    } else {
                        authorSuggestionsRecyclerView.setVisibility(View.GONE);
                    }
                }
            });
    }
    
    private void setupButtons() {
        addBookButton.setOnClickListener(v -> addBookToFirebase());
        backButton.setOnClickListener(v -> finish());
    }
    
    private void addBookToFirebase() {
        String title = titleEditText.getText().toString().trim();
        String authorName = authorEditText.getText().toString().trim();
        String annotation = annotationEditText.getText().toString().trim();
        String fileUrl = fileUrlEditText.getText().toString().trim();
        String imageUrl = imageUrlEditText.getText().toString().trim();
        String fileType = fileTypeEditText.getText().toString().trim();
        
        // Validate input
        if (title.isEmpty() || authorName.isEmpty() || fileUrl.isEmpty() || fileType.isEmpty()) {
            Toast.makeText(this, R.string.please_fill_in_all_required_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading
        addBookButton.setEnabled(false);
        addBookButton.setText(R.string.adding);
        
        // Generate book document ID from title
        String bookDocumentId = generateDocumentIdFromTitle(title);
        
        // Log the generated document ID for debugging
        System.out.println("Generated book document ID: " + bookDocumentId);
        
        // Create book data
        Map<String, Object> book = new HashMap<>();
        book.put("title", title);
        book.put("author", authorName);
        book.put("annotation", annotation);
        book.put("fileUrl", fileUrl);
        book.put("image", imageUrl);
        book.put("fileType", fileType);
        
        // If we have a selected author, add only the authorID reference
        if (selectedAuthorId != null) {
            // Create reference to author document (only this field, no string field)
            book.put("authorID", db.collection("authors").document(selectedAuthorId));
        }
        
        // Add book to Firebase
        db.collection("books").document(bookDocumentId)
            .set(book)
            .addOnSuccessListener(aVoid -> {
                System.out.println("Book added successfully with ID: " + bookDocumentId);
                // If we have a selected author, create/update the author's books collection
                if (selectedAuthorId != null) {
                    createAuthorBookReference(selectedAuthorId, bookDocumentId, title);
                } else {
                    // Create new author and link the book
                    createNewAuthorAndLinkBook(authorName, bookDocumentId, title);
                }
            })
            .addOnFailureListener(e -> {
                System.err.println("Failed to add book: " + e.getMessage());
                Toast.makeText(this, getString(R.string.failed_to_add_book) + e.getMessage(), Toast.LENGTH_LONG).show();
                addBookButton.setEnabled(true);
                addBookButton.setText(R.string.add_book);
            });
    }
    
    private void createAuthorBookReference(String authorId, String bookDocumentId, String bookTitle) {
        Map<String, Object> bookReference = new HashMap<>();
        bookReference.put("bookId", bookDocumentId);
        
        db.collection("authors").document(authorId)
            .collection("books").document(bookDocumentId)
            .set(bookReference)
            .addOnSuccessListener(aVoid -> {
                // Update author's totalBooks count by counting documents in books subcollection
                updateAuthorBookCount(authorId);
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Book added but failed to link to author", Toast.LENGTH_SHORT).show();
            });
        
        Toast.makeText(this, R.string.book_added_successfully, Toast.LENGTH_LONG).show();
        clearFields();
        addBookButton.setEnabled(true);
        addBookButton.setText(R.string.add_book);
    }
    
    private void createNewAuthorAndLinkBook(String authorName, String bookDocumentId, String bookTitle) {
        // Create new author document with special ID format
        String authorDocumentId = "author" + System.currentTimeMillis();
        
        Map<String, Object> author = new HashMap<>();
        author.put("name", authorName);
        author.put("biography", "");
        author.put("birthdayDate", "");
        author.put("deathDate", "");
        author.put("nationality", "");
        author.put("photoUrl", "");
        author.put("totalBooks", 0); // Start with 0, will be updated after adding book
        
        db.collection("authors").document(authorDocumentId)
            .set(author)
            .addOnSuccessListener(aVoid -> {
                System.out.println("New author created with ID: " + authorDocumentId);
                
                // Update the book with the new author reference
                Map<String, Object> bookUpdate = new HashMap<>();
                bookUpdate.put("authorID", db.collection("authors").document(authorDocumentId));
                
                db.collection("books").document(bookDocumentId)
                    .update(bookUpdate)
                    .addOnSuccessListener(aVoid2 -> {
                        // Create author's books collection and add book reference
                        Map<String, Object> bookReference = new HashMap<>();
                        bookReference.put("bookId", bookDocumentId);
                        
                        db.collection("authors").document(authorDocumentId)
                            .collection("books").document(bookDocumentId)
                            .set(bookReference)
                            .addOnSuccessListener(aVoid3 -> {
                                // Update author's totalBooks count by counting documents in books subcollection
                                updateAuthorBookCount(authorDocumentId);
                                Toast.makeText(this, R.string.book_added_successfully, Toast.LENGTH_LONG).show();
                                clearFields();
                                addBookButton.setEnabled(true);
                                addBookButton.setText(R.string.add_book);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Book and author added but failed to link", Toast.LENGTH_SHORT).show();
                                addBookButton.setEnabled(true);
                                addBookButton.setText(R.string.add_book);
                            });
                    })
                    .addOnFailureListener(e -> {
                        System.err.println("Failed to update book with author reference: " + e.getMessage());
                        Toast.makeText(this, "Author created but failed to update book", Toast.LENGTH_SHORT).show();
                        addBookButton.setEnabled(true);
                        addBookButton.setText(R.string.add_book);
                    });
            })
            .addOnFailureListener(e -> {
                System.err.println("Failed to create new author: " + e.getMessage());
                Toast.makeText(this, getString(R.string.failed_to_add_book) + e.getMessage(), Toast.LENGTH_LONG).show();
                addBookButton.setEnabled(true);
                addBookButton.setText(R.string.add_book);
            });
    }
    
    private void updateAuthorBookCount(String authorId) {
        db.collection("authors").document(authorId)
            .collection("books")
            .get()
            .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                    if (task.isSuccessful() && task.getResult() != null) {
                        int bookCount = task.getResult().size();
                        // Update the totalBooks field with the actual count of documents
                        db.collection("authors").document(authorId)
                            .update("totalBooks", bookCount)
                            .addOnSuccessListener(aVoid -> {
                                // Successfully updated totalBooks
                            })
                            .addOnFailureListener(e -> {
                                // Log error but don't show to user since book was already added
                                System.err.println("Failed to update totalBooks: " + e.getMessage());
                            });
                    }
                }
            });
    }
    
    private String generateDocumentIdFromTitle(String title) {
        // Convert title to a valid document ID
        String documentId = title.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        
        // Ensure the document ID is not empty and has valid characters
        if (documentId.isEmpty()) {
            documentId = "book_" + System.currentTimeMillis();
        }
        
        // Ensure it starts with a letter or number (not special characters)
        if (!documentId.matches("^[a-zA-Z0-9].*")) {
            documentId = "book_" + documentId;
        }
        
        // Limit length to avoid issues
        if (documentId.length() > 50) {
            documentId = documentId.substring(0, 50);
        }
        
        return documentId;
    }
    
    private void clearFields() {
        titleEditText.setText("");
        authorEditText.setText("");
        annotationEditText.setText("");
        fileUrlEditText.setText("");
        imageUrlEditText.setText("");
        fileTypeEditText.setText("");
        authorSuggestionsRecyclerView.setVisibility(View.GONE);
        selectedAuthorId = null;
        selectedAuthorName = null;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
} 