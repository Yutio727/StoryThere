package com.example.storythere;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.firebase.FirebaseApp;
import java.util.Locale;

public class StoryThereApplication extends Application {
    
    private static final String PREF_NAME = "ThemePrefs";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_LANGUAGE = "selected_language";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        
        // Check if this is the first launch
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean isFirstLaunch = sharedPreferences.getBoolean(KEY_FIRST_LAUNCH, true);
        
        if (isFirstLaunch) {
            // First launch - detect and set system language and theme
            detectAndSetSystemSettings();
            
            // Mark as not first launch anymore
            sharedPreferences.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
        } else {
            // Not first launch - apply saved settings
            applySavedSettings();
        }
    }
    
    private void detectAndSetSystemSettings() {
        // Detect system language
        String systemLanguage = Locale.getDefault().getLanguage();
        String detectedLanguage = "en"; // Default to English
        
        // Check if system language is Russian
        if ("ru".equals(systemLanguage)) {
            detectedLanguage = "ru";
        }
        
        // Detect system theme
        int systemTheme = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        int detectedTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; // Default to follow system
        
        if (systemTheme == Configuration.UI_MODE_NIGHT_YES) {
            detectedTheme = AppCompatDelegate.MODE_NIGHT_YES; // Dark theme
        } else if (systemTheme == Configuration.UI_MODE_NIGHT_NO) {
            detectedTheme = AppCompatDelegate.MODE_NIGHT_NO; // Light theme
        }
        
        // Save detected settings
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_LANGUAGE, detectedLanguage);
        editor.putInt(KEY_THEME_MODE, detectedTheme);
        editor.apply();
        
        // Apply detected settings
        setLocale(detectedLanguage);
        AppCompatDelegate.setDefaultNightMode(detectedTheme);
    }
    
    private void applySavedSettings() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        
        // Apply saved language
        String savedLanguage = sharedPreferences.getString(KEY_LANGUAGE, "en");
        setLocale(savedLanguage);
        
        // Apply saved theme
        int savedThemeMode = sharedPreferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedThemeMode);
    }
    
    private void setLocale(String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        
        Resources resources = getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(locale);
        
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
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
    
    public static void saveLanguage(Application application, String languageCode) {
        SharedPreferences sharedPreferences = application.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_LANGUAGE, languageCode);
        editor.apply();
    }
    
    public static String getSavedLanguage(Application application) {
        SharedPreferences sharedPreferences = application.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        return sharedPreferences.getString(KEY_LANGUAGE, "en");
    }
} 