package com.example.aipodcast.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.aipodcast.model.PodcastContent;
import com.example.aipodcast.model.PodcastSegment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for podcast playback.
 * Provides a unified interface for managing podcast audio playback.
 */
public class PodcastPlayerController {
    private static final String TAG = "PodcastPlayerController";
    
    // ExoPlayer manager
    private ExoPlayerManager exoPlayerManager;
    
    // Callback for player events
    private PlayerCallback playerCallback;
    
    // Current podcast content
    private PodcastContent podcastContent;
    private int currentSegmentIndex = 0;
    
    // Playback state
    private boolean isPlaying = false;
    private float playbackSpeed = 1.0f;
    
    /**
     * Callback interface for player events
     */
    public interface PlayerCallback {
        void onPlaybackStateChanged(boolean isPlaying);
        void onProgress(long position, long duration, int segmentIndex);
        void onError(String errorMessage);
        void onSegmentChanged(int segmentIndex);
        void onPlaybackComplete();
    }
    
    /**
     * Constructor
     * 
     * @param context Application context
     * @param callback Callback for player events
     */
    public PodcastPlayerController(Context context, PlayerCallback callback) {
        this.playerCallback = callback;
        
        // Initialize ExoPlayer manager
        initializeExoPlayer(context);
    }
    
