package com.example.aipodcast.ui;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.aipodcast.model.AudioSegment;
import com.example.aipodcast.model.PodcastContent;
import com.example.aipodcast.model.PodcastPlayerState;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.HashMap;
import java.util.Map;

/**
 * Class responsible for managing the podcast player UI state.
 * Handles UI updates, visibility states, and animations.
 */
public class PodcastUIState {
    // UI component visibility states
    private boolean isPlayerControlsVisible = false;
    private boolean isTranscriptVisible = true;
    private boolean isGeneratingStateVisible = false;
    
    // Animation states
    private boolean isAnimatingPlayerControls = false;
    private boolean isScrollingTranscript = false;
    
    // UI component references for fast access
    private Map<String, View> viewCache = new HashMap<>();
    
    // Handler for UI updates
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    
    // Current highlighted segment
    private AudioSegment currentHighlightedSegment = null;
    
    // Player state reference
    private PodcastPlayerState playerState;
    
    /**
     * Constructor
     * 
     * @param playerState Reference to player state
     */
    public PodcastUIState(PodcastPlayerState playerState) {
        this.playerState = playerState;
    }
    
    /**
     * Register a view for fast access
     * 
     * @param key View identifier
     * @param view View reference
     */
    public void registerView(String key, View view) {
        viewCache.put(key, view);
    }
    
    /**
     * Get a registered view
     * 
     * @param key View identifier
     * @return The view, or null if not found
     */
    public View getView(String key) {
        return viewCache.get(key);
    }
    
    /**
     * Clear all registered views
     */
    public void clearViews() {
        viewCache.clear();
    }
    
    /**
     * Show or hide the player controls
     * 
     * @param visible Whether controls should be visible
     * @param animate Whether to animate the transition
     */
    public void showPlayerControls(boolean visible, boolean animate) {
        if (isPlayerControlsVisible == visible) return;
        
        View playerControls = viewCache.get("playerControls");
        if (playerControls == null) return;
        
        isPlayerControlsVisible = visible;
        
        if (animate) {
            isAnimatingPlayerControls = true;
            
            if (visible) {
                playerControls.setVisibility(View.VISIBLE);
                playerControls.setTranslationY(playerControls.getHeight());
                playerControls.animate()
                    .translationY(0)
                    .setDuration(300)
                    .withEndAction(() -> isAnimatingPlayerControls = false)
                    .start();
            } else {
                playerControls.animate()
                    .translationY(playerControls.getHeight())
                    .setDuration(300)
                    .withEndAction(() -> {
                        playerControls.setVisibility(View.GONE);
                        isAnimatingPlayerControls = false;
                    })
                    .start();
            }
        } else {
            playerControls.setVisibility(visible ? View.VISIBLE : View.GONE);
            playerControls.setTranslationY(visible ? 0 : playerControls.getHeight());
        }
    }
    
    /**
     * Show or hide the generating state UI
     * 
     * @param visible Whether generating state should be visible
     */
    public void showGeneratingState(boolean visible) {
        if (isGeneratingStateVisible == visible) return;
        
        View generationCard = viewCache.get("generationCard");
        if (generationCard == null) return;
        
        isGeneratingStateVisible = visible;
        generationCard.setVisibility(visible ? View.VISIBLE : View.GONE);
        
        // Also update the progress bar
        ProgressBar progressBar = (ProgressBar) viewCache.get("generationProgress");
        if (progressBar != null) {
            if (visible) {
                if (playerState.getGenerationProgress() > 0 && playerState.getGenerationProgress() < 100) {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(playerState.getGenerationProgress());
                } else {
                    progressBar.setIndeterminate(true);
                }
            }
        }
        
        // Update status text
        TextView statusText = (TextView) viewCache.get("generationStatus");
        if (statusText != null && visible) {
            if (playerState.isGenerating()) {
                statusText.setText("Generating podcast content...");
            } else if (playerState.isError()) {
                statusText.setText("Error: " + playerState.getErrorMessage());
            }
        }
    }
    
    /**
     * Update the generation progress UI
     * 
     * @param progress Progress percentage (0-100)
     * @param statusMessage Status message to display
     */
    public void updateGenerationProgress(int progress, String statusMessage) {
        playerState.setGenerationProgress(progress);
        
        ProgressBar progressBar = (ProgressBar) viewCache.get("generationProgress");
        if (progressBar != null) {
            if (progress > 0 && progress < 100) {
                progressBar.setIndeterminate(false);
                progressBar.setProgress(progress);
            } else {
                progressBar.setIndeterminate(true);
            }
        }
        
        TextView statusText = (TextView) viewCache.get("generationStatus");
        if (statusText != null && statusMessage != null) {
            statusText.setText(statusMessage);
        }
    }
    
    /**
     * Update the podcast info UI with content
     * 
     * @param content Podcast content to display
     */
    public void updatePodcastInfo(PodcastContent content) {
        if (content == null) return;
        
        // Update title
        TextView titleView = (TextView) viewCache.get("podcastTitle");
        if (titleView != null) {
            titleView.setText(content.getTitle());
        }
        
        // Update duration
        TextView durationView = (TextView) viewCache.get("podcastDuration");
        if (durationView != null) {
            durationView.setText("Duration: " + content.getFormattedDuration());
        }
        
        // Update topics
        ChipGroup topicsGroup = (ChipGroup) viewCache.get("topicsChipGroup");
        if (topicsGroup != null && content.getTopics() != null) {
            topicsGroup.removeAllViews();
            
            for (String topic : content.getTopics()) {
                Chip chip = new Chip(topicsGroup.getContext());
                chip.setText(topic);
                chip.setClickable(false);
                topicsGroup.addView(chip);
            }
        }
    }
    
