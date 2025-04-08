package com.example.aipodcast.repository;

import android.content.Context;

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

    @Override
    public CompletableFuture<List<NewsArticle>> searchArticles(String keyword) {
        return newsService.searchArticles(keyword);
    }

    @Override
    public CompletableFuture<NewsArticle> getArticleDetails(String url) {
        return newsService.getArticleDetails(url);
    }

    @Override
    public boolean hasCachedData(String keyword) {
        return newsDao.hasCachedArticles(keyword);
    }

    @Override
    public CompletableFuture<Void> clearCache(String keyword) {
        return CompletableFuture.runAsync(() -> {
            newsDao.deleteArticlesByKeyword(keyword);
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> clearAllCache() {
        return CompletableFuture.runAsync(() -> {
            newsDao.deleteAllArticles();
        }, dbExecutor);
    }
} 