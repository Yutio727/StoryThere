package com.example.storythere.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.storythere.R;

public class SearchActivity extends AppCompatActivity {
    
    private ImageView iconHome, iconSearch, iconMyBooks, iconProfile;
    private TextView textHome, textSearch, textMyBooks, textProfile;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        
        initializeViews();
        setupBottomNavigation();
        setSelectedTab(1); // Search is selected
    }
    
    private void initializeViews() {
        iconHome = findViewById(R.id.icon_home);
        iconSearch = findViewById(R.id.icon_search);
        iconMyBooks = findViewById(R.id.icon_my_books);
        iconProfile = findViewById(R.id.icon_profile);
        
        textHome = findViewById(R.id.text_home);
        textSearch = findViewById(R.id.text_search);
        textMyBooks = findViewById(R.id.text_my_books);
        textProfile = findViewById(R.id.text_profile);
    }
    
    private void setupBottomNavigation() {
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            startActivity(intent);
            finish();
        });
        
        findViewById(R.id.nav_search).setOnClickListener(v -> {
            // Already on search, do nothing
        });
        
        findViewById(R.id.nav_my_books).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        });
        
        findViewById(R.id.nav_profile).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
            finish();
        });
    }
    
    private void setSelectedTab(int selectedIndex) {
        // Reset all icons and texts
        resetAllTabs();
        
        // Get theme-appropriate colors
        int selectedColor = getSelectedColor();
        int unselectedColor = getUnselectedColor();
        
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
} 