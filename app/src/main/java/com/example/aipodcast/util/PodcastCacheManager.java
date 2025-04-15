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
public class PodcastCacheManager {
    private static final String TAG = "PodcastCacheManager";
    private static final String CACHE_DIR = "podcast_cache";
    private static final long MAX_CACHE_SIZE_BYTES = 100 * 1024 * 1024; 
    private static final int MAX_CACHED_PODCASTS = 10;
    private static PodcastCacheManager instance;
    private final Context context;
    private final ExecutorService executor;
    private final Gson gson;
    private PodcastCacheManager(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.gson = new GsonBuilder().create();
        initializeCacheDir();
    }
    public static synchronized PodcastCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new PodcastCacheManager(context);
        }
        return instance;
    }
    private void initializeCacheDir() {
        File cacheDir = getCacheDir();
        if (!cacheDir.exists()) {
            boolean created = cacheDir.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create cache directory");
            }
        }
    }
    private File getCacheDir() {
        return new File(context.getCacheDir(), CACHE_DIR);
    }
    public CompletableFuture<String> cachePodcast(PodcastContent content) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (content.getId() == null || content.getId().isEmpty()) {
                    content.setId(UUID.randomUUID().toString());
                }
                String filename = "podcast_" + content.getId() + ".json";
                File cacheFile = new File(getCacheDir(), filename);
                String json = gson.toJson(content);
                try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    fos.write(json.getBytes());
                }
                Log.d(TAG, "Podcast cached to " + cacheFile.getPath());
                cleanupCacheIfNeeded();
                return cacheFile.getPath();
            } catch (Exception e) {
                Log.e(TAG, "Error caching podcast: " + e.getMessage(), e);
                throw new RuntimeException("Failed to cache podcast", e);
            }
        }, executor);
    }
    public CompletableFuture<PodcastContent> loadPodcast(String podcastId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String filename = "podcast_" + podcastId + ".json";
                File cacheFile = new File(getCacheDir(), filename);
                if (!cacheFile.exists()) {
                    Log.w(TAG, "Podcast file not found in cache: " + filename);
                    return null;
                }
                String json = readFileAsString(cacheFile);
                return gson.fromJson(json, PodcastContent.class);
            } catch (Exception e) {
                Log.e(TAG, "Error loading podcast: " + e.getMessage(), e);
                return null;
            }
        }, executor);
    }
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
    public CompletableFuture<String> cacheAudioFile(byte[] audioData, String podcastId, String segmentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String filename = "audio_" + podcastId;
                if (segmentId != null && !segmentId.isEmpty()) {
                    filename += "_" + segmentId;
                }
                filename += ".mp3"; 
                File cacheFile = new File(getCacheDir(), filename);
                try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    fos.write(audioData);
                }
                Log.d(TAG, "Audio cached to " + cacheFile.getPath());
                cleanupCacheIfNeeded();
                return cacheFile.getPath();
            } catch (Exception e) {
                Log.e(TAG, "Error caching audio: " + e.getMessage(), e);
                throw new RuntimeException("Failed to cache audio", e);
            }
        }, executor);
    }
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
    public CompletableFuture<Boolean> deletePodcast(String podcastId) {
        return CompletableFuture.supplyAsync(() -> {
            boolean success = true;
            String contentFilename = "podcast_" + podcastId + ".json";
            File contentFile = new File(getCacheDir(), contentFilename);
            if (contentFile.exists()) {
                success = contentFile.delete();
            }
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
    private void cleanupCacheIfNeeded() {
        File cacheDir = getCacheDir();
        File[] files = cacheDir.listFiles();
        if (files == null || files.length <= MAX_CACHED_PODCASTS) {
            return;
        }
        Arrays.sort(files, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();
        }
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
    private String readFileAsString(File file) throws IOException {
        byte[] buffer = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(buffer);
        }
        return new String(buffer);
    }
    public void shutdown() {
        executor.shutdown();
    }
} 