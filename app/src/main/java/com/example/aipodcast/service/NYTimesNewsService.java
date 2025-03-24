package com.example.aipodcast.service;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.NewsCategory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NYTimesNewsService implements NewsService {
    private static final String BASE_URL = "https://api.nytimes.com/svc/topstories/v2/";
    private final String apiKey;
    private final OkHttpClient client;

    public NYTimesNewsService(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient();
    }

    @Override
    public CompletableFuture<List<NewsArticle>> getNewsByCategory(NewsCategory category) {
        return CompletableFuture.supplyAsync(() -> {
            String url = BASE_URL + category.getValue() + ".json?api-key=" + apiKey;
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected response " + response);
                
                String responseData = response.body().string();
                JSONObject jsonObject = new JSONObject(responseData);
                JSONArray results = jsonObject.getJSONArray("results");
                
                List<NewsArticle> articles = new ArrayList<>();
                for (int i = 0; i < results.length(); i++) {
                    JSONObject article = results.getJSONObject(i);
                    articles.add(new NewsArticle(
                            article.getString("title"),
                            article.getString("abstract"),
                            article.getString("url"),
                            article.getString("section"),
                            article.getString("published_date")
                    ));
                }
                return articles;
            } catch (IOException | JSONException e) {
                throw new RuntimeException("Error fetching news", e);
            }
        });
    }

    @Override
    public CompletableFuture<NewsArticle> getArticleDetails(String url) {
        // Since NYTimes API doesn't provide a specific endpoint for article details,
        // we'll return the cached article data or fetch the whole list and find the article
        return CompletableFuture.supplyAsync(() -> {
            try {
                // This is a simplified implementation. In a real app, you might want to:
                // 1. Cache the articles
                // 2. Implement proper error handling
                // 3. Handle cases where the article isn't found
                throw new UnsupportedOperationException("Article details not implemented");
            } catch (Exception e) {
                throw new RuntimeException("Error fetching article details", e);
            }
        });
    }
}