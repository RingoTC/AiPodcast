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
 * Wrapper for NewsService that adds caching capabilities
 * Maintains the same interface as NewsService while enhancing functionality
 */
public class NewsServiceWrapper implements NewsService {
    private static final String TAG = "NewsServiceWrapper";
    
    private final NewsService wrappedService;
    private final NewsDao newsDao;
    private final Executor dbExecutor;
    
    /**
     * Constructor
     * 
     * @param context Application context
     * @param wrappedService The actual NewsService implementation to wrap
     */
    public NewsServiceWrapper(Context context, NewsService wrappedService) {
        this.wrappedService = wrappedService;
        this.newsDao = new SqliteNewsDao(context);
        this.dbExecutor = Executors.newSingleThreadExecutor();
    }
    
    @Override
    public CompletableFuture<List<NewsArticle>> getNewsByCategory(NewsCategory category) {
        // First try to get from the wrapped service
        return wrappedService.getNewsByCategory(category)
                .thenApplyAsync(articles -> {
                    // If successful, save to local storage
                    if (articles != null && !articles.isEmpty()) {
                        Log.d(TAG, "Saving " + articles.size() + " articles for " + category.getValue());
                        newsDao.insertArticles(articles, category);
                    }
                    return articles;
                }, dbExecutor)
                .exceptionally(e -> {
                    Log.e(TAG, "Error getting news from network: " + e.getMessage());
                    // If network request fails, try to return cached data
                    Log.d(TAG, "Falling back to cached data for " + category.getValue());
                    return newsDao.getArticlesByCategory(category);
                });
    }
    
    @Override
    public CompletableFuture<NewsArticle> getArticleDetails(String url) {
        // First check if article exists in local storage
        return CompletableFuture.supplyAsync(() -> newsDao.getArticleByUrl(url), dbExecutor)
                .thenCompose(cachedArticle -> {
                    if (cachedArticle != null) {
                        Log.d(TAG, "Cache hit for article: " + url);
                        return CompletableFuture.completedFuture(cachedArticle);
                    }
                    
                    // If not in cache, delegate to the wrapped service
                    Log.d(TAG, "Cache miss for article: " + url);
                    return wrappedService.getArticleDetails(url)
                            .thenApplyAsync(article -> {
                                // Save result to cache if successful
                                if (article != null) {
                                    // We don't know the category here, using HOME as default
                                    newsDao.insertArticle(article, NewsCategory.HOME);
                                    Log.d(TAG, "Saved article to cache: " + url);
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
        return newsDao.hasCachedArticles(category);
    }
} 