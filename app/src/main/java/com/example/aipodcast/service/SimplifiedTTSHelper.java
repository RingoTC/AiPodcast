package com.example.aipodcast.service;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.example.aipodcast.model.PodcastContent;
import com.example.aipodcast.model.PodcastSegment;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Simplified Text-To-Speech helper that provides basic TTS functionality
 * for generating and playing podcast content.
 */
public class SimplifiedTTSHelper {
    private static final String TAG = "SimplifiedTTSHelper";
    
    // TTS Engine
    private TextToSpeech tts;
    private boolean isInitialized = false;
    private Context context;
    
    // Media Player
    private MediaPlayer mediaPlayer;
    
    // Data
    private List<PodcastSegment> segments;
    private int currentSegmentIndex = 0;
    
    // Progress tracking
    private Handler handler = new Handler(Looper.getMainLooper());
    private ProgressCallback progressCallback;
    private WordTrackingCallback wordTrackingCallback;
    
    // New fields for TTS progress tracking
    private long ttsStartTime = 0;
    private int ttsSimulationOffset = 0;
    private int ttsTotalDuration = 0;
    private String ttsText = null;
    private boolean isTtsSeeking = false;
    
    /**
     * Callback interface for progress updates
     */
    public interface ProgressCallback {
        void onProgress(int currentPosition, int totalDuration, int segmentIndex);
        void onComplete();
        void onError(String message);
    }
    
    /**
     * Callback interface for word tracking
     */
    public interface WordTrackingCallback {
        void onWordSpoken(String word, int indexInSpeech);
    }
    
    /**
     * Callback interface for initialization result
     */
    public interface InitCallback {
        void onInitialized(boolean success);
    }
    
    /**
     * Constructor
     * 
     * @param context Application context
     */
    public SimplifiedTTSHelper(Context context) {
        this(context, null);
    }
    
