package com.example.aipodcast.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Manager for ExoPlayer media playback functionality.
 * Provides a simplified interface for podcast audio playback with ExoPlayer.
 */
public class ExoPlayerManager {
    private static final String TAG = "ExoPlayerManager";
    
    // Player instance
    private SimpleExoPlayer player;
    private Context context;
    
    // Callback interface for player events
    private PlayerCallback callback;
    
    // Progress tracking
    private Handler progressHandler;
    private boolean isTrackingProgress = false;
    private static final int PROGRESS_UPDATE_INTERVAL = 500; // 500ms
    
    // Media queue
    private List<MediaItem> mediaQueue = new ArrayList<>();
    private int currentItemIndex = 0;
    
    /**
     * Callback interface for player events
     */
    public interface PlayerCallback {
        void onPlaybackStateChanged(boolean isPlaying);
        void onProgress(long position, long duration);
        void onError(String errorMessage);
        void onMediaItemTransition(int index);
        void onPlaybackComplete();
    }
    
    /**
     * Constructor
     * 
     * @param context Application context
     * @param callback Callback for player events
     */
    public ExoPlayerManager(Context context, @Nullable PlayerCallback callback) {
        this.context = context;
        this.callback = callback;
        this.progressHandler = new Handler(Looper.getMainLooper());
        
        // Initialize player
        initializePlayer();
    }
    
    /**
     * Initialize ExoPlayer instance
     */
    private void initializePlayer() {
        player = new SimpleExoPlayer.Builder(context).build();
        
        // Set player event listener
        player.addListener(new Player.Listener() {
            public void onPlaybackStateChanged(int state) {
                switch (state) {
                    case Player.STATE_READY:
                        if (callback != null) {
                            callback.onPlaybackStateChanged(player.isPlaying());
                        }
                        if (player.isPlaying()) {
                            startProgressTracking();
                        }
                        break;
                    case Player.STATE_ENDED:
                        if (callback != null) {
                            callback.onPlaybackComplete();
                        }
                        stopProgressTracking();
                        break;
                    case Player.STATE_BUFFERING:
                        // Could show buffering indicator
                        break;
                    case Player.STATE_IDLE:
                        stopProgressTracking();
                        break;
                }
            }
            
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                if (callback != null) {
                    callback.onPlaybackStateChanged(playWhenReady);
                }
                
                if (playWhenReady) {
                    startProgressTracking();
                } else {
                    stopProgressTracking();
                }
            }
            
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                currentItemIndex = player.getCurrentMediaItemIndex();
                if (callback != null) {
                    callback.onMediaItemTransition(currentItemIndex);
                }
            }
            
            public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
                String errorMessage;
                
                if (error.errorCode == com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
                    errorMessage = "Network connection failed: " + error.getMessage();
                } else if (error.errorCode == com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
                    errorMessage = "Media file not found: " + error.getMessage();
                } else if (error.errorCode == com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
                    errorMessage = "Unsupported media format: " + error.getMessage();
                } else {
                    errorMessage = "Playback error: " + error.getMessage();
                }
                
                Log.e(TAG, errorMessage, error);
                
                if (callback != null) {
                    callback.onError(errorMessage);
                }
            }
        });
    }
    
    /**
     * Start progress tracking
     */
    private void startProgressTracking() {
        if (isTrackingProgress) return;
        
        isTrackingProgress = true;
        progressHandler.post(new Runnable() {
            @Override
            public void run() {
                if (player != null && isTrackingProgress) {
                    long position = player.getCurrentPosition();
                    long duration = player.getDuration();
                    
                    if (callback != null) {
                        callback.onProgress(position, duration);
                    }
                    
                    progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL);
                }
            }
        });
    }
    
    /**
     * Stop progress tracking
     */
    private void stopProgressTracking() {
        isTrackingProgress = false;
        progressHandler.removeCallbacksAndMessages(null);
    }
    
    /**
     * Set media URI for playback
     * 
     * @param uri Media URI
     */
    public void setMediaUri(Uri uri) {
        mediaQueue.clear();
        MediaItem mediaItem = MediaItem.fromUri(uri);
        mediaQueue.add(mediaItem);
        
        // Prepare the player with the media source
        player.setMediaItem(mediaItem);
        player.prepare();
    }
    
    /**
     * Set media file for playback
     * 
     * @param file Media file
     */
    public void setMediaFile(File file) {
        if (file == null || !file.exists()) {
            if (callback != null) {
                callback.onError("File does not exist");
            }
            return;
        }
        
        setMediaUri(Uri.fromFile(file));
    }
    
    /**
     * Set multiple media files for playback
     * 
     * @param files List of media files
     */
    public void setMediaFiles(List<File> files) {
        mediaQueue.clear();
        
        for (File file : files) {
            if (file.exists()) {
                mediaQueue.add(MediaItem.fromUri(Uri.fromFile(file)));
            }
        }
        
        if (mediaQueue.isEmpty()) {
            if (callback != null) {
                callback.onError("No valid files to play");
            }
            return;
        }
        
        // Prepare the player with the media queue
        player.setMediaItems(mediaQueue);
        player.prepare();
    }
    
    /**
     * Play or resume playback
     */
    public void play() {
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }
    
    /**
     * Pause playback
     */
    public void pause() {
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }
    
    /**
     * Stop playback
     */
    public void stop() {
        if (player != null) {
            player.stop();
            player.setPlayWhenReady(false);
        }
        stopProgressTracking();
    }
    
    /**
     * Skip to next media item
     * 
     * @return True if successfully skipped to next
     */
    public boolean next() {
        if (player != null && currentItemIndex < mediaQueue.size() - 1) {
            player.seekToNextMediaItem();
            return true;
        }
        return false;
    }
    
    /**
     * Skip to previous media item
     * 
     * @return True if successfully skipped to previous
     */
    public boolean previous() {
        if (player != null && currentItemIndex > 0) {
            player.seekToPreviousMediaItem();
            return true;
        }
        return false;
    }
    
    /**
     * Seek to position
     * 
     * @param position Position in milliseconds
     */
    public void seekTo(long position) {
        if (player != null) {
            player.seekTo(position);
        }
    }
    
    /**
     * Set playback speed
     * 
     * @param speed Playback speed (0.5f - 2.0f)
     */
    public void setPlaybackSpeed(float speed) {
        if (player != null) {
            player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(speed));
        }
    }
    
    /**
     * Get current playback position
     * 
     * @return Current position in milliseconds
     */
    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }
    
    /**
     * Get total duration
     * 
     * @return Total duration in milliseconds
     */
    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }
    
    /**
     * Check if currently playing
     * 
     * @return True if playing
     */
    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }
    
    /**
     * Release resources
     */
    public void release() {
        stopProgressTracking();
        
        if (player != null) {
            player.release();
            player = null;
        }
        
        progressHandler.removeCallbacksAndMessages(null);
    }
} 