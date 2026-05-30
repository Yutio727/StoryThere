package com.example.storythere.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.storythere.api.ApiClient;
import com.example.storythere.api.ApiService;
import com.example.storythere.api.model.ApiBook;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RemoteBookRepository {
    private static final String TAG = "RemoteBookRepository";
    private static final long RECOMMENDATION_CACHE_TTL_MS = 60_000L;
    private static final long MODEL_CACHE_RETRY_DELAY_MS = 5_000L;
    private static final int MAX_MODEL_CACHE_RETRY_ATTEMPTS = 12;
    private static final String SOURCE_MODEL_CACHE = "model-cache";

    private final RemoteBookDao remoteBookDao;
    private final ApiService apiService;
    private final ExecutorService executorService;
    private final Handler retryHandler;
    private int modelCacheRetryAttempts = 0;
    private boolean modelCacheRetryScheduled = false;
    private Runnable scheduledModelCacheRefresh;

    public RemoteBookRepository(Context context) {
        AppDatabase database = AppDatabase.getDatabase(context);
        remoteBookDao = database.remoteBookDao();
        apiService = ApiClient.getApiService();
        executorService = Executors.newFixedThreadPool(2);
        retryHandler = new Handler(Looper.getMainLooper());
    }

    public LiveData<List<RemoteBook>> getRecommendedBooks(int limit) {
        return remoteBookDao.getRecommendedBooks(limit);
    }

    public void loadRecommendedBooksFromApi(int limit) {
        executorService.execute(() -> {
            Long latestCache = remoteBookDao.getLatestCacheTimestampMillis();
            String latestSource = remoteBookDao.getLatestRecommendationSource();
            long now = System.currentTimeMillis();
            if (SOURCE_MODEL_CACHE.equals(latestSource)
                && latestCache != null
                && latestCache > 0
                && now - latestCache < RECOMMENDATION_CACHE_TTL_MS) {
                Log.d(TAG, "Using cached recommended books. Cache age: " + (now - latestCache) + "ms");
                scheduleModelCacheRefresh(limit, RECOMMENDATION_CACHE_TTL_MS - (now - latestCache));
                return;
            }
            fetchRecommendedBooksFromApi(limit);
        });
    }

    private void fetchRecommendedBooksFromApi(int limit) {
        apiService.getRecommendedBooks(limit).enqueue(new Callback<List<ApiBook>>() {
            @Override
            public void onResponse(Call<List<ApiBook>> call, Response<List<ApiBook>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    handleSuccessfulBooksResponse(response, limit);
                    return;
                }
                Log.w(TAG, "Failed to load recommended books: " + response.code() + " " + errorBody(response));
                loadCatalogBooksFallback(limit);
            }

            @Override
            public void onFailure(Call<List<ApiBook>> call, Throwable t) {
                Log.w(TAG, "Error loading recommended books", t);
                loadCatalogBooksFallback(limit);
            }
        });
    }

    private void loadCatalogBooksFallback(int limit) {
        apiService.getBooks(limit, 0).enqueue(new Callback<List<ApiBook>>() {
            @Override
            public void onResponse(Call<List<ApiBook>> call, Response<List<ApiBook>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    replaceFallbackBooksIfCacheIsEmpty(response.body());
                } else {
                    Log.w(TAG, "Failed to load fallback books: " + response.code() + " " + errorBody(response));
                }
            }

            @Override
            public void onFailure(Call<List<ApiBook>> call, Throwable t) {
                Log.w(TAG, "Error loading fallback books", t);
            }
        });
    }

    private void handleSuccessfulBooksResponse(Response<List<ApiBook>> response, int limit) {
        String source = response.headers().get("X-StoryThere-Recommendation-Source");
        if (SOURCE_MODEL_CACHE.equals(source)) {
            resetModelCacheRetry();
            replaceAllBooks(mapApiBooks(response.body(), System.currentTimeMillis(), source));
            scheduleModelCacheRefresh(limit, RECOMMENDATION_CACHE_TTL_MS);
            return;
        }

        executorService.execute(() -> {
            if (remoteBookDao.getRecommendedBookCount() <= 0) {
                replaceAllBooks(mapApiBooks(response.body(), 0L, source));
            } else {
                Log.d(TAG, "Preserving existing book recommendations while server returns " + source);
            }
            scheduleModelCacheRetry(limit);
        });
    }

    private void scheduleModelCacheRetry(int limit) {
        if (modelCacheRetryScheduled || modelCacheRetryAttempts >= MAX_MODEL_CACHE_RETRY_ATTEMPTS) {
            return;
        }
        modelCacheRetryScheduled = true;
        modelCacheRetryAttempts++;
        retryHandler.postDelayed(() -> {
            modelCacheRetryScheduled = false;
            fetchRecommendedBooksFromApi(limit);
        }, MODEL_CACHE_RETRY_DELAY_MS);
    }

    private void resetModelCacheRetry() {
        modelCacheRetryAttempts = 0;
        modelCacheRetryScheduled = false;
        scheduledModelCacheRefresh = null;
        retryHandler.removeCallbacksAndMessages(null);
    }

    private void scheduleModelCacheRefresh(int limit, long delayMillis) {
        if (scheduledModelCacheRefresh != null) {
            retryHandler.removeCallbacks(scheduledModelCacheRefresh);
        }
        long boundedDelayMillis = Math.max(250L, delayMillis + 250L);
        scheduledModelCacheRefresh = () -> {
            scheduledModelCacheRefresh = null;
            loadRecommendedBooksFromApi(limit);
        };
        retryHandler.postDelayed(scheduledModelCacheRefresh, boundedDelayMillis);
    }

    public void shutdown() {
        retryHandler.removeCallbacksAndMessages(null);
    }

    private void replaceFallbackBooksIfCacheIsEmpty(List<ApiBook> apiBooks) {
        executorService.execute(() -> {
            if (remoteBookDao.getRecommendedBookCount() <= 0) {
                replaceAllBooks(mapApiBooks(apiBooks, 0L, "catalog-fallback"));
            }
        });
    }

    private List<RemoteBook> mapApiBooks(List<ApiBook> apiBooks, long cachedAtMillis, String source) {
        List<RemoteBook> books = new ArrayList<>();
        int rank = 0;
        for (ApiBook apiBook : apiBooks) {
            books.add(mapApiBook(apiBook, rank++, cachedAtMillis, source));
        }
        return books;
    }

    private void replaceAllBooks(List<RemoteBook> books) {
        executorService.execute(() -> {
            remoteBookDao.deleteAllBooks();
            remoteBookDao.insertBooks(books);
        });
    }

    private RemoteBook mapApiBook(ApiBook apiBook, int rank, long cachedAtMillis, String source) {
        RemoteBook book = new RemoteBook();
        book.setId(apiBook.id);
        book.setTitle(apiBook.title);
        book.setAuthor(apiBook.author);
        book.setFileUrl(apiBook.fileUrl);
        book.setFileType(apiBook.fileType);
        book.setImage(apiBook.image);
        book.setAnnotation(apiBook.annotation);
        book.setLicense(apiBook.license);
        book.setRecommendationRank(rank);
        book.setCachedAtMillis(cachedAtMillis);
        book.setRecommendationSource(source);
        return book;
    }

    private String errorBody(Response<?> response) {
        if (response.errorBody() == null) {
            return "";
        }
        try {
            return response.errorBody().string();
        } catch (IOException e) {
            return "";
        }
    }
}
