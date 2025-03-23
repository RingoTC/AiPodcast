package com.example.aipodcast;


import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.List;

public class NewsListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_news_list);
//
//        // Get the selected topic from the intent
//        String topic = getIntent().getStringExtra("TOPIC");
//
//        // Set up RecyclerView
//        RecyclerView recyclerView = findViewById(R.id.recyclerViewNews);
//        recyclerView.setLayoutManager(new LinearLayoutManager(this));
//
//        // Get news articles for the selected topic
//        List<String> newsList = getNewsForTopic(topic);
//
//        // Set up adapter
//        NewsAdapter adapter = new NewsAdapter(newsList);
//        recyclerView.setAdapter(adapter);
    }

    // Method to get news articles for a specific topic
    private List<String> getNewsForTopic(String topic) {
        switch (topic) {
            case "Technology":
                return Arrays.asList("Tech News 1", "Tech News 2", "Tech News 3");
            case "Sports":
                return Arrays.asList("Sports News 1", "Sports News 2", "Sports News 3");
            case "Entertainment":
                return Arrays.asList("Entertainment News 1", "Entertainment News 2", "Entertainment News 3");
            case "Health":
                return Arrays.asList("Health News 1", "Health News 2", "Health News 3");
            case "Politics":
                return Arrays.asList("Politics News 1", "Politics News 2", "Politics News 3");
            default:
                return Arrays.asList("No news available");
        }
    }
}
