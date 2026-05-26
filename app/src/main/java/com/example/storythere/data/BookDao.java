package com.example.storythere.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface BookDao {
    @Insert
    void insert(Book book);
    
    @Update
    void update(Book book);
    
    @Delete
    void delete(Book book);
    
    @Query("SELECT * FROM books ORDER BY lastOpened DESC")
    LiveData<List<Book>> getAllBooks();
    
    @Query("SELECT * FROM books WHERE id = :bookId")
    LiveData<Book> getBookById(long bookId);
    
    @Query("SELECT * FROM books WHERE fileType = :fileType")
    LiveData<List<Book>> getBooksByType(String fileType);
    
    @Query("SELECT * FROM books WHERE filePath = :filePath")
    LiveData<Book> getBookByPath(String filePath);

    @Query("SELECT * FROM books WHERE serverBookId = :serverBookId LIMIT 1")
    LiveData<Book> getBookByServerBookId(long serverBookId);

    @Query("SELECT * FROM books WHERE serverBookId = :serverBookId LIMIT 1")
    Book findBookByServerBookId(long serverBookId);

    @Query("SELECT * FROM books WHERE title = :title AND author = :author LIMIT 1")
    Book findBookByTitleAndAuthor(String title, String author);

    @Query("SELECT * FROM books WHERE serverAudiobookId = :serverAudiobookId LIMIT 1")
    LiveData<Book> getBookByServerAudiobookId(long serverAudiobookId);
} 
