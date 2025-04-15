package com.example.aipodcast.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.example.aipodcast.PodcastPlayerActivity;
import com.example.aipodcast.R;
import com.example.aipodcast.model.PodcastContent;

/**
 * Foreground service for podcast audio playback.
 * Ensures audio playback continues even when app is in background.
 */
public class PodcastAudioService extends Service {
    private static final String TAG = "PodcastAudioService";
    private static final String CHANNEL_ID = "podcast_audio_channel";
    private static final int NOTIFICATION_ID = 1;
    
    // TTS Service
    private UnifiedTTSService ttsService;
    
    // Media session for notification controls
    private MediaSessionCompat mediaSession;
    
    // Binder for client
    private final IBinder binder = new LocalBinder();
    
    // Current podcast data
    private PodcastContent currentPodcast;
    private String podcastTitle = "Podcast";
    private boolean isPlaying = false;
    
    /**
     * Binder class for client connection
     */
    public class LocalBinder extends Binder {
        public PodcastAudioService getService() {
            return PodcastAudioService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize the media session
        createMediaSession();
        
        // Create notification channel for Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }
    }
    
    /**
     * Create a notification channel for API 26+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Podcast Audio Channel",
                    NotificationManager.IMPORTANCE_LOW);
            
            channel.setDescription("Audio playback for podcasts");
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * Initialize media session for notification controls
     */
    private void createMediaSession() {
        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        
        // Set initial playback state
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
        
        mediaSession.setPlaybackState(stateBuilder.build());
        
        // Register callback for media controls
        mediaSession.setCallback(new MediaSessionCallback());
        
        // Activate the session
        mediaSession.setActive(true);
    }
    
    /**
     * Media session callback for handling transport controls
     */
    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            // Handle play request from notification
            play();
        }
        
        @Override
        public void onPause() {
            // Handle pause request from notification
            pause();
        }
        
        @Override
        public void onSkipToPrevious() {
            // Handle previous request from notification
            skipToPrevious();
        }
        
        @Override
        public void onSkipToNext() {
            // Handle next request from notification
            skipToNext();
        }
        
