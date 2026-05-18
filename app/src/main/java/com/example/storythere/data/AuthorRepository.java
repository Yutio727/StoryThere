package com.example.storythere.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.storythere.api.ApiClient;
import com.example.storythere.api.ApiService;
import com.example.storythere.api.model.ApiAuthor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthorRepository {
    private final AuthorDao authorDao;
    private final ApiService apiService;
    private final ExecutorService executorService;

    public AuthorRepository(Context context) {
        AppDatabase database = AppDatabase.getDatabase(context);
        authorDao = database.authorDao();
        apiService = ApiClient.getApiService();
        executorService = Executors.newFixedThreadPool(4);
    }

    // Local database operations
    public LiveData<List<Author>> getAllAuthors() {
        return authorDao.getAllAuthors();
    }

    public LiveData<Author> getAuthorById(String authorId) {
        return authorDao.getAuthorById(authorId);
    }

    public LiveData<List<Author>> getPopularAuthors(int limit) {
        return authorDao.getPopularAuthors(limit);
    }

    public LiveData<List<Author>> searchAuthors(String query) {
        return authorDao.searchAuthors(query);
    }

    public void insertAuthor(Author author) {
        executorService.execute(() -> authorDao.insertAuthor(author));
    }

    public void insertAuthors(List<Author> authors) {
        executorService.execute(() -> authorDao.insertAuthors(authors));
    }

    public void updateAuthor(Author author) {
        executorService.execute(() -> authorDao.updateAuthor(author));
    }

    public void deleteAuthor(Author author) {
        executorService.execute(() -> authorDao.deleteAuthor(author));
    }

    // API operations (method names kept to avoid broad refactor)
    public void loadAuthorsFromFirebase() {
        apiService.getAuthors(200, 0).enqueue(new Callback<List<ApiAuthor>>() {
            @Override
            public void onResponse(Call<List<ApiAuthor>> call, Response<List<ApiAuthor>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Author> authors = new ArrayList<>();
                    for (ApiAuthor apiAuthor : response.body()) {
                        authors.add(mapApiAuthor(apiAuthor));
                    }
                    insertAuthors(authors);
                }
            }

            @Override
            public void onFailure(Call<List<ApiAuthor>> call, Throwable t) {
                // Keep local cache as-is on failure
            }
        });
    }

    public void loadAuthorByIdFromFirebase(String authorId) {
        long parsedId;
        try {
            parsedId = Long.parseLong(authorId);
        } catch (NumberFormatException ignored) {
            return;
        }

        apiService.getAuthorById(parsedId).enqueue(new Callback<ApiAuthor>() {
            @Override
            public void onResponse(Call<ApiAuthor> call, Response<ApiAuthor> response) {
                if (response.isSuccessful() && response.body() != null) {
                    insertAuthor(mapApiAuthor(response.body()));
                }
            }

            @Override
            public void onFailure(Call<ApiAuthor> call, Throwable t) {
                // no-op
            }
        });
    }

    public void saveAuthorToFirebase(Author author) {
        // Not implemented in API flow yet (write endpoints for authors are pending).
        insertAuthor(author);
    }

    public void deleteAuthorFromFirebase(String authorId) {
        // Not implemented in API flow yet.
        executorService.execute(() -> {
            Author author = new Author();
            author.setAuthorId(authorId);
            authorDao.deleteAuthor(author);
        });
    }

    private Author mapApiAuthor(ApiAuthor apiAuthor) {
        Author author = new Author();
        author.setAuthorId(String.valueOf(apiAuthor.id));
        author.setName(apiAuthor.name);
        author.setBiography(apiAuthor.biography);
        author.setBirthDate(apiAuthor.birthdayDate);
        author.setDeathDate(apiAuthor.deathDate);
        author.setNationality(apiAuthor.nationality);
        author.setPhotoUrl(apiAuthor.photoUrl);
        author.setTotalBooks(apiAuthor.totalBooks);
        return author;
    }
}
