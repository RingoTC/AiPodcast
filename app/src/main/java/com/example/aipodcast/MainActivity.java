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
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private AuthService authService;
    private static final float DISABLED_ALPHA = 1.0f;
    private static final int COLOR_PURPLE = 0xFF6200EE; 
    private static final int COLOR_DISABLED_BG = 0xFFE0E0E0; 
    private static final float ENABLED_ALPHA = 1.0f;
    private MaterialCardView logoCard;
    private ImageView logoImage;
    private TextView appTitle;
    private ChipGroup topicChips;
    private MaterialButton generatePodcastButton;
    private MaterialButton browseNewsButton;
    private TextView creditsText;
    private Slider commuteTimeSlider;
    private TextView commuteTimeValue;
    private com.google.android.material.switchmaterial.SwitchMaterial aiContentSwitch;
    private TextView aiStatusText;
    private List<String> selectedTopics = new ArrayList<>();
    private int commuteDuration = 30; 
    private boolean useAIGeneration = true; 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authService = AuthService.getInstance(this);
        if (!authService.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        initializeViews();
        setupUI();
        animateUI();
        updateButtonsState();
    }
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
        aiContentSwitch = findViewById(R.id.ai_content_switch);
        aiStatusText = findViewById(R.id.ai_status_text);
        setupCommuteTimeSlider();
    }
    private void setupUI() {
        setupTopicChips();
        setupActionButtons();
        setupAIOptions();
    }
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
    private void setupActionButtons() {
        generatePodcastButton.setOnClickListener(v -> handleGenerate());
        browseNewsButton.setOnClickListener(v -> navigateToNewsSearch());
        updateButtonsState();
    }
    private void setupAIOptions() {
        updateAIStatusText(aiContentSwitch.isChecked());
        aiContentSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            useAIGeneration = isChecked;
            updateAIStatusText(isChecked);
        });
    }
    private void updateAIStatusText(boolean isEnabled) {
        if (isEnabled) {
            aiStatusText.setText("AI generation is enabled. Your podcast will feature a conversation between two hosts.");
        } else {
            aiStatusText.setText("AI generation is disabled. Your podcast will use a standard template format.");
        }
    }
    private void updateButtonsState() {
        boolean hasTopicsSelected = !selectedTopics.isEmpty();
        generatePodcastButton.setEnabled(hasTopicsSelected);
        generatePodcastButton.setAlpha(ENABLED_ALPHA);
        if (!hasTopicsSelected) {
            generatePodcastButton.setBackgroundColor(COLOR_DISABLED_BG);
            generatePodcastButton.setTextColor(0xFF666666);
        } else {
            generatePodcastButton.setBackgroundColor(COLOR_PURPLE);
            generatePodcastButton.setTextColor(getResources().getColor(android.R.color.white));
        }
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
    private void handleGenerate() {
        if (selectedTopics.isEmpty()) {
            showTopicSelectionError();
            return;
        }
        try {
            Intent intent = new Intent(this, InputActivity.class);
            intent.putStringArrayListExtra("selected_topics", new ArrayList<>(selectedTopics));
            intent.putExtra("duration", commuteDuration); 
            intent.putExtra("podcast_mode", true); 
            intent.putExtra("use_ai_generation", useAIGeneration); 
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
    private void navigateToNewsSearch() {
        if (selectedTopics.isEmpty()) {
            showTopicSelectionError();
            return;
        }
        try {
            Intent intent = new Intent(this, InputActivity.class);
            intent.putStringArrayListExtra("selected_topics", new ArrayList<>(selectedTopics));
            intent.putExtra("duration", commuteDuration); 
            intent.putExtra("use_ai_generation", useAIGeneration); 
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
    private void showTopicSelectionError() {
        Snackbar.make(generatePodcastButton, 
                "Please select at least one topic", 
                Snackbar.LENGTH_LONG).show();
    }
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
        if (!authService.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        updateButtonsState();
    }
}