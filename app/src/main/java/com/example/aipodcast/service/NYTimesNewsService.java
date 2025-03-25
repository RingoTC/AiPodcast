package com.example.aipodcast.service;

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

public class NYTimesNewsService {
    private static final String BASE_URL = "https://api.nytimes.com/svc/search/v2/articlesearch.json";
    private final String apiKey;
    private final OkHttpClient client;

    public NYTimesNewsService(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient();
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
                        articles.add(new NewsArticle(
                                doc.getString("headline").isEmpty() ? "No title" : doc.getJSONObject("headline").getString("main"),
                                doc.optString("snippet", "No description"),
                                doc.getString("web_url"),
                                doc.optString("section_name", "Unknown"),
                                doc.optString("pub_date", "Unknown")
                        ));
                    }

                    return articles;
                }
            } catch (IOException | JSONException e) {
                throw new RuntimeException("Error fetching articles", e);
            }
        });
    }
}
