package com.example.storythere.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RemoteBookDao {
    @Query("SELECT * FROM remote_books ORDER BY recommendationRank ASC, id DESC LIMIT :limit")
    LiveData<List<RemoteBook>> getRecommendedBooks(int limit);

    @Query("SELECT MAX(cachedAtMillis) FROM remote_books")
    Long getLatestCacheTimestampMillis();

    @Query("SELECT recommendationSource FROM remote_books ORDER BY cachedAtMillis DESC LIMIT 1")
    String getLatestRecommendationSource();

    @Query("SELECT COUNT(*) FROM remote_books")
    int getRecommendedBookCount();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBooks(List<RemoteBook> books);

    @Query("DELETE FROM remote_books")
    void deleteAllBooks();
}
