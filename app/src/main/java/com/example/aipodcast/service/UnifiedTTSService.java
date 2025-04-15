package com.example.aipodcast.service;

import android.content.Context;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.example.aipodcast.model.PodcastContent;

import java.io.File;
import java.util.Locale;

/**
 * Unified service for text-to-speech functionality.
 * Provides a simplified interface for speaking text and podcast content.
 */
public class UnifiedTTSService {
    private static final String TAG = "UnifiedTTSService";
    
    // TTS engine
    private TextToSpeech textToSpeech;
    private boolean isInitialized = false;
    
    // Media player for playing audio files
    private MediaPlayer mediaPlayer;
    
    // Callback for TTS events
    private TTSCallback callback;
    
    // Context
    private Context context;
    
    /**
     * Callback interface for TTS events
     */
    public interface TTSCallback {
        void onTTSInitialized(boolean success);
        void onTTSStart(String utteranceId);
        void onTTSDone(String utteranceId);
        void onTTSError(String utteranceId, int errorCode);
        void onTTSProgress(String utteranceId, int percentDone);
        void onTTSRangeStart(String utteranceId, int start, int end, int frame);
        void onTTSStop(String utteranceId, boolean interrupted);
    }
    
    /**
     * Constructor
     * 
     * @param context Application context
     * @param callback Callback for TTS events
     */
    public UnifiedTTSService(Context context, TTSCallback callback) {
        this.context = context;
        this.callback = callback;
        
        // Initialize TTS engine
        initializeTTS();
    }
    
    /**
     * Initialize TTS engine
     */
    private void initializeTTS() {
        textToSpeech = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    // Set language to US English
                    int result = textToSpeech.setLanguage(Locale.US);
                    
                    if (result == TextToSpeech.LANG_MISSING_DATA || 
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        isInitialized = false;
                        Log.e(TAG, "Language not supported");
                        
                        if (callback != null) {
                            callback.onTTSInitialized(false);
                        }
                    } else {
                        isInitialized = true;
                        Log.d(TAG, "TTS initialized successfully");
                        
                        // Configure TTS settings
                        textToSpeech.setPitch(1.0f);
                        textToSpeech.setSpeechRate(1.0f);
                        
                        // Set utterance progress listener
                        textToSpeech.setOnUtteranceProgressListener(
                                new UtteranceProgressListener() {
                                    @Override
                                    public void onStart(String utteranceId) {
                                        if (callback != null) {
                                            callback.onTTSStart(utteranceId);
                                        }
                                    }
                                    
                                    @Override
                                    public void onDone(String utteranceId) {
                                        if (callback != null) {
                                            callback.onTTSDone(utteranceId);
                                        }
                                    }
                                    
                                    @Override
                                    public void onError(String utteranceId) {
                                        if (callback != null) {
                                            callback.onTTSError(utteranceId, -1);
                                        }
                                    }
                                    
                                    @Override
                                    public void onRangeStart(String utteranceId, 
                                                         int start, int end, int frame) {
                                        if (callback != null) {
                                            callback.onTTSRangeStart(utteranceId, start, end, frame);
                                        }
                                    }
                                });
                        
                        if (callback != null) {
                            callback.onTTSInitialized(true);
                        }
                    }
                } else {
                    isInitialized = false;
                    Log.e(TAG, "Failed to initialize TTS");
                    
                    if (callback != null) {
                        callback.onTTSInitialized(false);
                    }
                }
            }
        });
    }
    
    /**
     * Speak text
     * 
     * @param text Text to speak
     * @param utteranceId Unique ID for the utterance
     * @return True if successful
     */
    public boolean speak(String text, String utteranceId) {
        if (!isInitialized || text == null || text.isEmpty()) {
            return false;
        }
        
        // Stop any ongoing speech
        stop();
        
        // Speak the text
        int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        return result == TextToSpeech.SUCCESS;
    }
    
    /**
     * Speak podcast content
     * 
     * @param content Podcast content
     * @param utteranceId Unique ID for the utterance
     * @return True if successful
     */
    public boolean speakPodcast(PodcastContent content, String utteranceId) {
        if (!isInitialized || content == null || 
            content.getFullText() == null || content.getFullText().isEmpty()) {
            return false;
        }
        
        // Stop any ongoing speech
        stop();
        
        // Speak the podcast content
        int result = textToSpeech.speak(content.getFullText(), 
                TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        return result == TextToSpeech.SUCCESS;
    }
    
    /**
     * Play audio file
     * 
     * @param file Audio file
     * @return True if successful
     */
    public boolean playAudio(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        
        try {
            // Release any existing media player
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            
            // Create new media player
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error playing audio: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Stop speech or audio playback
     */
    public void stop() {
        // Stop TTS if speaking
        if (isInitialized && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
            
            if (callback != null) {
                callback.onTTSStop("", true);
            }
        }
        
        // Stop media player if playing
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
    
    /**
     * Set speech rate
     * 
     * @param rate Speech rate (0.5f - 2.0f)
     */
    public void setSpeechRate(float rate) {
        if (isInitialized) {
            textToSpeech.setSpeechRate(rate);
        }
    }
    
    /**
     * Set speech pitch
     * 
     * @param pitch Speech pitch (0.5f - 2.0f)
     */
    public void setPitch(float pitch) {
        if (isInitialized) {
            textToSpeech.setPitch(pitch);
        }
    }
    
    /**
     * Check if speaking
     * 
     * @return True if speaking
     */
    public boolean isSpeaking() {
        return isInitialized && textToSpeech.isSpeaking();
    }
    
    /**
     * Release resources
     */
    public void release() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        
        isInitialized = false;
    }
} 