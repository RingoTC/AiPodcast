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
    private PodcastContent currentPodcast;
    public static synchronized PodcastRepository getInstance(Context context) {
        if (instance == null) {
            instance = new PodcastRepository(context.getApplicationContext());
        }
        return instance;
    }
    private PodcastRepository(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new GsonBuilder().create();
        this.executor = Executors.newSingleThreadExecutor();
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }
    public CompletableFuture<Boolean> savePodcast(PodcastContent content) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                currentPodcast = content;
                String podcastJson = gson.toJson(content);
                preferences.edit().putString(KEY_LAST_PODCAST, podcastJson).apply();
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
    public CompletableFuture<PodcastContent> loadLastPodcast() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (currentPodcast != null) {
                    return currentPodcast;
                }
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
    public CompletableFuture<Boolean> isPodcastCached(Set<NewsArticle> articles, List<String> topics) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (currentPodcast != null) {
                    if (doTopicsMatch(currentPodcast.getTopics(), topics)) {
                        return true;
                    }
                }
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Error checking podcast cache: " + e.getMessage());
                return false;
            }
        }, executor);
    }
    private boolean doTopicsMatch(List<String> list1, List<String> list2) {
        if (list1 == null || list2 == null) {
            return false;
        }
        if (list1.size() != list2.size()) {
            return false;
        }
        return list1.containsAll(list2) && list2.containsAll(list1);
    }
    public void savePlaybackPosition(int position) {
        preferences.edit().putInt(KEY_PLAYBACK_POSITION, position).apply();
    }
    public int getPlaybackPosition() {
        return preferences.getInt(KEY_PLAYBACK_POSITION, 0);
    }
    public void savePlaybackSpeed(float speed) {
        preferences.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply();
    }
    public float getPlaybackSpeed() {
        return preferences.getFloat(KEY_PLAYBACK_SPEED, 1.0f);
    }
    public CompletableFuture<Void> clearCache() {
        return CompletableFuture.runAsync(() -> {
            try {
                currentPodcast = null;
                preferences.edit().clear().apply();
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