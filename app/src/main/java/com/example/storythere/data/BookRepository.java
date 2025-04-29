package com.example.storythere.data;

import android.app.Application;
import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BookRepository {
    private final BookDao bookDao;
    private final ExecutorService executorService;
    
    public BookRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        bookDao = db.bookDao();
        executorService = Executors.newSingleThreadExecutor();
    }
    
    public LiveData<List<Book>> getAllBooks() {
        return bookDao.getAllBooks();
    }
    
    public LiveData<Book> getBookById(long bookId) {
        return bookDao.getBookById(bookId);
    }
    
    public LiveData<Book> getBookByPath(String filePath) {
        return bookDao.getBookByPath(filePath);
    }
    
    public void insert(Book book) {
        executorService.execute(() -> {
            // Check if book already exists
            Book existingBook = bookDao.getBookByPath(book.getFilePath()).getValue();
            if (existingBook == null) {
                bookDao.insert(book);
            }
        });
    }
    
    public void update(Book book) {
        executorService.execute(() -> {
            bookDao.update(book);
        });
    }
    
    public void delete(Book book) {
        executorService.execute(() -> {
            bookDao.delete(book);
        });
    }
} 