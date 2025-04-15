package com.example.aipodcast.service;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

/**
 * Enhanced TTS service that supports multiple voice characters
 * for different speakers in a conversation.
 */
public class EnhancedTTSService {
    private static final String TAG = "EnhancedTTSService";
    
    // TTS engines for different voices
    private TextToSpeech alexTTS;
    private TextToSpeech jordanTTS;
    private TextToSpeech tts; // Single unified TTS for host mode
    private boolean isAlexInitialized = false;
    private boolean isJordanInitialized = false;
    private boolean isTtsInitialized = false;
    
    // Current speaker state
    private String currentSpeaker = null;
    
    // Voice parameters
    private float alexPitch = 1.0f;
    private float alexRate = 0.9f;
    private float jordanPitch = 0.85f; 
    private float jordanRate = 0.95f;
    private float hostPitch = 1.0f;
    private float hostRate = 1.0f;
    
    // Callbacks
    private TTSCallback callback;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // Current speech rate multiplier
    private float currentSpeechRate = 1.0f;
    
    /**
     * Interface for TTS callbacks
     */
    public interface TTSCallback {
        void onSpeakStart(String speaker);
        void onWordSpoken(String speaker, String word, int wordIndex);
        void onSpeakComplete(String speaker);
        void onInitialized();
        void onError(String message);
    }
    
    /**
     * Constructor
     * 
     * @param context Application context
     * @param callback Callback for TTS events
     */
    public EnhancedTTSService(Context context, TTSCallback callback) {
        this.callback = callback;
        initializeVoices(context);
    }
    
