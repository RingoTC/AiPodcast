package com.example.aipodcast;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.PodcastContent;
import com.example.aipodcast.model.PodcastSegment;
import com.example.aipodcast.service.PodcastGenerator;
import com.example.aipodcast.service.SimplifiedTTSHelper;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Activity for generating and playing podcast content.
 * Handles podcast generation, text-to-speech conversion, and playback controls.
 */
public class PodcastPlayerActivity extends AppCompatActivity {
    private static final String TAG = "PodcastPlayerActivity";
    
    // UI Components
    private TextView podcastTitle;
    private TextView podcastDuration;
    private ChipGroup topicsChipGroup;
    private TextView currentSectionLabel;
    private TextView transcriptText;
    private TextView generationStatus;
    private ProgressBar generationProgress;
    private SeekBar seekBar;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private ImageButton prevButton;
    private FloatingActionButton playPauseButton;
    private ImageButton nextButton;
    private TextView speedValueText;
    private Slider speedSlider;
    
    // Player state
    private boolean isPlaying = false;
    private boolean isPodcastGenerated = false;
    private boolean isSeekBarTracking = false;
    private int currentSegmentIndex = 0;
    private float playbackSpeed = 1.0f;
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    
    // Data
    private ArrayList<String> selectedTopics;
    private int duration;
    private Set<NewsArticle> selectedArticles;
    private PodcastContent podcastContent;
    private File audioFile;
    private boolean useAIGeneration = true; // Default to true
    
    // Services
    private SimplifiedTTSHelper ttsHelper;
    private PodcastGenerator podcastGenerator;
    
