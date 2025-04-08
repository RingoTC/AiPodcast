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

public class NYTimesNewsService implements NewsService {
    private static final String BASE_URL = "https://api.nytimes.com/svc/search/v2/articlesearch.json";
    private final String apiKey;
    private final OkHttpClient client;

    public NYTimesNewsService(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient();
    }

    @Override
    public CompletableFuture<List<NewsArticle>> getNewsByCategory(NewsCategory category) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String query = "section_name:" + category.getValue().toLowerCase();
                String encodedQuery = URLEncoder.encode(query, "UTF-8");
                String url = BASE_URL + "?fq=" + encodedQuery + "&api-key=" + apiKey;

                Request request = new Request.Builder()
                        .url(url)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body().string();
                        System.out.println("Error response: " + errorBody);
                        throw new IOException("Unexpected response " + response);
                    }

                    String responseData = response.body().string();
                    System.out.println("API Response: " + responseData);
                    
                    JSONObject json = new JSONObject(responseData);
                    if (!json.has("response")) {
                        throw new JSONException("Response object not found in JSON");
                    }
                    
                    JSONObject responseObj = json.getJSONObject("response");
                    if (!responseObj.has("docs")) {
                        throw new JSONException("Docs array not found in response");
                    }
                    
                    JSONArray docs = responseObj.getJSONArray("docs");
                    List<NewsArticle> articles = new ArrayList<>();
                    for (int i = 0; i < docs.length(); i++) {
                        JSONObject doc = docs.getJSONObject(i);
                        articles.add(parseArticle(doc));
                    }

                    return articles;
                }
            } catch (IOException | JSONException e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Error fetching articles by category", e);
            }
        });
    }

    @Override
    public CompletableFuture<NewsArticle> getArticleDetails(String url) {
        return CompletableFuture.supplyAsync(() -> {
            throw new UnsupportedOperationException("Article details not supported for NYTimes API");
        });
    }

    public CompletableFuture<List<NewsArticle>> searchArticles(String keyword) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String encodedKeyword = URLEncoder.encode(keyword, "UTF-8");
                String url = BASE_URL + "?q=" + encodedKeyword + "&api-key=" + apiKey;

                Request request = new Request.Builder()
                        .url(url)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new IOException("Unexpected response " + response);

                    String responseData = response.body().string();
                    JSONObject json = new JSONObject(responseData);
                    JSONArray docs = json.getJSONObject("response").getJSONArray("docs");

                    List<NewsArticle> articles = new ArrayList<>();
                    for (int i = 0; i < docs.length(); i++) {
                        JSONObject doc = docs.getJSONObject(i);
                        articles.add(parseArticle(doc));
                    }

                    return articles;
                }
            } catch (IOException | JSONException e) {
                throw new RuntimeException("Error searching articles", e);
            }
        });
    }

    private NewsArticle parseArticle(JSONObject doc) throws JSONException {
        return new NewsArticle(
            doc.getJSONObject("headline").getString("main"),
            doc.optString("abstract", "No description available"),
            doc.getString("web_url"),
            doc.optString("section_name", "Unknown"),
            doc.optString("pub_date", "Unknown")
        );
    }
}
