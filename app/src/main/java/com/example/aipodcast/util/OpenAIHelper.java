package com.example.aipodcast.util;

import android.util.Log;

import com.example.aipodcast.model.NewsArticle;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Helper utility for working with OpenAI API requests.
 * Provides methods for prompt building, request formatting, and error handling.
 */
public class OpenAIHelper {
    private static final String TAG = "OpenAIHelper";
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public static final String SPEAKER_MARKER = "§HOST§";
    
    // Constants for OpenAI API
    private static final String MODEL_GPT_4 = "gpt-4o";
    private static final String MODEL_GPT_3_5 = "gpt-3.5-turbo";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    
    /**
     * Build an optimized OkHttpClient for OpenAI API requests
     * 
     * @return Configured OkHttpClient
     */
    public static OkHttpClient buildOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }
    
    /**
     * Build the JSON body for an OpenAI chat completion request
     * 
     * @param prompt The prompt to send
     * @param model The model to use (optional, defaults to GPT-4o)
     * @param temperature Temperature setting (optional, defaults to 0.7)
     * @param stream Whether to stream the response (optional, defaults to false)
     * @return JSON string for the request body
     */
    public static String buildRequestBody(String prompt, String model, Float temperature, Boolean stream) {
        if (model == null) model = MODEL_GPT_4;
        if (temperature == null) temperature = 0.7f;
        if (stream == null) stream = false;
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("temperature", temperature);
        requestBody.addProperty("stream", stream);
        
        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        
        requestBody.add("messages", messages);
        
        return requestBody.toString();
    }
    
    /**
     * Build a podcastPrompt for OpenAI based on news articles and preferences
     * 
     * @param articles Set of news articles
     * @param topics List of topics
     * @param durationMinutes Desired podcast duration in minutes
     * @return Formatted prompt string
     */
    public static String buildPodcastPrompt(Set<NewsArticle> articles, List<String> topics, int durationMinutes) {
        StringBuilder prompt = new StringBuilder();
        
        // Explain the task
        prompt.append("Generate a podcast script where a host discusses today's news. ");
        
        // Target duration info
        int averageWordsPerMinute = 150;
        int targetWordCount = durationMinutes * averageWordsPerMinute;
        prompt.append("Your response should be about ").append(targetWordCount).append(" words ");
        prompt.append("to fill approximately ").append(durationMinutes).append(" minutes of speaking time. ");
        
        // Topic preferences
        if (topics != null && !topics.isEmpty()) {
            prompt.append("Focus on these topics: ").append(String.join(", ", topics)).append(". ");
        }
        
        // Styling guidance with special marker
        prompt.append("\nIMPORTANT: Format your response as a monologue with paragraph breaks.\n");
        prompt.append("Start every paragraph with '").append(SPEAKER_MARKER).append("' (exactly like that)\n");
        prompt.append("These markers will be used by our system to properly format the transcript.\n\n");
        
        prompt.append("For example:\n");
        prompt.append(SPEAKER_MARKER).append(" Welcome to our podcast! Today we'll be discussing some fascinating news stories.\n\n");
        prompt.append(SPEAKER_MARKER).append(" Our first story is about...\n\n");
        
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
        prompt.append("Format as a natural, engaging monologue with clear transitions between topics.\n");
        prompt.append("Start with a brief introduction and end with a conclusion.\n");
        prompt.append("Use a conversational tone as if speaking directly to listeners.\n\n");
        
        // Response format instructions
        prompt.append("Return the podcast as plain text without additional formatting or markdown.\n");
        prompt.append("Remember to use ").append(SPEAKER_MARKER).append(" to indicate the start of each paragraph.\n");
        
        return prompt.toString();
    }
    
    /**
     * Execute an OpenAI API request with retry logic
     * 
     * @param client OkHttpClient to use
     * @param request Request to execute
     * @return Response body as string, or null if failed
     */
    public static String executeWithRetry(OkHttpClient client, Request request) {
        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        return response.body().string();
                    } else {
                        String errorBody = response.body() != null ? response.body().string() : "No response body";
                        int code = response.code();
                        
                        // Check if we should retry based on status code
                        if (code == 429 || code >= 500) {
                            // Rate limit or server error - retry
                            Log.w(TAG, "API request failed with code " + code + ": " + errorBody + " - Retrying (" + (retries + 1) + "/" + MAX_RETRIES + ")");
                            retries++;
                            Thread.sleep(RETRY_DELAY_MS * retries); // Exponential backoff
                            continue;
                        } else {
                            // Other error - fail fast
                            Log.e(TAG, "API request failed with code " + code + ": " + errorBody);
                            throw new RuntimeException("API request failed: " + code + " - " + errorBody);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("API request interrupted", e);
            } catch (Exception e) {
                Log.e(TAG, "Error executing API request: " + e.getMessage());
                
                // Check if we should retry
                if (retries < MAX_RETRIES - 1) {
                    retries++;
                    try {
                        Log.w(TAG, "Retrying request after error (" + retries + "/" + MAX_RETRIES + ")");
                        Thread.sleep(RETRY_DELAY_MS * retries); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                } else {
                    throw new RuntimeException("Failed after " + MAX_RETRIES + " retries: " + e.getMessage(), e);
                }
            }
        }
        
        throw new RuntimeException("Failed after " + MAX_RETRIES + " retries");
    }
    
    /**
     * Extract content from OpenAI API response
     * 
     * @param jsonResponse JSON response from OpenAI API
     * @return Extracted content text
     */
    public static String extractContentFromResponse(String jsonResponse) {
        try {
            // Parse using regex to avoid heavy JSON parsing if possible
            String contentPattern = "\"content\"\\s*:\\s*\"([^\"]*(?:\\\\\"[^\"]*)*?)\"";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(contentPattern);
            java.util.regex.Matcher matcher = pattern.matcher(jsonResponse);
            
            if (matcher.find()) {
                String content = matcher.group(1);
                // Unescape JSON string
                return content.replace("\\n", "\n")
                              .replace("\\\"", "\"")
                              .replace("\\\\", "\\");
            }
            
            // If regex fails, try JSON parsing (implementation omitted for brevity)
            Log.w(TAG, "Regex extraction failed, fallback to manual parsing");
            
            // Simple approach - just extract what's between content: and the next quote
            int contentStart = jsonResponse.indexOf("\"content\":");
            if (contentStart >= 0) {
                int valueStart = jsonResponse.indexOf("\"", contentStart + 10) + 1;
                int valueEnd = -1;
                
                // Find the matching end quote, accounting for escaped quotes
                boolean inEscape = false;
                for (int i = valueStart; i < jsonResponse.length(); i++) {
                    char c = jsonResponse.charAt(i);
                    if (inEscape) {
                        inEscape = false;
                    } else if (c == '\\') {
                        inEscape = true;
                    } else if (c == '"') {
                        valueEnd = i;
                        break;
                    }
                }
                
                if (valueEnd > valueStart) {
                    String content = jsonResponse.substring(valueStart, valueEnd);
                    // Unescape JSON string
                    return content.replace("\\n", "\n")
                                  .replace("\\\"", "\"")
                                  .replace("\\\\", "\\");
                }
            }
            
            // If all else fails, return raw JSON with a warning
            Log.w(TAG, "Failed to extract content from JSON response");
            return "Error extracting content. Raw response: " + jsonResponse;
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting content from response: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * Process streaming response chunk
     * 
     * @param chunk Response chunk
     * @return Extracted content, or null if not a content delta
     */
    public static String processStreamingChunk(String chunk) {
        try {
            // Skip prefixes like "data: "
            if (chunk.startsWith("data: ")) {
                chunk = chunk.substring(6);
            }
            
            // Skip [DONE] message
            if (chunk.equals("[DONE]")) {
                return null;
            }
            
            // Check for content in the delta
            if (chunk.contains("\"delta\"") && chunk.contains("\"content\"")) {
                // Extract content using regex for speed
                String contentPattern = "\"content\"\\s*:\\s*\"([^\"]*(?:\\\\\"[^\"]*)*?)\"";
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(contentPattern);
                java.util.regex.Matcher matcher = pattern.matcher(chunk);
                
                if (matcher.find()) {
                    String content = matcher.group(1);
                    // Unescape JSON string
                    return content.replace("\\n", "\n")
                                  .replace("\\\"", "\"")
                                  .replace("\\\\", "\\");
                }
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error processing streaming chunk: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Format an error message for user display
     * 
     * @param error The error
     * @return User-friendly error message
     */
    public static String formatErrorMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null) message = "Unknown error";
        
        if (message.contains("connect") || message.contains("timeout") || message.contains("SocketTimeoutException")) {
            return "Connection failed. Please check your internet connection and try again.";
        } else if (message.contains("429") || message.contains("rate") || message.contains("limit")) {
            return "Service is busy. Please try again in a few minutes.";
        } else if (message.contains("401") || message.contains("auth") || message.contains("key")) {
            return "Authentication failed. Please check your API key settings.";
        } else {
            return "Error generating content: " + message;
        }
    }
} 