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
    // Constants for word counts and timing
    private static final int WORDS_PER_MINUTE = 140;
    private static final int MIN_WORDS_PER_ARTICLE = 150;
    private static final int MAX_WORDS_PER_ARTICLE = 400;

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
                // Create a more detailed prompt focused on article content
                String prompt = buildConversationalPrompt(articles, topics, durationMinutes);

                // Increase temperature for more creative/detailed responses
                String jsonResponse = sendChatCompletionRequest(prompt, 0.8f);

                PodcastContent content = convertResponseToPodcastContent(
                        jsonResponse, topics, podcastTitle, durationMinutes);

                // Validate the content to ensure it's covering the articles properly
                validateContentQuality(content, articles);

                return content;
            } catch (Exception e) {
                Log.e(TAG, "Error generating podcast content: " + e.getMessage());
                throw new RuntimeException("Failed to generate podcast content", e);
            }
        });
    }
    // Add a validation method to check content quality
    private void validateContentQuality(PodcastContent content, Set<NewsArticle> articles) {
        // Check if we have enough segments
        int expectedMinSegments = articles.size() + 2; // Intro, articles, conclusion

        if (content.getSegments().size() < expectedMinSegments) {
            Log.w(TAG, "Generated content has fewer segments than expected: " +
                    content.getSegments().size() + " vs expected " + expectedMinSegments);
        }

        // Check if content contains the article titles
        for (NewsArticle article : articles) {
            boolean foundTitle = false;
            String title = article.getTitle().toLowerCase();

            for (PodcastSegment segment : content.getSegments()) {
                if (segment.getText().toLowerCase().contains(title)) {
                    foundTitle = true;
                    break;
                }
            }

            if (!foundTitle) {
                Log.w(TAG, "Article title not found in content: " + title);
            }
        }
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
                        PodcastContent content = convertResponseToPodcastContent(
                                finalResponse, topics, podcastTitle, durationMinutes);
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

        // Calculate word count target based on duration
        int targetWordCount = durationMinutes * WORDS_PER_MINUTE;

        prompt.append("You are a professional podcast host creating a news podcast. ");
        prompt.append("Your task is to create a detailed " + durationMinutes + " minute podcast script analyzing the news articles I'll provide.\n\n");

        // Make it VERY explicit about using the full content
        prompt.append("CRITICAL INSTRUCTIONS:\n");
        prompt.append("1. I'm providing you with COMPLETE NEWS ARTICLES including FULL BODY TEXT.\n");
        prompt.append("2. Your podcast MUST primarily use information from the FULL BODY TEXT section of each article - this contains the complete article content.\n");
        prompt.append("3. DO NOT rely only on article titles or abstracts.\n");
        prompt.append("4. DO NOT add any generic commentary, analyses, or content not found in the articles.\n");
        prompt.append("5. The expected length is " + targetWordCount + " words total.\n\n");

        if (topics != null && !topics.isEmpty()) {
            prompt.append("Focus on these topics: ").append(String.join(", ", topics)).append("\n\n");
        }

        // Format instructions
        prompt.append("Format your podcast script by starting each paragraph with '§HOST§'\n\n");

        // Articles to discuss
        prompt.append("ARTICLES TO ANALYZE:\n\n");

        // Debug info to check if articles have content
        Log.d(TAG, "Building prompt with " + articles.size() + " articles");

        List<NewsArticle> articlesList = new ArrayList<>(articles);
        for (int i = 0; i < articlesList.size(); i++) {
            NewsArticle article = articlesList.get(i);
            String fullText = article.getFullBodyText();

            // Log to check if we actually have content
            Log.d(TAG, "Article " + i + ": " + article.getTitle() +
                    " - Full text length: " + (fullText != null ? fullText.length() : 0) + " chars");

            prompt.append("===================== ARTICLE " + (i+1) + " =====================\n");
            prompt.append("TITLE: " + article.getTitle() + "\n");

            if (article.getSection() != null && !article.getSection().equals("Unknown")) {
                prompt.append("SECTION: " + article.getSection() + "\n");
            }

            // Include the abstract for context
            prompt.append("BRIEF SUMMARY: " + article.getAbstract() + "\n\n");

            // This is the critical part - ensure we're using the full text
            prompt.append("FULL ARTICLE TEXT:\n" + (fullText != null && !fullText.isEmpty()
                    ? fullText
                    : "Full text not available. Use the brief summary above.") + "\n\n");

            prompt.append("=====================================================\n\n");
        }

        // Final reminders
        prompt.append("REMINDER: Your podcast script must:\n");
        prompt.append("1. Start every paragraph with '§HOST§'\n");
        prompt.append("2. Use the COMPLETE information from the full article texts\n");
        prompt.append("3. Be informative and engaging\n");
        prompt.append("4. Aim for approximately " + targetWordCount + " words total\n");

        return prompt.toString();
    }
    /**
     * Detects common patterns of generic filler content
     */
    private boolean containsGenericFiller(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // List of common filler patterns
        String[] fillerPatterns = {
                "broader implications",
                "experts in the field",
                "different perspectives",
                "potential long-term effects",
                "immediate impacts",
                "worth noting that",
                "additionally, it's worth",
                "some point to",
                "others focus on",
                "in conclusion",
                "to summarize",
                "this development could influence"
        };

        String lowerText = text.toLowerCase();

        for (String pattern : fillerPatterns) {
            if (lowerText.contains(pattern.toLowerCase())) {
                Log.w(TAG, "Detected generic filler pattern: " + pattern);
                return true;
            }
        }

        return false;
    }
    private String sendChatCompletionRequest(String prompt, float temperature) throws java.io.IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", MODEL);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);

        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", temperature);
        // Add higher max tokens to ensure complete responses
        requestBody.addProperty("max_tokens", 4000);

        RequestBody body = RequestBody.create(requestBody.toString(), JSON);
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new java.io.IOException("API request failed: " + response.code() + " " + response.message());
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    // Overload the original method to maintain backward compatibility
    private String sendChatCompletionRequest(String prompt) throws java.io.IOException {
        return sendChatCompletionRequest(prompt, 0.7f);
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
    private PodcastContent convertResponseToPodcastContent(
            String jsonResponse,
            List<String> topics,
            String podcastTitle,
            int targetDuration) {
        try {
            OpenAIResponse openAIResponse = gson.fromJson(jsonResponse, OpenAIResponse.class);
            String content = openAIResponse.getChoices().get(0).getMessage().getContent();

            Log.d(TAG, "Received content from OpenAI API");

            // Clean the content before processing
            content = removeGenericFiller(content);

            PodcastContent podcastContent;

            if (content.contains("§HOST§")) {
                Log.d(TAG, "Detected §HOST§ markers in content - using direct format parsing");
                podcastContent = createPodcastContentFromMarkedText(content, topics, podcastTitle);
            } else {
                podcastContent = createFallbackPodcastContent(content, topics, podcastTitle);
            }

            // Force duration explicitly
            int targetSeconds = targetDuration * 60;
            podcastContent.forceSetTotalDuration(targetSeconds);
            Log.d(TAG, "Enforced podcast duration to exactly " + targetSeconds + " seconds");

            return podcastContent;

        } catch (Exception e) {
            Log.e(TAG, "Error parsing API response: " + e.getMessage());
            PodcastContent fallbackContent = createFallbackPodcastContent(jsonResponse, topics, podcastTitle);

            // Force duration even for fallback
            int targetSeconds = targetDuration * 60;
            fallbackContent.forceSetTotalDuration(targetSeconds);

            return fallbackContent;
        }
    }

    /**
     * Removes paragraphs containing generic filler content
     */
    private String removeGenericFiller(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        StringBuilder cleanedContent = new StringBuilder();

        // Split the content by §HOST§ marker
        String[] parts = content.split("§HOST§");

        for (String part : parts) {
            if (part.trim().isEmpty()) {
                continue;
            }

            // Only add paragraphs that don't contain generic filler
            if (!containsGenericFiller(part)) {
                cleanedContent.append("§HOST§").append(part);
            } else {
                Log.d(TAG, "Removed filler paragraph: " + part.trim());
            }
        }

        return cleanedContent.toString();
    }

    private void validatePodcastDuration(PodcastContent podcastContent, int targetDurationMinutes) {
        int targetDurationSeconds = targetDurationMinutes * 60;
        int actualDuration = podcastContent.getTotalDuration();
        int difference = Math.abs(actualDuration - targetDurationSeconds);
        float percentDiff = (float) difference / targetDurationSeconds * 100;

        Log.d(TAG, String.format(
                "Podcast duration validation: %d seconds (target: %d). Difference: %.1f%%",
                actualDuration, targetDurationSeconds, percentDiff
        ));

        // If we're significantly off target, force the duration
        if (percentDiff > 15) {
            podcastContent.setTotalDuration(targetDurationSeconds);
            Log.d(TAG, "Adjusted podcast duration to match target: " + targetDurationSeconds + " seconds");
        }
    }
    private PodcastContent createPodcastContentFromMarkedText(String markedText, List<String> topics, String podcastTitle) {
        PodcastContent podcastContent = new PodcastContent(podcastTitle, topics);
        try {
            // Split on our marker
            String[] parts = markedText.split("§HOST§");

            int startIndex = (parts.length > 0 && parts[0].trim().isEmpty()) ? 1 : 0;

            // Check if we have enough content
            if (parts.length > startIndex + 2) { // At least intro, content, conclusion
                // Add intro segment
                String introText = parts[startIndex].trim();
                if (!introText.isEmpty()) {
                    podcastContent.addSegment(
                            new PodcastSegment("Introduction", introText, PodcastSegment.SegmentType.INTRO)
                    );
                }

                // Process content segments - try to map to articles
                for (int i = startIndex + 1; i < parts.length - 1; i++) {
                    String segmentText = parts[i].trim();
                    if (!segmentText.isEmpty()) {
                        // Try to identify which article this segment is about
                        String segmentTitle = generateSegmentTitle(segmentText);
                        podcastContent.addSegment(
                                new PodcastSegment(segmentTitle, segmentText, PodcastSegment.SegmentType.NEWS_ARTICLE)
                        );
                    }
                }

                // Add conclusion segment
                String conclusionText = parts[parts.length - 1].trim();
                if (!conclusionText.isEmpty()) {
                    podcastContent.addSegment(
                            new PodcastSegment("Conclusion", conclusionText, PodcastSegment.SegmentType.CONCLUSION)
                    );
                }
            } else {
                // Not enough segments - try to process differently
                Log.w(TAG, "Not enough content segments found. Processing as a single script.");

                // Remove marker text for full script
                String fullText = markedText.replace("§HOST§", "").trim();

                // Try to split intelligently
                String[] paragraphs = fullText.split("\n\n");
                if (paragraphs.length >= 3) {
                    // Treat first paragraph as intro
                    podcastContent.addSegment(
                            new PodcastSegment("Introduction", paragraphs[0], PodcastSegment.SegmentType.INTRO)
                    );

                    // Middle paragraphs as content
                    StringBuilder contentBuilder = new StringBuilder();
                    for (int i = 1; i < paragraphs.length - 1; i++) {
                        contentBuilder.append(paragraphs[i]).append("\n\n");
                    }

                    podcastContent.addSegment(
                            new PodcastSegment("News Content", contentBuilder.toString(), PodcastSegment.SegmentType.NEWS_ARTICLE)
                    );

                    // Last paragraph as conclusion
                    podcastContent.addSegment(
                            new PodcastSegment("Conclusion", paragraphs[paragraphs.length - 1], PodcastSegment.SegmentType.CONCLUSION)
                    );
                } else {
                    // Just use as is
                    podcastContent.addSegment(
                            new PodcastSegment("Complete Podcast", fullText, PodcastSegment.SegmentType.NEWS_ARTICLE)
                    );
                }
            }

            // Ensure we have at least one segment
            if (podcastContent.getSegments().isEmpty()) {
                Log.w(TAG, "No segments created. Using fallback.");

                String sanitizedText = cleanTextForDisplay(markedText);
                podcastContent.addSegment(
                        new PodcastSegment("AI Generated Content", sanitizedText, PodcastSegment.SegmentType.NEWS_ARTICLE)
                );
            }

            return podcastContent;
        } catch (Exception e) {
            Log.e(TAG, "Error processing marked text: " + e.getMessage());

            String sanitizedText = cleanTextForDisplay(markedText);
            podcastContent.addSegment(
                    new PodcastSegment("AI Generated Content", sanitizedText, PodcastSegment.SegmentType.NEWS_ARTICLE)
            );

            return podcastContent;
        }
    }

    // Helper method to clean text for display
    private String cleanTextForDisplay(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Remove markers but preserve paragraphs
        return text.replace("§HOST§", "").trim();
    }
    // Helper method to sanitize content (ensure complete sentences)
    private String sanitizeContent(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Remove any incomplete sentences at the end
        text = text.trim();
        char lastChar = text.charAt(text.length() - 1);
        if (lastChar != '.' && lastChar != '!' && lastChar != '?') {
            // Find the last sentence end
            int lastSentenceEnd = Math.max(
                    text.lastIndexOf('.'),
                    Math.max(text.lastIndexOf('!'), text.lastIndexOf('?'))
            );

            if (lastSentenceEnd > 0) {
                text = text.substring(0, lastSentenceEnd + 1);
            } else {
                // If no sentence end found, add a period
                text = text + ".";
            }
        }

        return text;
    }
    // Generate a meaningful title from the segment content
    private String generateSegmentTitle(String segmentText) {
        try {
            // Extract first sentence as a base for the title
            int firstSentenceEnd = -1;
            for (int i = 0; i < Math.min(segmentText.length(), 100); i++) {
                char c = segmentText.charAt(i);
                if (c == '.' || c == '!' || c == '?') {
                    firstSentenceEnd = i;
                    break;
                }
            }

            if (firstSentenceEnd > 0) {
                String firstSentence = segmentText.substring(0, firstSentenceEnd + 1);

                // Extract first 4-6 words for title
                String[] words = firstSentence.split("\\s+");
                int wordLimit = Math.min(5, words.length);

                StringBuilder titleBuilder = new StringBuilder();
                for (int i = 0; i < wordLimit; i++) {
                    titleBuilder.append(words[i]).append(" ");
                }

                return titleBuilder.toString().trim() + "...";
            }

            // Fallback if we can't extract a good title
            return "News Segment";
        } catch (Exception e) {
            Log.e(TAG, "Error generating segment title: " + e.getMessage());
            return "News Segment";
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