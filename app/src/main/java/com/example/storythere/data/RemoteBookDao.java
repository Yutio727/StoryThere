package com.example.storythere.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RemoteBookDao {
    @Query("SELECT * FROM remote_books ORDER BY id DESC LIMIT :limit")
    LiveData<List<RemoteBook>> getRecommendedBooks(int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBooks(List<RemoteBook> books);

    @Query("DELETE FROM remote_books")
    void deleteAllBooks();
}
