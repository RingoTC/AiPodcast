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
import java.util.ArrayList;
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
    
    // Fields for chunked playback
    private List<String> currentChunks = null;
    private int currentChunkIndex = 0;
    private boolean isChunkedPlayback = false;
    
    // Track current speech rate
    private float currentSpeechRate = 1.0f;
    
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
                    currentSpeechRate = 0.9f;
                    tts.setSpeechRate(currentSpeechRate);
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
        
        // Log text size for debugging
        Log.d(TAG, "Speaking text of length: " + text.length() + " characters");
        
        // Check for text size limits that might cause TTS issues
        if (text.length() > 4000) {
            Log.w(TAG, "Text exceeds recommended TTS size limit (4000 chars). Consider using chunked approach.");
        }
        
        // Stop any current playback
        stop();
        
        // Set up utterance progress listener for word tracking
        if (wordTrackingCallback != null) {
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                private int wordIndex = 0;
                
                @Override
                public void onStart(String utteranceId) {
                    Log.d(TAG, "TTS started speaking utterance: " + utteranceId);
                    wordIndex = 0;
                }
                
                @Override
                public void onDone(String utteranceId) {
                    Log.d(TAG, "TTS finished speaking utterance: " + utteranceId);
                    if (progressCallback != null) {
                        handler.post(() -> progressCallback.onComplete());
                    }
                }
                
                @Override
                public void onError(String utteranceId) {
                    // 记录详细错误日志
                    Log.e(TAG, "TTS Engine error for utteranceId: " + utteranceId);
                    
                    // 尝试获取更具体的错误信息
                    String errorMsg = "TTS error";
                    try {
                        int errorCode = -1;
                        if (utteranceId != null && utteranceId.contains("_error_")) {
                            // 有些TTS引擎会在utteranceId中包含错误代码
                            String[] parts = utteranceId.split("_error_");
                            if (parts.length > 1) {
                                errorCode = Integer.parseInt(parts[1]);
                                errorMsg = "TTS error code: " + errorCode;
                            }
                        }
                        
                        // 根据常见TTS错误码提供更具体的信息
                        if (errorCode == TextToSpeech.ERROR) {
                            errorMsg = "General TTS error occurred";
                        } else if (errorCode == TextToSpeech.ERROR_SYNTHESIS) {
                            errorMsg = "TTS synthesis error - text may be too complex";
                        } else if (errorCode == TextToSpeech.ERROR_SERVICE) {
                            errorMsg = "TTS service error - TTS engine may not be available";
                        } else if (errorCode == TextToSpeech.ERROR_OUTPUT) {
                            errorMsg = "TTS output error - audio output issue";
                        } else if (errorCode == TextToSpeech.ERROR_NETWORK) {
                            errorMsg = "TTS network error - check internet connection";
                        } else if (errorCode == TextToSpeech.ERROR_NETWORK_TIMEOUT) {
                            errorMsg = "TTS network timeout - server took too long to respond";
                        } else if (errorCode == TextToSpeech.ERROR_INVALID_REQUEST) {
                            errorMsg = "TTS invalid request - malformed input text";
                        } else if (errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) {
                            errorMsg = "TTS engine not fully installed";
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing TTS error code: " + e.getMessage());
                    }
                    
                    // 检查TTS引擎状态
                    if (tts != null) {
                        try {
                            if (!tts.isSpeaking()) {
                                errorMsg += " (Engine not speaking)";
                            }
                            
                            // 检查当前语言设置，采用更安全的方法
                            try {
                                Locale currentLocale = tts.getLanguage();
                                if (currentLocale == null) {
                                    errorMsg += " (No language set)";
                                } else {
                                    int langResult = tts.isLanguageAvailable(currentLocale);
                                    if (langResult == TextToSpeech.LANG_MISSING_DATA) {
                                        errorMsg += " (Language data missing)";
                                    } else if (langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                                        errorMsg += " (Language not supported)";
                                    }
                                }
                            } catch (Exception langEx) {
                                errorMsg += " (Error checking language: " + langEx.getMessage() + ")";
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error checking TTS status: " + e.getMessage());
                        }
                    } else {
                        errorMsg += " (TTS engine is null)";
                    }
                    
                    // Additional logging to help diagnose issues with large text
                    if (text.length() > 1000) {
                        Log.e(TAG, "TTS error occurred with large text (" + text.length() + " chars)");
                        errorMsg += " - Consider using chunked playback for large text";
                    }
                    
                    // 提供错误信息给UI
                    final String finalErrorMsg = errorMsg;
                    if (progressCallback != null) {
                        handler.post(() -> progressCallback.onError(finalErrorMsg));
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
        
        // Speak the text and capture the result
        int result;
        String utteranceId = "SPEECH_" + System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
        
        // Check if speech request was accepted
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS speak() call failed with error code: " + result);
            
            // Provide error message based on result code
            String errorMessage = "Failed to start TTS playback";
            switch (result) {
                case TextToSpeech.ERROR:
                    errorMessage += " (General error)";
                    break;
                case TextToSpeech.ERROR_SYNTHESIS:
                    errorMessage += " (Synthesis error - text may be too complex)";
                    break;
                case TextToSpeech.ERROR_SERVICE:
                    errorMessage += " (Service error - TTS engine unavailable)";
                    break;
                case TextToSpeech.ERROR_OUTPUT:
                    errorMessage += " (Output error - audio system issue)";
                    break;
                case TextToSpeech.ERROR_NETWORK:
                    errorMessage += " (Network error)";
                    break;
                case TextToSpeech.ERROR_INVALID_REQUEST:
                    errorMessage += " (Invalid request)";
                    break;
                case TextToSpeech.ERROR_NOT_INSTALLED_YET:
                    errorMessage += " (TTS engine not fully installed)";
                    break;
            }
            
            // Notify UI of error
            if (progressCallback != null) {
                final String finalErrorMessage = errorMessage;
                handler.post(() -> progressCallback.onError(finalErrorMessage));
            }
            
            return false;
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
        
        Log.d(TAG, "Starting TTS progress simulation, estimated duration: " + totalDuration + "ms");
        
        // Store these values for position tracking and seeking
        ttsStartTime = startTime;
        ttsSimulationOffset = 0;
        ttsTotalDuration = totalDuration;
        ttsText = text;
        
        // Create a runnable that updates progress at fixed intervals
        Runnable progressUpdater = new Runnable() {
            @Override
            public void run() {
                // Only update if TTS is still speaking and not seeking
                if (tts != null && tts.isSpeaking() && !isTtsSeeking) {
                    // Calculate current position
                    long elapsedTime = System.currentTimeMillis() - ttsStartTime;
                    
                    // Apply playback speed adjustment if not default
                    if (currentSpeechRate != 1.0f) {
                        elapsedTime = (long)(elapsedTime * currentSpeechRate);
                    }
                    
                    // Calculate current position with offset
                    int currentPosition = ttsSimulationOffset + (int)elapsedTime;
                    
                    // Ensure position doesn't exceed total duration
                    currentPosition = Math.min(currentPosition, totalDuration);
                    
                    // Estimate which segment we're in
                    int segmentIndex = estimateCurrentSegment(currentPosition, totalDuration);
                    
                    // Send progress update to callback
                    if (progressCallback != null) {
                        progressCallback.onProgress(currentPosition, totalDuration, segmentIndex);
                    }
                    
                    // Schedule next update - use smaller interval for smoother updates
                    handler.postDelayed(this, 50); // 50ms = 20 updates per second
                } else if (!tts.isSpeaking() && !isTtsSeeking) {
                    // TTS stopped speaking but we didn't get an onDone callback
                    // This is a fallback to ensure we always get a completion event
                    if (progressCallback != null) {
                        handler.post(() -> progressCallback.onComplete());
                    }
                }
            }
        };
        
        // Start the progress updates
        handler.post(progressUpdater);
    }
    
    /**
     * Estimate the duration of TTS speech in milliseconds
     */
    private int estimateTTSDuration(String text) {
        if (text == null || text.isEmpty()) return 0;
        
        // Improved algorithm for more accurate duration estimation
        // Count words (approximately) for better accuracy
        String[] words = text.split("\\s+");
        int wordCount = words.length;
        
        // Average speaking rate is about 150-170 words per minute
        // Using 160 words per minute = 2.67 words per second
        float wordsPerSecond = 2.67f;
        
        // Add extra time for punctuation pauses
        int sentenceCount = countSentences(text);
        int pauseTimeMs = sentenceCount * 300; // ~300ms pause per sentence
        
        // Calculate based on word count
        int calculatedDuration = Math.round((wordCount / wordsPerSecond) * 1000) + pauseTimeMs;
        
        // Ensure minimum reasonable duration
        return Math.max(calculatedDuration, 1000); // At least 1 second
    }
    
    /**
     * Count approximate number of sentences in text
     */
    private int countSentences(String text) {
        if (text == null || text.isEmpty()) return 0;
        
        // Count sentence-ending punctuation
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                count++;
            }
        }
        
        // Ensure at least one sentence
        return Math.max(count, 1);
    }
    
    /**
     * Speak podcast content
     * 
     * @param content Podcast content to speak
     * @return True if successful
     */
    public boolean speakPodcast(PodcastContent content) {
        if (!isInitialized || content == null) {
            Log.e(TAG, "TTS not initialized or content is null");
            return false;
        }
        
        this.segments = content.getSegments();
        if (segments.isEmpty()) {
            Log.e(TAG, "No segments in podcast content");
            return false;
        }
        
        String fullText = content.getFullText();
        if (fullText == null || fullText.isEmpty()) {
            Log.e(TAG, "Empty full text in podcast content");
            return false;
        }
        
        // Check if text is too large for TTS engine to handle at once
        // Many TTS engines have limits around 4000 chars
        final int MAX_TTS_CHUNK_SIZE = 4000;
        
        if (fullText.length() > MAX_TTS_CHUNK_SIZE) {
            Log.d(TAG, "Podcast text is large (" + fullText.length() + " chars), using chunked playback");
            return speakLargeContent(fullText, MAX_TTS_CHUNK_SIZE);
        }
        
        // For smaller content, use normal speak method
        return speak(fullText);
    }
    
    /**
     * Handle playback of large text content by breaking it into chunks
     * 
     * @param text The full text to speak
     * @param maxChunkSize Maximum size of each chunk
     * @return True if playback started successfully
     */
    private boolean speakLargeContent(String text, int maxChunkSize) {
        // Stop any current playback
        stop();
        
        if (text == null || text.isEmpty()) {
            Log.e(TAG, "Cannot speak empty text");
            return false;
        }
        
        try {
            // Split text into manageable chunks at sentence boundaries
            List<String> chunks = new ArrayList<>();
            
            // Try to split at paragraph boundaries first
            String[] paragraphs = text.split("\n\n");
            if (paragraphs.length <= 1) {
                // If no paragraphs found, try sentences
                paragraphs = new String[]{text};
            }
            
            StringBuilder currentChunk = new StringBuilder();
            for (String paragraph : paragraphs) {
                if (paragraph == null || paragraph.isEmpty()) {
                    continue;
                }
                
                // Check if adding this paragraph would exceed our chunk size
                if (currentChunk.length() + paragraph.length() > maxChunkSize) {
                    // If the current chunk already has content, add it to our chunks
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString());
                        currentChunk = new StringBuilder();
                    }
                    
                    // If the paragraph itself is too large, split it into sentences
                    if (paragraph.length() > maxChunkSize) {
                        // Split by sentence-ending punctuation followed by space
                        String[] sentences = paragraph.split("(?<=[.!?])\\s+");
                        for (String sentence : sentences) {
                            if (sentence == null || sentence.isEmpty()) {
                                continue;
                            }
                            
                            if (currentChunk.length() + sentence.length() > maxChunkSize) {
                                if (currentChunk.length() > 0) {
                                    chunks.add(currentChunk.toString());
                                    currentChunk = new StringBuilder();
                                }
                                
                                // If a single sentence is too long, we'll have to split arbitrarily
                                if (sentence.length() > maxChunkSize) {
                                    int start = 0;
                                    while (start < sentence.length()) {
                                        int end = Math.min(start + maxChunkSize, sentence.length());
                                        chunks.add(sentence.substring(start, end));
                                        start = end;
                                    }
                                } else {
                                    currentChunk.append(sentence).append(" ");
                                }
                            } else {
                                currentChunk.append(sentence).append(" ");
                            }
                        }
                    } else {
                        currentChunk.append(paragraph).append("\n\n");
                    }
                } else {
                    currentChunk.append(paragraph).append("\n\n");
                }
            }
            
            // Add the last chunk if it has content
            if (currentChunk.length() > 0) {
                chunks.add(currentChunk.toString());
            }
            
            Log.d(TAG, "Split podcast text into " + chunks.size() + " chunks for TTS");
            
            if (chunks.isEmpty()) {
                Log.e(TAG, "Failed to split text into chunks");
                return false;
            }
            
            // Store full text for seeking
            ttsText = text;
            
            // Set total duration estimate
            ttsTotalDuration = estimateTTSDuration(text);
            
            // Begin playing the first chunk
            playChunksSequentially(chunks, 0);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error in speakLargeContent: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Play text chunks sequentially
     * 
     * @param chunks List of text chunks
     * @param index Current chunk index to play
     */
    private void playChunksSequentially(List<String> chunks, int index) {
        if (index >= chunks.size()) {
            // We've played all chunks
            isChunkedPlayback = false;
            currentChunks = null;
            currentChunkIndex = 0;
            
            if (progressCallback != null) {
                handler.post(() -> progressCallback.onComplete());
            }
            return;
        }
        
        // Update chunked playback state
        isChunkedPlayback = true;
        currentChunks = chunks;
        currentChunkIndex = index;
        
        // Get the current chunk to play
        String chunk = chunks.get(index);
        
        // Set up a one-time completion listener for this chunk
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // Nothing special to do on start
            }
            
            @Override
            public void onDone(String utteranceId) {
                // Play the next chunk after a small delay
                handler.postDelayed(() -> {
                    if (tts != null && isInitialized) {
                        playChunksSequentially(chunks, index + 1);
                    }
                }, 300); // Small delay between chunks for natural pauses
            }
            
            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS Error while playing chunk " + index);
                
                // Report error but try to continue with next chunk
                if (progressCallback != null) {
                    handler.post(() -> progressCallback.onError("TTS error on chunk " + index));
                }
                
                // Try to play next chunk even if this one failed
                handler.postDelayed(() -> {
                    if (tts != null && isInitialized) {
                        playChunksSequentially(chunks, index + 1);
                    }
                }, 300);
            }
            
            @Override
            public void onRangeStart(String utteranceId, int start, int end, int frame) {
                // For word highlighting we can still expose the individual words
                if (wordTrackingCallback != null && start >= 0 && end > start && end <= chunk.length()) {
                    try {
                        String word = chunk.substring(start, end);
                        // Calculate a global word index based on chunk position
                        int globalWordIndex = index * 500 + start; // Approximate word index
                        handler.post(() -> wordTrackingCallback.onWordSpoken(word, globalWordIndex));
                    } catch (Exception e) {
                        Log.e(TAG, "Error tracking word in chunk: " + e.getMessage());
                    }
                }
            }
        });
        
        // Speak this chunk
        Log.d(TAG, "Playing chunk " + (index + 1) + " of " + chunks.size() + " (length: " + chunk.length() + ")");
        
        String utteranceId = "CHUNK_" + index;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            tts.speak(chunk, TextToSpeech.QUEUE_FLUSH, params);
        }
        
        // Set up the progress simulation for this chunk
        int chunkDuration = estimateTTSDuration(chunk);
        float progressInPodcast = (float)index / chunks.size();
        int estimatedTotalDuration = estimateTTSDuration(String.join("", chunks));
        
        // Estimate offset based on current chunk position
        ttsSimulationOffset = (int)(progressInPodcast * estimatedTotalDuration);
        ttsStartTime = System.currentTimeMillis();
        ttsTotalDuration = estimatedTotalDuration;
        ttsText = String.join("", chunks); // Keep full text for seeking
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
        Log.d(TAG, "Stopping TTS playback, isChunkedPlayback=" + isChunkedPlayback);
        
        // Stop TTS
        if (tts != null && isInitialized) {
            tts.stop();
        }
        
        // Reset chunked playback state
        isChunkedPlayback = false;
        currentChunks = null;
        currentChunkIndex = 0;
        
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
        // Validate speed range
        if (speed < 0.5f || speed > 2.0f) {
            Log.w(TAG, "Requested playback speed out of range: " + speed);
            speed = Math.max(0.5f, Math.min(2.0f, speed));
        }
        
        Log.d(TAG, "Setting playback speed to " + speed);
        
        if (mediaPlayer != null) {
            // For MediaPlayer, use PlaybackParams when available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Error setting MediaPlayer speed: " + e.getMessage());
                    return false;
                }
            } else {
                Log.i(TAG, "Playback speed control not supported on this Android version");
                return false;
            }
        } else if (tts != null && isInitialized) {
            try {
                // For TTS, adjust the speech rate
                // Need to recalculate our time tracking when speed changes
                long currentTime = System.currentTimeMillis();
                
                // If currently speaking, adjust the simulation offset based on elapsed time
                if (tts.isSpeaking() && !isTtsSeeking) {
                    // Calculate current position at the old rate
                    int currentPosition = getCurrentPosition();
                    
                    // Update simulation parameters for new rate
                    ttsSimulationOffset = currentPosition;
                    ttsStartTime = currentTime;
                }
                
                // Store the speech rate value
                currentSpeechRate = speed;
                
                // Set the new speech rate
                tts.setSpeechRate(speed);
                Log.d(TAG, "Set TTS speech rate to " + speed);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error setting TTS speed: " + e.getMessage());
                return false;
            }
        }
        
        return false;
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
                Log.d(TAG, "MediaPlayer seeked to: " + position + "ms");
            } catch (Exception e) {
                Log.e(TAG, "Error seeking in MediaPlayer: " + e.getMessage());
            }
        } else if (tts != null && isInitialized && ttsTotalDuration > 0) {
            try {
                Log.d(TAG, "Seeking TTS to position: " + position + "ms of " + ttsTotalDuration + "ms total");
                
                // For TTS, we need to simulate seeking by stopping and restarting at appropriate position
                isTtsSeeking = true;
                
                // Stop current speech
                tts.stop();
                
                // If the text is null, we can't seek
                if (ttsText == null || ttsText.isEmpty()) {
                    Log.e(TAG, "Cannot seek - ttsText is null or empty");
                    isTtsSeeking = false;
                    return;
                }
                
                // Calculate the percentage through the content
                float percentage = Math.min(1.0f, Math.max(0.0f, (float) position / ttsTotalDuration));
                
                // Store the new time reference points for progress calculations
                ttsSimulationOffset = position;
                ttsStartTime = System.currentTimeMillis();
                
                // Calculate character position based on percentage
                int approximateCharPosition = Math.min((int) (ttsText.length() * percentage), ttsText.length() - 1);
                approximateCharPosition = Math.max(0, approximateCharPosition);
                
                // For skip operations, we want precise control, not sentence-based seeking
                // Only find the nearest sentence boundary if we're not doing a small skip
                boolean isSmallSkip = false;
                
                // Check if this is a small skip (like 10 seconds) rather than a major seek
                if (tts.isSpeaking()) {
                    int currentPosition = getCurrentPosition();
                    int skipAmount = Math.abs(position - currentPosition);
                    isSmallSkip = skipAmount <= 15000; // Skip of 15 seconds or less is considered small
                    
                    if (isSmallSkip) {
                        Log.d(TAG, "Small skip detected (" + skipAmount + "ms), using precise seeking");
                    }
                }
                
                int textPosition;
                
                if (isSmallSkip) {
                    // For small skips, use the exact character position
                    textPosition = approximateCharPosition;
                    // Find the nearest word boundary for smoother playback
                    textPosition = findNearestWordBoundary(ttsText, textPosition);
                } else {
                    // For larger seeks, find nearest sentence boundary for more natural playback
                    textPosition = findNearestSentenceBoundary(ttsText, approximateCharPosition);
                }
                
                Log.d(TAG, "Seeking to char position " + textPosition + " of " + ttsText.length() + 
                      " (requested position: " + position + "ms, " + percentage * 100 + "%)");
                
                // Get the remaining text
                String remainingText;
                try {
                    remainingText = ttsText.substring(textPosition);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting substring for remaining text: " + e.getMessage(), e);
                    // Fall back to the full text if there's an error
                    remainingText = ttsText;
                }
                
                if (remainingText.isEmpty()) {
                    Log.w(TAG, "Remaining text is empty, seeking to the beginning");
                    remainingText = ttsText;
                    ttsSimulationOffset = 0; // Reset offset if seeking to beginning
                }
                
                // For chunked playback, we need to find the appropriate chunk
                final int MAX_TTS_CHUNK_SIZE = 4000;
                
                if (isChunkedPlayback && currentChunks != null && !currentChunks.isEmpty()) {
                    // Handle seeking in chunked mode
                    handleChunkedSeeking(position, percentage);
                } else if (remainingText.length() > MAX_TTS_CHUNK_SIZE) {
                    // For large text, use chunked approach
                    final String finalRemainingText = remainingText;
                    Log.d(TAG, "Remaining text is large (" + remainingText.length() + " chars), using chunked seeking");
                    
                    handler.post(() -> {
                        try {
                            // Split remaining text into manageable chunks
                            List<String> newChunks = splitTextIntoChunks(finalRemainingText, MAX_TTS_CHUNK_SIZE);
                            
                            if (!newChunks.isEmpty()) {
                                // Update chunk tracking state
                                isChunkedPlayback = true;
                                currentChunks = newChunks;
                                currentChunkIndex = 0;
                                
                                // Start playing from first chunk
                                playChunksSequentially(newChunks, 0);
                            } else {
                                Log.e(TAG, "Failed to create chunks for seeking");
                                // Fall back to simple speak method
                                speak(finalRemainingText);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error during chunked seeking: " + e.getMessage(), e);
                            // If chunking fails, try direct speech
                            speak(finalRemainingText);
                        }
                    });
                } else {
                    // For smaller remaining text, use standard speak method
                    Log.d(TAG, "Speaking remaining text directly (length: " + remainingText.length() + ")");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        tts.speak(remainingText, TextToSpeech.QUEUE_FLUSH, null, "SEEK_UTTERANCE_ID");
                    } else {
                        HashMap<String, String> params = new HashMap<>();
                        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "SEEK_UTTERANCE_ID");
                        tts.speak(remainingText, TextToSpeech.QUEUE_FLUSH, params);
                    }
                }
                
                isTtsSeeking = false;
            } catch (Exception e) {
                Log.e(TAG, "Error seeking in TTS: " + e.getMessage(), e);
                isTtsSeeking = false;
            }
        } else {
            Log.w(TAG, "Cannot seek - TTS not speaking or no duration available");
        }
    }
    
    /**
     * Handle seeking when in chunked playback mode
     * 
     * @param position Requested position in milliseconds
     * @param percentage Percentage through total content
     */
    private void handleChunkedSeeking(int position, float percentage) {
        if (currentChunks == null || currentChunks.isEmpty()) {
            Log.e(TAG, "Cannot seek - no chunks available");
            return;
        }
        
        // Calculate total text length across all chunks
        int totalLength = 0;
        for (String chunk : currentChunks) {
            totalLength += chunk.length();
        }
        
        // Calculate target position in combined text
        int targetPosition = (int)(totalLength * percentage);
        
        // Find which chunk contains this position
        int accumulatedLength = 0;
        int targetChunkIndex = 0;
        int positionInChunk = 0;
        
        for (int i = 0; i < currentChunks.size(); i++) {
            String chunk = currentChunks.get(i);
            if (targetPosition < accumulatedLength + chunk.length()) {
                targetChunkIndex = i;
                positionInChunk = targetPosition - accumulatedLength;
                break;
            }
            accumulatedLength += chunk.length();
        }
        
        // Get the chunk text from the determined position
        String targetChunk = currentChunks.get(targetChunkIndex);
        
        // Find nearest sentence boundary in this chunk
        int sentenceStart = findNearestSentenceBoundary(targetChunk, positionInChunk);
        
        // Get remaining text in this chunk
        String remainingChunkText = targetChunk.substring(sentenceStart);
        
        // Adjust the ttsSimulationOffset based on completed chunks
        float completedChunksPercentage = (float)targetChunkIndex / currentChunks.size();
        ttsSimulationOffset = (int)(completedChunksPercentage * ttsTotalDuration);
        
        // Start playing from this chunk
        Log.d(TAG, "Seeking to chunk " + targetChunkIndex + " of " + currentChunks.size() + 
              ", position " + sentenceStart + " in chunk");
        
        // Update the current chunk index for progress tracking
        currentChunkIndex = targetChunkIndex;
        
        // Create a new list with the remaining chunks
        List<String> remainingChunks = new ArrayList<>();
        
        // Add current chunk (from sentence boundary)
        remainingChunks.add(remainingChunkText);
        
        // Add all subsequent chunks
        for (int i = targetChunkIndex + 1; i < currentChunks.size(); i++) {
            remainingChunks.add(currentChunks.get(i));
        }
        
        // Start playing the remaining chunks
        playChunksSequentially(remainingChunks, 0);
    }
    
    /**
     * Split text into manageable chunks for TTS
     * 
     * @param text Text to split
     * @param maxChunkSize Maximum size of each chunk
     * @return List of text chunks
     */
    private List<String> splitTextIntoChunks(String text, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        
        // Try to split at paragraph boundaries first
        String[] paragraphs = text.split("\n\n");
        if (paragraphs.length <= 1) {
            // If no paragraphs found, treat as one block
            paragraphs = new String[]{text};
        }
        
        StringBuilder currentChunk = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            if (paragraph == null || paragraph.isEmpty()) {
                continue;
            }
            
            // Check if adding this paragraph would exceed chunk size
            if (currentChunk.length() + paragraph.length() > maxChunkSize) {
                // Add current chunk if not empty
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }
                
                // Handle paragraph larger than max chunk size
                if (paragraph.length() > maxChunkSize) {
                    // Split by sentences
                    String[] sentences = paragraph.split("(?<=[.!?])\\s+");
                    
                    for (String sentence : sentences) {
                        if (sentence == null || sentence.isEmpty()) continue;
                        
                        if (currentChunk.length() + sentence.length() > maxChunkSize) {
                            // Add current chunk if not empty
                            if (currentChunk.length() > 0) {
                                chunks.add(currentChunk.toString());
                                currentChunk = new StringBuilder();
                            }
                            
                            // Handle sentences larger than max chunk size
                            if (sentence.length() > maxChunkSize) {
                                int start = 0;
                                while (start < sentence.length()) {
                                    int end = Math.min(start + maxChunkSize, sentence.length());
                                    chunks.add(sentence.substring(start, end));
                                    start = end;
                                }
                            } else {
                                currentChunk.append(sentence).append(" ");
                            }
                        } else {
                            currentChunk.append(sentence).append(" ");
                        }
                    }
                } else {
                    currentChunk.append(paragraph).append("\n\n");
                }
            } else {
                currentChunk.append(paragraph).append("\n\n");
            }
        }
        
        // Add the last chunk if not empty
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
        
        Log.d(TAG, "Split text into " + chunks.size() + " chunks");
        return chunks;
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
     * Find nearest word boundary in text
     * 
     * @param text The text to search in
     * @param position Approximate character position
     * @return Position of nearest word boundary
     */
    private int findNearestWordBoundary(String text, int position) {
        if (text == null || text.isEmpty() || position >= text.length()) {
            return 0;
        }
        
        // Look for nearby space or punctuation
        int start = position;
        int end = position;
        
        // Search backward for word boundary
        while (start > 0) {
            char c = text.charAt(start);
            if (c == ' ' || c == '\n' || c == '.' || c == ',' || c == '!' || c == '?') {
                start++; // Move to start of next word
                break;
            }
            start--;
        }
        
        // Search forward for word boundary
        while (end < text.length() - 1) {
            char c = text.charAt(end);
            if (c == ' ' || c == '\n' || c == '.' || c == ',' || c == '!' || c == '?') {
                break;
            }
            end++;
        }
        
        // Choose the closer boundary
        if (position - start <= end - position) {
            return Math.max(0, start);
        } else {
            return Math.min(text.length() - 1, end);
        }
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
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - ttsStartTime;
            
            // For chunked playback, provide more accurate position
            if (isChunkedPlayback && currentChunks != null && currentChunks.size() > 0) {
                // Calculate position based on chunks completed plus current chunk progress
                int totalProcessedText = 0;
                int totalTextLength = 0;
                
                // Sum up all text to get total length
                for (String chunk : currentChunks) {
                    totalTextLength += chunk.length();
                }
                
                // Sum up completed chunks
                for (int i = 0; i < currentChunkIndex; i++) {
                    totalProcessedText += currentChunks.get(i).length();
                }
                
                // Calculate progress as percentage of total text processed
                float progressPercent = totalTextLength > 0 ? 
                    (float)totalProcessedText / totalTextLength : 0;
                
                // Apply progress percentage to total duration
                int basePosition = (int)(ttsTotalDuration * progressPercent);
                
                // Add elapsed time in current chunk, but cap it to current chunk's estimated duration
                String currentChunkText = currentChunkIndex < currentChunks.size() ? 
                    currentChunks.get(currentChunkIndex) : "";
                int currentChunkDuration = estimateTTSDuration(currentChunkText);
                int currentChunkProgress = (int)Math.min(elapsedTime, currentChunkDuration);
                
                // Combine base position with current chunk progress
                int calculatedPosition = basePosition + currentChunkProgress;
                
                // Log position occasionally for debugging
                if (calculatedPosition % 5000 < 100) { // Log approximately every 5 seconds
                    Log.d(TAG, "Position (chunked): " + calculatedPosition + "ms, chunk " + 
                          (currentChunkIndex + 1) + "/" + currentChunks.size());
                }
                
                return Math.min(calculatedPosition, ttsTotalDuration);
            } else {
                // Standard calculation for non-chunked playback
                int calculatedPosition = ttsSimulationOffset + (int)elapsedTime;
                
                // Apply playback speed adjustment if set
                if (currentSpeechRate != 1.0f) {
                    calculatedPosition = ttsSimulationOffset + (int)(elapsedTime * currentSpeechRate);
                }
                
                // Log position occasionally for debugging
                if (calculatedPosition % 5000 < 100) { // Log approximately every 5 seconds
                    Log.d(TAG, "Position (standard): " + calculatedPosition + "ms of " + ttsTotalDuration + "ms");
                }
                
                return Math.min(calculatedPosition, ttsTotalDuration);
            }
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
            // We already have a calculated total duration
            return ttsTotalDuration;
        } else if (ttsText != null && !ttsText.isEmpty()) {
            // Calculate based on full text
            ttsTotalDuration = estimateTTSDuration(ttsText);
            return ttsTotalDuration;
        } else if (isChunkedPlayback && currentChunks != null && !currentChunks.isEmpty()) {
            // Calculate total duration based on all chunks
            int totalDuration = 0;
            for (String chunk : currentChunks) {
                totalDuration += estimateTTSDuration(chunk);
            }
            
            // Save calculated duration for future use
            ttsTotalDuration = totalDuration > 0 ? totalDuration : 60000;
            return ttsTotalDuration;
        }
        
        // Default fallback duration (should rarely be used)
        return 60000; // Default to 60 seconds
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
            return tts.isSpeaking() || isChunkedPlayback;
        }
        return false;
    }
    
    /**
     * Get TTS engine instance for diagnostics
     * 
     * @return TTS engine instance
     */
    public TextToSpeech getTts() {
        return tts;
    }
    
    /**
     * Get current speech rate
     * 
     * @return Current speech rate
     */
    public float getCurrentSpeechRate() {
        return currentSpeechRate;
    }
    
    /**
     * Skip backward by specified amount of time
     * 
     * @param milliseconds Amount of time to skip backward
     */
    public void skipBackward(int milliseconds) {
        Log.d(TAG, "Skipping backward by " + milliseconds + "ms");
        
        // Calculate new position (current position minus skip amount)
        int currentPosition = getCurrentPosition();
        int newPosition = Math.max(0, currentPosition - milliseconds);
        
        Log.d(TAG, "Current position: " + currentPosition + "ms, new position: " + newPosition + "ms");
        
        // Perform seek operation to the new position
        seekTo(newPosition);
    }
    
    /**
     * Skip forward by specified amount of time
     * 
     * @param milliseconds Amount of time to skip forward
     */
    public void skipForward(int milliseconds) {
        Log.d(TAG, "Skipping forward by " + milliseconds + "ms");
        
        // Calculate new position (current position plus skip amount)
        int currentPosition = getCurrentPosition();
        int totalDuration = getTotalDuration();
        int newPosition = Math.min(totalDuration, currentPosition + milliseconds);
        
        Log.d(TAG, "Current position: " + currentPosition + "ms, new position: " + newPosition + "ms");
        
        // Perform seek operation to the new position
        seekTo(newPosition);
    }
} 