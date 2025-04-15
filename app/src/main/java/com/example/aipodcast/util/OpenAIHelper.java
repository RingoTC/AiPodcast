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
public class OpenAIHelper {
    private static final String TAG = "OpenAIHelper";
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public static final String SPEAKER_MARKER = "§HOST§";
    private static final String MODEL_GPT_4 = "gpt-4o";
    private static final String MODEL_GPT_3_5 = "gpt-3.5-turbo";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    public static OkHttpClient buildOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }
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
    public static String buildPodcastPrompt(Set<NewsArticle> articles, List<String> topics, int durationMinutes) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a podcast script where a host discusses today's news. ");
        int averageWordsPerMinute = 150;
        int targetWordCount = durationMinutes * averageWordsPerMinute;
        prompt.append("Your response should be about ").append(targetWordCount).append(" words ");
        prompt.append("to fill approximately ").append(durationMinutes).append(" minutes of speaking time. ");
        if (topics != null && !topics.isEmpty()) {
            prompt.append("Focus on these topics: ").append(String.join(", ", topics)).append(". ");
        }
        prompt.append("\nIMPORTANT: Format your response as a monologue with paragraph breaks.\n");
        prompt.append("Start every paragraph with '").append(SPEAKER_MARKER).append("' (exactly like that)\n");
        prompt.append("These markers will be used by our system to properly format the transcript.\n\n");
        prompt.append("For example:\n");
        prompt.append(SPEAKER_MARKER).append(" Welcome to our podcast! Today we'll be discussing some fascinating news stories.\n\n");
        prompt.append(SPEAKER_MARKER).append(" Our first story is about...\n\n");
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
        prompt.append("Remember to use ").append(SPEAKER_MARKER).append(" to indicate the start of each paragraph.\n");
        return prompt.toString();
    }
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
                        if (code == 429 || code >= 500) {
                            Log.w(TAG, "API request failed with code " + code + ": " + errorBody + " - Retrying (" + (retries + 1) + "/" + MAX_RETRIES + ")");
                            retries++;
                            Thread.sleep(RETRY_DELAY_MS * retries); 
                            continue;
                        } else {
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
                if (retries < MAX_RETRIES - 1) {
                    retries++;
                    try {
                        Log.w(TAG, "Retrying request after error (" + retries + "/" + MAX_RETRIES + ")");
                        Thread.sleep(RETRY_DELAY_MS * retries); 
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
    public static String extractContentFromResponse(String jsonResponse) {
        try {
            String contentPattern = "\"content\"\\s*:\\s*\"([^\"]*(?:\\\\\"[^\"]*)*?)\"";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(contentPattern);
            java.util.regex.Matcher matcher = pattern.matcher(jsonResponse);
            if (matcher.find()) {
                String content = matcher.group(1);
                return content.replace("\\n", "\n")
                              .replace("\\\"", "\"")
                              .replace("\\\\", "\\");
            }
            Log.w(TAG, "Regex extraction failed, fallback to manual parsing");
            int contentStart = jsonResponse.indexOf("\"content\":");
            if (contentStart >= 0) {
                int valueStart = jsonResponse.indexOf("\"", contentStart + 10) + 1;
                int valueEnd = -1;
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
                    return content.replace("\\n", "\n")
                                  .replace("\\\"", "\"")
                                  .replace("\\\\", "\\");
                }
            }
            Log.w(TAG, "Failed to extract content from JSON response");
            return "Error extracting content. Raw response: " + jsonResponse;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting content from response: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
    public static String processStreamingChunk(String chunk) {
        try {
            if (chunk.startsWith("data: ")) {
                chunk = chunk.substring(6);
            }
            if (chunk.equals("[DONE]")) {
                return null;
            }
            if (chunk.contains("\"delta\"") && chunk.contains("\"content\"")) {
                String contentPattern = "\"content\"\\s*:\\s*\"([^\"]*(?:\\\\\"[^\"]*)*?)\"";
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(contentPattern);
                java.util.regex.Matcher matcher = pattern.matcher(chunk);
                if (matcher.find()) {
                    String content = matcher.group(1);
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