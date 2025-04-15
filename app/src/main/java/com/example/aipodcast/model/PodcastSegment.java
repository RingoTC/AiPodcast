package com.example.aipodcast.model;

/**
 * Model representing a single segment within a podcast.
 * Segments are discrete parts of a podcast such as introduction, 
 * individual news articles, transitions, and conclusion.
 */
public class PodcastSegment {
    private String title;
    private String text;
    private NewsArticle sourceArticle;
    private int estimatedDuration; // estimated duration in seconds
    private SegmentType type;

    /**
     * Segment types in a podcast
     */
    public enum SegmentType {
        INTRO,
        NEWS_ARTICLE,
        TRANSITION,
        CONCLUSION
    }

    /**
     * Default constructor
     */
    public PodcastSegment() {
    }

    /**
     * Constructor for segments with source articles
     * 
     * @param title Segment title
     * @param text Content text
     * @param sourceArticle Source news article
     * @param type Segment type
     */
    public PodcastSegment(String title, String text, NewsArticle sourceArticle, SegmentType type) {
        this.title = title;
        this.text = text;
        this.sourceArticle = sourceArticle;
        this.type = type;
        this.estimatedDuration = estimateDuration(text);
    }

    /**
     * Constructor for segments without source articles (intros, transitions, etc)
     * 
     * @param title Segment title
     * @param text Content text
     * @param type Segment type
     */
    public PodcastSegment(String title, String text, SegmentType type) {
        this.title = title;
        this.text = text;
        this.type = type;
        this.estimatedDuration = estimateDuration(text);
    }

    /**
     * Estimate the duration based on text length
     * Using a simple approximation: average English speaker says ~150 words per minute
     * 
     * @param text The text to estimate
     * @return Estimated duration in seconds
     */
    private int estimateDuration(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // Count words by splitting on whitespace
        String[] words = text.split("\\s+");
        int wordCount = words.length;
        
        // Calculate seconds (150 words per minute = 2.5 words per second)
        float wordsPerSecond = 2.5f;
        return Math.round(wordCount / wordsPerSecond);
    }

    // Getters and setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        this.estimatedDuration = estimateDuration(text);
    }

    public NewsArticle getSourceArticle() {
        return sourceArticle;
    }

    public void setSourceArticle(NewsArticle sourceArticle) {
        this.sourceArticle = sourceArticle;
    }

    public int getEstimatedDuration() {
        return estimatedDuration;
    }

    public void setEstimatedDuration(int estimatedDuration) {
        this.estimatedDuration = estimatedDuration;
    }

    public SegmentType getType() {
        return type;
    }

    public void setType(SegmentType type) {
        this.type = type;
    }
} 