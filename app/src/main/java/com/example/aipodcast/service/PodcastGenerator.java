package com.example.aipodcast.service;

import android.util.Log;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.PodcastContent;
import com.example.aipodcast.model.PodcastSegment;
import com.example.aipodcast.model.PodcastSegment.SegmentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for generating structured podcast content from news articles.
 * Creates a well-formatted podcast with intro, news segments, transitions, and conclusion.
 */
public class PodcastGenerator {
    private static final String TAG = "PodcastGenerator";
    
    private Set<NewsArticle> selectedArticles;
    private int targetDuration; // target duration in minutes
    private List<String> topics;
    
    /**
     * Constructor for PodcastGenerator
     * 
     * @param articles The set of news articles to include in the podcast
     * @param duration Target duration in minutes
     * @param topics List of topics or categories
     */
    public PodcastGenerator(Set<NewsArticle> articles, int duration, List<String> topics) {
        this.selectedArticles = articles;
        this.targetDuration = duration;
        this.topics = topics;
    }
    
    /**
     * Generate a complete podcast content asynchronously
     * 
     * @return CompletableFuture that will complete with the generated podcast content
     */
    public CompletableFuture<PodcastContent> generateContentAsync() {
        return CompletableFuture.supplyAsync(this::generateContent);
    }
    
    /**
     * Generate a complete podcast content synchronously
     * 
     * @return The generated podcast content
     */
    public PodcastContent generateContent() {
        try {
            // Create a new podcast content with the default title
            String title = createPodcastTitle();
            
            PodcastContent content = new PodcastContent(title, topics);
            
            // Step 1: Sort articles by relevance/importance
            List<NewsArticle> sortedArticles = sortArticles(new ArrayList<>(selectedArticles));
            
            // Step 2: Add introduction
            content.addSegment(createIntroduction());
            
            // Step 3: Add news segments with transitions
            for (int i = 0; i < sortedArticles.size(); i++) {
                NewsArticle article = sortedArticles.get(i);
                
                // Add the news segment
                content.addSegment(createNewsSegment(article, i + 1, sortedArticles.size()));
                
                // Add transition between articles (except after the last one)
                if (i < sortedArticles.size() - 1) {
                    content.addSegment(createTransition(i, sortedArticles.size()));
                }
            }
            
            // Step 4: Add conclusion
            content.addSegment(createConclusion());
            
            // Verify the content reaches the target duration
            adjustContentToTargetDuration(content);
            
            return content;
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating podcast content: " + e.getMessage());
            throw new RuntimeException("Failed to generate podcast content", e);
        }
    }
    
    /**
     * Create a dynamic title for the podcast based on topics
     * 
     * @return A descriptive title
     */
    private String createPodcastTitle() {
        if (topics == null || topics.isEmpty()) {
            return "Your Custom News Podcast";
        }
        
        if (topics.size() == 1) {
            return "Your " + topics.get(0) + " News Update";
        } else {
            StringBuilder builder = new StringBuilder("News Update: ");
            for (int i = 0; i < topics.size(); i++) {
                if (i > 0) {
                    if (i == topics.size() - 1) {
                        builder.append(" and ");
                    } else {
                        builder.append(", ");
                    }
                }
                builder.append(topics.get(i));
            }
            return builder.toString();
        }
    }
    
    /**
     * Sort articles by relevance (for now, by publishedDate)
     * 
     * @param articles List of articles to sort
     * @return Sorted list of articles
     */
    private List<NewsArticle> sortArticles(List<NewsArticle> articles) {
        // Simple sort by published date (newest first)
        Collections.sort(articles, (a1, a2) -> {
            // If dates are missing or malformed, don't change order
            if (a1.getPublishedDate() == null || a2.getPublishedDate() == null) {
                return 0;
            }
            // Reverse comparison for newest first
            return a2.getPublishedDate().compareTo(a1.getPublishedDate());
        });
        return articles;
    }
    
