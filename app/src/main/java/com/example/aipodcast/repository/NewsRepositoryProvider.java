package com.example.aipodcast.repository;

import android.content.Context;

import com.example.aipodcast.service.GuardianNewsService;
import com.example.aipodcast.service.NewsService;
import com.example.aipodcast.service.NewsServiceWrapper;
import com.example.aipodcast.BuildConfig;

/**
 * Provider class for accessing the NewsRepository
 * Simplifies repository creation and provides a single access point
 */
public class NewsRepositoryProvider {
    private static NewsRepository sInstance;
    
    /**
     * Get the singleton instance of NewsRepository
     * 
     * @param context Application context
     * @return NewsRepository instance
     */
    public static synchronized NewsRepository getRepository(Context context) {
        if (sInstance == null) {
            // Create the base news service
            NewsService baseNewsService = new GuardianNewsService(BuildConfig.GUARDIAN_API_KEY);
            
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