package com.example.aipodcast.model;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
public class PodcastContent implements Serializable {
    private String id;
    private String title;
    private List<String> topics;
    private List<PodcastSegment> segments;
    private List<AudioSegment> audioSegments;
    private Date creationDate;
    private int totalDuration; 
    private boolean isAIGenerated;
    private String sourceText;
    private String transcriptText;
    public PodcastContent() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.topics = new ArrayList<>();
        this.segments = new ArrayList<>();
        this.audioSegments = new ArrayList<>();
        this.creationDate = new Date();
        this.isAIGenerated = true;
    }
    public PodcastContent(String title, List<String> topics) {
        this();
        this.title = title;
        this.topics = topics;
    }
    public PodcastContent(String title, List<String> topics, int durationInSeconds) {
        this();
        this.title = title;
        this.topics = topics;
        this.totalDuration = durationInSeconds;
    }
    public PodcastContent(String id, String title, List<String> topics, boolean isAIGenerated) {
        this();
        this.id = id;
        this.title = title;
        this.topics = topics;
        this.isAIGenerated = isAIGenerated;
    }
    public PodcastContent addSegment(PodcastSegment segment) {
        this.segments.add(segment);
        return this;
    }
    public void forceSetTotalDuration(int durationInSeconds) {
        this.totalDuration = durationInSeconds;
    }
    public PodcastContent addAudioSegment(AudioSegment audioSegment) {
        this.audioSegments.add(audioSegment);
        if (!audioSegments.isEmpty()) {
            AudioSegment lastSegment = audioSegments.get(audioSegments.size() - 1);
            totalDuration = (int) (lastSegment.getEndTimeMs() / 1000);
        }
        return this;
    }
    public PodcastContent convertSegmentsToAudio() {
        if (!audioSegments.isEmpty() || segments.isEmpty()) {
            return this;
        }
        long currentTimeMs = 0;
        for (PodcastSegment segment : segments) {
            AudioSegment.SegmentType audioType;
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
            AudioSegment audioSegment = new AudioSegment(
                String.valueOf(System.currentTimeMillis()) + "_" + audioSegments.size(),
                segment.getText(),
                null, 
                currentTimeMs,
                segment.getEstimatedDuration() * 1000L, 
                audioType
            );
            audioSegments.add(audioSegment);
            currentTimeMs += audioSegment.getDurationMs();
        }
        return this;
    }
    public PodcastContent processAITranscript(String transcript, String speakerMarker) {
        if (transcript == null || transcript.isEmpty()) {
            return this;
        }
        this.transcriptText = transcript;
        audioSegments.clear();
        String[] parts = transcript.split(speakerMarker);
        long currentTimeMs = 0;
        String speaker = "HOST"; 
        int startIdx = parts[0].trim().isEmpty() ? 1 : 0;
        for (int i = startIdx; i < parts.length; i++) {
            String text = parts[i].trim();
            if (text.isEmpty()) continue;
            AudioSegment audioSegment = new AudioSegment(
                String.valueOf(System.currentTimeMillis()) + "_" + i,
                text,
                speaker,
                currentTimeMs,
                0, 
                AudioSegment.SegmentType.CONTENT
            );
            long durationMs = (long) (audioSegment.getWordCount() / 2.5 * 1000);
            audioSegment.setDurationMs(durationMs);
            if (i == startIdx) {
                audioSegment.setType(AudioSegment.SegmentType.INTRO);
            } else if (i == parts.length - 1) {
                audioSegment.setType(AudioSegment.SegmentType.CONCLUSION);
            }
            audioSegments.add(audioSegment);
            currentTimeMs += durationMs;
        }
        if (!audioSegments.isEmpty()) {
            AudioSegment lastSegment = audioSegments.get(audioSegments.size() - 1);
            totalDuration = (int) (lastSegment.getEndTimeMs() / 1000);
        }
        return this;
    }
    public String getFullText() {
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
        StringBuilder builder = new StringBuilder();
        for (PodcastSegment segment : segments) {
            builder.append(segment.getText()).append("\n\n");
        }
        return builder.toString();
    }
    public AudioSegment findAudioSegmentAtTime(long currentTimeMs) {
        for (AudioSegment segment : audioSegments) {
            if (segment.containsTime(currentTimeMs)) {
                return segment;
            }
        }
        return null;
    }
    public void updateAudioSegmentsStatus(long currentTimeMs) {
        for (AudioSegment segment : audioSegments) {
            segment.updatePlaybackStatus(currentTimeMs);
        }
    }
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
    public String getFormattedDuration() {
        int minutes = totalDuration / 60;
        int seconds = totalDuration % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
} 