package com.example.storythere.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RemoteBookDao {
    @Query("SELECT * FROM remote_books WHERE ownerUid = :ownerUid ORDER BY recommendationRank ASC, id DESC LIMIT :limit")
    LiveData<List<RemoteBook>> getRecommendedBooks(int limit, String ownerUid);

    @Query("SELECT MAX(cachedAtMillis) FROM remote_books WHERE ownerUid = :ownerUid")
    Long getLatestCacheTimestampMillis(String ownerUid);

    @Query("SELECT recommendationSource FROM remote_books WHERE ownerUid = :ownerUid ORDER BY cachedAtMillis DESC LIMIT 1")
    String getLatestRecommendationSource(String ownerUid);

    @Query("SELECT COUNT(*) FROM remote_books WHERE ownerUid = :ownerUid")
    int getRecommendedBookCount(String ownerUid);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBooks(List<RemoteBook> books);

    @Query("DELETE FROM remote_books WHERE ownerUid = :ownerUid")
    void deleteAllBooks(String ownerUid);
}
