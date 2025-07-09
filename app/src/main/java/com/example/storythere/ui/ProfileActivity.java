package com.example.storythere.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.appcompat.widget.Toolbar;

import com.example.storythere.R;
import com.example.storythere.StoryThereApplication;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.graphics.drawable.GradientDrawable;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Handler;
import android.view.ViewAnimationUtils;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import java.util.Random;
import android.view.ViewGroup;
import android.util.Log;

public class ProfileActivity extends AppCompatActivity {
    
    private ImageView iconHome, iconSearch, iconMyBooks, iconProfile;
    private TextView textHome, textSearch, textMyBooks, textProfile;
    
    // Profile views
    private TextView userName, userEmail, currentTheme, currentLanguage, joinDate;
    // Section headers and labels
    private TextView toolbarTitle, settingsHeader, themeLabel, notificationsLabel, languageHeader, interfaceLanguageLabel, aboutUsHeader, reportIssueLabel, logoutLabel;
    private LinearLayout themeSetting, notificationsSetting, languageSetting, reportIssueSetting, logoutSetting;
    private LinearLayout userProfileSection, emailSection;
    
    private Toolbar toolbar;
    
    // Icon square backgrounds
    private View themeIconBg, notificationsIconBg, languageIconBg, faqIconBg, exitIconBg, emailIconBg, userIconFrameBg, themeChangeOverlay;
    
    private boolean isThemeAnimating = false;
    
    private static final String PREF_THEME_ANIM_PENDING = "theme_anim_pending";
    
    private ImageView notificationsSwitchBar;
    private boolean notificationsEnabled = false;
    
    private static final String PREFS_NAME = "ProfilePrefs";
    private static final String PREF_NOTIFICATIONS_ENABLED = "notifications_enabled";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply saved theme before setting content view
        applySavedTheme();
        
        setContentView(R.layout.activity_profile);
        
