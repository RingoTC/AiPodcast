package com.example.aipodcast.repository;

import android.content.Context;
import android.util.Log;

import com.example.aipodcast.database.dao.NewsDao;
import com.example.aipodcast.database.dao.SqliteNewsDao;
import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.NewsCategory;
import com.example.aipodcast.service.NewsService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    
    // Maximum articles to keep per category
    private static final int MAX_ARTICLES_PER_CATEGORY = 50;
    
    private final NewsService newsService;
    private final NewsDao newsDao;
    private final Executor dbExecutor;
    
    // Topic to Category mapping
    private final Map<String, NewsCategory> topicToCategoryMap;
    
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
        
        // Initialize topic to category mapping
        this.topicToCategoryMap = new HashMap<>();
        initializeTopicToCategoryMap();
    }
    
    private void initializeTopicToCategoryMap() {
        // Map UI topics to API categories
        topicToCategoryMap.put("Technology", NewsCategory.SCIENCE);
        topicToCategoryMap.put("Sports", NewsCategory.HOME);
        topicToCategoryMap.put("Entertainment", NewsCategory.ARTS);
        topicToCategoryMap.put("Health", NewsCategory.SCIENCE);
        topicToCategoryMap.put("Politics", NewsCategory.US);
        
        // Default mappings for all categories
        topicToCategoryMap.put("Arts", NewsCategory.ARTS);
        topicToCategoryMap.put("Home", NewsCategory.HOME);
        topicToCategoryMap.put("Science", NewsCategory.SCIENCE);
        topicToCategoryMap.put("US", NewsCategory.US);
        topicToCategoryMap.put("World", NewsCategory.WORLD);
    }
    
    @Override
    public CompletableFuture<List<NewsArticle>> getNewsByCategory(NewsCategory category) {
        // First check if we have fresh cached data
        if (hasFreshCache(category)) {
            return getNewsByCategoryFromCache(category);
        }
        
        // Otherwise fetch from network and update cache
        return refreshNewsByCategory(category);
    }
    
    @Override
    public CompletableFuture<List<NewsArticle>> getNewsByCategoryFromCache(NewsCategory category) {
        return CompletableFuture.supplyAsync(() -> 
            newsDao.getArticlesByCategory(category), dbExecutor);
    }
    
    @Override
    public CompletableFuture<List<NewsArticle>> refreshNewsByCategory(NewsCategory category) {
        return newsService.getNewsByCategory(category)
                .thenApplyAsync(articles -> {
                    // Save articles to database
                    if (articles != null && !articles.isEmpty()) {
                        newsDao.insertArticles(articles, category);
                        
                        // Clean up old articles to prevent database from growing too large
                        newsDao.deleteOldArticles(category, MAX_ARTICLES_PER_CATEGORY);
                    }
                    return articles;
                }, dbExecutor)
                .exceptionally(e -> {
                    Log.e(TAG, "Error refreshing news: " + e.getMessage());
                    // If network request fails, try to return cached data
                    if (hasCachedData(category)) {
                        return newsDao.getArticlesByCategory(category);
                    }
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
                    
                    // If not in cache, try to fetch from network
                    return newsService.getArticleDetails(url)
                            .thenApplyAsync(article -> {
                                // Save to cache if fetch successful
                                if (article != null) {
                                    // We don't know the category here, using HOME as default
                                    newsDao.insertArticle(article, NewsCategory.HOME);
                                }
                                return article;
                            }, dbExecutor)
                            .exceptionally(e -> {
                                Log.e(TAG, "Error fetching article details: " + e.getMessage());
                                return null;
                            });
                });
    }
    
    @Override
    public NewsCategory getCategoryForTopic(String topic) {
        return topicToCategoryMap.getOrDefault(topic, NewsCategory.HOME);
    }
    
    @Override
    public CompletableFuture<Void> clearCategoryCache(NewsCategory category) {
        return CompletableFuture.runAsync(() -> {
            newsDao.deleteArticlesByCategory(category);
        }, dbExecutor);
    }
    
    @Override
    public boolean hasCachedData(NewsCategory category) {
        return newsDao.hasCachedArticles(category);
    }
    
    /**
     * Check if the cache for a category is fresh (not expired)
     * 
     * @param category The category to check
     * @return true if cache is fresh, false if stale or empty
     */
    private boolean hasFreshCache(NewsCategory category) {
        if (!hasCachedData(category)) {
            return false;
        }
        
        long lastUpdateTime = newsDao.getLastUpdateTime(category);
        long currentTime = System.currentTimeMillis();
        
        return (currentTime - lastUpdateTime) < CACHE_EXPIRATION_MS;
    }
} 