    // Runnable for seekbar updates
    private Runnable seekBarUpdater;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcast_player);
        
        // Get data from intent
        Intent intent = getIntent();
        selectedTopics = intent.getStringArrayListExtra("selected_topics");
        duration = intent.getIntExtra("duration", 5);
        useAIGeneration = intent.getBooleanExtra("use_ai_generation", true);
        
        // 修改：接收ArrayList而不是Set
        ArrayList<NewsArticle> articlesList = (ArrayList<NewsArticle>) intent.getSerializableExtra("selected_articles_list");
        if (articlesList != null) {
            selectedArticles = new HashSet<>(articlesList);
        }
        
        if (selectedArticles == null || selectedArticles.isEmpty()) {
            showError("No articles selected for podcast");
            finish();
            return;
        }
        
        // Initialize services
        ttsHelper = new SimplifiedTTSHelper(this);
        podcastGenerator = new PodcastGenerator(selectedArticles, duration, selectedTopics);
        
        // Configure AI generation
        podcastGenerator.setUseAI(useAIGeneration);
        
        // Initialize UI
        initializeViews();
        setupListeners();
        setupProgressTracking();
        
        // Apply animations
        animateUserInterface();
        
        // Start podcast generation
        showGeneratingState(true);
        generatePodcast();
    }
    
    /**
     * Initialize view references
     */
    private void initializeViews() {
        // Header section
        podcastTitle = findViewById(R.id.podcast_title);
        podcastDuration = findViewById(R.id.podcast_duration);
        topicsChipGroup = findViewById(R.id.podcast_topics_chips);
        
        // Content section
        currentSectionLabel = findViewById(R.id.current_section_label);
        transcriptText = findViewById(R.id.transcript_text);
        generationStatus = findViewById(R.id.generation_status);
        generationProgress = findViewById(R.id.generation_progress);
        
        // Player controls
        seekBar = findViewById(R.id.seek_bar);
        currentTimeText = findViewById(R.id.current_time);
        totalTimeText = findViewById(R.id.total_time);
        prevButton = findViewById(R.id.prev_button);
        playPauseButton = findViewById(R.id.play_pause_button);
        nextButton = findViewById(R.id.next_button);
        speedValueText = findViewById(R.id.speed_value);
        speedSlider = findViewById(R.id.speed_slider);
    }
    
    /**
     * Set up listeners for UI components
     */
    private void setupListeners() {
        // Play/pause button
        playPauseButton.setOnClickListener(v -> togglePlayback());
        
        // Previous button
        prevButton.setOnClickListener(v -> skipToPreviousSegment());
        
        // Next button
        nextButton.setOnClickListener(v -> skipToNextSegment());
        
        // Seek bar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateCurrentTimeText(progress);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Pause updates while seeking
                isSeekBarTracking = true;
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Seek to position when user stops dragging
                if (ttsHelper != null && isPodcastGenerated) {
                    ttsHelper.seekTo(seekBar.getProgress() * 1000); // Convert to milliseconds
                }
                isSeekBarTracking = false;
            }
        });
        
        // Speed slider
        speedSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                playbackSpeed = value;
                updateSpeedText();
                if (ttsHelper != null && isPodcastGenerated) {
                    ttsHelper.setPlaybackSpeed(value);
                }
            }
        });
    }
    
    /**
     * Set up progress tracking for TTS
     */
    private void setupProgressTracking() {
        ttsHelper.setProgressCallback(new SimplifiedTTSHelper.ProgressCallback() {
            @Override
            public void onProgress(int currentPosition, int totalDuration, int segmentIndex) {
                runOnUiThread(() -> {
                    if (segmentIndex != currentSegmentIndex) {
                        // Segment changed, show feedback
                        currentSegmentIndex = segmentIndex;
                        highlightCurrentSegment();
                    }
                    
                    if (podcastContent != null && segmentIndex < podcastContent.getSegments().size()) {
                        PodcastSegment segment = podcastContent.getSegments().get(segmentIndex);
                        currentSectionLabel.setText(segment.getTitle());
                        transcriptText.setText(segment.getText());
                    }
                    
                    // Update seek bar and time displays
                    seekBar.setProgress(currentPosition / 1000); // Convert to seconds
                    updateCurrentTimeText(currentPosition / 1000);
                    updateTotalTimeText(totalDuration / 1000);
                });
            }
            
            @Override
            public void onComplete() {
                runOnUiThread(() -> {
                    updatePlayButtonState(false);
                    resetToBeginning();
                    isPlaying = false;
                    stopProgressUpdates();
                });
            }
            
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    showError("Error during playback: " + message);
                    updatePlayButtonState(false);
                    isPlaying = false;
                    stopProgressUpdates();
                });
            }
        });
    }
    
    /**
     * Generate podcast content from selected articles
     */
    private void generatePodcast() {
        if (useAIGeneration) {
            generationStatus.setText("Generating AI conversation podcast...");
        } else {
            generationStatus.setText("Generating standard podcast content...");
        }
        generationProgress.setIndeterminate(true);
        
        // Generate podcast content asynchronously
        podcastGenerator.generateContentAsync()
            .thenAccept(content -> {
                podcastContent = content;
                
                runOnUiThread(() -> {
                    // Update UI with generated content info
                    updatePodcastInfo();
                    
                    // Mark as generated
                    isPodcastGenerated = true;
                    
                    // Hide generating state
                    showGeneratingState(false);
                    
                    // Update total duration
                    updateTotalTimeText(podcastContent.getTotalDuration());
                    
                    // Update UI for player
                    updateUIForPlayerState();
                    
                    // Prepare for direct playback
                    generationStatus.setText("Ready to play");
                });
            })
            .exceptionally(e -> {
                runOnUiThread(() -> {
                    showError("Error generating podcast: " + e.getMessage());
                    showGeneratingState(false);
                });
                return null;
            });
    }
    
    /**
     * Synthesize podcast content to audio file
     */
    private void synthesizeToFile() {
        // 简化版的TTS不支持合成文件，这里直接使用直接播放
        directSpeechPlayback();
    }
    
    /**
     * Use direct speech playback (without file creation)
     */
    private void directSpeechPlayback() {
        if (podcastContent != null) {
            isPlaying = ttsHelper.speakPodcast(podcastContent);
            updatePlayButtonState(isPlaying);
            showGeneratingState(false);
            updateUIForPlayerState();
            
            if (isPlaying) {
                startProgressUpdates();
            }
        }
    }
    
    /**
     * Update podcast info in the UI
     */
    private void updatePodcastInfo() {
        if (podcastContent == null) return;
        
        podcastTitle.setText(podcastContent.getTitle());
        podcastDuration.setText("Duration: " + podcastContent.getFormattedDuration());
        
        // Add topic chips
        topicsChipGroup.removeAllViews();
        for (String topic : podcastContent.getTopics()) {
            Chip chip = new Chip(this);
            chip.setText(topic);
            chip.setClickable(false);
            topicsChipGroup.addView(chip);
        }
    }
    
    /**
     * Start periodic updates of the seek bar progress
     */
    private void startProgressUpdates() {
        // Remove any existing callbacks
        stopProgressUpdates();
        
        // Start updating progress
        progressHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isPlaying && !isSeekBarTracking && isPodcastGenerated && ttsHelper != null) {
                    updateProgress();
                    progressHandler.postDelayed(this, 500); // Update every 500ms
                }
            }
        });
    }
    
    /**
     * Stop periodic progress updates
     */
    private void stopProgressUpdates() {
        progressHandler.removeCallbacksAndMessages(null);
    }
    
    /**
     * Update the progress UI (seekbar and time displays)
     */
    private void updateProgress() {
        if (ttsHelper == null) return;
        
        try {
            int currentPosition = ttsHelper.getCurrentPosition() / 1000; // Convert from ms to seconds
            int totalDuration = ttsHelper.getTotalDuration() / 1000;
            
            // Update seek bar
            seekBar.setMax(totalDuration);
            seekBar.setProgress(currentPosition);
            
            // Update time displays
            updateCurrentTimeText(currentPosition);
            updateTotalTimeText(totalDuration);
        } catch (Exception e) {
            Log.e(TAG, "Error updating progress: " + e.getMessage());
        }
    }
    
    /**
     * Skip to previous segment
     */
    private void skipToPreviousSegment() {
        if (!isPodcastGenerated || podcastContent == null || podcastContent.getSegments().isEmpty()) {
            return;
        }
        
        // Calculate previous segment index
        int prevIndex = Math.max(0, currentSegmentIndex - 1);
        
        // If we're already near the start of the current segment, go to previous segment
        // Otherwise, go to the start of the current segment
        int currentPosition = ttsHelper.getCurrentPosition();
        if (currentPosition < 3000) { // If we're less than 3 seconds into current segment
            seekToSegment(prevIndex);
        } else {
            seekToSegment(currentSegmentIndex); // Go to start of current segment
        }
    }
    
    /**
     * Skip to next segment
     */
    private void skipToNextSegment() {
        if (!isPodcastGenerated || podcastContent == null || podcastContent.getSegments().isEmpty()) {
            return;
        }
        
        int nextIndex = Math.min(currentSegmentIndex + 1, podcastContent.getSegments().size() - 1);
        seekToSegment(nextIndex);
    }
    
    /**
     * Seek to a specific segment
     * 
     * @param segmentIndex Index of segment to seek to
     */
    private void seekToSegment(int segmentIndex) {
        if (!isPodcastGenerated || ttsHelper == null) return;
        
        // Calculate position based on segment index
        int totalDuration = ttsHelper.getTotalDuration();
        float segmentProgress = (float) segmentIndex / podcastContent.getSegments().size();
        int position = (int) (segmentProgress * totalDuration);
        
        // Seek to position
        ttsHelper.seekTo(position);
        currentSegmentIndex = segmentIndex;
        
        // Update UI
        if (segmentIndex < podcastContent.getSegments().size()) {
            PodcastSegment segment = podcastContent.getSegments().get(segmentIndex);
            currentSectionLabel.setText(segment.getTitle());
            transcriptText.setText(segment.getText());
            highlightCurrentSegment();
        }
    }
    
    /**
     * Toggle playback (play/pause)
     */
    private void togglePlayback() {
        if (!isPodcastGenerated) return;
        
        if (isPlaying) {
            ttsHelper.stop();
            isPlaying = false;
            stopProgressUpdates();
        } else {
            if (audioFile != null) {
                isPlaying = ttsHelper.playAudio(audioFile);
                startProgressUpdates();
            } else {
                isPlaying = ttsHelper.speakPodcast(podcastContent);
                startProgressUpdates();
            }
        }
        
        updatePlayButtonState(isPlaying);
    }
    
    /**
     * Update play/pause button state
     * 
     * @param isPlaying True if currently playing
     */
    private void updatePlayButtonState(boolean isPlaying) {
        int icon = isPlaying ? 
                android.R.drawable.ic_media_pause : 
                android.R.drawable.ic_media_play;
        playPauseButton.setImageResource(icon);
        this.isPlaying = isPlaying;
    }
    
    /**
     * Update playback speed text
     */
    private void updateSpeedText() {
        speedValueText.setText(String.format("%.1fx", playbackSpeed));
    }
    
    /**
     * Update the current time text
     * 
     * @param seconds Current position in seconds
     */
    private void updateCurrentTimeText(int seconds) {
        currentTimeText.setText(formatTime(seconds));
    }
    
    /**
     * Update the total time text
     * 
     * @param seconds Total duration in seconds
     */
    private void updateTotalTimeText(int seconds) {
        totalTimeText.setText(formatTime(seconds));
        seekBar.setMax(seconds);
    }
    
    /**
     * Format time value as MM:SS
     * 
     * @param seconds Time in seconds
     * @return Formatted time string
     */
    private String formatTime(int seconds) {
        return String.format("%d:%02d", 
                TimeUnit.SECONDS.toMinutes(seconds),
                seconds % 60);
    }
    
    /**
     * Reset player to beginning
     */
    private void resetToBeginning() {
        currentSegmentIndex = 0;
        if (podcastContent != null && !podcastContent.getSegments().isEmpty()) {
            PodcastSegment segment = podcastContent.getSegments().get(0);
            currentSectionLabel.setText(segment.getTitle());
            transcriptText.setText(segment.getText());
        }
    }
    
    /**
     * Show or hide the generating state UI with animation
     */
    private void showGeneratingState(boolean isGenerating) {
        int visibility = isGenerating ? View.VISIBLE : View.GONE;
        
        if (isGenerating) {
            generationStatus.setAlpha(0f);
            generationProgress.setAlpha(0f);
            
            generationStatus.setVisibility(visibility);
            generationProgress.setVisibility(visibility);
            
            generationStatus.animate().alpha(1f).setDuration(300).start();
            generationProgress.animate().alpha(1f).setDuration(300).start();
        } else {
            generationStatus.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> generationStatus.setVisibility(View.GONE)).start();
            generationProgress.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> generationProgress.setVisibility(View.GONE)).start();
        }
    }
    
    /**
     * Show visual feedback for segment changes
     */
    private void highlightCurrentSegment() {
        if (podcastContent == null || 
            currentSegmentIndex < 0 || 
            currentSegmentIndex >= podcastContent.getSegments().size()) {
            return;
        }
        
        // Flash the background of the text view
        transcriptText.setBackgroundColor(0x22FF0000); // Light red background
        
        // Animate back to normal
        transcriptText.animate()
                .setDuration(500)
                .withEndAction(() -> transcriptText.setBackgroundColor(0x00000000))
                .start();
        
        // Scroll to show current segment
        androidx.core.widget.NestedScrollView scrollView = findViewById(R.id.content_scroll_view);
        View transcriptCard = findViewById(R.id.transcript_card);
        if (scrollView != null && transcriptCard != null) {
            scrollView.smoothScrollTo(0, transcriptCard.getTop());
        }
    }
    
    /**
     * Show error message
     * 
     * @param message Error message to display
     */
    private void showError(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
        Log.e(TAG, message);
    }
    
    /**
     * Apply entrance animations to UI elements
     */
    private void animateUserInterface() {
        // Get references to animated views
        View podcastHeaderCard = findViewById(R.id.podcast_header_card);
        View currentSectionLabel = findViewById(R.id.current_section_label);
        View transcriptCard = findViewById(R.id.transcript_card);
        View playerControls = findViewById(R.id.player_controls);
        
        // Define animation properties
        final int duration = 500;
        final int staggerDelay = 150;
        
        // Animate header card
        podcastHeaderCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(staggerDelay)
                .start();
        
        // Animate section label
        currentSectionLabel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(staggerDelay * 2)
                .start();
        
        // Animate transcript card
        transcriptCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(staggerDelay * 3)
                .start();
        
        // Animate player controls
        playerControls.animate()
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(staggerDelay * 4)
                .start();
    }
    
    /**
     * Update UI based on player state
     */
    private void updateUIForPlayerState() {
        boolean enableControls = isPodcastGenerated;
        
        // Update button states
        prevButton.setEnabled(enableControls);
        playPauseButton.setEnabled(enableControls);
        nextButton.setEnabled(enableControls);
        seekBar.setEnabled(enableControls);
        speedSlider.setEnabled(enableControls);
        
        // Apply visual feedback
        float alpha = enableControls ? 1.0f : 0.5f;
        prevButton.setAlpha(alpha);
        nextButton.setAlpha(alpha);
        speedSlider.setAlpha(alpha);
        
        // Show player controls
        findViewById(R.id.player_controls).setVisibility(View.VISIBLE);
        
        // Set initial segment text
        if (podcastContent != null && !podcastContent.getSegments().isEmpty()) {
            PodcastSegment segment = podcastContent.getSegments().get(0);
            currentSectionLabel.setText(segment.getTitle());
            transcriptText.setText(segment.getText());
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Stop updates when activity is paused
        stopProgressUpdates();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Resume updates if we were playing
        if (isPlaying) {
            startProgressUpdates();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release resources
        stopProgressUpdates();
        progressHandler.removeCallbacksAndMessages(null);
        
        if (ttsHelper != null) {
            ttsHelper.shutdown();
            ttsHelper = null;
        }
    }
} 