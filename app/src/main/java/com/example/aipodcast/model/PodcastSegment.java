package com.example.aipodcast.model;

public class PodcastSegment {
    private String title;
    private String text;
    private NewsArticle sourceArticle;
    private int estimatedDuration; // in seconds
    private SegmentType type;

    // Standard speaking rate constants
    private static final float SLOW_WORDS_PER_SECOND = 2.0f;  // 120 wpm
    private static final float MEDIUM_WORDS_PER_SECOND = 2.33f; // 140 wpm
    private static final float FAST_WORDS_PER_SECOND = 2.67f;  // 160 wpm

    // Pauses between sentences in milliseconds
    private static final int SENTENCE_PAUSE_MS = 500;

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

        // Count words
        String[] words = text.split("\\s+");
        int wordCount = words.length;

        // Count sentences for pause estimation
        int sentenceCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                sentenceCount++;
            }
        }
        sentenceCount = Math.max(1, sentenceCount);

        // Determine speaking rate based on segment type
        float wordsPerSecond;
        switch (type) {
            case INTRO:
                wordsPerSecond = MEDIUM_WORDS_PER_SECOND;
                break;
            case CONCLUSION:
                wordsPerSecond = SLOW_WORDS_PER_SECOND;
                break;
            case TRANSITION:
                wordsPerSecond = FAST_WORDS_PER_SECOND;
                break;
            case NEWS_ARTICLE:
            default:
                wordsPerSecond = MEDIUM_WORDS_PER_SECOND;
                break;
        }

        // Calculate speaking time
        int speakingTimeSeconds = Math.round(wordCount / wordsPerSecond);

        // Add time for pauses between sentences
        int pauseTimeSeconds = sentenceCount * SENTENCE_PAUSE_MS / 1000;

        // Total estimated duration
        return speakingTimeSeconds + pauseTimeSeconds;
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

    /**
     * Force recalculation of the estimated duration
     */
    public void recalculateDuration() {
        this.estimatedDuration = estimateDuration(this.text);
    }

    /**
     * Get the estimated word count of this segment
     */
    public int getWordCount() {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String[] words = text.split("\\s+");
        return words.length;
    }
}