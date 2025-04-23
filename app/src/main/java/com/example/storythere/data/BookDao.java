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
} 