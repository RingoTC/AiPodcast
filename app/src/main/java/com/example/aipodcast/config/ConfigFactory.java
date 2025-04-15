package com.example.aipodcast.config;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.example.aipodcast.service.EnhancedOpenAIService;
import com.example.aipodcast.service.PodcastAudioService;
import com.example.aipodcast.service.UnifiedTTSService;
import com.example.aipodcast.util.PodcastCacheManager;
public class ConfigFactory {
    private static final String TAG = "ConfigFactory";
    private static final String PREFS_NAME = "config_prefs";
    private static final String KEY_OPENAI_API_KEY = "openai_api_key";
    private static final String KEY_USE_STREAMING = "use_streaming";
    private static final String KEY_ENABLE_CACHING = "enable_caching";
    private static final String KEY_SPEECH_RATE = "speech_rate";
    private static ConfigFactory instance;
    private final Context context;
    private final SharedPreferences preferences;
    private EnhancedOpenAIService openAIService;
    private PodcastCacheManager cacheManager;
    private ConfigFactory(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    public static synchronized ConfigFactory getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigFactory(context);
        }
        return instance;
    }
    public String getOpenAIApiKey() {
        String apiKey = preferences.getString(KEY_OPENAI_API_KEY, null);
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = ApiConfig.OPENAI_API_KEY;
        }
        return apiKey;
    }
    public void setOpenAIApiKey(String apiKey) {
        preferences.edit().putString(KEY_OPENAI_API_KEY, apiKey).apply();
        openAIService = null;
    }
    public boolean useStreaming() {
        return preferences.getBoolean(KEY_USE_STREAMING, true);
    }
    public void setUseStreaming(boolean useStreaming) {
        preferences.edit().putBoolean(KEY_USE_STREAMING, useStreaming).apply();
    }
    public boolean isCachingEnabled() {
        return preferences.getBoolean(KEY_ENABLE_CACHING, true);
    }
    public void setCachingEnabled(boolean enableCaching) {
        preferences.edit().putBoolean(KEY_ENABLE_CACHING, enableCaching).apply();
    }
    public float getSpeechRate() {
        return preferences.getFloat(KEY_SPEECH_RATE, 1.0f);
    }
    public void setSpeechRate(float rate) {
        if (rate < 0.5f || rate > 2.0f) {
            rate = 1.0f;
        }
        preferences.edit().putFloat(KEY_SPEECH_RATE, rate).apply();
    }
    public EnhancedOpenAIService getOpenAIService() {
        if (openAIService == null) {
            String apiKey = getOpenAIApiKey();
            if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your_openai_api_key_here")) {
                Log.e(TAG, "OpenAI API key not configured");
                throw new IllegalStateException("OpenAI API key not configured");
            }
            openAIService = new EnhancedOpenAIService(apiKey);
        }
        return openAIService;
    }
    public PodcastCacheManager getCacheManager() {
        if (cacheManager == null) {
            cacheManager = PodcastCacheManager.getInstance(context);
        }
        return cacheManager;
    }
    public UnifiedTTSService createTTSService(UnifiedTTSService.TTSCallback callback) {
        UnifiedTTSService ttsService = new UnifiedTTSService(context, callback);
        float speechRate = getSpeechRate();
        ttsService.setSpeechRate(speechRate);
        return ttsService;
    }
    public android.content.Intent createAudioServiceIntent() {
        return new android.content.Intent(context, PodcastAudioService.class);
    }
    public void resetToDefaults() {
        preferences.edit()
                .remove(KEY_OPENAI_API_KEY)
                .remove(KEY_USE_STREAMING)
                .remove(KEY_ENABLE_CACHING)
                .remove(KEY_SPEECH_RATE)
                .apply();
        openAIService = null;
        cacheManager = null;
    }
    public void shutdown() {
        if (openAIService != null) {
            openAIService.shutdown();
            openAIService = null;
        }
        if (cacheManager != null) {
            cacheManager.shutdown();
            cacheManager = null;
        }
    }
} 