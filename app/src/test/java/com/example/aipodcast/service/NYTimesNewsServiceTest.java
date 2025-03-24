package com.example.aipodcast.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.NewsCategory;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class NYTimesNewsServiceTest {
    private NewsService newsService;
    
    @Before
    public void setup() {
        // Use a test API key or mock implementation for tests
        newsService = new NYTimesNewsService("test_api_key");
    }
    
    @Test
    public void testGetNewsByCategory() throws ExecutionException, InterruptedException {
        CompletableFuture<List<NewsArticle>> futureArticles = newsService.getNewsByCategory(NewsCategory.ARTS);
        List<NewsArticle> articles = futureArticles.get();
        
        assertNotNull("Articles list should not be null", articles);
        assertTrue("Articles list should not be empty", !articles.isEmpty());
        
        NewsArticle firstArticle = articles.get(0);
        assertNotNull("Article title should not be null", firstArticle.getTitle());
        assertNotNull("Article URL should not be null", firstArticle.getUrl());
    }
    
    @Test
    public void testGetArticleDetails() {
        CompletableFuture<NewsArticle> futureArticle = newsService.getArticleDetails("test_url");
        try {
            futureArticle.get();
        } catch (Exception e) {
            assertTrue("Should throw UnsupportedOperationException", 
                e.getCause() instanceof UnsupportedOperationException);
        }
    }
}