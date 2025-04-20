package com.example.aipodcast.service;
import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.util.Log;

import com.example.aipodcast.model.NewsArticle;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
public class GuardianNewsService implements NewsService {
    private static final String BASE_URL = "https://content.guardianapis.com/search";
    private final String apiKey;
    private final OkHttpClient client;
    public GuardianNewsService(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient();
    }
    @Override
    public CompletableFuture<List<NewsArticle>> searchArticles(String keyword) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String encodedKeyword = URLEncoder.encode(keyword, "UTF-8");
                String url = BASE_URL + "?q=" + encodedKeyword 
                    + "&show-fields=bodyText,thumbnail"
                    + "&api-key=" + apiKey;
                Request request = new Request.Builder()
                        .url(url)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No error body";
                        throw new IOException("API request failed with code " + response.code() 
                            + ", message: " + response.message()
                            + ", error body: " + errorBody);
                    }
                    String responseData = response.body().string();
                    JSONObject json = new JSONObject(responseData);
                    if (json.has("response") && json.getJSONObject("response").has("status")) {
                        String status = json.getJSONObject("response").getString("status");
                        if (!"ok".equals(status)) {
                            throw new IOException("API returned error status: " + status);
                        }
                    }
                    JSONArray results = json.getJSONObject("response").getJSONArray("results");
                    List<NewsArticle> articles = new ArrayList<>();
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject result = results.getJSONObject(i);
                        articles.add(parseArticle(result));
                    }
                    return articles;
                }
            } catch (IOException | JSONException e) {
                throw new RuntimeException("Error searching articles: " + e.getMessage(), e);
            }
        });
    }
    @Override
    public CompletableFuture<NewsArticle> getArticleDetails(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Add "show-blocks=all" to get more complete content
                String apiUrl = url.replace("https://www.theguardian.com", "https://content.guardianapis.com")
                        + "?show-fields=bodyText,thumbnail,byline,headline,trailText,main"
                        + "&show-blocks=all"
                        + "&api-key=" + apiKey;

                Log.d(TAG, "Requesting full article: " + apiUrl);

                Request request = new Request.Builder()
                        .url(apiUrl)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected response " + response);
                    }

                    String responseData = response.body().string();

                    // Log full API response for debugging
                    Log.d(TAG, "API Response preview (first 200 chars): " +
                            responseData.substring(0, Math.min(200, responseData.length())));

                    JSONObject json = new JSONObject(responseData);
                    JSONObject content = json.getJSONObject("response").getJSONObject("content");

                    NewsArticle article = extractFullArticleContent(content);

                    // Verify we got substantial content
                    Log.d(TAG, "Extracted article: " + article.getTitle() +
                            " - Abstract length: " + article.getAbstract().length() +
                            " - Full text length: " + article.getFullBodyText().length());

                    return article;
                }
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error fetching article details: " + e.getMessage());
                throw new RuntimeException("Error fetching article details", e);
            }
        });
    }

    private NewsArticle extractFullArticleContent(JSONObject article) throws JSONException {
        String title = article.getString("webTitle");
        String url = article.getString("webUrl");
        String section = article.getString("sectionName");
        String publishedDate = article.getString("webPublicationDate");
        String abstract_ = "No description available";
        StringBuilder fullBodyTextBuilder = new StringBuilder();

        try {
            // Try to extract full article content - first from bodyText
            if (article.has("fields")) {
                JSONObject fields = article.getJSONObject("fields");
                if (fields.has("bodyText")) {
                    String bodyText = fields.getString("bodyText");
                    fullBodyTextBuilder.append(bodyText);

                    // Create a shorter abstract for display
                    abstract_ = bodyText.length() > 200 ?
                            bodyText.substring(0, 200) + "..." :
                            bodyText;
                }
            }

            // Also try to extract from blocks if available
            if (article.has("blocks")) {
                JSONObject blocks = article.getJSONObject("blocks");
                if (blocks.has("body")) {
                    JSONArray bodyBlocks = blocks.getJSONArray("body");

                    // Loop through all body blocks to extract text
                    for (int i = 0; i < bodyBlocks.length(); i++) {
                        JSONObject block = bodyBlocks.getJSONObject(i);
                        if (block.has("bodyTextSummary")) {
                            fullBodyTextBuilder.append("\n\n").append(block.getString("bodyTextSummary"));
                        } else if (block.has("bodyText")) {
                            fullBodyTextBuilder.append("\n\n").append(block.getString("bodyText"));
                        } else if (block.has("body")) {
                            fullBodyTextBuilder.append("\n\n").append(block.getString("body"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting full content: " + e.getMessage(), e);
        }

        String fullBodyText = fullBodyTextBuilder.toString().trim();

        // If we couldn't get full text but have an abstract, use that
        if (fullBodyText.isEmpty() && !abstract_.equals("No description available")) {
            fullBodyText = abstract_;
        }

        // Log the result
        Log.d(TAG, "Article extraction result - Title: " + title +
                ", Abstract length: " + abstract_.length() +
                ", Full text length: " + fullBodyText.length());

        // Always use the constructor with fullBodyText
        return new NewsArticle(title, abstract_, url, section, publishedDate, fullBodyText);
    }
    private NewsArticle parseArticle(JSONObject article) throws JSONException {
        String title = article.getString("webTitle");
        String url = article.getString("webUrl");
        String section = article.getString("sectionName");
        String publishedDate = article.getString("webPublicationDate");
        String abstract_ = "No description available";
        String fullBodyText = ""; // Store full body text

        if (article.has("fields")) {
            JSONObject fields = article.getJSONObject("fields");
            if (fields.has("bodyText")) {
                String bodyText = fields.getString("bodyText");

                // Store the FULL body text without truncation
                fullBodyText = bodyText;

                // Create a shorter abstract for display only
                abstract_ = bodyText.length() > 200 ?
                        bodyText.substring(0, 200) + "..." :
                        bodyText;

                // Log clear info about the content we're extracting
                Log.d(TAG, "Article: " + title);
                Log.d(TAG, "Abstract length: " + abstract_.length() + " chars");
                Log.d(TAG, "Full text length: " + fullBodyText.length() + " chars");
            }
        }

        // Always return an article with the fullBodyText field populated
        return new NewsArticle(title, abstract_, url, section, publishedDate, fullBodyText);
    }
}