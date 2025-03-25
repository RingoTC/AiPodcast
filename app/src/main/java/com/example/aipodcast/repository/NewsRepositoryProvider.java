package com.example.aipodcast.repository;

import android.content.Context;

import com.example.aipodcast.service.NYTimesNewsService;
import com.example.aipodcast.service.NewsService;
import com.example.aipodcast.service.NewsServiceWrapper;

/**
 * Provider class for accessing the NewsRepository
 * Simplifies repository creation and provides a single access point
 */
public class NewsRepositoryProvider {
    private static NewsRepository sInstance;
    
    // NYTimes API Key - In a real app, this would be in BuildConfig or fetched securely
    private static final String API_KEY = "YOUR_API_KEY";
    
    /**
     * Get the singleton instance of NewsRepository
     * 
     * @param context Application context
     * @return NewsRepository instance
     */
    public static synchronized NewsRepository getRepository(Context context) {
        if (sInstance == null) {
            // Create the base news service
            NewsService baseNewsService = new NYTimesNewsService(API_KEY);
            
            // Wrap it with caching functionality
            NewsService newsService = new NewsServiceWrapper(context.getApplicationContext(), baseNewsService);
            
            // Create and return the repository implementation
            sInstance = new NewsRepositoryImpl(context.getApplicationContext(), newsService);
        }
        return sInstance;
    }
    
    /**
     * Get the repository with a custom news service
     * Useful for testing or when a different news service is needed
     * 
     * @param context Application context
     * @param newsService Custom news service implementation
     * @return NewsRepository instance
     */
    public static NewsRepository getRepository(Context context, NewsService newsService) {
        // Wrap the custom service with caching functionality unless it's already a wrapper
        if (!(newsService instanceof NewsServiceWrapper)) {
            newsService = new NewsServiceWrapper(context.getApplicationContext(), newsService);
        }
        
        return new NewsRepositoryImpl(context.getApplicationContext(), newsService);
    }
    
    // Private constructor to prevent instantiation
    private NewsRepositoryProvider() {
    }
} 