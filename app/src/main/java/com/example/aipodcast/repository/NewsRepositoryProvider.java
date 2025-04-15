package com.example.aipodcast.repository;
import android.content.Context;
import com.example.aipodcast.service.GuardianNewsService;
import com.example.aipodcast.service.NewsService;
import com.example.aipodcast.service.NewsServiceWrapper;
import com.example.aipodcast.BuildConfig;
public class NewsRepositoryProvider {
    private static NewsRepository sInstance;
    public static synchronized NewsRepository getRepository(Context context) {
        if (sInstance == null) {
            NewsService baseNewsService = new GuardianNewsService(BuildConfig.GUARDIAN_API_KEY);
            NewsService newsService = new NewsServiceWrapper(context.getApplicationContext(), baseNewsService);
            sInstance = new NewsRepositoryImpl(context.getApplicationContext(), newsService);
        }
        return sInstance;
    }
    public static NewsRepository getRepository(Context context, NewsService newsService) {
        if (!(newsService instanceof NewsServiceWrapper)) {
            newsService = new NewsServiceWrapper(context.getApplicationContext(), newsService);
        }
        return new NewsRepositoryImpl(context.getApplicationContext(), newsService);
    }
    private NewsRepositoryProvider() {
    }
} 