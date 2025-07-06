package com.example.storythere.data;

import android.content.Context;
import androidx.lifecycle.LiveData;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.OnCompleteListener;
import androidx.annotation.NonNull;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthorRepository {
    private AuthorDao authorDao;
    private FirebaseFirestore firestore;
    private ExecutorService executorService;
    
    public AuthorRepository(Context context) {
        AppDatabase database = AppDatabase.getDatabase(context);
        authorDao = database.authorDao();
        firestore = FirebaseFirestore.getInstance();
        executorService = Executors.newFixedThreadPool(4);
    }
    
    // Local database operations
    public LiveData<List<Author>> getAllAuthors() {
        return authorDao.getAllAuthors();
    }
    
    public LiveData<Author> getAuthorById(String authorId) {
        return authorDao.getAuthorById(authorId);
    }
    
    public LiveData<List<Author>> getPopularAuthors(int limit) {
        return authorDao.getPopularAuthors(limit);
    }
    
    public LiveData<List<Author>> searchAuthors(String query) {
        return authorDao.searchAuthors(query);
    }
    
    public void insertAuthor(Author author) {
        executorService.execute(() -> {
            authorDao.insertAuthor(author);
        });
    }
    
    public void insertAuthors(List<Author> authors) {
        executorService.execute(() -> {
            authorDao.insertAuthors(authors);
        });
    }
    
    public void updateAuthor(Author author) {
        executorService.execute(() -> {
            authorDao.updateAuthor(author);
        });
    }
    
    public void deleteAuthor(Author author) {
        executorService.execute(() -> {
            authorDao.deleteAuthor(author);
        });
    }
    
    // Firebase operations
    public void loadAuthorsFromFirebase() {
        firestore.collection("authors")
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
                                authors.add(author);
                            }
                        }
                        insertAuthors(authors);
                    }
                }
            });
    }
    
    public void loadAuthorByIdFromFirebase(String authorId) {
        firestore.collection("authors")
            .document(authorId)
            .get()
            .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if (task.isSuccessful() && task.getResult() != null) {
                        Author author = task.getResult().toObject(Author.class);
                        if (author != null) {
                            author.setAuthorId(task.getResult().getId());
                            insertAuthor(author);
                        }
                    }
                }
            });
    }
    
    public void saveAuthorToFirebase(Author author) {
        firestore.collection("authors")
            .document(author.getAuthorId())
            .set(author)
            .addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (task.isSuccessful()) {
                        // Update local database
                        insertAuthor(author);
                    }
                }
            });
    }
    
    public void deleteAuthorFromFirebase(String authorId) {
        firestore.collection("authors")
            .document(authorId)
            .delete()
            .addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (task.isSuccessful()) {
                        // Remove from local database
                        executorService.execute(() -> {
                            Author author = new Author();
                            author.setAuthorId(authorId);
                            authorDao.deleteAuthor(author);
                        });
                    }
                }
            });
    }
} 