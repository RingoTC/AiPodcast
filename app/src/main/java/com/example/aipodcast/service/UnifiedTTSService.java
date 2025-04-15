package com.example.aipodcast.service;
import android.content.Context;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import com.example.aipodcast.model.PodcastContent;
import java.io.File;
import java.util.Locale;
public class UnifiedTTSService {
    private static final String TAG = "UnifiedTTSService";
    private TextToSpeech textToSpeech;
    private boolean isInitialized = false;
    private MediaPlayer mediaPlayer;
    private TTSCallback callback;
    private Context context;
    public interface TTSCallback {
        void onTTSInitialized(boolean success);
        void onTTSStart(String utteranceId);
        void onTTSDone(String utteranceId);
        void onTTSError(String utteranceId, int errorCode);
        void onTTSProgress(String utteranceId, int percentDone);
        void onTTSRangeStart(String utteranceId, int start, int end, int frame);
        void onTTSStop(String utteranceId, boolean interrupted);
    }
    public UnifiedTTSService(Context context, TTSCallback callback) {
        this.context = context;
        this.callback = callback;
        initializeTTS();
    }
    private void initializeTTS() {
        textToSpeech = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.US);
                    if (result == TextToSpeech.LANG_MISSING_DATA || 
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        isInitialized = false;
                        Log.e(TAG, "Language not supported");
                        if (callback != null) {
                            callback.onTTSInitialized(false);
                        }
                    } else {
                        isInitialized = true;
                        Log.d(TAG, "TTS initialized successfully");
                        textToSpeech.setPitch(1.0f);
                        textToSpeech.setSpeechRate(1.0f);
                        textToSpeech.setOnUtteranceProgressListener(
                                new UtteranceProgressListener() {
                                    @Override
                                    public void onStart(String utteranceId) {
                                        if (callback != null) {
                                            callback.onTTSStart(utteranceId);
                                        }
                                    }
                                    @Override
                                    public void onDone(String utteranceId) {
                                        if (callback != null) {
                                            callback.onTTSDone(utteranceId);
                                        }
                                    }
                                    @Override
                                    public void onError(String utteranceId) {
                                        if (callback != null) {
                                            callback.onTTSError(utteranceId, -1);
                                        }
                                    }
                                    @Override
                                    public void onRangeStart(String utteranceId, 
                                                         int start, int end, int frame) {
                                        if (callback != null) {
                                            callback.onTTSRangeStart(utteranceId, start, end, frame);
                                        }
                                    }
                                });
                        if (callback != null) {
                            callback.onTTSInitialized(true);
                        }
                    }
                } else {
                    isInitialized = false;
                    Log.e(TAG, "Failed to initialize TTS");
                    if (callback != null) {
                        callback.onTTSInitialized(false);
                    }
                }
            }
        });
    }
    public boolean speak(String text, String utteranceId) {
        if (!isInitialized || text == null || text.isEmpty()) {
            return false;
        }
        stop();
        int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        return result == TextToSpeech.SUCCESS;
    }
    public boolean speakPodcast(PodcastContent content, String utteranceId) {
        if (!isInitialized || content == null || 
            content.getFullText() == null || content.getFullText().isEmpty()) {
            return false;
        }
        stop();
        int result = textToSpeech.speak(content.getFullText(), 
                TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        return result == TextToSpeech.SUCCESS;
    }
    public boolean playAudio(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error playing audio: " + e.getMessage(), e);
            return false;
        }
    }
    public void stop() {
        if (isInitialized && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
            if (callback != null) {
                callback.onTTSStop("", true);
            }
        }
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
    public void setSpeechRate(float rate) {
        if (isInitialized) {
            textToSpeech.setSpeechRate(rate);
        }
    }
    public void setPitch(float pitch) {
        if (isInitialized) {
            textToSpeech.setPitch(pitch);
        }
    }
    public boolean isSpeaking() {
        return isInitialized && textToSpeech.isSpeaking();
    }
    public void release() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        isInitialized = false;
    }
} 