    /**
     * Update the transcript display with current content
     * 
     * @param segment Current audio segment to highlight
     * @param scrollToSegment Whether to scroll to the segment
     */
    public void updateTranscriptDisplay(AudioSegment segment, boolean scrollToSegment) {
        if (segment == null) return;
        
        // Update current segment text
        TextView currentSegmentLabel = (TextView) viewCache.get("currentSectionLabel");
        if (currentSegmentLabel != null) {
            String typeLabel = "";
            
            switch (segment.getType()) {
                case INTRO:
                    typeLabel = "Introduction";
                    break;
                case CONCLUSION:
                    typeLabel = "Conclusion";
                    break;
                case TRANSITION:
                    typeLabel = "Transition";
                    break;
                default:
                    typeLabel = "Content";
            }
            
            currentSegmentLabel.setText(typeLabel);
        }
        
        // Update transcript text
        TextView transcriptText = (TextView) viewCache.get("transcriptText");
        TextView hostText = (TextView) viewCache.get("hostText");
        View hostContainer = viewCache.get("hostContainer");
        
        if (segment != currentHighlightedSegment) {
            currentHighlightedSegment = segment;
            
            // Check if we're using streaming mode with host containers
            if (hostContainer != null && hostContainer.getVisibility() == View.VISIBLE && hostText != null) {
                hostText.setText(segment.getText());
            } else if (transcriptText != null) {
                transcriptText.setText(segment.getFormattedText());
            }
            
            // Scroll to make current text visible
            if (scrollToSegment && !isScrollingTranscript) {
                isScrollingTranscript = true;
                uiHandler.postDelayed(() -> {
                    View scrollView = viewCache.get("transcriptScrollView");
                    if (scrollView != null && scrollView instanceof androidx.core.widget.NestedScrollView) {
                        ((androidx.core.widget.NestedScrollView) scrollView).smoothScrollTo(0, 
                            getEstimatedScrollPosition(segment));
                    }
                    isScrollingTranscript = false;
                }, 300);
            }
        }
    }
    
    /**
     * Estimate scroll position for a segment
     * 
     * @param segment The segment to scroll to
     * @return Estimated scroll position
     */
    private int getEstimatedScrollPosition(AudioSegment segment) {
        // Simple estimation - in a real implementation, this would calculate
        // based on actual segment positions in the transcript
        View transcriptScrollView = viewCache.get("transcriptScrollView");
        if (transcriptScrollView == null) return 0;
        
        int viewHeight = transcriptScrollView.getHeight();
        int totalContentHeight = viewHeight * 3; // Approximate content height
        
        if (segment == null) return 0;
        
        // Estimate position based on segment start time relative to total duration
        float progress = 0;
        if (playerState.getTotalDuration() > 0) {
            progress = (float) segment.getStartTimeMs() / (playerState.getTotalDuration() * 1000);
        }
        
        return (int) (totalContentHeight * progress);
    }
    
    /**
     * Update play/pause button state
     * 
     * @param isPlaying Whether podcast is currently playing
     */
    public void updatePlayButtonState(boolean isPlaying) {
        View playPauseButton = viewCache.get("playPauseButton");
        if (playPauseButton == null) return;
        
        if (playPauseButton instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) playPauseButton).setImageResource(
                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        } else if (playPauseButton instanceof com.google.android.material.floatingactionbutton.FloatingActionButton) {
            ((com.google.android.material.floatingactionbutton.FloatingActionButton) playPauseButton).setImageResource(
                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        }
    }
    
    /**
     * Update playback time displays
     * 
     * @param currentPosition Current position in milliseconds
     * @param totalDuration Total duration in milliseconds
     */
    public void updateTimeDisplays(int currentPosition, int totalDuration) {
        // Convert to seconds
        int currentSec = currentPosition / 1000;
        int totalSec = totalDuration / 1000;
        
        // Update player state
        playerState.setCurrentPosition(currentPosition);
        playerState.setTotalDuration(totalDuration);
        
        // Update UI
        TextView currentTimeText = (TextView) viewCache.get("currentTimeText");
        if (currentTimeText != null) {
            currentTimeText.setText(formatTime(currentSec));
        }
        
        TextView totalTimeText = (TextView) viewCache.get("totalTimeText");
        if (totalTimeText != null) {
            totalTimeText.setText(formatTime(totalSec));
        }
    }
    
    /**
     * Update seekbar progress
     * 
     * @param currentPosition Current position in milliseconds
     * @param totalDuration Total duration in milliseconds
     */
    public void updateSeekBar(int currentPosition, int totalDuration) {
        android.widget.SeekBar seekBar = (android.widget.SeekBar) viewCache.get("seekBar");
        if (seekBar == null) return;
        
        // Convert to seconds for the seekbar
        int currentSec = currentPosition / 1000;
        int totalSec = totalDuration / 1000;
        
        seekBar.setMax(totalSec);
        seekBar.setProgress(currentSec);
    }
    
    /**
     * Format time in seconds to MM:SS
     * 
     * @param seconds Time in seconds
     * @return Formatted time string
     */
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
    
    /**
     * Show error message in appropriate UI location
     * 
     * @param message Error message to display
     */
    public void showError(String message) {
        // Update player state
        playerState.setError(message);
        
        // Show in appropriate error display
        TextView errorText = (TextView) viewCache.get("errorText");
        if (errorText != null) {
            errorText.setText(message);
            errorText.setVisibility(View.VISIBLE);
            
            // Auto-hide after delay
            uiHandler.postDelayed(() -> {
                errorText.setVisibility(View.GONE);
            }, 5000);
        } else {
            // Fallback to generation status
            TextView statusText = (TextView) viewCache.get("generationStatus");
            if (statusText != null) {
                statusText.setText("Error: " + message);
                showGeneratingState(true);
            }
        }
    }
} 