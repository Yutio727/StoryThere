package com.example.storythere.listening;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.example.storythere.R;
import com.example.storythere.data.RecommendationTrackingRepository;

import java.io.IOException;
import java.util.Locale;

public class AudiobookPlayerActivity extends AppCompatActivity {
    public static final String EXTRA_AUDIOBOOK_ID = "audiobookId";
    public static final String EXTRA_AUDIO_URL = "audioUrl";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_AUTHOR = "author";
    public static final String EXTRA_PREVIEW_IMAGE_PATH = "previewImagePath";
    public static final String EXTRA_DURATION_SECONDS = "durationSeconds";
    public static final String EXTRA_START_POSITION_MS = "start_position_ms";
    public static final String EXTRA_FROM_RECOMMENDATION = "fromRecommendation";

    private static final String TAG = "AudiobookPlayerActivity";
    private static final String PREFS_NAME = "AudiobookPlayerPrefs";
    private static final String KEY_POSITION_MS = "position_ms_";
    private static final int SEEK_STEP_MS = 10_000;
    private static final int PROGRESS_UPDATE_MS = 500;
    private static final int PROGRESS_SYNC_INTERVAL_MS = 15_000;

    private MediaPlayer mediaPlayer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;

    private ImageButton playPauseButton;
    private ImageButton rewindButton;
    private ImageButton forwardButton;
    private SeekBar progressBar;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private TextView bookTitle;
    private TextView bookAuthor;
    private TextView currentWordsText;
    private ProgressBar loadingProgressBar;
    private TextView loadingStatusText;
    private RecommendationTrackingRepository trackingRepository;

