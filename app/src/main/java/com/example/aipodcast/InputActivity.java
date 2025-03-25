package com.example.aipodcast;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.example.aipodcast.model.NewsCategory;
import com.example.aipodcast.repository.NewsRepository;
import com.example.aipodcast.repository.NewsRepositoryProvider;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private EditText keywordInput;
    private ChipGroup categoryChipGroup;
    private ProgressBar progressBar;
    private RecyclerView newsRecyclerView;
    private TextView emptyStateView;
    private Button submitButton;
    private MaterialCardView searchCard;

    // Data
    private NewsAdapter newsAdapter;
    private NewsRepository newsRepository;
    private List<NewsArticle> currentArticles = new ArrayList<>();
    private Map<Integer, NewsCategory> chipCategoryMap = new HashMap<>();

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

        // Initialize repository
        newsRepository = NewsRepositoryProvider.getRepository(this);
        
        // Initialize UI components
        initializeViews();
        setupRecyclerView();
        setupCategoryChips();
        setupListeners();
        
        // Apply animations
        animateUI();
        
        // Complete transition
        supportStartPostponedEnterTransition();
    }

    /**
     * Initialize and bind all UI views from layout
     */
    private void initializeViews() {
        logoContainer = findViewById(R.id.logo_container);
        logoSmall = findViewById(R.id.logo_small);
        searchTitle = findViewById(R.id.search_title);
        keywordInput = findViewById(R.id.input_keyword);
        categoryChipGroup = findViewById(R.id.category_chip_group);
        progressBar = findViewById(R.id.progress_bar);
        newsRecyclerView = findViewById(R.id.news_recycler_view);
        emptyStateView = findViewById(R.id.empty_state_view);
        submitButton = findViewById(R.id.btn_submit);
        searchCard = findViewById(R.id.search_card);
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
     * Setup category chips with corresponding news categories
     */
    private void setupCategoryChips() {
        chipCategoryMap.put(R.id.chip_technology, NewsCategory.TECHNOLOGY);
        chipCategoryMap.put(R.id.chip_entertainment, NewsCategory.ENTERTAINMENT);
        chipCategoryMap.put(R.id.chip_sports, NewsCategory.SPORTS);
        chipCategoryMap.put(R.id.chip_health, NewsCategory.HEALTH);
        chipCategoryMap.put(R.id.chip_politics, NewsCategory.POLITICS);
    }

    /**
     * Setup click listeners for interactive elements
     */
    private void setupListeners() {
        // Back navigation
        logoContainer.setOnClickListener(v -> finishAfterTransition());
        
        submitButton.setOnClickListener(v -> performSearch());
        
        // Add listener for category selection
        categoryChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                int selectedChipId = checkedIds.get(0);
                Log.d(TAG, "Selected category: " + chipCategoryMap.get(selectedChipId));
                
                // Auto-search when category changes if there's already a keyword
                String keyword = keywordInput.getText().toString().trim();
                if (!keyword.isEmpty()) {
                    performSearch();
                }
            }
        });
        
        // Add keyboard search action
        keywordInput.setOnEditorActionListener((v, actionId, event) -> {
            performSearch();
            return true;
        });
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
     * Perform the news search based on user input
     */
    private void performSearch() {
        String keyword = keywordInput.getText().toString().trim();

        if (keyword.isEmpty()) {
            showError("Please enter a keyword");
            return;
        }

        // Show loading state
        setLoadingState(true);
        
        // Get selected category (if any)
        NewsCategory selectedCategory = null;
        int checkedChipId = categoryChipGroup.getCheckedChipId();
        if (checkedChipId != View.NO_ID) {
            selectedCategory = chipCategoryMap.get(checkedChipId);
            Log.d(TAG, "Searching for keyword: " + keyword + " in category: " + selectedCategory);
        } else {
            Log.d(TAG, "Searching for keyword: " + keyword + " in all categories");
        }
        
        // Check if we have cached results first
        if (newsRepository.hasCachedData(keyword)) {
            Log.d(TAG, "Using cached data for: " + keyword);
            Snackbar.make(keywordInput, "Using cached results", Snackbar.LENGTH_SHORT).show();
        }

        // Perform search using repository
        final NewsCategory finalSelectedCategory = selectedCategory;
        CompletableFuture<List<NewsArticle>> future = newsRepository.searchArticles(keyword);
        
        future.thenAccept(articles -> runOnUiThread(() -> {
            setLoadingState(false);
            
            if (articles == null || articles.isEmpty()) {
                showEmptyState("No news found for \"" + keyword + "\"");
            } else {
                // Filter by category if selected
                if (finalSelectedCategory != null) {
                    List<NewsArticle> filteredArticles = new ArrayList<>();
                    for (NewsArticle article : articles) {
                        if (finalSelectedCategory.getValue().equalsIgnoreCase(article.getSection())) {
                            filteredArticles.add(article);
                        }
                    }
                    
                    if (filteredArticles.isEmpty()) {
                        showEmptyState("No " + finalSelectedCategory.getValue() + " news found for \"" + keyword + "\"");
                    } else {
                        showResults(filteredArticles);
                    }
                } else {
                    showResults(articles);
                }
            }
        })).exceptionally(e -> {
            runOnUiThread(() -> {
                setLoadingState(false);
                
                Throwable cause = e.getCause();
                if (cause instanceof UnknownHostException) {
                    showError("Network error. Please check your connection");
                } else {
                    showError("Error: " + e.getMessage());
                }
                
                Log.e(TAG, "Search error", e);
            });
            return null;
        });
    }

    /**
     * Display search results in the RecyclerView
     */
    private void showResults(List<NewsArticle> articles) {
        emptyStateView.setVisibility(View.GONE);
        newsRecyclerView.setVisibility(View.VISIBLE);
        
        // Add animation for results
        newsRecyclerView.setAlpha(0f);
        newsRecyclerView.animate()
                .alpha(1f)
                .setDuration(300)
                .start();
        
        currentArticles.clear();
        currentArticles.addAll(articles);
        newsAdapter.notifyDataSetChanged();
        
        // Scroll to top
        if (!articles.isEmpty()) {
            newsRecyclerView.smoothScrollToPosition(0);
        }
    }

    /**
     * Show empty state with custom message
     */
    private void showEmptyState(String message) {
        newsRecyclerView.setVisibility(View.GONE);
        emptyStateView.setVisibility(View.VISIBLE);
        emptyStateView.setText(message);
        
        // Fade in animation
        emptyStateView.setAlpha(0f);
        emptyStateView.animate()
                .alpha(1f)
                .setDuration(300)
                .start();
    }

    /**
     * Show error message to user
     */
    private void showError(String message) {
        Snackbar.make(keywordInput, message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(getResources().getColor(R.color.purple_700, getTheme()))
                .setTextColor(getResources().getColor(R.color.white, getTheme()))
                .show();
    }

    /**
     * Toggle loading state UI elements
     */
    private void setLoadingState(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!isLoading);
        keywordInput.setEnabled(!isLoading);
        
        // Add animation for progress bar
        if (isLoading) {
            progressBar.setScaleX(0f);
            progressBar.setScaleY(0f);
            progressBar.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .start();
        }
        
        // Disable category selection during loading
        for (int i = 0; i < categoryChipGroup.getChildCount(); i++) {
            View child = categoryChipGroup.getChildAt(i);
            if (child instanceof Chip) {
                child.setEnabled(!isLoading);
            }
        }
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
        finishAfterTransition();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up resources if needed
    }
}