        initializeViews();
        // Restore notifications switch state
        notificationsEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_NOTIFICATIONS_ENABLED, false);
        updateNotificationsSwitchBar();
        // Check if we need to show the overlay after recreation
        if (shouldShowThemeOverlayOnStart()) {
            showAndFadeOutThemeOverlayAfterRecreate();
        }
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
        // Apply saved language and theme from centralized preferences
        String savedLang = StoryThereApplication.getSavedLanguage(getApplication());
        if (savedLang != null) {
            setLocale(savedLang);
        }
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
        joinDate = findViewById(R.id.joinDate);

        // Section headers and labels
        settingsHeader = findViewByIdWithText("@string/settings");
        themeLabel = findViewByIdWithText("@string/theme");
        notificationsLabel = findViewByIdWithText("@string/notifications");
        languageHeader = findViewByIdWithText("@string/language");
        interfaceLanguageLabel = findViewByIdWithText("@string/interface_language");
        aboutUsHeader = findViewByIdWithText("@string/about_us");
        reportIssueLabel = findViewByIdWithText("@string/report_issue_faq");
        logoutLabel = findViewByIdWithText("@string/logout");

        // Bottom navigation
        iconHome = findViewById(R.id.icon_home);
        iconSearch = findViewById(R.id.icon_search);
        iconMyBooks = findViewById(R.id.icon_my_books);
        iconProfile = findViewById(R.id.icon_profile);
        textHome = findViewById(R.id.text_home);
        textSearch = findViewById(R.id.text_search);
        textMyBooks = findViewById(R.id.text_my_books);
        textProfile = findViewById(R.id.text_profile);

        // Profile sections for theme adaptation
        userProfileSection = findViewById(R.id.userProfileSection);
        emailSection = findViewById(R.id.emailSection);

        // Settings
        themeSetting = findViewById(R.id.themeSetting);
        notificationsSetting = findViewById(R.id.notificationsSetting);
        languageSetting = findViewById(R.id.languageSetting);
        reportIssueSetting = findViewById(R.id.reportIssueSetting);
        logoutSetting = findViewById(R.id.logoutSetting);

        // Icon backgrounds
        themeIconBg = findViewById(R.id.themeIconBg);
        notificationsIconBg = findViewById(R.id.notificationsIconBg);
        languageIconBg = findViewById(R.id.languageIconBg);
        faqIconBg = findViewById(R.id.faqIconBg);
        exitIconBg = findViewById(R.id.exitIconBg);
        emailIconBg = findViewById(R.id.emailIconBg);
        userIconFrameBg = findViewById(R.id.userIconFrameBg);
        themeChangeOverlay = findViewById(R.id.themeChangeOverlay);

        notificationsSwitchBar = findViewById(R.id.notificationsSwitchBar);
    }
    
    private void loadUserData() {
        // Check if we're in offline mode
        boolean isOfflineMode = getIntent().getBooleanExtra("offline_mode", false);
        
        if (isOfflineMode) {
            Log.d("ProfileActivity", "Running in offline mode - showing offline profile");
            // Show offline profile
            userName.setText(R.string.offline_user);
            userEmail.setText(R.string.offline_mode);
            joinDate.setText(R.string.offline_mode);
            if (toolbarTitle != null) {
                toolbarTitle.setText(R.string.profile);
            }
            
            // Set current theme and language displays
            updateThemeDisplay();
            updateLanguageDisplay();
            return;
        }
        
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
                    userName.setText(R.string.user);
                }
            }
            
            // Set user email
            String email = user.getEmail();
            if (email != null) {
                userEmail.setText(email);
            } else {
                userEmail.setText(R.string.no_email_available);
            }
            
            // Set join date
            long creationTimestamp = user.getMetadata().getCreationTimestamp();
            Date date = new Date(creationTimestamp);
            Locale locale = getResources().getConfiguration().locale;
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", locale);
            String formattedDate = sdf.format(date);
            String joinText;
            if ("ru".equals(locale.getLanguage())) {
                joinText = getString(R.string.signed_in) + formattedDate;
            } else {
                joinText = getString(R.string.signed_in) + formattedDate;
            }
            joinDate.setText(joinText);
            
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
        themeSetting.setOnClickListener(v -> {
            if (!isThemeAnimating) {
                animateThemeChangeAndApply();
            }
        });
        notificationsSetting.setOnClickListener(v -> toggleNotificationsSwitch());
        notificationsSwitchBar.setOnClickListener(v -> toggleNotificationsSwitch());
        languageSetting.setOnClickListener(v -> {
            if (!isThemeAnimating) {
                animateScrambleLanguageChange();
            }
        });
        reportIssueSetting.setOnClickListener(v -> {});
        logoutSetting.setOnClickListener(v -> { logout(); });
    }
    
    private void animateThemeChangeAndApply() {
        if (themeChangeOverlay == null || isThemeAnimating) return;
        isThemeAnimating = true;
        int currentMode = StoryThereApplication.getSavedThemeMode(getApplication());
        int newMode = (currentMode == AppCompatDelegate.MODE_NIGHT_YES) ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        boolean toLight = (newMode == AppCompatDelegate.MODE_NIGHT_NO);
        int targetColor = toLight ? 0xFFFFFFFF : 0xFF1A1A1A;
        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.BL_TR,
            new int[] { targetColor, targetColor }
        );
        themeChangeOverlay.setBackground(gradient);
        themeChangeOverlay.setVisibility(View.VISIBLE);
        themeChangeOverlay.bringToFront();
        themeChangeOverlay.setClickable(true);
        themeChangeOverlay.setFocusable(true);
        themeChangeOverlay.post(() -> {
            int cx, cy;
            if (toLight) {
                cx = 0;
                cy = themeChangeOverlay.getHeight();
            } else {
                cx = themeChangeOverlay.getWidth();
                cy = 0;
            }
            int finalRadius = (int) Math.hypot(themeChangeOverlay.getWidth(), themeChangeOverlay.getHeight());
            Animator anim = ViewAnimationUtils.createCircularReveal(
                themeChangeOverlay, cx, cy, 0, finalRadius);
            anim.setDuration(1500);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    themeChangeOverlay.setAlpha(1f);
                    // Set flag to show overlay after recreate
                    getSharedPreferences("ThemePrefs", MODE_PRIVATE).edit().putBoolean(PREF_THEME_ANIM_PENDING, true).apply();
                    // Actually change the theme after animation
                    StoryThereApplication.saveThemeMode(getApplication(), newMode);
                    AppCompatDelegate.setDefaultNightMode(newMode);
                    // Do NOT hide overlay here; it will be hidden after recreate
                }
            });
            anim.start();
        });
    }
    
    private void animateScrambleLanguageChange() {
        if (isThemeAnimating) return;
        isThemeAnimating = true;
        // Block user interaction
        if (themeChangeOverlay != null) {
            themeChangeOverlay.setBackgroundColor(0x00000000);
            themeChangeOverlay.setVisibility(View.VISIBLE);
            themeChangeOverlay.bringToFront();
            themeChangeOverlay.setClickable(true);
            themeChangeOverlay.setFocusable(true);
        }
        // Prepare old and new texts
        String currentLang = StoryThereApplication.getSavedLanguage(getApplication());
        String nextLang = "en".equals(currentLang) ? "ru" : "en";
        // Save selected language to centralized storage
        StoryThereApplication.saveLanguage(getApplication(), nextLang);
        Resources res = getResources();
        Locale oldLocale = res.getConfiguration().locale;
        Locale newLocale = new Locale(nextLang);
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(newLocale);
        Resources newRes = createConfigurationContext(config).getResources();
        // Collect all relevant TextViews (EXCLUDE toolbarTitle, userName, userEmail)
        TextView[] views = new TextView[] {
            joinDate, currentTheme, currentLanguage,
            settingsHeader, themeLabel, notificationsLabel, languageHeader, interfaceLanguageLabel, aboutUsHeader, reportIssueLabel, logoutLabel,
            textHome, textSearch, textMyBooks, textProfile
        };
        String[] oldTexts = new String[views.length];
        String[] newTexts = new String[views.length];
        for (int i = 0; i < views.length; i++) {
            oldTexts[i] = views[i] != null ? views[i].getText().toString() : "";
        }
        // Get new values
        // joinDate: recalc with new locale
        String joinText;
        if ("ru".equals(nextLang)) {
            joinText = getString(R.string.signed_in) + joinDate.getText().toString().replaceAll(".*(\\d{2}\\.\\d{2}\\.\\d{4})$", "$1");
        } else {
            joinText = getString(R.string.signed_in) + joinDate.getText().toString().replaceAll(".*(\\d{2}\\.\\d{2}\\.\\d{4})$", "$1");
        }
        newTexts[0] = joinText;
        // currentTheme
        boolean isDark = isDarkTheme();
        newTexts[1] = newRes.getString(isDark ? R.string.dark : R.string.light);
        // currentLanguage
        newTexts[2] = newRes.getString("ru".equals(nextLang) ? R.string.russian : R.string.english);
        // Section headers and labels
        newTexts[3] = newRes.getString(R.string.settings);
        newTexts[4] = newRes.getString(R.string.theme);
        newTexts[5] = newRes.getString(R.string.notifications);
        newTexts[6] = newRes.getString(R.string.language);
        newTexts[7] = newRes.getString(R.string.interface_language);
        newTexts[8] = newRes.getString(R.string.about_us);
        newTexts[9] = newRes.getString(R.string.report_issue_faq);
        newTexts[10] = newRes.getString(R.string.logout);
        // Bottom navigation
        newTexts[11] = newRes.getString(R.string.home);
        newTexts[12] = newRes.getString(R.string.search);
        newTexts[13] = newRes.getString(R.string.my_books);
        newTexts[14] = newRes.getString(R.string.profile);
        // Animation loop
        int duration = 1500;
        int steps = 30;
        int delay = duration / steps;
        Handler handler = new Handler(Looper.getMainLooper());
        Random random = new Random();
        // Define color palette: predominantly theme blue shades, avoid black/white
        int[] scrambleColors;
        if (isDark) {
            scrambleColors = new int[] {
                0xFF1A73E8, // progress_blue
                0xFF3C83B6, // slightly_blue
                0xFF4A739C, // blue-gray
                0xFF212121, // background_blue (dark)
                0xFF00B8D4, // cyan accent
                0xFF1976D2, // deep blue
                0xFF64B5F6, // light blue
                0xFF1565C0, // dark blue
                0xFF2196F3, // material blue
                0xFF90CAF9  // very light blue
            };
        } else {
            scrambleColors = new int[] {
                0xFF1A73E8, // progress_blue
                0xFF3C83B6, // slightly_blue
                0xFF4A739C, // blue-gray
                0xFFE8F0FE, // background_blue (light)
                0xFF00B8D4, // cyan accent
                0xFF1976D2, // deep blue
                0xFF64B5F6, // light blue
                0xFF1565C0, // dark blue
                0xFF2196F3, // material blue
                0xFF90CAF9  // very light blue
            };
        }
        // Final color: white for dark, black for light
        int finalColor = isDark ? 0xFFFFFFFF : 0xFF000000;
        String scrambleChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=<>?";
        for (int step = 0; step <= steps; step++) {
            final int s = step;
            handler.postDelayed(() -> {
                for (int i = 0; i < views.length; i++) {
                    if (views[i] == null) continue;
                    String oldStr = oldTexts[i];
                    String newStr = newTexts[i];
                    int maxLen = Math.max(oldStr.length(), newStr.length());
                    SpannableStringBuilder builder = new SpannableStringBuilder();
                    for (int j = 0; j < maxLen; j++) {
                        char c;
                        int color;
                        if (s == steps) {
                            c = j < newStr.length() ? newStr.charAt(j) : ' ';
                            color = finalColor;
                        } else if (j < oldStr.length() && j < newStr.length() && oldStr.charAt(j) == newStr.charAt(j)) {
                            c = oldStr.charAt(j);
                            color = finalColor;
                        } else if (s > (steps * j / maxLen)) {
                            c = j < newStr.length() ? newStr.charAt(j) : ' ';
                            color = finalColor;
                        } else {
                            c = scrambleChars.charAt(random.nextInt(scrambleChars.length()));
                            color = scrambleColors[random.nextInt(scrambleColors.length)];
                        }
                        builder.append(c);
                        builder.setSpan(new ForegroundColorSpan(color), builder.length() - 1, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    views[i].setText(builder, TextView.BufferType.SPANNABLE);
                }
                if (s == steps) {
                    // Unblock and actually change language
                    if (themeChangeOverlay != null) {
                        themeChangeOverlay.setVisibility(View.GONE);
                        themeChangeOverlay.setAlpha(1f);
                    }
                    isThemeAnimating = false;
                    setLocale(nextLang);
                    recreate();
                }
            }, step * delay);
        }
    }
    
    private void updateThemeDisplay() {
        int currentMode = StoryThereApplication.getSavedThemeMode(getApplication());
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
        String currentLang = StoryThereApplication.getSavedLanguage(getApplication());
        if ("ru".equals(currentLang)) {
            currentLanguage.setText(getString(R.string.russian));
        } else {
            currentLanguage.setText(getString(R.string.english));
        }
    }
    
    private String getCurrentLanguage() {
        return StoryThereApplication.getSavedLanguage(getApplication());
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
        // Check if we're in offline mode
        boolean isOfflineMode = getIntent().getBooleanExtra("offline_mode", false);
        
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            if (isOfflineMode) {
                intent.putExtra("offline_mode", true);
            }
            startActivity(intent);
            finish();
        });
        
        findViewById(R.id.nav_search).setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            if (isOfflineMode) {
                intent.putExtra("offline_mode", true);
            }
            startActivity(intent);
            finish();
        });
        
        findViewById(R.id.nav_my_books).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            if (isOfflineMode) {
                intent.putExtra("offline_mode", true);
            }
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
        int currentMode = StoryThereApplication.getSavedThemeMode(getApplication());
        if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            return true;
        } else if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) {
            return false;
        } else {
            // Follow system
            return (getResources().getConfiguration().uiMode & 
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                    android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
    }
    
    private void applyThemeAdaptiveBackgrounds() {
        int squareColor = isDarkTheme() ? 0xFF333435 : 0xFFF0F2F5;
        int cornerRadiusPx = (int) (getResources().getDisplayMetrics().density * 10); // 10dp
        GradientDrawable squareBg = new GradientDrawable();
        squareBg.setColor(squareColor);
        squareBg.setCornerRadius(cornerRadiusPx);
        if (themeIconBg != null) themeIconBg.setBackground(squareBg.getConstantState().newDrawable());
        if (notificationsIconBg != null) notificationsIconBg.setBackground(squareBg.getConstantState().newDrawable());
        if (languageIconBg != null) languageIconBg.setBackground(squareBg.getConstantState().newDrawable());
        if (faqIconBg != null) faqIconBg.setBackground(squareBg.getConstantState().newDrawable());
        if (exitIconBg != null) exitIconBg.setBackground(squareBg.getConstantState().newDrawable());
        if (emailIconBg != null) emailIconBg.setBackground(squareBg.getConstantState().newDrawable());
        // User icon frame: white circle with blue stroke
        if (userIconFrameBg != null) {
            GradientDrawable userFrame = new GradientDrawable();
            userFrame.setShape(GradientDrawable.OVAL);
            userFrame.setColor(0xFFFFFFFF);
            userFrame.setStroke((int)(getResources().getDisplayMetrics().density * 2), ContextCompat.getColor(this, R.color.progress_blue)); // 2dp stroke
            userIconFrameBg.setBackground(userFrame);
        }
        if (userProfileSection != null) userProfileSection.setBackgroundColor(squareColor);
        if (emailSection != null) emailSection.setBackgroundColor(squareColor);
    }
    
    private boolean shouldShowThemeOverlayOnStart() {
        return getSharedPreferences("ThemePrefs", MODE_PRIVATE).getBoolean(PREF_THEME_ANIM_PENDING, false);
    }
    private void showAndFadeOutThemeOverlayAfterRecreate() {
        // Remove the flag
        getSharedPreferences("ThemePrefs", MODE_PRIVATE).edit().putBoolean(PREF_THEME_ANIM_PENDING, false).apply();
        // Set overlay color to match the current theme
        int targetColor = isDarkTheme() ? 0xFF1A1A1A : 0xFFFFFFFF;
        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.BL_TR,
            new int[] { targetColor, targetColor }
        );
        themeChangeOverlay.setBackground(gradient);
        themeChangeOverlay.setAlpha(1f);
        themeChangeOverlay.setVisibility(View.VISIBLE);
        themeChangeOverlay.bringToFront();
        themeChangeOverlay.setClickable(true);
        themeChangeOverlay.setFocusable(true);
        themeChangeOverlay.postDelayed(() -> {
            themeChangeOverlay.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction(() -> {
                    themeChangeOverlay.setVisibility(View.GONE);
                    themeChangeOverlay.setAlpha(1f);
                    isThemeAnimating = false;
                })
                .start();
        }, 100);
    }

    private void toggleNotificationsSwitch() {
        // Animate the dot movement by crossfading icons
        float density = getResources().getDisplayMetrics().density;
        if (notificationsEnabled) {
            // Turn off
            notificationsSwitchBar.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                notificationsSwitchBar.setImageResource(R.drawable.ic_switchbar_unselected);
                notificationsSwitchBar.setTranslationX(0f); // Reset position
                notificationsSwitchBar.animate().alpha(1f).setDuration(120).start();
            }).start();
        } else {
            // Turn on
            notificationsSwitchBar.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                notificationsSwitchBar.setImageResource(R.drawable.ic_switchbar_selected);
                notificationsSwitchBar.setTranslationX(4 * density); // Shift 4px right
                notificationsSwitchBar.animate().alpha(1f).setDuration(120).start();
            }).start();
        }
        notificationsEnabled = !notificationsEnabled;
        // Save state
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(PREF_NOTIFICATIONS_ENABLED, notificationsEnabled).apply();
    }

    // Helper to update the switchbar icon and position based on state
    private void updateNotificationsSwitchBar() {
        float density = getResources().getDisplayMetrics().density;
        if (notificationsEnabled) {
            notificationsSwitchBar.setImageResource(R.drawable.ic_switchbar_selected);
            notificationsSwitchBar.setTranslationX(4 * density);
        } else {
            notificationsSwitchBar.setImageResource(R.drawable.ic_switchbar_unselected);
            notificationsSwitchBar.setTranslationX(0f);
        }
    }

    // Helper to find TextView by its text (for section headers/labels without IDs)
    private TextView findViewByIdWithText(String stringResName) {
        String text = getString(getResources().getIdentifier(stringResName.replace("@string/", ""), "string", getPackageName()));
        View root = findViewById(android.R.id.content);
        return findTextViewWithText(root, text);
    }
    private TextView findTextViewWithText(View root, String text) {
        if (root instanceof TextView && text.equals(((TextView) root).getText().toString())) {
            return (TextView) root;
        } else if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextViewWithText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
} 