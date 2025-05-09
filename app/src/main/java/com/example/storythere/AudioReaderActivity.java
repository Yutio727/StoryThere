package com.example.storythere;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.LocaleList;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
    private TextToSpeech textToSpeech;
    private String textContent;
    private boolean isPlaying = false;
    private int currentPosition = 0;
    private int totalDuration = 0;
    private float currentSpeed = 1.0f;
    private Handler handler = new Handler();
    private Runnable updateProgressRunnable;
    
    private ImageButton playPauseButton;
    private ImageButton rewindButton;
    private ImageButton forwardButton;
    private SeekBar progressBar;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private TextView bookTitle;
    private TextView bookAuthor;

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
            
            if (title != null && getSupportActionBar() != null) {
                getSupportActionBar().setTitle(title);
            }
            
            bookTitle.setText(title);
            bookAuthor.setText(author);
            
            // Read text content
            textContent = readTextFromFile(contentUri);
            
            // Calculate total duration (rough estimate: 1 word per second)
            totalDuration = textContent.split("\\s+").length;
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
        } else if (id == R.id.action_book_info) {
            // TODO: Implement book info page
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
        
        // Determine which language to show based on the current text
        String targetLanguage = isTextPrimarilyRussian(textContent) ? "ru" : "en";
        
        // Filter voices by language and quality, and limit to 3 voices
        int count = 0;
        for (Voice voice : allVoices) {
            if (voice.getLocale().getLanguage().equals(targetLanguage) && 
                voice.getQuality() == Voice.QUALITY_HIGH) {
                filteredVoices.add(voice);
                count++;
                if (count >= 3) break; // Only take the first 3 voices
            }
        }
        
        // If we don't have enough high-quality voices, add some medium quality ones
        if (count < 3) {
            for (Voice voice : allVoices) {
                if (voice.getLocale().getLanguage().equals(targetLanguage) && 
                    voice.getQuality() != Voice.QUALITY_HIGH) {
                    filteredVoices.add(voice);
                    count++;
                    if (count >= 3) break;
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
                    @Override
                    public void onStart(String utteranceId) {
                        runOnUiThread(() -> {
                            playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                            isPlaying = true;
                        });
                    }
                    
                    @Override
                    public void onDone(String utteranceId) {
                        runOnUiThread(() -> {
                            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                            isPlaying = false;
                            currentPosition = 0;
                            updateProgressBar();
                        });
                    }
                    
                    @Override
                    public void onError(String utteranceId) {
                        runOnUiThread(() -> {
                            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                            isPlaying = false;
                        });
                    }
                });
            }
        });
    }
    
    private void setupClickListeners() {
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        
        rewindButton.setOnClickListener(v -> {
            if (textToSpeech != null) {
                textToSpeech.stop();
                currentPosition = Math.max(0, currentPosition - 10);
                updateProgressBar();
                startReading();
            }
        });
        
        forwardButton.setOnClickListener(v -> {
            if (textToSpeech != null) {
                textToSpeech.stop();
                currentPosition = Math.min(totalDuration, currentPosition + 10);
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
        if (isPlaying) {
            stopReading();
        } else {
            startReading();
        }
    }
    
    private void startReading() {
        if (textToSpeech != null) {
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "messageID");
            
            // Calculate the remaining text based on current position
            String[] words = textContent.split("\\s+");
            int startWord = Math.min(currentPosition, words.length - 1);
            StringBuilder remainingText = new StringBuilder();
            for (int i = startWord; i < words.length; i++) {
                remainingText.append(words[i]).append(" ");
            }
            
            textToSpeech.speak(remainingText.toString(), TextToSpeech.QUEUE_FLUSH, params, "messageID");
            isPlaying = true;
            startProgressUpdate();
        }
    }
    
    private void stopReading() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            isPlaying = false;
            stopProgressUpdate();
        }
    }
    
    private void startProgressUpdate() {
        updateProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPlaying) {
                    currentPosition++;
                    updateProgressBar();
                    handler.postDelayed(this, 1000);
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
            progressBar.setProgress(currentPosition);
            currentTimeText.setText(formatTime(currentPosition));
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
                text.append(line).append(" ");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return text.toString();
    }
    
    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        stopProgressUpdate();
        super.onDestroy();
    }
} 