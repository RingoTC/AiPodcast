package com.example.aipodcast.service;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.NewsCategory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface NewsService {
    /**
     * Get news articles by category
     * @param category the news category
     * @return a future that completes with a list of news articles
     */
    CompletableFuture<List<NewsArticle>> getNewsByCategory(NewsCategory category);
    
    /**
     * Search articles by keyword
     * @param keyword The search keyword
     * @return CompletableFuture with list of matching articles
     */
    CompletableFuture<List<NewsArticle>> searchArticles(String keyword);
    
    /**
     * Get article details by URL
     * @param url The article URL
     * @return CompletableFuture with article details
     */
    CompletableFuture<NewsArticle> getArticleDetails(String url);
}