package com.example.aipodcast;

import android.app.ActivityOptions;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aipodcast.activity.LoginActivity;
import com.example.aipodcast.service.AuthService;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;

/**
 * Entry point activity of the application.
 * Provides topic selection and actions for news podcast generation.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private AuthService authService;
    private static final float DISABLED_ALPHA = 1.0f;
    private static final int COLOR_PURPLE = 0xFF6200EE; // Primary purple color
    private static final int COLOR_DISABLED_BG = 0xFFE0E0E0; // Light gray for disabled background
    private static final float ENABLED_ALPHA = 1.0f;
    
    // UI Elements
    private MaterialCardView logoCard;
    private ImageView logoImage;
    private TextView appTitle;
    private ChipGroup topicChips;
    private MaterialButton generatePodcastButton;
    private MaterialButton browseNewsButton;
    private TextView creditsText;
    private Slider commuteTimeSlider;
    private TextView commuteTimeValue;

    // State
    private List<String> selectedTopics = new ArrayList<>();
    private int commuteDuration = 30; // Default duration in minutes

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize AuthService
        authService = AuthService.getInstance(this);

        // Check if user is logged in
        if (!authService.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        
        setContentView(R.layout.activity_main);

        // Initialize views
        initializeViews();
        
        // Setup UI interactions
        setupUI();
        
        // Apply animations
        animateUI();
        updateButtonsState();
    }
    
    /**
     * Initialize view references
     */
    private void initializeViews() {
        logoCard = findViewById(R.id.logo_card);
        logoImage = findViewById(R.id.logo_image);
        appTitle = findViewById(R.id.app_title);
        topicChips = findViewById(R.id.topic_chips);
        generatePodcastButton = findViewById(R.id.generate_podcast_button);
        browseNewsButton = findViewById(R.id.browse_news_button);
        creditsText = findViewById(R.id.credits_text);
        commuteTimeSlider = findViewById(R.id.commute_time_slider);
        commuteTimeValue = findViewById(R.id.commute_time_value);
        
        setupCommuteTimeSlider();
    }
    
    /**
     * Set up UI elements and their listeners
     */
    private void setupUI() {
        // Setup Chips
        setupTopicChips();
        
        // Setup action buttons
        setupActionButtons();
    }

    /**
     * Setup topic selection chips
     */
    private void setupTopicChips() {
        topicChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            selectedTopics.clear();
            for (int id : checkedIds) {
                Chip chip = group.findViewById(id);
                if (chip != null) {
                    selectedTopics.add(chip.getText().toString());
                }
            }
            updateButtonsState();
        });
    }

    /**
     * Setup action buttons
     */
    private void setupActionButtons() {
        generatePodcastButton.setOnClickListener(v -> handleGenerate());
        browseNewsButton.setOnClickListener(v -> navigateToNewsSearch());
        
        // Initially disable buttons
        updateButtonsState();
    }

    /**
     * Update buttons state based on topic selection
     */
    private void updateButtonsState() {
        boolean hasTopicsSelected = !selectedTopics.isEmpty();
        
        // Update generate podcast button
        generatePodcastButton.setEnabled(hasTopicsSelected);
        generatePodcastButton.setAlpha(ENABLED_ALPHA);
        if (!hasTopicsSelected) {
            generatePodcastButton.setBackgroundColor(COLOR_DISABLED_BG);
            generatePodcastButton.setTextColor(0xFF666666);
        } else {
            generatePodcastButton.setBackgroundColor(COLOR_PURPLE);
            generatePodcastButton.setTextColor(getResources().getColor(android.R.color.white));
        }
        
        // Update browse news button
        browseNewsButton.setEnabled(hasTopicsSelected);
        browseNewsButton.setAlpha(ENABLED_ALPHA);
        if (!hasTopicsSelected) {
            browseNewsButton.setBackgroundColor(COLOR_DISABLED_BG);
            browseNewsButton.setTextColor(0xFF666666);
            browseNewsButton.setStrokeColor(ColorStateList.valueOf(COLOR_DISABLED_BG));
        } else {
            browseNewsButton.setBackgroundColor(getResources().getColor(android.R.color.white));
            browseNewsButton.setTextColor(COLOR_PURPLE);
            browseNewsButton.setStrokeColor(ColorStateList.valueOf(COLOR_PURPLE));
        }
    }

    /**
     * Handle generate button click
     */
    private void handleGenerate() {
        if (selectedTopics.isEmpty()) {
            showTopicSelectionError();
            return;
        }
        
        try {
            Intent intent = new Intent(this, InputActivity.class);
            intent.putStringArrayListExtra("selected_topics", new ArrayList<>(selectedTopics));
            intent.putExtra("duration", commuteDuration); // Pass the selected duration
            intent.putExtra("podcast_mode", true); // Set podcast mode flag to true
            
            // Create transition animation
            Pair<View, String> p1 = Pair.create(logoCard, "app_logo_transition");
            ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(this, p1);
            
            startActivity(intent, options.toBundle());
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to InputActivity: " + e.getMessage());
            Snackbar.make(generatePodcastButton, 
                    "Error opening podcast selection screen. Please try again.", 
                    Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Navigate to news search screen
     */
    private void navigateToNewsSearch() {
        if (selectedTopics.isEmpty()) {
            showTopicSelectionError();
            return;
        }

        try {
            Intent intent = new Intent(this, InputActivity.class);
            intent.putStringArrayListExtra("selected_topics", new ArrayList<>(selectedTopics));
            intent.putExtra("duration", commuteDuration); // Pass the selected duration
            
            // Create transition animation
            Pair<View, String> p1 = Pair.create(logoCard, "app_logo_transition");
            ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(this, p1);
            
            startActivity(intent, options.toBundle());
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to InputActivity: " + e.getMessage());
            Snackbar.make(generatePodcastButton, 
                    "Error opening search screen. Please try again.", 
                    Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Show topic selection error message
     */
    private void showTopicSelectionError() {
        Snackbar.make(generatePodcastButton, 
                "Please select at least one topic", 
                Snackbar.LENGTH_LONG).show();
    }

    /**
     * Animate UI elements
     */
    private void animateUI() {
        float startY = 100f;
        float startAlpha = 0f;
        int duration = 1000;
        
        logoCard.setTranslationY(startY);
        logoCard.setAlpha(startAlpha);
        logoCard.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(duration)
                .start();

        appTitle.setTranslationY(startY);
        appTitle.setAlpha(startAlpha);
        appTitle.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(duration)
                .setStartDelay(300)
                .start();

        topicChips.setTranslationY(startY);
        topicChips.setAlpha(startAlpha);
        topicChips.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(duration)
                .setStartDelay(600)
                .start();
    }

    private void setupCommuteTimeSlider() {
        commuteTimeSlider.addOnChangeListener((slider, value, fromUser) -> {
            commuteDuration = (int) value;
            updateCommuteTimeText();
        });
        
        // Set initial value
        updateCommuteTimeText();
    }

    private void updateCommuteTimeText() {
        commuteTimeValue.setText(String.format("%d minutes", commuteDuration));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            handleLogout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleLogout() {
        authService.logout();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if user is still logged in
        if (!authService.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        updateButtonsState();
    }
}
