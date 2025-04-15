package com.example.aipodcast.service;

import android.util.Log;

import com.example.aipodcast.config.ApiConfig;
import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.PodcastContent;
import com.example.aipodcast.util.OpenAIHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Enhanced OpenAI service for generating podcast content.
 * Provides improved error handling, streaming capabilities, and fallback options.
 */
public class EnhancedOpenAIService {
    private static final String TAG = "EnhancedOpenAIService";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    
    private final String apiKey;
    private final OkHttpClient client;
    private final ExecutorService executor;
    
    // Streaming callback interface
    public interface StreamingCallback {
        void onStart();
        void onContent(String content);
        void onComplete(String fullContent);
        void onError(Throwable error);
        void onProgress(int progress, String progressMessage);
    }
    
    /**
     * Constructor with API key
     * 
     * @param apiKey OpenAI API key
     */
    public EnhancedOpenAIService(String apiKey) {
        this.apiKey = apiKey;
        this.client = OpenAIHelper.buildOkHttpClient();
        this.executor = Executors.newCachedThreadPool();
    }
    
    /**
     * Generate podcast content from news articles
     * 
     * @param articles News articles to discuss
     * @param topics Topics to focus on
     * @param durationMinutes Desired duration in minutes
     * @param podcastTitle Title for the podcast
     * @return CompletableFuture with generated PodcastContent
     */
    public CompletableFuture<PodcastContent> generatePodcastContent(
            Set<NewsArticle> articles,
            List<String> topics,
            int durationMinutes,
            String podcastTitle) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Build the prompt
                String prompt = OpenAIHelper.buildPodcastPrompt(articles, topics, durationMinutes);
                
