package com.example.aipodcast.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Model representing the complete content of a podcast.
 * Contains all segments and metadata for a podcast.
 */
public class PodcastContent {
    private String title;
    private List<String> topics;
    private List<PodcastSegment> segments;
    private Date creationDate;
    private int totalDuration; // estimated duration in seconds

    /**
     * Default constructor initializing empty lists
     */
    public PodcastContent() {
        this.topics = new ArrayList<>();
        this.segments = new ArrayList<>();
        this.creationDate = new Date();
    }

    /**
     * Parameterized constructor
     * 
     * @param title Podcast title
     * @param topics List of topics covered
     */
    public PodcastContent(String title, List<String> topics) {
        this.title = title;
        this.topics = topics;
        this.segments = new ArrayList<>();
        this.creationDate = new Date();
    }

    /**
     * Add a segment to the podcast content
     * 
     * @param segment The segment to add
     * @return This PodcastContent instance for chaining
     */
    public PodcastContent addSegment(PodcastSegment segment) {
        this.segments.add(segment);
        this.totalDuration += segment.getEstimatedDuration();
        return this;
    }

    /**
     * Get the complete text content of the podcast
     * 
     * @return Concatenated text of all segments
     */
    public String getFullText() {
        StringBuilder builder = new StringBuilder();
        for (PodcastSegment segment : segments) {
            builder.append(segment.getText()).append("\n\n");
        }
        return builder.toString();
    }

    // Getters and setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }

    public List<PodcastSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<PodcastSegment> segments) {
        this.segments = segments;
        this.totalDuration = 0;
        for (PodcastSegment segment : segments) {
            this.totalDuration += segment.getEstimatedDuration();
        }
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public int getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(int totalDuration) {
        this.totalDuration = totalDuration;
    }
    
    /**
     * Format total duration as minutes and seconds
     * 
     * @return Formatted duration string (MM:SS)
     */
    public String getFormattedDuration() {
        int minutes = totalDuration / 60;
        int seconds = totalDuration % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
} 