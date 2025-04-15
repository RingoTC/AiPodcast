package com.example.aipodcast.util;

import android.content.Context;
import android.util.Log;

import com.example.aipodcast.model.PodcastContent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manager for caching podcast content and audio files.
 * Handles saving and loading podcasts, as well as managing the cache size.
 */
public class PodcastCacheManager {
    private static final String TAG = "PodcastCacheManager";
    private static final String CACHE_DIR = "podcast_cache";
    private static final long MAX_CACHE_SIZE_BYTES = 100 * 1024 * 1024; // 100 MB
    private static final int MAX_CACHED_PODCASTS = 10;
    
    private static PodcastCacheManager instance;
    private final Context context;
    private final ExecutorService executor;
    private final Gson gson;
    
    // Private constructor for singleton pattern
    private PodcastCacheManager(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.gson = new GsonBuilder().create();
        initializeCacheDir();
    }
    
    /**
     * Get the singleton instance
     * 
     * @param context Application context
     * @return PodcastCacheManager instance
     */
    public static synchronized PodcastCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new PodcastCacheManager(context);
        }
        return instance;
    }
    
    /**
     * Initialize the cache directory
     */
    private void initializeCacheDir() {
        File cacheDir = getCacheDir();
        if (!cacheDir.exists()) {
            boolean created = cacheDir.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create cache directory");
            }
        }
    }
    
    /**
     * Get the cache directory
     * 
     * @return Cache directory
     */
    private File getCacheDir() {
        return new File(context.getCacheDir(), CACHE_DIR);
    }
    
    /**
     * Save podcast content to cache
     * 
     * @param content Podcast content to save
     * @return CompletableFuture with the cache file path
     */
    public CompletableFuture<String> cachePodcast(PodcastContent content) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Ensure content has an ID
                if (content.getId() == null || content.getId().isEmpty()) {
                    content.setId(UUID.randomUUID().toString());
                }
                
                // Generate filename based on ID
                String filename = "podcast_" + content.getId() + ".json";
                File cacheFile = new File(getCacheDir(), filename);
                
                // Convert to JSON
                String json = gson.toJson(content);
                
                // Write to file
                try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    fos.write(json.getBytes());
                }
                
                Log.d(TAG, "Podcast cached to " + cacheFile.getPath());
                
                // Manage cache size after adding new content
                cleanupCacheIfNeeded();
                
                return cacheFile.getPath();
                
            } catch (Exception e) {
                Log.e(TAG, "Error caching podcast: " + e.getMessage(), e);
                throw new RuntimeException("Failed to cache podcast", e);
            }
        }, executor);
    }
    
    /**
     * Load podcast content from cache by ID
     * 
     * @param podcastId Podcast ID
     * @return CompletableFuture with the loaded PodcastContent
     */
    public CompletableFuture<PodcastContent> loadPodcast(String podcastId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String filename = "podcast_" + podcastId + ".json";
                File cacheFile = new File(getCacheDir(), filename);
                
                if (!cacheFile.exists()) {
                    Log.w(TAG, "Podcast file not found in cache: " + filename);
                    return null;
                }
                
                // Read file
                String json = readFileAsString(cacheFile);
                
                // Parse JSON
                return gson.fromJson(json, PodcastContent.class);
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading podcast: " + e.getMessage(), e);
                return null;
            }
        }, executor);
    }
    
    /**
     * Get all cached podcasts
     * 
     * @return CompletableFuture with list of podcasts
     */
    public CompletableFuture<List<PodcastContent>> getAllCachedPodcasts() {
        return CompletableFuture.supplyAsync(() -> {
            List<PodcastContent> podcasts = new ArrayList<>();
            File cacheDir = getCacheDir();
            
            File[] files = cacheDir.listFiles((dir, name) -> name.startsWith("podcast_") && name.endsWith(".json"));
            if (files == null) {
                return podcasts;
            }
            
            for (File file : files) {
                try {
                    String json = readFileAsString(file);
                    PodcastContent podcast = gson.fromJson(json, PodcastContent.class);
                    if (podcast != null) {
                        podcasts.add(podcast);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing podcast file: " + file.getName(), e);
                }
            }
            
            return podcasts;
        }, executor);
    }
    
    /**
     * Save audio file to cache
     * 
     * @param audioData Audio data as byte array
     * @param podcastId Podcast ID to associate with
     * @param segmentId Optional segment ID for partial audio
     * @return CompletableFuture with the cache file path
     */
    public CompletableFuture<String> cacheAudioFile(byte[] audioData, String podcastId, String segmentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String filename = "audio_" + podcastId;
                if (segmentId != null && !segmentId.isEmpty()) {
                    filename += "_" + segmentId;
                }
                filename += ".mp3"; // Assume MP3 format
                
                File cacheFile = new File(getCacheDir(), filename);
                
                // Write audio data
                try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    fos.write(audioData);
                }
                
                Log.d(TAG, "Audio cached to " + cacheFile.getPath());
                
                // Manage cache size
                cleanupCacheIfNeeded();
                
                return cacheFile.getPath();
                
            } catch (Exception e) {
                Log.e(TAG, "Error caching audio: " + e.getMessage(), e);
                throw new RuntimeException("Failed to cache audio", e);
            }
        }, executor);
    }
    
    /**
     * Load audio file from cache
     * 
     * @param podcastId Podcast ID
     * @param segmentId Optional segment ID
     * @return CompletableFuture with the audio file path or null if not found
     */
    public CompletableFuture<String> getAudioFilePath(String podcastId, String segmentId) {
        return CompletableFuture.supplyAsync(() -> {
            String filename = "audio_" + podcastId;
            if (segmentId != null && !segmentId.isEmpty()) {
                filename += "_" + segmentId;
            }
            filename += ".mp3";
            
            File cacheFile = new File(getCacheDir(), filename);
            
            if (cacheFile.exists()) {
                return cacheFile.getPath();
            } else {
                Log.w(TAG, "Audio file not found in cache: " + filename);
                return null;
            }
        }, executor);
    }
    
    /**
     * Delete a podcast and its associated files from cache
     * 
     * @param podcastId Podcast ID to delete
     * @return CompletableFuture with success state
     */
    public CompletableFuture<Boolean> deletePodcast(String podcastId) {
        return CompletableFuture.supplyAsync(() -> {
            boolean success = true;
            
            // Delete podcast content file
            String contentFilename = "podcast_" + podcastId + ".json";
            File contentFile = new File(getCacheDir(), contentFilename);
            if (contentFile.exists()) {
                success = contentFile.delete();
            }
            
            // Delete associated audio files
            File cacheDir = getCacheDir();
            File[] audioFiles = cacheDir.listFiles((dir, name) -> 
                    name.startsWith("audio_" + podcastId) && name.endsWith(".mp3"));
            
            if (audioFiles != null) {
                for (File audioFile : audioFiles) {
                    if (!audioFile.delete()) {
                        success = false;
                        Log.w(TAG, "Failed to delete audio file: " + audioFile.getName());
                    }
                }
            }
            
            return success;
        }, executor);
    }
    
    /**
     * Clean up the cache if it exceeds size limits
     */
    private void cleanupCacheIfNeeded() {
        File cacheDir = getCacheDir();
        File[] files = cacheDir.listFiles();
        
        if (files == null || files.length <= MAX_CACHED_PODCASTS) {
            return;
        }
        
        // Sort files by last modified time (oldest first)
        Arrays.sort(files, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
        
        // Calculate total size
        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();
        }
        
        // Delete oldest files until under limits
        for (int i = 0; i < files.length && 
                (totalSize > MAX_CACHE_SIZE_BYTES || files.length - i > MAX_CACHED_PODCASTS); i++) {
            File file = files[i];
            long fileSize = file.length();
            
            if (file.delete()) {
                totalSize -= fileSize;
                Log.d(TAG, "Deleted cached file: " + file.getName());
            } else {
                Log.w(TAG, "Failed to delete cached file: " + file.getName());
            }
        }
    }
    
    /**
     * Clear all cached files
     * 
     * @return CompletableFuture with success state
     */
    public CompletableFuture<Boolean> clearCache() {
        return CompletableFuture.supplyAsync(() -> {
            boolean success = true;
            File cacheDir = getCacheDir();
            File[] files = cacheDir.listFiles();
            
            if (files != null) {
                for (File file : files) {
                    if (!file.delete()) {
                        success = false;
                        Log.w(TAG, "Failed to delete cached file: " + file.getName());
                    }
                }
            }
            
            return success;
        }, executor);
    }
    
    /**
     * Read file contents as string
     * 
     * @param file File to read
     * @return File contents as string
     * @throws IOException If read fails
     */
    private String readFileAsString(File file) throws IOException {
        byte[] buffer = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(buffer);
        }
        return new String(buffer);
    }
    
    /**
     * Shutdown the cache manager
     */
    public void shutdown() {
        executor.shutdown();
    }
} 