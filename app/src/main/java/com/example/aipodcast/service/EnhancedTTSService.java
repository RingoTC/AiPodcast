package com.example.aipodcast.service;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
public class EnhancedTTSService {
    private static final String TAG = "EnhancedTTSService";
    private TextToSpeech alexTTS;
    private TextToSpeech jordanTTS;
    private TextToSpeech tts; 
    private boolean isAlexInitialized = false;
    private boolean isJordanInitialized = false;
    private boolean isTtsInitialized = false;
    private String currentSpeaker = null;
    private float alexPitch = 1.0f;
    private float alexRate = 0.9f;
    private float jordanPitch = 0.85f; 
    private float jordanRate = 0.95f;
    private float hostPitch = 1.0f;
    private float hostRate = 1.0f;
    private TTSCallback callback;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private float currentSpeechRate = 1.0f;
    public interface TTSCallback {
        void onSpeakStart(String speaker);
        void onWordSpoken(String speaker, String word, int wordIndex);
        void onSpeakComplete(String speaker);
        void onInitialized();
        void onError(String message);
    }
    public EnhancedTTSService(Context context, TTSCallback callback) {
        this.callback = callback;
        initializeVoices(context);
    }
    private void initializeVoices(Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                isTtsInitialized = result != TextToSpeech.LANG_MISSING_DATA && 
                                  result != TextToSpeech.LANG_NOT_SUPPORTED;
                if (isTtsInitialized) {
                    tts.setPitch(hostPitch);
                    tts.setSpeechRate(hostRate);
                    selectVoice(tts, true);
                    Log.d(TAG, "Host TTS initialized");
                    checkInitialization();
                }
            }
        });
        alexTTS = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = alexTTS.setLanguage(Locale.US);
                isAlexInitialized = result != TextToSpeech.LANG_MISSING_DATA && 
                                   result != TextToSpeech.LANG_NOT_SUPPORTED;
                if (isAlexInitialized) {
                    alexTTS.setPitch(alexPitch);
                    alexTTS.setSpeechRate(alexRate);
                    selectVoice(alexTTS, true);
                    Log.d(TAG, "Alex TTS initialized");
                    checkInitialization();
                }
            }
        });
        jordanTTS = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = jordanTTS.setLanguage(Locale.US);
                isJordanInitialized = result != TextToSpeech.LANG_MISSING_DATA && 
                                     result != TextToSpeech.LANG_NOT_SUPPORTED;
                if (isJordanInitialized) {
                    jordanTTS.setPitch(jordanPitch);
                    jordanTTS.setSpeechRate(jordanRate);
                    selectVoice(jordanTTS, false);
                    Log.d(TAG, "Jordan TTS initialized");
                    checkInitialization();
                }
            }
        });
    }
    private void selectVoice(TextToSpeech tts, boolean male) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Set<android.speech.tts.Voice> voices = tts.getVoices();
                if (voices != null && !voices.isEmpty()) {
                    for (android.speech.tts.Voice voice : voices) {
                        if (voice.getLocale().equals(Locale.US) && voice.getQuality() >= android.speech.tts.Voice.QUALITY_NORMAL) {
                            String voiceName = voice.getName().toLowerCase();
                            boolean isMaleVoice = voiceName.contains("male") || 
                                               !voiceName.contains("female");
                            if ((male && isMaleVoice) || (!male && !isMaleVoice)) {
                                tts.setVoice(voice);
                                Log.d(TAG, "Selected voice: " + voice.getName());
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error selecting voice: " + e.getMessage());
            }
        }
    }
    private void checkInitialization() {
        if (isAlexInitialized && isJordanInitialized && isTtsInitialized) {
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onInitialized();
                }
            });
        }
    }
    public boolean speak(String speaker, String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        final String finalSpeaker = "ALEX".equals(speaker) || "JORDAN".equals(speaker) ? "HOST" : speaker;
        if (!isTtsInitialized) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("TTS not fully initialized"));
            }
            return false;
        }
        currentSpeaker = finalSpeaker;
        if (callback != null) {
            callback.onSpeakStart(finalSpeaker);
        }
        String utteranceId = finalSpeaker + "_" + System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final String finalText = text;
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override
                public void onStart(String s) {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onSpeakStart(finalSpeaker);
                        }
                    });
                }
                @Override
                public void onDone(String s) {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onSpeakComplete(finalSpeaker);
                        }
                    });
                }
                @Override
                public void onError(String s) {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onError("Error speaking with " + finalSpeaker + ": " + s);
                        }
                    });
                }
                @Override
                public void onRangeStart(String utteranceId, int start, int end, int frame) {
                    if (callback != null) {
                        String word = finalText.substring(start, end);
                        mainHandler.post(() -> {
                            callback.onWordSpoken(finalSpeaker, word, start);
                        });
                    }
                }
            });
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Bundle bundle = new Bundle();
                tts.speak(finalText, TextToSpeech.QUEUE_FLUSH, bundle, utteranceId);
            } else {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                tts.speak(finalText, TextToSpeech.QUEUE_FLUSH, params);
            }
            return true;
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
            return true;
        }
    }
    public void stop() {
        if (isTtsInitialized) {
            tts.stop();
        }
        if (isAlexInitialized) {
            alexTTS.stop();
        }
        if (isJordanInitialized) {
            jordanTTS.stop();
        }
        currentSpeaker = null;
    }
    public void shutdown() {
        stop();
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        if (alexTTS != null) {
            alexTTS.shutdown();
            alexTTS = null;
        }
        if (jordanTTS != null) {
            jordanTTS.shutdown();
            jordanTTS = null;
        }
        isTtsInitialized = false;
        isAlexInitialized = false;
        isJordanInitialized = false;
    }
    public void setSpeechRate(float rate) {
        if (rate < 0.5f || rate > 2.0f) {
            return;
        }
        currentSpeechRate = rate;
        if (isTtsInitialized) {
            tts.setSpeechRate(rate);
        }
        alexRate = rate;
        jordanRate = rate * 1.05f; 
        if (isAlexInitialized) {
            alexTTS.setSpeechRate(alexRate);
        }
        if (isJordanInitialized) {
            jordanTTS.setSpeechRate(jordanRate);
        }
    }
    public boolean isInitialized() {
        return isTtsInitialized;
    }
    public boolean testTTS() {
        if (!isTtsInitialized) {
            return false;
        }
        try {
            String testText = "Test text";
            int result = tts.speak(testText, TextToSpeech.QUEUE_FLUSH, null, "TEST_UTTERANCE");
            return result == TextToSpeech.SUCCESS;
        } catch (Exception e) {
            Log.e(TAG, "TTS test failed: " + e.getMessage());
            return false;
        }
    }
}