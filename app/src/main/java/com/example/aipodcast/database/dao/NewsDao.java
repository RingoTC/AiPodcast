package com.example.aipodcast.database.dao;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.NewsCategory;
import java.util.List;

/**
 * Data Access Object interface for news articles
 */
public interface NewsDao {
    /**
     * Insert a single article with keyword
     * @param article Article to insert
     * @param keyword Search keyword associated with the article
     * @return Row ID of inserted article
     */
    long insertArticle(NewsArticle article, String keyword);

    /**
     * Insert multiple articles with keyword
     * @param articles List of articles to insert
     * @param keyword Search keyword associated with the articles
     * @return Number of articles inserted
     */
    int insertArticles(List<NewsArticle> articles, String keyword);

    /**
     * Search articles by keyword
     * @param keyword Search keyword
     * @return List of matching articles
     */
    List<NewsArticle> searchArticles(String keyword);

    /**
     * Get article by URL
     * @param url Article URL
     * @return Article if found, null otherwise
     */
    NewsArticle getArticleByUrl(String url);

    /**
     * Update an existing article
     * @param article Article to update
     * @return Number of rows affected
     */
    int updateArticle(NewsArticle article);

    /**
     * Delete old articles keeping only the most recent ones
     * @param keepCount Number of most recent articles to keep
     * @return Number of articles deleted
     */
    int deleteOldArticles(int keepCount);

    /**
     * Delete articles by keyword
     * @param keyword Search keyword
     * @return Number of articles deleted
     */
    int deleteArticlesByKeyword(String keyword);

    /**
     * Delete all articles
     * @return Number of articles deleted
     */
    int deleteAllArticles();

    /**
     * Check if there are cached articles for a keyword
     * @param keyword Search keyword
     * @return true if articles exist
     */
    boolean hasCachedArticles(String keyword);

    /**
     * Get last update time for a keyword
     * @param keyword Search keyword
     * @return Timestamp of last update
     */
    long getLastUpdateTime(String keyword);

    /**
     * Get articles by category
     * @param category News category
     * @return List of articles in the category
     */
    List<NewsArticle> getArticlesByCategory(NewsCategory category);
} 