    /**
     * Initialize ExoPlayer manager
     * 
     * @param context Application context
     */
    private void initializeExoPlayer(Context context) {
        exoPlayerManager = new ExoPlayerManager(context, new ExoPlayerManager.PlayerCallback() {
            @Override
            public void onPlaybackStateChanged(boolean isPlaying) {
                PodcastPlayerController.this.isPlaying = isPlaying;
                if (playerCallback != null) {
                    playerCallback.onPlaybackStateChanged(isPlaying);
                }
            }
            
            @Override
            public void onProgress(long position, long duration) {
                if (playerCallback != null) {
                    // Calculate current segment based on position
                    int segmentIndex = estimateCurrentSegment(position, duration);
                    if (segmentIndex != currentSegmentIndex) {
                        currentSegmentIndex = segmentIndex;
                        playerCallback.onSegmentChanged(segmentIndex);
                    }
                    
                    playerCallback.onProgress(position, duration, segmentIndex);
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                if (playerCallback != null) {
                    playerCallback.onError(errorMessage);
                }
            }
            
            @Override
            public void onMediaItemTransition(int index) {
                // Handle media item transition if needed
            }
            
            @Override
            public void onPlaybackComplete() {
                if (playerCallback != null) {
                    playerCallback.onPlaybackComplete();
                }
            }
        });
    }
    
    /**
     * Estimate current segment based on position
     * 
     * @param position Current position
     * @param duration Total duration
     * @return Estimated segment index
     */
    private int estimateCurrentSegment(long position, long duration) {
        if (podcastContent == null || podcastContent.getSegments().isEmpty() || duration <= 0) {
            return 0;
        }
        
        float progress = (float) position / duration;
        return Math.min((int)(progress * podcastContent.getSegments().size()), 
                podcastContent.getSegments().size() - 1);
    }
    
    /**
     * Set podcast content
     * 
     * @param content Podcast content
     */
    public void setPodcastContent(PodcastContent content) {
        this.podcastContent = content;
        currentSegmentIndex = 0;
    }
    
    /**
     * Play audio file
     * 
     * @param file Audio file
     * @return True if successful
     */
    public boolean playFile(File file) {
        if (file == null || !file.exists()) {
            if (playerCallback != null) {
                playerCallback.onError("Audio file not found");
            }
            return false;
        }
        
        try {
            exoPlayerManager.setMediaFile(file);
            exoPlayerManager.play();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error playing file: " + e.getMessage(), e);
            if (playerCallback != null) {
                playerCallback.onError("Error playing audio: " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Play multiple audio files
     * 
     * @param files List of audio files
     * @return True if successful
     */
    public boolean playFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            if (playerCallback != null) {
                playerCallback.onError("No audio files provided");
            }
            return false;
        }
        
        try {
            exoPlayerManager.setMediaFiles(files);
            exoPlayerManager.play();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error playing files: " + e.getMessage(), e);
            if (playerCallback != null) {
                playerCallback.onError("Error playing audio files: " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Play podcast content
     * 
     * @param content Podcast content
     * @return True if successful
     */
    public boolean playPodcast(PodcastContent content) {
        this.podcastContent = content;
        
        // For now, just play the content as a single stream
        // In a more advanced implementation, we could generate audio files for each segment
        
        return play();
    }
    
    /**
     * Play or resume playback
     * 
     * @return True if successful
     */
    public boolean play() {
        if (exoPlayerManager != null) {
            exoPlayerManager.play();
            return true;
        }
        return false;
    }
    
    /**
     * Pause playback
     */
    public void pause() {
        if (exoPlayerManager != null) {
            exoPlayerManager.pause();
        }
    }
    
    /**
     * Stop playback
     */
    public void stop() {
        if (exoPlayerManager != null) {
            exoPlayerManager.stop();
        }
    }
    
    /**
     * Skip to next segment
     * 
     * @return True if successful
     */
    public boolean skipToNextSegment() {
        if (podcastContent == null || podcastContent.getSegments().isEmpty()) {
            return false;
        }
        
        int nextIndex = Math.min(currentSegmentIndex + 1, podcastContent.getSegments().size() - 1);
        return skipToSegment(nextIndex);
    }
    
    /**
     * Skip to previous segment
     * 
     * @return True if successful
     */
    public boolean skipToPreviousSegment() {
        if (podcastContent == null || podcastContent.getSegments().isEmpty()) {
            return false;
        }
        
        int prevIndex = Math.max(0, currentSegmentIndex - 1);
        return skipToSegment(prevIndex);
    }
    
    /**
     * Skip to specific segment
     * 
     * @param segmentIndex Segment index
     * @return True if successful
     */
    public boolean skipToSegment(int segmentIndex) {
        if (podcastContent == null || segmentIndex < 0 || 
            segmentIndex >= podcastContent.getSegments().size() || exoPlayerManager == null) {
            return false;
        }
        
        // Calculate position based on segment index
        long duration = exoPlayerManager.getDuration();
        float segmentProgress = (float) segmentIndex / podcastContent.getSegments().size();
        long position = (long) (segmentProgress * duration);
        
        exoPlayerManager.seekTo(position);
        currentSegmentIndex = segmentIndex;
        
        if (playerCallback != null) {
            playerCallback.onSegmentChanged(segmentIndex);
        }
        
        return true;
    }
    
    /**
     * Seek to position
     * 
     * @param position Position in milliseconds
     */
    public void seekTo(long position) {
        if (exoPlayerManager != null) {
            exoPlayerManager.seekTo(position);
        }
    }
    
    /**
     * Set playback speed
     * 
     * @param speed Playback speed (0.5f - 2.0f)
     */
    public void setPlaybackSpeed(float speed) {
        if (speed < 0.5f || speed > 2.0f) {
            return;
        }
        
        this.playbackSpeed = speed;
        if (exoPlayerManager != null) {
            exoPlayerManager.setPlaybackSpeed(speed);
        }
    }
    
    /**
     * Get current playback position
     * 
     * @return Current position in milliseconds
     */
    public long getCurrentPosition() {
        return exoPlayerManager != null ? exoPlayerManager.getCurrentPosition() : 0;
    }
    
    /**
     * Get total duration
     * 
     * @return Total duration in milliseconds
     */
    public long getDuration() {
        return exoPlayerManager != null ? exoPlayerManager.getDuration() : 0;
    }
    
    /**
     * Get current segment index
     * 
     * @return Current segment index
     */
    public int getCurrentSegmentIndex() {
        return currentSegmentIndex;
    }
    
    /**
     * Get current segment
     * 
     * @return Current segment or null if none
     */
    public PodcastSegment getCurrentSegment() {
        if (podcastContent != null && currentSegmentIndex >= 0 && 
            currentSegmentIndex < podcastContent.getSegments().size()) {
            return podcastContent.getSegments().get(currentSegmentIndex);
        }
        return null;
    }
    
    /**
     * Check if currently playing
     * 
     * @return True if playing
     */
    public boolean isPlaying() {
        return exoPlayerManager != null && exoPlayerManager.isPlaying();
    }
    
    /**
     * Release resources
     */
    public void release() {
        if (exoPlayerManager != null) {
            exoPlayerManager.release();
            exoPlayerManager = null;
        }
    }
} 