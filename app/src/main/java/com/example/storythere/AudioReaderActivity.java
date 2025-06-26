package com.example.storythere;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import android.speech.tts.UtteranceProgressListener;
import android.content.SharedPreferences;

public class AudioReaderActivity extends AppCompatActivity {
    private static final String TAG = "AudioReaderActivity";
    private static final int MAX_CHUNK_SIZE = 100; // Maximum words per chunk
    private static final int CHUNK_OVERLAP = 30; // Number of words to overlap between chunks
    private static final int SYNC_INTERVAL = 16; // Sync every 16ms (60fps) for smoother updates
    private static final int QUEUE_AHEAD_THRESHOLD = 50; // Queue next chunk when this many words are left
    private static final float POSITION_UPDATE_FACTOR = 0.1f; // Reduced from 0.5f to make updates more frequent
    private static final float WORDS_PER_MINUTE = 150.0f; // Average reading speed in words per minute
    
    // Progressive chunk sizing
    private static final int FIRST_CHUNK_SIZE = 50;
    private static final int SECOND_CHUNK_SIZE = 50;
    private static final int NORMAL_CHUNK_SIZE = 100;
    private static final int MAX_QUEUED_CHUNKS = 10; // Increased to ensure smoother playback
    
    private int currentChunkIndex = 0; // Track which chunk we're on
    private int queuedChunksCount = 0; // Count of currently queued chunks
    
    private TextToSpeech textToSpeech;
    private String textContent;
    private boolean isPlaying = false;
    private int currentPosition = 0;
    private int totalWords = 0;
    private float currentSpeed = 1.0f;
    private Handler handler = new Handler();
    private Runnable updateProgressRunnable;
    private boolean isTTSReady = false;
    private boolean isPreloading = false;
    private ProgressBar loadingProgressBar;
    private TextView loadingStatusText;
    private int currentChunkStart = 0;
    private String[] words;
    
    // Time tracking variables
    private long playbackStartTime = 0;
    private long totalPlaybackTime = 0; // Total time spent playing in milliseconds
    private long lastPauseTime = 0;
    private boolean isPaused = false;
    
    private ImageButton playPauseButton;
    private ImageButton rewindButton;
    private ImageButton forwardButton;
    private SeekBar progressBar;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private TextView bookTitle;
    private TextView bookAuthor;
    private TextView currentWordsText;
    private boolean isShowingText = false;
    
    // Pattern for detecting Russian characters
    private static final Pattern RUSSIAN_PATTERN = Pattern.compile("[а-яА-ЯёЁ]");
    
    private int lastLoggedPosition = -1;
    
    private boolean ttsIsSpeaking = false;
    private boolean isChunkQueued = false; // Flag to prevent multiple chunk queuing
    
    // Add at the top of the class
    private static final String PREFS_NAME = "AudioReaderPrefs";
    private static final String KEY_POSITION = "position_";
    private static final String KEY_VOICE = "voice_";
    