    /**
     * Create the podcast introduction segment
     * 
     * @return Introduction segment
     */
    private PodcastSegment createIntroduction() {
        StringBuilder introText = new StringBuilder();
        
        introText.append("Welcome to your personalized news podcast. ");
        
        if (topics != null && !topics.isEmpty()) {
            introText.append("Today, we're covering ");
            for (int i = 0; i < topics.size(); i++) {
                if (i > 0) {
                    if (i == topics.size() - 1) {
                        introText.append(" and ");
                    } else {
                        introText.append(", ");
                    }
                }
                introText.append(topics.get(i));
            }
            introText.append(". ");
        }
        
        introText.append("We've got ");
        introText.append(selectedArticles.size());
        introText.append(selectedArticles.size() == 1 ? " story " : " stories ");
        introText.append("for you today, so let's get started.");
        
        return new PodcastSegment("Introduction", introText.toString(), SegmentType.INTRO);
    }
    
    /**
     * Create a news segment from an article
     * 
     * @param article The news article
     * @param index The position of this article
     * @param total Total number of articles
     * @return News segment
     */
    private PodcastSegment createNewsSegment(NewsArticle article, int index, int total) {
        StringBuilder newsText = new StringBuilder();
        
        // Create header
        String sectionInfo = article.getSection() != null && !article.getSection().equals("Unknown") 
                ? " in " + article.getSection() 
                : "";
                
        newsText.append("Story ").append(index).append(" of ").append(total);
        newsText.append(sectionInfo).append(": ");
        newsText.append(article.getTitle()).append(". ");
        
        // Add article content
        newsText.append(article.getAbstract());
        
        return new PodcastSegment(
            article.getTitle(),
            newsText.toString(),
            article,
            SegmentType.NEWS_ARTICLE
        );
    }
    
    /**
     * Create a transition between news segments
     * 
     * @param index Current position
     * @param total Total number of articles
     * @return Transition segment
     */
    private PodcastSegment createTransition(int index, int total) {
        // Create a variety of transitions to make the podcast sound more natural
        String[] transitions = {
            "Moving on to our next story.",
            "Next up on your personalized podcast.",
            "Let's continue with the next headline.",
            "Our next story covers a different topic.",
            "Switching gears to our next piece of news."
        };
        
        int choice = index % transitions.length;
        return new PodcastSegment(
            "Transition", 
            transitions[choice], 
            SegmentType.TRANSITION
        );
    }
    
    /**
     * Create the podcast conclusion
     * 
     * @return Conclusion segment
     */
    private PodcastSegment createConclusion() {
        StringBuilder conclusion = new StringBuilder();
        
        conclusion.append("That's all for your personalized news update. ");
        
        // Add a timestamp
        conclusion.append("This podcast was generated on ");
        conclusion.append(java.time.LocalDate.now().toString());
        conclusion.append(". ");
        
        // Add sign-off
        conclusion.append("Thanks for listening, and we'll see you next time for more curated news.");
        
        return new PodcastSegment("Conclusion", conclusion.toString(), SegmentType.CONCLUSION);
    }
    
    /**
     * Adjust content to meet target duration
     * Currently just logs a warning if not matching target
     * 
     * @param content The podcast content to adjust
     */
    private void adjustContentToTargetDuration(PodcastContent content) {
        int currentDuration = content.getTotalDuration();
        int targetDurationSecs = targetDuration * 60;
        
        // Log difference from target
        int difference = Math.abs(currentDuration - targetDurationSecs);
        float percentDiff = (float) difference / targetDurationSecs * 100;
        
        Log.d(TAG, String.format(
            "Podcast duration: %d seconds (target: %d). Difference: %.1f%%",
            currentDuration, targetDurationSecs, percentDiff
        ));
        
        // Future enhancement: Adjust content length to better match target duration
        // For example, add more details to articles if too short, or summarize if too long
    }
} 