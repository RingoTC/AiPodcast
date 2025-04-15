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
public class OpenAIService {
    private static final String TAG = "OpenAIService";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final String apiKey;
    private final OkHttpClient client;
    private final Gson gson;
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
    public CompletableFuture<PodcastContent> generatePodcastContent(
            Set<NewsArticle> articles, 
            List<String> topics, 
            int durationMinutes,
            String podcastTitle) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = buildConversationalPrompt(articles, topics, durationMinutes);
                String jsonResponse = sendChatCompletionRequest(prompt);
                return convertResponseToPodcastContent(jsonResponse, topics, podcastTitle);
            } catch (Exception e) {
                Log.e(TAG, "Error generating podcast content: " + e.getMessage());
                throw new RuntimeException("Failed to generate podcast content", e);
            }
        });
    }
    public CompletableFuture<PodcastContent> generatePodcastContentStreaming(
            Set<NewsArticle> articles, 
            List<String> topics, 
            int durationMinutes,
            String podcastTitle,
            StreamingResponseHandler responseHandler) {
        CompletableFuture<PodcastContent> future = new CompletableFuture<>();
        try {
            String prompt = buildConversationalPrompt(articles, topics, durationMinutes);
            sendStreamingRequest(prompt, responseHandler)
                .thenApply(finalResponse -> {
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
    private String buildConversationalPrompt(Set<NewsArticle> articles, List<String> topics, int durationMinutes) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a podcast host creating a news summary podcast. ");
        prompt.append("Create a podcast script where you discuss today's news in an engaging, informative manner. ");
        int averageWordsPerMinute = 190;
        int targetWordCount = durationMinutes * averageWordsPerMinute;
        prompt.append("EXTREMELY IMPORTANT: Your response MUST contain approximately ").append(targetWordCount).append(" words ");
        prompt.append("to fill ").append(durationMinutes).append(" minutes of speaking time. ");
        prompt.append("RESPONSE LENGTH IS CRITICAL - I need exactly ").append(durationMinutes).append(" minutes of content, ");
        prompt.append("no more, no less. Short responses are unacceptable and will not meet requirements. ");
        if (topics != null && !topics.isEmpty()) {
            prompt.append("Focus on these topics: ").append(String.join(", ", topics)).append(". ");
        }
        prompt.append("To reiterate: aim for ").append(targetWordCount).append(" words (about ").append(targetWordCount/5).append(" seconds of speech). ");
        prompt.append("Be thorough and detailed in your coverage of each article. Do not abbreviate or summarize. ");
        prompt.append("Include comprehensive discussion, background context, and analysis for each news item. ");
        prompt.append("IMPORTANT: Use special markers to indicate speaker paragraphs as follows:\n");
        prompt.append("Start every paragraph with '§HOST§' (exactly like that)\n");
        prompt.append("These markers will be used by our system to properly format the transcript.\n\n");
        prompt.append("For example:\n");
        prompt.append("§HOST§ Welcome to our podcast! Today we'll be discussing some fascinating news stories.\n\n");
        prompt.append("§HOST§ Our first story is about...\n\n");
        prompt.append("Discuss these news articles:\n\n");
        for (NewsArticle article : articles) {
            prompt.append("Title: ").append(article.getTitle()).append("\n");
            if (article.getSection() != null && !article.getSection().equals("Unknown")) {
                prompt.append("Section: ").append(article.getSection()).append("\n");
            }
            prompt.append("Abstract: ").append(article.getAbstract()).append("\n\n");
        }
        prompt.append("Format as a natural, engaging monologue with clear transitions between topics.\n");
        prompt.append("Start with a brief introduction and end with a conclusion.\n");
        prompt.append("Use a conversational tone as if speaking directly to listeners.\n\n");
        prompt.append("Return the podcast as plain text without additional formatting or markdown.\n");
        prompt.append("Remember to use §HOST§ to indicate the start of each paragraph.\n");
        prompt.append("Make sure your response has enough content to fill the requested duration of ").append(durationMinutes)
              .append(" minutes. Include detailed explanations and complete coverage of each news story. ");
        return prompt.toString();
    }
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
    private CompletableFuture<String> sendStreamingRequest(String prompt, StreamingResponseHandler responseHandler) {
        CompletableFuture<String> future = new CompletableFuture<>();
        final StringBuilder completeResponse = new StringBuilder();
        responseHandler.onContentReceived("Starting generation...");
        Log.d(TAG, "Starting streaming request to OpenAI API...");
        Thread streamingThread = new Thread(() -> {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);
            OkHttpClient streamClient = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .readTimeout(120, TimeUnit.SECONDS) 
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
                try (okio.BufferedSource source = response.body().source()) {
                    String currentLine;
                    String currentSpeaker = null;
                    int lineCount = 0;
                    String fullTranscript = "";
                    while ((currentLine = source.readUtf8Line()) != null) {
                        lineCount++;
                        if (lineCount % 10 == 0) {
                            Log.d(TAG, "Processed " + lineCount + " lines from stream");
                        }
                        if (currentLine.isEmpty()) continue;
                        Log.d(TAG, "Stream line: " + currentLine);
                        if (currentLine.startsWith("data: ")) {
                            currentLine = currentLine.substring(6);
                        }
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
                                            completeResponse.append(content);
                                            String currentFullText = completeResponse.toString();
                                            boolean containsHostMarker = content.contains("§HOST§");
                                            responseHandler.onFullTranscriptUpdate(currentFullText);
                                            if (containsHostMarker) {
                                                String newSpeaker = "HOST";
                                                if (!newSpeaker.equals(currentSpeaker)) {
                                                    currentSpeaker = newSpeaker;
                                                    responseHandler.onSpeakerChange(currentSpeaker);
                                                    Log.d(TAG, "Speaker changed to: " + currentSpeaker);
                                                }
                                            }
                                            if (currentSpeaker != null) {
                                                responseHandler.onTokenReceived(currentSpeaker, content);
                                            } else {
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
                String finalResponse = completeResponse.toString();
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
    private PodcastContent convertResponseToPodcastContent(String jsonResponse, List<String> topics, String podcastTitle) {
        try {
            OpenAIResponse openAIResponse = gson.fromJson(jsonResponse, OpenAIResponse.class);
            String content = openAIResponse.getChoices().get(0).getMessage().getContent();
            Log.d(TAG, "Received content from OpenAI API");
            if (content.contains("§HOST§")) {
                Log.d(TAG, "Detected §HOST§ markers in content - using direct format parsing");
                return createPodcastContentFromMarkedText(content, topics, podcastTitle);
            }
            if (content.contains("{") && content.contains("}")) {
                try {
                    int jsonStart = content.indexOf('{');
                    int jsonEnd = content.lastIndexOf('}') + 1;
                    if (jsonStart >= 0 && jsonEnd > jsonStart) {
                        String jsonContent = content.substring(jsonStart, jsonEnd);
                        PodcastResponseContent responseContent = gson.fromJson(jsonContent, PodcastResponseContent.class);
                        if (responseContent != null && responseContent.getIntro() != null) {
                            PodcastContent podcastContent = new PodcastContent(podcastTitle, topics);
                            podcastContent.addSegment(
                                new PodcastSegment("Introduction", responseContent.getIntro(), PodcastSegment.SegmentType.INTRO)
                            );
                            if (responseContent.getSegments() != null) {
                                for (PodcastSegmentContent segment : responseContent.getSegments()) {
                                    podcastContent.addSegment(
                                        new PodcastSegment(segment.getTitle(), segment.getContent(), PodcastSegment.SegmentType.NEWS_ARTICLE)
                                    );
                                }
                            }
                            podcastContent.addSegment(
                                new PodcastSegment("Conclusion", responseContent.getConclusion(), PodcastSegment.SegmentType.CONCLUSION)
                            );
                            return podcastContent;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing JSON content: " + e.getMessage());
                }
            }
            return createFallbackPodcastContent(content, topics, podcastTitle);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing API response: " + e.getMessage());
            return createFallbackPodcastContent(jsonResponse, topics, podcastTitle);
        }
    }
    private PodcastContent createPodcastContentFromMarkedText(String markedText, List<String> topics, String podcastTitle) {
        PodcastContent podcastContent = new PodcastContent(podcastTitle, topics);
        try {
            String[] paragraphs = markedText.split("§HOST§");
            int startIndex = (paragraphs.length > 0 && paragraphs[0].trim().isEmpty()) ? 1 : 0;
            if (paragraphs.length > startIndex) {
                String introText = paragraphs[startIndex].trim();
                podcastContent.addSegment(
                    new PodcastSegment("Introduction", introText, PodcastSegment.SegmentType.INTRO)
                );
                for (int i = startIndex + 1; i < paragraphs.length - 1; i++) {
                    String segmentText = paragraphs[i].trim();
                    if (!segmentText.isEmpty()) {
                        String segmentTitle = "Segment " + (i - startIndex);
                        podcastContent.addSegment(
                            new PodcastSegment(segmentTitle, segmentText, PodcastSegment.SegmentType.NEWS_ARTICLE)
                        );
                    }
                }
                if (paragraphs.length > startIndex + 1) {
                    String conclusionText = paragraphs[paragraphs.length - 1].trim();
                    podcastContent.addSegment(
                        new PodcastSegment("Conclusion", conclusionText, PodcastSegment.SegmentType.CONCLUSION)
                    );
                }
            } else {
                podcastContent.addSegment(
                    new PodcastSegment("AI Generated Content", markedText, PodcastSegment.SegmentType.NEWS_ARTICLE)
                );
            }
            if (podcastContent.getSegments().isEmpty()) {
                podcastContent.addSegment(
                    new PodcastSegment("AI Generated Content", markedText, PodcastSegment.SegmentType.NEWS_ARTICLE)
                );
            }
            return podcastContent;
        } catch (Exception e) {
            Log.e(TAG, "Error processing marked text: " + e.getMessage());
            podcastContent.addSegment(
                new PodcastSegment("AI Generated Content", markedText, PodcastSegment.SegmentType.NEWS_ARTICLE)
            );
            return podcastContent;
        }
    }
    private PodcastContent createFallbackPodcastContent(String rawContent, List<String> topics, String podcastTitle) {
        PodcastContent fallbackContent = new PodcastContent(podcastTitle, topics);
        if (rawContent.contains("§HOST§")) {
            return createPodcastContentFromMarkedText(rawContent, topics, podcastTitle);
        }
        String conversationContent = extractConversationFromRawText(rawContent);
        fallbackContent.addSegment(
            new PodcastSegment("Introduction", 
                "Welcome to your AI-generated podcast about " + String.join(", ", topics) + ". " +
                "Let's dive into today's topics!", 
                PodcastSegment.SegmentType.INTRO)
        );
        fallbackContent.addSegment(
            new PodcastSegment("AI Generated Content", 
                conversationContent,
                PodcastSegment.SegmentType.NEWS_ARTICLE)
        );
        fallbackContent.addSegment(
            new PodcastSegment("Conclusion", 
                "Thanks for listening to this AI-generated podcast. We hope you found it informative!", 
                PodcastSegment.SegmentType.CONCLUSION)
        );
        return fallbackContent;
    }
    private String extractConversationFromRawText(String rawText) {
        StringBuilder formattedConversation = new StringBuilder();
        try {
            if (rawText.contains("§HOST§")) {
                return rawText.replace("§HOST§", "HOST: ").trim();
            }
            if (rawText.contains("\"content\":")) {
                int contentStart = rawText.indexOf("\"content\":");
                if (contentStart >= 0) {
                    int valueStart = rawText.indexOf("\"", contentStart + 10) + 1;
                    int valueEnd = rawText.indexOf("\"", valueStart);
                    if (valueStart >= 0 && valueEnd > valueStart) {
                        String content = rawText.substring(valueStart, valueEnd);
                        content = content.replace("\\n", "\n")
                                         .replace("\\\"", "\"")
                                         .replace("\\\\", "\\");
                        return content;
                    }
                }
            }
            if (rawText.contains("HOST:") || rawText.contains("Host:")) {
                String[] lines = rawText.split("\\r?\\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.startsWith("HOST:") || line.startsWith("Host:")) {
                        formattedConversation.append(line).append("\n\n");
                    } else if (!line.isEmpty()) {
                        formattedConversation.append(line).append("\n");
                    }
                }
                return formattedConversation.toString();
            }
            return rawText.replace("{", "")
                         .replace("}", "")
                         .replace("\"", "")
                         .replace("\\n", "\n")
                         .replace("role:", "\nRole: ")
                         .replace("content:", "\nContent: ");
        } catch (Exception e) {
            Log.e(TAG, "Error extracting conversation: " + e.getMessage());
            return rawText;
        }
    }
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
    public interface StreamingResponseHandler {
        void onContentReceived(String content);
        void onSpeakerChange(String speaker);
        void onTokenReceived(String speaker, String token);
        void onSpeakerComplete(String speaker, String completeText);
        void onComplete(String fullResponse);
        void onError(String error);
        void onFullTranscriptUpdate(String fullTranscript);
    }
    private void processSpeakerChange(String content, String currentSpeaker, 
                                     StringBuilder currentSpeakerBuffer,
                                     StreamingResponseHandler responseHandler) {
        if (currentSpeaker != null && currentSpeakerBuffer != null && currentSpeakerBuffer.length() > 0) {
            responseHandler.onSpeakerComplete(currentSpeaker, currentSpeakerBuffer.toString());
            Log.d(TAG, "完成说话者段落: " + currentSpeaker + " 内容: " + currentSpeakerBuffer.toString());
        }
    }
    private String extractSpeakerContent(String speakerTag, String content) {
        int startPos = content.indexOf(speakerTag);
        if (startPos < 0) return "";
        int contentStart = startPos + speakerTag.length();
        if (contentStart >= content.length()) return "";
        String extracted = content.substring(contentStart).trim();
        Log.d(TAG, "提取的说话者内容: " + speakerTag + " -> " + extracted);
        return extracted;
    }
} 