    private Uri audioUri;
    private long audiobookId = -1L;
    private boolean fromRecommendation = false;
    private String title;
    private String author;
    private String cacheKey;
    private int durationMs = 0;
    private boolean isPrepared = false;
    private boolean isUserSeeking = false;
    private long lastProgressSyncAtMs = 0L;
    private boolean completionTracked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_reader);

        setupToolbar();
        initializeViews();
        trackingRepository = new RecommendationTrackingRepository();
        readIntent();
        bindMetadata();
        setupClickListeners();
        setupProgressBar();
        preparePlayer();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void initializeViews() {
        playPauseButton = findViewById(R.id.playPauseButton);
        rewindButton = findViewById(R.id.rewindButton);
        forwardButton = findViewById(R.id.forwardButton);
        progressBar = findViewById(R.id.progressBar);
        currentTimeText = findViewById(R.id.currentTime);
        totalTimeText = findViewById(R.id.totalTime);
        bookTitle = findViewById(R.id.bookTitle);
        bookAuthor = findViewById(R.id.bookAuthor);
        currentWordsText = findViewById(R.id.currentWordsText);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        loadingStatusText = findViewById(R.id.loadingStatusText);

        currentWordsText.setVisibility(View.GONE);
        playPauseButton.setEnabled(false);
        rewindButton.setEnabled(false);
        forwardButton.setEnabled(false);
        progressBar.setEnabled(false);
        progressBar.setMax(100);
        currentTimeText.setText(formatTime(0));
        totalTimeText.setText(formatTime(0));
        showLoading(true);
    }

    private void readIntent() {
        Intent intent = getIntent();
        audioUri = intent != null ? intent.getData() : null;
        if (audioUri == null && intent != null && intent.getStringExtra(EXTRA_AUDIO_URL) != null) {
            audioUri = Uri.parse(intent.getStringExtra(EXTRA_AUDIO_URL));
        }

        title = intent != null ? intent.getStringExtra(EXTRA_TITLE) : null;
        author = intent != null ? intent.getStringExtra(EXTRA_AUTHOR) : null;
        if (title == null || title.trim().isEmpty()) {
            title = getString(R.string.book_title);
        }
        if (author == null || author.trim().isEmpty()) {
            author = getString(R.string.unknown_author);
        }

        audiobookId = intent != null ? intent.getLongExtra(EXTRA_AUDIOBOOK_ID, -1L) : -1L;
        fromRecommendation = intent != null && intent.getBooleanExtra(EXTRA_FROM_RECOMMENDATION, false);
        cacheKey = audiobookId > 0 ? String.valueOf(audiobookId) : String.valueOf(audioUri);

        int durationSeconds = intent != null ? intent.getIntExtra(EXTRA_DURATION_SECONDS, 0) : 0;
        if (durationSeconds > 0) {
            durationMs = durationSeconds * 1000;
            progressBar.setMax(durationMs);
            totalTimeText.setText(formatTime(durationSeconds));
        }
    }

    private void bindMetadata() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
        bookTitle.setText(title);
        bookAuthor.setText(author);

        String imagePath = getIntent() != null ? getIntent().getStringExtra(EXTRA_PREVIEW_IMAGE_PATH) : null;
        ImageView bookCoverImage = findViewById(R.id.bookCoverImage);
        if (imagePath != null && !imagePath.trim().isEmpty()) {
            Glide.with(this)
                .load(imagePath)
                .placeholder(R.drawable.ic_book_placeholder)
                .error(R.drawable.ic_book_placeholder)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .into(bookCoverImage);
        } else {
            bookCoverImage.setImageResource(R.drawable.ic_book_placeholder);
        }
    }

    private void setupClickListeners() {
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        rewindButton.setOnClickListener(v -> seekBy(-SEEK_STEP_MS));
        forwardButton.setOnClickListener(v -> seekBy(SEEK_STEP_MS));
    }

    private void setupProgressBar() {
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTimeText.setText(formatTime(progress / 1000));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mediaPlayer != null && isPrepared) {
                    mediaPlayer.seekTo(seekBar.getProgress());
                    currentTimeText.setText(formatTime(seekBar.getProgress() / 1000));
                    savePosition(seekBar.getProgress());
                    syncAudiobookProgress(seekBar.getProgress(), true);
                }
                isUserSeeking = false;
            }
        });
    }

    private void preparePlayer() {
        if (audioUri == null) {
            showLoading(false);
            Toast.makeText(this, R.string.no_content_provided, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(
            new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        );
        mediaPlayer.setOnPreparedListener(mp -> {
            isPrepared = true;
            showLoading(false);
            enableControls(true);

            int playerDuration = mp.getDuration();
            if (playerDuration > 0) {
                durationMs = playerDuration;
                progressBar.setMax(durationMs);
                totalTimeText.setText(formatTime(durationMs / 1000));
            }

            int startPosition = initialPositionMs();
            if (startPosition > 0 && durationMs > 0) {
                mp.seekTo(Math.min(startPosition, durationMs));
            }
            updateProgress();
            Toast.makeText(this, R.string.ready_to_play, Toast.LENGTH_SHORT).show();
        });
        mediaPlayer.setOnCompletionListener(mp -> {
            stopProgressUpdates();
            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
            progressBar.setProgress(durationMs > 0 ? durationMs : progressBar.getMax());
            currentTimeText.setText(formatTime((durationMs > 0 ? durationMs : progressBar.getMax()) / 1000));
            syncAudiobookProgress(durationMs > 0 ? durationMs : progressBar.getMax(), true);
            trackAudiobookCompletion();
            savePosition(0);
        });
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error. what=" + what + ", extra=" + extra);
            showLoading(false);
            enableControls(false);
            Toast.makeText(this, R.string.error_launching_audio_reader, Toast.LENGTH_SHORT).show();
            return true;
        });

        try {
            mediaPlayer.setDataSource(this, audioUri);
            mediaPlayer.prepareAsync();
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            Log.e(TAG, "Failed to prepare audiobook: " + audioUri, e);
            showLoading(false);
            enableControls(false);
            Toast.makeText(this, R.string.error_launching_audio_reader, Toast.LENGTH_SHORT).show();
        }
    }

    private int initialPositionMs() {
        Intent intent = getIntent();
        int explicitStart = intent != null ? intent.getIntExtra(EXTRA_START_POSITION_MS, -1) : -1;
        if (explicitStart >= 0) {
            return explicitStart;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getInt(KEY_POSITION_MS + cacheKey, 0);
    }

    private void togglePlayPause() {
        if (mediaPlayer == null || !isPrepared) {
            return;
        }
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
            stopProgressUpdates();
            saveCurrentPosition();
        } else {
            mediaPlayer.start();
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
            startProgressUpdates();
        }
    }

    private void seekBy(int deltaMs) {
        if (mediaPlayer == null || !isPrepared) {
            return;
        }
        int duration = durationMs > 0 ? durationMs : mediaPlayer.getDuration();
        int target = Math.max(0, Math.min(mediaPlayer.getCurrentPosition() + deltaMs, duration));
        mediaPlayer.seekTo(target);
        progressBar.setProgress(target);
        currentTimeText.setText(formatTime(target / 1000));
        savePosition(target);
        syncAudiobookProgress(target, true);
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                updateProgress();
                handler.postDelayed(this, PROGRESS_UPDATE_MS);
            }
        };
        handler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        if (progressRunnable != null) {
            handler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    private void updateProgress() {
        if (mediaPlayer == null || !isPrepared || isUserSeeking) {
            return;
        }
        int position = mediaPlayer.getCurrentPosition();
        progressBar.setProgress(position);
        currentTimeText.setText(formatTime(position / 1000));
        syncAudiobookProgress(position, false);
    }

    private void showLoading(boolean show) {
        loadingProgressBar.setIndeterminate(show);
        loadingProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        loadingStatusText.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            loadingStatusText.setText(R.string.loading_content);
        }
    }

    private void enableControls(boolean enabled) {
        playPauseButton.setEnabled(enabled);
        rewindButton.setEnabled(enabled);
        forwardButton.setEnabled(enabled);
        progressBar.setEnabled(enabled);
    }

    private void saveCurrentPosition() {
        if (mediaPlayer != null && isPrepared) {
            int positionMs = mediaPlayer.getCurrentPosition();
            savePosition(positionMs);
            syncAudiobookProgress(positionMs, true);
        }
    }

    private void savePosition(int positionMs) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putInt(KEY_POSITION_MS + cacheKey, positionMs)
            .apply();
    }

    private void syncAudiobookProgress(int positionMs, boolean force) {
        if (trackingRepository == null || audiobookId <= 0) {
            return;
        }
        int duration = durationMs;
        if (duration <= 0 && mediaPlayer != null && isPrepared) {
            duration = mediaPlayer.getDuration();
        }
        if (duration <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastProgressSyncAtMs < PROGRESS_SYNC_INTERVAL_MS) {
            return;
        }
        lastProgressSyncAtMs = now;
        double progress = Math.max(0.0, Math.min(100.0, (positionMs * 100.0) / duration));
        trackingRepository.trackAudiobookProgress(audiobookId, progress, Math.max(0, positionMs));
    }

    private void trackAudiobookCompletion() {
        if (completionTracked) {
            return;
        }
        completionTracked = true;
        if (fromRecommendation && audiobookId > 0) {
            trackingRepository.trackAudiobookEvent(
                audiobookId,
                RecommendationTrackingRepository.EVENT_COMPLETION,
                null,
                null,
                100.0
            );
        }
    }

    private String formatTime(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, secs);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrentPosition();
    }

    @Override
    protected void onDestroy() {
        saveCurrentPosition();
        stopProgressUpdates();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}
