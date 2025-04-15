package com.example.aipodcast.database.dao;
import com.example.aipodcast.model.NewsArticle;
import java.util.List;
public interface NewsDao {
    long insertArticle(NewsArticle article, String keyword);
    int insertArticles(List<NewsArticle> articles, String keyword);
    List<NewsArticle> searchArticles(String keyword);
    NewsArticle getArticleByUrl(String url);
    int updateArticle(NewsArticle article);
    int deleteOldArticles(int keepCount);
    int deleteArticlesByKeyword(String keyword);
    int deleteAllArticles();
    boolean hasCachedArticles(String keyword);
    long getLastUpdateTime(String keyword);
} 