package com.example.storythere.data;

import android.app.Application;
import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BookRepository {
    private final BookDao bookDao;
    private final ExecutorService executorService;
    private final String ownerUid;
    
    public BookRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        bookDao = db.bookDao();
        executorService = Executors.newSingleThreadExecutor();
        ownerUid = LocalUserScope.currentOwnerUid();
    }
    
    public LiveData<List<Book>> getAllBooks() {
        return bookDao.getAllBooks(ownerUid);
    }
    
    public LiveData<Book> getBookById(long bookId) {
        return bookDao.getBookById(bookId, ownerUid);
    }
    
    public LiveData<Book> getBookByPath(String filePath) {
        return bookDao.getBookByPath(filePath, ownerUid);
    }

    public LiveData<Book> getBookByServerBookId(long serverBookId) {
        return bookDao.getBookByServerBookId(serverBookId, ownerUid);
    }

    public LiveData<Book> getBookByServerAudiobookId(long serverAudiobookId) {
        return bookDao.getBookByServerAudiobookId(serverAudiobookId, ownerUid);
    }
    
    public void insert(Book book) {
        executorService.execute(() -> {
            ensureOwner(book);
            // Check if book already exists
            Book existingBook = book.getFilePath() == null
                ? null
                : bookDao.findBookByPath(book.getFilePath(), ownerUid);
            if (existingBook == null) {
                bookDao.insert(book);
            }
        });
    }
    
    public void update(Book book) {
        executorService.execute(() -> {
            ensureOwner(book);
            bookDao.update(book);
        });
    }
    
    public void delete(Book book) {
        executorService.execute(() -> {
            bookDao.delete(book);
        });
    }

    public void markAlreadyReadById(long bookId) {
        if (bookId <= 0) {
            return;
        }
        executorService.execute(() -> bookDao.markAlreadyReadById(bookId, ownerUid));
    }

    public void markAlreadyReadByServerBookId(long serverBookId) {
        if (serverBookId <= 0) {
            return;
        }
        executorService.execute(() -> bookDao.markAlreadyReadByServerBookId(serverBookId, ownerUid));
    }

    public void markAlreadyReadByServerAudiobookId(long serverAudiobookId) {
        if (serverAudiobookId <= 0) {
            return;
        }
        executorService.execute(() -> bookDao.markAlreadyReadByServerAudiobookId(serverAudiobookId, ownerUid));
    }

    public void markAlreadyReadByPath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }
        executorService.execute(() -> bookDao.markAlreadyReadByPath(filePath, ownerUid));
    }

    public void clearCurrentUserBooks() {
        executorService.execute(() -> bookDao.deleteBooksForOwner(ownerUid));
    }

    private void ensureOwner(Book book) {
        if (book != null && LocalUserScope.isMissing(book.getOwnerUid())) {
            book.setOwnerUid(ownerUid);
        }
    }
}
