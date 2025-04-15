package com.example.aipodcast.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.aipodcast.service.EnhancedOpenAIService;
import com.example.aipodcast.service.PodcastAudioService;
import com.example.aipodcast.service.UnifiedTTSService;
import com.example.aipodcast.util.PodcastCacheManager;

/**
 * Factory class for creating and configuring service instances.
 * Manages API keys, service creation, and configuration options.
 */
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
    
    // Service instances (lazily initialized)
    private EnhancedOpenAIService openAIService;
    private PodcastCacheManager cacheManager;
    
    /**
     * Constructor
     * 
     * @param context Application context
     */
    private ConfigFactory(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Get singleton instance
     * 
     * @param context Application context
     * @return ConfigFactory instance
     */
    public static synchronized ConfigFactory getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigFactory(context);
        }
        return instance;
    }
    
    /**
     * Get OpenAI API key
     * 
     * @return API key
     */
    public String getOpenAIApiKey() {
        // Try to get from preferences first (user provided key)
        String apiKey = preferences.getString(KEY_OPENAI_API_KEY, null);
        
        // Fall back to the one in ApiConfig (from BuildConfig)
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = ApiConfig.OPENAI_API_KEY;
        }
        
        return apiKey;
    }
    
    /**
     * Set OpenAI API key
     * 
     * @param apiKey API key
     */
    public void setOpenAIApiKey(String apiKey) {
        preferences.edit().putString(KEY_OPENAI_API_KEY, apiKey).apply();
        
        // Reset service instance when API key changes
        openAIService = null;
    }
    
    /**
     * Get whether to use streaming for OpenAI
     * 
     * @return True if streaming should be used
     */
    public boolean useStreaming() {
        return preferences.getBoolean(KEY_USE_STREAMING, true);
    }
    
    /**
     * Set whether to use streaming
     * 
     * @param useStreaming Whether to use streaming
     */
    public void setUseStreaming(boolean useStreaming) {
        preferences.edit().putBoolean(KEY_USE_STREAMING, useStreaming).apply();
    }
    
    /**
     * Get whether to enable caching
     * 
     * @return True if caching is enabled
     */
    public boolean isCachingEnabled() {
        return preferences.getBoolean(KEY_ENABLE_CACHING, true);
    }
    
    /**
     * Set whether to enable caching
     * 
     * @param enableCaching Whether to enable caching
     */
    public void setCachingEnabled(boolean enableCaching) {
        preferences.edit().putBoolean(KEY_ENABLE_CACHING, enableCaching).apply();
    }
    
    /**
     * Get speech rate
     * 
     * @return Speech rate (0.5 - 2.0)
     */
    public float getSpeechRate() {
        return preferences.getFloat(KEY_SPEECH_RATE, 1.0f);
    }
    
    /**
     * Set speech rate
     * 
     * @param rate Speech rate (0.5 - 2.0)
     */
    public void setSpeechRate(float rate) {
        if (rate < 0.5f || rate > 2.0f) {
            rate = 1.0f;
        }
        preferences.edit().putFloat(KEY_SPEECH_RATE, rate).apply();
    }
    
    /**
     * Get the OpenAIService instance
     * 
     * @return OpenAIService instance
     */
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
    
    /**
     * Get the PodcastCacheManager instance
     * 
     * @return PodcastCacheManager instance
     */
    public PodcastCacheManager getCacheManager() {
        if (cacheManager == null) {
            cacheManager = PodcastCacheManager.getInstance(context);
        }
        return cacheManager;
    }
    
    /**
     * Initialize a new UnifiedTTSService
     * 
     * @param callback Callback for TTS events
     * @return New UnifiedTTSService instance
     */
    public UnifiedTTSService createTTSService(UnifiedTTSService.TTSCallback callback) {
        UnifiedTTSService ttsService = new UnifiedTTSService(context, callback);
        
        // Apply configuration
        float speechRate = getSpeechRate();
        ttsService.setSpeechRate(speechRate);
        
        return ttsService;
    }
    
    /**
     * Create intent for PodcastAudioService
     * 
     * @return Intent for starting the service
     */
    public android.content.Intent createAudioServiceIntent() {
        return new android.content.Intent(context, PodcastAudioService.class);
    }
    
    /**
     * Reset all configuration to defaults
     */
    public void resetToDefaults() {
        preferences.edit()
                .remove(KEY_OPENAI_API_KEY)
                .remove(KEY_USE_STREAMING)
                .remove(KEY_ENABLE_CACHING)
                .remove(KEY_SPEECH_RATE)
                .apply();
        
        // Reset service instances
        openAIService = null;
        cacheManager = null;
    }
    
    /**
     * Shutdown all services
     */
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