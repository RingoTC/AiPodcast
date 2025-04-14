package com.example.aipodcast;

import android.content.Intent;
import android.os.Bundle;
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
import com.example.aipodcast.service.EnhancedTTSHelper;
import com.example.aipodcast.service.PodcastGenerator;
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
    private int currentSegmentIndex = 0;
    private float playbackSpeed = 1.0f;
    
    // Data
    private ArrayList<String> selectedTopics;
    private int duration;
    private Set<NewsArticle> selectedArticles;
    private PodcastContent podcastContent;
    private File audioFile;
    
    // Services
    private EnhancedTTSHelper ttsHelper;
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
        ttsHelper = new EnhancedTTSHelper(this);
        podcastGenerator = new PodcastGenerator(selectedArticles, duration, selectedTopics);
        
        // Initialize UI
        initializeViews();
        setupListeners();
        setupProgressTracking();
        
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
                // Optional: Pause updates while seeking
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Implement seeking to position
                // ttsHelper.seekTo(seekBar.getProgress());
            }
        });
        
        // Speed slider
        speedSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                playbackSpeed = value;
                updateSpeedText();
                // ttsHelper.setPlaybackSpeed(value);
            }
        });
    }
    
    /**
     * Set up progress tracking for TTS
     */
    private void setupProgressTracking() {
        ttsHelper.setProgressListener(new EnhancedTTSHelper.ProgressListener() {
            @Override
            public void onPrepared() {
                runOnUiThread(() -> {
                    showGeneratingState(false);
                    updatePlayButtonState(true);
                });
            }
            
            @Override
            public void onProgress(int segmentIndex, int totalSegments, String currentText) {
                runOnUiThread(() -> {
                    currentSegmentIndex = segmentIndex;
                    if (podcastContent != null && segmentIndex < podcastContent.getSegments().size()) {
                        PodcastSegment segment = podcastContent.getSegments().get(segmentIndex);
                        currentSectionLabel.setText(segment.getTitle());
                        transcriptText.setText(segment.getText());
                    }
                });
            }
            
            @Override
            public void onSegmentComplete(int segmentIndex) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Segment complete: " + segmentIndex);
                });
            }
            
            @Override
            public void onComplete() {
                runOnUiThread(() -> {
                    updatePlayButtonState(false);
                    resetToBeginning();
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    showError("Error during playback: " + errorMessage);
                    updatePlayButtonState(false);
                });
            }
        });
    }
    
    /**
     * Generate podcast content from selected articles
     */
    private void generatePodcast() {
        generationStatus.setText("Generating podcast content...");
        generationProgress.setIndeterminate(true);
        
        // Generate podcast content asynchronously
        podcastGenerator.generateContentAsync()
            .thenAccept(content -> {
                podcastContent = content;
                
                runOnUiThread(() -> {
                    // Update UI with generated content info
                    updatePodcastInfo();
                    
                    // Prepare for TTS
                    generationStatus.setText("Converting to speech...");
                    
                    // Option 1: Synthesize to file for better playback control
                    synthesizeToFile();
                    
                    // Option 2: Direct speech without file creation
                    // directSpeechPlayback();
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
        ttsHelper.synthesizeToFile(podcastContent)
            .thenAccept(file -> {
                audioFile = file;
                isPodcastGenerated = true;
                
                runOnUiThread(() -> {
                    showGeneratingState(false);
                    updatePlayButtonState(false);
                    updateTotalTimeText(podcastContent.getTotalDuration());
                });
            })
            .exceptionally(e -> {
                runOnUiThread(() -> {
                    showError("Error synthesizing audio: " + e.getMessage());
                    showGeneratingState(false);
                });
                return null;
            });
    }
    
    /**
     * Use direct speech playback (without file creation)
     */
    private void directSpeechPlayback() {
        if (podcastContent != null) {
            isPlaying = ttsHelper.speak(podcastContent);
            updatePlayButtonState(isPlaying);
            showGeneratingState(false);
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
     * Toggle playback (play/pause)
     */
    private void togglePlayback() {
        if (!isPodcastGenerated) return;
        
        if (isPlaying) {
            ttsHelper.stop();
            isPlaying = false;
        } else {
            if (audioFile != null) {
                ttsHelper.playAudioFile(audioFile, podcastContent);
                isPlaying = true;
            } else {
                isPlaying = ttsHelper.speak(podcastContent);
            }
        }
        
        updatePlayButtonState(isPlaying);
    }
    
    /**
     * Skip to previous segment
     */
    private void skipToPreviousSegment() {
        // To be implemented
        showError("Skip to previous not implemented yet");
    }
    
    /**
     * Skip to next segment
     */
    private void skipToNextSegment() {
        // To be implemented
        showError("Skip to next not implemented yet");
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
     * Show or hide the generating state UI
     * 
     * @param isGenerating True to show generating UI, false to hide
     */
    private void showGeneratingState(boolean isGenerating) {
        int visibility = isGenerating ? View.VISIBLE : View.GONE;
        generationStatus.setVisibility(visibility);
        generationProgress.setVisibility(visibility);
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
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ttsHelper != null) {
            ttsHelper.shutdown();
        }
    }
} 