    private long lastTTSOnDoneTime = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_reader);
        
        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        // Initialize views
        initializeViews();
        
        // Get data from intent
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            Uri contentUri = intent.getData();
            String title = intent.getStringExtra("title");
            String author = intent.getStringExtra("author");
            boolean isRussian = intent.getBooleanExtra("is_russian", false);
            String previewImagePath = intent.getStringExtra("previewImagePath");
            
            if (title != null && getSupportActionBar() != null) {
                if (title.contains(".")) {
                    title = title.substring(0, title.lastIndexOf('.'));
                }
                getSupportActionBar().setTitle(title);
            }
            
            bookTitle.setText(title);
            bookAuthor.setText(author);

            // Load preview image if available
            ImageView bookCoverImage = findViewById(R.id.bookCoverImage);
            if (previewImagePath != null) {
                Glide.with(this)
                    .load(previewImagePath)
                    .placeholder(R.drawable.ic_book_placeholder)
                    .into(bookCoverImage);
            }
            
            // Read and clean text content
            textContent = readTextFromFile(contentUri);
            
            // Calculate total duration based on actual words only
            String[] words = textContent.split("\\s+");
            totalWords = 0;
            for (String word : words) {
                if (!word.trim().isEmpty()) {
                    totalWords++;
                }
            }
            
            // Calculate estimated total time in seconds based on words per minute
            int estimatedTotalSeconds = (int) Math.ceil((totalWords * 60.0f) / WORDS_PER_MINUTE);
            
            progressBar.setMax(100);
            totalTimeText.setText(formatTime(estimatedTotalSeconds));
            currentTimeText.setText(formatTime(0));
            
            // Initialize TTS with the correct language
            initializeTTS(isRussian);
        }
        
        // Setup click listeners
        setupClickListeners();
        
        // Setup progress bar
        setupProgressBar();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.audio_reader_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_playback_speed) {
            showPlaybackSpeedDialog();
            return true;
        } else if (id == R.id.action_voice) {
            showVoiceSelectionDialog();
            return true;
        } else if (id == R.id.action_show_text) {
            toggleTextDisplay();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void showPlaybackSpeedDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_playback_speed, null);
        dialog.setContentView(view);

        RadioGroup speedGroup = view.findViewById(R.id.speedRadioGroup);
        
        // Set current speed
        switch ((int)(currentSpeed * 100)) {
            case 25:
                speedGroup.check(R.id.speed_025);
                break;
            case 50:
                speedGroup.check(R.id.speed_05);
                break;
            case 100:
                speedGroup.check(R.id.speed_1);
                break;
            case 125:
                speedGroup.check(R.id.speed_125);
                break;
            case 150:
                speedGroup.check(R.id.speed_15);
                break;
            case 200:
                speedGroup.check(R.id.speed_2);
                break;
        }

        speedGroup.setOnCheckedChangeListener((group, checkedId) -> {
            float newSpeed = 1.0f;
            if (checkedId == R.id.speed_025) newSpeed = 0.25f;
            else if (checkedId == R.id.speed_05) newSpeed = 0.5f;
            else if (checkedId == R.id.speed_1) newSpeed = 1.0f;
            else if (checkedId == R.id.speed_125) newSpeed = 1.25f;
            else if (checkedId == R.id.speed_15) newSpeed = 1.5f;
            else if (checkedId == R.id.speed_2) newSpeed = 2.0f;

            Log.d(TAG, "Speed: " + newSpeed + "x");

            if (textToSpeech != null) {
                textToSpeech.setSpeechRate(newSpeed);
                currentSpeed = newSpeed;
                
                if (isPlaying) {
                    stopReading();
                    startReading();
                }
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showVoiceSelectionDialog() {
        if (textToSpeech == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_voice_selection, null);
        dialog.setContentView(view);

        RecyclerView recyclerView = view.findViewById(R.id.voiceRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        Set<Voice> allVoices = textToSpeech.getVoices();
        List<Voice> filteredVoices = new ArrayList<>();
        
        // Check if the text is primarily Russian
        boolean isRussian = isTextPrimarilyRussian(textContent);
        
        for (Voice voice : allVoices) {
            String voiceName = voice.getName();
            if (isRussian) {
                // For Russian text, use Russian voices
                if (voiceName.equals("ru-ru-x-ruc-local") ||
                    voiceName.equals("ru-ru-x-ruf-local") ||
                    voiceName.equals("ru-ru-x-rud-network")) {
                    filteredVoices.add(voice);
                }
            } else {
                // For English text, use the specified English voices
                if (voiceName.equals("en-us-x-tpf-local") || // Barbara
                    voiceName.equals("en-au-x-auc-local") || // Batty
                    voiceName.equals("en-gb-x-gbd-local")) { // Oliver
                    filteredVoices.add(voice);
                }
            }
        }

        recyclerView.setAdapter(new VoiceAdapter(filteredVoices, voice -> {
            textToSpeech.setVoice(voice);
            if (isPlaying) {
                stopReading();
                startReading();
            }
            dialog.dismiss();
        }));

        dialog.show();
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
        
        // Initially hide loading UI
        loadingProgressBar.setVisibility(View.GONE);
        loadingStatusText.setVisibility(View.GONE);
    }
    
    private boolean isTextPrimarilyRussian(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // Count Russian characters
        int russianCharCount = 0;
        int totalCharCount = 0;

        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                totalCharCount++;
                if (RUSSIAN_PATTERN.matcher(String.valueOf(c)).matches()) {
                    russianCharCount++;
                }
            }
        }

        // If no letters found, default to English
        if (totalCharCount == 0) {
            return false;
        }

        // Calculate percentage of Russian characters
        double russianPercentage = (double) russianCharCount / totalCharCount;
        
        // If more than 30% of characters are Russian, consider it Russian text
        return russianPercentage > 0.3;
    }
    
    private void initializeTTS(boolean isRussian) {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Set appropriate locale based on the language flag
                Locale selectedLocale = isRussian ? new Locale("ru", "RU") : Locale.US;
                Toast.makeText(this, "Using " + (isRussian ? "Russian" : "English") + " TTS", Toast.LENGTH_SHORT).show();

                int result = textToSpeech.setLanguage(selectedLocale);
                
                if (result == TextToSpeech.LANG_MISSING_DATA) {
                    // Language data is missing, download it
                    Intent installIntent = new Intent();
                    installIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
                    startActivity(installIntent);
                } else if (result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Language is not supported
                    Toast.makeText(this, selectedLocale.getLanguage() + " language is not supported on this device", Toast.LENGTH_LONG).show();
                    // Fallback to default language
                    textToSpeech.setLanguage(Locale.getDefault());
                }

                // Set default voice based on language
                Set<Voice> voices = textToSpeech.getVoices();
                for (Voice voice : voices) {
                    String voiceName = voice.getName();
                    if (isRussian) {
                        if (voiceName.equals("ru-ru-x-ruc-local")) {
                            textToSpeech.setVoice(voice);
                            break;
                        }
                    } else {
                        if (voiceName.equals("en-us-x-tpf-local")) { // Default to Barbara for English
                            textToSpeech.setVoice(voice);
                            break;
                        }
                    }
                }

                // Set default speech rate
                textToSpeech.setSpeechRate(currentSpeed);
                
                // Set up utterance progress listener
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        runOnUiThread(() -> {
                            long currentTime = System.currentTimeMillis();
                            long delay = lastTTSOnDoneTime > 0 ? (currentTime - lastTTSOnDoneTime) : 0;
                            long elapsedPlaybackTime = totalPlaybackTime + (currentTime - playbackStartTime);
                            int currentSeconds = (int) (elapsedPlaybackTime / 1000);
                            Log.d(TAG, "TTS onStart: " + utteranceId + " | Time: " + formatTime(currentSeconds) + (delay > 0 ? (" | Delay since last onDone: " + delay + "ms") : ""));
                            // Only reset playbackStartTime if this is the first chunk (messageID)
                            if (utteranceId.equals("messageID")) {
                                playbackStartTime = System.currentTimeMillis();
                            }
                            isPlaying = true;
                            isPaused = false;
                            ttsIsSpeaking = true;
                            startProgressUpdate();
                        });
                    }
                    @Override
                    public void onDone(String utteranceId) {
                        runOnUiThread(() -> {
                            long currentTime = System.currentTimeMillis();
                            long elapsedPlaybackTime = totalPlaybackTime + (currentTime - playbackStartTime);
                            int currentSeconds = (int) (elapsedPlaybackTime / 1000);
                            Log.d(TAG, "TTS onDone: " + utteranceId + " | Time: " + formatTime(currentSeconds));
                            lastTTSOnDoneTime = System.currentTimeMillis();
                            // Increment chunk index when a chunk is completed
                            currentChunkIndex++;
                            // Update current chunk start position based on previous chunk's end
                            if (utteranceId.startsWith("chunk_")) {
                                String[] parts = utteranceId.split("_");
                                if (parts.length == 3) {
                                    int chunkIndex = Integer.parseInt(parts[1]);
                                    int chunkStart = Integer.parseInt(parts[2]);
                                    int chunkSize = getChunkSize(chunkIndex);
                                    currentChunkStart = chunkStart + chunkSize;
                                }
                            }
                            // Decrement queued chunks count
                            if (queuedChunksCount > 0) {
                                queuedChunksCount--;
                            }
                            // Always queue next batch to ensure smooth playback
                            if (isPlaying) {
                                String[] words = textContent.split("\\s+");
                                // Calculate next start position based on current chunk's end position
                                int nextStart = currentChunkStart; // Start from current chunk's end position
                                // Queue chunks in sequence from current position
                                for (int i = 0; i < MAX_QUEUED_CHUNKS - queuedChunksCount && nextStart < words.length; i++) {
                                    int nextChunkSize = getChunkSize(currentChunkIndex + i);
                                    StringBuilder chunkText = new StringBuilder();
                                    // Add overlap words from previous chunk
                                    if (i > 0) {
                                        int overlapStart = Math.max(0, nextStart - CHUNK_OVERLAP);
                                        for (int j = overlapStart; j < nextStart; j++) {
                                            String word = words[j].trim();
                                            if (!word.isEmpty() && isValidWord(word)) {
                                                chunkText.append(word).append(" ");
                                            }
                                        }
                                    }
                                    // Add current chunk words
                                    int nextEnd = Math.min(nextStart + nextChunkSize, words.length);
                                    for (int j = nextStart; j < nextEnd; j++) {
                                        String word = words[j].trim();
                                        if (!word.isEmpty() && isValidWord(word)) {
                                            chunkText.append(word).append(" ");
                                        }
                                    }
                                    String nextTextToSpeak = chunkText.toString().trim();
                                    if (!nextTextToSpeak.isEmpty()) {
                                        Bundle nextParams = new Bundle();
                                        String nextUtteranceId = "chunk_" + (currentChunkIndex + i) + "_" + nextStart;
                                        nextParams.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, nextUtteranceId);
                                        Log.d(TAG, "Queueing chunk " + (currentChunkIndex + i) + " at position " + nextStart + " with size " + nextChunkSize);
                                        int nextResult = textToSpeech.speak(nextTextToSpeak, TextToSpeech.QUEUE_ADD, nextParams, nextUtteranceId);
                                        if (nextResult == TextToSpeech.ERROR) {
                                            Log.e(TAG, "Error queueing chunk " + (currentChunkIndex + i));
                                        } else {
                                            queuedChunksCount++;
                                        }
                                    }
                                    nextStart = nextEnd;
                                }
                            }
                            // If no more chunks and we're at the end, stop
                            if (queuedChunksCount == 0) {
                                stopProgressUpdate();
                                isPlaying = false;
                                isPaused = false;
                                ttsIsSpeaking = false;
                            }
                        });
                    }
                    @Override
                    public void onError(String utteranceId) {
                        runOnUiThread(() -> {
                            Log.d(TAG, "TTS onError: " + utteranceId);
                            stopProgressUpdate();
                            isPlaying = false;
                            isPaused = false;
                            ttsIsSpeaking = false;
                        });
                    }
                });

                isTTSReady = true;
                Log.d(TAG, "TTS initialized successfully");
                
                // In initializeTTS, after TTS is ready and voices are set, call restoreCache()
                restoreCache();
            } else {
                Log.e(TAG, "TTS initialization failed with status: " + status);
                Toast.makeText(this, "Failed to initialize text-to-speech", Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void setupClickListeners() {
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        
        rewindButton.setOnClickListener(v -> {
            if (textToSpeech != null) {
                Log.d(TAG, "Rewind: " + currentPosition);
                stopReading();
                // Skip back 10 seconds worth of words
                float wordsPerSecond = (WORDS_PER_MINUTE * 1.0f) / 60.0f; // Always use 1.0x for time calculation
                int skipWords = (int)(10 * wordsPerSecond);
                int newPosition = Math.max(0, currentPosition - skipWords);
                
                currentPosition = newPosition;
                
                // Calculate the correct time for this position
                long expectedTimeForPosition = (long)((newPosition * 1000.0f) / wordsPerSecond);
                totalPlaybackTime = expectedTimeForPosition;
                
                updateProgressBar();
                startReading();
            }
        });
        
        forwardButton.setOnClickListener(v -> {
            if (textToSpeech != null) {
                Log.d(TAG, "Forward: " + currentPosition);
                stopReading();
                // Skip forward 10 seconds worth of words
                float wordsPerSecond = (WORDS_PER_MINUTE * 1.0f) / 60.0f; // Always use 1.0x for time calculation
                int skipWords = (int)(10 * wordsPerSecond);
                String[] words = textContent.split("\\s+");
                int newPosition = Math.min(words.length - 1, currentPosition + skipWords);
                
                currentPosition = newPosition;
                
                // Calculate the correct time for this position
                long expectedTimeForPosition = (long)((newPosition * 1000.0f) / wordsPerSecond);
                totalPlaybackTime = expectedTimeForPosition;
                
                updateProgressBar();
                startReading();
            }
        });
    }
    
    private void setupProgressBar() {
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    Log.d(TAG, "Seek: " + currentPosition + " -> " + progress + "%");
                    // Map percent to word index
                    int targetPosition = (int)((progress / 100.0f) * totalWords);
                    targetPosition = Math.max(0, Math.min(targetPosition, totalWords - 1));
                    currentPosition = targetPosition;
                    float wordsPerSecond = (WORDS_PER_MINUTE * 1.0f) / 60.0f;
                    long expectedTimeForPosition = (long)((currentPosition * 1000.0f) / wordsPerSecond);
                    totalPlaybackTime = expectedTimeForPosition;
                    // Fix: Reset playbackStartTime if not playing, so time calculation is correct
                    if (!isPlaying) {
                        playbackStartTime = System.currentTimeMillis();
                    }
                    updateProgressBar();
                    // Log the current time after updating the UI
                    Log.d(TAG, "SeekBar currentTimeText: " + currentTimeText.getText().toString());
                    // Update play/pause button state to reflect isPlaying
                    if (isPlaying) {
                        playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                        playPauseButton.setPadding(0, 0, 0, 0);
                    } else {
                        playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                        int padding = (int) (getResources().getDisplayMetrics().density * 3); // 3dp
                        playPauseButton.setPadding(padding, 0, 0, 0);
                    }
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (textToSpeech != null) {
                    textToSpeech.stop();
                    startReading();
                }
            }
        });
    }
    
    private void togglePlayPause() {
        if (!isTTSReady) {
            startPreloading();
            return;
        }
        
        if (isPlaying) {
            Log.d(TAG, "Pausing playback");
            // Pause TTS
            if (textToSpeech != null) {
                textToSpeech.stop();
            }
            
            // Update total playback time
            if (!isPaused) {
                long currentTime = System.currentTimeMillis();
                totalPlaybackTime += (currentTime - playbackStartTime);
                lastPauseTime = currentTime;
                isPaused = true;
            }
            
            // Immediately set button to play state
            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
            // Adjust padding for the play icon when stopping
            int padding = (int) (getResources().getDisplayMetrics().density * 3); // 3dp
            playPauseButton.setPadding(padding, 0, 0, 0);
            isPlaying = false; // Update state immediately
        } else {
            Log.d(TAG, "Starting/resuming playback");
            // Resume or start reading
            if (isPaused) {
                // Resume from pause
                playbackStartTime = System.currentTimeMillis();
                isPaused = false;
            }
            startReading();
            // Immediately set button to pause state
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
            // Adjust padding for the pause icon when starting
            playPauseButton.setPadding(0, 0, 0, 0);
            isPlaying = true; // Update state immediately
        }
    }
    
    private void startReading() {
        if (textToSpeech != null && isTTSReady) {
            // Reset any existing progress updates
            if (updateProgressRunnable != null) {
                handler.removeCallbacks(updateProgressRunnable);
                updateProgressRunnable = null;
            }
            // Reset chunk queuing flag and tracking
            isChunkQueued = false;
            currentChunkIndex = 0;
            queuedChunksCount = 0;
            // Initialize time tracking
            if (!isPlaying) {
                isPaused = false;
            }
            String[] words = textContent.split("\\s+");
            int startWord = Math.min((int)currentPosition, words.length - 1);
            int chunkSize = FIRST_CHUNK_SIZE; // Always use 50 words for starting chunk
            int endWord = Math.min(startWord + chunkSize, words.length);
            StringBuilder remainingText = new StringBuilder();
            for (int i = startWord; i < endWord; i++) {
                String word = words[i].trim();
                if (!word.isEmpty() && isValidWord(word)) {
                    remainingText.append(word).append(" ");
                }
            }
            String textToSpeak = remainingText.toString().trim();
            if (!textToSpeak.isEmpty()) {
                try {
                    textToSpeech.stop();
                    currentPosition = startWord;
                    queuedChunksCount = 1; // First chunk is queued
                    Bundle params = new Bundle();
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "messageID");
                    int result = textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "messageID");
                    if (result == TextToSpeech.ERROR) {
                        Log.e(TAG, "Error starting TTS");
                        Toast.makeText(this, "Error starting text-to-speech", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Immediately queue the next N chunks for seamless playback
                    int nextStart = endWord;
                    for (int i = 0; i < MAX_QUEUED_CHUNKS - 1 && nextStart < words.length; i++) {
                        int nextChunkSize = getChunkSize(i); // Use progressive chunk sizing
                        int nextEnd = Math.min(nextStart + nextChunkSize, words.length);
                        StringBuilder chunkText = new StringBuilder();
                        for (int j = nextStart; j < nextEnd; j++) {
                            String word = words[j].trim();
                            if (!word.isEmpty() && isValidWord(word)) {
                                chunkText.append(word).append(" ");
                            }
                        }
                        String nextTextToSpeak = chunkText.toString().trim();
                        if (!nextTextToSpeak.isEmpty()) {
                            Bundle nextParams = new Bundle();
                            String nextUtteranceId = "chunk_" + (i) + "_" + nextStart;
                            nextParams.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, nextUtteranceId);
                            Log.d(TAG, "Queueing chunk " + (i) + " at position " + nextStart + " with size " + nextChunkSize );
                            int nextResult = textToSpeech.speak(nextTextToSpeak, TextToSpeech.QUEUE_ADD, nextParams, nextUtteranceId);
                            if (nextResult == TextToSpeech.ERROR) {
                                Log.e(TAG, "Error queueing chunk " + (i));
                            } else {
                                queuedChunksCount++;
                            }
                        }
                        nextStart = nextEnd;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception while starting TTS: " + e.getMessage());
                    Toast.makeText(this, "Error starting text-to-speech", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Log.e(TAG, "Cannot start reading: TTS not ready");
            Toast.makeText(this, "Text-to-speech is not ready", Toast.LENGTH_SHORT).show();
        }
    }
    
    private int getChunkSize(int chunkIndex) {
        if (chunkIndex == 0) {
            return SECOND_CHUNK_SIZE; // 50 words for first queued chunk
        } else {
            return NORMAL_CHUNK_SIZE; // 200 words for all other chunks
        }
    }
    
    private void startProgressUpdate() {
        updateProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPlaying && !isPaused) {
                    // Calculate current time based on actual elapsed playback time
                    long currentTime = System.currentTimeMillis();
                    long elapsedPlaybackTime = totalPlaybackTime + (currentTime - playbackStartTime);
                    
                    // Calculate current position based on elapsed time at 1.0x speed (real time)
                    float wordsPerSecond = (WORDS_PER_MINUTE * 1.0f) / 60.0f;
                    float newPosition = (elapsedPlaybackTime * wordsPerSecond) / 1000.0f;

                    // Ensure we don't exceed the total words
                    String[] words = textContent.split("\\s+");
                    if (newPosition >= words.length) {
                        newPosition = words.length - 1;
                        stopReading();
                        return;
                    }

                    // Update position and progress bar smoothly
                    int currentPos = (int)newPosition;
                    if (currentPos != currentPosition) {
                        synchronized (this) {
                            currentPosition = currentPos;
                        }
                    }
                    
                    handler.postDelayed(this, SYNC_INTERVAL);
                        }
                    }
        };
        handler.post(updateProgressRunnable);
        
        // Start separate smooth progress updates
        startSmoothProgressUpdate();
    }
    
    private void startSmoothProgressUpdate() {
        Runnable smoothProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPlaying && !isPaused) {
                    updateProgressBar();
                    handler.postDelayed(this, 50); // Update every 50ms for smooth progress
                }
            }
        };
        handler.post(smoothProgressRunnable);
    }
    
    private void stopProgressUpdate() {
        if (updateProgressRunnable != null) {
            handler.removeCallbacks(updateProgressRunnable);
        }
        // Remove any pending smooth progress updates
        handler.removeCallbacksAndMessages(null);
    }
    
    private void updateProgressBar() {
        runOnUiThread(() -> {
            long currentTime = System.currentTimeMillis();
            // Scale elapsed time by playback speed
            float speed = currentSpeed > 0 ? currentSpeed : 1.0f;
            long elapsedPlaybackTime = totalPlaybackTime + (currentTime - playbackStartTime);
            float currentSeconds = (elapsedPlaybackTime / 1000.0f) * speed; // scale by speed
            float wordsPerSecond = (WORDS_PER_MINUTE * 1.0f) / 60.0f;
            float totalSeconds = totalWords / wordsPerSecond;
            int progress = totalWords > 0 ? (int)((currentPosition * 100.0f) / totalWords) : 0;
            progressBar.setMax(100);
            progressBar.setProgress(progress);
            currentTimeText.setText(formatTime((int)currentSeconds));
            if (isShowingText) {
                updateCurrentWords();
            }
        });
    }
    
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    private String readTextFromFile(Uri uri) {
        StringBuilder text = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Remove HTML entities like &#160; (non-breaking space)
                String cleanedLine = line.replaceAll("&#\\d+;", " ");
                // Clean the line by removing special characters and extra spaces
                cleanedLine = cleanedLine.trim()
                    .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "") // Remove control characters except newlines and tabs
                    .replaceAll("\\s+", " ") // Replace multiple spaces with single space
                    .replaceAll("[^\\p{L}\\p{N}\\p{P}\\s]", ""); // Keep only letters, numbers, punctuation and spaces
                
                if (!cleanedLine.isEmpty()) {
                    text.append(cleanedLine).append(" ");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading file: " + e.getMessage());
            e.printStackTrace();
        }
        return text.toString().trim();
    }
    
    private boolean isValidWord(String word) {
        // Check if word contains only valid characters
        return word.matches("[\\p{L}\\p{N}\\p{P}]+");
    }
    
    private void toggleTextDisplay() {
        isShowingText = !isShowingText;
        currentWordsText.setVisibility(isShowingText ? View.VISIBLE : View.GONE);
        if (isShowingText) {
            // Force update the text display
            updateCurrentWords();
        }
    }

    private void updateCurrentWords() {
        if (!isShowingText) return;
        
        String[] words = textContent.split("\\s+");
        
        // Use the actual current position (what TTS is actually speaking)
        // The currentPosition is already updated based on TTS progress
        int currentPos = currentPosition;
        
        // Ensure bounds
        currentPos = Math.max(0, Math.min(currentPos, words.length - 1));
        
        // Only log when position changes (not on every update)
        if (currentPos != lastLoggedPosition) {
            String dictatedWord = currentPos < words.length ? words[currentPos] : "END";
            String shownWord = currentPos < words.length ? words[currentPos] : "END";
            long currentTime = System.currentTimeMillis();
            long elapsedPlaybackTime = totalPlaybackTime + (currentTime - playbackStartTime);
            int currentSeconds = (int) (elapsedPlaybackTime / 1000);
            

        }
        
        int startWord = Math.max(0, currentPos - 2); // Show 2 words before current position
        int endWord = Math.min(words.length, startWord + 7); // Show 7 words total (2 before + current + 4 after)
        
        StringBuilder currentWords = new StringBuilder();
        for (int i = startWord; i < endWord; i++) {
            if (!words[i].trim().isEmpty() && isValidWord(words[i])) {
                // Highlight the current word being spoken
                if (i == currentPos) {
                    currentWords.append("<b>").append(words[i]).append("</b> ");
                } else {
                    currentWords.append(words[i]).append(" ");
                }
            }
        }
        
        String displayText = currentWords.toString().trim();
        currentWordsText.setText(Html.fromHtml(displayText, Html.FROM_HTML_MODE_LEGACY));
    }
    
    private void startPreloading() {
        if (isPreloading || isTTSReady) return;
        
        isPreloading = true;
        loadingProgressBar.setVisibility(View.VISIBLE);
        loadingStatusText.setVisibility(View.VISIBLE);
        playPauseButton.setEnabled(false);
        
        // Start preloading in background
        new Thread(() -> {
            String[] words = textContent.split("\\s+");
            int totalWords = words.length;
            int processedWords = 0;
            
            for (int i = 0; i < totalWords; i += MAX_CHUNK_SIZE) {
                if (!isPreloading) break; // Stop if preloading was cancelled
                
                int end = Math.min(i + MAX_CHUNK_SIZE, totalWords);
                StringBuilder chunk = new StringBuilder();
                for (int j = i; j < end; j++) {
                    if (!words[j].trim().isEmpty() && isValidWord(words[j])) {
                        chunk.append(words[j]).append(" ");
                    }
                }
                
                // Update progress
                processedWords = end;
                int progress = (processedWords * 100) / totalWords;
                runOnUiThread(() -> {
                    loadingProgressBar.setProgress(progress);
                    loadingStatusText.setText("Preparing audio: " + progress + "%");
                });
                
                // Simulate processing time
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            runOnUiThread(() -> {
                isPreloading = false;
                isTTSReady = true;
                loadingProgressBar.setVisibility(View.GONE);
                loadingStatusText.setVisibility(View.GONE);
                playPauseButton.setEnabled(true);
                Toast.makeText(this, "Ready to play", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }
    
    private void stopReading() {
        if (textToSpeech != null) {
            Log.d(TAG, "Stopping TTS");
            textToSpeech.stop();
            
            // Update total playback time
            if (isPlaying && !isPaused) {
                long currentTime = System.currentTimeMillis();
                totalPlaybackTime += (currentTime - playbackStartTime);
            }
            
            isPlaying = false;
            isPaused = false;
            isChunkQueued = false; // Reset chunk queuing flag
            currentChunkIndex = 0; // Reset chunk index
            queuedChunksCount = 0; // Reset queued chunks count
            stopProgressUpdate();
            // Reset position tracking
            if (updateProgressRunnable != null) {
                handler.removeCallbacks(updateProgressRunnable);
                updateProgressRunnable = null;
            }
            // Ensure position is updated one last time
            updateProgressBar();
            // Set the button to play state
            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
            // Adjust padding for the play icon when stopping
            int padding = (int) (getResources().getDisplayMetrics().density * 3); // 3dp
            playPauseButton.setPadding(padding, 0, 0, 0);
        }
    }
    
    @Override
    protected void onDestroy() {
        isPreloading = false; // Stop preloading if activity is destroyed
        Log.d(TAG, "Activity being destroyed");
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        stopProgressUpdate();
        // Reset time tracking
        totalPlaybackTime = 0;
        isPaused = false;
        super.onDestroy();
    }

    private void saveCache() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String uniqueKey = getUniqueKey();
        editor.putInt(KEY_POSITION + uniqueKey, currentPosition);
        if (textToSpeech != null && textToSpeech.getVoice() != null) {
            editor.putString(KEY_VOICE + uniqueKey, textToSpeech.getVoice().getName());
        }
        editor.apply();
    }

    private void restoreCache() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String uniqueKey = getUniqueKey();
        currentPosition = prefs.getInt(KEY_POSITION + uniqueKey, 0);
        // Set totalPlaybackTime to match restored position
        float wordsPerSecond = (WORDS_PER_MINUTE * 1.0f) / 60.0f;
        totalPlaybackTime = (long)((currentPosition * 1000.0f) / wordsPerSecond);
        int restoredSeconds = (int)(currentPosition / wordsPerSecond);
        String savedVoice = prefs.getString(KEY_VOICE + uniqueKey, null);
        Log.d(TAG, "[CACHE] Restored position: " + currentPosition + " (" + formatTime(restoredSeconds) + ")");
        Log.d(TAG, "[CACHE] Restored voice: " + savedVoice);
        if (savedVoice != null && textToSpeech != null) {
            for (Voice voice : textToSpeech.getVoices()) {
                if (voice.getName().equals(savedVoice)) {
                    textToSpeech.setVoice(voice);
                    break;
                }
            }
        }
        // Update UI to show correct current time
        if (currentTimeText != null) {
            currentTimeText.setText(formatTime(restoredSeconds));
        }
        if (progressBar != null && totalWords > 0) {
            int progress = (int)((currentPosition * 100.0f) / totalWords);
            progressBar.setProgress(progress);
        }
    }

    private String getUniqueKey() {
        return (bookTitle != null && bookTitle.getText() != null) ? bookTitle.getText().toString() : "default";
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCache();
    }
} 