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
import com.example.aipodcast.repository.NewsRepository;
import com.example.aipodcast.repository.NewsRepositoryProvider;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
public class PodcastPlayerActivity extends AppCompatActivity {
    private static final String TAG = "PodcastPlayerActivity";
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
    private View hostContainer;
    private TextView hostText;
    private TextView currentWordIndicator;
    private boolean isPlaying = false;
    private boolean isPodcastGenerated = false;
    private boolean isSeekBarTracking = false;
    private int currentSegmentIndex = 0;
    private float playbackSpeed = 1.0f;
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private boolean useStreamingMode = false; 
    private int generationProgressPercent = 0; 
    private static final int MAX_PROGRESS = 100;
    private Handler generationProgressHandler = new Handler(Looper.getMainLooper());
    private boolean isCancelled = false;
    private ArrayList<String> selectedTopics;
    private int duration;
    private Set<NewsArticle> selectedArticles;
    private PodcastContent podcastContent;
    private File audioFile;
    private boolean useAIGeneration = false; 
    private SimplifiedTTSHelper ttsHelper;
    private PodcastGenerator podcastGenerator;
    private Runnable seekBarUpdater;
    private EnhancedTTSService enhancedTTS;
    private TextToSpeech directSystemTTS;
    private boolean userIsScrolling = false;
    private long lastAutoScrollTime = 0;
    private static final long AUTO_SCROLL_THROTTLE_MS = 1500; 
    private long lastUIUpdateTime = 0;
    private static final long UI_UPDATE_THROTTLE_MS = 500; 
    private boolean isTtsInitialized = false;
    private String currentPlayingSentence = "";
    private int currentPlayingSentenceIndex = -1;
    private String[] allSentences = new String[0];
    private String lastProcessedText = "";
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
        useAIGeneration = intent.getBooleanExtra("use_ai_generation", false);
        useStreamingMode = false;

        ArrayList<NewsArticle> articlesList = (ArrayList<NewsArticle>) intent.getSerializableExtra("selected_articles_list");
        if (articlesList != null) {
            selectedArticles = new HashSet<>(articlesList);
        }

        if (selectedArticles == null || selectedArticles.isEmpty()) {
            showError("No articles selected for podcast");
            finish();
            return;
        }

        // Initialize TTS
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

        // Initialize views ONLY ONCE
        initializeViews();
        setupListeners();
        setupProgressTracking();

        if (useAIGeneration && aiAttributionPanel != null) {
            aiAttributionPanel.setVisibility(View.VISIBLE);
        } else if (aiAttributionPanel != null) {
            aiAttributionPanel.setVisibility(View.GONE);
        }

        animateUserInterface();

