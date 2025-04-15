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
public class PodcastAudioService extends Service {
    private static final String TAG = "PodcastAudioService";
    private static final String CHANNEL_ID = "podcast_audio_channel";
    private static final int NOTIFICATION_ID = 1;
    private UnifiedTTSService ttsService;
    private MediaSessionCompat mediaSession;
    private final IBinder binder = new LocalBinder();
    private PodcastContent currentPodcast;
    private String podcastTitle = "Podcast";
    private boolean isPlaying = false;
    public class LocalBinder extends Binder {
        public PodcastAudioService getService() {
            return PodcastAudioService.this;
        }
    }
    @Override
    public void onCreate() {
        super.onCreate();
        createMediaSession();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }
    }
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
    private void createMediaSession() {
        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
        mediaSession.setPlaybackState(stateBuilder.build());
        mediaSession.setCallback(new MediaSessionCallback());
        mediaSession.setActive(true);
    }
    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            play();
        }
        @Override
        public void onPause() {
            pause();
        }
        @Override
        public void onSkipToPrevious() {
            skipToPrevious();
        }
        @Override
        public void onSkipToNext() {
            skipToNext();
        }
        @Override
        public void onStop() {
            stop();
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");
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
        startForeground(NOTIFICATION_ID, createNotification());
        return START_STICKY;
    }
    private Notification createNotification() {
        Intent openIntent = new Intent(this, PodcastPlayerActivity.class);
        PendingIntent pendingOpenIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(podcastTitle)
                .setContentText(isPlaying ? "Playing" : "Paused")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingOpenIntent)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        builder.addAction(android.R.drawable.ic_media_previous, "Previous", createPendingIntent("ACTION_PREV"));
        if (isPlaying) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", createPendingIntent("ACTION_PAUSE"));
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Play", createPendingIntent("ACTION_PLAY"));
        }
        builder.addAction(android.R.drawable.ic_media_next, "Next", createPendingIntent("ACTION_NEXT"));
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", createPendingIntent("ACTION_STOP"));
        MediaStyle mediaStyle = new MediaStyle()
                .setShowActionsInCompactView(0, 1, 2) 
                .setMediaSession(mediaSession.getSessionToken());
        builder.setStyle(mediaStyle);
        return builder.build();
    }
    private PendingIntent createPendingIntent(String action) {
        Intent intent = new Intent(this, PodcastAudioService.class);
        intent.setAction(action);
        return PendingIntent.getService(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public void initializeTTS(Context context, final UnifiedTTSService.TTSCallback callback) {
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
    private void updatePlaybackState(boolean isPlaying) {
        this.isPlaying = isPlaying;
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
        if (isPlaying) {
            stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f);
        } else {
            stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, 0, 0);
        }
        mediaSession.setPlaybackState(stateBuilder.build());
        NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, createNotification());
    }
    public boolean play(PodcastContent content) {
        if (content != null) {
            this.currentPodcast = content;
            this.podcastTitle = content.getTitle();
        }
        return play();
    }
    public boolean play() {
        if (ttsService != null && currentPodcast != null) {
            boolean result;
            if (!ttsService.isSpeaking()) {
                result = ttsService.speakPodcast(currentPodcast, "podcast_utterance");
            } else {
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
    public void pause() {
        if (ttsService != null) {
            ttsService.stop();
            isPlaying = false;
            updatePlaybackState(false);
        }
    }
    public void stop() {
        if (ttsService != null) {
            ttsService.stop();
        }
        isPlaying = false;
        updatePlaybackState(false);
        stopForeground(true);
        stopSelf();
    }
    public void skipToPrevious() {
        Log.d(TAG, "Skip to previous segment called");
    }
    public void skipToNext() {
        Log.d(TAG, "Skip to next segment called");
    }
    public boolean setPlaybackSpeed(float speed) {
        if (ttsService != null) {
            ttsService.setSpeechRate(speed);
            return true;
        }
        return false;
    }
    public int getCurrentPosition() {
        return 0;
    }
    public int getTotalDuration() {
        return 0;
    }
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