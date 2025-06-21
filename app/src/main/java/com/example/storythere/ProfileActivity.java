package com.example.storythere;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.appcompat.widget.Toolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {
    
    private ImageView iconHome, iconSearch, iconMyBooks, iconProfile;
    private TextView textHome, textSearch, textMyBooks, textProfile;
    
    // Profile views
    private TextView userName, userEmail, currentTheme, currentLanguage;
    private LinearLayout themeSetting, notificationsSetting, languageSetting, reportIssueSetting, logoutSetting;
    private LinearLayout userProfileSection, emailSection;
    
    private Toolbar toolbar;
    private TextView toolbarTitle;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply saved theme before setting content view
        applySavedTheme();
        
        setContentView(R.layout.activity_profile);
        
        initializeViews();
        setupBottomNavigation();
        setSelectedTab(3); // Profile is selected
        
        // Load user data from Firebase
        loadUserData();
        
        // Setup click listeners
        setupClickListeners();
        
        // Apply theme-adaptive backgrounds
        applyThemeAdaptiveBackgrounds();
    }
    
    private void applySavedTheme() {
        int savedThemeMode = StoryThereApplication.getSavedThemeMode(getApplication());
        AppCompatDelegate.setDefaultNightMode(savedThemeMode);
    }
    
    private void initializeViews() {
        // Toolbar
        toolbar = findViewById(R.id.toolbar);
        toolbarTitle = findViewById(R.id.toolbar_title);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Profile views
        userName = findViewById(R.id.userName);
        userEmail = findViewById(R.id.userEmail);
        currentTheme = findViewById(R.id.currentTheme);
        currentLanguage = findViewById(R.id.currentLanguage);

        // Profile sections for theme adaptation
        userProfileSection = findViewById(R.id.userProfileSection);
        emailSection = findViewById(R.id.emailSection);

        // Settings
        themeSetting = findViewById(R.id.themeSetting);
        notificationsSetting = findViewById(R.id.notificationsSetting);
        languageSetting = findViewById(R.id.languageSetting);
        reportIssueSetting = findViewById(R.id.reportIssueSetting);
        logoutSetting = findViewById(R.id.logoutSetting);

        // Bottom navigation - using same pattern as other activities
        iconHome = findViewById(R.id.icon_home);
        iconSearch = findViewById(R.id.icon_search);
        iconMyBooks = findViewById(R.id.icon_my_books);
        iconProfile = findViewById(R.id.icon_profile);
        
        textHome = findViewById(R.id.text_home);
        textSearch = findViewById(R.id.text_search);
        textMyBooks = findViewById(R.id.text_my_books);
        textProfile = findViewById(R.id.text_profile);
    }
    
    private void loadUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            // Set user name (display name or email prefix)
            String displayName = user.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                userName.setText(displayName);
                if (toolbarTitle != null) {
                    toolbarTitle.setText(displayName);
                }
            } else {
                // Use email prefix as username
                String email = user.getEmail();
                if (email != null && email.contains("@")) {
                    String emailPrefix = email.substring(0, email.indexOf("@"));
                    userName.setText(emailPrefix);
                    if (toolbarTitle != null) {
                        toolbarTitle.setText(emailPrefix);
                    }
                } else {
                    userName.setText("User");
                }
            }
            
            // Set user email
            String email = user.getEmail();
            if (email != null) {
                userEmail.setText(email);
            } else {
                userEmail.setText("No email available");
            }
            
            // Set current theme
            updateThemeDisplay();
            updateLanguageDisplay();
        } else {
            // User not logged in, redirect to login
            startActivity(new Intent(this, Login.class));
            finish();
        }
    }
    
    private void setupClickListeners() {
        // Theme setting
        themeSetting.setOnClickListener(v -> {
            // Toggle theme
            int currentMode = AppCompatDelegate.getDefaultNightMode();
            int newMode;
            
            if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
                newMode = AppCompatDelegate.MODE_NIGHT_NO;
            } else {
                newMode = AppCompatDelegate.MODE_NIGHT_YES;
            }
            
            // Save the new theme mode using Application class
            StoryThereApplication.saveThemeMode(getApplication(), newMode);
            
            // Apply the new theme
            AppCompatDelegate.setDefaultNightMode(newMode);
            
            // Update the display immediately
            updateThemeDisplay();
            
            // Reapply theme-adaptive backgrounds
            applyThemeAdaptiveBackgrounds();
        });
        
        // Notifications setting (placeholder)
        notificationsSetting.setOnClickListener(v -> {
            // Placeholder for notifications
        });
        
        // Language setting
        languageSetting.setOnClickListener(v -> {
            // Toggle language
            String currentLang = getCurrentLanguage();
            if ("en".equals(currentLang)) {
                setLocale("ru");
            } else {
                setLocale("en");
            }
            recreate();
        });
        
        // Report Issue & FAQ (placeholder)
        reportIssueSetting.setOnClickListener(v -> {
            // Placeholder for report issue
        });
        
        // Logout
        logoutSetting.setOnClickListener(v -> {
            logout();
        });
    }
    
    private void updateThemeDisplay() {
        int currentMode = AppCompatDelegate.getDefaultNightMode();
        String themeText;
        
        if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            themeText = getString(R.string.dark);
        } else if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) {
            themeText = getString(R.string.light);
        } else {
            // Follow system
            boolean isDark = getResources().getConfiguration().uiMode == Configuration.UI_MODE_NIGHT_YES;
            themeText = isDark ? getString(R.string.dark) : getString(R.string.light);
        }
        
        if (currentTheme != null) {
            currentTheme.setText(themeText);
        }
    }
    
    private void updateLanguageDisplay() {
        String currentLang = getCurrentLanguage();
        if ("ru".equals(currentLang)) {
            currentLanguage.setText(getString(R.string.russian));
        } else {
            currentLanguage.setText(getString(R.string.english));
        }
    }
    
    private String getCurrentLanguage() {
        return getResources().getConfiguration().locale.getLanguage();
    }
    
    private void setLocale(String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        
        Resources resources = getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(locale);
        
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
    }
    
    private void logout() {
        FirebaseAuth.getInstance().signOut();
        
        // Redirect to login screen
        Intent intent = new Intent(this, Login.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
    
    private void setupBottomNavigation() {
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            startActivity(intent);
            finish();
        });
        
        findViewById(R.id.nav_search).setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivity(intent);
            finish();
        });
        
        findViewById(R.id.nav_my_books).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        });
        
        findViewById(R.id.nav_profile).setOnClickListener(v -> {
            // Already on profile, do nothing
        });
    }
    
    private void setSelectedTab(int selectedIndex) {
        // Reset all icons and texts
        resetAllTabs();
        
        // Get theme-appropriate colors
        int selectedColor = getSelectedColor();
        
        // Set selected tab
        switch (selectedIndex) {
            case 0: // Home
                iconHome.setColorFilter(selectedColor);
                textHome.setTextColor(selectedColor);
                textHome.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_bold));
                break;
            case 1: // Search
                iconSearch.setColorFilter(selectedColor);
                textSearch.setTextColor(selectedColor);
                textSearch.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_bold));
                break;
            case 2: // My Books
                iconMyBooks.setColorFilter(selectedColor);
                textMyBooks.setTextColor(selectedColor);
                textMyBooks.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_bold));
                break;
            case 3: // Profile
                iconProfile.setColorFilter(selectedColor);
                textProfile.setTextColor(selectedColor);
                textProfile.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_bold));
                break;
        }
    }
    
    private void resetAllTabs() {
        // Get theme-appropriate unselected color
        int unselectedColor = getUnselectedColor();
        
        // Reset all icons to unselected color
        iconHome.setColorFilter(unselectedColor);
        iconSearch.setColorFilter(unselectedColor);
        iconMyBooks.setColorFilter(unselectedColor);
        iconProfile.setColorFilter(unselectedColor);
        
        // Reset all texts to default color and normal weight
        textHome.setTextColor(unselectedColor);
        textSearch.setTextColor(unselectedColor);
        textMyBooks.setTextColor(unselectedColor);
        textProfile.setTextColor(unselectedColor);
        
        textHome.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_regular));
        textSearch.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_regular));
        textMyBooks.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_regular));
        textProfile.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat_regular));
    }
    
    private int getSelectedColor() {
        // Check if we're in dark mode
        if (isDarkTheme()) {
            return ContextCompat.getColor(this, R.color.bottom_nav_selected_dark);
        } else {
            return ContextCompat.getColor(this, R.color.bottom_nav_selected_light);
        }
    }
    
    private int getUnselectedColor() {
        // Check if we're in dark mode
        if (isDarkTheme()) {
            return ContextCompat.getColor(this, R.color.bottom_nav_unselected_dark);
        } else {
            return ContextCompat.getColor(this, R.color.bottom_nav_unselected_light);
        }
    }
    
    private boolean isDarkTheme() {
        return (getResources().getConfiguration().uiMode & 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
    
    private void applyThemeAdaptiveBackgrounds() {
        int backgroundColor = ContextCompat.getColor(this, R.color.background_activity);
        
        if (userProfileSection != null) {
            userProfileSection.setBackgroundColor(backgroundColor);
        }
        
        if (emailSection != null) {
            emailSection.setBackgroundColor(backgroundColor);
        }
    }
} 