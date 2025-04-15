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
     * Generate conversational podcast content based on news articles with streaming updates
     * 
     * @param articles Set of news articles to discuss
     * @param topics List of preferred topics
     * @param durationMinutes Target duration in minutes
     * @param podcastTitle Title for the podcast
     * @param responseHandler Callback for streaming updates
     * @return CompletableFuture with generated PodcastContent
     */
    public CompletableFuture<PodcastContent> generatePodcastContentStreaming(
            Set<NewsArticle> articles, 
            List<String> topics, 
            int durationMinutes,
            String podcastTitle,
            StreamingResponseHandler responseHandler) {
        
        CompletableFuture<PodcastContent> future = new CompletableFuture<>();
        
        try {
            // Build the prompt for GPT-4o
            String prompt = buildConversationalPrompt(articles, topics, durationMinutes);
            
            // Send streaming request asynchronously
            sendStreamingRequest(prompt, responseHandler)
                .thenApply(finalResponse -> {
                    // Create final podcast content from complete response
                    PodcastContent content = convertResponseToPodcastContent(finalResponse, topics, podcastTitle);
                    future.complete(content);
                    return content;
                })
                .exceptionally(e -> {
                    future.completeExceptionally(e);
                    return null;
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Error initializing streaming request: " + e.getMessage());
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Build a prompt for GPT-4o to create single-host podcast content
     * 
     * @param articles News articles to discuss
     * @param topics Preferred topics
     * @param durationMinutes Target duration
     * @return Formatted prompt string
     */
    private String buildConversationalPrompt(Set<NewsArticle> articles, List<String> topics, int durationMinutes) {
        StringBuilder prompt = new StringBuilder();
        
        // Explain the task - 修改为单人播客风格
        prompt.append("You are a podcast host creating a news summary podcast. ");
        prompt.append("Create a podcast script where you discuss today's news in an engaging, informative manner. ");
        
        // Target duration info
        int averageWordsPerMinute = 150;
        int targetWordCount = durationMinutes * averageWordsPerMinute;
        prompt.append("Your response should be about ").append(targetWordCount).append(" words ");
        prompt.append("to fill approximately ").append(durationMinutes).append(" minutes of speaking time. ");
        
        // Topic preferences
        if (topics != null && !topics.isEmpty()) {
            prompt.append("Focus on these topics: ").append(String.join(", ", topics)).append(". ");
        }
        
        // Styling guidance with special markers - 使用单一HOST标记
        prompt.append("IMPORTANT: Use special markers to indicate speaker paragraphs as follows:\n");
        prompt.append("Start every paragraph with '§HOST§' (exactly like that)\n");
        prompt.append("These markers will be used by our system to properly format the transcript.\n\n");
        
        prompt.append("For example:\n");
        prompt.append("§HOST§ Welcome to our podcast! Today we'll be discussing some fascinating news stories.\n\n");
        prompt.append("§HOST§ Our first story is about...\n\n");
        
        // Add article information
        prompt.append("Discuss these news articles:\n\n");
        
        for (NewsArticle article : articles) {
            prompt.append("Title: ").append(article.getTitle()).append("\n");
            if (article.getSection() != null && !article.getSection().equals("Unknown")) {
                prompt.append("Section: ").append(article.getSection()).append("\n");
            }
            prompt.append("Abstract: ").append(article.getAbstract()).append("\n\n");
        }
        
        // Output format instruction - 修改为单人风格指导
        prompt.append("Format as a natural, engaging monologue with clear transitions between topics.\n");
        prompt.append("Start with a brief introduction and end with a conclusion.\n");
        prompt.append("Use a conversational tone as if speaking directly to listeners.\n\n");
        
        // Response format instructions
        prompt.append("Return the podcast as plain text without additional formatting or markdown.\n");
        prompt.append("Remember to use §HOST§ to indicate the start of each paragraph.\n");
        
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
     * Send a streaming chat completion request to OpenAI API
     * 
     * @param prompt The prompt to send
     * @param responseHandler Handler for streaming responses
     * @return CompletableFuture with final complete response
     */
    private CompletableFuture<String> sendStreamingRequest(String prompt, StreamingResponseHandler responseHandler) {
        CompletableFuture<String> future = new CompletableFuture<>();
        final StringBuilder completeResponse = new StringBuilder();
        
        // Start with notification of beginning generation
        responseHandler.onContentReceived("Starting generation...");
        Log.d(TAG, "Starting streaming request to OpenAI API...");
        
        Thread streamingThread = new Thread(() -> {
            // Add logging interceptor for debugging
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);
            
            OkHttpClient streamClient = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .readTimeout(120, TimeUnit.SECONDS) // Increased timeout
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();
                    
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", MODEL);
            requestBody.addProperty("stream", true);
            requestBody.addProperty("temperature", 0.7);
            
            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt);
            messages.add(message);
            
            requestBody.add("messages", messages);
            
            // Log request body for debugging
            Log.d(TAG, "API Request Body: " + requestBody.toString());
            
            RequestBody body = RequestBody.create(requestBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();
            
            try (Response response = streamClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No response body";
                    Log.e(TAG, "API request failed: " + response.code() + " " + response.message() + " - " + errorBody);
                    throw new IOException("API request failed: " + response.code() + " " + response.message());
                }
                
                if (response.body() == null) {
                    Log.e(TAG, "Empty response body from API");
                    throw new IOException("Empty response body");
                }
                
                Log.d(TAG, "API response received, processing stream...");
                responseHandler.onContentReceived("API connected, processing response...");
                
                // Process streaming response
                try (okio.BufferedSource source = response.body().source()) {
                    String currentLine;
                    String currentSpeaker = null;
                    int lineCount = 0;
                    
                    // Initialize the full transcript that will be continuously updated
                    String fullTranscript = "";
                    
                    while ((currentLine = source.readUtf8Line()) != null) {
                        lineCount++;
                        if (lineCount % 10 == 0) {
                            Log.d(TAG, "Processed " + lineCount + " lines from stream");
                        }
                        
                        if (currentLine.isEmpty()) continue;
                        
                        // Log raw line for debugging
                        Log.d(TAG, "Stream line: " + currentLine);
                        
                        // Skip prefixes like "data: "
                        if (currentLine.startsWith("data: ")) {
                            currentLine = currentLine.substring(6);
                        }
                        
                        // Skip "[DONE]" message
                        if (currentLine.equals("[DONE]")) {
                            Log.d(TAG, "Received stream [DONE] marker");
                            continue;
                        }
                        
                        try {
                            JsonObject chunk = gson.fromJson(currentLine, JsonObject.class);
                            if (chunk.has("choices")) {
                                JsonArray choices = chunk.getAsJsonArray("choices");
                                if (choices.size() > 0) {
                                    JsonObject choice = choices.get(0).getAsJsonObject();
                                    
                                    if (choice.has("delta")) {
                                        JsonObject delta = choice.getAsJsonObject("delta");
                                        
                                        if (delta.has("content")) {
                                            String content = delta.get("content").getAsString();
                                            
                                            // Append to complete response
                                            completeResponse.append(content);
                                            String currentFullText = completeResponse.toString();
                                            
                                            // Check if content contains our special markers
                                            boolean containsHostMarker = content.contains("§HOST§");
                                            
                                            // Always send the full transcript update with every token
                                            responseHandler.onFullTranscriptUpdate(currentFullText);
                                            
                                            // Process speaker changes if markers are detected
                                            if (containsHostMarker) {
                                                // Determine new speaker
                                                String newSpeaker = "HOST";
                                                
                                                // Update current speaker if changed
                                                if (!newSpeaker.equals(currentSpeaker)) {
                                                    currentSpeaker = newSpeaker;
                                                    responseHandler.onSpeakerChange(currentSpeaker);
                                                    Log.d(TAG, "Speaker changed to: " + currentSpeaker);
                                                }
                                            }
                                            
                                            // Send token regardless of speaker change
                                            if (currentSpeaker != null) {
                                                responseHandler.onTokenReceived(currentSpeaker, content);
                                            } else {
                                                // Handle text before any speaker is identified
                                                responseHandler.onContentReceived(content);
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing streaming response: " + e.getMessage(), e);
                        }
                    }
                }
                
                // Process complete response at the end
                String finalResponse = completeResponse.toString();
                
                // Notify handler of completion
                responseHandler.onComplete(finalResponse);
                future.complete(finalResponse);
                
                Log.d(TAG, "Streaming completed successfully");
                
            } catch (Exception e) {
                Log.e(TAG, "Error in streaming request: " + e.getMessage(), e);
                responseHandler.onError("Streaming error: " + e.getMessage());
                future.completeExceptionally(e);
            }
        });
        
        streamingThread.start();
        return future;
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
            
            // Check if the content contains JSON
            if (content.contains("{") && content.contains("}")) {
                // Extract JSON part if it's embedded in text (like with code blocks)
                int jsonStart = content.indexOf('{');
                int jsonEnd = content.lastIndexOf('}') + 1;
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    content = content.substring(jsonStart, jsonEnd);
                }
            }
            
            Log.d(TAG, "Parsed content from GPT: " + content);
            
            // Parse the JSON content returned by GPT
            PodcastResponseContent responseContent;
            try {
                responseContent = gson.fromJson(content, PodcastResponseContent.class);
                
                // Validate the parsed content
                if (responseContent == null || responseContent.getIntro() == null) {
                    throw new Exception("Invalid JSON format from GPT");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing JSON content: " + e.getMessage());
                // Fallback to generating a default structure from the raw content
                return createFallbackPodcastContent(content, topics, podcastTitle);
            }
            
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
            
            // Create a more user-friendly fallback
            return createFallbackPodcastContent(jsonResponse, topics, podcastTitle);
        }
    }
    
    /**
     * Create a fallback podcast content when parsing fails
     * 
     * @param rawContent Raw content from API
     * @param topics List of topics
     * @param podcastTitle The podcast title
     * @return A basic PodcastContent with formatted segments
     */
    private PodcastContent createFallbackPodcastContent(String rawContent, List<String> topics, String podcastTitle) {
        PodcastContent fallbackContent = new PodcastContent(podcastTitle, topics);
        
        // Try to extract conversation parts if they exist
        String conversationContent = extractConversationFromRawText(rawContent);
        
        // Introduction segment
        fallbackContent.addSegment(
            new PodcastSegment("Introduction", 
                "Welcome to your AI-generated podcast about " + String.join(", ", topics) + ". " +
                "Let's dive into today's topics!", 
                PodcastSegment.SegmentType.INTRO)
        );
        
        // Main content segment
        fallbackContent.addSegment(
            new PodcastSegment("AI Generated Content", 
                conversationContent,
                PodcastSegment.SegmentType.NEWS_ARTICLE)
        );
        
        // Conclusion segment
        fallbackContent.addSegment(
            new PodcastSegment("Conclusion", 
                "Thanks for listening to this AI-generated podcast. We hope you found it informative!", 
                PodcastSegment.SegmentType.CONCLUSION)
        );
        
        return fallbackContent;
    }
    
    /**
     * Extract conversation format from raw text
     * Tries to find HOST: patterns
     */
    private String extractConversationFromRawText(String rawText) {
        StringBuilder formattedConversation = new StringBuilder();
        
        try {
            // Check if it contains JSON with conversation parts
            if (rawText.contains("\"content\":")) {
                // Try to extract content
                int contentStart = rawText.indexOf("\"content\":");
                if (contentStart >= 0) {
                    int valueStart = rawText.indexOf("\"", contentStart + 10) + 1;
                    int valueEnd = rawText.indexOf("\"", valueStart);
                    if (valueStart >= 0 && valueEnd > valueStart) {
                        String content = rawText.substring(valueStart, valueEnd);
                        // Unescape JSON string
                        content = content.replace("\\n", "\n")
                                         .replace("\\\"", "\"")
                                         .replace("\\\\", "\\");
                        return content;
                    }
                }
            }
            
            // Check for conversation format directly
            if (rawText.contains("HOST:") || rawText.contains("HOST:")) {
                // Split by lines and format nicely
                String[] lines = rawText.split("\\r?\\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.startsWith("HOST:") || line.startsWith("HOST:")) {
                        formattedConversation.append(line).append("\n\n");
                    } else if (!line.isEmpty()) {
                        formattedConversation.append(line).append("\n");
                    }
                }
                return formattedConversation.toString();
            }
            
            // Last resort - just try to clean up the raw text
            return rawText.replace("{", "")
                         .replace("}", "")
                         .replace("\"", "")
                         .replace("\\n", "\n")
                         .replace("role:", "\nRole: ")
                         .replace("content:", "\nContent: ");
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting conversation: " + e.getMessage());
            // Return somewhat cleaned raw text as fallback
            return rawText;
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
    
    /**
     * Interface for handling streaming responses
     */
    public interface StreamingResponseHandler {
        void onContentReceived(String content);
        void onSpeakerChange(String speaker);
        void onTokenReceived(String speaker, String token);
        void onSpeakerComplete(String speaker, String completeText);
        void onComplete(String fullResponse);
        void onError(String error);
        void onFullTranscriptUpdate(String fullTranscript);
    }
    
    /**
     * 处理说话者切换逻辑
     * 
     * @param content 当前内容片段
     * @param currentSpeaker 当前说话者
     * @param currentSpeakerBuffer 当前说话者的文本缓冲
     * @param responseHandler 响应处理器
     */
    private void processSpeakerChange(String content, String currentSpeaker, 
                                     StringBuilder currentSpeakerBuffer,
                                     StreamingResponseHandler responseHandler) {
        if (currentSpeaker != null && currentSpeakerBuffer != null && currentSpeakerBuffer.length() > 0) {
            // 完成当前说话者的段落，发送完整文本
            responseHandler.onSpeakerComplete(currentSpeaker, currentSpeakerBuffer.toString());
            Log.d(TAG, "完成说话者段落: " + currentSpeaker + " 内容: " + currentSpeakerBuffer.toString());
        }
    }
    
    /**
     * 从内容中提取说话者后的文本
     * 
     * @param speakerTag 说话者标签 (如 "HOST:")
     * @param content 原始内容
     * @return 提取的文本
     */
    private String extractSpeakerContent(String speakerTag, String content) {
        int startPos = content.indexOf(speakerTag);
        if (startPos < 0) return "";
        
        int contentStart = startPos + speakerTag.length();
        if (contentStart >= content.length()) return "";
        
        // 提取说话者标签后的内容
        String extracted = content.substring(contentStart).trim();
        Log.d(TAG, "提取的说话者内容: " + speakerTag + " -> " + extracted);
        return extracted;
    }
} 