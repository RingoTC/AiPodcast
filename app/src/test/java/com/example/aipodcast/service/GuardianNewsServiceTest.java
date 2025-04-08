package com.example.aipodcast.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.NewsCategory;

import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class GuardianNewsServiceTest {
    private NewsService newsService;
    private String guardianApiKey;
    
    @Before
    public void setup() throws IOException {
        // Load API key from local.properties
        Properties properties = new Properties();
        String rootDir = System.getProperty("user.dir");
        FileInputStream input = new FileInputStream(rootDir + "/local.properties");
        properties.load(input);
        guardianApiKey = properties.getProperty("guardian.api.key");
        input.close();
        
        newsService = new GuardianNewsService(guardianApiKey);
    }
    
    @Test
    public void testGetNewsByCategory() throws ExecutionException, InterruptedException {
        CompletableFuture<List<NewsArticle>> futureArticles = newsService.getNewsByCategory(NewsCategory.TECHNOLOGY);
        List<NewsArticle> articles = futureArticles.get();
        
        assertNotNull("Articles list should not be null", articles);
        assertTrue("Articles list should not be empty", !articles.isEmpty());
        
        NewsArticle firstArticle = articles.get(0);
        assertNotNull("Article title should not be null", firstArticle.getTitle());
        assertNotNull("Article URL should not be null", firstArticle.getUrl());
        assertNotNull("Article section should not be null", firstArticle.getSection());
        assertNotNull("Article published date should not be null", firstArticle.getPublishedDate());
        assertNotNull("Article abstract should not be null", firstArticle.getAbstract());
    }
    
    @Test
    public void testSearchArticles() throws ExecutionException, InterruptedException {
        String searchKeyword = "technology";
        CompletableFuture<List<NewsArticle>> futureArticles = newsService.searchArticles(searchKeyword);
        List<NewsArticle> articles = futureArticles.get();
        
        assertNotNull("Search results should not be null", articles);
        assertTrue("Search results should not be empty", !articles.isEmpty());
        
        NewsArticle firstArticle = articles.get(0);
        assertNotNull("Article title should not be null", firstArticle.getTitle());
        assertNotNull("Article URL should not be null", firstArticle.getUrl());
        assertNotNull("Article section should not be null", firstArticle.getSection());
        assertNotNull("Article published date should not be null", firstArticle.getPublishedDate());
        assertNotNull("Article abstract should not be null", firstArticle.getAbstract());
    }
    
    @Test
    public void testGetArticleDetails() throws ExecutionException, InterruptedException {
        // First get a real article URL by searching
        CompletableFuture<List<NewsArticle>> futureArticles = newsService.searchArticles("technology");
        List<NewsArticle> articles = futureArticles.get();
        String realArticleUrl = articles.get(0).getUrl();
        
        CompletableFuture<NewsArticle> futureArticle = newsService.getArticleDetails(realArticleUrl);
        NewsArticle article = futureArticle.get();
        
        assertNotNull("Article should not be null", article);
        assertNotNull("Article title should not be null", article.getTitle());
        assertEquals("Article URL should match input URL", realArticleUrl, article.getUrl());
        assertNotNull("Article section should not be null", article.getSection());
        assertNotNull("Article published date should not be null", article.getPublishedDate());
        assertNotNull("Article abstract should not be null", article.getAbstract());
    }
} 