                // Build the request
                String requestBody = OpenAIHelper.buildRequestBody(prompt, null, 0.7f, false);
                Request request = new Request.Builder()
                        .url(API_URL)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(requestBody, OpenAIHelper.JSON))
                        .build();
                
                // Execute the request with retries
                String jsonResponse = OpenAIHelper.executeWithRetry(client, request);
                
                // Extract content
                String content = OpenAIHelper.extractContentFromResponse(jsonResponse);
                
                // Create PodcastContent from the response
                PodcastContent podcastContent = new PodcastContent(podcastTitle, topics);
                podcastContent.setAIGenerated(true);
                podcastContent.setSourceText(prompt);
                
                // Process content into audio segments
                podcastContent.processAITranscript(content, OpenAIHelper.SPEAKER_MARKER);
                
                return podcastContent;
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating podcast content: " + e.getMessage(), e);
                throw new RuntimeException("Failed to generate podcast content: " + 
                        OpenAIHelper.formatErrorMessage(e), e);
            }
        }, executor);
    }
    
    /**
     * Generate podcast content with streaming updates
     * 
     * @param articles News articles to discuss
     * @param topics Topics to focus on
     * @param durationMinutes Desired duration in minutes
     * @param podcastTitle Title for the podcast
     * @param callback Callback for streaming updates
     * @return CompletableFuture with generated PodcastContent
     */
    public CompletableFuture<PodcastContent> generatePodcastContentStreaming(
            Set<NewsArticle> articles,
            List<String> topics,
            int durationMinutes,
            String podcastTitle,
            StreamingCallback callback) {
        
        CompletableFuture<PodcastContent> future = new CompletableFuture<>();
        
        executor.submit(() -> {
            StringBuilder fullContent = new StringBuilder();
            
            try {
                if (callback != null) {
                    callback.onStart();
                    callback.onProgress(5, "Preparing content generation...");
                }
                
                // Build the prompt
                String prompt = OpenAIHelper.buildPodcastPrompt(articles, topics, durationMinutes);
                
                // Build the request body for streaming
                String requestBody = OpenAIHelper.buildRequestBody(prompt, null, 0.7f, true);
                
                Request request = new Request.Builder()
                        .url(API_URL)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(requestBody, OpenAIHelper.JSON))
                        .build();
                
                if (callback != null) {
                    callback.onProgress(10, "Connecting to AI service...");
                }
                
                // Start streaming request
                try (okhttp3.Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No response body";
                        throw new IOException("API request failed: " + response.code() + " - " + errorBody);
                    }
                    
                    if (response.body() == null) {
                        throw new IOException("Empty response body");
                    }
                    
                    if (callback != null) {
                        callback.onProgress(20, "Generating content...");
                    }
                    
                    // Process streaming response
                    try (okhttp3.ResponseBody responseBody = response.body()) {
                        okio.BufferedSource source = responseBody.source();
                        String line;
                        int progressCounter = 0;
                        int progressPercentBase = 20;
                        
                        while ((line = source.readUtf8Line()) != null) {
                            if (line.isEmpty()) continue;
                            
                            // Process the chunk
                            String content = OpenAIHelper.processStreamingChunk(line);
                            if (content != null) {
                                fullContent.append(content);
                                
                                // Update progress approximately
                                progressCounter++;
                                if (progressCounter % 10 == 0) {
                                    // Calculate estimated progress (20% - 90%)
                                    int estimatedContentLength = durationMinutes * 150; // ~words
                                    int currentLength = fullContent.toString().split("\\s+").length;
                                    int progress = progressPercentBase + 
                                            (int)(Math.min(currentLength, estimatedContentLength) * 70.0 / estimatedContentLength);
                                    
                                    if (callback != null) {
                                        callback.onProgress(progress, "Generating content: " + progress + "%");
                                        callback.onContent(content);
                                    }
                                    
                                    progressPercentBase = progress;
                                } else if (callback != null) {
                                    callback.onContent(content);
                                }
                            }
                        }
                    }
                    
                    // Create PodcastContent with the complete response
                    String finalContent = fullContent.toString();
                    
                    if (callback != null) {
                        callback.onProgress(95, "Processing final content...");
                    }
                    
                    PodcastContent podcastContent = new PodcastContent(podcastTitle, topics);
                    podcastContent.setAIGenerated(true);
                    podcastContent.setSourceText(prompt);
                    
                    // Process into audio segments
                    podcastContent.processAITranscript(finalContent, OpenAIHelper.SPEAKER_MARKER);
                    
                    if (callback != null) {
                        callback.onComplete(finalContent);
                        callback.onProgress(100, "Content generation complete");
                    }
                    
                    future.complete(podcastContent);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error in streaming request: " + e.getMessage(), e);
                    future.completeExceptionally(e);
                    
                    if (callback != null) {
                        callback.onError(e);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating podcast content: " + e.getMessage(), e);
                future.completeExceptionally(e);
                
                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
        
        return future;
    }
    
    /**
     * Try to generate content using a fallback model if primary model fails
     * 
     * @param articles News articles
     * @param topics Topics
     * @param durationMinutes Duration
     * @param podcastTitle Title
     * @return CompletableFuture with PodcastContent
     */
    public CompletableFuture<PodcastContent> generateWithFallback(
            Set<NewsArticle> articles,
            List<String> topics,
            int durationMinutes,
            String podcastTitle) {
        
        return generatePodcastContent(articles, topics, durationMinutes, podcastTitle)
                .exceptionally(error -> {
                    Log.w(TAG, "Primary generation failed, trying fallback model: " + error.getMessage());
                    
                    // Try with GPT-3.5 as a fallback
                    try {
                        // Build the prompt - simplified for the smaller model
                        String prompt = buildSimplifiedPrompt(articles, topics, durationMinutes);
                        
                        // Build the request with GPT-3.5
                        String requestBody = OpenAIHelper.buildRequestBody(
                                prompt, "gpt-3.5-turbo", 0.7f, false);
                        
                        Request request = new Request.Builder()
                                .url(API_URL)
                                .addHeader("Authorization", "Bearer " + apiKey)
                                .addHeader("Content-Type", "application/json")
                                .post(RequestBody.create(requestBody, OpenAIHelper.JSON))
                                .build();
                        
                        // Execute with retry
                        String jsonResponse = OpenAIHelper.executeWithRetry(client, request);
                        String content = OpenAIHelper.extractContentFromResponse(jsonResponse);
                        
                        // Create podcast content
                        PodcastContent podcastContent = new PodcastContent(podcastTitle, topics);
                        podcastContent.setAIGenerated(true);
                        podcastContent.setSourceText(prompt);
                        
                        // Process the content - might not have proper markers, so add them
                        content = addMarkersIfNeeded(content);
                        podcastContent.processAITranscript(content, OpenAIHelper.SPEAKER_MARKER);
                        
                        return podcastContent;
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Fallback generation also failed: " + e.getMessage(), e);
                        throw new RuntimeException("All generation attempts failed: " + 
                                OpenAIHelper.formatErrorMessage(e), e);
                    }
                });
    }
    
    /**
     * Build a simplified prompt for fallback model
     * 
     * @param articles News articles
     * @param topics Topics
     * @param durationMinutes Duration
     * @return Simplified prompt
     */
    private String buildSimplifiedPrompt(Set<NewsArticle> articles, List<String> topics, int durationMinutes) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Generate a podcast script about news. ");
        
        // Add topics
        if (topics != null && !topics.isEmpty()) {
            prompt.append("Topics: ").append(String.join(", ", topics)).append(". ");
        }
        
        // Add articles (limited number for shorter context)
        prompt.append("Discuss these articles:\n\n");
        
        List<NewsArticle> articlesList = new ArrayList<>(articles);
        int maxArticles = Math.min(articlesList.size(), 3); // Limit to 3 for fallback
        
        for (int i = 0; i < maxArticles; i++) {
            NewsArticle article = articlesList.get(i);
            prompt.append("Title: ").append(article.getTitle()).append("\n");
            prompt.append("Abstract: ").append(article.getAbstract()).append("\n\n");
        }
        
        // Format instructions
        prompt.append("Format your response as monologue paragraphs.\n");
        prompt.append("Start each paragraph with '").append(OpenAIHelper.SPEAKER_MARKER).append("'\n");
        prompt.append("Keep it concise and informative.\n");
        
        return prompt.toString();
    }
    
    /**
     * Add speaker markers to content if they're missing
     * 
     * @param content Content to process
     * @return Content with markers
     */
    private String addMarkersIfNeeded(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        // Check if the content already has markers
        if (content.contains(OpenAIHelper.SPEAKER_MARKER)) {
            return content;
        }
        
        // Split into paragraphs and add markers
        String[] paragraphs = content.split("\\n\\s*\\n");
        StringBuilder result = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (!paragraph.isEmpty()) {
                result.append(OpenAIHelper.SPEAKER_MARKER)
                      .append(" ")
                      .append(paragraph)
                      .append("\n\n");
            }
        }
        
        return result.toString();
    }
    
    /**
     * Shutdown the service
     */
    public void shutdown() {
        executor.shutdown();
    }
} 