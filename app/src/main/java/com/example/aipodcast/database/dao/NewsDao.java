package com.example.aipodcast.database.dao;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.NewsCategory;

import java.util.List;

/**
 * Data Access Object interface for News Articles
 * Defines the operations that can be performed on news articles in the database
 */
public interface NewsDao {
    
    /**
     * Insert a single news article into the database
     * 
     * @param article The news article to insert
     * @param category The category these articles belong to
     * @return The row ID of the inserted article, or -1 if an error occurred
     */
    long insertArticle(NewsArticle article, NewsCategory category);
    
    /**
     * Insert a batch of news articles into the database
     * 
     * @param articles The list of news articles to insert
     * @param category The category these articles belong to
     * @return The number of articles successfully inserted
     */
    int insertArticles(List<NewsArticle> articles, NewsCategory category);
    
    /**
     * Get all news articles for a specific category
     * 
     * @param category The news category to filter by
     * @return A list of news articles in the given category
     */
    List<NewsArticle> getArticlesByCategory(NewsCategory category);
    
    /**
     * Get a specific news article by its URL
     * 
     * @param url The URL of the article to retrieve
     * @return The news article, or null if not found
     */
    NewsArticle getArticleByUrl(String url);
    
    /**
     * Update an existing news article
     * 
     * @param article The updated news article
     * @return The number of rows affected (should be 1 if successful)
     */
    int updateArticle(NewsArticle article);
    
    /**
     * Delete old articles from a specific category
     * 
     * @param category The category to clean up
     * @param keepLatestCount The number of most recent articles to keep
     * @return The number of articles deleted
     */
    int deleteOldArticles(NewsCategory category, int keepLatestCount);
    
    /**
     * Delete all articles for a specific category
     * 
     * @param category The category to delete articles for
     * @return The number of articles deleted
     */
    int deleteArticlesByCategory(NewsCategory category);
    
    /**
     * Check if there are any articles stored for a specific category
     * 
     * @param category The category to check
     * @return true if articles exist, false otherwise
     */
    boolean hasCachedArticles(NewsCategory category);
    
    /**
     * Get the timestamp of the most recent update for a category
     * 
     * @param category The category to check
     * @return The timestamp of the most recent update, or 0 if no articles exist
     */
    long getLastUpdateTime(NewsCategory category);
} 