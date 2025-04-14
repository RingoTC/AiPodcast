package com.example.aipodcast.service;

import android.content.Context;
import android.util.Log;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.PodcastContent;
import com.example.aipodcast.model.PodcastSegment;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Service for generating podcast content using OpenAI's GPT-4o model.
 * Creates conversational podcast content based on news articles and topic preferences.
 */
public class OpenAIService {
    private static final String TAG = "OpenAIService";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private final String apiKey;
    private final OkHttpClient client;
    private final Gson gson;
    
    /**
     * Constructor with API key
     * 
     * @param apiKey Your OpenAI API key
     */
    public OpenAIService(String apiKey) {
        this.apiKey = apiKey;
        
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        
        this.client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        
        this.gson = new GsonBuilder().create();
    }
    
    /**
     * Generate conversational podcast content based on news articles
     * 
     * @param articles Set of news articles to discuss
     * @param topics List of preferred topics
     * @param durationMinutes Target duration in minutes
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
                // Build the prompt for GPT-4o
                String prompt = buildConversationalPrompt(articles, topics, durationMinutes);
                
                // Make API request
                String jsonResponse = sendChatCompletionRequest(prompt);
                
                // Parse and convert response to PodcastContent
                return convertResponseToPodcastContent(jsonResponse, topics, podcastTitle);
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating podcast content: " + e.getMessage());
                throw new RuntimeException("Failed to generate podcast content", e);
            }
        });
    }
    
    /**
     * Build a prompt for GPT-4o to create conversational podcast content
     * 
     * @param articles News articles to discuss
     * @param topics Preferred topics
     * @param durationMinutes Target duration
     * @return Formatted prompt string
     */
    private String buildConversationalPrompt(Set<NewsArticle> articles, List<String> topics, int durationMinutes) {
        StringBuilder prompt = new StringBuilder();
        
        // Explain the task
        prompt.append("You are two podcast hosts, Alex and Jordan, having a conversation about today's news. ");
        prompt.append("Create a podcast script formatted as a conversation between you two. ");
        
        // Target duration info
        int averageWordsPerMinute = 150;
        int targetWordCount = durationMinutes * averageWordsPerMinute;
        prompt.append("Your response should be about ").append(targetWordCount).append(" words ");
        prompt.append("to fill approximately ").append(durationMinutes).append(" minutes of speaking time. ");
        
        // Topic preferences
        if (topics != null && !topics.isEmpty()) {
            prompt.append("Focus on these topics: ").append(String.join(", ", topics)).append(". ");
        }
        
        // Styling guidance
        prompt.append("Start with a brief introduction and end with a conclusion. ");
        prompt.append("Make the conversation sound natural, engaging, and informative. ");
        prompt.append("Include some light banter and personality. ");
        
        // Add article information
        prompt.append("Discuss these news articles:\n\n");
        
        for (NewsArticle article : articles) {
            prompt.append("Title: ").append(article.getTitle()).append("\n");
            if (article.getSection() != null && !article.getSection().equals("Unknown")) {
                prompt.append("Section: ").append(article.getSection()).append("\n");
            }
            prompt.append("Abstract: ").append(article.getAbstract()).append("\n\n");
        }
        
        // Output format instruction
        prompt.append("Format the conversation as follows:\n");
        prompt.append("ALEX: [Alex's dialogue]\n");
        prompt.append("JORDAN: [Jordan's dialogue]\n\n");
        
        // Request response structure
        prompt.append("Return a JSON object with these fields:\n");
        prompt.append("1. 'intro': The introduction segment\n");
        prompt.append("2. 'segments': An array of news discussion segments (one per article)\n");
        prompt.append("3. 'conclusion': The conclusion segment\n");
        prompt.append("Each segment should have a 'title' and 'content' field. The content should be the formatted conversation.");
        
        return prompt.toString();
    }
    
    /**
     * Send a chat completion request to OpenAI API
     * 
     * @param prompt The prompt to send
     * @return Raw JSON response from API
     * @throws IOException If request fails
     */
    private String sendChatCompletionRequest(String prompt) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", MODEL);
        
        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        
        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.7);
        
        RequestBody body = RequestBody.create(requestBody.toString(), JSON);
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API request failed: " + response.code() + " " + response.message());
            }
            
            return response.body() != null ? response.body().string() : "";
        }
    }
    
    /**
     * Convert the API response to a PodcastContent object
     * 
     * @param jsonResponse Raw JSON response from API
     * @param topics List of topics
     * @param podcastTitle The podcast title
     * @return Structured PodcastContent
     */
    private PodcastContent convertResponseToPodcastContent(String jsonResponse, List<String> topics, String podcastTitle) {
        try {
            // Parse the OpenAI response
            OpenAIResponse openAIResponse = gson.fromJson(jsonResponse, OpenAIResponse.class);
            String content = openAIResponse.getChoices().get(0).getMessage().getContent();
            
            // Parse the JSON content returned by GPT
            PodcastResponseContent responseContent = gson.fromJson(content, PodcastResponseContent.class);
            
            // Create a new PodcastContent
            PodcastContent podcastContent = new PodcastContent(podcastTitle, topics);
            
            // Add introduction segment
            podcastContent.addSegment(
                new PodcastSegment("Introduction", responseContent.getIntro(), PodcastSegment.SegmentType.INTRO)
            );
            
            // Add news segments
            if (responseContent.getSegments() != null) {
                for (PodcastSegmentContent segment : responseContent.getSegments()) {
                    podcastContent.addSegment(
                        new PodcastSegment(segment.getTitle(), segment.getContent(), PodcastSegment.SegmentType.NEWS_ARTICLE)
                    );
                }
            }
            
            // Add conclusion segment
            podcastContent.addSegment(
                new PodcastSegment("Conclusion", responseContent.getConclusion(), PodcastSegment.SegmentType.CONCLUSION)
            );
            
            return podcastContent;
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing API response: " + e.getMessage());
            
            // Fallback: create a simple podcast content with the raw response
            PodcastContent fallbackContent = new PodcastContent(podcastTitle, topics);
            fallbackContent.addSegment(
                new PodcastSegment("AI Generated Content", jsonResponse, PodcastSegment.SegmentType.NEWS_ARTICLE)
            );
            return fallbackContent;
        }
    }
    
    /**
     * Helper class for parsing OpenAI API response
     */
    private static class OpenAIResponse {
        private List<Choice> choices;
        
        public List<Choice> getChoices() {
            return choices;
        }
        
        public static class Choice {
            private Message message;
            
            public Message getMessage() {
                return message;
            }
        }
        
        public static class Message {
            private String content;
            
            public String getContent() {
                return content;
            }
        }
    }
    
    /**
     * Helper class for parsing the podcast content JSON from GPT
     */
    private static class PodcastResponseContent {
        private String intro;
        private List<PodcastSegmentContent> segments;
        private String conclusion;
        
        public String getIntro() {
            return intro;
        }
        
        public List<PodcastSegmentContent> getSegments() {
            return segments;
        }
        
        public String getConclusion() {
            return conclusion;
        }
    }
    
    /**
     * Helper class for podcast segment JSON format
     */
    private static class PodcastSegmentContent {
        private String title;
        private String content;
        
        public String getTitle() {
            return title;
        }
        
        public String getContent() {
            return content;
        }
    }
} 