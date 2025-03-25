package com.example.aipodcast;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

/**
 * Entry point activity of the application.
 * Provides a welcome screen and navigation to the news search functionality.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    
    private MaterialCardView logoCard;
    private ImageView logoImage;
    private TextView appTitle;
    private TextView appSubtitle;
    private MaterialButton browseNewsButton;
    private TextView creditsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide action bar to keep full-screen welcome design
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        setContentView(R.layout.activity_main);

        // Initialize views
        logoCard = findViewById(R.id.logo_card);
        logoImage = findViewById(R.id.logo_image);
        appTitle = findViewById(R.id.app_title);
        appSubtitle = findViewById(R.id.app_subtitle);
        browseNewsButton = findViewById(R.id.start_button);
        creditsText = findViewById(R.id.credits_text);

        // Setup click listeners
        setupClickListeners();
        
        // Apply animations
        animateUI();
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
     * Apply entrance animations to UI elements
     */
    private void animateUI() {
        // Prepare animations
        logoCard.setScaleX(0f);
        logoCard.setScaleY(0f);
        logoCard.setAlpha(0f);
        
        appTitle.setTranslationY(50f);
        appTitle.setAlpha(0f);
        
        appSubtitle.setTranslationY(50f);
        appSubtitle.setAlpha(0f);
        
        browseNewsButton.setScaleX(0f);
        browseNewsButton.setScaleY(0f);
        
        creditsText.setAlpha(0f);
        
        // Animate logo
        logoCard.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(400)
                .start();
        
        // Animate title with delay
        appTitle.animate()
                .translationY(0f)
                .alpha(1f)
                .setStartDelay(200)
                .setDuration(400)
                .start();
        
        // Animate subtitle with delay
        appSubtitle.animate()
                .translationY(0f)
                .alpha(1f)
                .setStartDelay(300)
                .setDuration(400)
                .start();
        
        // Animate button with delay
        browseNewsButton.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(400)
                .setDuration(400)
                .start();
        
        // Fade in credits
        creditsText.animate()
                .alpha(1f)
                .setStartDelay(500)
                .setDuration(400)
                .start();
    }
    
    /**
     * Navigate to the news search screen with transition animation
     */
    private void navigateToNewsSearch() {
        Intent intent = new Intent(MainActivity.this, InputActivity.class);
        
        // Only use logo for shared element transition for better performance
        Pair<View, String> logoPair = Pair.create(logoCard, "app_logo_transition");
        
        ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(
                this, logoPair);
        
        try {
            startActivity(intent, options.toBundle());
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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
        // Reset animations if needed
        if (logoCard != null && logoCard.getScaleX() == 0f) {
            animateUI();
        }
    }
}

