package com.example.aipodcast;
import android.content.Context;
import android.content.Intent;
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
import com.example.aipodcast.service.SimplifiedTTSHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
public class InputActivity extends AppCompatActivity {
    private static final String TAG = "InputActivity";
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
    private SimplifiedTTSHelper ttsHelper;
    private com.google.android.material.floatingactionbutton.FloatingActionButton generatePodcastFab;
    private com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton selectModeButton;
    private boolean isPodcastMode = false;
    private NewsAdapter newsAdapter;
    private NewsRepository newsRepository;
    private List<NewsArticle> currentArticles = new ArrayList<>();
    private ArrayList<String> selectedTopics;
    private int duration;
    private int commuteDuration;
    private boolean useAIGeneration = true; 
    private ConnectivityManager connectivityManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        supportPostponeEnterTransition();
        setContentView(R.layout.activity_input);
        selectedTopics = getIntent().getStringArrayListExtra("selected_topics");
        duration = getIntent().getIntExtra("duration", 5);
        isPodcastMode = getIntent().getBooleanExtra("podcast_mode", false);
        useAIGeneration = getIntent().getBooleanExtra("use_ai_generation", true);
        newsRepository = NewsRepositoryProvider.getRepository(this);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        initializeViews();
        setupRecyclerView();
        setupListeners();
        setupSelectedTopics();
        updateUIForMode();
        animateUI();
        supportStartPostponedEnterTransition();
        if (selectedTopics != null && !selectedTopics.isEmpty()) {
            if (isNetworkAvailable()) {
                performSearch(selectedTopics.get(0));
            } else {
                showError("No internet connection. Please check your network settings and try again.");
                showEmptyState("No internet connection");
            }
        }
    }
    private void initializeViews() {
        logoContainer = findViewById(R.id.logo_container);
        logoSmall = findViewById(R.id.logo_small);
        searchTitle = findViewById(R.id.search_title);
        selectedTopicsChipGroup = findViewById(R.id.selected_topics_chip_group);
        progressBar = findViewById(R.id.progress_bar);
        newsRecyclerView = findViewById(R.id.news_recycler_view);
        emptyStateView = findViewById(R.id.empty_state_view);
        searchCard = findViewById(R.id.search_card);
        generatePodcastFab = findViewById(R.id.generate_podcast_fab);
        selectModeButton = findViewById(R.id.select_mode_button);
        if (isPodcastMode) {
            searchTitle.setText(String.format("Select News for %d Minute Podcast", duration));
        } else {
            searchTitle.setText(String.format("News for %d minute podcast", duration));
        }
    }
    private void updateUIForMode() {
        if (isPodcastMode) {
            selectModeButton.setVisibility(View.VISIBLE);
            generatePodcastFab.setVisibility(View.VISIBLE);
            generatePodcastFab.hide();
            generatePodcastFab.setOnClickListener(v -> handlePodcastGeneration());
            selectModeButton.setOnClickListener(v -> toggleSelectionMode());
        } else {
            if (selectModeButton != null) selectModeButton.setVisibility(View.GONE);
            if (generatePodcastFab != null) generatePodcastFab.setVisibility(View.GONE);
        }
    }
    private void toggleSelectionMode() {
        if (newsAdapter != null) {
            boolean newMode = !newsAdapter.isSelectMode();
            newsAdapter.setSelectMode(newMode);
            selectModeButton.setText(newMode ? "Cancel Selection" : "Select Articles");
            if (newMode) {
                newsAdapter.setOnSelectionChangedListener(selectedArticles -> {
                    if (selectedArticles != null && !selectedArticles.isEmpty()) {
                        generatePodcastFab.show();
                    } else {
                        generatePodcastFab.hide();
                    }
                });
            } else {
                generatePodcastFab.hide();
            }
        }
    }
    private void handlePodcastGeneration() {
        if (newsAdapter == null || !newsAdapter.isSelectMode()) {
            showError("Please select articles first");
            return;
        }
        Set<NewsArticle> selectedArticles = newsAdapter.getSelectedArticles();
        if (selectedArticles.isEmpty()) {
            showError("Please select at least one article");
            return;
        }
        try {
            Intent intent = new Intent(this, PodcastPlayerActivity.class);
            intent.putStringArrayListExtra("selected_topics", selectedTopics);
            intent.putExtra("duration", duration);
            Log.d(TAG, "Sending podcast duration to PodcastPlayerActivity: " + duration + " minutes");
            intent.putExtra("use_ai_generation", useAIGeneration);
            ArrayList<NewsArticle> articlesList = new ArrayList<>(selectedArticles);
            intent.putExtra("selected_articles_list", articlesList);
            startActivity(intent);
            newsAdapter.setSelectMode(false);
            selectModeButton.setText("Select Articles");
            generatePodcastFab.hide();
        } catch (Exception e) {
            Log.e(TAG, "Error launching PodcastPlayerActivity: " + e.getMessage());
            showError("Error launching podcast player: " + e.getMessage());
        }
    }
    private void animateUI() {
        searchCard.setTranslationY(50f);
        searchCard.setAlpha(0f);
        emptyStateView.setAlpha(0f);
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
    private void setupRecyclerView() {
        newsAdapter = new NewsAdapter(currentArticles, this::onArticleClicked);
        newsRecyclerView.setAdapter(newsAdapter);
        newsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        newsRecyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
    }
    private void setupListeners() {
        logoContainer.setOnClickListener(v -> finishAfterTransition());
    }
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
    private void onArticleClicked(NewsArticle article) {
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
    private boolean isNetworkAvailable() {
        if (connectivityManager == null) return false;
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }
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
            finishAfterTransition();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishAfterTransition();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ttsHelper != null) {
            ttsHelper.shutdown();
        }
    }
}