    /**
     * Initialize TTS engines
     */
    private void initializeVoices(Context context) {
        // Initialize single host TTS
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                isTtsInitialized = result != TextToSpeech.LANG_MISSING_DATA && 
                                  result != TextToSpeech.LANG_NOT_SUPPORTED;
                
                if (isTtsInitialized) {
                    // Configure host voice
                    tts.setPitch(hostPitch);
                    tts.setSpeechRate(hostRate);
                    
                    // Try to select a good voice if available
                    selectVoice(tts, true);
                    
                    Log.d(TAG, "Host TTS initialized");
                    checkInitialization();
                }
            }
        });
        
        // For backward compatibility, still initialize Alex and Jordan
        // Initialize Alex voice
        alexTTS = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = alexTTS.setLanguage(Locale.US);
                isAlexInitialized = result != TextToSpeech.LANG_MISSING_DATA && 
                                   result != TextToSpeech.LANG_NOT_SUPPORTED;
                
                if (isAlexInitialized) {
                    // Configure Alex voice - male voice
                    alexTTS.setPitch(alexPitch);
                    alexTTS.setSpeechRate(alexRate);
                    
                    // Try to select a male voice if available
                    selectVoice(alexTTS, true);
                    
                    Log.d(TAG, "Alex TTS initialized");
                    checkInitialization();
                }
            }
        });
        
        // Initialize Jordan voice
        jordanTTS = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = jordanTTS.setLanguage(Locale.US);
                isJordanInitialized = result != TextToSpeech.LANG_MISSING_DATA && 
                                     result != TextToSpeech.LANG_NOT_SUPPORTED;
                
                if (isJordanInitialized) {
                    // Configure Jordan voice - female voice  
                    jordanTTS.setPitch(jordanPitch);
                    jordanTTS.setSpeechRate(jordanRate);
                    
                    // Try to select a female voice if available
                    selectVoice(jordanTTS, false);
                    
                    Log.d(TAG, "Jordan TTS initialized");
                    checkInitialization();
                }
            }
        });
    }
    
    /**
     * Attempt to select a gender-appropriate voice
     * 
     * @param tts The TTS engine
     * @param male True for male voice, false for female
     */
    private void selectVoice(TextToSpeech tts, boolean male) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Set<android.speech.tts.Voice> voices = tts.getVoices();
                if (voices != null && !voices.isEmpty()) {
                    for (android.speech.tts.Voice voice : voices) {
                        // Check if voice matches locale and gender requirements
                        if (voice.getLocale().equals(Locale.US) && voice.getQuality() >= android.speech.tts.Voice.QUALITY_NORMAL) {
                            // Simple gender detection by voice name (not always reliable)
                            String voiceName = voice.getName().toLowerCase();
                            boolean isMaleVoice = voiceName.contains("male") || 
                                               !voiceName.contains("female");
                            
                            if ((male && isMaleVoice) || (!male && !isMaleVoice)) {
                                tts.setVoice(voice);
                                Log.d(TAG, "Selected voice: " + voice.getName());
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error selecting voice: " + e.getMessage());
            }
        }
    }
    
    /**
     * Check if all TTS engines are initialized
     */
    private void checkInitialization() {
        if (isAlexInitialized && isJordanInitialized && isTtsInitialized) {
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onInitialized();
                }
            });
        }
    }
    
    /**
     * Use specified voice to speak text
     * 
     * @param speaker Speaker identifier ("ALEX", "JORDAN", or "HOST")
     * @param text Text to speak
     * @return True if successful
     */
    public boolean speak(String speaker, String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        // For single host mode, convert all speakers to HOST
        final String finalSpeaker = "ALEX".equals(speaker) || "JORDAN".equals(speaker) ? "HOST" : speaker;
        
        // Use the unified TTS for all speaking
        if (!isTtsInitialized) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("TTS not fully initialized"));
            }
            return false;
        }
        
        // Track current speaker
        currentSpeaker = finalSpeaker;
        
        // Notify callback
        if (callback != null) {
            callback.onSpeakStart(finalSpeaker);
        }
        
        // Utterance ID for progress tracking
        String utteranceId = finalSpeaker + "_" + System.currentTimeMillis();
        
        // Set up utterance progress listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Final reference to text for lambda
            final String finalText = text;
            
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override
                public void onStart(String s) {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onSpeakStart(finalSpeaker);
                        }
                    });
                }
                
                @Override
                public void onDone(String s) {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onSpeakComplete(finalSpeaker);
                        }
                    });
                }
                
                @Override
                public void onError(String s) {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onError("Error speaking with " + finalSpeaker + ": " + s);
                        }
                    });
                }
                
                // Available in API 23+
                @Override
                public void onRangeStart(String utteranceId, int start, int end, int frame) {
                    if (callback != null) {
                        String word = finalText.substring(start, end);
                        mainHandler.post(() -> {
                            callback.onWordSpoken(finalSpeaker, word, start);
                        });
                    }
                }
            });
            
            // Speak the text
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Bundle bundle = new Bundle();
                tts.speak(finalText, TextToSpeech.QUEUE_FLUSH, bundle, utteranceId);
            } else {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                tts.speak(finalText, TextToSpeech.QUEUE_FLUSH, params);
            }
            return true;
        } else {
            // Fallback for older API levels
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
            return true;
        }
    }
    
    /**
     * Stop all speech
     */
    public void stop() {
        if (isTtsInitialized) {
            tts.stop();
        }
        
        // For backward compatibility
        if (isAlexInitialized) {
            alexTTS.stop();
        }
        
        if (isJordanInitialized) {
            jordanTTS.stop();
        }
        
        currentSpeaker = null;
    }
    
    /**
     * Clean up resources
     */
    public void shutdown() {
        stop();
        
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        
        if (alexTTS != null) {
            alexTTS.shutdown();
            alexTTS = null;
        }
        
        if (jordanTTS != null) {
            jordanTTS.shutdown();
            jordanTTS = null;
        }
        
        isTtsInitialized = false;
        isAlexInitialized = false;
        isJordanInitialized = false;
    }
    
    /**
     * Set speech rate for all speakers
     * 
     * @param rate Speech rate (0.5f - 2.0f)
     */
    public void setSpeechRate(float rate) {
        if (rate < 0.5f || rate > 2.0f) {
            return;
        }
        
        currentSpeechRate = rate;
        
        // Apply to all TTS engines
        if (isTtsInitialized) {
            tts.setSpeechRate(rate);
        }
        
        // For backward compatibility
        alexRate = rate;
        jordanRate = rate * 1.05f; // Slightly different for variety
        
        if (isAlexInitialized) {
            alexTTS.setSpeechRate(alexRate);
        }
        
        if (isJordanInitialized) {
            jordanTTS.setSpeechRate(jordanRate);
        }
    }
    
    /**
     * 检查TTS是否已初始化完成
     * 
     * @return 是否初始化完成
     */
    public boolean isInitialized() {
        return isTtsInitialized;
    }
    
    /**
     * 测试TTS是否可以正常工作
     * 
     * @return 是否能正常工作
     */
    public boolean testTTS() {
        if (!isTtsInitialized) {
            return false;
        }
        
        try {
            // 尝试使用TTS引擎说一个简短的测试文本
            String testText = "Test text";
            int result = tts.speak(testText, TextToSpeech.QUEUE_FLUSH, null, "TEST_UTTERANCE");
            return result == TextToSpeech.SUCCESS;
        } catch (Exception e) {
            Log.e(TAG, "TTS test failed: " + e.getMessage());
            return false;
        }
    }
}
