package com.example.storythere.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface AuthorDao {
    @Query("SELECT * FROM authors ORDER BY name ASC")
    LiveData<List<Author>> getAllAuthors();
    
    @Query("SELECT * FROM authors WHERE authorId = :authorId")
    LiveData<Author> getAuthorById(String authorId);
    
    @Query("SELECT * FROM authors WHERE name LIKE '%' || :searchQuery || '%'")
    LiveData<List<Author>> searchAuthors(String searchQuery);
    
    @Query("SELECT * FROM authors ORDER BY name ASC LIMIT :limit")
    LiveData<List<Author>> getPopularAuthors(int limit);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAuthor(Author author);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAuthors(List<Author> authors);
    
    @Update
    void updateAuthor(Author author);
    
    @Delete
    void deleteAuthor(Author author);
    
    @Query("DELETE FROM authors")
    void deleteAllAuthors();
} 