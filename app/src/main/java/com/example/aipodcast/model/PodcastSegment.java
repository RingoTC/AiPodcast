package com.example.aipodcast.model;
public class PodcastSegment {
    private String title;
    private String text;
    private NewsArticle sourceArticle;
    private int estimatedDuration; 
    private SegmentType type;
    public enum SegmentType {
        INTRO,
        NEWS_ARTICLE,
        TRANSITION,
        CONCLUSION
    }
    public PodcastSegment() {
    }
    public PodcastSegment(String title, String text, NewsArticle sourceArticle, SegmentType type) {
        this.title = title;
        this.text = text;
        this.sourceArticle = sourceArticle;
        this.type = type;
        this.estimatedDuration = estimateDuration(text);
    }
    public PodcastSegment(String title, String text, SegmentType type) {
        this.title = title;
        this.text = text;
        this.type = type;
        this.estimatedDuration = estimateDuration(text);
    }
    private int estimateDuration(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String[] words = text.split("\\s+");
        int wordCount = words.length;
        float wordsPerSecond = 2.5f;
        return Math.round(wordCount / wordsPerSecond);
    }
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