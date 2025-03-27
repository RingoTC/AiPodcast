package com.example.aipodcast.repository;

import com.example.aipodcast.model.NewsArticle;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.example.aipodcast.model.NewsCategory;

/**
 * Repository interface for accessing news data
 */
public interface NewsRepository {
    /**
     * Search articles by keyword
     * @param keyword The search keyword
     * @return CompletableFuture with list of matching articles
     */
    CompletableFuture<List<NewsArticle>> searchArticles(String keyword);

    CompletableFuture<List<NewsArticle>> searchArticles(String keyword, NewsCategory category);
    
    /**
     * Get article details by URL
     * @param url The article URL
     * @return CompletableFuture with article details
     */
    CompletableFuture<NewsArticle> getArticleDetails(String url);
    
    /**
     * Check if there is cached data for a keyword
     * @param keyword The search keyword
     * @return true if cached data exists
     */
    boolean hasCachedData(String keyword);
    
    /**
     * Clear the cache for a specific keyword
     * @param keyword The search keyword
     */
    CompletableFuture<Void> clearCache(String keyword);
    
    /**
     * Clear all cached articles
     */
    CompletableFuture<Void> clearAllCache();
} 