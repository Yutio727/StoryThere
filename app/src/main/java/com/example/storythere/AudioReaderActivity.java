package com.example.storythere;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.LocaleList;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class AudioReaderActivity extends AppCompatActivity {
    private static final String TAG = "AudioReaderActivity";
    private static final int MAX_CHUNK_SIZE = 200; // Maximum words per chunk
    private static final int CHUNK_OVERLAP = 20; // Number of words to overlap between chunks
    private static final int SYNC_INTERVAL = 50; // Reduced from 100ms to 50ms for smoother updates
    private static final int QUEUE_AHEAD_THRESHOLD = 50;
    private static final float POSITION_UPDATE_FACTOR = 0.1f; // Reduced from 0.5f to make updates more frequent
    private TextToSpeech textToSpeech;
    private String textContent;
    private boolean isPlaying = false;
    private int currentPosition = 0;
    private int totalDuration = 0;
    private float currentSpeed = 1.0f;
    private Handler handler = new Handler();
    private Runnable updateProgressRunnable;
    private boolean isTTSReady = false;
    private boolean isPreloading = false;
    private ProgressBar loadingProgressBar;
    private TextView loadingStatusText;
    private int currentChunkStart = 0;
    private String[] words;
    
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
                // Remove file extension from title
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
            totalDuration = 0;
            for (String word : words) {
                if (!word.trim().isEmpty()) {
                    totalDuration++;
                }
            }
            
            progressBar.setMax(totalDuration);
            totalTimeText.setText(formatTime(totalDuration));
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
        
        // Add only the three specific Russian voices
        for (Voice voice : allVoices) {
            String voiceName = voice.getName();
            if (voiceName.equals("ru-ru-x-ruc-local") || 
                voiceName.equals("ru-ru-x-ruf-local") || 
                voiceName.equals("ru-ru-x-rud-network")) {
                filteredVoices.add(voice);
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

                // Set default speech rate
                textToSpeech.setSpeechRate(currentSpeed);
                
                // Set up utterance progress listener
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    private int wordCount = 0;
                    
                    @Override
                    public void onStart(String utteranceId) {
                        Log.d(TAG, "TTS started for utterance: " + utteranceId);
                        wordCount = 0;
                    }
                    
                    @Override
                    public void onDone(String utteranceId) {
                        Log.d(TAG, "TTS completed for utterance: " + utteranceId);
                        runOnUiThread(() -> {
                            String[] words = textContent.split("\\s+");
                            
                            if (utteranceId.startsWith("chunk_")) {
                                // Extract the position from the chunk ID
                                int chunkPosition = Integer.parseInt(utteranceId.substring(6));
                                currentPosition = chunkPosition;
                                
                                if (currentPosition >= words.length - 1) {
                                    // We've reached the end
                                    playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                                    // Adjust padding for the play icon
                                    int padding = (int) (getResources().getDisplayMetrics().density * 3); // 3dp
                                    playPauseButton.setPadding(padding, 0, 0, 0);
                                    isPlaying = false;
                                    currentPosition = 0;
                                } else {
                                     // For chunks, ensure the button is still showing pause if playing
                                     if (isPlaying) {
                                         playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                                         playPauseButton.setPadding(0, 0, 0, 0);
                                     }
                                }
                            } else {
                                // Handle main utterance completion
                                if (currentPosition >= words.length - 1) {
                                    playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                                     // Adjust padding for the play icon
                                    int padding = (int) (getResources().getDisplayMetrics().density * 3); // 3dp
                                    playPauseButton.setPadding(padding, 0, 0, 0);
                                    isPlaying = false;
                                    currentPosition = 0;
                                } else {
                                    // If not the end, ensure button shows pause
                                    if (isPlaying) {
                                        playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                                        playPauseButton.setPadding(0, 0, 0, 0);
                                    }
                                }
                            }
                            updateProgressBar();
                            if (isShowingText) {
                                updateCurrentWords();
                            }
                        });
                    }
                    
                    @Override
                    public void onError(String utteranceId) {
                        Log.e(TAG, "TTS error for utterance: " + utteranceId);
                        runOnUiThread(() -> {
                            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                            isPlaying = false;
                            Toast.makeText(AudioReaderActivity.this, 
                                "Error during text-to-speech playback", 
                                Toast.LENGTH_SHORT).show();
                        });
                    }
                });

                isTTSReady = true;
                Log.d(TAG, "TTS initialized successfully");
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
                stopReading();
                // Skip back 5 seconds worth of words
                float wordsPerSecond = currentSpeed * 2.0f; // Approximate words per second
                int skipWords = (int)(5 * wordsPerSecond);
                currentPosition = Math.max(0, currentPosition - skipWords);
                updateProgressBar();
                startReading();
            }
        });
        
        forwardButton.setOnClickListener(v -> {
            if (textToSpeech != null) {
                stopReading();
                // Skip forward 5 seconds worth of words
                float wordsPerSecond = currentSpeed * 2.0f; // Approximate words per second
                int skipWords = (int)(5 * wordsPerSecond);
                String[] words = textContent.split("\\s+");
                currentPosition = Math.min(words.length - 1, currentPosition + skipWords);
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
                    currentPosition = progress;
                    updateProgressBar();
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
            Log.d(TAG, "Stopping playback");
            stopReading();
            // Immediately set button to play state
            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
            // Adjust padding for the play icon when stopping
            int padding = (int) (getResources().getDisplayMetrics().density * 3); // 3dp
            playPauseButton.setPadding(padding, 0, 0, 0);
            isPlaying = false; // Update state immediately
        } else {
            Log.d(TAG, "Starting playback");
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

            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "messageID");
            
            String[] words = textContent.split("\\s+");
            int startWord = Math.min((int)currentPosition, words.length - 1);
            StringBuilder remainingText = new StringBuilder();
            
            // Take a larger chunk of text for TTS
            int endWord = Math.min(startWord + MAX_CHUNK_SIZE, words.length);
            for (int i = startWord; i < endWord; i++) {
                String word = words[i].trim();
                if (!word.isEmpty() && isValidWord(word)) {
                    remainingText.append(word).append(" ");
                }
            }
            
            String textToSpeak = remainingText.toString().trim();
            Log.d(TAG, "Starting TTS at position " + currentPosition + " with text: [" + textToSpeak + "]");
            
            if (!textToSpeak.isEmpty()) {
                try {
                    // Stop any ongoing speech first
                    textToSpeech.stop();
                    
                    // Reset position tracking
                    currentPosition = startWord;
                    
                    // Add a small delay before starting new speech
                    handler.postDelayed(() -> {
                        int result = textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "messageID");
                        if (result == TextToSpeech.ERROR) {
                            Log.e(TAG, "Error starting TTS");
                            Toast.makeText(this, "Error starting text-to-speech", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        isPlaying = true;
                        startProgressUpdate();
                        
                        // Queue next chunk immediately
                        if (endWord < words.length) {
                            queueNextChunk(endWord - CHUNK_OVERLAP);
                        }
                    }, 100);
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
    
    private void queueNextChunk(int startPosition) {
        if (!isPlaying) return;
        
        String[] words = textContent.split("\\s+");
        if (startPosition >= words.length) return;
        
        int endWord = Math.min(startPosition + MAX_CHUNK_SIZE, words.length);
        
        StringBuilder chunkText = new StringBuilder();
        for (int i = startPosition; i < endWord; i++) {
            String word = words[i].trim();
            if (!word.isEmpty() && isValidWord(word)) {
                chunkText.append(word).append(" ");
            }
        }
        
        String textToSpeak = chunkText.toString().trim();
        if (!textToSpeak.isEmpty()) {
            Bundle params = new Bundle();
            String utteranceId = "chunk_" + startPosition;
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            
            Log.d(TAG, "Queueing chunk at position " + startPosition + " with text: [" + textToSpeak + "]");
            
            int result = textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_ADD, params, utteranceId);
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "Error queueing next chunk");
            } else {
                // Queue the next chunk after this one
                if (endWord < words.length) {
                    handler.postDelayed(() -> queueNextChunk(endWord - CHUNK_OVERLAP), 100);
                }
            }
        }
    }
    
    private void startProgressUpdate() {
        updateProgressRunnable = new Runnable() {
            private long startTime = System.currentTimeMillis();
            private int initialPosition = (int)currentPosition;
            private int lastUpdatePosition = initialPosition;
            
            @Override
            public void run() {
                if (isPlaying) {
                    // Calculate position based on elapsed time and speech rate
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    float wordsPerSecond = currentSpeed * 2.0f; // Approximate words per second
                    float newPosition = initialPosition + (elapsedTime * wordsPerSecond / 1000.0f);
                    
                    // Ensure we don't exceed the total duration
                    String[] words = textContent.split("\\s+");
                    if (newPosition >= words.length) {
                        newPosition = words.length - 1;
                        stopReading();
                        return;
                    }
                    
                    // Only update if we've moved to a new position
                    int currentPos = (int)newPosition;
                    if (currentPos != lastUpdatePosition) {
                        synchronized (this) {
                            currentPosition = currentPos;
                            lastUpdatePosition = currentPos;
                            updateProgressBar();
                        }
                    }
                    
                    // Check if we need to queue next chunk
                    int wordsLeft = words.length - currentPos;
                    
                    if (wordsLeft <= QUEUE_AHEAD_THRESHOLD && currentPos < words.length - 1) {
                        // Queue next chunk if we're running low on words
                        queueNextChunk(currentPos + 1);
                    }
                    
                    handler.postDelayed(this, SYNC_INTERVAL);
                }
            }
        };
        handler.post(updateProgressRunnable);
    }
    
    private void stopProgressUpdate() {
        if (updateProgressRunnable != null) {
            handler.removeCallbacks(updateProgressRunnable);
        }
    }
    
    private void updateProgressBar() {
        runOnUiThread(() -> {
            progressBar.setProgress((int)currentPosition);
            currentTimeText.setText(formatTime((int)currentPosition));
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
                // Clean the line by removing special characters and extra spaces
                String cleanedLine = line.trim()
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
        int currentPos = (int)currentPosition;
        int startWord = Math.max(0, currentPos - 2); // Show 2 words before current position
        int endWord = Math.min(words.length, startWord + 7); // Show 7 words total (2 before + current + 4 after)
        
        StringBuilder currentWords = new StringBuilder();
        for (int i = startWord; i < endWord; i++) {
            if (!words[i].trim().isEmpty() && isValidWord(words[i])) {
                // Highlight the current word
                if (i == currentPos) {
                    currentWords.append("<b>").append(words[i]).append("</b> ");
                } else {
                    currentWords.append(words[i]).append(" ");
                }
            }
        }
        
        String displayText = currentWords.toString().trim();
        Log.d(TAG, "Current words at position " + currentPos + ": [" + displayText + "]");
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
            isPlaying = false;
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
        super.onDestroy();
    }
} 