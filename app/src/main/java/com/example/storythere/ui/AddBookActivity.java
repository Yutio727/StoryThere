package com.example.storythere.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.storythere.R;
import com.example.storythere.adapters.AuthorSuggestionsAdapter;
import com.example.storythere.api.ApiClient;
import com.example.storythere.api.ApiService;
import com.example.storythere.api.model.ApiAuthor;
import com.example.storythere.api.model.ApiAudiobook;
import com.example.storythere.api.model.ApiBook;
import com.example.storythere.api.model.AudiobookWriteRequest;
import com.example.storythere.api.model.AuthorWriteRequest;
import com.example.storythere.api.model.BookWriteRequest;
import com.example.storythere.data.Author;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddBookActivity extends AppCompatActivity {
    private static final int AUTHOR_SEARCH_LIMIT = 5;

    private EditText titleEditText;
    private EditText authorEditText;
    private EditText annotationEditText;
    private EditText imageUrlEditText;
    private EditText publicationYearEditText;
    private EditText licenseEditText;
    private EditText recommendationItemIdEditText;
    private EditText genresEditText;
    private EditText fileUrlEditText;
    private EditText fileTypeEditText;
    private EditText dictorEditText;
    private EditText audioUrlEditText;
    private EditText audioTypeEditText;
    private EditText durationSecondsEditText;
    private EditText sourceTextUrlEditText;
    private EditText sourceUrlEditText;
    private CheckBox audiobookCheckBox;
    private View bookFieldsContainer;
    private View audiobookFieldsContainer;
    private Button addBookButton;
    private Button backButton;
    private RecyclerView authorSuggestionsRecyclerView;
    private AuthorSuggestionsAdapter authorSuggestionsAdapter;

    private ApiService apiService;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingAuthorSearch;
    private Long selectedAuthorId = null;
    private String selectedAuthorName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_book);

        apiService = ApiClient.getApiService();
        initializeViews();
        setupAuthorSearch();
        setupButtons();
        updateCatalogTypeFields();
    }

    private void initializeViews() {
        titleEditText = findViewById(R.id.edit_text_title);
        authorEditText = findViewById(R.id.edit_text_author);
        annotationEditText = findViewById(R.id.edit_text_annotation);
        imageUrlEditText = findViewById(R.id.edit_text_image_url);
        publicationYearEditText = findViewById(R.id.edit_text_publication_year);
        licenseEditText = findViewById(R.id.edit_text_license);
        recommendationItemIdEditText = findViewById(R.id.edit_text_recommendation_item_id);
        genresEditText = findViewById(R.id.edit_text_genres);
        fileUrlEditText = findViewById(R.id.edit_text_file_url);
        fileTypeEditText = findViewById(R.id.edit_text_file_type);
        dictorEditText = findViewById(R.id.edit_text_dictor);
        audioUrlEditText = findViewById(R.id.edit_text_audio_url);
        audioTypeEditText = findViewById(R.id.edit_text_audio_type);
        durationSecondsEditText = findViewById(R.id.edit_text_duration_seconds);
        sourceTextUrlEditText = findViewById(R.id.edit_text_source_text_url);
        sourceUrlEditText = findViewById(R.id.edit_text_source_url);
        audiobookCheckBox = findViewById(R.id.checkbox_is_audiobook);
        bookFieldsContainer = findViewById(R.id.book_fields_container);
        audiobookFieldsContainer = findViewById(R.id.audiobook_fields_container);
        addBookButton = findViewById(R.id.button_add_book);
        backButton = findViewById(R.id.button_back);
        authorSuggestionsRecyclerView = findViewById(R.id.author_suggestions_recycler);
    }

    private void setupAuthorSearch() {
        authorSuggestionsAdapter = new AuthorSuggestionsAdapter(new ArrayList<>(), author -> {
            selectedAuthorId = parseLong(author.getAuthorId());
            selectedAuthorName = author.getName();
            authorEditText.setText(author.getName());
            authorEditText.setSelection(authorEditText.getText().length());
            authorSuggestionsRecyclerView.setVisibility(View.GONE);
        });

        authorSuggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        authorSuggestionsRecyclerView.setAdapter(authorSuggestionsAdapter);

        authorEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                if (selectedAuthorName != null && !selectedAuthorName.equals(query)) {
                    selectedAuthorId = null;
                    selectedAuthorName = null;
                }
                if (pendingAuthorSearch != null) {
                    searchHandler.removeCallbacks(pendingAuthorSearch);
                }
                if (query.length() < 2) {
                    authorSuggestionsRecyclerView.setVisibility(View.GONE);
                    return;
                }
                pendingAuthorSearch = () -> searchAuthors(query);
                searchHandler.postDelayed(pendingAuthorSearch, 350);
            }
        });
    }

    private void setupButtons() {
        audiobookCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> updateCatalogTypeFields());
        addBookButton.setOnClickListener(v -> submitCatalogItem());
        backButton.setOnClickListener(v -> finish());
    }

    private void updateCatalogTypeFields() {
        boolean isAudiobook = audiobookCheckBox.isChecked();
        bookFieldsContainer.setVisibility(isAudiobook ? View.GONE : View.VISIBLE);
        audiobookFieldsContainer.setVisibility(isAudiobook ? View.VISIBLE : View.GONE);
    }

    private void searchAuthors(String query) {
        apiService.searchAuthors(AUTHOR_SEARCH_LIMIT, 0, query).enqueue(new Callback<List<ApiAuthor>>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiAuthor>> call, @NonNull Response<List<ApiAuthor>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    authorSuggestionsRecyclerView.setVisibility(View.GONE);
                    return;
                }
                List<Author> authors = new ArrayList<>();
                for (ApiAuthor apiAuthor : response.body()) {
                    authors.add(mapApiAuthor(apiAuthor));
                }
                authorSuggestionsAdapter.updateAuthors(authors);
                authorSuggestionsRecyclerView.setVisibility(authors.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiAuthor>> call, @NonNull Throwable t) {
                authorSuggestionsRecyclerView.setVisibility(View.GONE);
            }
        });
    }

    private void submitCatalogItem() {
        String title = text(titleEditText);
        String authorName = text(authorEditText);
        if (title.isEmpty() || authorName.isEmpty()) {
            Toast.makeText(this, R.string.please_fill_in_all_required_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        if (audiobookCheckBox.isChecked() && parseLong(text(recommendationItemIdEditText)) == null) {
            Toast.makeText(this, R.string.recommendation_item_id_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!audiobookCheckBox.isChecked()
            && (text(fileUrlEditText).isEmpty() || text(fileTypeEditText).isEmpty())) {
            Toast.makeText(this, R.string.please_fill_in_all_required_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        if (audiobookCheckBox.isChecked()
            && (text(audioUrlEditText).isEmpty() || text(audioTypeEditText).isEmpty())) {
            Toast.makeText(this, R.string.please_fill_in_all_required_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        setSubmitting(true);
        resolveAuthor(authorName);
    }

    private void resolveAuthor(String authorName) {
        if (selectedAuthorId != null && authorName.equals(selectedAuthorName)) {
            submitWithAuthor(selectedAuthorId, authorName);
            return;
        }

        apiService.searchAuthors(10, 0, authorName).enqueue(new Callback<List<ApiAuthor>>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiAuthor>> call, @NonNull Response<List<ApiAuthor>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (ApiAuthor author : response.body()) {
                        if (author.name != null && author.name.trim().equalsIgnoreCase(authorName.trim())) {
                            submitWithAuthor(author.id, author.name);
                            return;
                        }
                    }
                }
                createAuthorThenSubmit(authorName);
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiAuthor>> call, @NonNull Throwable t) {
                createAuthorThenSubmit(authorName);
            }
        });
    }

    private void createAuthorThenSubmit(String authorName) {
        AuthorWriteRequest request = new AuthorWriteRequest();
        request.name = authorName;
        apiService.createAuthor(request).enqueue(new Callback<ApiAuthor>() {
            @Override
            public void onResponse(@NonNull Call<ApiAuthor> call, @NonNull Response<ApiAuthor> response) {
                if (response.isSuccessful() && response.body() != null) {
                    submitWithAuthor(response.body().id, response.body().name);
                } else {
                    failSubmit(getString(R.string.failed_to_add_author) + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiAuthor> call, @NonNull Throwable t) {
                failSubmit(getString(R.string.failed_to_add_author) + t.getMessage());
            }
        });
    }

    private void submitWithAuthor(long authorId, String authorName) {
        if (audiobookCheckBox.isChecked()) {
            submitAudiobook(authorId, authorName);
        } else {
            submitBook(authorId, authorName);
        }
    }

    private void submitBook(long authorId, String authorName) {
        BookWriteRequest request = new BookWriteRequest();
        request.recommendationItemId = parseLong(text(recommendationItemIdEditText));
        request.title = text(titleEditText);
        request.author = authorName;
        request.authorID = authorId;
        request.publicationYear = parseInt(text(publicationYearEditText));
        request.annotation = optionalText(annotationEditText);
        request.image = optionalText(imageUrlEditText);
        request.license = optionalText(licenseEditText);
        request.fileType = text(fileTypeEditText).toLowerCase(Locale.US);
        request.fileUrl = text(fileUrlEditText);
        request.genres = genres();

        apiService.createBook(request).enqueue(new Callback<ApiBook>() {
            @Override
            public void onResponse(@NonNull Call<ApiBook> call, @NonNull Response<ApiBook> response) {
                if (response.isSuccessful()) {
                    finishSubmit();
                } else {
                    failSubmit(getString(R.string.failed_to_add_book) + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiBook> call, @NonNull Throwable t) {
                failSubmit(getString(R.string.failed_to_add_book) + t.getMessage());
            }
        });
    }

    private void submitAudiobook(long authorId, String authorName) {
        AudiobookWriteRequest request = new AudiobookWriteRequest();
        request.recommendationItemId = parseLong(text(recommendationItemIdEditText));
        request.title = text(titleEditText);
        request.author = authorName;
        request.authorID = authorId;
        request.dictor = optionalText(dictorEditText);
        request.publicationYear = parseInt(text(publicationYearEditText));
        request.annotation = optionalText(annotationEditText);
        request.image = optionalText(imageUrlEditText);
        request.license = optionalText(licenseEditText);
        request.audioType = text(audioTypeEditText).toLowerCase(Locale.US);
        request.audioUrl = text(audioUrlEditText);
        request.durationSeconds = parseInt(text(durationSecondsEditText));
        request.sourceTextUrl = optionalText(sourceTextUrlEditText);
        request.sourceUrl = optionalText(sourceUrlEditText);
        request.genres = genres();

        apiService.createAudiobook(request).enqueue(new Callback<ApiAudiobook>() {
            @Override
            public void onResponse(@NonNull Call<ApiAudiobook> call, @NonNull Response<ApiAudiobook> response) {
                if (response.isSuccessful()) {
                    finishSubmit();
                } else {
                    failSubmit(getString(R.string.failed_to_add_book) + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiAudiobook> call, @NonNull Throwable t) {
                failSubmit(getString(R.string.failed_to_add_book) + t.getMessage());
            }
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

    private void finishSubmit() {
        Toast.makeText(this, R.string.book_added_successfully, Toast.LENGTH_LONG).show();
        clearFields();
        setSubmitting(false);
    }

    private void failSubmit(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        setSubmitting(false);
    }

    private void setSubmitting(boolean submitting) {
        addBookButton.setEnabled(!submitting);
        addBookButton.setText(submitting ? R.string.adding : R.string.add_book);
    }

    private void clearFields() {
        for (EditText editText : Arrays.asList(
            titleEditText,
            authorEditText,
            annotationEditText,
            imageUrlEditText,
            publicationYearEditText,
            licenseEditText,
            recommendationItemIdEditText,
            genresEditText,
            fileUrlEditText,
            fileTypeEditText,
            dictorEditText,
            audioUrlEditText,
            audioTypeEditText,
            durationSecondsEditText,
            sourceTextUrlEditText,
            sourceUrlEditText
        )) {
            editText.setText("");
        }
        selectedAuthorId = null;
        selectedAuthorName = null;
        authorSuggestionsRecyclerView.setVisibility(View.GONE);
    }

    private String text(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private String optionalText(EditText editText) {
        String value = text(editText);
        return value.isEmpty() ? null : value;
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.trim().isEmpty() ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        try {
            return value == null || value.trim().isEmpty() ? null : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<String> genres() {
        String value = text(genresEditText);
        if (value.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String part : value.split(",")) {
            String genre = part.trim();
            if (!genre.isEmpty()) {
                out.add(genre);
            }
        }
        return out;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingAuthorSearch != null) {
            searchHandler.removeCallbacks(pendingAuthorSearch);
        }
    }
}
