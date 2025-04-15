package com.example.aipodcast.model;
import java.io.Serializable;
public class AudioSegment implements Serializable {
    public enum SegmentType {
        INTRO,
        CONTENT,
        TRANSITION,
        CONCLUSION
    }
    private String id;
    private String text;
    private String speakerTag;
    private long startTimeMs;
    private long durationMs;
    private SegmentType type;
    private boolean isHighlighted;
    private int wordCount;
    private boolean isPlayed;
    private boolean isCurrentlyPlaying;
    private int lastPlayPosition;
    public AudioSegment() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.startTimeMs = 0;
        this.durationMs = 0;
        this.type = SegmentType.CONTENT;
        this.isHighlighted = false;
        this.isPlayed = false;
        this.isCurrentlyPlaying = false;
        this.lastPlayPosition = 0;
        this.wordCount = 0;
    }
    public AudioSegment(String text, SegmentType type) {
        this();
        this.text = text;
        this.type = type;
        this.wordCount = countWords(text);
        this.durationMs = (long) (wordCount / 2.5 * 1000);
    }
    public AudioSegment(String id, String text, String speakerTag, 
                       long startTimeMs, long durationMs, SegmentType type) {
        this.id = id;
        this.text = text;
        this.speakerTag = speakerTag;
        this.startTimeMs = startTimeMs;
        this.durationMs = durationMs;
        this.type = type;
        this.isHighlighted = false;
        this.isPlayed = false;
        this.isCurrentlyPlaying = false;
        this.lastPlayPosition = 0;
        this.wordCount = countWords(text);
    }
    private int countWords(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String[] words = text.trim().split("\\s+");
        return words.length;
    }
    public long getEndTimeMs() {
        return startTimeMs + durationMs;
    }
    public boolean containsTime(long timeMs) {
        return timeMs >= startTimeMs && timeMs < getEndTimeMs();
    }
    public int getProgressPercentage(long currentTimeMs) {
        if (durationMs <= 0 || !containsTime(currentTimeMs)) {
            return 0;
        }
        long elapsedTime = currentTimeMs - startTimeMs;
        return (int) (elapsedTime * 100 / durationMs);
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
        this.wordCount = countWords(text);
    }
    public String getSpeakerTag() {
        return speakerTag;
    }
    public void setSpeakerTag(String speakerTag) {
        this.speakerTag = speakerTag;
    }
    public long getStartTimeMs() {
        return startTimeMs;
    }
    public void setStartTimeMs(long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }
    public long getDurationMs() {
        return durationMs;
    }
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
    public SegmentType getType() {
        return type;
    }
    public void setType(SegmentType type) {
        this.type = type;
    }
    public boolean isHighlighted() {
        return isHighlighted;
    }
    public void setHighlighted(boolean highlighted) {
        isHighlighted = highlighted;
    }
    public boolean isPlayed() {
        return isPlayed;
    }
    public void setPlayed(boolean played) {
        isPlayed = played;
    }
    public boolean isCurrentlyPlaying() {
        return isCurrentlyPlaying;
    }
    public void setCurrentlyPlaying(boolean currentlyPlaying) {
        isCurrentlyPlaying = currentlyPlaying;
    }
    public int getLastPlayPosition() {
        return lastPlayPosition;
    }
    public void setLastPlayPosition(int lastPlayPosition) {
        this.lastPlayPosition = lastPlayPosition;
    }
    public int getWordCount() {
        return wordCount;
    }
    public void updatePlaybackStatus(long currentTimeMs) {
        if (containsTime(currentTimeMs)) {
            this.isCurrentlyPlaying = true;
            if (currentTimeMs > startTimeMs) {
                this.isPlayed = true;
            }
            this.lastPlayPosition = (int)(currentTimeMs - startTimeMs);
        } else if (currentTimeMs >= getEndTimeMs()) {
            this.isPlayed = true;
            this.isCurrentlyPlaying = false;
            this.lastPlayPosition = (int)durationMs;
        } else {
            this.isCurrentlyPlaying = false;
        }
    }
    public String getFormattedText() {
        if (speakerTag != null && !speakerTag.isEmpty()) {
            return speakerTag + ": " + text;
        } else {
            return text;
        }
    }
    public String getFormattedDuration() {
        int seconds = (int) (durationMs / 1000);
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
} 