        @Override
        public void onStop() {
            // Handle stop request from notification
            stop();
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");
        
        // When service is started, show a notification
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "ACTION_PLAY":
                    play();
                    break;
                case "ACTION_PAUSE":
                    pause();
                    break;
                case "ACTION_STOP":
                    stop();
                    break;
                case "ACTION_PREV":
                    skipToPrevious();
                    break;
                case "ACTION_NEXT":
                    skipToNext();
                    break;
            }
        }
        
        // Make this a foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification());
        
        // We want the service to continue running until explicitly stopped
        return START_STICKY;
    }
    
    /**
     * Create notification with media controls
     */
    private Notification createNotification() {
        // Create intent for opening activity
        Intent openIntent = new Intent(this, PodcastPlayerActivity.class);
        PendingIntent pendingOpenIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Create actions for media controls
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(podcastTitle)
                .setContentText(isPlaying ? "Playing" : "Paused")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingOpenIntent)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        
        // Add media controls
        builder.addAction(android.R.drawable.ic_media_previous, "Previous", createPendingIntent("ACTION_PREV"));
        
        if (isPlaying) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", createPendingIntent("ACTION_PAUSE"));
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Play", createPendingIntent("ACTION_PLAY"));
        }
        
        builder.addAction(android.R.drawable.ic_media_next, "Next", createPendingIntent("ACTION_NEXT"));
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", createPendingIntent("ACTION_STOP"));
        
        // Set style for media controls
        MediaStyle mediaStyle = new MediaStyle()
                .setShowActionsInCompactView(0, 1, 2) // Show prev, play/pause, next in compact view
                .setMediaSession(mediaSession.getSessionToken());
        
        builder.setStyle(mediaStyle);
        
        return builder.build();
    }
    
    /**
     * Create pending intent for notification actions
     */
    private PendingIntent createPendingIntent(String action) {
        Intent intent = new Intent(this, PodcastAudioService.class);
        intent.setAction(action);
        return PendingIntent.getService(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    
    /**
     * Initialize the TTS service
     */
    public void initializeTTS(Context context, final UnifiedTTSService.TTSCallback callback) {
        // Initialize the TTS service
        ttsService = new UnifiedTTSService(context, new UnifiedTTSService.TTSCallback() {
            @Override
            public void onTTSInitialized(boolean success) {
                if (callback != null) {
                    callback.onTTSInitialized(success);
                }
            }
            
            @Override
            public void onTTSStart(String utteranceId) {
                if (callback != null) {
                    callback.onTTSStart(utteranceId);
                }
                updatePlaybackState(true);
            }
            
            @Override
            public void onTTSDone(String utteranceId) {
                if (callback != null) {
                    callback.onTTSDone(utteranceId);
                }
                updatePlaybackState(false);
            }
            
            @Override
            public void onTTSError(String utteranceId, int errorCode) {
                if (callback != null) {
                    callback.onTTSError(utteranceId, errorCode);
                }
                updatePlaybackState(false);
            }
            
            @Override
            public void onTTSProgress(String utteranceId, int percentDone) {
                if (callback != null) {
                    callback.onTTSProgress(utteranceId, percentDone);
                }
            }
            
            @Override
            public void onTTSRangeStart(String utteranceId, int start, int end, int frame) {
                if (callback != null) {
                    callback.onTTSRangeStart(utteranceId, start, end, frame);
                }
            }
            
            @Override
            public void onTTSStop(String utteranceId, boolean interrupted) {
                if (callback != null) {
                    callback.onTTSStop(utteranceId, interrupted);
                }
                updatePlaybackState(false);
            }
        });
    }
    
    /**
     * Update playback state and notification
     */
    private void updatePlaybackState(boolean isPlaying) {
        this.isPlaying = isPlaying;
        
        // Update media session state
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
        if (isPlaying) {
            stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f);
        } else {
            stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, 0, 0);
        }
        mediaSession.setPlaybackState(stateBuilder.build());
        
        // Update notification
        NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, createNotification());
    }
    
    /**
     * Play podcast content
     */
    public boolean play(PodcastContent content) {
        if (content != null) {
            this.currentPodcast = content;
            this.podcastTitle = content.getTitle();
        }
        
        return play();
    }
    
    /**
     * Play or resume
     */
    public boolean play() {
        if (ttsService != null && currentPodcast != null) {
            boolean result;
            
            if (!ttsService.isSpeaking()) {
                result = ttsService.speakPodcast(currentPodcast, "podcast_utterance");
            } else {
                // Already playing
                result = true;
            }
            
            if (result) {
                isPlaying = true;
                updatePlaybackState(true);
            }
            
            return result;
        }
        return false;
    }
    
    /**
     * Pause playback
     */
    public void pause() {
        if (ttsService != null) {
            ttsService.stop();
            isPlaying = false;
            updatePlaybackState(false);
        }
    }
    
    /**
     * Stop playback and service
     */
    public void stop() {
        if (ttsService != null) {
            ttsService.stop();
        }
        
        isPlaying = false;
        updatePlaybackState(false);
        
        // Stop the foreground service
        stopForeground(true);
        stopSelf();
    }
    
    /**
     * Skip to previous segment
     */
    public void skipToPrevious() {
        // Implement based on TTS service segment navigation
        // For now, this is just a placeholder
        Log.d(TAG, "Skip to previous segment called");
    }
    
    /**
     * Skip to next segment
     */
    public void skipToNext() {
        // Implement based on TTS service segment navigation
        // For now, this is just a placeholder
        Log.d(TAG, "Skip to next segment called");
    }
    
    /**
     * Set playback speed
     */
    public boolean setPlaybackSpeed(float speed) {
        if (ttsService != null) {
            ttsService.setSpeechRate(speed);
            return true;
        }
        return false;
    }
    
    /**
     * Get current position
     */
    public int getCurrentPosition() {
        // This method is a placeholder since UnifiedTTSService doesn't track position directly
        return 0;
    }
    
    /**
     * Get total duration
     */
    public int getTotalDuration() {
        // This method is a placeholder since UnifiedTTSService doesn't track duration directly
        return 0;
    }
    
    /**
     * Get current playback state
     */
    public boolean isPlaying() {
        return isPlaying;
    }
    
    @Override
    @Nullable
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onDestroy() {
        if (ttsService != null) {
            ttsService.release();
            ttsService = null;
        }
        
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        
        super.onDestroy();
    }
} 