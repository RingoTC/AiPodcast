package com.example.aipodcast;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aipodcast.models.Article;
import com.example.aipodcast.models.NewsResponse;
import com.example.aipodcast.network.NewsApiClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NewsListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private NewsAdapter adapter;
    private String topic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_news_list);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        topic = getIntent().getStringExtra("topic");
        fetchNews(topic);
    }

    private void fetchNews(String category) {
        String apiKey = "YOUR_API_KEY"; // 🔒 Replace this with your actual NewsAPI key
        String country = "us"; // or "gb", "ca", etc.

        NewsApiClient.getInstance().getTopHeadlines(category, country, apiKey)
                .enqueue(new Callback<NewsResponse>() {
                    @Override
                    public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Article> articles = response.body().getArticles();
                            adapter = new NewsAdapter(articles);
                            recyclerView.setAdapter(adapter);
                        } else {
                            Toast.makeText(NewsListActivity.this, "No articles found.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<NewsResponse> call, Throwable t) {
                        Toast.makeText(NewsListActivity.this, "Failed to fetch news", Toast.LENGTH_SHORT).show();
                        Log.e("NewsListActivity", "API Error", t);
                    }
                });
    }

}
