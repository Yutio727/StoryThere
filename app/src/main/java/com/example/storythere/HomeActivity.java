package com.example.storythere;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class HomeActivity extends AppCompatActivity {
    
    private ImageView iconHome, iconSearch, iconMyBooks, iconProfile;
    private TextView textHome, textSearch, textMyBooks, textProfile;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        
        initializeViews();
        setupBottomNavigation();
        setSelectedTab(0); // Home is selected
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
            // Already on home, do nothing
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
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
            finish();
        });
    }
    
    private void setSelectedTab(int selectedIndex) {
        // Reset all icons and texts
        resetAllTabs();
        
        // Set selected tab
        switch (selectedIndex) {
            case 0: // Home
                iconHome.setColorFilter(ContextCompat.getColor(this, android.R.color.black));
                textHome.setTextColor(ContextCompat.getColor(this, android.R.color.black));
                break;
            case 1: // Search
                iconSearch.setColorFilter(ContextCompat.getColor(this, android.R.color.black));
                textSearch.setTextColor(ContextCompat.getColor(this, android.R.color.black));
                break;
            case 2: // My Books
                iconMyBooks.setColorFilter(ContextCompat.getColor(this, android.R.color.black));
                textMyBooks.setTextColor(ContextCompat.getColor(this, android.R.color.black));
                break;
            case 3: // Profile
                iconProfile.setColorFilter(ContextCompat.getColor(this, android.R.color.black));
                textProfile.setTextColor(ContextCompat.getColor(this, android.R.color.black));
                break;
        }
    }
    
    private void resetAllTabs() {
        // Reset all icons to default color
        iconHome.setColorFilter(null);
        iconSearch.setColorFilter(null);
        iconMyBooks.setColorFilter(null);
        iconProfile.setColorFilter(null);
        
        // Reset all texts to default color
        textHome.setTextColor(ContextCompat.getColor(this, R.color.text_activity_primary));
        textSearch.setTextColor(ContextCompat.getColor(this, R.color.text_activity_primary));
        textMyBooks.setTextColor(ContextCompat.getColor(this, R.color.text_activity_primary));
        textProfile.setTextColor(ContextCompat.getColor(this, R.color.text_activity_primary));
    }
} 