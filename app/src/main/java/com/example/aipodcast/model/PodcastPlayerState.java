package com.example.aipodcast.model;
public class PodcastPlayerState {
    public static final int STATE_IDLE = 0;
    public static final int STATE_GENERATING = 1;
    public static final int STATE_READY = 2;
    public static final int STATE_PLAYING = 3;
    public static final int STATE_PAUSED = 4;
    public static final int STATE_ERROR = 5;
    private int currentState = STATE_IDLE;
    private int generationProgress = 0;
    private int currentPosition = 0;
    private int totalDuration = 0;
    private int currentSegmentIndex = 0;
    private float playbackSpeed = 1.0f;
    private String errorMessage = "";
    private boolean isAIGeneration = true;
    private boolean isGenerationCancelled = false;
    public PodcastPlayerState() {
    }
    public void setGenerating() {
        currentState = STATE_GENERATING;
        generationProgress = 0;
    }
    public void setReady() {
        currentState = STATE_READY;
        generationProgress = 100;
    }
    public void setPlaying() {
        currentState = STATE_PLAYING;
    }
    public void setPaused() {
        currentState = STATE_PAUSED;
    }
    public void setError(String message) {
        currentState = STATE_ERROR;
        this.errorMessage = message;
    }
    public void setIdle() {
        currentState = STATE_IDLE;
    }
    public boolean isGenerating() {
        return currentState == STATE_GENERATING;
    }
    public boolean isReady() {
        return currentState == STATE_READY;
    }
    public boolean isPlaying() {
        return currentState == STATE_PLAYING;
    }
    public boolean isPaused() {
        return currentState == STATE_PAUSED;
    }
    public boolean isError() {
        return currentState == STATE_ERROR;
    }
    public boolean isIdle() {
        return currentState == STATE_IDLE;
    }
    public int getCurrentState() {
        return currentState;
    }
    public void setCurrentState(int currentState) {
        this.currentState = currentState;
    }
    public int getGenerationProgress() {
        return generationProgress;
    }
    public void setGenerationProgress(int generationProgress) {
        this.generationProgress = Math.min(100, Math.max(0, generationProgress));
    }
    public int getCurrentPosition() {
        return currentPosition;
    }
    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }
    public int getTotalDuration() {
        return totalDuration;
    }
    public void setTotalDuration(int totalDuration) {
        this.totalDuration = totalDuration;
    }
    public int getCurrentSegmentIndex() {
        return currentSegmentIndex;
    }
    public void setCurrentSegmentIndex(int currentSegmentIndex) {
        this.currentSegmentIndex = currentSegmentIndex;
    }
    public float getPlaybackSpeed() {
        return playbackSpeed;
    }
    public void setPlaybackSpeed(float playbackSpeed) {
        this.playbackSpeed = playbackSpeed;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    public boolean isAIGeneration() {
        return isAIGeneration;
    }
    public void setAIGeneration(boolean AIGeneration) {
        isAIGeneration = AIGeneration;
    }
    public boolean isGenerationCancelled() {
        return isGenerationCancelled;
    }
    public void setGenerationCancelled(boolean generationCancelled) {
        isGenerationCancelled = generationCancelled;
    }
    public String getFormattedCurrentTime() {
        return formatTime(currentPosition / 1000); 
    }
    public String getFormattedTotalTime() {
        return formatTime(totalDuration / 1000); 
    }
    public String getFormattedPlaybackSpeed() {
        return String.format("%.1fx", playbackSpeed);
    }
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
} 