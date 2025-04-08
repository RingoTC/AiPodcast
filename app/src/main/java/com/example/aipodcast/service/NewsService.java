package com.example.aipodcast.service;

import com.example.aipodcast.model.NewsArticle;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface NewsService {
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