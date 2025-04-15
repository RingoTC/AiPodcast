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
public class ExoPlayerManager {
    private static final String TAG = "ExoPlayerManager";
    private SimpleExoPlayer player;
    private Context context;
    private PlayerCallback callback;
    private Handler progressHandler;
    private boolean isTrackingProgress = false;
    private static final int PROGRESS_UPDATE_INTERVAL = 500; 
    private List<MediaItem> mediaQueue = new ArrayList<>();
    private int currentItemIndex = 0;
    public interface PlayerCallback {
        void onPlaybackStateChanged(boolean isPlaying);
        void onProgress(long position, long duration);
        void onError(String errorMessage);
        void onMediaItemTransition(int index);
        void onPlaybackComplete();
    }
    public ExoPlayerManager(Context context, @Nullable PlayerCallback callback) {
        this.context = context;
        this.callback = callback;
        this.progressHandler = new Handler(Looper.getMainLooper());
        initializePlayer();
    }
    private void initializePlayer() {
        player = new SimpleExoPlayer.Builder(context).build();
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
    private void stopProgressTracking() {
        isTrackingProgress = false;
        progressHandler.removeCallbacksAndMessages(null);
    }
    public void setMediaUri(Uri uri) {
        mediaQueue.clear();
        MediaItem mediaItem = MediaItem.fromUri(uri);
        mediaQueue.add(mediaItem);
        player.setMediaItem(mediaItem);
        player.prepare();
    }
    public void setMediaFile(File file) {
        if (file == null || !file.exists()) {
            if (callback != null) {
                callback.onError("File does not exist");
            }
            return;
        }
        setMediaUri(Uri.fromFile(file));
    }
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
        player.setMediaItems(mediaQueue);
        player.prepare();
    }
    public void play() {
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }
    public void pause() {
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }
    public void stop() {
        if (player != null) {
            player.stop();
            player.setPlayWhenReady(false);
        }
        stopProgressTracking();
    }
    public boolean next() {
        if (player != null && currentItemIndex < mediaQueue.size() - 1) {
            player.seekToNextMediaItem();
            return true;
        }
        return false;
    }
    public boolean previous() {
        if (player != null && currentItemIndex > 0) {
            player.seekToPreviousMediaItem();
            return true;
        }
        return false;
    }
    public void seekTo(long position) {
        if (player != null) {
            player.seekTo(position);
        }
    }
    public void setPlaybackSpeed(float speed) {
        if (player != null) {
            player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(speed));
        }
    }
    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }
    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }
    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }
    public void release() {
        stopProgressTracking();
        if (player != null) {
            player.release();
            player = null;
        }
        progressHandler.removeCallbacksAndMessages(null);
    }
} 