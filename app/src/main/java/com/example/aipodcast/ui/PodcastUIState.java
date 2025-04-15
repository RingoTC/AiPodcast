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
public class PodcastUIState {
    private boolean isPlayerControlsVisible = false;
    private boolean isTranscriptVisible = true;
    private boolean isGeneratingStateVisible = false;
    private boolean isAnimatingPlayerControls = false;
    private boolean isScrollingTranscript = false;
    private Map<String, View> viewCache = new HashMap<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private AudioSegment currentHighlightedSegment = null;
    private PodcastPlayerState playerState;
    public PodcastUIState(PodcastPlayerState playerState) {
        this.playerState = playerState;
    }
    public void registerView(String key, View view) {
        viewCache.put(key, view);
    }
    public View getView(String key) {
        return viewCache.get(key);
    }
    public void clearViews() {
        viewCache.clear();
    }
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
    public void showGeneratingState(boolean visible) {
        if (isGeneratingStateVisible == visible) return;
        View generationCard = viewCache.get("generationCard");
        if (generationCard == null) return;
        isGeneratingStateVisible = visible;
        generationCard.setVisibility(visible ? View.VISIBLE : View.GONE);
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
        TextView statusText = (TextView) viewCache.get("generationStatus");
        if (statusText != null && visible) {
            if (playerState.isGenerating()) {
                statusText.setText("Generating podcast content...");
            } else if (playerState.isError()) {
                statusText.setText("Error: " + playerState.getErrorMessage());
            }
        }
    }
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
    public void updatePodcastInfo(PodcastContent content) {
        if (content == null) return;
        TextView titleView = (TextView) viewCache.get("podcastTitle");
        if (titleView != null) {
            titleView.setText(content.getTitle());
        }
        TextView durationView = (TextView) viewCache.get("podcastDuration");
        if (durationView != null) {
            durationView.setText("Duration: " + content.getFormattedDuration());
        }
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
    public void updateTranscriptDisplay(AudioSegment segment, boolean scrollToSegment) {
        if (segment == null) return;
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
        TextView transcriptText = (TextView) viewCache.get("transcriptText");
        TextView hostText = (TextView) viewCache.get("hostText");
        View hostContainer = viewCache.get("hostContainer");
        if (segment != currentHighlightedSegment) {
            currentHighlightedSegment = segment;
            if (hostContainer != null && hostContainer.getVisibility() == View.VISIBLE && hostText != null) {
                hostText.setText(segment.getText());
            } else if (transcriptText != null) {
                transcriptText.setText(segment.getFormattedText());
            }
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
    private int getEstimatedScrollPosition(AudioSegment segment) {
        View transcriptScrollView = viewCache.get("transcriptScrollView");
        if (transcriptScrollView == null) return 0;
        int viewHeight = transcriptScrollView.getHeight();
        int totalContentHeight = viewHeight * 3; 
        if (segment == null) return 0;
        float progress = 0;
        if (playerState.getTotalDuration() > 0) {
            progress = (float) segment.getStartTimeMs() / (playerState.getTotalDuration() * 1000);
        }
        return (int) (totalContentHeight * progress);
    }
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
    public void updateTimeDisplays(int currentPosition, int totalDuration) {
        int currentSec = currentPosition / 1000;
        int totalSec = totalDuration / 1000;
        playerState.setCurrentPosition(currentPosition);
        playerState.setTotalDuration(totalDuration);
        TextView currentTimeText = (TextView) viewCache.get("currentTimeText");
        if (currentTimeText != null) {
            currentTimeText.setText(formatTime(currentSec));
        }
        TextView totalTimeText = (TextView) viewCache.get("totalTimeText");
        if (totalTimeText != null) {
            totalTimeText.setText(formatTime(totalSec));
        }
    }
    public void updateSeekBar(int currentPosition, int totalDuration) {
        android.widget.SeekBar seekBar = (android.widget.SeekBar) viewCache.get("seekBar");
        if (seekBar == null) return;
        int currentSec = currentPosition / 1000;
        int totalSec = totalDuration / 1000;
        seekBar.setMax(totalSec);
        seekBar.setProgress(currentSec);
    }
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
    public void showError(String message) {
        playerState.setError(message);
        TextView errorText = (TextView) viewCache.get("errorText");
        if (errorText != null) {
            errorText.setText(message);
            errorText.setVisibility(View.VISIBLE);
            uiHandler.postDelayed(() -> {
                errorText.setVisibility(View.GONE);
            }, 5000);
        } else {
            TextView statusText = (TextView) viewCache.get("generationStatus");
            if (statusText != null) {
                statusText.setText("Error: " + message);
                showGeneratingState(true);
            }
        }
    }
} 