package com.example.aipodcast.service;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.NewsCategory;

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
    public CompletableFuture<List<NewsArticle>> getNewsByCategory(NewsCategory category) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = BASE_URL + "?section=" + category.getValue() 
                    + "&show-fields=bodyText,thumbnail"
                    + "&api-key=" + apiKey;

                Request request = new Request.Builder()
                        .url(url)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new IOException("Unexpected response " + response);

                    String responseData = response.body().string();
                    JSONObject json = new JSONObject(responseData);
                    JSONArray results = json.getJSONObject("response").getJSONArray("results");

                    List<NewsArticle> articles = new ArrayList<>();
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject result = results.getJSONObject(i);
                        articles.add(parseArticle(result));
                    }

                    return articles;
                }
            } catch (IOException | JSONException e) {
                throw new RuntimeException("Error fetching articles by category", e);
            }
        });
    }

    @Override
    public CompletableFuture<NewsArticle> getArticleDetails(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Guardian API expects the path after the domain
                String apiUrl = url.replace("https://www.theguardian.com", "https://content.guardianapis.com")
                    + "?show-fields=bodyText,thumbnail"
                    + "&api-key=" + apiKey;

                Request request = new Request.Builder()
                        .url(apiUrl)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new IOException("Unexpected response " + response);

                    String responseData = response.body().string();
                    JSONObject json = new JSONObject(responseData);
                    JSONObject content = json.getJSONObject("response").getJSONObject("content");

                    return parseArticle(content);
                }
            } catch (IOException | JSONException e) {
                throw new RuntimeException("Error fetching article details", e);
            }
        });
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
                    if (!response.isSuccessful()) throw new IOException("Unexpected response " + response);

                    String responseData = response.body().string();
                    JSONObject json = new JSONObject(responseData);
                    JSONArray results = json.getJSONObject("response").getJSONArray("results");

                    List<NewsArticle> articles = new ArrayList<>();
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject result = results.getJSONObject(i);
                        articles.add(parseArticle(result));
                    }

                    return articles;
                }
            } catch (IOException | JSONException e) {
                throw new RuntimeException("Error searching articles", e);
            }
        });
    }

    private NewsArticle parseArticle(JSONObject article) throws JSONException {
        String title = article.getString("webTitle");
        String url = article.getString("webUrl");
        String section = article.getString("sectionName");
        String publishedDate = article.getString("webPublicationDate");
        
        // Get abstract from bodyText field if available
        String abstract_ = "No description available";
        if (article.has("fields")) {
            JSONObject fields = article.getJSONObject("fields");
            if (fields.has("bodyText")) {
                String bodyText = fields.getString("bodyText");
                // Take first 200 characters as abstract
                abstract_ = bodyText.length() > 200 ? 
                    bodyText.substring(0, 200) + "..." : 
                    bodyText;
            }
        }

        return new NewsArticle(title, abstract_, url, section, publishedDate);
    }
} 