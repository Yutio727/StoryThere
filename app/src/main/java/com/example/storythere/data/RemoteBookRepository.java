package com.example.storythere.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.storythere.api.ApiClient;
import com.example.storythere.api.ApiService;
import com.example.storythere.api.model.ApiBook;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RemoteBookRepository {
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
        apiService.getBooks(limit, 0).enqueue(new Callback<List<ApiBook>>() {
            @Override
            public void onResponse(Call<List<ApiBook>> call, Response<List<ApiBook>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<RemoteBook> books = new ArrayList<>();
                    for (ApiBook apiBook : response.body()) {
                        books.add(mapApiBook(apiBook));
                    }
                    replaceAllBooks(books);
                }
            }

            @Override
            public void onFailure(Call<List<ApiBook>> call, Throwable t) {
                // Keep local cache as-is on failure.
            }
        });
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
}