    /**
     * Constructor with initialization callback
     * 
     * @param context Application context
     * @param initCallback Callback for initialization result
     */
    public SimplifiedTTSHelper(Context context, InitCallback initCallback) {
        this.context = context;
        
        // Initialize TTS engine
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                isInitialized = result != TextToSpeech.LANG_MISSING_DATA &&
                               result != TextToSpeech.LANG_NOT_SUPPORTED;
                
                if (isInitialized) {
                    // Optimize speech parameters
                    tts.setSpeechRate(0.9f);
                    tts.setPitch(1.0f);
                    Log.d(TAG, "TTS initialized successfully");
                } else {
                    Log.e(TAG, "Language not supported");
                }
                
                // Notify callback
                if (initCallback != null) {
                    handler.post(() -> initCallback.onInitialized(isInitialized));
                }
            } else {
                Log.e(TAG, "TTS initialization failed with status: " + status);
                
                // Notify callback of failure
                if (initCallback != null) {
                    handler.post(() -> initCallback.onInitialized(false));
                }
            }
        });
    }
    
    /**
     * Set progress callback
     * 
     * @param callback Callback for progress updates
     */
    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }
    
    /**
     * Set word tracking callback
     * 
     * @param callback Callback for word tracking
     */
    public void setWordTrackingCallback(WordTrackingCallback callback) {
        this.wordTrackingCallback = callback;
    }
    
    /**
     * Speak text directly
     * 
     * @param text Text to speak
     * @return True if successful
     */
    public boolean speak(String text) {
        if (!isInitialized) {
            Log.e(TAG, "TTS not initialized");
            return false;
        }
        
        if (text == null || text.isEmpty()) {
            Log.e(TAG, "Empty text provided");
            return false;
        }
        
        // Stop any current playback
        stop();
        
        // Set up utterance progress listener for word tracking
        if (wordTrackingCallback != null) {
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                private int wordIndex = 0;
                
                @Override
                public void onStart(String utteranceId) {
                    wordIndex = 0;
                }
                
                @Override
                public void onDone(String utteranceId) {
                    if (progressCallback != null) {
                        handler.post(() -> progressCallback.onComplete());
                    }
                }
                
                @Override
                public void onError(String utteranceId) {
                    if (progressCallback != null) {
                        handler.post(() -> progressCallback.onError("TTS error"));
                    }
                }
                
                @Override
                public void onRangeStart(String utteranceId, int start, int end, int frame) {
                    // This is called when a range of text is about to be spoken
                    // Extract the word being spoken
                    if (start >= 0 && end > start && end <= text.length()) {
                        try {
                            String word = text.substring(start, end);
                            wordIndex++;
                            if (wordTrackingCallback != null) {
                                final int idx = wordIndex;
                                handler.post(() -> wordTrackingCallback.onWordSpoken(word, idx));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error tracking word: " + e.getMessage());
                        }
                    }
                }
            });
        }
        
        // Speak the text
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_ID");
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "UTTERANCE_ID");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
        
        // Set up a timer to simulate progress updates since TTS doesn't provide position
        startTTSProgressUpdates(text);
        
        return true;
    }
    
    /**
     * Start simulated progress updates for TTS
     */
    private void startTTSProgressUpdates(String text) {
        final int totalDuration = estimateTTSDuration(text);
        final long startTime = System.currentTimeMillis();
        
        // Store the current simulation state for seeking
        final long[] simulationStartTime = {startTime};
        final int[] simulationOffset = {0};
        
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (tts != null && tts.isSpeaking()) {
                    long elapsedTime = System.currentTimeMillis() - simulationStartTime[0];
                    int currentPosition = Math.min(simulationOffset[0] + (int)elapsedTime, totalDuration);
                    int segmentIndex = estimateCurrentSegment(currentPosition, totalDuration);
                    
                    if (progressCallback != null) {
                        progressCallback.onProgress(currentPosition, totalDuration, segmentIndex);
                    }
                    
                    handler.postDelayed(this, 100); // Update more frequently (100ms)
                }
            }
        });
        
        // Store these values for seekTo method
        this.ttsStartTime = simulationStartTime[0];
        this.ttsSimulationOffset = simulationOffset[0];
        this.ttsTotalDuration = totalDuration;
        this.ttsText = text;
    }
    
    /**
     * Estimate the duration of TTS speech in milliseconds
     */
    private int estimateTTSDuration(String text) {
        if (text == null || text.isEmpty()) return 0;
        
        // Rough estimate: ~60-70 words per minute for TTS, average word length ~5 chars
        // So ~300-350 chars per minute or ~5 chars per second
        float charsPerMs = 5.0f / 1000.0f;
        return (int)(text.length() / charsPerMs);
    }
    
    /**
     * Speak podcast content
     * 
     * @param content Podcast content to speak
     * @return True if successful
     */
    public boolean speakPodcast(PodcastContent content) {
        if (!isInitialized || content == null) {
            return false;
        }
        
        this.segments = content.getSegments();
        if (segments.isEmpty()) {
            return false;
        }
        
        // Speak the full content
        return speak(content.getFullText());
    }
    
    /**
     * Play audio file
     * 
     * @param audioFile Audio file to play
     * @return True if successful
     */
    public boolean playAudio(File audioFile) {
        if (audioFile == null || !audioFile.exists()) {
            Log.e(TAG, "Audio file is null or does not exist");
            return false;
        }
        
        stop();
        
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mediaPlayer.prepare();
            
            // Set completion listener
            mediaPlayer.setOnCompletionListener(mp -> {
                if (progressCallback != null) {
                    progressCallback.onComplete();
                }
            });
            
            // Start playback
            mediaPlayer.start();
            
            // Start progress updates
            startProgressUpdates();
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error playing audio: " + e.getMessage());
            if (progressCallback != null) {
                progressCallback.onError("Error playing audio: " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Start progress updates
     */
    private void startProgressUpdates() {
        if (mediaPlayer == null) return;
        
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    int currentPosition = mediaPlayer.getCurrentPosition();
                    int totalDuration = mediaPlayer.getDuration();
                    int segmentIndex = estimateCurrentSegment(currentPosition, totalDuration);
                    
                    if (progressCallback != null) {
                        progressCallback.onProgress(currentPosition, totalDuration, segmentIndex);
                    }
                    
                    handler.postDelayed(this, 500);
                }
            }
        });
    }
    
    /**
     * Estimate current segment based on position
     * 
     * @param currentPosition Current position
     * @param totalDuration Total duration
     * @return Estimated segment index
     */
    private int estimateCurrentSegment(int currentPosition, int totalDuration) {
        if (segments == null || segments.isEmpty() || totalDuration <= 0) {
            return 0;
        }
        
        float progress = (float) currentPosition / totalDuration;
        return Math.min((int)(progress * segments.size()), segments.size() - 1);
    }
    
    /**
     * Stop playback
     */
    public void stop() {
        // Stop TTS
        if (tts != null && isInitialized) {
            tts.stop();
        }
        
        // Stop media player
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping media player: " + e.getMessage());
            }
        }
        
        // Remove callbacks
        handler.removeCallbacksAndMessages(null);
    }
    
    /**
     * Check if currently playing
     * 
     * @return True if playing
     */
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }
    
    /**
     * Set playback speed
     * 
     * @param speed Speed factor (0.5f - 2.0f)
     * @return True if successful
     */
    public boolean setPlaybackSpeed(float speed) {
        if (mediaPlayer == null || speed < 0.5f || speed > 2.0f) {
            return false;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error setting playback speed: " + e.getMessage());
                return false;
            }
        } else {
            Log.i(TAG, "Playback speed control not supported on this device");
            return false;
        }
    }
    
    /**
     * Seek to position
     * 
     * @param position Position in milliseconds
     */
    public void seekTo(int position) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(position);
            } catch (Exception e) {
                Log.e(TAG, "Error seeking in MediaPlayer: " + e.getMessage());
            }
        } else if (tts != null && tts.isSpeaking() && ttsTotalDuration > 0) {
            try {
                // For TTS, we need to simulate seeking by stopping and restarting at appropriate position
                isTtsSeeking = true;
                
                // Calculate what percentage of the total text to skip
                float percentage = (float) position / ttsTotalDuration;
                int textPosition = (int) (ttsText.length() * percentage);
                
                // Ensure text position is valid
                textPosition = Math.max(0, Math.min(textPosition, ttsText.length() - 1));
                
                // Find the nearest sentence boundary
                int sentenceStart = findNearestSentenceBoundary(ttsText, textPosition);
                
                // Stop current speech
                tts.stop();
                
                // Store the new offset for progress calculations
                ttsSimulationOffset = position;
                ttsStartTime = System.currentTimeMillis();
                
                // Speak the remaining text
                String remainingText = ttsText.substring(sentenceStart);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts.speak(remainingText, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_ID");
                } else {
                    HashMap<String, String> params = new HashMap<>();
                    params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "UTTERANCE_ID");
                    tts.speak(remainingText, TextToSpeech.QUEUE_FLUSH, params);
                }
                
                isTtsSeeking = false;
            } catch (Exception e) {
                Log.e(TAG, "Error seeking in TTS: " + e.getMessage());
                isTtsSeeking = false;
            }
        }
    }
    
    /**
     * Helper method to find nearest sentence boundary
     */
    private int findNearestSentenceBoundary(String text, int position) {
        if (text == null || text.isEmpty() || position >= text.length()) {
            return 0;
        }
        
        // Search backward for the start of the current sentence
        int start = position;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (c == '.' || c == '!' || c == '?') {
                break;
            }
            start--;
        }
        
        return start;
    }
    
    /**
     * Get current position
     * 
     * @return Current position in milliseconds
     */
    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                Log.e(TAG, "Error getting position from MediaPlayer: " + e.getMessage());
            }
        } else if (tts != null && tts.isSpeaking() && ttsTotalDuration > 0) {
            // For TTS, calculate position based on elapsed time and offset
            long elapsedTime = System.currentTimeMillis() - ttsStartTime;
            return Math.min(ttsSimulationOffset + (int)elapsedTime, ttsTotalDuration);
        }
        return 0;
    }
    
    /**
     * Get total duration
     * 
     * @return Total duration in milliseconds
     */
    public int getTotalDuration() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception e) {
                Log.e(TAG, "Error getting duration from MediaPlayer: " + e.getMessage());
            }
        } else if (ttsTotalDuration > 0) {
            return ttsTotalDuration;
        }
        return 0;
    }
    
    /**
     * Release resources
     */
    public void shutdown() {
        stop();
        
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        
        handler.removeCallbacksAndMessages(null);
    }
    
    /**
     * Check if TTS is currently speaking
     * 
     * @return True if speaking
     */
    public boolean isSpeaking() {
        if (mediaPlayer != null) {
            return mediaPlayer.isPlaying();
        } else if (tts != null && isInitialized) {
            return tts.isSpeaking();
        }
        return false;
    }
} 