package com.example.aipodcast.service;
import com.example.aipodcast.model.NewsArticle;
import java.util.List;
import java.util.concurrent.CompletableFuture;
public interface NewsService {
    CompletableFuture<List<NewsArticle>> searchArticles(String keyword);
    CompletableFuture<NewsArticle> getArticleDetails(String url);
}