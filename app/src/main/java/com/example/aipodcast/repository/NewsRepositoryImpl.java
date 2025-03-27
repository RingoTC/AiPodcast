package com.example.aipodcast.repository;

import android.content.Context;
import android.util.Log;

import com.example.aipodcast.database.dao.NewsDao;
import com.example.aipodcast.database.dao.SqliteNewsDao;
import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.service.NewsService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Implementation of NewsRepository that integrates network API and local storage
 */
public class NewsRepositoryImpl implements NewsRepository {
    private static final String TAG = "NewsRepositoryImpl";
    
    // Cache expiration time in milliseconds (30 minutes)
    private static final long CACHE_EXPIRATION_MS = 30 * 60 * 1000;
    
    // Maximum articles to keep per search
    private static final int MAX_ARTICLES_PER_SEARCH = 50;
    
    private final NewsService newsService;
    private final NewsDao newsDao;
    private final Executor dbExecutor;
    
    /**
     * Constructor
     * 
     * @param context Application context
     * @param newsService News service for network requests
     */
    public NewsRepositoryImpl(Context context, NewsService newsService) {
        this.newsService = newsService;
        this.newsDao = new SqliteNewsDao(context);
        this.dbExecutor = Executors.newSingleThreadExecutor();
    }
    
    /**
     * Search articles by keyword
     * @param keyword The search keyword
     * @return CompletableFuture with list of matching articles
     */
    public CompletableFuture<List<NewsArticle>> searchArticles(String keyword) {
        // First try to search in cache
        CompletableFuture<List<NewsArticle>> cachedResults = CompletableFuture.supplyAsync(() ->
            newsDao.searchArticles(keyword), dbExecutor);
            
        // Then fetch from network
        CompletableFuture<List<NewsArticle>> networkResults = newsService.searchArticles(keyword)
            .thenApplyAsync(articles -> {
                // Cache the results
                if (articles != null && !articles.isEmpty()) {
                    newsDao.insertArticles(articles, keyword);
                    
                    // Clean up old articles to prevent database from growing too large
                    newsDao.deleteOldArticles(MAX_ARTICLES_PER_SEARCH);
                }
                return articles;
            }, dbExecutor);
            
        // Combine results from both sources
        return cachedResults.thenCombine(networkResults, (cached, network) -> {
            if (network != null && !network.isEmpty()) {
                return network; // Prefer network results if available
            }
            return cached; // Fall back to cached results
        }).exceptionally(e -> {
            Log.e(TAG, "Error searching articles: " + e.getMessage());
            return null;
        });
    }


    
    /**
     * Get article details by URL
     * @param url The article URL
     * @return CompletableFuture with article details
     */
    public CompletableFuture<NewsArticle> getArticleDetails(String url) {
        // First check if article exists in local cache
        return CompletableFuture.supplyAsync(() -> newsDao.getArticleByUrl(url), dbExecutor)
                .thenCompose(cachedArticle -> {
                    if (cachedArticle != null) {
                        return CompletableFuture.completedFuture(cachedArticle);
                    }
                    
                    // If not in cache, fetch from network
                    return newsService.getArticleDetails(url)
                            .thenApplyAsync(article -> {
                                // Save to cache if fetch successful
                                if (article != null) {
                                    // Use the URL as the keyword for article details
                                    newsDao.insertArticle(article, url);
                                }
                                return article;
                            }, dbExecutor);
                });
    }
    
    /**
     * Check if there is cached data for a keyword
     * @param keyword The search keyword
     * @return true if cached data exists
     */
    public boolean hasCachedData(String keyword) {
        return newsDao.hasCachedArticles(keyword);
    }
    
    /**
     * Clear the cache for a specific keyword
     * @param keyword The search keyword
     */
    public CompletableFuture<Void> clearCache(String keyword) {
        return CompletableFuture.runAsync(() -> {
            newsDao.deleteArticlesByKeyword(keyword);
        }, dbExecutor);
    }
    
    /**
     * Clear all cached articles
     */
    public CompletableFuture<Void> clearAllCache() {
        return CompletableFuture.runAsync(() -> {
            newsDao.deleteAllArticles();
        }, dbExecutor);
    }
} 