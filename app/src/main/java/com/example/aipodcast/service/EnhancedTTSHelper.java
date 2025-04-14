package com.example.aipodcast.service;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;

import com.example.aipodcast.model.PodcastContent;
import com.example.aipodcast.model.PodcastSegment;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced Text-To-Speech helper that provides advanced TTS functionality
 * for generating and playing podcast content.
 */
public class EnhancedTTSHelper {
    private static final String TAG = "EnhancedTTSHelper";
    private TextToSpeech tts;
    private boolean isInitialized = false;
    private Context context;
    private File outputDirectory;
    private ProgressListener progressListener;
    private int currentSegmentIndex = 0;
    private List<PodcastSegment> segments;
    private MediaPlayer mediaPlayer;
    
    /**
     * Interface for tracking TTS progress
     */
    public interface ProgressListener {
        void onPrepared();
        void onProgress(int segmentIndex, int totalSegments, String currentText);
        void onSegmentComplete(int segmentIndex);
        void onComplete();
        void onError(String errorMessage);
    }
    
    /**
     * Constructor
     * 
     * @param context Application context
     */
    public EnhancedTTSHelper(Context context) {
        this.context = context;
        setupOutputDirectory();
        
        // Initialize TTS engine
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                isInitialized = result != TextToSpeech.LANG_MISSING_DATA &&
                                result != TextToSpeech.LANG_NOT_SUPPORTED;
                
                if (isInitialized) {
                    setupTTSParameters();
                    Log.d(TAG, "TTS initialized successfully");
                } else {
                    Log.e(TAG, "Language not supported");
                }
            } else {
                Log.e(TAG, "TTS initialization failed with status: " + status);
            }
        });
    }
    
    /**
     * Set up TTS parameters for optimal podcast speech
     */
    private void setupTTSParameters() {
        tts.setSpeechRate(0.9f);  // Slightly slower for better comprehension
        tts.setPitch(1.0f);       // Default pitch
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                // Try to set a clear voice for podcasts if available
                for (Voice voice : tts.getVoices()) {
                    if (voice.getName().contains("en-us") && 
                        (voice.getName().contains("premium") || voice.getName().contains("neural"))) {
                        tts.setVoice(voice);
                        Log.d(TAG, "Using high-quality voice: " + voice.getName());
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting voice: " + e.getMessage());
            }
        }
    }
    
    /**
     * Set up output directory for audio files
     */
    private void setupOutputDirectory() {
        // Create a folder for podcast audio files
        File externalDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        outputDirectory = new File(externalDir, "Podcasts");
        if (!outputDirectory.exists()) {
            boolean created = outputDirectory.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create output directory");
            }
        }
    }
    
    /**
     * Set progress listener
     * 
     * @param listener The listener to be notified of progress
     */
    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }
    
    /**
     * Speak the given podcast content
     * 
     * @param content The podcast content to speak
     * @return True if the content was successfully queued for speech
     */
    public boolean speak(PodcastContent content) {
        if (!isInitialized) {
            Log.e(TAG, "TTS not initialized");
            if (progressListener != null) {
                progressListener.onError("Text-to-speech engine not initialized");
            }
            return false;
        }
        
        this.segments = content.getSegments();
        this.currentSegmentIndex = 0;
        
        if (segments.isEmpty()) {
            Log.e(TAG, "No segments to speak");
            if (progressListener != null) {
                progressListener.onError("No content to speak");
            }
            return false;
        }
        
        // Set up progress listener
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "Started speaking segment: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                if (utteranceId.startsWith("segment_")) {
                    int segmentIndex = Integer.parseInt(utteranceId.substring(8));
                    
                    if (progressListener != null) {
                        progressListener.onSegmentComplete(segmentIndex);
                    }
                    
                    // Queue next segment if available
                    if (segmentIndex < segments.size() - 1) {
                        currentSegmentIndex = segmentIndex + 1;
                        speakSegment(currentSegmentIndex);
                    } else {
                        // All segments complete
                        if (progressListener != null) {
                            progressListener.onComplete();
                        }
                    }
                }
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "Error speaking segment: " + utteranceId);
                if (progressListener != null) {
                    progressListener.onError("Error speaking segment: " + utteranceId);
                }
            }
            
            // Required for older Android versions
            @Override
            public void onError(String utteranceId, int errorCode) {
                Log.e(TAG, "Error speaking segment: " + utteranceId + ", code: " + errorCode);
                if (progressListener != null) {
                    progressListener.onError("Error speaking segment: " + utteranceId);
                }
            }
        });
        
        // Start speaking the first segment
        speakSegment(0);
        return true;
    }
    
    /**
     * Speak a specific segment
     * 
     * @param segmentIndex Index of the segment to speak
     */
    private void speakSegment(int segmentIndex) {
        if (segmentIndex >= segments.size()) {
            Log.e(TAG, "Invalid segment index: " + segmentIndex);
            return;
        }
        
        PodcastSegment segment = segments.get(segmentIndex);
        String text = segment.getText();
        
        if (progressListener != null) {
            progressListener.onProgress(segmentIndex, segments.size(), text);
        }
        
        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "segment_" + segmentIndex);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "segment_" + segmentIndex);
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
    }
    
    /**
     * Generate an audio file from the podcast content
     * 
     * @param content The podcast content to synthesize
     * @return A CompletableFuture that will complete with the generated file
     */
    public CompletableFuture<File> synthesizeToFile(PodcastContent content) {
        CompletableFuture<File> future = new CompletableFuture<>();
        
        if (!isInitialized) {
            future.completeExceptionally(new Exception("TTS not initialized"));
            return future;
        }
        
        // Create a filename based on the podcast title and date
        String safeTitle = content.getTitle().replaceAll("[^a-zA-Z0-9]", "_");
        String filename = safeTitle + "_" + UUID.randomUUID().toString().substring(0, 8) + ".wav";
        File outputFile = new File(outputDirectory, filename);
        
        // Concatenate all segments
        String fullText = content.getFullText();
        
        // Set up synthesis parameters
        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "synthesis");
        
        // Set up listener
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "Started synthesis");
                if (progressListener != null) {
                    progressListener.onProgress(0, 1, "Synthesizing audio...");
                }
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "Synthesis complete: " + outputFile.getAbsolutePath());
                if (progressListener != null) {
                    progressListener.onComplete();
                }
                future.complete(outputFile);
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "Synthesis error");
                future.completeExceptionally(new Exception("Error synthesizing audio"));
            }
            
            @Override
            public void onError(String utteranceId, int errorCode) {
                Log.e(TAG, "Synthesis error code: " + errorCode);
                future.completeExceptionally(new Exception("Error synthesizing audio: " + errorCode));
            }
        });
        
        // Start synthesis
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            result = tts.synthesizeToFile(fullText, null, outputFile, "synthesis");
        } else {
            result = tts.synthesizeToFile(fullText, params, outputFile.getAbsolutePath());
        }
        
        if (result != TextToSpeech.SUCCESS) {
            future.completeExceptionally(new Exception("Failed to start synthesis, error code: " + result));
        }
        
        return future;
    }
    
    /**
     * Stop current speech
     */
    public void stop() {
        if (tts != null && isInitialized) {
            tts.stop();
        }
        
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
    }
    
    /**
     * Play audio file with progress updates
     * 
     * @param audioFile The audio file to play
     * @param content The corresponding podcast content
     */
    public void playAudioFile(File audioFile, PodcastContent content) {
        if (mediaPlayer != null) {
            stop();
        }
        
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mediaPlayer.prepare();
            
            // Set up completion listener
            mediaPlayer.setOnCompletionListener(mp -> {
                if (progressListener != null) {
                    progressListener.onComplete();
                }
            });
            
            // Start playback
            mediaPlayer.start();
            
            if (progressListener != null) {
                progressListener.onPrepared();
            }
            
            // Start a thread to track progress
            this.segments = content.getSegments();
            new Thread(() -> {
                int totalDuration = mediaPlayer.getDuration();
                int segmentCount = segments.size();
                
                while (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    try {
                        int currentPosition = mediaPlayer.getCurrentPosition();
                        float progress = (float) currentPosition / totalDuration;
                        
                        // Estimate current segment based on progress
                        int segmentIndex = Math.min((int)(progress * segmentCount), segmentCount - 1);
                        
                        if (progressListener != null && segmentIndex != currentSegmentIndex) {
                            currentSegmentIndex = segmentIndex;
                            progressListener.onProgress(
                                segmentIndex, 
                                segmentCount, 
                                segments.get(segmentIndex).getText()
                            );
                        }
                        
                        Thread.sleep(500);  // Update every half second
                    } catch (Exception e) {
                        Log.e(TAG, "Error tracking media progress: " + e.getMessage());
                        break;
                    }
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing audio file: " + e.getMessage());
            if (progressListener != null) {
                progressListener.onError("Error playing audio file: " + e.getMessage());
            }
        }
    }
    
    /**
     * Check if TTS is initialized
     * 
     * @return True if TTS is ready to use
     */
    public boolean isInitialized() {
        return isInitialized;
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
    }
} 