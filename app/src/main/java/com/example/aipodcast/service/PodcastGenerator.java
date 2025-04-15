package com.example.aipodcast.service;
import android.util.Log;
import com.example.aipodcast.config.ApiConfig;
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
public class PodcastGenerator {
    private static final String TAG = "PodcastGenerator";
    private Set<NewsArticle> selectedArticles;
    private int targetDuration; 
    private List<String> topics;
    private boolean useAI; 
    private OpenAIService openAIService;
    public PodcastGenerator(Set<NewsArticle> articles, int duration, List<String> topics) {
        this.selectedArticles = articles;
        this.targetDuration = duration;
        this.topics = topics;
        this.useAI = !ApiConfig.OPENAI_API_KEY.equals("your_openai_api_key_here");
        if (this.useAI) {
            this.openAIService = new OpenAIService(ApiConfig.OPENAI_API_KEY);
        }
    }
    public void setUseAI(boolean useAI) {
        this.useAI = useAI && !ApiConfig.OPENAI_API_KEY.equals("your_openai_api_key_here");
    }
    public CompletableFuture<PodcastContent> generateContentAsync() {
        if (useAI && openAIService != null) {
            String title = createPodcastTitle();
            Log.d(TAG, "Generating AI podcast with target duration: " + targetDuration + " minutes");
            return openAIService.generatePodcastContent(selectedArticles, topics, targetDuration, title);
        } else {
            Log.d(TAG, "Generating template podcast with target duration: " + targetDuration + " minutes");
            return CompletableFuture.supplyAsync(this::generateContent);
        }
    }
    public PodcastContent generateContent() {
        try {
            String title = createPodcastTitle();
            PodcastContent content = new PodcastContent(title, topics);
            List<NewsArticle> sortedArticles = sortArticles(new ArrayList<>(selectedArticles));
            content.addSegment(createIntroduction());
            for (int i = 0; i < sortedArticles.size(); i++) {
                NewsArticle article = sortedArticles.get(i);
                content.addSegment(createNewsSegment(article, i + 1, sortedArticles.size()));
                if (i < sortedArticles.size() - 1) {
                    content.addSegment(createTransition(i, sortedArticles.size()));
                }
            }
            content.addSegment(createConclusion());
            adjustContentToTargetDuration(content);
            return content;
        } catch (Exception e) {
            Log.e(TAG, "Error generating podcast content: " + e.getMessage());
            throw new RuntimeException("Failed to generate podcast content", e);
        }
    }
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
    private List<NewsArticle> sortArticles(List<NewsArticle> articles) {
        Collections.sort(articles, (a1, a2) -> {
            if (a1.getPublishedDate() == null || a2.getPublishedDate() == null) {
                return 0;
            }
            return a2.getPublishedDate().compareTo(a1.getPublishedDate());
        });
        return articles;
    }
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
    private PodcastSegment createNewsSegment(NewsArticle article, int index, int total) {
        StringBuilder newsText = new StringBuilder();
        String sectionInfo = article.getSection() != null && !article.getSection().equals("Unknown") 
                ? " in " + article.getSection() 
                : "";
        newsText.append("Story ").append(index).append(" of ").append(total);
        newsText.append(sectionInfo).append(": ");
        newsText.append(article.getTitle()).append(". ");
        newsText.append(article.getAbstract());
        return new PodcastSegment(
            article.getTitle(),
            newsText.toString(),
            article,
            SegmentType.NEWS_ARTICLE
        );
    }
    private PodcastSegment createTransition(int index, int total) {
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
    private PodcastSegment createConclusion() {
        StringBuilder conclusion = new StringBuilder();
        conclusion.append("That's all for your personalized news update. ");
        conclusion.append("This podcast was generated on ");
        conclusion.append(java.time.LocalDate.now().toString());
        conclusion.append(". ");
        conclusion.append("Thanks for listening, and we'll see you next time for more curated news.");
        return new PodcastSegment("Conclusion", conclusion.toString(), SegmentType.CONCLUSION);
    }
    private void adjustContentToTargetDuration(PodcastContent content) {
        int currentDuration = content.getTotalDuration();
        int targetDurationSecs = targetDuration * 60;
        int difference = Math.abs(currentDuration - targetDurationSecs);
        float percentDiff = (float) difference / targetDurationSecs * 100;
        Log.d(TAG, String.format(
            "Podcast duration: %d seconds (target: %d). Difference: %.1f%%",
            currentDuration, targetDurationSecs, percentDiff
        ));
    }
} 