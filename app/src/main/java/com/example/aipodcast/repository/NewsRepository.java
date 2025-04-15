package com.example.aipodcast.repository;
import com.example.aipodcast.model.NewsArticle;
import java.util.List;
import java.util.concurrent.CompletableFuture;
public interface NewsRepository {
    CompletableFuture<List<NewsArticle>> searchArticles(String keyword);
    CompletableFuture<NewsArticle> getArticleDetails(String url);
    boolean hasCachedData(String keyword);
    CompletableFuture<Void> clearCache(String keyword);
    CompletableFuture<Void> clearAllCache();
} 