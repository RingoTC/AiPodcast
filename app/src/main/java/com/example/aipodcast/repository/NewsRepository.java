package com.example.aipodcast.repository;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.NewsCategory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for accessing news data
 * Acts as a single source of truth for news data, abstracting the data source
 * (local database or network API)
 */
public interface NewsRepository {
    
    /**
     * Get news articles by category
     * First checks local cache, fetches from network if cache is empty or stale
     * 
     * @param category News category to fetch
     * @return CompletableFuture that resolves to a list of news articles
     */
    CompletableFuture<List<NewsArticle>> getNewsByCategory(NewsCategory category);
    
    /**
     * Get news articles by category from local cache only
     * Useful for offline mode or when network requests should be avoided
     * 
     * @param category News category to fetch
     * @return CompletableFuture that resolves to a list of news articles
     */
    CompletableFuture<List<NewsArticle>> getNewsByCategoryFromCache(NewsCategory category);
    
    /**
     * Force refresh news articles from network and update local cache
     * 
     * @param category News category to fetch
     * @return CompletableFuture that resolves to a list of news articles
     */
    CompletableFuture<List<NewsArticle>> refreshNewsByCategory(NewsCategory category);
    
    /**
     * Get article details by URL
     * Checks local cache first, then fetches from network if not found
     * 
     * @param url Article URL
     * @return CompletableFuture that resolves to article details
     */
    CompletableFuture<NewsArticle> getArticleDetails(String url);
    
    /**
     * Get category for a topic string (used to map UI topics to API categories)
     * 
     * @param topic Topic string from UI (e.g., "Technology", "Sports")
     * @return Corresponding NewsCategory for API calls
     */
    NewsCategory getCategoryForTopic(String topic);
    
    /**
     * Clear cache for a specific category
     * 
     * @param category Category to clear cache for
     * @return CompletableFuture that completes when cache is cleared
     */
    CompletableFuture<Void> clearCategoryCache(NewsCategory category);
    
    /**
     * Check if there is cached data for a category
     * 
     * @param category Category to check
     * @return true if cache exists, false otherwise
     */
    boolean hasCachedData(NewsCategory category);
} 