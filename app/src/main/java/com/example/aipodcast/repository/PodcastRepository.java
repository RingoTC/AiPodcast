package com.example.aipodcast.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.PodcastContent;
import com.example.aipodcast.model.PodcastSegment;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Repository for managing podcast content data.
 * Handles caching, storage, and retrieval of podcast content.
 */
public class PodcastRepository {
    private static final String TAG = "PodcastRepository";
    private static final String PREFS_NAME = "podcast_prefs";
    private static final String KEY_LAST_PODCAST = "last_podcast";
    private static final String KEY_PLAYBACK_POSITION = "playback_position";
    private static final String KEY_PLAYBACK_SPEED = "playback_speed";
    private static final String CACHE_DIR = "podcast_cache";
    
    private static PodcastRepository instance;
    private final Context context;
    private final SharedPreferences preferences;
    private final Gson gson;
    private final Executor executor;
    
    // In-memory cache
    private PodcastContent currentPodcast;
    
    /**
     * Get the singleton instance of PodcastRepository
     * 
     * @param context Application context
     * @return PodcastRepository instance
     */
    public static synchronized PodcastRepository getInstance(Context context) {
        if (instance == null) {
            instance = new PodcastRepository(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Private constructor for singleton pattern
     * 
     * @param context Application context
     */
    private PodcastRepository(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new GsonBuilder().create();
        this.executor = Executors.newSingleThreadExecutor();
        
        // Create cache directory if it doesn't exist
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }
    
    /**
     * Save podcast content to persistent storage
     * 
     * @param content Podcast content to save
     * @return CompletableFuture for async operation
     */
    public CompletableFuture<Boolean> savePodcast(PodcastContent content) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Save to in-memory cache
                currentPodcast = content;
                
                // Save reference to preferences
                String podcastJson = gson.toJson(content);
                preferences.edit().putString(KEY_LAST_PODCAST, podcastJson).apply();
                
                // Save to cache file for larger content
                String cacheFileName = "podcast_" + System.currentTimeMillis() + ".json";
                File cacheFile = new File(new File(context.getCacheDir(), CACHE_DIR), cacheFileName);
                
                try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    fos.write(podcastJson.getBytes());
                }
                
                Log.d(TAG, "Podcast saved successfully to " + cacheFile.getPath());
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error saving podcast: " + e.getMessage());
                return false;
            }
        }, executor);
    }
    
    /**
     * Load the last saved podcast from persistent storage
     * 
     * @return CompletableFuture with the loaded podcast content
     */
    public CompletableFuture<PodcastContent> loadLastPodcast() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check in-memory cache first
                if (currentPodcast != null) {
                    return currentPodcast;
                }
                
                // Try to load from preferences
                String podcastJson = preferences.getString(KEY_LAST_PODCAST, null);
                if (podcastJson != null) {
                    currentPodcast = gson.fromJson(podcastJson, PodcastContent.class);
                    return currentPodcast;
                }
                
                return null;
            } catch (Exception e) {
                Log.e(TAG, "Error loading podcast: " + e.getMessage());
                return null;
            }
        }, executor);
    }
    
    /**
     * Check if podcast is cached for the given articles and topics
     * 
     * @param articles Set of news articles
     * @param topics List of topics
     * @return CompletableFuture with boolean result
     */
    public CompletableFuture<Boolean> isPodcastCached(Set<NewsArticle> articles, List<String> topics) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check in-memory cache first
                if (currentPodcast != null) {
                    // Compare topics
                    if (doTopicsMatch(currentPodcast.getTopics(), topics)) {
                        return true;
                    }
                }
                
                // If not in memory, it's not considered cached for this implementation
                // Future enhancement: Check file cache for matching podcast
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Error checking podcast cache: " + e.getMessage());
                return false;
            }
        }, executor);
    }
    
    /**
     * Compare two lists of topics to see if they match
     */
    private boolean doTopicsMatch(List<String> list1, List<String> list2) {
        if (list1 == null || list2 == null) {
            return false;
        }
        
        if (list1.size() != list2.size()) {
            return false;
        }
        
        // Simple comparison - both lists should contain the same elements
        // Note: This ignores order differences
        return list1.containsAll(list2) && list2.containsAll(list1);
    }
    
    /**
     * Save playback position for the current podcast
     * 
     * @param position Playback position in milliseconds
     */
    public void savePlaybackPosition(int position) {
        preferences.edit().putInt(KEY_PLAYBACK_POSITION, position).apply();
    }
    
    /**
     * Get last saved playback position
     * 
     * @return Playback position in milliseconds
     */
    public int getPlaybackPosition() {
        return preferences.getInt(KEY_PLAYBACK_POSITION, 0);
    }
    
    /**
     * Save playback speed for the current podcast
     * 
     * @param speed Playback speed factor
     */
    public void savePlaybackSpeed(float speed) {
        preferences.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply();
    }
    
    /**
     * Get last saved playback speed
     * 
     * @return Playback speed factor
     */
    public float getPlaybackSpeed() {
        return preferences.getFloat(KEY_PLAYBACK_SPEED, 1.0f);
    }
    
    /**
     * Clear all cached data
     */
    public CompletableFuture<Void> clearCache() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Clear in-memory cache
                currentPodcast = null;
                
                // Clear preferences
                preferences.edit().clear().apply();
                
                // Delete cache files
                File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
                if (cacheDir.exists()) {
                    for (File file : cacheDir.listFiles()) {
                        file.delete();
                    }
                }
                
                Log.d(TAG, "Cache cleared successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing cache: " + e.getMessage());
            }
        }, executor);
    }
} 