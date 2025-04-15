package com.example.aipodcast.config;

import com.example.aipodcast.BuildConfig;

/**
 * Configuration class for storing API keys and other configuration values.
 * In a production app, you would typically store these in a more secure way
 * or retrieve them from a backend service.
 */
public class ApiConfig {
    // Get the OpenAI API key from BuildConfig
    public static final String OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY;
} 