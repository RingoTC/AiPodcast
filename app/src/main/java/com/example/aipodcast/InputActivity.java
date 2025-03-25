package com.example.aipodcast;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.service.NYTimesNewsService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class InputActivity extends AppCompatActivity {

    EditText keywordInput;
    RadioGroup newsTypeGroup;
    TextView responseOutput;
    Button submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_input);

        // Link views
        keywordInput = findViewById(R.id.input_keyword);
        newsTypeGroup = findViewById(R.id.news_type_group);
        responseOutput = findViewById(R.id.response_output);
        submitButton = findViewById(R.id.btn_submit);

        submitButton.setOnClickListener(v -> {
            String keyword = keywordInput.getText().toString().trim();

            if (keyword.isEmpty()) {
                responseOutput.setText("Please enter a keyword.");
                return;
            }

            responseOutput.setText("Loading news...");

            Log.d("API_KEY_TEST", "NYT Key = [" + BuildConfig.NYT_ARTICLE_API_KEY + "]");

            // Initialize news service with API key
            NYTimesNewsService service = new NYTimesNewsService(BuildConfig.NYT_ARTICLE_API_KEY);

            // Perform search
            CompletableFuture<List<NewsArticle>> future = service.searchArticles(keyword);
            future.thenAccept(articles -> runOnUiThread(() -> {
                if (articles.isEmpty()) {
                    responseOutput.setText("No news found.");
                } else {
                    StringBuilder result = new StringBuilder();
                    for (NewsArticle article : articles) {
                        result.append("• ")
                                .append(article.getTitle())
                                .append("\n")
                                .append(article.getAbstract())
                                .append("\n")
                                .append(article.getPublishedDate())
                                .append("\n\n");
                    }
                    responseOutput.setText(result.toString());
                }
            })).exceptionally(e -> {
                runOnUiThread(() -> responseOutput.setText("Error: " + e.getMessage()));
                return null;
            });
        });
    }
}
