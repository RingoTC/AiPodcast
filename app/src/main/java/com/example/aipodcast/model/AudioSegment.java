package com.example.aipodcast.model;

import java.io.Serializable;

/**
 * Model class representing an audio segment in a podcast.
 * Designed to support streaming playback with text highlighting.
 */
public class AudioSegment implements Serializable {
    // Audio segment types
    public enum SegmentType {
        INTRO,
        CONTENT,
        TRANSITION,
        CONCLUSION
    }
    
    // Audio segment data
    private String id;
    private String text;
    private String speakerTag;
    private long startTimeMs;
    private long durationMs;
    private SegmentType type;
    private boolean isHighlighted;
    private int wordCount;
    
    // Playback status flags
    private boolean isPlayed;
    private boolean isCurrentlyPlaying;
    private int lastPlayPosition;
    
    /**
     * Default constructor
     */
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
    
    /**
     * Constructor with basic parameters
     * 
     * @param text The text content
     * @param type The segment type
     */
    public AudioSegment(String text, SegmentType type) {
        this();
        this.text = text;
        this.type = type;
        this.wordCount = countWords(text);
        
        // Estimate duration based on word count
        // Average speaking rate: ~150 words per minute = 2.5 words per second
        this.durationMs = (long) (wordCount / 2.5 * 1000);
    }
    
    /**
     * Full constructor
     * 
     * @param id Unique identifier
     * @param text Text content
     * @param speakerTag Speaker identifier
     * @param startTimeMs Start time in milliseconds
     * @param durationMs Duration in milliseconds
     * @param type Segment type
     */
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
    
    /**
     * Count the number of words in a text
     * 
     * @param text Text to analyze
     * @return Word count
     */
    private int countWords(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        String[] words = text.trim().split("\\s+");
        return words.length;
    }
    
    /**
     * Calculate the end time of this segment
     * 
     * @return End time in milliseconds
     */
    public long getEndTimeMs() {
        return startTimeMs + durationMs;
    }
    
    /**
     * Check if a specific time falls within this segment
     * 
     * @param timeMs Time in milliseconds
     * @return True if time is within this segment
     */
    public boolean containsTime(long timeMs) {
        return timeMs >= startTimeMs && timeMs < getEndTimeMs();
    }
    
    /**
     * Calculate the progress percentage within this segment
     * 
     * @param currentTimeMs Current time in milliseconds
     * @return Progress percentage (0-100)
     */
    public int getProgressPercentage(long currentTimeMs) {
        if (durationMs <= 0 || !containsTime(currentTimeMs)) {
            return 0;
        }
        
        long elapsedTime = currentTimeMs - startTimeMs;
        return (int) (elapsedTime * 100 / durationMs);
    }

    // Getters and Setters
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
    
    /**
     * Update playback status based on current time
     * 
     * @param currentTimeMs Current playback time
     */
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
    
    /**
     * Get formatted text with speaker tag if present
     * 
     * @return Formatted text for display
     */
    public String getFormattedText() {
        if (speakerTag != null && !speakerTag.isEmpty()) {
            return speakerTag + ": " + text;
        } else {
            return text;
        }
    }
    
    /**
     * Get formatted duration as mm:ss
     * 
     * @return Formatted duration string
     */
    public String getFormattedDuration() {
        int seconds = (int) (durationMs / 1000);
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
} 