package com.example.aipodcast.integration;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.NewsCategory;
import com.example.aipodcast.repository.NewsRepository;
import com.example.aipodcast.repository.NewsRepositoryProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Sample integration class to demonstrate how to use the NewsRepository
 * in the NewsListActivity
 * 
 * This is a reference implementation that colleagues can use as a guide
 * to integrate the local storage solution with their UI components.
 */
public class NewsListIntegration {
    private static final String TAG = "NewsListIntegration";
    
    private final Context context;
    private final NewsRepository repository;
    
    // List adapter (this would be provided by your colleague)
    private RecyclerView.Adapter<?> newsAdapter;
    
    // News data
    private List<NewsArticle> newsList = new ArrayList<>();
    
    /**
     * Constructor
     * 
     * @param context Application context
     */
    public NewsListIntegration(Context context) {
        this.context = context;
        this.repository = NewsRepositoryProvider.getRepository(context);
    }
    
    /**
     * Set the adapter that will be updated with news data
     * 
     * @param adapter The RecyclerView adapter
     */
    public void setAdapter(RecyclerView.Adapter<?> adapter) {
        this.newsAdapter = adapter;
    }
    
    /**
     * Load news articles for a specific topic
     * 
     * @param topic Topic string from UI (e.g., "Technology", "Sports")
     */
    public void loadNewsForTopic(String topic) {
        // Show loading state (colleague would implement this)
        showLoading(true);
        
        // Get corresponding API category for UI topic
        NewsCategory category = repository.getCategoryForTopic(topic);
        
        // Get news from repository (cached if available, otherwise from network)
        repository.getNewsByCategory(category)
                .thenAccept(articles -> {
                    // Update news list
                    newsList.clear();
                    if (articles != null) {
                        newsList.addAll(articles);
                    }
                    
                    // Update UI on main thread
                    updateUI();
                })
                .exceptionally(e -> {
                    // Handle error
                    Log.e(TAG, "Error loading news: " + e.getMessage());
                    showError("Error loading news: " + e.getMessage());
                    return null;
                });
    }
    
    /**
     * Force refresh news from network
     * 
     * @param topic Topic string from UI
     */
    public void refreshNewsForTopic(String topic) {
        // Show loading state
        showLoading(true);
        
        // Get corresponding API category for UI topic
        NewsCategory category = repository.getCategoryForTopic(topic);
        
        // Force refresh from network
        repository.refreshNewsByCategory(category)
                .thenAccept(articles -> {
                    // Update news list
                    newsList.clear();
                    if (articles != null) {
                        newsList.addAll(articles);
                    }
                    
                    // Update UI on main thread
                    updateUI();
                })
                .exceptionally(e -> {
                    // Handle error
                    Log.e(TAG, "Error refreshing news: " + e.getMessage());
                    showError("Error refreshing news: " + e.getMessage());
                    return null;
                });
    }
    
    /**
     * Get article details by URL
     * 
     * @param url Article URL
     * @return CompletableFuture that resolves to article details
     */
    public CompletableFuture<NewsArticle> getArticleDetails(String url) {
        return repository.getArticleDetails(url);
    }
    
    /**
     * Check if there is cached data for a topic
     * 
     * @param topic Topic string from UI
     * @return true if cache exists, false otherwise
     */
    public boolean hasCachedData(String topic) {
        NewsCategory category = repository.getCategoryForTopic(topic);
        return repository.hasCachedData(category);
    }
    
    // UI helper methods (these would be implemented by your colleague)
    
    private void updateUI() {
        // This is just a mock implementation
        // Your colleague would implement the actual UI update
        if (newsAdapter != null) {
            newsAdapter.notifyDataSetChanged();
        }
    }
    
    private void showLoading(boolean loading) {
        // Mock implementation
        Log.d(TAG, "Loading: " + loading);
    }
    
    private void showError(String message) {
        // Mock implementation
        Log.e(TAG, message);
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
} 