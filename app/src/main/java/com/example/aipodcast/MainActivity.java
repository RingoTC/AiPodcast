package com.example.aipodcast;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

/**
 * Entry point activity of the application.
 * Provides a welcome screen and navigation to the news search functionality.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    
    private ImageView logoImage;
    private TextView appTitle;
    private MaterialButton browseNewsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide action bar to keep full-screen welcome design
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        setContentView(R.layout.activity_main);

        // Initialize views
        logoImage = findViewById(R.id.logo_image);
        appTitle = findViewById(R.id.app_title);
        browseNewsButton = findViewById(R.id.start_button);

        // Setup click listeners
        setupClickListeners();
    }
    
    /**
     * Set up click listeners for interactive elements
     */
    private void setupClickListeners() {
        browseNewsButton.setOnClickListener(v -> navigateToNewsSearch());
        
        // Add a long click listener to show app info
        browseNewsButton.setOnLongClickListener(v -> {
            showAppInfo();
            return true;
        });
    }
    
    /**
     * Navigate to the news search screen with transition animation
     */
    private void navigateToNewsSearch() {
        Intent intent = new Intent(MainActivity.this, InputActivity.class);
        
        // Create shared element transition
        Pair<View, String> logoPair = Pair.create(logoImage, "app_logo_transition");
        Pair<View, String> titlePair = Pair.create(appTitle, "app_title_transition");
        
        ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(
                this, logoPair, titlePair);
        
        try {
            startActivity(intent, options.toBundle());
        } catch (Exception e) {
            // Fallback if transition fails
            Log.e(TAG, "Error during transition: " + e.getMessage());
            startActivity(intent);
        }
    }
    
    /**
     * Show application information
     */
    private void showAppInfo() {
        Snackbar.make(browseNewsButton, 
                "AI Podcast News - Powered by NY Times API", 
                Snackbar.LENGTH_LONG)
                .setAction("About", v -> {
                    // TODO: Add about screen navigation
                    Snackbar.make(browseNewsButton, 
                            "Version 1.0 - Group 1 Project", 
                            Snackbar.LENGTH_SHORT).show();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Additional animation or update logic can be added here
    }
}

