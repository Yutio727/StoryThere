package com.example.storythere.data;

import android.content.Context;
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

    private final RemoteBookDao remoteBookDao;
    private final ApiService apiService;
    private final ExecutorService executorService;

    public RemoteBookRepository(Context context) {
        AppDatabase database = AppDatabase.getDatabase(context);
        remoteBookDao = database.remoteBookDao();
        apiService = ApiClient.getApiService();
        executorService = Executors.newFixedThreadPool(2);
    }

    public LiveData<List<RemoteBook>> getRecommendedBooks(int limit) {
        return remoteBookDao.getRecommendedBooks(limit);
    }

    public void loadRecommendedBooksFromApi(int limit) {
        apiService.getRecommendedBooks(limit).enqueue(new Callback<List<ApiBook>>() {
            @Override
            public void onResponse(Call<List<ApiBook>> call, Response<List<ApiBook>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    replaceAllBooks(mapApiBooks(response.body()));
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
                    replaceAllBooks(mapApiBooks(response.body()));
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

    private List<RemoteBook> mapApiBooks(List<ApiBook> apiBooks) {
        List<RemoteBook> books = new ArrayList<>();
        for (ApiBook apiBook : apiBooks) {
            books.add(mapApiBook(apiBook));
        }
        return books;
    }

    private void replaceAllBooks(List<RemoteBook> books) {
        executorService.execute(() -> {
            remoteBookDao.deleteAllBooks();
            remoteBookDao.insertBooks(books);
        });
    }

    private RemoteBook mapApiBook(ApiBook apiBook) {
        RemoteBook book = new RemoteBook();
        book.setId(apiBook.id);
        book.setTitle(apiBook.title);
        book.setAuthor(apiBook.author);
        book.setFileUrl(apiBook.fileUrl);
        book.setFileType(apiBook.fileType);
        book.setImage(apiBook.image);
        book.setAnnotation(apiBook.annotation);
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
