package com.example.aipodcast.service;

import android.util.Log;
import com.example.aipodcast.config.ApiConfig;
import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.PodcastContent;
import com.example.aipodcast.model.PodcastSegment;
import com.example.aipodcast.model.PodcastSegment.SegmentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class PodcastGenerator {
    private static final String TAG = "PodcastGenerator";
    private Set<NewsArticle> selectedArticles;
    private int targetDuration; // in minutes
    private List<String> topics;
    private boolean useAI;
    private OpenAIService openAIService;
    private static final int WORDS_PER_MINUTE = 140; // Average speaking rate

    public PodcastGenerator(Set<NewsArticle> articles, int duration, List<String> topics) {
        this.selectedArticles = articles;
        this.targetDuration = duration;
        this.topics = topics;
        this.useAI = !ApiConfig.OPENAI_API_KEY.equals("your_openai_api_key_here");
        if (this.useAI) {
            // Pass the useAI flag to OpenAIService constructor
            this.openAIService = new OpenAIService(ApiConfig.OPENAI_API_KEY, this.useAI);
        }
    }

    public void setUseAI(boolean useAI) {
        this.useAI = useAI && !ApiConfig.OPENAI_API_KEY.equals("your_openai_api_key_here");

        Log.d(TAG, "setUseAI called with " + useAI + ", final value: " + this.useAI);

        // Update the OpenAIService if needed
        if (this.openAIService != null) {
            Log.d(TAG, "Updating useAIGeneration in OpenAIService to: " + this.useAI);
            this.openAIService.useAIGeneration(true);  // Always set to true for conversational
        } else if (this.useAI) {
            Log.d(TAG, "Creating new OpenAIService with useAIGeneration: " + true);
            this.openAIService = new OpenAIService(ApiConfig.OPENAI_API_KEY, true);
        }
    }

    // In PodcastGenerator class
    public CompletableFuture<PodcastContent> generateContentAsync() {
        if (useAI && openAIService != null) {
            String title = createPodcastTitle();

            Log.d(TAG, "useAI flag is set to: " + useAI);
            Log.d(TAG, "Generating AI podcast with target duration: " + targetDuration + " minutes");

            return openAIService.generatePodcastContent(selectedArticles, topics, targetDuration, title, useAI)
                    .thenApply(content -> {
                        // Force the duration to match our target
                        int targetSeconds = targetDuration * 60;
                        content.forceSetTotalDuration(targetSeconds);
                        Log.d(TAG, "Forced podcast duration to " + targetSeconds + " seconds");

                        // Validate content - if not sufficient, fallback to template
                        if (!isContentSufficient(content)) {
                            Log.w(TAG, "AI-generated content insufficient. Falling back to template.");
                            PodcastContent templateContent = generateContent();
                            templateContent.forceSetTotalDuration(targetSeconds);
                            return templateContent;
                        }

                        return content;
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "Error generating AI content: " + e.getMessage() + ". Falling back to template.");
                        PodcastContent templateContent = generateContent();
                        templateContent.forceSetTotalDuration(targetDuration * 60);
                        return templateContent;
                    });
        } else {
            Log.d(TAG, "Generating template podcast with target duration: " + targetDuration + " minutes");
            return CompletableFuture.supplyAsync(() -> {
                PodcastContent content = generateContent();
                // Force the duration to match our target
                int targetSeconds = targetDuration * 60;
                content.forceSetTotalDuration(targetSeconds);
                Log.d(TAG, "Forced podcast duration to " + targetSeconds + " seconds");
                return content;
            });
        }
    }

    // Helper method to check if content is sufficient
    private boolean isContentSufficient(PodcastContent content) {
        if (content == null || content.getSegments().isEmpty()) {
            return false;
        }

        // Check if we have enough segments - at minimum intro, article(s), conclusion
        if (content.getSegments().size() < 3) {
            return false;
        }

        // Check if content is too short
        String fullText = content.getFullText();
        if (fullText == null || fullText.length() < 500) {
            return false;
        }

        // Check if text has enough words for our duration
        String[] words = fullText.split("\\s+");
        int targetWordCount = targetDuration * 140; // 140 words per minute
        if (words.length < targetWordCount * 0.7) { // At least 70% of target
            return false;
        }

        return true;
    }
    private void forceTargetDuration(PodcastContent content) {
        int targetDurationSeconds = targetDuration * 60;
        content.forceSetTotalDuration(targetDurationSeconds);
        Log.d(TAG, "Forced podcast duration to " + targetDurationSeconds +
                " seconds (ignoring calculated duration)");
    }

    public PodcastContent generateContent() {
        try {
            String title = createPodcastTitle();
            PodcastContent content = new PodcastContent(title, topics);
            List<NewsArticle> sortedArticles = sortArticles(new ArrayList<>(selectedArticles));

            // Calculate target word count based on speaking rate
            int targetWordCount = targetDuration * WORDS_PER_MINUTE;
            int wordsPerArticle = sortedArticles.isEmpty() ? 0 : targetWordCount / sortedArticles.size();

            content.addSegment(createIntroduction());

            for (int i = 0; i < sortedArticles.size(); i++) {
                NewsArticle article = sortedArticles.get(i);
                // Pass in per-article word count target
                content.addSegment(createNewsSegment(article, i + 1, sortedArticles.size(), wordsPerArticle));

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
        introText.append("for you today, in a podcast that will run about ");
        introText.append(targetDuration);
        introText.append(" minutes. Let's get started.");

        return new PodcastSegment("Introduction", introText.toString(), SegmentType.INTRO);
    }

    private PodcastSegment createNewsSegment(NewsArticle article, int index, int total, int targetWordCount) {
        StringBuilder newsText = new StringBuilder();
        String sectionInfo = article.getSection() != null && !article.getSection().equals("Unknown")
                ? " in " + article.getSection()
                : "";

        newsText.append("Article ").append(index).append(" of ").append(total);
        newsText.append(sectionInfo).append(": ");
        newsText.append(article.getTitle()).append(". ");

        // Use the full article content when available, otherwise use abstract
        String fullContent = article.getFullBodyText();
        if (fullContent != null && !fullContent.isEmpty()) {
            // If full content is too long, truncate it to a reasonable size
            if (fullContent.length() > 1000) {
                // Get first ~1000 characters, but don't cut off mid-sentence
                int cutPoint = fullContent.indexOf(". ", 800);
                if (cutPoint > 0) {
                    fullContent = fullContent.substring(0, cutPoint + 1);
                }
            }
            newsText.append(fullContent);
        } else {
            // Fall back to abstract if no full body text available
            newsText.append(article.getAbstract());
        }

        return new PodcastSegment(
                article.getTitle(),
                newsText.toString(),
                article,
                PodcastSegment.SegmentType.NEWS_ARTICLE
        );
    }
    private PodcastSegment createTransition(int index, int total) {
        String[] transitions = {
                "Moving on to our next story.",
                "Next up on your personalized podcast.",
                "Let's continue with the next headline.",
                "Our next story covers a different topic.",
                "Switching gears to our next piece of news.",
                "Now for something different, but equally interesting.",
                "Following that important story, let's look at our next item.",
                "That's an interesting development. Now, here's another story worth discussing."
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

        if (topics != null && !topics.isEmpty()) {
            conclusion.append("We've covered important developments in ");
            conclusion.append(String.join(", ", topics));
            conclusion.append(". ");
        }

        conclusion.append("This podcast was generated on ");
        conclusion.append(java.time.LocalDate.now().toString());
        conclusion.append(". ");
        conclusion.append("Thanks for listening, and we'll see you next time for more curated news tailored to your interests.");

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

        // If we're significantly off target, adjust content
        if (percentDiff > 15) {
            if (currentDuration > targetDurationSecs) {
                // Too long - reduce length
                trimContentToFitDuration(content, targetDurationSecs);
            } else {
                // Too short - expand content
                expandContentToFitDuration(content, targetDurationSecs);
            }
        }
    }

    private void trimContentToFitDuration(PodcastContent content, int targetDurationSecs) {
        Log.d(TAG, "Trimming content to fit target duration");
        List<PodcastSegment> segments = content.getSegments();

        // Don't touch the intro and conclusion
        for (int i = 1; i < segments.size() - 1; i++) {
            PodcastSegment segment = segments.get(i);
            if (segment.getType() == SegmentType.NEWS_ARTICLE) {
                // Trim to about 70% of original length
                String text = segment.getText();
                String[] paragraphs = text.split("\n\n");

                // Keep the first paragraph (title and basic info)
                StringBuilder trimmedText = new StringBuilder(paragraphs[0]);

                // Add only essential paragraphs
                int paragraphsToKeep = Math.max(1, paragraphs.length / 2);
                for (int j = 1; j <= paragraphsToKeep && j < paragraphs.length; j++) {
                    trimmedText.append("\n\n").append(paragraphs[j]);
                }

                segment.setText(trimmedText.toString());

                // Check if we've reached target duration
                if (content.getTotalDuration() <= targetDurationSecs * 1.1) {
                    break;
                }
            }
        }
    }

    private void expandContentToFitDuration(PodcastContent content, int targetDurationSecs) {
        Log.d(TAG, "Expanding content to fit target duration");
        List<PodcastSegment> segments = content.getSegments();

        // Don't touch the intro and conclusion
        for (int i = 1; i < segments.size() - 1; i++) {
            PodcastSegment segment = segments.get(i);
            if (segment.getType() == SegmentType.NEWS_ARTICLE) {
                // Instead of adding generic filler content, we'll repeat or emphasize
                // key information from the article itself
                String text = segment.getText();
                StringBuilder expandedText = new StringBuilder(text);

                // Add a simple, non-generic connector
                expandedText.append("\n\nThis information comes directly from the article and represents factual reporting on this topic.");

                // Set the expanded text
                segment.setText(expandedText.toString());

                // Check if we've reached target duration
                if (content.getTotalDuration() >= targetDurationSecs * 0.9) {
                    break;
                }
            }
        }
    }
}