package com.example.storythere;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.firebase.FirebaseApp;

public class StoryThereApplication extends Application {
    
    private static final String PREF_NAME = "ThemePrefs";
    private static final String KEY_THEME_MODE = "theme_mode";
    
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        
        // Apply saved theme mode when app starts
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        int savedThemeMode = sharedPreferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedThemeMode);
    }
    
    public static void saveThemeMode(Application application, int themeMode) {
        SharedPreferences sharedPreferences = application.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_THEME_MODE, themeMode);
        editor.apply();
    }
    
    public static int getSavedThemeMode(Application application) {
        SharedPreferences sharedPreferences = application.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        return sharedPreferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
} 