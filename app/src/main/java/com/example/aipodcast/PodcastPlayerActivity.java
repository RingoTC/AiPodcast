package com.example.aipodcast;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Html;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.PodcastContent;
import com.example.aipodcast.model.PodcastSegment;
import com.example.aipodcast.service.EnhancedTTSService;
import com.example.aipodcast.service.OpenAIService;
import com.example.aipodcast.service.PodcastGenerator;
import com.example.aipodcast.service.SimplifiedTTSHelper;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private View aiAttributionPanel;
    private androidx.core.widget.NestedScrollView transcriptScrollView;
    
    // Streaming UI elements
    private View hostContainer;
    private TextView hostText;
    private TextView currentWordIndicator;
    
    // Player state
    private boolean isPlaying = false;
    private boolean isPodcastGenerated = false;
    private boolean isSeekBarTracking = false;
    private int currentSegmentIndex = 0;
    private float playbackSpeed = 1.0f;
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private boolean useStreamingMode = false; // Set to false for standard mode only
    private int generationProgressPercent = 0; // Track generation progress
    private static final int MAX_PROGRESS = 100;
    private Handler generationProgressHandler = new Handler(Looper.getMainLooper());
    private boolean isCancelled = false;
    
    // Data
    private ArrayList<String> selectedTopics;
    private int duration;
    private Set<NewsArticle> selectedArticles;
    private PodcastContent podcastContent;
    private File audioFile;
    private boolean useAIGeneration = false; // Set to false for standard mode only
    
    // Services
    private SimplifiedTTSHelper ttsHelper;
    private PodcastGenerator podcastGenerator;
    
    // Runnable for seekbar updates
    private Runnable seekBarUpdater;
    
    // Enhanced services
    private EnhancedTTSService enhancedTTS;
    
    // Direct TTS instance for system TTS fallback
    private TextToSpeech directSystemTTS;
    
    // Add a new member variable to track user interaction with scroll
    private boolean userIsScrolling = false;
    private long lastAutoScrollTime = 0;
    private static final long AUTO_SCROLL_THROTTLE_MS = 1500; // Limit scrolling to once per 1.5 seconds
    
    // 创建一个方法来控制UI更新频率
    private long lastUIUpdateTime = 0;
    private static final long UI_UPDATE_THROTTLE_MS = 500; // 限制UI更新频率为每500毫秒一次
    
    // Add this near the top of the class to ensure TTS initialization completes before attempting playback
    private boolean isTtsInitialized = false;
    
    // Track current playing sentence for highlighting
    private String currentPlayingSentence = "";
    private int currentPlayingSentenceIndex = -1;
    private String[] allSentences = new String[0];
    
    // Add this field to track if the text has changed
    private String lastProcessedText = "";
    
    /**
     * 控制UI更新频率，避免频繁更新导致界面抖动
     * 
     * @return 是否应该更新UI
     */
    private boolean shouldUpdateUI() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUIUpdateTime < UI_UPDATE_THROTTLE_MS) {
            return false;
        }
        lastUIUpdateTime = currentTime;
        return true;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcast_player);
        
        // Get data from intent
        Intent intent = getIntent();
        selectedTopics = intent.getStringArrayListExtra("selected_topics");
        duration = intent.getIntExtra("duration", 5);
        Log.d(TAG, "Received podcast duration: " + duration + " minutes");
        // 使用传入的AI生成设置，不再强制关闭
        useAIGeneration = intent.getBooleanExtra("use_ai_generation", false);
        Log.d(TAG, "Using AI generation: " + useAIGeneration);
        useStreamingMode = false; // We still don't use streaming mode
        
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
        
        // Keep standard TTS for fallback - ensure it initializes properly
        ttsHelper = new SimplifiedTTSHelper(this, new SimplifiedTTSHelper.InitCallback() {
            @Override
            public void onInitialized(boolean success) {
                isTtsInitialized = success;
                if (!success) {
                    Log.e(TAG, "Failed to initialize standard TTS");
                    runOnUiThread(() -> showError("Failed to initialize text-to-speech. Please check system settings."));
                } else {
                    Log.d(TAG, "Standard TTS initialized successfully");
                }
            }
        });
        
        // Initialize podcast generator
        podcastGenerator = new PodcastGenerator(selectedArticles, duration, selectedTopics);
        
        // Configure AI generation
        podcastGenerator.setUseAI(useAIGeneration);
        
        // Initialize UI
        initializeViews();
        setupListeners();
        setupProgressTracking();
        
        // Show AI attribution if using AI generation
        if (useAIGeneration && aiAttributionPanel != null) {
            aiAttributionPanel.setVisibility(View.VISIBLE);
        } else if (aiAttributionPanel != null) {
            aiAttributionPanel.setVisibility(View.GONE);
        }
        
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
        transcriptScrollView = findViewById(R.id.transcript_scroll_view);
        generationStatus = findViewById(R.id.generation_status);
        generationProgress = findViewById(R.id.generation_progress);
        aiAttributionPanel = findViewById(R.id.ai_attribution_panel);
        
        // Add cancel button for generation process
        Button cancelButton = findViewById(R.id.cancel_generation_button);
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> cancelGeneration());
            // Initially hide the cancel button
            cancelButton.setVisibility(View.GONE);
        }
        
        // Player controls
        seekBar = findViewById(R.id.seek_bar);
        currentTimeText = findViewById(R.id.current_time);
        totalTimeText = findViewById(R.id.total_time);
        prevButton = findViewById(R.id.prev_button);
        playPauseButton = findViewById(R.id.play_pause_button);
        nextButton = findViewById(R.id.next_button);
        speedValueText = findViewById(R.id.speed_value);
        speedSlider = findViewById(R.id.speed_slider);
        
        // Set tooltips for the navigation buttons to indicate they jump 10 seconds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            prevButton.setTooltipText("Back 10 seconds");
            nextButton.setTooltipText("Forward 10 seconds");
        }
    }
    
    /**
     * Set up listeners for UI components
     */
    private void setupListeners() {
        // Add scroll listener to detect user interaction
        if (transcriptScrollView != null) {
            transcriptScrollView.setOnScrollChangeListener(
                (View.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    // If the user is manually scrolling (not programmatic)
                    if (Math.abs(scrollY - oldScrollY) > 10) {
                        userIsScrolling = true;
                        
                        // Reset auto-scroll after 5 seconds of user inactivity
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            userIsScrolling = false;
                        }, 5000);
                    }
                }
            );
        }
        
        // Play/pause button
        playPauseButton.setOnClickListener(v -> togglePlayback());
        
        // Previous button - now jumps back 10 seconds
        prevButton.setOnClickListener(v -> {
            skipToPreviousSegment();
            // Show a toast to indicate the action
            android.widget.Toast.makeText(this, "Back 10 seconds", android.widget.Toast.LENGTH_SHORT).show();
        });
        
        // Next button - now jumps forward 10 seconds
        nextButton.setOnClickListener(v -> {
            skipToNextSegment();
            // Show a toast to indicate the action
            android.widget.Toast.makeText(this, "Forward 10 seconds", android.widget.Toast.LENGTH_SHORT).show();
        });
        
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
                
                // Apply speed to the appropriate TTS service
                if (useStreamingMode && enhancedTTS != null) {
                    enhancedTTS.setSpeechRate(value);
                } else if (ttsHelper != null && isPodcastGenerated) {
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
                        // Segment changed, update internal tracking
                        currentSegmentIndex = segmentIndex;
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
        
        // Add word tracking callback to highlight sentences
        ttsHelper.setWordTrackingCallback(new SimplifiedTTSHelper.WordTrackingCallback() {
            @Override
            public void onWordSpoken(String word, int indexInSpeech) {
                runOnUiThread(() -> {
                    // Use the word tracking to determine which sentence is currently being played
                    if (podcastContent != null) {
                        String fullText = podcastContent.getFullText();
                        
                        // Find the current sentence based on word position
                        String currentSentence = findSentenceContainingWord(fullText, word, indexInSpeech);
                        if (currentSentence != null && !currentSentence.equals(currentPlayingSentence)) {
                            currentPlayingSentence = currentSentence;
                            updateTranscriptWithHighlighting(fullText, currentSentence);
                            
                            // Auto-scroll to the highlighted sentence if user isn't manually scrolling
                            if (!userIsScrolling) {
                                scrollToHighlightedSentence();
                            }
                        }
                    }
                });
            }
        });
    }
    
    /**
     * Find the sentence containing the spoken word based on position
     */
    private String findSentenceContainingWord(String text, String word, int wordIndex) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        // Split text into sentences if not already done or text has changed
        if (allSentences.length == 0 || !text.equals(lastProcessedText)) {
            // Better sentence splitting pattern that handles common abbreviations
            allSentences = text.split("(?<=[.!?])(?=\\s+[A-Z]|\\s*$)");
            lastProcessedText = text;
        }
        
        // If we have very few sentences, this might be paragraph splitting instead
        if (allSentences.length <= 3 && text.length() > 500) {
            // Try to split by paragraphs instead
            allSentences = text.split("\\n\\s*\\n");
        }
        
        // Count words to estimate which sentence we're in
        int wordCount = 0;
        for (int i = 0; i < allSentences.length; i++) {
            String sentence = allSentences[i];
            String[] wordsInSentence = sentence.split("\\s+");
            
            wordCount += wordsInSentence.length;
            if (wordCount >= wordIndex) {
                currentPlayingSentenceIndex = i;
                return sentence;
            }
        }
        
        return null;
    }
    
    /**
     * Update transcript text with highlighting for the current sentence
     */
    private void updateTranscriptWithHighlighting(String fullText, String highlightedSentence) {
        if (fullText == null || fullText.isEmpty() || transcriptText == null) {
            return;
        }
        
        // Escape the sentence for regex
        String escapedSentence = highlightedSentence;
        if (escapedSentence != null && !escapedSentence.isEmpty()) {
            escapedSentence = escapedSentence.replaceAll("([\\[\\]\\(\\)\\{\\}\\*\\+\\?\\^\\$\\\\\\.\\|])", "\\\\$1");
            
            // Replace the sentence with highlighted version
            String highlighted = fullText.replaceAll(
                "(" + escapedSentence + ")", 
                "<span style='background-color:#E6E6FA; color:#6200EE;'>$1</span>"
            );
            
            // Display with HTML formatting
            transcriptText.setText(Html.fromHtml(highlighted, Html.FROM_HTML_MODE_COMPACT));
        } else {
            // No highlighting
            transcriptText.setText(fullText);
        }
    }
    
    /**
     * Scroll to the currently highlighted sentence
     */
    private void scrollToHighlightedSentence() {
        if (transcriptScrollView == null || transcriptText == null || 
            currentPlayingSentenceIndex < 0 || userIsScrolling) {
            return;
        }
        
        // Check if we should throttle scrolling
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAutoScrollTime < AUTO_SCROLL_THROTTLE_MS) {
            return;
        }
        lastAutoScrollTime = currentTime;
        
        // Find the text layout to calculate position
        try {
            // Get the text layout
            android.text.Layout layout = transcriptText.getLayout();
            if (layout != null) {
                // Get the bounds of the current sentence in the text
                String fullText = transcriptText.getText().toString();
                int startOfSentence = fullText.indexOf(currentPlayingSentence);
                
                if (startOfSentence >= 0) {
                    // Get the vertical position of this text
                    int lineStart = layout.getLineForOffset(startOfSentence);
                    int y = layout.getLineTop(lineStart);
                    
                    // Scroll to the position with some offset
                    final int scrollTo = Math.max(0, y - 100);
                    transcriptScrollView.smoothScrollTo(0, scrollTo);
                    return;
                }
            }
            
            // Fallback to the approximation method if the layout method doesn't work
            int totalHeight = transcriptText.getHeight();
            int numSentences = allSentences.length;
            
            if (numSentences > 0 && totalHeight > 0) {
                int approxPosition = (currentPlayingSentenceIndex * totalHeight) / numSentences;
                
                // Scroll to the approximate position with some offset
                int scrollPosition = Math.max(0, approxPosition - 100);
                transcriptScrollView.smoothScrollTo(0, scrollPosition);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scrolling to sentence: " + e.getMessage());
        }
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
        
        // Create a retry button during generation
        Button retryButton = findViewById(R.id.cancel_generation_button);
        if (retryButton != null) {
            retryButton.setText("Retry");
            retryButton.setVisibility(View.VISIBLE);
            retryButton.setOnClickListener(v -> {
                if (!isPodcastGenerated) {
                    // Reset state and try again
                    generatePodcast();
                }
            });
        }
        
        // Set a timeout for generation to prevent indefinite waiting
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutHandler.postDelayed(() -> {
            if (!isPodcastGenerated && !isCancelled) {
                // Only show timeout if still generating and not cancelled
                Log.w(TAG, "Podcast generation taking longer than expected");
                runOnUiThread(() -> {
                    generationStatus.setText("Taking longer than expected...");
                    // Make retry button visible
                    if (retryButton != null) {
                        retryButton.setVisibility(View.VISIBLE);
                    }
                });
            }
        }, 30000); // 30 second timeout - reduced from 60 seconds
        
        try {
            // Generate podcast content asynchronously
            podcastGenerator.generateContentAsync()
                .thenAccept(content -> {
                    podcastContent = content;
                    
                    runOnUiThread(() -> {
                        // Cancel timeout handler
                        timeoutHandler.removeCallbacksAndMessages(null);
                        
                        // Hide retry button
                        if (retryButton != null) {
                            retryButton.setVisibility(View.GONE);
                        }
                        
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
                    // Cancel timeout handler
                    timeoutHandler.removeCallbacksAndMessages(null);
                    
                    runOnUiThread(() -> {
                        // Determine error type and provide appropriate message
                        String errorMessage = e.getMessage();
                        if (errorMessage == null) errorMessage = "Unknown error";
                        
                        Log.e(TAG, "Error generating podcast: " + errorMessage);
                        
                        // If using AI generation, try with standard mode instead of showing error immediately
                        if (useAIGeneration) {
                            useAIGeneration = false;
                            useStreamingMode = false;
                            podcastGenerator.setUseAI(false);
                            
                            generationStatus.setText("Switching to standard mode...");
                            
                            // Wait a moment then restart generation in standard mode
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                generatePodcast();
                            }, 1000);
                        } else {
                            // Show appropriate error message for non-AI mode
                            String userMessage;
                            if (errorMessage.contains("API") || errorMessage.contains("connect")) {
                                userMessage = "Connection to OpenAI failed. Please check your internet connection and try again.";
                            } else if (errorMessage.contains("timeout")) {
                                userMessage = "Request timed out. The service may be busy, please try again later.";
                            } else if (errorMessage.contains("rate") || errorMessage.contains("limit")) {
                                userMessage = "API rate limit exceeded. Please try again in a few minutes.";
                            } else {
                                userMessage = "Error generating podcast: " + errorMessage;
                            }
                            
                            // Show error in a non-blocking way
                            showError(userMessage);
                            
                            // Allow retry
                            if (retryButton != null) {
                                retryButton.setVisibility(View.VISIBLE);
                            }
                            
                            showGeneratingState(false);
                        }
                    });
                    return null;
                });
        } catch (Exception e) {
            // Handle exceptions during setup
            Log.e(TAG, "Exception starting podcast generation: " + e.getMessage(), e);
            showError("Error starting podcast generation: " + e.getMessage());
            
            // Allow retry
            if (retryButton != null) {
                retryButton.setVisibility(View.VISIBLE);
            }
        }
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
        if (!isPodcastGenerated || ttsHelper == null) {
            return;
        }
        
        // Skip back 10 seconds
        int currentPosition = ttsHelper.getCurrentPosition();
        int newPosition = Math.max(0, currentPosition - 10000); // 10 seconds in milliseconds
        ttsHelper.seekTo(newPosition);
    }
    
    /**
     * Skip to next segment
     */
    private void skipToNextSegment() {
        if (!isPodcastGenerated || ttsHelper == null) {
            return;
        }
        
        // Skip forward 10 seconds
        int currentPosition = ttsHelper.getCurrentPosition();
        int totalDuration = ttsHelper.getTotalDuration();
        int newPosition = Math.min(totalDuration, currentPosition + 10000); // 10 seconds in milliseconds
        ttsHelper.seekTo(newPosition);
    }
    
    /**
     * Toggle playback (play/pause)
     */
    private void togglePlayback() {
        Log.d(TAG, "togglePlayback called, isPodcastGenerated=" + isPodcastGenerated + 
              ", isPlaying=" + isPlaying + ", isTtsInitialized=" + isTtsInitialized);
        
        if (!isPodcastGenerated) {
            Log.e(TAG, "Cannot toggle playback: podcast not yet generated");
            showError("Podcast content is still being generated. Please wait.");
            return;
        }
        
        if (podcastContent == null || podcastContent.getSegments().isEmpty()) {
            Log.e(TAG, "Cannot toggle playback: podcast content is null or empty");
            showError("No podcast content available to play");
            return;
        }
        
        // Ensure TTS is initialized before trying to play
        if (!isTtsInitialized && !isPlaying) {
            Log.e(TAG, "TTS not initialized yet, showing error");
            showError("Text-to-speech engine is still initializing. Please try again in a moment.");
            
            // Try to re-initialize TTS if needed
            if (ttsHelper == null) {
                ttsHelper = new SimplifiedTTSHelper(this, success -> {
                    isTtsInitialized = success;
                    if (success) {
                        Log.d(TAG, "TTS re-initialized successfully");
                        setupProgressTracking();
                        // Try playback again after successful initialization
                        new Handler(Looper.getMainLooper()).postDelayed(this::togglePlayback, 500);
                    }
                });
            }
            return;
        }
        
        // If using streaming mode, use the chat playback toggle
        if (useStreamingMode && enhancedTTS != null) {
            toggleChatPlayback();
            return;
        }
        
        // Standard playback toggle
        if (isPlaying) {
            // Currently playing, so pause
            Log.d(TAG, "Stopping playback");
            ttsHelper.stop();
            isPlaying = false;
            stopProgressUpdates();
        } else {
            // Currently paused, so play
            Log.d(TAG, "Starting playback, audioFile=" + (audioFile != null ? "exists" : "null"));
            
            if (audioFile != null) {
                isPlaying = ttsHelper.playAudio(audioFile);
            } else {
                // Make sure ttsHelper is fully initialized
                if (ttsHelper == null) {
                    Log.e(TAG, "TTS Helper is null, re-initializing");
                    ttsHelper = new SimplifiedTTSHelper(this, success -> {
                        isTtsInitialized = success;
                        setupProgressTracking();
                    });
                    showError("TTS service needed to be reinitialized. Please try playing again.");
                    return;
                }
                
                // Ensure TTS is initialized
                Log.d(TAG, "Speaking podcast with " + podcastContent.getSegments().size() + " segments");
                
                try {
                    isPlaying = ttsHelper.speakPodcast(podcastContent);
                    
                    if (!isPlaying) {
                        // If standard TTS fails, try fallback to direct system TTS
                        Log.e(TAG, "Failed to start podcast playback with standard TTS, trying fallback method");
                        fallbackToDirectSystemTTS();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception trying to play podcast: " + e.getMessage(), e);
                    showError("Playback error: " + e.getMessage());
                    isPlaying = false;
                }
            }
            
            if (isPlaying) {
                startProgressUpdates();
            } else {
                Log.e(TAG, "Failed to start playback with all methods");
                showTTSErrorDialog();
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
        currentPlayingSentenceIndex = -1;
        currentPlayingSentence = "";
        
        // Reset the highlighted text by showing the full text without highlighting
        if (podcastContent != null) {
            String fullText = podcastContent.getFullText();
            String formattedText = formatTranscriptText(fullText);
            transcriptText.setText(Html.fromHtml(formattedText, Html.FROM_HTML_MODE_COMPACT));
            
            // Scroll back to the top
            if (transcriptScrollView != null) {
                transcriptScrollView.smoothScrollTo(0, 0);
            }
        }
    }
    
    /**
     * Show or hide the generating state UI with animation
     */
    private void showGeneratingState(boolean isGenerating) {
        if (isGenerating) {
            generationStatus.setVisibility(View.VISIBLE);
            generationProgress.setVisibility(View.VISIBLE);
            
            // Show cancel button during generation
            Button cancelButton = findViewById(R.id.cancel_generation_button);
            if (cancelButton != null) {
                cancelButton.setVisibility(View.VISIBLE);
            }
            
            // Hide player controls
            seekBar.setVisibility(View.GONE);
            currentTimeText.setVisibility(View.GONE);
            totalTimeText.setVisibility(View.GONE);
            prevButton.setVisibility(View.GONE);
            playPauseButton.setVisibility(View.GONE);
            nextButton.setVisibility(View.GONE);
            speedValueText.setVisibility(View.GONE);
            speedSlider.setVisibility(View.GONE);
            
            // Streaming specific UI
            if (useStreamingMode) {
                currentWordIndicator.setVisibility(View.VISIBLE);
                hostContainer.setVisibility(View.VISIBLE);
            }
        } else {
            generationStatus.setVisibility(View.GONE);
            generationProgress.setVisibility(View.GONE);
            
            // Hide cancel button when generation is complete
            Button cancelButton = findViewById(R.id.cancel_generation_button);
            if (cancelButton != null) {
                cancelButton.setVisibility(View.GONE);
            }
            
            // Show player controls
            seekBar.setVisibility(View.VISIBLE);
            currentTimeText.setVisibility(View.VISIBLE);
            totalTimeText.setVisibility(View.VISIBLE);
            prevButton.setVisibility(View.VISIBLE);
            playPauseButton.setVisibility(View.VISIBLE);
            nextButton.setVisibility(View.VISIBLE);
            speedValueText.setVisibility(View.VISIBLE);
            speedSlider.setVisibility(View.VISIBLE);
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
        if (transcriptScrollView != null) {
            transcriptScrollView.smoothScrollTo(0, 0);
        }
    }
    
    /**
     * Show error message with retry option
     * 
     * @param message Error message to display
     */
    private void showError(String message) {
        Log.e(TAG, "Error: " + message);
        
        // Use Snackbar for less critical errors with retry option
        Snackbar snackbar = Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_LONG
        );
        
        // Add retry action for generation errors
        if (message.contains("generating") || message.contains("AI") || message.contains("OpenAI")) {
            snackbar.setAction("Retry", v -> {
                if (!isPodcastGenerated) {
                    generatePodcast();
                }
            });
        }
        
        snackbar.show();
        
        // For critical errors, show dialog with more options
        if (message.contains("API") || message.contains("failed") || message.contains("error")) {
            showErrorDialog(message);
        }
    }
    
    /**
     * Show a more detailed error dialog for critical errors
     * 
     * @param message Error message
     */
    private void showErrorDialog(String message) {
        // Create a non-blocking error dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message + "\n\nWhat would you like to do?")
            .setPositiveButton("Retry", (dialog, which) -> {
                // Reset state and restart generation
                isPodcastGenerated = false;
                if (useStreamingMode) {
                    generateStreamingPodcast();
                } else {
                    generatePodcast();
                }
            })
            .setNeutralButton("Switch to Standard Mode", (dialog, which) -> {
                // Disable streaming mode and retry with standard generation
                useStreamingMode = false;
                useAIGeneration = false;
                isPodcastGenerated = false;
                podcastGenerator.setUseAI(false);
                generatePodcast();
            })
            .setNegativeButton("Go Back", (dialog, which) -> {
                // Return to previous screen
                finish();
            })
            .setCancelable(true) // Allow dismissing the dialog
            .show();
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
        
        // Show the full podcast transcript in a single scrollable view
        if (podcastContent != null) {
            // Get the complete transcript text
            String fullText = podcastContent.getFullText();
            String formattedText = formatTranscriptText(fullText);
            transcriptText.setText(Html.fromHtml(formattedText, Html.FROM_HTML_MODE_COMPACT));
            
            // Set the section label to the podcast title
            currentSectionLabel.setText(podcastContent.getTitle());
        }
    }
    
    /**
     * Format the transcript text for better readability
     * 
     * @param text The raw transcript text
     * @return Formatted text
     */
    private String formatTranscriptText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // Clean up the text by removing any HTML that might be present
        String cleanText = text.replaceAll("<[^>]*>", "");
        
        // Add proper paragraph spacing
        cleanText = cleanText.replaceAll("(?m)^\\s*$", "<br/>");
        
        // Add spacing between sentences for better readability
        StringBuilder formatted = new StringBuilder();
        String[] sentences = cleanText.split("(?<=[.!?])\\s+");
        
        for (String sentence : sentences) {
            if (!sentence.trim().isEmpty()) {
                formatted.append(sentence).append(" ");
            }
        }
        
        // Ensure good spacing between paragraphs
        String result = formatted.toString()
            .replaceAll("\\n\\s*\\n", "<br/><br/>")
            .replaceAll("\\n", "<br/>");
        
        return result;
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
        
        // Stop any ongoing operations
        if (enhancedTTS != null) {
            enhancedTTS.shutdown();
            enhancedTTS = null;
        }
        
        // Clean up direct TTS
        if (directSystemTTS != null) {
            directSystemTTS.stop();
            directSystemTTS.shutdown();
            directSystemTTS = null;
        }
        
        // Stop any progress simulations
        stopGenerationProgressSimulation();
        
        // Release any other resources
        generationProgressHandler.removeCallbacksAndMessages(null);
    }
    
    /**
     * Highlight the current speaker in the UI - 修改为单人主持模式
     * 
     * @param speaker The current speaker (any value will highlight the host container)
     */
    private void highlightCurrentSpeaker(String speaker) {
        runOnUiThread(() -> {
            // Updated to use new UI without hostContainer or currentWordIndicator
            // No action needed - we now use sentence highlighting directly in the transcript
        });
    }
    
    /**
     * Highlight the current word being spoken
     * 
     * @param speaker The current speaker
     * @param word The word being spoken
     */
    private void highlightCurrentWord(String speaker, String word) {
        // No action needed - we now use sentence highlighting directly in the transcript
    }
    
    /**
     * Generate podcast content with streaming updates
     */
    private void generateStreamingPodcast() {
        if (!useAIGeneration) {
            // Fall back to standard generation if AI is not enabled
            generatePodcast();
            return;
        }
        
        generationStatus.setText("Connecting to AI...");
        generationProgress.setIndeterminate(false);
        generationProgress.setMax(MAX_PROGRESS);
        generationProgress.setProgress(0);
        generationProgressPercent = 0;
        isCancelled = false;
        
        // Start progress simulation for user feedback
        startGenerationProgressSimulation();
        
        // Initialize UI elements
        hostContainer.setVisibility(View.VISIBLE);
        hostText.setText(""); // Clear content until generation starts
        
        currentSectionLabel.setText("Generating Podcast");
        currentWordIndicator.setVisibility(View.VISIBLE);
        currentWordIndicator.setText("Starting AI generator...");
        
        // Set up a TextView for the full transcript
        TextView fullTranscriptView = findViewById(R.id.transcript_text);
        fullTranscriptView.setVisibility(View.VISIBLE);
        fullTranscriptView.setText("");
        
        // Set a timeout for API connection
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutHandler.postDelayed(() -> {
            if (!isPodcastGenerated && !isCancelled) {
                showErrorDialog("Connection to OpenAI is taking longer than expected. Please check your internet connection.");
            }
        }, 30000); // 30 second timeout for initial connection
        
        try {
            // Reuse the PodcastGenerator's OpenAI service instead of creating a new one
            if (podcastGenerator == null) {
                showErrorDialog("PodcastGenerator not initialized. Please try again.");
                return;
            }
            
            // Get OpenAI service from PodcastGenerator via reflection to avoid modifying its interface
            OpenAIService openAIService = null;
            try {
                java.lang.reflect.Field field = PodcastGenerator.class.getDeclaredField("openAIService");
                field.setAccessible(true);
                openAIService = (OpenAIService) field.get(podcastGenerator);
            } catch (Exception e) {
                Log.e(TAG, "Could not access OpenAIService from PodcastGenerator: " + e.getMessage());
            }
            
            // If we couldn't get it through reflection, create a new one using ApiConfig
            if (openAIService == null) {
                String apiKey = com.example.aipodcast.config.ApiConfig.OPENAI_API_KEY;
                if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your_openai_api_key_here")) {
                timeoutHandler.removeCallbacksAndMessages(null);
                    showErrorDialog("Invalid API key. Please configure a valid OpenAI API key in ApiConfig.");
                return;
                }
                openAIService = new OpenAIService(apiKey);
            }
        
        // Prepare podcast title
        String podcastTitle = "AI Podcast";
        if (selectedTopics != null && !selectedTopics.isEmpty()) {
            podcastTitle = "AI Podcast: " + String.join(", ", selectedTopics);
        }
        
        // Update UI with podcast title
        this.podcastTitle.setText(podcastTitle);
        
            // Final reference for lambda
            final OpenAIService finalOpenAIService = openAIService;
            
            // Create streaming response handler
        OpenAIService.StreamingResponseHandler handler = new OpenAIService.StreamingResponseHandler() {
            @Override
            public void onContentReceived(String content) {
                    // Limit UI updates for better performance
                    if (!shouldUpdateUI()) {
                        return;
                    }
                
                runOnUiThread(() -> {
                        // Clear placeholders 
                        clearPlaceholderTexts();
                        
                        // Update status
                        generationStatus.setText("AI is generating content...");
                        currentWordIndicator.setVisibility(View.VISIBLE);
                        currentWordIndicator.setText("Generating podcast...");
                });
            }
            
            @Override
            public void onFullTranscriptUpdate(String fullTranscript) {
                runOnUiThread(() -> {
                        // Clear placeholders
                        clearPlaceholderTexts();
                        
                        // Format and display transcript
                    String formattedText = formatFullTranscript(fullTranscript);
                        fullTranscriptView.setText(android.text.Html.fromHtml(formattedText, android.text.Html.FROM_HTML_MODE_COMPACT));
                    
                        // Smooth scroll if user isn't interacting
                        smoothScrollToBottom();
                });
            }
            
            @Override
            public void onSpeakerChange(String speaker) {
                Log.d(TAG, "Speaker changed to: " + speaker);
                runOnUiThread(() -> {
                        // Clear placeholders
                        clearPlaceholderTexts();
                        
                    // Update generation status
                        generationStatus.setText("Generating content...");
                        
                        // Show host container
                        hostContainer.setVisibility(View.VISIBLE);
                        hostContainer.setBackgroundColor(0x1A6200EE); // Light purple background
                        currentWordIndicator.setText("HOST is speaking...");
                });
            }
            
            @Override
            public void onTokenReceived(String speaker, String token) {
                    // Limit UI updates for better performance
                    if (!shouldUpdateUI()) {
                        return;
                    }
                    
                    // Filter out common words to reduce UI updates
                    if (token == null || token.isEmpty() || 
                        token.equals("the") || token.equals("and") || 
                        token.equals("is") || token.equals("to") ||
                        token.equals("of") || token.equals("AI") ||
                        token.equals("ai") || token.length() > 20) {
                        return;
                    }
                    
                runOnUiThread(() -> {
                        // Clear placeholders
                        clearPlaceholderTexts();
                        
                        // Update status
                    currentWordIndicator.setVisibility(View.VISIBLE);
                        currentWordIndicator.setText("HOST is speaking...");
                });
            }
            
            @Override
            public void onSpeakerComplete(String speaker, String completeText) {
                    // Less important with full transcript updates
                    Log.d(TAG, "Speaker complete: " + speaker);
            }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error during streaming: " + error);
                    
                    // Cancel timeout handler
                    timeoutHandler.removeCallbacksAndMessages(null);
                    
                    runOnUiThread(() -> {
                        // Stop the progress simulation
                        stopGenerationProgressSimulation();
                        
                        // Determine error type and provide helpful message
                        String userMessage;
                        if (error.contains("API") || error.contains("connect") || error.contains("Connection")) {
                            userMessage = "Connection to OpenAI failed. Please check your internet connection and try again.";
                        } else if (error.contains("timeout") || error.contains("timed out")) {
                            userMessage = "Request timed out. The service may be busy, please try again later.";
                        } else if (error.contains("rate") || error.contains("limit")) {
                            userMessage = "API rate limit exceeded. Please try again in a few minutes.";
                        } else {
                            userMessage = "Error generating podcast: " + error;
                        }
                        
                        showErrorDialog(userMessage);
                        showGeneratingState(false);
                    });
                }
            
            @Override
            public void onComplete(String fullResponse) {
                Log.d(TAG, "Generation complete");
                    
                    // Cancel timeout handler
                    timeoutHandler.removeCallbacksAndMessages(null);
                
                // Create structured content and update UI
                runOnUiThread(() -> {
                    try {
                            // Clean up generation indicators
                            clearGeneratingIndicators();
                            
                        // Hide generation state UI
                        showGeneratingState(false);
                            
                            // Stop progress simulation
                            stopGenerationProgressSimulation();
                        
                        // Mark as generated
                        isPodcastGenerated = true;
                        
                        // Update status indicator
                            currentWordIndicator.setText("Ready to play");
                            currentSectionLabel.setText("AI Podcast");
                        
                        // Create a PodcastContent object for TTS use
                        createPodcastContentFromTranscript(fullResponse);
                        
                        // Update UI for player controls
                        updateUIForPlayerState();
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onComplete: " + e.getMessage());
                        showError("Error processing generated content: " + e.getMessage());
                    }
                });
            }
            };
            
            // Start streaming generation with error handling
            try {
                finalOpenAIService.generatePodcastContentStreaming(
                selectedArticles,
                selectedTopics,
                duration,
                podcastTitle,
                handler
        );
            } catch (Exception e) {
                // Cancel timeout handler
                timeoutHandler.removeCallbacksAndMessages(null);
                
                Log.e(TAG, "Error starting streaming generation: " + e.getMessage());
                showErrorDialog("Failed to start streaming generation: " + e.getMessage());
                showGeneratingState(false);
            }
        } catch (Exception e) {
            // Cancel timeout handler
            if (timeoutHandler != null) {
                timeoutHandler.removeCallbacksAndMessages(null);
            }
            
            Log.e(TAG, "Error initializing streaming: " + e.getMessage());
            showErrorDialog("Failed to initialize streaming: " + e.getMessage());
            showGeneratingState(false);
        }
    }
    
    /**
     * Start a simulated progress indication for generation
     * This provides visual feedback while the actual generation occurs
     */
    private void startGenerationProgressSimulation() {
        // Reset progress
        generationProgressPercent = 0;
        generationProgress.setProgress(0);
        
        // Create a runnable that updates progress
        Runnable progressUpdater = new Runnable() {
            @Override
            public void run() {
                if (isCancelled) {
                    // Stop updating if generation was cancelled
                    return;
                }
                
                if (generationProgressPercent < 95) { // Leave room for completion
                    // Calculate next progress increment
                    // Start fast, then slow down to simulate real progress
                    int increment;
                    if (generationProgressPercent < 30) {
                        increment = 3; // Fast initial progress
                    } else if (generationProgressPercent < 60) {
                        increment = 2; // Medium speed
                    } else {
                        increment = 1; // Slow down near the end
                    }
                    
                    generationProgressPercent += increment;
                    generationProgress.setProgress(generationProgressPercent);
                    
                    // Update status message to keep user informed
                    updateGenerationStatusMessage(generationProgressPercent);
                    
                    // Schedule the next update
                    generationProgressHandler.postDelayed(this, 800);
                }
            }
        };
        
        // Start the progress updates
        generationProgressHandler.post(progressUpdater);
    }
    
    /**
     * Update generation status message based on progress percentage
     */
    private void updateGenerationStatusMessage(int progress) {
        String message;
        if (progress < 20) {
            message = "Connecting to OpenAI...";
        } else if (progress < 40) {
            message = "Analyzing news articles...";
        } else if (progress < 60) {
            message = "Generating podcast script...";
        } else if (progress < 80) {
            message = "Formatting conversation...";
        } else {
            message = "Almost done...";
        }
        
        generationStatus.setText(message);
        
        // 同步更新Current Section以保持一致性
        if (currentSectionLabel != null) {
            // 使用与生成状态相同的消息，保持一致性
            currentSectionLabel.setText(message);
        }
    }
    
    /**
     * Stop generation progress simulation
     */
    private void stopGenerationProgressSimulation() {
        // Remove all pending progress updates
        generationProgressHandler.removeCallbacksAndMessages(null);
        
        // Set progress to 100% to indicate completion
        generationProgressPercent = MAX_PROGRESS;
        generationProgress.setProgress(MAX_PROGRESS);
    }
    
    /**
     * Play a specific segment with the appropriate voice
     *
     * @param speaker The speaker identifier ("HOST")
     * @param text Text content to play
     */
    private void playStreamingSegment(String speaker, String text) {
        if (text == null || text.isEmpty()) {
            Log.e(TAG, "Play text is empty");
            showError("No content to play");
            return;
        }
        
        Log.d(TAG, "Playing " + speaker + " content, length: " + text.length());
        
        // Clean up any placeholder text
        text = text.replace("preparing to connect to AI...", "")
                  .replace("received content: Starting generation...", "")
                  .replace("processing...", "")
                  .trim();
        
        if (text.isEmpty()) {
            Log.e(TAG, "Text is empty after cleanup");
            showError("No content to play after cleanup");
            return;
        }
        
        // Check if text is too long for TTS (some engines have size limits)
        final int MAX_TTS_LENGTH = 3500; // Characters
        if (text.length() > MAX_TTS_LENGTH) {
            Log.d(TAG, "Text too long for TTS (" + text.length() + " chars), splitting into chunks");
            playLongTextInChunks(speaker, text, MAX_TTS_LENGTH);
            return;
        }
        
        // Show current playback status
        currentWordIndicator.setText("Playing " + speaker + " content");
        currentWordIndicator.setVisibility(View.VISIBLE);
        
        // Highlight current speaker
        highlightCurrentSpeaker(speaker);
        
        // Determine which TTS engine to use
        if (enhancedTTS != null && enhancedTTS.isInitialized()) {
            try {
            boolean success = enhancedTTS.speak(speaker, text);
            if (!success) {
                Log.e(TAG, "Enhanced TTS playback failed");
                
                // Fall back to standard TTS
                    tryFallbackTTS(speaker, text);
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception during TTS playback: " + e.getMessage());
                tryFallbackTTS(speaker, text);
            }
        } else {
            // Use standard TTS if enhanced not available
            tryFallbackTTS(speaker, text);
        }
    }
    
    /**
     * Split long text and play it in manageable chunks
     * 
     * @param speaker Speaker identifier
     * @param text Full text content
     * @param maxChunkSize Maximum characters per chunk
     */
    private void playLongTextInChunks(String speaker, String text, int maxChunkSize) {
        try {
            List<String> chunks = new ArrayList<>();
            
            // Try to split at paragraph or sentence boundaries
            String[] paragraphs = text.split("\n\n");
            
            StringBuilder currentChunk = new StringBuilder();
            for (String paragraph : paragraphs) {
                // If adding this paragraph would exceed our chunk size
                if (currentChunk.length() + paragraph.length() > maxChunkSize) {
                    // If the current chunk already has content, add it to our chunks
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString());
                        currentChunk = new StringBuilder();
                    }
                    
                    // If the paragraph itself is too big, split it into sentences
                    if (paragraph.length() > maxChunkSize) {
                        String[] sentences = paragraph.split("(?<=[.!?])\\s+");
                        for (String sentence : sentences) {
                            if (currentChunk.length() + sentence.length() > maxChunkSize) {
                                if (currentChunk.length() > 0) {
                                    chunks.add(currentChunk.toString());
                                    currentChunk = new StringBuilder();
                                }
                                
                                // If even a single sentence is too long, we'll have to split arbitrarily
                                if (sentence.length() > maxChunkSize) {
                                    int start = 0;
                                    while (start < sentence.length()) {
                                        int end = Math.min(start + maxChunkSize, sentence.length());
                                        chunks.add(sentence.substring(start, end));
                                        start = end;
                                    }
                } else {
                                    currentChunk.append(sentence).append(" ");
                                }
                            } else {
                                currentChunk.append(sentence).append(" ");
                            }
                }
            } else {
                        currentChunk.append(paragraph).append("\n\n");
                    }
                } else {
                    currentChunk.append(paragraph).append("\n\n");
                }
            }
            
            // Add the last chunk if it has content
            if (currentChunk.length() > 0) {
                chunks.add(currentChunk.toString());
            }
            
            Log.d(TAG, "Split text into " + chunks.size() + " chunks for TTS");
            
            // Now play the first chunk, and set up callbacks for the rest
            if (!chunks.isEmpty()) {
                playChunksSequentially(speaker, chunks, 0);
            } else {
                showError("Error preparing text for playback");
                isPlaying = false;
                updatePlayButtonState(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error splitting text: " + e.getMessage());
            
            // Try playing with standard TTS as fallback
            tryFallbackTTS(speaker, text);
        }
    }
    
    /**
     * Play chunks of text sequentially
     * 
     * @param speaker Speaker identifier
     * @param chunks List of text chunks
     * @param index Current chunk index
     */
    private void playChunksSequentially(String speaker, List<String> chunks, int index) {
        if (index >= chunks.size() || !isPlaying) {
            // We're done with all chunks or playback was stopped
            return;
        }
        
        String chunk = chunks.get(index);
        
        // Use enhanced TTS with a completion callback
        if (enhancedTTS != null && enhancedTTS.isInitialized()) {
            // Set a one-time completion listener
            final int nextIndex = index + 1;
            EnhancedTTSService.TTSCallback originalCallback = null;
            
            // Create temporary callback that chains to the next chunk
            EnhancedTTSService.TTSCallback chunkCallback = new EnhancedTTSService.TTSCallback() {
                @Override
                public void onSpeakStart(String speaker) {
                    if (callback != null) callback.onSpeakStart(speaker);
                }
                
                @Override
                public void onWordSpoken(String speaker, String word, int wordIndex) {
                    if (callback != null) callback.onWordSpoken(speaker, word, wordIndex);
                }
                
                @Override
                public void onSpeakComplete(String speaker) {
                    if (callback != null) callback.onSpeakComplete(speaker);
                    
                    // Continue with next chunk
                    if (isPlaying) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            playChunksSequentially(speaker, chunks, nextIndex);
                        }, 300);
                    }
                }
                
                @Override
                public void onInitialized() {
                    if (callback != null) callback.onInitialized();
                }
                
                @Override
                public void onError(String message) {
                    if (callback != null) callback.onError(message);
                    
                    // Try next chunk even if there was an error
                    if (isPlaying) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            playChunksSequentially(speaker, chunks, nextIndex);
                        }, 300);
                    }
                }
                
                // Reference to original callback for chain restoration
                private EnhancedTTSService.TTSCallback callback = originalCallback;
            };
            
            // Speak the current chunk
            boolean success = enhancedTTS.speak(speaker, chunk);
            if (!success && isPlaying) {
                // If failed, try next chunk
                playChunksSequentially(speaker, chunks, index + 1);
            }
        } else {
            // Fallback to standard TTS
            if (ttsHelper != null) {
                String prefixedChunk = speaker + ": " + chunk;
                boolean success = ttsHelper.speak(prefixedChunk);
                
                if (success) {
                    // Schedule next chunk with a delay
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isPlaying) {
                            playChunksSequentially(speaker, chunks, index + 1);
                        }
                    }, chunk.length() * 50); // Rough estimate of completion time
                } else if (isPlaying) {
                    // Try next chunk
                    playChunksSequentially(speaker, chunks, index + 1);
                }
            }
        }
    }
    
    /**
     * Attempt to use the fallback TTS when enhanced TTS fails
     * 
     * @param speaker Speaker identifier
     * @param text Text to speak
     */
    private void tryFallbackTTS(String speaker, String text) {
        if (ttsHelper != null) {
            String prefixedText = speaker + ": " + text;
            boolean success = ttsHelper.speak(prefixedText);
            if (!success) {
                showError("TTS playback failed");
                isPlaying = false;
                updatePlayButtonState(false);
            }
        } else {
            showError("TTS service not initialized");
            isPlaying = false;
            updatePlayButtonState(false);
        }
    }
    
    /**
     * Toggle playback of conversation TTS
     */
    private void toggleChatPlayback() {
        if (!isPodcastGenerated) {
            showError("Content is still generating, please wait...");
            return;
        }
        
        if (isPlaying) {
            // Stop playback
            Log.d(TAG, "Stopping playback");
            if (enhancedTTS != null) {
                enhancedTTS.stop();
            } else if (ttsHelper != null) {
                ttsHelper.stop();
            }
            
            // Also stop direct system TTS if active
            if (directSystemTTS != null) {
                directSystemTTS.stop();
            }
            
            isPlaying = false;
            updatePlayButtonState(false);
        } else {
            // Start playback
            Log.d(TAG, "Starting playback");
            
            // First test the TTS engine to ensure it's working
            boolean ttsTestSuccessful = false;
            if (enhancedTTS != null && enhancedTTS.isInitialized()) {
                Log.d(TAG, "Testing Enhanced TTS engine...");
                ttsTestSuccessful = enhancedTTS.testTTS();
                Log.d(TAG, "Enhanced TTS test result: " + ttsTestSuccessful);
            }
            
            // Get the content to play
            String contentToPlay = "";
            if (transcriptText != null && transcriptText.getText().length() > 0) {
                contentToPlay = android.text.Html.fromHtml(
                    transcriptText.getText().toString(),
                    android.text.Html.FROM_HTML_MODE_LEGACY
                ).toString();
                Log.d(TAG, "Using transcript text content, length: " + contentToPlay.length());
            }
            
            // Check if content is available
            if (contentToPlay.isEmpty()) {
                if (podcastContent != null) {
                    contentToPlay = podcastContent.getFullText();
                    Log.d(TAG, "Using podcast content text, length: " + contentToPlay.length());
                }
            }
            
            if (contentToPlay.isEmpty()) {
                showError("No playable content found");
                return;
            }
            
            // Try to play the content
            Log.d(TAG, "Playing content, length: " + contentToPlay.length());
            boolean success = false;
            
            if (enhancedTTS != null && enhancedTTS.isInitialized()) {
                success = enhancedTTS.speak("HOST", contentToPlay);
            }
            
            if (!success && ttsHelper != null) {
                success = ttsHelper.speak(contentToPlay);
            }
            
            if (!success) {
                fallbackToDirectSystemTTS();
            } else {
                isPlaying = true;
                updatePlayButtonState(true);
            }
        }
    }
    
    /**
     * Initialize and play content using Android's direct TTS engine
     * 
     * @param contentToPlay The text content to play
     */
    private void initializeAndPlayWithDirectTTS(String contentToPlay) {
        // Clean up any existing TTS instance
        if (directSystemTTS != null) {
            directSystemTTS.stop();
            directSystemTTS.shutdown();
        }
        
        // Create a new direct TTS instance
        directSystemTTS = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "Direct TTS initialized successfully");
                directSystemTTS.setLanguage(Locale.US);
                directSystemTTS.setSpeechRate(1.0f);
                
                // Show message to user
                runOnUiThread(() -> {
                    currentWordIndicator.setText("Playing with system TTS...");
                });
                
                // Speak using direct TTS
                String utteranceId = "DIRECT_" + System.currentTimeMillis();
                
                directSystemTTS.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override
                    public void onStart(String s) {
                        Log.d(TAG, "Direct TTS started");
                    }
                    
                    @Override
                    public void onDone(String s) {
                        Log.d(TAG, "Direct TTS completed");
                        runOnUiThread(() -> {
                            isPlaying = false;
                            updatePlayButtonState(false);
                            currentWordIndicator.setText("Playback complete");
                        });
                    }
                    
                    @Override
                    public void onError(String s) {
                        Log.e(TAG, "Direct TTS error: " + s);
                        runOnUiThread(() -> {
                            showError("System TTS error: " + s);
                            isPlaying = false;
                            updatePlayButtonState(false);
                        });
                    }
                });
                
                // Speak the content
                int result;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Bundle params = new Bundle();
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                    result = directSystemTTS.speak(contentToPlay, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
                } else {
                    HashMap<String, String> params = new HashMap<>();
                    params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                    result = directSystemTTS.speak(contentToPlay, TextToSpeech.QUEUE_FLUSH, params);
                }
                Log.d(TAG, "Direct TTS speak result: " + result);
                
                if (result == TextToSpeech.ERROR) {
                    // If even this fails, try a super short message
                    runOnUiThread(() -> {
                        showTTSErrorDialog();
                        isPlaying = false;
                        updatePlayButtonState(false);
                    });
                }
            } else {
                Log.e(TAG, "Failed to initialize direct TTS: " + status);
                runOnUiThread(() -> {
                    showError("Failed to initialize system TTS");
                    isPlaying = false;
                    updatePlayButtonState(false);
                    showTTSErrorDialog();
                });
            }
        });
    }
    
    /**
     * Show dialog with TTS troubleshooting options
     */
    private void showTTSErrorDialog() {
        // First attempt to check if system TTS is available and working
        boolean systemTtsAvailable = checkSystemTtsAvailable();
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Failed to start playback")
            .setMessage("There was a problem playing the podcast audio.\n\n" + 
                       (systemTtsAvailable ? 
                        "The system Text-to-Speech (TTS) engine appears to be available, but encountered an error with this content." : 
                        "The system Text-to-Speech (TTS) engine may not be properly configured."))
            .setPositiveButton("Try Again", (dialog, which) -> {
                // Try a simpler method to play content
                if (podcastContent != null) {
                    try {
                        // Use the simplest approach possible
                        String shortContent = getShortSampleText();
                        if (ttsHelper != null) {
                            ttsHelper.speak(shortContent);
                        }
                    } catch (Exception e) {
                        showError("Could not initialize TTS: " + e.getMessage());
                    }
                }
            })
            .setNeutralButton("TTS Settings", (dialog, which) -> {
                try {
                    // Try to open Android's TTS settings
                    Intent intent = new Intent();
                    intent.setAction("com.android.settings.TTS_SETTINGS");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        // Alternative way to open TTS settings
                        Intent intent = new Intent();
                        intent.setAction(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        startActivity(intent);
                    } catch (Exception e2) {
                        showError("Could not open settings");
                    }
                }
            })
            .setNegativeButton("Read Text Only", (dialog, which) -> {
                // Just show transcript without audio
                transcriptText.setVisibility(View.VISIBLE);
                if (hostContainer != null) {
                    hostContainer.setVisibility(View.GONE);
                }
                currentWordIndicator.setText("Audio playback unavailable. Reading transcript only.");
                
                // Show a snackbar with instructions
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "You can now read the podcast transcript directly.",
                    Snackbar.LENGTH_LONG
                ).show();
            })
            .show();
        
        // 初始化检查TTS是否可用
        TextToSpeech testTts = null;
        try {
            testTts = new TextToSpeech(this, status -> {
                // 不需要在这里处理Status，只是检查TTS是否可以初始化
            });
        } catch (Exception e) {
            Log.e(TAG, "Error creating test TTS: " + e.getMessage());
        } finally {
            if (testTts != null) {
                testTts.shutdown(); // Clean up
            }
        }
    }
    
    /**
     * Check if system TTS is available and working
     * 
     * @return True if system TTS is available
     */
    private boolean checkSystemTtsAvailable() {
        final boolean[] available = {false};
        final boolean[] initialized = {false};
        
        // 初始化检查TTS是否可用
        TextToSpeech testTts = null;
        try {
            testTts = new TextToSpeech(this, status -> {
                available[0] = (status == TextToSpeech.SUCCESS);
                initialized[0] = true;
                synchronized (available) {
                    available.notify();
                }
            });
            
            // 等待初始化完成
            synchronized (available) {
                try {
                    if (!initialized[0]) {
                        available.wait(2000); // 最多等待2秒
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "TTS check interrupted: " + e.getMessage());
                }
            }
            
            return available[0];
        } catch (Exception e) {
            Log.e(TAG, "Error checking TTS availability: " + e.getMessage());
            return false;
        } finally {
            final TextToSpeech tts = testTts;
            if (tts != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        tts.shutdown();
                    } catch (Exception e) {
                        Log.e(TAG, "Error shutting down test TTS: " + e.getMessage());
                    }
                });
            }
        }
    }
    
    /**
     * Get a short sample of text suitable for testing TTS
     * 
     * @return Short text sample
     */
    private String getShortSampleText() {
        if (podcastContent != null && podcastContent.getSegments().size() > 0) {
            PodcastSegment segment = podcastContent.getSegments().get(0);
            String text = segment.getText();
            
            if (text != null && !text.isEmpty()) {
                // Get first sentence or up to 50 chars
                int endIndex = text.indexOf(". ");
                if (endIndex > 10 && endIndex < 100) {
                    return text.substring(0, endIndex + 1);
                } else {
                    return text.substring(0, Math.min(50, text.length()));
                }
            }
        }
        
        // Default test text
        return "This is a test of the text to speech system.";
    }
    
    /**
     * Format full transcript text for display as HTML
     * 
     * @param fullTranscript The raw transcript text
     * @return Formatted HTML string
     */
    private String formatFullTranscript(String fullTranscript) {
        if (fullTranscript == null || fullTranscript.isEmpty()) {
            return "";
        }
        
        // Clean up generation status text at beginning
        String cleanTranscript = fullTranscript;
        String[] statusPrefixes = {
            "AI is generating", "Starting generation", "Generating",
            "Connecting", "API connected", "processing"
        };
        
        for (String prefix : statusPrefixes) {
            if (cleanTranscript.startsWith(prefix)) {
                int contentStart = cleanTranscript.indexOf("§HOST§");
            if (contentStart > 0) {
                cleanTranscript = cleanTranscript.substring(contentStart);
                    break;
                }
            }
        }
        
        // Replace host marker with styled HTML tag
        String formatted = cleanTranscript
            .replace("§HOST§", "<span style='color:#6200EE; font-weight:bold; display:block; margin-top:16dp'>HOST: </span>")
            // Legacy marker support
            .replace("§ALEX§", "<span style='color:#6200EE; font-weight:bold; display:block; margin-top:16dp'>HOST: </span>")
            .replace("§JORDAN§", "<span style='color:#6200EE; font-weight:bold; display:block; margin-top:16dp'>HOST: </span>");
        
        // Add paragraph breaks for readability
        formatted = formatted.replace("\n\n", "<br><br>")
                           .replace("\n", "<br>");
        
        // Remove any remaining status messages
        formatted = formatted.replace("AI is generating", "")
                         .replace("Starting generation", "")
                         .replace("Analyzing news", "")
                         .replace("Formatting conversation", "");
        
        return formatted;
    }
    
    /**
     * Create a PodcastContent object from transcript text
     */
    private void createPodcastContentFromTranscript(String transcript) {
        if (transcript == null || transcript.isEmpty()) {
            Log.e(TAG, "Empty transcript");
            return;
        }
        
        // Create a simplified PodcastContent object
        PodcastContent content = new PodcastContent();
        content.setTitle(podcastTitle.getText().toString());
        // Don't need to set duration directly, it will be calculated from segments
        
        // Get duration from preference or default
        int estimatedDuration = Math.max(120, duration * 60); // At least 2 minutes
        content.setTotalDuration(estimatedDuration);
        
        // Set transcript text
        content.setTranscriptText(transcript);
        
        // Create a single segment for playback
        PodcastSegment segment = new PodcastSegment();
        segment.setTitle("Podcast");
        segment.setText(transcript);
        segment.setType(PodcastSegment.SegmentType.NEWS_ARTICLE);
        
        // Add the segment to the content
        List<PodcastSegment> segments = new ArrayList<>();
        segments.add(segment);
        content.setSegments(segments);
        
        // Use selected topics for topic list
        content.setTopics(selectedTopics != null ? new ArrayList<>(selectedTopics) : new ArrayList<>());
        
        // Save as current podcast content
        this.podcastContent = content;
        
        // Update UI
        updatePodcastInfo();
        
        // Set content for TTS playback using speakPodcast method
        if (ttsHelper != null) {
            ttsHelper.speakPodcast(content);
        }
    }
    
    /**
     * Cancel the current generation process
     */
    private void cancelGeneration() {
        // Show confirmation dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cancel Generation")
            .setMessage("Are you sure you want to cancel the podcast generation?")
            .setPositiveButton("Yes", (dialog, which) -> {
                // Mark as cancelled to stop progress updates
                isCancelled = true;
                
                // Stop progress simulation
                stopGenerationProgressSimulation();
                
                // Stop any API requests in progress
                if (enhancedTTS != null) {
                    enhancedTTS.stop();
                }
                
                // Update UI
                showGeneratingState(false);
                generationStatus.setText("Generation cancelled");
                
                // Provide options to retry or return
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Generation Cancelled")
                    .setMessage("Would you like to retry with different settings or return to topic selection?")
                    .setPositiveButton("Retry", (retryDialog, retryWhich) -> {
                        // Reset state and restart generation
                        isPodcastGenerated = false;
                        generatePodcast();
                    })
                    .setNegativeButton("Return", (retryDialog, retryWhich) -> {
                        // Return to previous screen
                        finish();
                    })
                    .show();
            })
            .setNegativeButton("No", null)
            .show();
    }
    
    /**
     * Clear any placeholder texts in speaker containers
     */
    private void clearPlaceholderTexts() {
        runOnUiThread(() -> {
            // Check if text contains placeholder messages - 只检查单一主持人容器
            String hostCurrentText = hostText.getText().toString();
            
            if (hostCurrentText.contains("waiting") || 
                hostCurrentText.contains("preparing") || 
                hostCurrentText.contains("Preparing") ||
                hostCurrentText.contains("connect")) {
                hostText.setText("");
            }
        });
    }
    
    /**
     * Smooth scroll to the bottom of the transcript with throttling
     */
    private void smoothScrollToBottom() {
        // Don't scroll if user is manually scrolling
        if (userIsScrolling) {
            return;
        }
        
        // Throttle auto-scrolling to avoid too frequent updates
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAutoScrollTime < AUTO_SCROLL_THROTTLE_MS) {
            return;
        }
        
        // Update the last scroll time
        lastAutoScrollTime = currentTime;
        
        // Perform smooth scroll
        if (transcriptScrollView != null) {
            transcriptScrollView.post(() -> {
                // Smooth scroll instead of jumping
                transcriptScrollView.smoothScrollTo(0, transcriptScrollView.getChildAt(0).getHeight());
            });
        }
    }
    
    /**
     * 清除所有生成状态指示器
     */
    private void clearGeneratingIndicators() {
        // Update the code to not use hostContainer and hostText
        if (transcriptText != null) {
            transcriptText.setVisibility(View.VISIBLE);
        }
        
        generationProgress.setVisibility(View.GONE);
    }
    
    /**
     * Fall back to standard TTS when enhanced TTS fails
     * 
     * @param content Text content to speak
     */
    private void fallbackToStandardTTS(String content) {
        if (ttsHelper != null) {
            // Try with podcast content first if available
            boolean success = false;
            if (podcastContent != null) {
                success = ttsHelper.speakPodcast(podcastContent);
            }
            
            // If that fails or no podcast content, try direct speech
            if (!success) {
                success = ttsHelper.speak(content);
                if (!success) {
                    showError("All TTS playback methods failed. Please try again later.");
                    isPlaying = false;
                    updatePlayButtonState(false);
                    
                    // Show dialog with troubleshooting options
                    showTTSErrorDialog();
                }
            }
        } else {
            showError("TTS service not initialized");
            isPlaying = false;
            updatePlayButtonState(false);
        }
    }
    
    /**
     * Fallback to direct system TTS when all else fails
     */
    private void fallbackToDirectSystemTTS() {
        try {
            // 创建一个简短的测试内容
            String content = getShortSampleText();
            directSystemTTS = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    directSystemTTS.setLanguage(Locale.US);
                    
                    // 注册监听器
                    directSystemTTS.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                            runOnUiThread(() -> {
                                isPlaying = true;
                                updatePlayButtonState(true);
                            });
                        }
                        
                        @Override
                        public void onDone(String utteranceId) {
                            runOnUiThread(() -> {
                                isPlaying = false;
                                updatePlayButtonState(false);
                            });
                        }
                        
                        @Override
                        public void onError(String utteranceId) {
                            runOnUiThread(() -> {
                                isPlaying = false;
                                updatePlayButtonState(false);
                                showError("TTS playback error");
                            });
                        }
                    });
                    
                    // 开始播放
                    String utteranceId = "directTTS_" + System.currentTimeMillis();
                    
                    // 修复：正确创建Bundle而不是HashMap
                    Bundle params = new Bundle();
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                    
                    int result = directSystemTTS.speak(content, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
                    if (result != TextToSpeech.SUCCESS) {
                        showError("Failed to play content with TTS");
                    }
                } else {
                    showError("Failed to initialize system TTS");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in direct TTS: " + e.getMessage());
            showError("Could not initialize TTS: " + e.getMessage());
        }
    }
} 