        // Start generating podcast
        showGeneratingState(true);
        loadFullArticleContent();
    }

    private void initializeViews() {
        podcastTitle = findViewById(R.id.podcast_title);
        podcastDuration = findViewById(R.id.podcast_duration);
        topicsChipGroup = findViewById(R.id.podcast_topics_chips);
        currentSectionLabel = findViewById(R.id.current_section_label);
        transcriptText = findViewById(R.id.transcript_text);
        transcriptScrollView = findViewById(R.id.transcript_scroll_view);
        generationStatus = findViewById(R.id.generation_status);
        generationProgress = findViewById(R.id.generation_progress);
        aiAttributionPanel = findViewById(R.id.ai_attribution_panel);
        Button cancelButton = findViewById(R.id.cancel_generation_button);
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> cancelGeneration());
            cancelButton.setVisibility(View.GONE);
        }
        seekBar = findViewById(R.id.seek_bar);
        currentTimeText = findViewById(R.id.current_time);
        totalTimeText = findViewById(R.id.total_time);
        prevButton = findViewById(R.id.prev_button);
        playPauseButton = findViewById(R.id.play_pause_button);
        nextButton = findViewById(R.id.next_button);
        speedValueText = findViewById(R.id.speed_value);
        speedSlider = findViewById(R.id.speed_slider);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            prevButton.setTooltipText("Back 10 seconds");
            nextButton.setTooltipText("Forward 10 seconds");
        }
    }
    private void setupListeners() {
        if (transcriptScrollView != null) {
            transcriptScrollView.setOnScrollChangeListener(
                (View.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (Math.abs(scrollY - oldScrollY) > 10) {
                        userIsScrolling = true;
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            userIsScrolling = false;
                        }, 5000);
                    }
                }
            );
        }
        playPauseButton.setOnClickListener(v -> togglePlayback());
        prevButton.setOnClickListener(v -> {
            skipToPreviousSegment();
            android.widget.Toast.makeText(this, "Back 10 seconds", android.widget.Toast.LENGTH_SHORT).show();
        });
        nextButton.setOnClickListener(v -> {
            skipToNextSegment();
            android.widget.Toast.makeText(this, "Forward 10 seconds", android.widget.Toast.LENGTH_SHORT).show();
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int userSelectedPosition = 0;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    userSelectedPosition = progress;
                    updateCurrentTimeText(progress);
                    Log.d(TAG, "User seeking to position: " + progress + "s");
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeekBarTracking = true;
                userSelectedPosition = seekBar.getProgress();
                Log.d(TAG, "Started tracking seek at: " + userSelectedPosition + "s");
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int seekPosition = seekBar.getProgress();
                Log.d(TAG, "Seeking to position: " + seekPosition + "s");
                if (ttsHelper != null && isPodcastGenerated) {
                    int seekPositionMs = seekPosition * 1000;
                    ttsHelper.seekTo(seekPositionMs);
                    currentPlayingSentenceIndex = -1;
                    currentPlayingSentence = "";
                    updateCurrentTimeText(seekPosition);
                }
                isSeekBarTracking = false;
            }
        });
        speedSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                playbackSpeed = value;
                updateSpeedText();
                if (useStreamingMode && enhancedTTS != null) {
                    enhancedTTS.setSpeechRate(value);
                } else if (ttsHelper != null && isPodcastGenerated) {
                    ttsHelper.setPlaybackSpeed(value);
                }
            }
        });
    }
    private void setupProgressTracking() {
        ttsHelper.setProgressCallback(new SimplifiedTTSHelper.ProgressCallback() {
            @Override
            public void onProgress(int currentPosition, int totalDuration, int segmentIndex) {
                runOnUiThread(() -> {
                    if (segmentIndex != currentSegmentIndex) {
                        currentSegmentIndex = segmentIndex;
                    }
                    if (!isSeekBarTracking) {
                        seekBar.setProgress(currentPosition / 1000);
                        updateCurrentTimeText(currentPosition / 1000);
                        updateTotalTimeText(totalDuration / 1000);
                    }
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
        ttsHelper.setWordTrackingCallback(new SimplifiedTTSHelper.WordTrackingCallback() {
            private long lastProcessedTime = 0;
            private static final long WORD_PROCESSING_THROTTLE_MS = 300;
            @Override
            public void onWordSpoken(String word, int indexInSpeech) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastProcessedTime < WORD_PROCESSING_THROTTLE_MS) {
                    return;
                }
                lastProcessedTime = currentTime;
                runOnUiThread(() -> {
                    if (podcastContent != null) {
                        String fullText = podcastContent.getFullText();
                        if (word == null || word.length() <= 1 || 
                            word.equals("the") || word.equals("and") || 
                            word.equals("a") || word.equals("of")) {
                            return;
                        }
                        if (indexInSpeech % 10 == 0) {
                            Log.d(TAG, "Word spoken: '" + word + "' at index " + indexInSpeech);
                        }
                        String currentSentence = findSentenceContainingWord(fullText, word, indexInSpeech);
                        if (currentSentence != null && !currentSentence.equals(currentPlayingSentence)) {
                            Log.d(TAG, "New sentence detected at word index " + indexInSpeech);
                            currentPlayingSentence = currentSentence;
                            updateTranscriptWithHighlighting(fullText, currentSentence);
                            if (!userIsScrolling) {
                                scrollToHighlightedSentence();
                            }
                        }
                    }
                });
            }
        });
    }
    private String findSentenceContainingWord(String text, String word, int wordIndex) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (allSentences.length == 0 || !text.equals(lastProcessedText)) {
            allSentences = text.split("(?<=[.!?])(?=\\s+[A-Z]|\\s*$)");
            lastProcessedText = text;
            Log.d(TAG, "Split text into " + allSentences.length + " sentences");
            if (allSentences.length <= 3 && text.length() > 500) {
                allSentences = text.split("\\n\\s*\\n");
                Log.d(TAG, "Switched to paragraph splitting, found " + allSentences.length + " paragraphs");
            }
        }
        if (allSentences.length == 0) {
            return null;
        }
        int totalWords = 0;
        for (int i = 0; i < allSentences.length; i++) {
            String sentence = allSentences[i].trim();
            if (sentence.isEmpty()) continue;
            String[] wordsInSentence = sentence.split("\\s+");
            int sentenceWordCount = wordsInSentence.length;
            if (wordIndex >= totalWords && wordIndex < (totalWords + sentenceWordCount)) {
                currentPlayingSentenceIndex = i;
                Log.d(TAG, "Found sentence at index " + i + " for word index " + wordIndex);
                return sentence;
            }
            totalWords += sentenceWordCount;
        }
        if (wordIndex > 0) {
            int estimatedSentenceIndex = Math.min(
                (int)((wordIndex / (float)totalWords) * allSentences.length),
                allSentences.length - 1
            );
            currentPlayingSentenceIndex = estimatedSentenceIndex;
            Log.d(TAG, "Using estimated sentence index " + estimatedSentenceIndex + " for word index " + wordIndex);
            return allSentences[estimatedSentenceIndex];
        }
        return null;
    }
    private void updateTranscriptWithHighlighting(String fullText, String highlightedSentence) {
        if (fullText == null || fullText.isEmpty() || transcriptText == null) {
            return;
        }
        String escapedSentence = highlightedSentence;
        if (escapedSentence != null && !escapedSentence.isEmpty()) {
            escapedSentence = escapedSentence.replaceAll("([\\[\\]\\(\\)\\{\\}\\*\\+\\?\\^\\$\\\\\\.\\|])", "\\\\$1");
            try {
                String highlighted = fullText.replaceAll(
                    "(" + escapedSentence + ")", 
                    "<span style='background-color:#E6E6FA; color:#6200EE; font-weight:bold;'>$1</span>"
                );
                transcriptText.setText(Html.fromHtml(highlighted, Html.FROM_HTML_MODE_COMPACT));
                Log.d(TAG, "Updated highlighting for sentence (" + highlightedSentence.length() + " chars)");
                scrollToHighlightedSentence();
            } catch (Exception e) {
                Log.e(TAG, "Error highlighting sentence: " + e.getMessage());
                transcriptText.setText(fullText);
            }
        } else {
            transcriptText.setText(fullText);
        }
    }
    private void scrollToHighlightedSentence() {
        if (transcriptScrollView == null || transcriptText == null || 
            currentPlayingSentenceIndex < 0 || userIsScrolling) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAutoScrollTime < AUTO_SCROLL_THROTTLE_MS) {
            return;
        }
        lastAutoScrollTime = currentTime;
        try {
            android.text.Layout layout = transcriptText.getLayout();
            if (layout != null) {
                String fullText = transcriptText.getText().toString();
                String currentSentence = allSentences[currentPlayingSentenceIndex];
                if (currentSentence == null || currentSentence.isEmpty()) {
                    Log.d(TAG, "Cannot scroll - current sentence is empty");
                    return;
                }
                int startOfSentence = fullText.indexOf(currentSentence);
                if (startOfSentence >= 0) {
                    int lineStart = layout.getLineForOffset(startOfSentence);
                    int y = layout.getLineTop(lineStart);
                    int scrollViewHeight = transcriptScrollView.getHeight();
                    int scrollTo = Math.max(0, y - (scrollViewHeight / 4));
                    Log.d(TAG, "Scrolling to line " + lineStart + " at position " + scrollTo);
                    transcriptScrollView.smoothScrollTo(0, scrollTo);
                    return;
                }
                Log.d(TAG, "Sentence not found in text, using fallback scrolling method");
            }
            if (allSentences.length > 0) {
                float scrollProgress = (float) currentPlayingSentenceIndex / allSentences.length;
                int totalHeight = transcriptText.getHeight();
                int approximatePosition = (int)(scrollProgress * totalHeight);
                int scrollPosition = Math.max(0, approximatePosition - 200);
                Log.d(TAG, "Using approximate scroll position: " + scrollPosition + 
                      " (sentence " + currentPlayingSentenceIndex + " of " + allSentences.length + ")");
                transcriptScrollView.smoothScrollTo(0, scrollPosition);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scrolling to sentence: " + e.getMessage());
        }
    }
    private void generatePodcast() {
        // Already in generating state from loadFullArticleContent, just update text
        if (generationStatus != null) {
            generationStatus.setText("Generating podcast content...");
        }

        if (generationProgress != null) {
            generationProgress.setIndeterminate(true);
        }

        // Log content status if we have it
        logArticleContentStatus();

        // Make sure PodcastGenerator is initialized
        if (podcastGenerator == null) {
            podcastGenerator = new PodcastGenerator(selectedArticles, duration, selectedTopics);
            podcastGenerator.setUseAI(useAIGeneration);
        }

        podcastGenerator.generateContentAsync()
                .thenAccept(content -> {
                    podcastContent = content;
                    runOnUiThread(() -> {
                        updatePodcastInfo();
                        isPodcastGenerated = true;
                        showGeneratingState(false);
                        updateTotalTimeText(podcastContent.getTotalDuration());
                        updateUIForPlayerState();
                        if (generationStatus != null) {
                            generationStatus.setText("Ready to play");
                        }
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
    private void synthesizeToFile() {
        directSpeechPlayback();
    }
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
    private void updatePodcastInfo() {
        if (podcastContent == null) return;
        // Make sure to display the forced duration
        String durationText = String.format("Duration: %d:%02d",
                podcastContent.getTotalDuration() / 60,
                podcastContent.getTotalDuration() % 60);
        podcastDuration.setText(durationText);
        podcastTitle.setText(podcastContent.getTitle());
        //podcastDuration.setText("Duration: " + podcastContent.getFormattedDuration());
        topicsChipGroup.removeAllViews();
        // Always update the seekbar max to match our forced duration
        if (seekBar != null) {
            seekBar.setMax(podcastContent.getTotalDuration());
        }
        for (String topic : podcastContent.getTopics()) {
            Chip chip = new Chip(this);
            chip.setText(topic);
            chip.setClickable(false);
            topicsChipGroup.addView(chip);
        }
    }
    private void startProgressUpdates() {
        stopProgressUpdates();
        progressHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isPodcastGenerated && ttsHelper != null) {
                    boolean shouldUpdate = isPlaying ||
                        (ttsHelper != null && ttsHelper.isSpeaking());
                    if (shouldUpdate && !isSeekBarTracking) {
                        updateProgress();
                    }
                    progressHandler.postDelayed(this, 1000);
                }
            }
        });
    }
    private void stopProgressUpdates() {
        progressHandler.removeCallbacksAndMessages(null);
    }
    private void updateProgress() {
        if (ttsHelper == null) return;
        try {
            Log.d(TAG, "TTS position: " + ttsHelper.getCurrentPosition());

            int currentPosition = ttsHelper.getCurrentPosition() / 1000;
            int totalDuration = ttsHelper.getTotalDuration() / 1000;
            if (totalDuration <= 0) {
                if (podcastContent != null) {
                    totalDuration = podcastContent.getTotalDuration();
                }
            }
            if (currentPosition >= 0 && totalDuration > 0) {
                seekBar.setMax(totalDuration);
                if (!isSeekBarTracking) {
                    seekBar.setProgress(currentPosition);
                }
                updateCurrentTimeText(currentPosition);
                updateTotalTimeText(totalDuration);
                if (currentPosition % 5 == 0) {
                    Log.d(TAG, "Progress updated - position: " + currentPosition +
                          "s, duration: " + totalDuration + "s");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating progress: " + e.getMessage());
        }
    }
    private void skipToPreviousSegment() {
        if (!isPodcastGenerated || ttsHelper == null) {
            Log.w(TAG, "Cannot skip back: podcast not generated or TTS helper is null");
            return;
        }
        try {
            ttsHelper.skipBackward(10000);
            int currentPosition = ttsHelper.getCurrentPosition() / 1000;
            seekBar.setProgress(currentPosition);
            updateCurrentTimeText(currentPosition);
            updateProgress();
            currentPlayingSentenceIndex = -1;
            currentPlayingSentence = "";
        } catch (Exception e) {
            Log.e(TAG, "Error during skipToPreviousSegment: " + e.getMessage(), e);
            showError("Skip error: " + e.getMessage());
        }
    }
    private void skipToNextSegment() {
        if (!isPodcastGenerated || ttsHelper == null) {
            Log.w(TAG, "Cannot skip forward: podcast not generated or TTS helper is null");
            return;
        }
        try {
            ttsHelper.skipForward(10000);
            int currentPosition = ttsHelper.getCurrentPosition() / 1000;
            seekBar.setProgress(currentPosition);
            updateCurrentTimeText(currentPosition);
            updateProgress();
            currentPlayingSentenceIndex = -1;
            currentPlayingSentence = "";
        } catch (Exception e) {
            Log.e(TAG, "Error during skipToNextSegment: " + e.getMessage(), e);
            showError("Skip error: " + e.getMessage());
        }
    }
    private void togglePlayback() {
        Log.d(TAG, "togglePlayback called, isPodcastGenerated=" + isPodcastGenerated + 
              ", isPlaying=" + isPlaying + ", isTtsInitialized=" + isTtsInitialized);
        if (!isPodcastGenerated) {
            Log.e(TAG, "Cannot toggle playback: podcast not yet generated");
            showError("播客内容仍在生成中，请稍候。");
            return;
        }
        if (podcastContent == null || podcastContent.getSegments().isEmpty()) {
            Log.e(TAG, "Cannot toggle playback: podcast content is null or empty");
            showError("没有可用的播客内容");
            return;
        }
        if (isPlaying) {
            Log.d(TAG, "Stopping playback");
            if (ttsHelper != null) {
                ttsHelper.stop();
            }
            if (directSystemTTS != null) {
                directSystemTTS.stop();
            }
            isPlaying = false;
            stopProgressUpdates();
            updatePlayButtonState(false);
            return;
        }
        if (!isTtsInitialized) {
            Log.e(TAG, "TTS not initialized yet, attempting to initialize");
            showError("正在初始化文本转语音引擎，请稍候再试。");
            reInitializeTts();
            return;
        }
        if (useStreamingMode && enhancedTTS != null) {
            toggleChatPlayback();
            return;
        }
        Log.d(TAG, "Starting playback, audioFile=" + (audioFile != null ? "exists" : "null"));
        try {
            if (audioFile != null && audioFile.exists()) {
                isPlaying = ttsHelper.playAudio(audioFile);
                if (!isPlaying) {
                    Log.e(TAG, "Failed to play audio file");
                    showError("播放音频文件失败");
                    directSpeechPlayback();
                }
            } else {
                if (ttsHelper == null) {
                    Log.e(TAG, "TTS Helper is null, reinitializing");
                    reInitializeTts();
                    return;
                }
                if (podcastContent != null) {
                    Log.d(TAG, "Speaking podcast with " + podcastContent.getSegments().size() + " segments");
                    try {
                        generationStatus.setText("正在准备播放...");
                        generationStatus.setVisibility(View.VISIBLE);
                        String fullText = podcastContent.getFullText();
                        if (fullText != null) {
                            Log.d(TAG, "Podcast content size: " + fullText.length() + " characters");
                        }
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                if (fullText != null && fullText.length() > 10000) {
                                    Log.d(TAG, "Large podcast content detected (" + fullText.length() + 
                                          " chars). Using chunked playback approach.");
                                }
                                isPlaying = ttsHelper.speakPodcast(podcastContent);
                                updatePlayButtonState(isPlaying);
                                generationStatus.setVisibility(View.GONE);
                                if (isPlaying) {
                                    startProgressUpdates();
                                } else {
                                    Log.e(TAG, "Failed to start podcast playback with standard TTS, trying fallback");
                                    fallbackToDirectSystemTTS();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error during delayed TTS start: " + e.getMessage(), e);
                                showError("播放错误: " + e.getMessage());
                                isPlaying = false;
                                updatePlayButtonState(false);
                                generationStatus.setVisibility(View.GONE);
                            }
                        }, 300); 
                    } catch (Exception e) {
                        Log.e(TAG, "Exception trying to play podcast: " + e.getMessage(), e);
                        showError("播放错误: " + e.getMessage());
                        isPlaying = false;
                        fallbackToDirectSystemTTS();
                    }
                } else {
                    showError("没有播客内容可播放");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in togglePlayback: " + e.getMessage(), e);
            showError("播放错误: " + e.getMessage());
            isPlaying = false;
            updatePlayButtonState(false);
            showTTSErrorDialog();
        }
    }
    private void updatePlayButtonState(boolean isPlaying) {
        int icon = isPlaying ? 
                android.R.drawable.ic_media_pause : 
                android.R.drawable.ic_media_play;
        playPauseButton.setImageResource(icon);
        this.isPlaying = isPlaying;
    }
    private void updateSpeedText() {
        speedValueText.setText(String.format("%.1fx", playbackSpeed));
    }
    private void updateCurrentTimeText(int seconds) {
        if (currentTimeText != null) {
            String formattedTime = formatTime(seconds);
            currentTimeText.setText(formattedTime);
            if (seconds % 30 == 0) {
                Log.d(TAG, "Current playback position: " + formattedTime);
            }
        }
    }
    private void updateTotalTimeText(int seconds) {
        if (totalTimeText != null && seekBar != null) {
            String formattedTime = formatTime(seconds);
            totalTimeText.setText(formattedTime);
            seekBar.setMax(seconds);
            Log.d(TAG, "Total duration updated: " + formattedTime + " (" + seconds + "s)");
        }
    }
    private String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        if (seconds >= 3600) {
            return String.format("%d:%02d:%02d",
                    TimeUnit.SECONDS.toHours(seconds),
                    TimeUnit.SECONDS.toMinutes(seconds) % 60,
                    seconds % 60);
        } else {
            return String.format("%d:%02d",
                    TimeUnit.SECONDS.toMinutes(seconds),
                    seconds % 60);
        }
    }
    private void resetToBeginning() {
        currentSegmentIndex = 0;
        currentPlayingSentenceIndex = -1;
        currentPlayingSentence = "";
        if (podcastContent != null) {
            String fullText = podcastContent.getFullText();
            String formattedText = formatTranscriptText(fullText);
            transcriptText.setText(Html.fromHtml(formattedText, Html.FROM_HTML_MODE_COMPACT));
            if (transcriptScrollView != null) {
                transcriptScrollView.smoothScrollTo(0, 0);
            }
        }
    }
    private void showGeneratingState(boolean isGenerating) {
        if (isGenerating) {
            if (generationStatus != null) {
                generationStatus.setVisibility(View.VISIBLE);
            }
            if (generationProgress != null) {
                generationProgress.setVisibility(View.VISIBLE);
            }
            Button cancelButton = findViewById(R.id.cancel_generation_button);
            if (cancelButton != null) {
                cancelButton.setVisibility(View.VISIBLE);
            }
            if (seekBar != null) {
                seekBar.setVisibility(View.GONE);
            }
            if (currentTimeText != null) {
                currentTimeText.setVisibility(View.GONE);
            }
            if (totalTimeText != null) {
                totalTimeText.setVisibility(View.GONE);
            }
            if (prevButton != null) {
                prevButton.setVisibility(View.GONE);
            }
            if (playPauseButton != null) {
                playPauseButton.setVisibility(View.GONE);
            }
            if (nextButton != null) {
                nextButton.setVisibility(View.GONE);
            }
            if (speedValueText != null) {
                speedValueText.setVisibility(View.GONE);
            }
            if (speedSlider != null) {
                speedSlider.setVisibility(View.GONE);
            }
            if (useStreamingMode) {
                if (currentWordIndicator != null) {
                    currentWordIndicator.setVisibility(View.VISIBLE);
                }
                if (hostContainer != null) {
                    hostContainer.setVisibility(View.VISIBLE);
                }
            }
        } else {
            if (generationStatus != null) {
                generationStatus.setVisibility(View.GONE);
            }
            if (generationProgress != null) {
                generationProgress.setVisibility(View.GONE);
            }
            Button cancelButton = findViewById(R.id.cancel_generation_button);
            if (cancelButton != null) {
                cancelButton.setVisibility(View.GONE);
            }
            if (seekBar != null) {
                seekBar.setVisibility(View.VISIBLE);
            }
            if (currentTimeText != null) {
                currentTimeText.setVisibility(View.VISIBLE);
            }
            if (totalTimeText != null) {
                totalTimeText.setVisibility(View.VISIBLE);
            }
            if (prevButton != null) {
                prevButton.setVisibility(View.VISIBLE);
            }
            if (playPauseButton != null) {
                playPauseButton.setVisibility(View.VISIBLE);
            }
            if (nextButton != null) {
                nextButton.setVisibility(View.VISIBLE);
            }
            if (speedValueText != null) {
                speedValueText.setVisibility(View.VISIBLE);
            }
            if (speedSlider != null) {
                speedSlider.setVisibility(View.VISIBLE);
            }
        }
    }
    private void highlightCurrentSegment() {
        if (podcastContent == null || 
            currentSegmentIndex < 0 || 
            currentSegmentIndex >= podcastContent.getSegments().size()) {
            return;
        }
        transcriptText.setBackgroundColor(0x22FF0000); 
        transcriptText.animate()
                .setDuration(500)
                .withEndAction(() -> transcriptText.setBackgroundColor(0x00000000))
                .start();
        if (transcriptScrollView != null) {
            transcriptScrollView.smoothScrollTo(0, 0);
        }
    }
    private void showError(String message) {
        Log.e(TAG, "Error: " + message);
        Snackbar snackbar = Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_LONG
        );
        if (message.contains("generating") || message.contains("AI") || message.contains("OpenAI")) {
            snackbar.setAction("Retry", v -> {
                if (!isPodcastGenerated) {
                    generatePodcast();
                }
            });
        }
        snackbar.show();
        if (message.contains("API") || message.contains("failed") || message.contains("error")) {
            showErrorDialog(message);
        }
    }
    private void showErrorDialog(String message) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message + "\n\nWhat would you like to do?")
            .setPositiveButton("Retry", (dialog, which) -> {
                isPodcastGenerated = false;
                if (useStreamingMode) {
                    generateStreamingPodcast();
                } else {
                    generatePodcast();
                }
            })
            .setNeutralButton("Switch to Standard Mode", (dialog, which) -> {
                useStreamingMode = false;
                useAIGeneration = false;
                isPodcastGenerated = false;
                podcastGenerator.setUseAI(false);
                generatePodcast();
            })
            .setNegativeButton("Go Back", (dialog, which) -> {
                finish();
            })
            .setCancelable(true) 
            .show();
    }
    private void animateUserInterface() {
        View podcastHeaderCard = findViewById(R.id.podcast_header_card);
        View currentSectionLabel = findViewById(R.id.current_section_label);
        View transcriptCard = findViewById(R.id.transcript_card);
        View playerControls = findViewById(R.id.player_controls);
        final int duration = 500;
        final int staggerDelay = 150;
        podcastHeaderCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(staggerDelay)
                .start();
        currentSectionLabel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(staggerDelay * 2)
                .start();
        transcriptCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(staggerDelay * 3)
                .start();
        playerControls.animate()
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(staggerDelay * 4)
                .start();
    }
    private void updateUIForPlayerState() {
        boolean enableControls = isPodcastGenerated;
        prevButton.setEnabled(enableControls);
        playPauseButton.setEnabled(enableControls);
        nextButton.setEnabled(enableControls);
        seekBar.setEnabled(enableControls);
        speedSlider.setEnabled(enableControls);
        float alpha = enableControls ? 1.0f : 0.5f;
        prevButton.setAlpha(alpha);
        nextButton.setAlpha(alpha);
        speedSlider.setAlpha(alpha);
        findViewById(R.id.player_controls).setVisibility(View.VISIBLE);
        if (podcastContent != null) {
            String fullText = podcastContent.getFullText();
            String formattedText = formatTranscriptText(fullText);
            transcriptText.setText(Html.fromHtml(formattedText, Html.FROM_HTML_MODE_COMPACT));
            currentSectionLabel.setText(podcastContent.getTitle());
        }
    }
    private String formatTranscriptText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String cleanText = text.replaceAll("<[^>]*>", "");
        cleanText = cleanText.replaceAll("(?m)^\\s*$", "<br/>");
        StringBuilder formatted = new StringBuilder();
        String[] sentences = cleanText.split("(?<=[.!?])\\s+");
        for (String sentence : sentences) {
            if (!sentence.trim().isEmpty()) {
                formatted.append(sentence).append(" ");
            }
        }
        String result = formatted.toString()
            .replaceAll("\\n\\s*\\n", "<br/><br/>")
            .replaceAll("\\n", "<br/>");
        return result;
    }
    @Override
    protected void onPause() {
        super.onPause();
        stopProgressUpdates();
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (isPlaying) {
            startProgressUpdates();
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressUpdates();
        progressHandler.removeCallbacksAndMessages(null);
        if (ttsHelper != null) {
            ttsHelper.shutdown();
            ttsHelper = null;
        }
        if (enhancedTTS != null) {
            enhancedTTS.shutdown();
            enhancedTTS = null;
        }
        if (directSystemTTS != null) {
            directSystemTTS.stop();
            directSystemTTS.shutdown();
            directSystemTTS = null;
        }
        stopGenerationProgressSimulation();
        generationProgressHandler.removeCallbacksAndMessages(null);
    }
    private void highlightCurrentSpeaker(String speaker) {
        runOnUiThread(() -> {
        });
    }
    private void highlightCurrentWord(String speaker, String word) {
    }
    private void generateStreamingPodcast() {
        if (!useAIGeneration) {
            generatePodcast();
            return;
        }
        generationStatus.setText("Connecting to AI...");
        generationProgress.setIndeterminate(false);
        generationProgress.setMax(MAX_PROGRESS);
        generationProgress.setProgress(0);
        generationProgressPercent = 0;
        isCancelled = false;
        startGenerationProgressSimulation();
        hostContainer.setVisibility(View.VISIBLE);
        hostText.setText(""); 
        currentSectionLabel.setText("Generating Podcast");
        currentWordIndicator.setVisibility(View.VISIBLE);
        currentWordIndicator.setText("Starting AI generator...");
        TextView fullTranscriptView = findViewById(R.id.transcript_text);
        fullTranscriptView.setVisibility(View.VISIBLE);
        fullTranscriptView.setText("");
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutHandler.postDelayed(() -> {
            if (!isPodcastGenerated && !isCancelled) {
                showErrorDialog("Connection to OpenAI is taking longer than expected. Please check your internet connection.");
            }
        }, 30000); 
        try {
            if (podcastGenerator == null) {
                showErrorDialog("PodcastGenerator not initialized. Please try again.");
                return;
            }
            OpenAIService openAIService = null;
            try {
                java.lang.reflect.Field field = PodcastGenerator.class.getDeclaredField("openAIService");
                field.setAccessible(true);
                openAIService = (OpenAIService) field.get(podcastGenerator);
            } catch (Exception e) {
                Log.e(TAG, "Could not access OpenAIService from PodcastGenerator: " + e.getMessage());
            }
            if (openAIService == null) {
                String apiKey = com.example.aipodcast.config.ApiConfig.OPENAI_API_KEY;
                if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your_openai_api_key_here")) {
                timeoutHandler.removeCallbacksAndMessages(null);
                    showErrorDialog("Invalid API key. Please configure a valid OpenAI API key in ApiConfig.");
                return;
                }
                openAIService = new OpenAIService(apiKey);
            }
        String podcastTitle = "AI Podcast";
        if (selectedTopics != null && !selectedTopics.isEmpty()) {
            podcastTitle = "AI Podcast: " + String.join(", ", selectedTopics);
        }
        this.podcastTitle.setText(podcastTitle);
            final OpenAIService finalOpenAIService = openAIService;
        OpenAIService.StreamingResponseHandler handler = new OpenAIService.StreamingResponseHandler() {
            @Override
            public void onContentReceived(String content) {
                    if (!shouldUpdateUI()) {
                        return;
                    }
                runOnUiThread(() -> {
                        clearPlaceholderTexts();
                        generationStatus.setText("AI is generating content...");
                        currentWordIndicator.setVisibility(View.VISIBLE);
                        currentWordIndicator.setText("Generating podcast...");
                });
            }
            @Override
            public void onFullTranscriptUpdate(String fullTranscript) {
                runOnUiThread(() -> {
                        clearPlaceholderTexts();
                    String formattedText = formatFullTranscript(fullTranscript);
                        fullTranscriptView.setText(android.text.Html.fromHtml(formattedText, android.text.Html.FROM_HTML_MODE_COMPACT));
                        smoothScrollToBottom();
                });
            }
            @Override
            public void onSpeakerChange(String speaker) {
                Log.d(TAG, "Speaker changed to: " + speaker);
                runOnUiThread(() -> {
                        clearPlaceholderTexts();
                        generationStatus.setText("Generating content...");
                        hostContainer.setVisibility(View.VISIBLE);
                        hostContainer.setBackgroundColor(0x1A6200EE); 
                        currentWordIndicator.setText("HOST is speaking...");
                });
            }
            @Override
            public void onTokenReceived(String speaker, String token) {
                    if (!shouldUpdateUI()) {
                        return;
                    }
                    if (token == null || token.isEmpty() || 
                        token.equals("the") || token.equals("and") || 
                        token.equals("is") || token.equals("to") ||
                        token.equals("of") || token.equals("AI") ||
                        token.equals("ai") || token.length() > 20) {
                        return;
                    }
                runOnUiThread(() -> {
                        clearPlaceholderTexts();
                    currentWordIndicator.setVisibility(View.VISIBLE);
                        currentWordIndicator.setText("HOST is speaking...");
                });
            }
            @Override
            public void onSpeakerComplete(String speaker, String completeText) {
                    Log.d(TAG, "Speaker complete: " + speaker);
            }
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error during streaming: " + error);
                    timeoutHandler.removeCallbacksAndMessages(null);
                    runOnUiThread(() -> {
                        stopGenerationProgressSimulation();
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
                    timeoutHandler.removeCallbacksAndMessages(null);
                runOnUiThread(() -> {
                    try {
                            clearGeneratingIndicators();
                        showGeneratingState(false);
                            stopGenerationProgressSimulation();
                        isPodcastGenerated = true;
                            currentWordIndicator.setText("Ready to play");
                            currentSectionLabel.setText("AI Podcast");
                        createPodcastContentFromTranscript(fullResponse);
                        updateUIForPlayerState();
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onComplete: " + e.getMessage());
                        showError("Error processing generated content: " + e.getMessage());
                    }
                });
            }
            };
            try {
                finalOpenAIService.generatePodcastContentStreaming(
                selectedArticles,
                selectedTopics,
                duration,
                podcastTitle,
                handler
        );
            } catch (Exception e) {
                timeoutHandler.removeCallbacksAndMessages(null);
                Log.e(TAG, "Error starting streaming generation: " + e.getMessage());
                showErrorDialog("Failed to start streaming generation: " + e.getMessage());
                showGeneratingState(false);
            }
        } catch (Exception e) {
            if (timeoutHandler != null) {
                timeoutHandler.removeCallbacksAndMessages(null);
            }
            Log.e(TAG, "Error initializing streaming: " + e.getMessage());
            showErrorDialog("Failed to initialize streaming: " + e.getMessage());
            showGeneratingState(false);
        }
    }

    private void loadFullArticleContent() {
        if (selectedArticles == null || selectedArticles.isEmpty()) {
            Log.e(TAG, "No articles selected");
            generatePodcast(); // Fall back to generating with what we have
            return;
        }

        // Make sure UI elements are initialized
        if (generationStatus != null) {
            generationStatus.setText("Loading full article content...");
        }

        if (generationProgress != null) {
            generationProgress.setIndeterminate(true);
        }

        Log.d(TAG, "Starting to load full content for " + selectedArticles.size() + " articles");

        Set<NewsArticle> articlesWithContent = new HashSet<>();
        List<CompletableFuture<NewsArticle>> futures = new ArrayList<>();

        // Create a repository for fetching article details
        NewsRepository repository = NewsRepositoryProvider.getRepository(this);

        // Proceed with article loading
        for (NewsArticle article : selectedArticles) {
            Log.d(TAG, "Requesting full content for: " + article.getTitle());

            CompletableFuture<NewsArticle> future = repository.getArticleDetails(article.getUrl())
                    .thenApply(fullArticle -> {
                        String fullText = fullArticle.getFullBodyText();
                        int wordCount = fullText != null ? fullText.split("\\s+").length : 0;

                        Log.d(TAG, "SUCCESS: Got full article: " + fullArticle.getTitle() +
                                " - Full text length: " + (fullText != null ? fullText.length() : 0) +
                                " chars, Word count: " + wordCount);

                        // Only use the article if it has substantial content
                        if (fullText != null && fullText.length() > 300) {
                            articlesWithContent.add(fullArticle);
                            return fullArticle;
                        } else {
                            Log.w(TAG, "Article has insufficient content, using original");
                            articlesWithContent.add(article);
                            return article;
                        }
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "Error fetching full article: " + e.getMessage());
                        articlesWithContent.add(article); // Add original article if fetching fails
                        return article;
                    });

            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    selectedArticles = articlesWithContent;

                    // Log detailed content status
                    Log.d(TAG, "===== ARTICLE CONTENT STATUS =====");
                    int totalLength = 0;
                    final int[] articlesWithFullText = {0}; // Make it an array to be effectively final

                    for (NewsArticle article : selectedArticles) {
                        String fullText = article.getFullBodyText();
                        int length = fullText != null ? fullText.length() : 0;
                        totalLength += length;

                        if (length > article.getAbstract().length() * 2) {
                            articlesWithFullText[0]++;
                        }

                        Log.d(TAG, "Article: " + article.getTitle() +
                                " - Abstract: " + article.getAbstract().length() + " chars" +
                                " - Full text: " + length + " chars" +
                                " - Has full content: " + (length > article.getAbstract().length() * 2 ? "YES" : "NO"));
                    }

                    final int finalTotalLength = totalLength; // Make it final

                    Log.d(TAG, "Total full text length: " + finalTotalLength +
                            " chars, Articles with full text: " + articlesWithFullText[0] +
                            " of " + selectedArticles.size());
                    Log.d(TAG, "====================================");

                    runOnUiThread(() -> {
                        // Create fresh instance with the updated articles containing full content
                        podcastGenerator = new PodcastGenerator(selectedArticles, duration, selectedTopics);
                        podcastGenerator.setUseAI(useAIGeneration);

                        // Continue with podcast generation
                        if (generationStatus != null) {
                            generationStatus.setText("Generating podcast from " +
                                    (articlesWithFullText[0] > 0 ? "full" : "limited") +
                                    " article content...");
                        }
                        generatePodcast();
                    });
                })
                .exceptionally(e -> {
                    Log.e(TAG, "Error fetching articles: " + e.getMessage());
                    runOnUiThread(() -> {
                        if (generationStatus != null) {
                            generationStatus.setText("Generating podcast with limited content...");
                        }
                        podcastGenerator = new PodcastGenerator(selectedArticles, duration, selectedTopics);
                        podcastGenerator.setUseAI(useAIGeneration);
                        generatePodcast(); // Proceed with what we have
                    });
                    return null;
                });
    }
    private void startGenerationProgressSimulation() {
        generationProgressPercent = 0;
        generationProgress.setProgress(0);
        Runnable progressUpdater = new Runnable() {
            @Override
            public void run() {
                if (isCancelled) {
                    return;
                }
                if (generationProgressPercent < 95) { 
                    int increment;
                    if (generationProgressPercent < 30) {
                        increment = 3; 
                    } else if (generationProgressPercent < 60) {
                        increment = 2; 
                    } else {
                        increment = 1; 
                    }
                    generationProgressPercent += increment;
                    generationProgress.setProgress(generationProgressPercent);
                    updateGenerationStatusMessage(generationProgressPercent);
                    generationProgressHandler.postDelayed(this, 800);
                }
            }
        };
        generationProgressHandler.post(progressUpdater);
    }
    private void logArticleContentStatus() {
        if (selectedArticles == null || selectedArticles.isEmpty()) {
            Log.w(TAG, "No articles to log content status");
            return;
        }

        Log.d(TAG, "Article content status report:");
        Log.d(TAG, "------------------------------");
        int totalFullContentArticles = 0;

        for (NewsArticle article : selectedArticles) {
            String fullText = article.getFullBodyText();
            int fullTextLength = (fullText != null) ? fullText.length() : 0;
            int abstractLength = (article.getAbstract() != null) ? article.getAbstract().length() : 0;

            boolean hasSubstantialFullText = fullTextLength > abstractLength * 2;

            if (hasSubstantialFullText) {
                totalFullContentArticles++;
            }

            Log.d(TAG, String.format(
                    "Article: '%s', Abstract length: %d chars, Full content length: %d chars, Has substantial content: %s",
                    article.getTitle(),
                    abstractLength,
                    fullTextLength,
                    hasSubstantialFullText ? "YES" : "NO"
            ));
        }

        Log.d(TAG, String.format(
                "Summary: %d of %d articles have substantial content",
                totalFullContentArticles,
                selectedArticles.size()
        ));
        Log.d(TAG, "------------------------------");
    }
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
        if (currentSectionLabel != null) {
            currentSectionLabel.setText(message);
        }
    }
    private void stopGenerationProgressSimulation() {
        generationProgressHandler.removeCallbacksAndMessages(null);
        generationProgressPercent = MAX_PROGRESS;
        generationProgress.setProgress(MAX_PROGRESS);
    }
    private void playStreamingSegment(String speaker, String text) {
        if (text == null || text.isEmpty()) {
            Log.e(TAG, "Play text is empty");
            showError("No content to play");
            return;
        }
        Log.d(TAG, "Playing " + speaker + " content, length: " + text.length());
        text = text.replace("preparing to connect to AI...", "")
                  .replace("received content: Starting generation...", "")
                  .replace("processing...", "")
                  .trim();
        if (text.isEmpty()) {
            Log.e(TAG, "Text is empty after cleanup");
            showError("No content to play after cleanup");
            return;
        }
        final int MAX_TTS_LENGTH = 3500; 
        if (text.length() > MAX_TTS_LENGTH) {
            Log.d(TAG, "Text too long for TTS (" + text.length() + " chars), splitting into chunks");
            playLongTextInChunks(speaker, text, MAX_TTS_LENGTH);
            return;
        }
        currentWordIndicator.setText("Playing " + speaker + " content");
        currentWordIndicator.setVisibility(View.VISIBLE);
        highlightCurrentSpeaker(speaker);
        if (enhancedTTS != null && enhancedTTS.isInitialized()) {
            try {
            boolean success = enhancedTTS.speak(speaker, text);
            if (!success) {
                Log.e(TAG, "Enhanced TTS playback failed");
                    tryFallbackTTS(speaker, text);
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception during TTS playback: " + e.getMessage());
                tryFallbackTTS(speaker, text);
            }
        } else {
            tryFallbackTTS(speaker, text);
        }
    }
    private void playLongTextInChunks(String speaker, String text, int maxChunkSize) {
        try {
            List<String> chunks = new ArrayList<>();
            String[] paragraphs = text.split("\n\n");
            StringBuilder currentChunk = new StringBuilder();
            for (String paragraph : paragraphs) {
                if (currentChunk.length() + paragraph.length() > maxChunkSize) {
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString());
                        currentChunk = new StringBuilder();
                    }
                    if (paragraph.length() > maxChunkSize) {
                        String[] sentences = paragraph.split("(?<=[.!?])\\s+");
                        for (String sentence : sentences) {
                            if (currentChunk.length() + sentence.length() > maxChunkSize) {
                                if (currentChunk.length() > 0) {
                                    chunks.add(currentChunk.toString());
                                    currentChunk = new StringBuilder();
                                }
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
            if (currentChunk.length() > 0) {
                chunks.add(currentChunk.toString());
            }
            Log.d(TAG, "Split text into " + chunks.size() + " chunks for TTS");
            if (!chunks.isEmpty()) {
                playChunksSequentially(speaker, chunks, 0);
            } else {
                showError("Error preparing text for playback");
                isPlaying = false;
                updatePlayButtonState(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error splitting text: " + e.getMessage());
            tryFallbackTTS(speaker, text);
        }
    }
    private void playChunksSequentially(String speaker, List<String> chunks, int index) {
        if (index >= chunks.size() || !isPlaying) {
            return;
        }
        String chunk = chunks.get(index);
        if (enhancedTTS != null && enhancedTTS.isInitialized()) {
            final int nextIndex = index + 1;
            EnhancedTTSService.TTSCallback originalCallback = null;
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
                    if (isPlaying) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            playChunksSequentially(speaker, chunks, nextIndex);
                        }, 300);
                    }
                }
                private EnhancedTTSService.TTSCallback callback = originalCallback;
            };
            boolean success = enhancedTTS.speak(speaker, chunk);
            if (!success && isPlaying) {
                playChunksSequentially(speaker, chunks, index + 1);
            }
        } else {
            if (ttsHelper != null) {
                String prefixedChunk = speaker + ": " + chunk;
                boolean success = ttsHelper.speak(prefixedChunk);
                if (success) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isPlaying) {
                            playChunksSequentially(speaker, chunks, index + 1);
                        }
                    }, chunk.length() * 50); 
                } else if (isPlaying) {
                    playChunksSequentially(speaker, chunks, index + 1);
                }
            }
        }
    }
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
    private void toggleChatPlayback() {
        if (!isPodcastGenerated) {
            showError("Content is still generating, please wait...");
            return;
        }
        if (isPlaying) {
            Log.d(TAG, "Stopping playback");
            if (enhancedTTS != null) {
                enhancedTTS.stop();
            } else if (ttsHelper != null) {
                ttsHelper.stop();
            }
            if (directSystemTTS != null) {
                directSystemTTS.stop();
            }
            isPlaying = false;
            updatePlayButtonState(false);
        } else {
            Log.d(TAG, "Starting playback");
            boolean ttsTestSuccessful = false;
            if (enhancedTTS != null && enhancedTTS.isInitialized()) {
                Log.d(TAG, "Testing Enhanced TTS engine...");
                ttsTestSuccessful = enhancedTTS.testTTS();
                Log.d(TAG, "Enhanced TTS test result: " + ttsTestSuccessful);
            }
            String contentToPlay = "";
            if (transcriptText != null && transcriptText.getText().length() > 0) {
                contentToPlay = android.text.Html.fromHtml(
                    transcriptText.getText().toString(),
                    android.text.Html.FROM_HTML_MODE_LEGACY
                ).toString();
                Log.d(TAG, "Using transcript text content, length: " + contentToPlay.length());
            }
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
    private void initializeAndPlayWithDirectTTS(String contentToPlay) {
        if (directSystemTTS != null) {
            directSystemTTS.stop();
            directSystemTTS.shutdown();
        }
        directSystemTTS = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "Direct TTS initialized successfully");
                directSystemTTS.setLanguage(Locale.US);
                directSystemTTS.setSpeechRate(1.0f);
                runOnUiThread(() -> {
                    currentWordIndicator.setText("Playing with system TTS...");
                });
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
    private void showTTSErrorDialog() {
        if (isFinishing()) {
            return;
        }
        StringBuilder diagnosticInfo = new StringBuilder();
        diagnosticInfo.append("设备: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        diagnosticInfo.append("Android版本: ").append(Build.VERSION.RELEASE).append("\n");
        boolean systemTtsAvailable = checkSystemTtsAvailable();
        diagnosticInfo.append("系统TTS可用: ").append(systemTtsAvailable).append("\n");
        String ttsEngines = "未知";
        try {
            String defaultEngine = null;
            if (ttsHelper != null && ttsHelper.getTts() != null) {
                defaultEngine = ttsHelper.getTts().getDefaultEngine();
            } else if (directSystemTTS != null) {
                defaultEngine = directSystemTTS.getDefaultEngine();
            }
            if (defaultEngine != null) {
                ttsEngines = defaultEngine;
            }
        } catch (Exception e) {
            ttsEngines = "获取失败: " + e.getMessage();
        }
        diagnosticInfo.append("TTS引擎: ").append(ttsEngines).append("\n");
        Log.e(TAG, "TTS诊断信息: " + diagnosticInfo.toString());
        String dialogMessage;
        if (systemTtsAvailable) {
            dialogMessage = "TTS播放过程中出现错误。下列信息可能有助于解决问题：\n\n" +
                "• 检查您的设备是否已安装完整的文本转语音引擎\n" +
                "• 确保您已下载英语(美国)语言包\n" +
                "• 尝试重启应用或设备\n" +
                "• 检查设备音量是否已打开\n" +
                "• 某些设备上的TTS引擎可能存在兼容性问题";
        } else {
            dialogMessage = "您的设备似乎没有可用的文本转语音引擎。要使用音频播放功能，请：\n\n" +
                "• 在设备设置中安装或启用文本转语音引擎\n" +
                "• 下载和安装Google文本转语音引擎\n" +
                "• 下载英语(美国)语言包";
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("播放故障排除")
            .setMessage(dialogMessage)
            .setPositiveButton("重试", (dialog, which) -> {
                if (podcastContent != null) {
                    try {
                        String shortContent = getShortSampleText();
                        if (ttsHelper != null) {
                            boolean success = ttsHelper.speak(shortContent);
                            if (success) {
                                isPlaying = true;
                                updatePlayButtonState(true);
                                startProgressUpdates();
                            } else {
                                fallbackToDirectSystemTTS();
                            }
                        } else {
                            reInitializeTts();
                        }
                    } catch (Exception e) {
                        showError("重试TTS失败: " + e.getMessage());
                    }
                }
            })
            .setNeutralButton("TTS设置", (dialog, which) -> {
                try {
                    Intent intent = new Intent();
                    intent.setAction("com.android.settings.TTS_SETTINGS");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "无法打开TTS设置: " + e.getMessage());
                    try {
                        Intent intent = new Intent();
                        intent.setAction(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        startActivity(intent);
                    } catch (Exception e2) {
                        showError("无法打开设置，请手动进入系统设置 > 辅助功能 > 文本转语音");
                    }
                }
            })
            .setNegativeButton("仅阅读文本", (dialog, which) -> {
                transcriptText.setVisibility(View.VISIBLE);
                if (hostContainer != null) {
                    hostContainer.setVisibility(View.GONE);
                }
                currentWordIndicator.setText("音频播放不可用。正在以文本形式显示内容。");
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "您现在可以直接阅读播客文本内容。",
                    Snackbar.LENGTH_LONG
                ).show();
                if (transcriptScrollView != null) {
                    transcriptScrollView.requestFocus();
                    transcriptScrollView.scrollTo(0, 0);
                }
            })
            .setCancelable(true)
            .show();
    }
    private void reInitializeTts() {
        if (ttsHelper != null) {
            ttsHelper.shutdown();
            ttsHelper = null;
        }
        ttsHelper = new SimplifiedTTSHelper(this, new SimplifiedTTSHelper.InitCallback() {
            @Override
            public void onInitialized(boolean success) {
                isTtsInitialized = success;
                if (!success) {
                    Log.e(TAG, "重新初始化TTS失败");
                    runOnUiThread(() -> showError("无法初始化文本转语音。请检查系统设置。"));
                } else {
                    Log.d(TAG, "TTS重新初始化成功");
                    setupProgressTracking();
                    runOnUiThread(() -> {
                        String shortContent = getShortSampleText();
                        boolean playSuccess = ttsHelper.speak(shortContent);
                        if (playSuccess) {
                            isPlaying = true;
                            updatePlayButtonState(true);
                            startProgressUpdates();
                        } else {
                            fallbackToDirectSystemTTS();
                        }
                    });
                }
            }
        });
    }
    private boolean checkSystemTtsAvailable() {
        final boolean[] available = {false};
        final boolean[] initialized = {false};
        TextToSpeech testTts = null;
        try {
            testTts = new TextToSpeech(this, status -> {
                available[0] = (status == TextToSpeech.SUCCESS);
                initialized[0] = true;
                synchronized (available) {
                    available.notify();
                }
            });
            synchronized (available) {
                try {
                    if (!initialized[0]) {
                        available.wait(2000); 
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
    private String getShortSampleText() {
        if (podcastContent != null && podcastContent.getSegments().size() > 0) {
            PodcastSegment segment = podcastContent.getSegments().get(0);
            String text = segment.getText();
            if (text != null && !text.isEmpty()) {
                int endIndex = text.indexOf(". ");
                if (endIndex > 10 && endIndex < 100) {
                    return text.substring(0, endIndex + 1);
                } else {
                    return text.substring(0, Math.min(50, text.length()));
                }
            }
        }
        return "This is a test of the text to speech system.";
    }
    private String formatFullTranscript(String fullTranscript) {
        if (fullTranscript == null || fullTranscript.isEmpty()) {
            return "";
        }
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
        String formatted = cleanTranscript
            .replace("§HOST§", "<span style='color:#6200EE; font-weight:bold; display:block; margin-top:16dp'>HOST: </span>")
            .replace("§ALEX§", "<span style='color:#6200EE; font-weight:bold; display:block; margin-top:16dp'>HOST: </span>")
            .replace("§JORDAN§", "<span style='color:#6200EE; font-weight:bold; display:block; margin-top:16dp'>HOST: </span>");
        formatted = formatted.replace("\n\n", "<br><br>")
                           .replace("\n", "<br>");
        formatted = formatted.replace("AI is generating", "")
                         .replace("Starting generation", "")
                         .replace("Analyzing news", "")
                         .replace("Formatting conversation", "");
        return formatted;
    }
    private void createPodcastContentFromTranscript(String transcript) {
        if (transcript == null || transcript.isEmpty()) {
            Log.e(TAG, "Empty transcript");
            return;
        }
        PodcastContent content = new PodcastContent();
        content.setTitle(podcastTitle.getText().toString());
        String[] words = transcript.split("\\s+");
        int wordCount = words.length;
        float wordsPerSecond = 2.5f;
        int calculatedDuration = Math.round(wordCount / wordsPerSecond);
        int requestedDurationSeconds = duration * 60;
        int minimumDuration = Math.max(requestedDurationSeconds * 8 / 10, 180);
        int estimatedDuration = Math.max(calculatedDuration, minimumDuration);
        Log.d(TAG, "Transcript word count: " + wordCount + 
              ", Calculated duration: " + calculatedDuration + 
              "s, Using duration: " + estimatedDuration + "s");
        content.setTotalDuration(estimatedDuration);
        content.setTranscriptText(transcript);
        PodcastSegment segment = new PodcastSegment();
        segment.setTitle("Podcast");
        segment.setText(transcript);
        segment.setType(PodcastSegment.SegmentType.NEWS_ARTICLE);
        segment.setEstimatedDuration(estimatedDuration);
        List<PodcastSegment> segments = new ArrayList<>();
        segments.add(segment);
        content.setSegments(segments);
        content.setTopics(selectedTopics != null ? new ArrayList<>(selectedTopics) : new ArrayList<>());
        this.podcastContent = content;
        updatePodcastInfo();
        if (ttsHelper != null) {
            ttsHelper.speakPodcast(content);
        }
    }
    private void cancelGeneration() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cancel Generation")
            .setMessage("Are you sure you want to cancel the podcast generation?")
            .setPositiveButton("Yes", (dialog, which) -> {
                isCancelled = true;
                stopGenerationProgressSimulation();
                if (enhancedTTS != null) {
                    enhancedTTS.stop();
                }
                showGeneratingState(false);
                generationStatus.setText("Generation cancelled");
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Generation Cancelled")
                    .setMessage("Would you like to retry with different settings or return to topic selection?")
                    .setPositiveButton("Retry", (retryDialog, retryWhich) -> {
                        isPodcastGenerated = false;
                        generatePodcast();
                    })
                    .setNegativeButton("Return", (retryDialog, retryWhich) -> {
                        finish();
                    })
                    .show();
            })
            .setNegativeButton("No", null)
            .show();
    }
    private void clearPlaceholderTexts() {
        runOnUiThread(() -> {
            String hostCurrentText = hostText.getText().toString();
            if (hostCurrentText.contains("waiting") || 
                hostCurrentText.contains("preparing") || 
                hostCurrentText.contains("Preparing") ||
                hostCurrentText.contains("connect")) {
                hostText.setText("");
            }
        });
    }
    private void smoothScrollToBottom() {
        if (userIsScrolling) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAutoScrollTime < AUTO_SCROLL_THROTTLE_MS) {
            return;
        }
        lastAutoScrollTime = currentTime;
        if (transcriptScrollView != null) {
            transcriptScrollView.post(() -> {
                transcriptScrollView.smoothScrollTo(0, transcriptScrollView.getChildAt(0).getHeight());
            });
        }
    }
    private void clearGeneratingIndicators() {
        if (transcriptText != null) {
            transcriptText.setVisibility(View.VISIBLE);
        }
        generationProgress.setVisibility(View.GONE);
    }
    private void fallbackToStandardTTS(String content) {
        if (ttsHelper != null) {
            boolean success = false;
            if (podcastContent != null) {
                success = ttsHelper.speakPodcast(podcastContent);
            }
            if (!success) {
                success = ttsHelper.speak(content);
                if (!success) {
                    showError("All TTS playback methods failed. Please try again later.");
                    isPlaying = false;
                    updatePlayButtonState(false);
                    showTTSErrorDialog();
                }
            }
        } else {
            showError("TTS service not initialized");
            isPlaying = false;
            updatePlayButtonState(false);
        }
    }
    private void fallbackToDirectSystemTTS() {
        try {
            Log.d(TAG, "Falling back to direct system TTS");
            String content = getShortSampleText();
            if (directSystemTTS != null) {
                try {
                    directSystemTTS.stop();
                    directSystemTTS.shutdown();
                    directSystemTTS = null;
                } catch (Exception e) {
                    Log.e(TAG, "Error shutting down previous TTS instance: " + e.getMessage());
                }
            }
            directSystemTTS = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    Log.d(TAG, "Direct TTS initialized successfully");
                    int langResult = directSystemTTS.setLanguage(Locale.US);
                    if (langResult == TextToSpeech.LANG_MISSING_DATA || 
                        langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        showError("TTS language data is missing or not supported. Please install English language pack.");
                        return;
                    }
                    directSystemTTS.setSpeechRate(1.0f);
                    directSystemTTS.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                            runOnUiThread(() -> {
                                isPlaying = true;
                                updatePlayButtonState(true);
                                Log.d(TAG, "Direct TTS started playback");
                            });
                        }
                        @Override
                        public void onDone(String utteranceId) {
                            runOnUiThread(() -> {
                                isPlaying = false;
                                updatePlayButtonState(false);
                                Log.d(TAG, "Direct TTS playback completed");
                            });
                        }
                        @Override
                        public void onError(String utteranceId) {
                            runOnUiThread(() -> {
                                isPlaying = false;
                                updatePlayButtonState(false);
                                showError("TTS playback error: " + utteranceId);
                                Log.e(TAG, "Direct TTS error: " + utteranceId);
                            });
                        }
                    });
                    String utteranceId = "directTTS_" + System.currentTimeMillis();
                    Bundle params = new Bundle();
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f); 
                    Log.d(TAG, "Attempting to play text with length: " + content.length());
                    int result = directSystemTTS.speak(content, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
                    if (result != TextToSpeech.SUCCESS) {
                        Log.e(TAG, "Failed to play content with TTS, result code: " + result);
                        String errorMsg;
                        switch (result) {
                            case TextToSpeech.ERROR:
                                errorMsg = "TTS general error";
                                break;
                            case TextToSpeech.ERROR_SYNTHESIS:
                                errorMsg = "TTS synthesis error";
                                break;
                            case TextToSpeech.ERROR_SERVICE:
                                errorMsg = "TTS service error";
                                break;
                            case TextToSpeech.ERROR_OUTPUT:
                                errorMsg = "TTS audio output error";
                                break;
                            case TextToSpeech.ERROR_NETWORK:
                                errorMsg = "TTS network error";
                                break;
                            case TextToSpeech.ERROR_INVALID_REQUEST:
                                errorMsg = "TTS invalid request";
                                break;
                            case TextToSpeech.ERROR_NOT_INSTALLED_YET:
                                errorMsg = "TTS engine not fully installed";
                                break;
                            default:
                                errorMsg = "Unknown TTS error: " + result;
                        }
                        showError("Failed to play content: " + errorMsg);
                        showTTSErrorDialog();
                    }
                } else {
                    Log.e(TAG, "Failed to initialize system TTS, status: " + status);
                    showError("Failed to initialize system TTS. Please check your device settings.");
                    showTTSErrorDialog();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in direct TTS initialization: " + e.getMessage(), e);
            showError("TTS initialization error: " + e.getMessage());
            showTTSErrorDialog();
        }
    }
    private boolean isSpeaking() {
        return ttsHelper != null && ttsHelper.isSpeaking();
    }
} 