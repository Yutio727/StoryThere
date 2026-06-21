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
    
    @Query("SELECT * FROM books WHERE ownerUid = :ownerUid ORDER BY lastOpened DESC")
    LiveData<List<Book>> getAllBooks(String ownerUid);
    
    @Query("SELECT * FROM books WHERE id = :bookId AND ownerUid = :ownerUid")
    LiveData<Book> getBookById(long bookId, String ownerUid);
    
    @Query("SELECT * FROM books WHERE fileType = :fileType AND ownerUid = :ownerUid")
    LiveData<List<Book>> getBooksByType(String fileType, String ownerUid);
    
    @Query("SELECT * FROM books WHERE filePath = :filePath AND ownerUid = :ownerUid")
    LiveData<Book> getBookByPath(String filePath, String ownerUid);

    @Query("SELECT * FROM books WHERE filePath = :filePath AND ownerUid = :ownerUid LIMIT 1")
    Book findBookByPath(String filePath, String ownerUid);

    @Query("SELECT * FROM books WHERE serverBookId = :serverBookId AND ownerUid = :ownerUid LIMIT 1")
    LiveData<Book> getBookByServerBookId(long serverBookId, String ownerUid);

    @Query("SELECT * FROM books WHERE serverBookId = :serverBookId AND ownerUid = :ownerUid LIMIT 1")
    Book findBookByServerBookId(long serverBookId, String ownerUid);

    @Query("SELECT * FROM books WHERE title = :title AND author = :author AND ownerUid = :ownerUid LIMIT 1")
    Book findBookByTitleAndAuthor(String title, String author, String ownerUid);

    @Query("SELECT * FROM books WHERE serverAudiobookId = :serverAudiobookId AND ownerUid = :ownerUid LIMIT 1")
    LiveData<Book> getBookByServerAudiobookId(long serverAudiobookId, String ownerUid);

    @Query("UPDATE books SET isAlreadyRead = 1 WHERE id = :bookId AND ownerUid = :ownerUid")
    void markAlreadyReadById(long bookId, String ownerUid);

    @Query("UPDATE books SET isAlreadyRead = 1 WHERE serverBookId = :serverBookId AND serverBookId > 0 AND ownerUid = :ownerUid")
    void markAlreadyReadByServerBookId(long serverBookId, String ownerUid);

    @Query("UPDATE books SET isAlreadyRead = 1 WHERE serverAudiobookId = :serverAudiobookId AND serverAudiobookId > 0 AND ownerUid = :ownerUid")
    void markAlreadyReadByServerAudiobookId(long serverAudiobookId, String ownerUid);

    @Query("UPDATE books SET isAlreadyRead = 1 WHERE filePath = :filePath AND ownerUid = :ownerUid")
    void markAlreadyReadByPath(String filePath, String ownerUid);

    @Query("DELETE FROM books WHERE ownerUid = :ownerUid")
    void deleteBooksForOwner(String ownerUid);
}
