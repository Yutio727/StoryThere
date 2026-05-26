package com.example.storythere.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.storythere.R;
import com.example.storythere.adapters.OnboardingAuthorAdapter;
import com.example.storythere.adapters.OnboardingBookAdapter;
import com.example.storythere.adapters.OnboardingGenreAdapter;
import com.example.storythere.api.ApiClient;
import com.example.storythere.api.ApiService;
import com.example.storythere.api.model.ApiAuthor;
import com.example.storythere.api.model.ApiAudiobook;
import com.example.storythere.api.model.ApiBook;
import com.example.storythere.data.Author;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OnboardingActivity extends AppCompatActivity {
    public static final String ONBOARDING_PREFS = "OnboardingPrefs";
    private static final String KEY_COMPLETED_PREFIX = "completed_";
    private static final String KEY_COMPLETED_AT_PREFIX = "completed_at_";
    private static final String KEY_AUTHOR_IDS_PREFIX = "author_ids_";
    private static final String KEY_AUTHOR_NAMES_PREFIX = "author_names_";
    private static final String KEY_BOOK_IDS_PREFIX = "book_ids_";
    private static final String KEY_BOOK_TITLES_PREFIX = "book_titles_";
    private static final String KEY_GENRES_PREFIX = "genres_";

    private static final int STEP_WELCOME = 0;
    private static final int STEP_AUTHORS = 1;
    private static final int STEP_BOOKS = 2;
    private static final int STEP_GENRES = 3;
    private static final int STEP_DONE = 4;
    private static final int AUTHOR_POOL_SIZE = 100;
    private static final int AUTHORS_TO_SHOW = 10;
    private static final int BOOKS_TO_SHOW = 9;
    private static final int RANDOM_BOOK_POOL_SIZE = 60;
    private static final int AUTHOR_GRID_SPACING_DP = 20;
    private static final int BOOK_GRID_SPACING_DP = 40;
    private static final int GENRE_GRID_SPACING_DP = 8;
    private static final int AUTHOR_RECYCLER_PADDING_START_DP = 18;
    private static final int AUTHOR_RECYCLER_PADDING_TOP_DP = 12;
    private static final int AUTHOR_RECYCLER_PADDING_END_DP = 20;
    private static final int AUTHOR_RECYCLER_PADDING_BOTTOM_DP = 12;
    private static final int BOOK_RECYCLER_PADDING_START_DP = 30;
    private static final int BOOK_RECYCLER_PADDING_TOP_DP = 12;
    private static final int BOOK_RECYCLER_PADDING_END_DP = 14;
    private static final int BOOK_RECYCLER_PADDING_BOTTOM_DP = 12;
    private static final int GENRE_RECYCLER_PADDING_START_DP = 18;
    private static final int GENRE_RECYCLER_PADDING_TOP_DP = 12;
    private static final int GENRE_RECYCLER_PADDING_END_DP = 18;
    private static final int GENRE_RECYCLER_PADDING_BOTTOM_DP = 12;

    private final Handler handler = new Handler();
    private final List<Author> authorOptions = new ArrayList<>();
    private final List<ApiBook> bookOptions = new ArrayList<>();
    private final List<String> genreOptions = new ArrayList<>();
    private final Set<String> selectedAuthorIds = new LinkedHashSet<>();
    private final Set<String> selectedBookIds = new LinkedHashSet<>();
    private final Set<String> selectedGenres = new LinkedHashSet<>();

    private ApiService apiService;
    private TextView stepText;
    private TextView titleText;
    private TextView subtitleText;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private MaterialButton backButton;
    private MaterialButton nextButton;
    private GridSpacingItemDecoration gridSpacingItemDecoration;
    private int currentStep = STEP_WELCOME;
    private boolean authorsLoaded = false;
    private boolean booksLoading = false;
    private boolean initialRecommendationsRequested = false;
    private String booksLoadedForAuthorsKey = "";

    private final String[] popularGenres = {
        "Любовное фэнтези",
        "Современная русская литература",
        "Современные любовные романы",
        "Современные детективы",
        "Попаданцы",
        "Героическое фэнтези",
        "Боевая фантастика",
        "Зарубежные любовные романы",
        "Саморазвитие / личностный рост",
        "Боевое фэнтези",
        "Русская классика",
        "Литература 19 века",
        "Эротические романы",
        "Зарубежные детективы",
        "Триллеры",
        "Книги про волшебников",
        "Остросюжетные любовные романы",
        "Научная фантастика",
        "Мистика"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        getWindow().setStatusBarColor(getResources().getColor(R.color.progress_blue));

        apiService = ApiClient.getApiService();
        initializeViews();
        setupButtons();
        showStep(STEP_WELCOME);
    }

    private void initializeViews() {
        stepText = findViewById(R.id.onboardingStep);
        titleText = findViewById(R.id.onboardingTitle);
        subtitleText = findViewById(R.id.onboardingSubtitle);
        recyclerView = findViewById(R.id.onboardingRecyclerView);
        progressBar = findViewById(R.id.onboardingProgress);
        backButton = findViewById(R.id.onboardingBack);
        nextButton = findViewById(R.id.onboardingNext);
    }

    private void setupButtons() {
        backButton.setOnClickListener(v -> {
            if (currentStep > STEP_WELCOME && currentStep < STEP_DONE) {
                showStep(currentStep - 1);
            }
        });

        nextButton.setOnClickListener(v -> {
            if (currentStep == STEP_GENRES) {
                showStep(STEP_DONE);
                return;
            }

            showStep(currentStep + 1);
        });
    }

    private void showStep(int step) {
        currentStep = step;
        stepText.setText(getString(R.string.onboarding_step_format, step + 1, 5));
        backButton.setVisibility(step == STEP_WELCOME || step == STEP_DONE ? View.INVISIBLE : View.VISIBLE);
        nextButton.setVisibility(step == STEP_DONE ? View.INVISIBLE : View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);

        switch (step) {
            case STEP_WELCOME:
                showWelcomeStep();
                break;
            case STEP_AUTHORS:
                showAuthorsStep();
                break;
            case STEP_BOOKS:
                showBooksStep();
                break;
            case STEP_GENRES:
                showGenresStep();
                break;
            case STEP_DONE:
                showDoneStep();
                break;
        }
        updateNextButtonState();
    }

    private void showWelcomeStep() {
        titleText.setText(R.string.onboarding_welcome_title);
        subtitleText.setText(R.string.onboarding_welcome_subtitle);
        nextButton.setText(R.string.onboarding_start);
    }

    private void showAuthorsStep() {
        titleText.setText(R.string.onboarding_authors_title);
        subtitleText.setText(R.string.onboarding_authors_subtitle);
        nextButton.setText(R.string.onboarding_next);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        setRecyclerPadding(
            AUTHOR_RECYCLER_PADDING_START_DP,
            AUTHOR_RECYCLER_PADDING_TOP_DP,
            AUTHOR_RECYCLER_PADDING_END_DP,
            AUTHOR_RECYCLER_PADDING_BOTTOM_DP
        );
        setGridSpacing(AUTHOR_GRID_SPACING_DP);
        recyclerView.setAdapter(new OnboardingAuthorAdapter(this, authorOptions, selectedAuthorIds, this::toggleAuthor));
        recyclerView.setVisibility(View.VISIBLE);

        if (!authorsLoaded && authorOptions.isEmpty()) {
            loadAuthorOptions();
        }
    }

    private void showBooksStep() {
        titleText.setText(R.string.onboarding_books_title);
        subtitleText.setText(R.string.onboarding_books_subtitle);
        nextButton.setText(R.string.onboarding_next);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        setRecyclerPadding(
            BOOK_RECYCLER_PADDING_START_DP,
            BOOK_RECYCLER_PADDING_TOP_DP,
            BOOK_RECYCLER_PADDING_END_DP,
            BOOK_RECYCLER_PADDING_BOTTOM_DP
        );
        setGridSpacing(BOOK_GRID_SPACING_DP);
        recyclerView.setAdapter(new OnboardingBookAdapter(bookOptions, selectedBookIds, this::toggleBook));
        recyclerView.setVisibility(View.VISIBLE);

        String authorKey = selectedAuthorKey();
        if ((!authorKey.equals(booksLoadedForAuthorsKey) || bookOptions.isEmpty()) && !booksLoading) {
            selectedBookIds.clear();
            bookOptions.clear();
            loadBookOptions(authorKey);
        }
    }

    private void showGenresStep() {
        titleText.setText(R.string.onboarding_genres_title);
        subtitleText.setText(R.string.onboarding_genres_subtitle);
        nextButton.setText(R.string.onboarding_finish);
        prepareGenreOptionsIfNeeded();
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        setRecyclerPadding(
            GENRE_RECYCLER_PADDING_START_DP,
            GENRE_RECYCLER_PADDING_TOP_DP,
            GENRE_RECYCLER_PADDING_END_DP,
            GENRE_RECYCLER_PADDING_BOTTOM_DP
        );
        setGridSpacing(GENRE_GRID_SPACING_DP);
        recyclerView.setAdapter(new OnboardingGenreAdapter(genreOptions, selectedGenres, this::toggleGenre));
        recyclerView.setVisibility(View.VISIBLE);
    }

    private void setRecyclerPadding(int startDp, int topDp, int endDp, int bottomDp) {
        recyclerView.setPadding(
            dpToPx(startDp),
            dpToPx(topDp),
            dpToPx(endDp),
            dpToPx(bottomDp)
        );
    }

    private void setGridSpacing(int spacingDp) {
        if (gridSpacingItemDecoration != null) {
            recyclerView.removeItemDecoration(gridSpacingItemDecoration);
        }
        gridSpacingItemDecoration = new GridSpacingItemDecoration(dpToPx(spacingDp));
        recyclerView.addItemDecoration(gridSpacingItemDecoration);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void showDoneStep() {
        saveOnboardingPreferences();
        requestInitialRecommendations();
        titleText.setText(R.string.onboarding_done_title);
        subtitleText.setText(R.string.onboarding_done_subtitle);
        handler.postDelayed(this::navigateToHome, 900);
    }

    private void requestInitialRecommendations() {
        if (initialRecommendationsRequested) {
            return;
        }
        initialRecommendationsRequested = true;

        apiService.getRecommendedBooks(7).enqueue(new Callback<List<ApiBook>>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiBook>> call, @NonNull Response<List<ApiBook>> response) {
                if (!response.isSuccessful()) {
                    Log.w("OnboardingActivity", "Initial book recommendations request failed: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiBook>> call, @NonNull Throwable t) {
                Log.w("OnboardingActivity", "Initial book recommendations request error", t);
            }
        });

        apiService.getRecommendedAudiobooks(7).enqueue(new Callback<List<ApiAudiobook>>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiAudiobook>> call, @NonNull Response<List<ApiAudiobook>> response) {
                if (!response.isSuccessful()) {
                    Log.w("OnboardingActivity", "Initial audiobook recommendations request failed: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiAudiobook>> call, @NonNull Throwable t) {
                Log.w("OnboardingActivity", "Initial audiobook recommendations request error", t);
            }
        });
    }

    private void updateNextButtonState() {
        nextButton.setEnabled(true);
    }

    private boolean toggleAuthor(Author author) {
        if (author == null || author.getAuthorId() == null) {
            return false;
        }

        String authorId = author.getAuthorId();
        if (selectedAuthorIds.contains(authorId)) {
            selectedAuthorIds.remove(authorId);
            updateNextButtonState();
            return true;
        }

        selectedAuthorIds.add(authorId);
        booksLoadedForAuthorsKey = "";
        updateNextButtonState();
        return true;
    }

    private void toggleBook(ApiBook book) {
        if (book == null) {
            return;
        }
        String bookId = String.valueOf(book.id);
        if (selectedBookIds.contains(bookId)) {
            selectedBookIds.remove(bookId);
        } else {
            selectedBookIds.add(bookId);
        }
    }

    private void toggleGenre(String genre) {
        if (selectedGenres.contains(genre)) {
            selectedGenres.remove(genre);
        } else {
            selectedGenres.add(genre);
        }
    }

    private void loadAuthorOptions() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getAuthors(AUTHOR_POOL_SIZE, 0).enqueue(new Callback<List<ApiAuthor>>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiAuthor>> call, @NonNull Response<List<ApiAuthor>> response) {
                progressBar.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null) {
                    showLoadError();
                    return;
                }

                List<ApiAuthor> pool = new ArrayList<>(response.body());
                Collections.shuffle(pool);
                authorOptions.clear();
                int count = Math.min(AUTHORS_TO_SHOW, pool.size());
                for (int i = 0; i < count; i++) {
                    authorOptions.add(mapApiAuthor(pool.get(i)));
                }
                authorsLoaded = true;
                recyclerView.getAdapter().notifyDataSetChanged();
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiAuthor>> call, @NonNull Throwable t) {
                progressBar.setVisibility(View.GONE);
                showLoadError();
            }
        });
    }

    private void loadBookOptions(String authorKey) {
        booksLoading = true;
        progressBar.setVisibility(View.VISIBLE);
        if (selectedAuthorIds.isEmpty()) {
            loadRandomBooks(authorKey);
            return;
        }

        List<String> authorIds = new ArrayList<>(selectedAuthorIds);
        final int[] completedCalls = {0};
        for (String authorId : authorIds) {
            long parsedAuthorId;
            try {
                parsedAuthorId = Long.parseLong(authorId);
            } catch (NumberFormatException e) {
                completedCalls[0]++;
                continue;
            }

            apiService.getAuthorBooks(parsedAuthorId, BOOKS_TO_SHOW, 0).enqueue(new Callback<List<ApiBook>>() {
                @Override
                public void onResponse(@NonNull Call<List<ApiBook>> call, @NonNull Response<List<ApiBook>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        addUniqueBooks(response.body());
                    }
                    finishAuthorBooksCall(authorIds.size(), ++completedCalls[0], authorKey);
                }

                @Override
                public void onFailure(@NonNull Call<List<ApiBook>> call, @NonNull Throwable t) {
                    finishAuthorBooksCall(authorIds.size(), ++completedCalls[0], authorKey);
                }
            });
        }

        if (completedCalls[0] == authorIds.size()) {
            finishAuthorBooksCall(authorIds.size(), completedCalls[0], authorKey);
        }
    }

    private void finishAuthorBooksCall(int totalCalls, int completedCalls, String authorKey) {
        if (completedCalls < totalCalls) {
            return;
        }

        Collections.shuffle(bookOptions);
        trimBookOptions();
        if (bookOptions.size() < BOOKS_TO_SHOW) {
            loadRandomBooks(authorKey);
            return;
        }

        completeBooksLoading(authorKey);
    }

    private void loadRandomBooks(String authorKey) {
        apiService.getBooks(RANDOM_BOOK_POOL_SIZE, 0).enqueue(new Callback<List<ApiBook>>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiBook>> call, @NonNull Response<List<ApiBook>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ApiBook> pool = new ArrayList<>(response.body());
                    Collections.shuffle(pool);
                    addUniqueBooks(pool);
                    Collections.shuffle(bookOptions);
                    trimBookOptions();
                }
                completeBooksLoading(authorKey);
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiBook>> call, @NonNull Throwable t) {
                completeBooksLoading(authorKey);
            }
        });
    }

    private void completeBooksLoading(String authorKey) {
        booksLoading = false;
        progressBar.setVisibility(View.GONE);
        booksLoadedForAuthorsKey = authorKey;
        if (recyclerView.getAdapter() != null) {
            recyclerView.getAdapter().notifyDataSetChanged();
        }
        if (bookOptions.isEmpty()) {
            Toast.makeText(this, R.string.catalog_books_empty, Toast.LENGTH_SHORT).show();
        }
    }

    private void addUniqueBooks(List<ApiBook> books) {
        Set<String> existingIds = new HashSet<>();
        for (ApiBook book : bookOptions) {
            existingIds.add(String.valueOf(book.id));
        }
        for (ApiBook book : books) {
            String bookId = String.valueOf(book.id);
            if (!existingIds.contains(bookId)) {
                bookOptions.add(book);
                existingIds.add(bookId);
            }
            if (bookOptions.size() >= BOOKS_TO_SHOW) {
                break;
            }
        }
    }

    private void trimBookOptions() {
        while (bookOptions.size() > BOOKS_TO_SHOW) {
            bookOptions.remove(bookOptions.size() - 1);
        }
    }

    private void prepareGenreOptionsIfNeeded() {
        if (!genreOptions.isEmpty()) {
            return;
        }
        int count = Math.min(10, popularGenres.length);
        for (int i = 0; i < count; i++) {
            genreOptions.add(popularGenres[i]);
        }
    }

    private String selectedAuthorKey() {
        List<String> ids = new ArrayList<>(selectedAuthorIds);
        Collections.sort(ids);
        StringBuilder builder = new StringBuilder();
        for (String id : ids) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
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

    private void saveOnboardingPreferences() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : "anonymous";
        SharedPreferences.Editor editor = getSharedPreferences(ONBOARDING_PREFS, MODE_PRIVATE).edit();
        editor.putBoolean(KEY_COMPLETED_PREFIX + uid, true);
        editor.putLong(KEY_COMPLETED_AT_PREFIX + uid, System.currentTimeMillis());
        editor.putStringSet(KEY_AUTHOR_IDS_PREFIX + uid, new HashSet<>(selectedAuthorIds));
        editor.putStringSet(KEY_AUTHOR_NAMES_PREFIX + uid, selectedAuthorNames());
        editor.putStringSet(KEY_BOOK_IDS_PREFIX + uid, new HashSet<>(selectedBookIds));
        editor.putStringSet(KEY_BOOK_TITLES_PREFIX + uid, selectedBookTitles());
        editor.putStringSet(KEY_GENRES_PREFIX + uid, new HashSet<>(selectedGenres));
        editor.apply();
    }

    private Set<String> selectedAuthorNames() {
        Set<String> names = new HashSet<>();
        for (Author author : authorOptions) {
            if (selectedAuthorIds.contains(author.getAuthorId()) && author.getName() != null) {
                names.add(author.getName());
            }
        }
        return names;
    }

    private Set<String> selectedBookTitles() {
        Set<String> titles = new HashSet<>();
        for (ApiBook book : bookOptions) {
            if (selectedBookIds.contains(String.valueOf(book.id)) && book.title != null) {
                titles.add(book.title);
            }
        }
        return titles;
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoadError() {
        Toast.makeText(this, R.string.catalog_load_failed, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (currentStep > STEP_WELCOME && currentStep < STEP_DONE) {
            showStep(currentStep - 1);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    public static boolean isCompleted(Context context, FirebaseUser user) {
        if (user == null) {
            return true;
        }
        SharedPreferences prefs = context.getSharedPreferences(ONBOARDING_PREFS, MODE_PRIVATE);
        return prefs.getBoolean(KEY_COMPLETED_PREFIX + user.getUid(), false);
    }

    private static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private final int spacing;

        GridSpacingItemDecoration(int spacing) {
            this.spacing = spacing;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
            if (!(layoutManager instanceof GridLayoutManager)) {
                return;
            }

            GridLayoutManager gridLayoutManager = (GridLayoutManager) layoutManager;
            int spanCount = gridLayoutManager.getSpanCount();
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            int column = position % spanCount;
            outRect.left = spacing * column / spanCount;
            outRect.right = spacing * (spanCount - 1 - column) / spanCount;
            if (position >= spanCount) {
                outRect.top = spacing;
            }
        }
    }
}
