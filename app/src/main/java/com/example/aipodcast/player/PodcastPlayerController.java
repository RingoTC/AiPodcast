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
public class PodcastPlayerController {
    private static final String TAG = "PodcastPlayerController";
    private ExoPlayerManager exoPlayerManager;
    private PlayerCallback playerCallback;
    private PodcastContent podcastContent;
    private int currentSegmentIndex = 0;
    private boolean isPlaying = false;
    private float playbackSpeed = 1.0f;
    public interface PlayerCallback {
        void onPlaybackStateChanged(boolean isPlaying);
        void onProgress(long position, long duration, int segmentIndex);
        void onError(String errorMessage);
        void onSegmentChanged(int segmentIndex);
        void onPlaybackComplete();
    }
    public PodcastPlayerController(Context context, PlayerCallback callback) {
        this.playerCallback = callback;
        initializeExoPlayer(context);
    }
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
            }
            @Override
            public void onPlaybackComplete() {
                if (playerCallback != null) {
                    playerCallback.onPlaybackComplete();
                }
            }
        });
    }
    private int estimateCurrentSegment(long position, long duration) {
        if (podcastContent == null || podcastContent.getSegments().isEmpty() || duration <= 0) {
            return 0;
        }
        float progress = (float) position / duration;
        return Math.min((int)(progress * podcastContent.getSegments().size()), 
                podcastContent.getSegments().size() - 1);
    }
    public void setPodcastContent(PodcastContent content) {
        this.podcastContent = content;
        currentSegmentIndex = 0;
    }
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
    public boolean playPodcast(PodcastContent content) {
        this.podcastContent = content;
        return play();
    }
    public boolean play() {
        if (exoPlayerManager != null) {
            exoPlayerManager.play();
            return true;
        }
        return false;
    }
    public void pause() {
        if (exoPlayerManager != null) {
            exoPlayerManager.pause();
        }
    }
    public void stop() {
        if (exoPlayerManager != null) {
            exoPlayerManager.stop();
        }
    }
    public boolean skipToNextSegment() {
        if (podcastContent == null || podcastContent.getSegments().isEmpty()) {
            return false;
        }
        int nextIndex = Math.min(currentSegmentIndex + 1, podcastContent.getSegments().size() - 1);
        return skipToSegment(nextIndex);
    }
    public boolean skipToPreviousSegment() {
        if (podcastContent == null || podcastContent.getSegments().isEmpty()) {
            return false;
        }
        int prevIndex = Math.max(0, currentSegmentIndex - 1);
        return skipToSegment(prevIndex);
    }
    public boolean skipToSegment(int segmentIndex) {
        if (podcastContent == null || segmentIndex < 0 || 
            segmentIndex >= podcastContent.getSegments().size() || exoPlayerManager == null) {
            return false;
        }
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
    public void seekTo(long position) {
        if (exoPlayerManager != null) {
            exoPlayerManager.seekTo(position);
        }
    }
    public void setPlaybackSpeed(float speed) {
        if (speed < 0.5f || speed > 2.0f) {
            return;
        }
        this.playbackSpeed = speed;
        if (exoPlayerManager != null) {
            exoPlayerManager.setPlaybackSpeed(speed);
        }
    }
    public long getCurrentPosition() {
        return exoPlayerManager != null ? exoPlayerManager.getCurrentPosition() : 0;
    }
    public long getDuration() {
        return exoPlayerManager != null ? exoPlayerManager.getDuration() : 0;
    }
    public int getCurrentSegmentIndex() {
        return currentSegmentIndex;
    }
    public PodcastSegment getCurrentSegment() {
        if (podcastContent != null && currentSegmentIndex >= 0 && 
            currentSegmentIndex < podcastContent.getSegments().size()) {
            return podcastContent.getSegments().get(currentSegmentIndex);
        }
        return null;
    }
    public boolean isPlaying() {
        return exoPlayerManager != null && exoPlayerManager.isPlaying();
    }
    public void release() {
        if (exoPlayerManager != null) {
            exoPlayerManager.release();
            exoPlayerManager = null;
        }
    }
} 