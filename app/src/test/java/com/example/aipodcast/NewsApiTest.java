package com.example.aipodcast;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.NewsCategory;
import com.example.aipodcast.service.NYTimesNewsService;
import com.example.aipodcast.service.NewsService;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class NewsApiTest {
    private static final String API_KEY = "GQ0kAFD5l57Ugb4lWgMOmbE3RP2i4032";

    @Test
    public void testNewsApi() throws ExecutionException, InterruptedException {
        NewsService newsService = new NYTimesNewsService(API_KEY);
        
        System.out.println("Testing News API...");
        System.out.println("==================");
        
        // Test each category
        for (NewsCategory category : NewsCategory.values()) {
            System.out.println("\nTesting category: " + category.name());
            System.out.println("----------------------------------------");
            
            CompletableFuture<List<NewsArticle>> future = newsService.getNewsByCategory(category);
            List<NewsArticle> articles = future.get();
            
            // Print first 3 articles from each category
            for (int i = 0; i < Math.min(3, articles.size()); i++) {
                NewsArticle article = articles.get(i);
                System.out.println("\nArticle " + (i + 1) + ":");
                System.out.println("Title: " + article.getTitle());
                System.out.println("Abstract: " + article.getAbstract());
                System.out.println("URL: " + article.getUrl());
                System.out.println("Published: " + article.getPublishedDate());
            }
            
            System.out.println("\nTotal articles in " + category.name() + ": " + articles.size());
        }
    }
}