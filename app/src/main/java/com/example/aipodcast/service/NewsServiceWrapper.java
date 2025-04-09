package com.example.aipodcast.service;

import android.content.Context;
import android.util.Log;

import com.example.aipodcast.database.dao.NewsDao;
import com.example.aipodcast.database.dao.SqliteNewsDao;
import com.example.aipodcast.model.NewsArticle;

import java.util.ArrayList;
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
            }, dbExecutor)
            .exceptionally(e -> {
                Log.e(TAG, "Network error while searching articles: " + e.getMessage(), e);
                // Return null to indicate network error, will fallback to cache
                return null;
            });
            
        // Combine results from both sources
        return cachedResults.thenCombine(networkResults, (cached, network) -> {
            if (network != null && !network.isEmpty()) {
                Log.d(TAG, "Using network results for keyword: " + keyword);
                return network; // Prefer network results if available
            }
            if (cached != null && !cached.isEmpty()) {
                Log.d(TAG, "Using cached results for keyword: " + keyword);
                return cached; // Fall back to cached results
            }
            Log.w(TAG, "No results found (network or cache) for keyword: " + keyword);
            return new ArrayList<NewsArticle>(); // Return empty list if no results found
        }).exceptionally(e -> {
            Log.e(TAG, "Error searching articles: " + e.getMessage(), e);
            return new ArrayList<NewsArticle>(); // Return empty list on error
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
} 