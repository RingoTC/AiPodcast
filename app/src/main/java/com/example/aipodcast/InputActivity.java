package com.example.aipodcast;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aipodcast.adapter.NewsAdapter;
import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.repository.NewsRepository;
import com.example.aipodcast.repository.NewsRepositoryProvider;
import com.example.aipodcast.service.TextToSpeechHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Activity for searching news articles based on keywords and displaying the results.
 * Implements a clean architecture approach by leveraging the repository pattern.
 */
public class InputActivity extends AppCompatActivity {

    private static final String TAG = "InputActivity";

    // UI components
    private MaterialCardView logoContainer;
    private ImageView logoSmall;
    private TextView searchTitle;
    private ChipGroup selectedTopicsChipGroup;
    private ProgressBar progressBar;
    private RecyclerView newsRecyclerView;
    private TextView emptyStateView;
    private MaterialCardView searchCard;
    private TextView timeLabel;
    private int selectedTime = 0;
    private TextToSpeechHelper ttsHelper;

    // Data
    private NewsAdapter newsAdapter;
    private NewsRepository newsRepository;
    private List<NewsArticle> currentArticles = new ArrayList<>();
    private ArrayList<String> selectedTopics;
    private int duration;

    // Network
    private ConnectivityManager connectivityManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide action bar to keep consistent with MainActivity
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Enable shared element transition
        supportPostponeEnterTransition();

        setContentView(R.layout.activity_input);

        // Get selected topics from intent
        selectedTopics = getIntent().getStringArrayListExtra("selected_topics");
        duration = getIntent().getIntExtra("duration", 5);

        // Initialize repository and connectivity manager
        newsRepository = NewsRepositoryProvider.getRepository(this);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        // Initialize UI components
        initializeViews();
        setupRecyclerView();
        setupListeners();
        setupSelectedTopics();

        // Apply animations
        animateUI();

        // Complete transition
        supportStartPostponedEnterTransition();

        // Automatically search for the first topic
        if (selectedTopics != null && !selectedTopics.isEmpty()) {
            if (isNetworkAvailable()) {
                performSearch(selectedTopics.get(0));
            } else {
                showError("No internet connection. Please check your network settings and try again.");
                showEmptyState("No internet connection");
            }
        }
    }

    /**
     * Initialize and bind all UI views from layout
     */
    private void initializeViews() {
        logoContainer = findViewById(R.id.logo_container);
        logoSmall = findViewById(R.id.logo_small);
        searchTitle = findViewById(R.id.search_title);
        selectedTopicsChipGroup = findViewById(R.id.selected_topics_chip_group);
        progressBar = findViewById(R.id.progress_bar);
        newsRecyclerView = findViewById(R.id.news_recycler_view);
        emptyStateView = findViewById(R.id.empty_state_view);
        searchCard = findViewById(R.id.search_card);

        // Update search title to show duration
        searchTitle.setText(String.format("News for %d minute podcast", duration));
    }

    /**
     * Apply entrance animations to UI elements
     */
    private void animateUI() {
        // Prepare animations (except for shared elements)
        searchCard.setTranslationY(50f);
        searchCard.setAlpha(0f);

        emptyStateView.setAlpha(0f);

        // Only animate views that aren't part of the shared element transition
        searchCard.animate()
                .translationY(0f)
                .alpha(1f)
                .setStartDelay(300)
                .setDuration(400)
                .start();

        emptyStateView.animate()
                .alpha(1f)
                .setStartDelay(400)
                .setDuration(300)
                .start();
    }

    /**
     * Setup RecyclerView with adapter and layout manager
     */
    private void setupRecyclerView() {
        newsAdapter = new NewsAdapter(currentArticles, this::onArticleClicked);
        newsRecyclerView.setAdapter(newsAdapter);
        newsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        newsRecyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
    }

    /**
     * Setup click listeners for interactive elements
     */
    private void setupListeners() {
        // Back navigation
        logoContainer.setOnClickListener(v -> finishAfterTransition());
    }

    /**
     * Setup selected topics chips
     */
    private void setupSelectedTopics() {
        if (selectedTopics != null) {
            for (String topic : selectedTopics) {
                Chip chip = new Chip(this);
                chip.setText(topic);
                chip.setClickable(false);
                chip.setCheckable(false);
                selectedTopicsChipGroup.addView(chip);
            }
        }
    }

    /**
     * Handle user clicks on news articles
     */
    private void onArticleClicked(NewsArticle article) {
        // Open article in Custom Chrome Tab
        try {
            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
                    .setToolbarColor(getResources().getColor(R.color.purple_500, getTheme()))
                    .setShowTitle(true)
                    .build();

            customTabsIntent.launchUrl(this, Uri.parse(article.getUrl()));
        } catch (Exception e) {
            Log.e(TAG, "Error opening article: " + e.getMessage());
            Toast.makeText(this, "Error opening article", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Check if network is available
     */
    private boolean isNetworkAvailable() {
        if (connectivityManager == null) return false;
        
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    /**
     * Perform the news search
     */
    private void performSearch(String keyword) {
        if (!isNetworkAvailable()) {
            showError("No internet connection. Please check your network settings and try again.");
            showEmptyState("No internet connection");
            return;
        }

        setLoadingState(true);
        Log.d(TAG, "Searching for keyword: " + keyword);

        try {
            CompletableFuture<List<NewsArticle>> future = newsRepository.searchArticles(keyword);

            future.thenAccept(articles -> runOnUiThread(() -> {
                setLoadingState(false);
                if (articles != null && !articles.isEmpty()) {
                    showResults(articles);
                } else {
                    showEmptyState("No articles found for: " + keyword);
                }
            })).exceptionally(e -> {
                Log.e(TAG, "Error searching articles: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    setLoadingState(false);
                    String errorMsg;
                    if (e.getCause() instanceof UnknownHostException) {
                        errorMsg = "Unable to connect to news server. Please check your internet connection and try again.";
                    } else {
                        errorMsg = "Error loading news. Please try again later.";
                    }
                    showError(errorMsg);
                    showEmptyState("Connection error");
                });
                return null;
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing search: " + e.getMessage(), e);
            setLoadingState(false);
            showError("Unable to initialize search. Please try again.");
            showEmptyState("Error occurred");
        }
    }

    private void setLoadingState(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        emptyStateView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        newsRecyclerView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    private void showResults(List<NewsArticle> articles) {
        currentArticles.clear();
        currentArticles.addAll(articles);
        newsAdapter.notifyDataSetChanged();
        emptyStateView.setVisibility(View.GONE);
        newsRecyclerView.setVisibility(View.VISIBLE);
    }

    private void showEmptyState(String message) {
        currentArticles.clear();
        newsAdapter.notifyDataSetChanged();
        emptyStateView.setText(message);
        emptyStateView.setVisibility(View.VISIBLE);
        newsRecyclerView.setVisibility(View.GONE);
    }

    private void showError(String message) {
        Snackbar snackbar = Snackbar.make(searchCard, message, Snackbar.LENGTH_LONG);
        snackbar.setAction("Retry", v -> {
            if (selectedTopics != null && !selectedTopics.isEmpty()) {
                performSearch(selectedTopics.get(0));
            }
        });
        snackbar.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Handle the back button press with transition animation
            finishAfterTransition();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Handle back button with proper transition
        super.onBackPressed();
        finishAfterTransition();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ttsHelper != null) {
            ttsHelper.shutdown();
        }
        // Clean up resources if needed
    }
}
