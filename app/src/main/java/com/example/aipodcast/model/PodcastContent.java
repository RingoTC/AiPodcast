package com.example.aipodcast.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Enhanced model representing the complete content of a podcast.
 * Contains all segments, audio data, and metadata for a podcast.
 */
public class PodcastContent implements Serializable {
    private String id;
    private String title;
    private List<String> topics;
    private List<PodcastSegment> segments;
    private List<AudioSegment> audioSegments;
    private Date creationDate;
    private int totalDuration; // estimated duration in seconds
    private boolean isAIGenerated;
    private String sourceText;
    private String transcriptText;
    
    /**
     * Default constructor initializing empty lists
     */
    public PodcastContent() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.topics = new ArrayList<>();
        this.segments = new ArrayList<>();
        this.audioSegments = new ArrayList<>();
        this.creationDate = new Date();
        this.isAIGenerated = true;
    }

    /**
     * Parameterized constructor
     * 
     * @param title Podcast title
     * @param topics List of topics covered
     */
    public PodcastContent(String title, List<String> topics) {
        this();
        this.title = title;
        this.topics = topics;
    }
    
    /**
     * Full constructor
     * 
     * @param id Unique identifier
     * @param title Podcast title
     * @param topics List of topics covered
     * @param isAIGenerated Whether content was AI generated
     */
    public PodcastContent(String id, String title, List<String> topics, boolean isAIGenerated) {
        this();
        this.id = id;
        this.title = title;
        this.topics = topics;
        this.isAIGenerated = isAIGenerated;
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
     * Add an audio segment to the podcast content
     * 
     * @param audioSegment The audio segment to add
     * @return This PodcastContent instance for chaining
     */
    public PodcastContent addAudioSegment(AudioSegment audioSegment) {
        this.audioSegments.add(audioSegment);
        // Update total duration based on end time of last segment
        if (!audioSegments.isEmpty()) {
            AudioSegment lastSegment = audioSegments.get(audioSegments.size() - 1);
            totalDuration = (int) (lastSegment.getEndTimeMs() / 1000);
        }
        return this;
    }
    
    /**
     * Convert traditional segments to audio segments
     * 
     * @return This PodcastContent instance for chaining
     */
    public PodcastContent convertSegmentsToAudio() {
        if (!audioSegments.isEmpty() || segments.isEmpty()) {
            return this;
        }
        
        long currentTimeMs = 0;
        
        for (PodcastSegment segment : segments) {
            AudioSegment.SegmentType audioType;
            
            // Map PodcastSegment type to AudioSegment type
            switch (segment.getType()) {
                case INTRO:
                    audioType = AudioSegment.SegmentType.INTRO;
                    break;
                case CONCLUSION:
                    audioType = AudioSegment.SegmentType.CONCLUSION;
                    break;
                case TRANSITION:
                    audioType = AudioSegment.SegmentType.TRANSITION;
                    break;
                default:
                    audioType = AudioSegment.SegmentType.CONTENT;
            }
            
            // Create new audio segment
            AudioSegment audioSegment = new AudioSegment(
                String.valueOf(System.currentTimeMillis()) + "_" + audioSegments.size(),
                segment.getText(),
                null, // No speaker tag
                currentTimeMs,
                segment.getEstimatedDuration() * 1000L, // Convert to ms
                audioType
            );
            
            // Add the segment
            audioSegments.add(audioSegment);
            
            // Update current time for next segment
            currentTimeMs += audioSegment.getDurationMs();
        }
        
        return this;
    }
    
    /**
     * Process AI generated transcript into audio segments
     * 
     * @param transcript Full transcript text
     * @param speakerMarker Marker for speaker changes (e.g., "§HOST§")
     * @return This PodcastContent instance for chaining
     */
    public PodcastContent processAITranscript(String transcript, String speakerMarker) {
        if (transcript == null || transcript.isEmpty()) {
            return this;
        }
        
        // Store the full transcript
        this.transcriptText = transcript;
        
        // Clear existing audio segments
        audioSegments.clear();
        
        // Split by speaker markers
        String[] parts = transcript.split(speakerMarker);
        
        long currentTimeMs = 0;
        String speaker = "HOST"; // Default speaker
        
        // Skip the first part if it's empty
        int startIdx = parts[0].trim().isEmpty() ? 1 : 0;
        
        for (int i = startIdx; i < parts.length; i++) {
            String text = parts[i].trim();
            if (text.isEmpty()) continue;
            
            // Create audio segment
            AudioSegment audioSegment = new AudioSegment(
                String.valueOf(System.currentTimeMillis()) + "_" + i,
                text,
                speaker,
                currentTimeMs,
                0, // Duration to be calculated
                AudioSegment.SegmentType.CONTENT
            );
            
            // Calculate duration based on word count
            long durationMs = (long) (audioSegment.getWordCount() / 2.5 * 1000);
            audioSegment.setDurationMs(durationMs);
            
            // Adjust segment type for first and last segments
            if (i == startIdx) {
                audioSegment.setType(AudioSegment.SegmentType.INTRO);
            } else if (i == parts.length - 1) {
                audioSegment.setType(AudioSegment.SegmentType.CONCLUSION);
            }
            
            // Add the segment
            audioSegments.add(audioSegment);
            
            // Update current time for next segment
            currentTimeMs += durationMs;
        }
        
        // Update total duration
        if (!audioSegments.isEmpty()) {
            AudioSegment lastSegment = audioSegments.get(audioSegments.size() - 1);
            totalDuration = (int) (lastSegment.getEndTimeMs() / 1000);
        }
        
        return this;
    }

    /**
     * Get the complete text content of the podcast
     * 
     * @return Concatenated text of all segments
     */
    public String getFullText() {
        // If we have audio segments, use those for the full text
        if (!audioSegments.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (AudioSegment segment : audioSegments) {
                if (segment.getSpeakerTag() != null && !segment.getSpeakerTag().isEmpty()) {
                    builder.append(segment.getSpeakerTag()).append(": ");
                }
                builder.append(segment.getText()).append("\n\n");
            }
            return builder.toString();
        }
        
        // Otherwise, use traditional segments
        StringBuilder builder = new StringBuilder();
        for (PodcastSegment segment : segments) {
            builder.append(segment.getText()).append("\n\n");
        }
        return builder.toString();
    }
    
    /**
     * Find the audio segment at a specific time
     * 
     * @param currentTimeMs Current playback time in milliseconds
     * @return The audio segment containing this time, or null if none found
     */
    public AudioSegment findAudioSegmentAtTime(long currentTimeMs) {
        for (AudioSegment segment : audioSegments) {
            if (segment.containsTime(currentTimeMs)) {
                return segment;
            }
        }
        return null;
    }
    
    /**
     * Update playback status of all audio segments
     * 
     * @param currentTimeMs Current playback time
     */
    public void updateAudioSegmentsStatus(long currentTimeMs) {
        for (AudioSegment segment : audioSegments) {
            segment.updatePlaybackStatus(currentTimeMs);
        }
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
    
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
    
    public List<AudioSegment> getAudioSegments() {
        return audioSegments;
    }
    
    public void setAudioSegments(List<AudioSegment> audioSegments) {
        this.audioSegments = audioSegments;
        
        // Update total duration
        if (!audioSegments.isEmpty()) {
            AudioSegment lastSegment = audioSegments.get(audioSegments.size() - 1);
            totalDuration = (int) (lastSegment.getEndTimeMs() / 1000);
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
    
    public boolean isAIGenerated() {
        return isAIGenerated;
    }
    
    public void setAIGenerated(boolean AIGenerated) {
        isAIGenerated = AIGenerated;
    }
    
    public String getSourceText() {
        return sourceText;
    }
    
    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }
    
    public String getTranscriptText() {
        return transcriptText;
    }
    
    public void setTranscriptText(String transcriptText) {
        this.transcriptText = transcriptText;
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