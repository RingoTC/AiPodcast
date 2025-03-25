package com.example.aipodcast.service;

import android.content.Context;
import android.util.Log;

import com.example.aipodcast.database.dao.NewsDao;
import com.example.aipodcast.database.dao.SqliteNewsDao;
import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.NewsCategory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Wrapper for NewsService that adds caching functionality
 */
public class NewsServiceWrapper implements NewsService {
    private static final String TAG = "NewsServiceWrapper";
    
    private final NewsService newsService;
    private final NewsDao newsDao;
    private final Executor dbExecutor;
    
    /**
     * Constructor
     * 
     * @param context Application context
     * @param newsService The actual NewsService implementation to wrap
     */
    public NewsServiceWrapper(Context context, NewsService newsService) {
        this.newsService = newsService;
        this.newsDao = new SqliteNewsDao(context);
        this.dbExecutor = Executors.newSingleThreadExecutor();
    }
    
    @Override
    public CompletableFuture<List<NewsArticle>> getNewsByCategory(NewsCategory category) {
        // First check cache
        CompletableFuture<List<NewsArticle>> cachedResults = CompletableFuture.supplyAsync(() ->
            newsDao.getArticlesByCategory(category), dbExecutor);
            
        // Then fetch from network
        CompletableFuture<List<NewsArticle>> networkResults = newsService.getNewsByCategory(category)
            .thenApplyAsync(articles -> {
                // Cache the results
                if (articles != null && !articles.isEmpty()) {
                    newsDao.insertArticles(articles, category.getValue());
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
            Log.e(TAG, "Error getting news by category: " + e.getMessage());
            return null;
        });
    }
    
    @Override
    public CompletableFuture<List<NewsArticle>> searchArticles(String keyword) {
        // First check cache
        CompletableFuture<List<NewsArticle>> cachedResults = CompletableFuture.supplyAsync(() ->
            newsDao.searchArticles(keyword), dbExecutor);
            
        // Then fetch from network
        CompletableFuture<List<NewsArticle>> networkResults = newsService.searchArticles(keyword)
            .thenApplyAsync(articles -> {
                // Cache the results
                if (articles != null && !articles.isEmpty()) {
                    newsDao.insertArticles(articles, keyword);
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
    
    @Override
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
                                    newsDao.insertArticle(article, url);
                                }
                                return article;
                            }, dbExecutor);
                });
    }
    
    /**
     * Get news articles by category from cache only
     * Not part of NewsService interface, but useful for offline access
     * 
     * @param category News category to fetch
     * @return CompletableFuture that resolves to a list of news articles
     */
    public CompletableFuture<List<NewsArticle>> getNewsByCategoryFromCache(NewsCategory category) {
        return CompletableFuture.supplyAsync(() -> newsDao.getArticlesByCategory(category), dbExecutor);
    }
    
    /**
     * Check if there are any cached articles for a category
     * 
     * @param category The category to check
     * @return true if cached articles exist, false otherwise
     */
    public boolean hasCachedArticles(NewsCategory category) {
        return newsDao.hasCachedArticles(category.getValue());
    }
} 