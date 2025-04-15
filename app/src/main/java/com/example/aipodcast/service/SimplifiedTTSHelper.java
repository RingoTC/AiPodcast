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
public class SimplifiedTTSHelper {
    private static final String TAG = "SimplifiedTTSHelper";
    private TextToSpeech tts;
    private boolean isInitialized = false;
    private Context context;
    private MediaPlayer mediaPlayer;
    private List<PodcastSegment> segments;
    private int currentSegmentIndex = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ProgressCallback progressCallback;
    private WordTrackingCallback wordTrackingCallback;
    private long ttsStartTime = 0;
    private int ttsSimulationOffset = 0;
    private int ttsTotalDuration = 0;
    private String ttsText = null;
    private boolean isTtsSeeking = false;
    private List<String> currentChunks = null;
    private int currentChunkIndex = 0;
    private boolean isChunkedPlayback = false;
    private float currentSpeechRate = 1.0f;
    public interface ProgressCallback {
        void onProgress(int currentPosition, int totalDuration, int segmentIndex);
        void onComplete();
        void onError(String message);
    }
    public interface WordTrackingCallback {
        void onWordSpoken(String word, int indexInSpeech);
    }
    public interface InitCallback {
        void onInitialized(boolean success);
    }
    public SimplifiedTTSHelper(Context context) {
        this(context, null);
    }
    public SimplifiedTTSHelper(Context context, InitCallback initCallback) {
        this.context = context;
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                isInitialized = result != TextToSpeech.LANG_MISSING_DATA &&
                               result != TextToSpeech.LANG_NOT_SUPPORTED;
                if (isInitialized) {
                    currentSpeechRate = 0.9f;
                    tts.setSpeechRate(currentSpeechRate);
                    tts.setPitch(1.0f);
                    Log.d(TAG, "TTS initialized successfully");
                } else {
                    Log.e(TAG, "Language not supported");
                }
                if (initCallback != null) {
                    handler.post(() -> initCallback.onInitialized(isInitialized));
                }
            } else {
                Log.e(TAG, "TTS initialization failed with status: " + status);
                if (initCallback != null) {
                    handler.post(() -> initCallback.onInitialized(false));
                }
            }
        });
    }
    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }
    public void setWordTrackingCallback(WordTrackingCallback callback) {
        this.wordTrackingCallback = callback;
    }
    public boolean speak(String text) {
        if (!isInitialized) {
            Log.e(TAG, "TTS not initialized");
            return false;
        }
        if (text == null || text.isEmpty()) {
            Log.e(TAG, "Empty text provided");
            return false;
        }
        Log.d(TAG, "Speaking text of length: " + text.length() + " characters");
        if (text.length() > 4000) {
            Log.w(TAG, "Text exceeds recommended TTS size limit (4000 chars). Consider using chunked approach.");
        }
        stop();
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
                    Log.e(TAG, "TTS Engine error for utteranceId: " + utteranceId);
                    String errorMsg = "TTS error";
                    try {
                        int errorCode = -1;
                        if (utteranceId != null && utteranceId.contains("_error_")) {
                            String[] parts = utteranceId.split("_error_");
                            if (parts.length > 1) {
                                errorCode = Integer.parseInt(parts[1]);
                                errorMsg = "TTS error code: " + errorCode;
                            }
                        }
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
                    if (tts != null) {
                        try {
                            if (!tts.isSpeaking()) {
                                errorMsg += " (Engine not speaking)";
                            }
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
                    if (text.length() > 1000) {
                        Log.e(TAG, "TTS error occurred with large text (" + text.length() + " chars)");
                        errorMsg += " - Consider using chunked playback for large text";
                    }
                    final String finalErrorMsg = errorMsg;
                    if (progressCallback != null) {
                        handler.post(() -> progressCallback.onError(finalErrorMsg));
                    }
                }
                @Override
                public void onRangeStart(String utteranceId, int start, int end, int frame) {
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
        int result;
        String utteranceId = "SPEECH_" + System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS speak() call failed with error code: " + result);
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
            if (progressCallback != null) {
                final String finalErrorMessage = errorMessage;
                handler.post(() -> progressCallback.onError(finalErrorMessage));
            }
            return false;
        }
        startTTSProgressUpdates(text);
        return true;
    }
    private void startTTSProgressUpdates(String text) {
        final int totalDuration = estimateTTSDuration(text);
        final long startTime = System.currentTimeMillis();
        Log.d(TAG, "Starting TTS progress simulation, estimated duration: " + totalDuration + "ms");
        ttsStartTime = startTime;
        ttsSimulationOffset = 0;
        ttsTotalDuration = totalDuration;
        ttsText = text;
        Runnable progressUpdater = new Runnable() {
            @Override
            public void run() {
                if (tts != null && tts.isSpeaking() && !isTtsSeeking) {
                    long elapsedTime = System.currentTimeMillis() - ttsStartTime;
                    if (currentSpeechRate != 1.0f) {
                        elapsedTime = (long)(elapsedTime * currentSpeechRate);
                    }
                    int currentPosition = ttsSimulationOffset + (int)elapsedTime;
                    currentPosition = Math.min(currentPosition, totalDuration);
                    int segmentIndex = estimateCurrentSegment(currentPosition, totalDuration);
                    if (progressCallback != null) {
                        progressCallback.onProgress(currentPosition, totalDuration, segmentIndex);
                    }
                    handler.postDelayed(this, 50); 
                } else if (!tts.isSpeaking() && !isTtsSeeking) {
                    if (progressCallback != null) {
                        handler.post(() -> progressCallback.onComplete());
                    }
                }
            }
        };
        handler.post(progressUpdater);
    }
    private int estimateTTSDuration(String text) {
        if (text == null || text.isEmpty()) return 0;
        String[] words = text.split("\\s+");
        int wordCount = words.length;
        float wordsPerSecond = 2.67f;
        int sentenceCount = countSentences(text);
        int pauseTimeMs = sentenceCount * 300; 
        int calculatedDuration = Math.round((wordCount / wordsPerSecond) * 1000) + pauseTimeMs;
        return Math.max(calculatedDuration, 1000); 
    }
    private int countSentences(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                count++;
            }
        }
        return Math.max(count, 1);
    }
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
        final int MAX_TTS_CHUNK_SIZE = 4000;
        if (fullText.length() > MAX_TTS_CHUNK_SIZE) {
            Log.d(TAG, "Podcast text is large (" + fullText.length() + " chars), using chunked playback");
            return speakLargeContent(fullText, MAX_TTS_CHUNK_SIZE);
        }
        return speak(fullText);
    }
    private boolean speakLargeContent(String text, int maxChunkSize) {
        stop();
        if (text == null || text.isEmpty()) {
            Log.e(TAG, "Cannot speak empty text");
            return false;
        }
        try {
            List<String> chunks = new ArrayList<>();
            String[] paragraphs = text.split("\n\n");
            if (paragraphs.length <= 1) {
                paragraphs = new String[]{text};
            }
            StringBuilder currentChunk = new StringBuilder();
            for (String paragraph : paragraphs) {
                if (paragraph == null || paragraph.isEmpty()) {
                    continue;
                }
                if (currentChunk.length() + paragraph.length() > maxChunkSize) {
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString());
                        currentChunk = new StringBuilder();
                    }
                    if (paragraph.length() > maxChunkSize) {
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
            if (currentChunk.length() > 0) {
                chunks.add(currentChunk.toString());
            }
            Log.d(TAG, "Split podcast text into " + chunks.size() + " chunks for TTS");
            if (chunks.isEmpty()) {
                Log.e(TAG, "Failed to split text into chunks");
                return false;
            }
            ttsText = text;
            ttsTotalDuration = estimateTTSDuration(text);
            playChunksSequentially(chunks, 0);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error in speakLargeContent: " + e.getMessage(), e);
            return false;
        }
    }
    private void playChunksSequentially(List<String> chunks, int index) {
        if (index >= chunks.size()) {
            isChunkedPlayback = false;
            currentChunks = null;
            currentChunkIndex = 0;
            if (progressCallback != null) {
                handler.post(() -> progressCallback.onComplete());
            }
            return;
        }
        isChunkedPlayback = true;
        currentChunks = chunks;
        currentChunkIndex = index;
        String chunk = chunks.get(index);
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }
            @Override
            public void onDone(String utteranceId) {
                handler.postDelayed(() -> {
                    if (tts != null && isInitialized) {
                        playChunksSequentially(chunks, index + 1);
                    }
                }, 300); 
            }
            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS Error while playing chunk " + index);
                if (progressCallback != null) {
                    handler.post(() -> progressCallback.onError("TTS error on chunk " + index));
                }
                handler.postDelayed(() -> {
                    if (tts != null && isInitialized) {
                        playChunksSequentially(chunks, index + 1);
                    }
                }, 300);
            }
            @Override
            public void onRangeStart(String utteranceId, int start, int end, int frame) {
                if (wordTrackingCallback != null && start >= 0 && end > start && end <= chunk.length()) {
                    try {
                        String word = chunk.substring(start, end);
                        int globalWordIndex = index * 500 + start; 
                        handler.post(() -> wordTrackingCallback.onWordSpoken(word, globalWordIndex));
                    } catch (Exception e) {
                        Log.e(TAG, "Error tracking word in chunk: " + e.getMessage());
                    }
                }
            }
        });
        Log.d(TAG, "Playing chunk " + (index + 1) + " of " + chunks.size() + " (length: " + chunk.length() + ")");
        String utteranceId = "CHUNK_" + index;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            tts.speak(chunk, TextToSpeech.QUEUE_FLUSH, params);
        }
        int chunkDuration = estimateTTSDuration(chunk);
        float progressInPodcast = (float)index / chunks.size();
        int estimatedTotalDuration = estimateTTSDuration(String.join("", chunks));
        ttsSimulationOffset = (int)(progressInPodcast * estimatedTotalDuration);
        ttsStartTime = System.currentTimeMillis();
        ttsTotalDuration = estimatedTotalDuration;
        ttsText = String.join("", chunks); 
    }
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
            mediaPlayer.setOnCompletionListener(mp -> {
                if (progressCallback != null) {
                    progressCallback.onComplete();
                }
            });
            mediaPlayer.start();
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
    private int estimateCurrentSegment(int currentPosition, int totalDuration) {
        if (segments == null || segments.isEmpty() || totalDuration <= 0) {
            return 0;
        }
        float progress = (float) currentPosition / totalDuration;
        return Math.min((int)(progress * segments.size()), segments.size() - 1);
    }
    public void stop() {
        Log.d(TAG, "Stopping TTS playback, isChunkedPlayback=" + isChunkedPlayback);
        if (tts != null && isInitialized) {
            tts.stop();
        }
        isChunkedPlayback = false;
        currentChunks = null;
        currentChunkIndex = 0;
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
        handler.removeCallbacksAndMessages(null);
    }
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }
    public boolean setPlaybackSpeed(float speed) {
        if (speed < 0.5f || speed > 2.0f) {
            Log.w(TAG, "Requested playback speed out of range: " + speed);
            speed = Math.max(0.5f, Math.min(2.0f, speed));
        }
        Log.d(TAG, "Setting playback speed to " + speed);
        if (mediaPlayer != null) {
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
                long currentTime = System.currentTimeMillis();
                if (tts.isSpeaking() && !isTtsSeeking) {
                    int currentPosition = getCurrentPosition();
                    ttsSimulationOffset = currentPosition;
                    ttsStartTime = currentTime;
                }
                currentSpeechRate = speed;
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
                isTtsSeeking = true;
                tts.stop();
                if (ttsText == null || ttsText.isEmpty()) {
                    Log.e(TAG, "Cannot seek - ttsText is null or empty");
                    isTtsSeeking = false;
                    return;
                }
                float percentage = Math.min(1.0f, Math.max(0.0f, (float) position / ttsTotalDuration));
                ttsSimulationOffset = position;
                ttsStartTime = System.currentTimeMillis();
                int approximateCharPosition = Math.min((int) (ttsText.length() * percentage), ttsText.length() - 1);
                approximateCharPosition = Math.max(0, approximateCharPosition);
                boolean isSmallSkip = false;
                if (tts.isSpeaking()) {
                    int currentPosition = getCurrentPosition();
                    int skipAmount = Math.abs(position - currentPosition);
                    isSmallSkip = skipAmount <= 15000; 
                    if (isSmallSkip) {
                        Log.d(TAG, "Small skip detected (" + skipAmount + "ms), using precise seeking");
                    }
                }
                int textPosition;
                if (isSmallSkip) {
                    textPosition = approximateCharPosition;
                    textPosition = findNearestWordBoundary(ttsText, textPosition);
                } else {
                    textPosition = findNearestSentenceBoundary(ttsText, approximateCharPosition);
                }
                Log.d(TAG, "Seeking to char position " + textPosition + " of " + ttsText.length() + 
                      " (requested position: " + position + "ms, " + percentage * 100 + "%)");
                String remainingText;
                try {
                    remainingText = ttsText.substring(textPosition);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting substring for remaining text: " + e.getMessage(), e);
                    remainingText = ttsText;
                }
                if (remainingText.isEmpty()) {
                    Log.w(TAG, "Remaining text is empty, seeking to the beginning");
                    remainingText = ttsText;
                    ttsSimulationOffset = 0; 
                }
                final int MAX_TTS_CHUNK_SIZE = 4000;
                if (isChunkedPlayback && currentChunks != null && !currentChunks.isEmpty()) {
                    handleChunkedSeeking(position, percentage);
                } else if (remainingText.length() > MAX_TTS_CHUNK_SIZE) {
                    final String finalRemainingText = remainingText;
                    Log.d(TAG, "Remaining text is large (" + remainingText.length() + " chars), using chunked seeking");
                    handler.post(() -> {
                        try {
                            List<String> newChunks = splitTextIntoChunks(finalRemainingText, MAX_TTS_CHUNK_SIZE);
                            if (!newChunks.isEmpty()) {
                                isChunkedPlayback = true;
                                currentChunks = newChunks;
                                currentChunkIndex = 0;
                                playChunksSequentially(newChunks, 0);
                            } else {
                                Log.e(TAG, "Failed to create chunks for seeking");
                                speak(finalRemainingText);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error during chunked seeking: " + e.getMessage(), e);
                            speak(finalRemainingText);
                        }
                    });
                } else {
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
    private void handleChunkedSeeking(int position, float percentage) {
        if (currentChunks == null || currentChunks.isEmpty()) {
            Log.e(TAG, "Cannot seek - no chunks available");
            return;
        }
        int totalLength = 0;
        for (String chunk : currentChunks) {
            totalLength += chunk.length();
        }
        int targetPosition = (int)(totalLength * percentage);
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
        String targetChunk = currentChunks.get(targetChunkIndex);
        int sentenceStart = findNearestSentenceBoundary(targetChunk, positionInChunk);
        String remainingChunkText = targetChunk.substring(sentenceStart);
        float completedChunksPercentage = (float)targetChunkIndex / currentChunks.size();
        ttsSimulationOffset = (int)(completedChunksPercentage * ttsTotalDuration);
        Log.d(TAG, "Seeking to chunk " + targetChunkIndex + " of " + currentChunks.size() + 
              ", position " + sentenceStart + " in chunk");
        currentChunkIndex = targetChunkIndex;
        List<String> remainingChunks = new ArrayList<>();
        remainingChunks.add(remainingChunkText);
        for (int i = targetChunkIndex + 1; i < currentChunks.size(); i++) {
            remainingChunks.add(currentChunks.get(i));
        }
        playChunksSequentially(remainingChunks, 0);
    }
    private List<String> splitTextIntoChunks(String text, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        if (paragraphs.length <= 1) {
            paragraphs = new String[]{text};
        }
        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph == null || paragraph.isEmpty()) {
                continue;
            }
            if (currentChunk.length() + paragraph.length() > maxChunkSize) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }
                if (paragraph.length() > maxChunkSize) {
                    String[] sentences = paragraph.split("(?<=[.!?])\\s+");
                    for (String sentence : sentences) {
                        if (sentence == null || sentence.isEmpty()) continue;
                        if (currentChunk.length() + sentence.length() > maxChunkSize) {
                            if (currentChunk.length() > 0) {
                                chunks.add(currentChunk.toString());
                                currentChunk = new StringBuilder();
                            }
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
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
        Log.d(TAG, "Split text into " + chunks.size() + " chunks");
        return chunks;
    }
    private int findNearestSentenceBoundary(String text, int position) {
        if (text == null || text.isEmpty() || position >= text.length()) {
            return 0;
        }
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
    private int findNearestWordBoundary(String text, int position) {
        if (text == null || text.isEmpty() || position >= text.length()) {
            return 0;
        }
        int start = position;
        int end = position;
        while (start > 0) {
            char c = text.charAt(start);
            if (c == ' ' || c == '\n' || c == '.' || c == ',' || c == '!' || c == '?') {
                start++; 
                break;
            }
            start--;
        }
        while (end < text.length() - 1) {
            char c = text.charAt(end);
            if (c == ' ' || c == '\n' || c == '.' || c == ',' || c == '!' || c == '?') {
                break;
            }
            end++;
        }
        if (position - start <= end - position) {
            return Math.max(0, start);
        } else {
            return Math.min(text.length() - 1, end);
        }
    }
    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                Log.e(TAG, "Error getting position from MediaPlayer: " + e.getMessage());
            }
        } else if (tts != null && tts.isSpeaking() && ttsTotalDuration > 0) {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - ttsStartTime;
            if (isChunkedPlayback && currentChunks != null && currentChunks.size() > 0) {
                int totalProcessedText = 0;
                int totalTextLength = 0;
                for (String chunk : currentChunks) {
                    totalTextLength += chunk.length();
                }
                for (int i = 0; i < currentChunkIndex; i++) {
                    totalProcessedText += currentChunks.get(i).length();
                }
                float progressPercent = totalTextLength > 0 ? 
                    (float)totalProcessedText / totalTextLength : 0;
                int basePosition = (int)(ttsTotalDuration * progressPercent);
                String currentChunkText = currentChunkIndex < currentChunks.size() ? 
                    currentChunks.get(currentChunkIndex) : "";
                int currentChunkDuration = estimateTTSDuration(currentChunkText);
                int currentChunkProgress = (int)Math.min(elapsedTime, currentChunkDuration);
                int calculatedPosition = basePosition + currentChunkProgress;
                if (calculatedPosition % 5000 < 100) { 
                    Log.d(TAG, "Position (chunked): " + calculatedPosition + "ms, chunk " + 
                          (currentChunkIndex + 1) + "/" + currentChunks.size());
                }
                return Math.min(calculatedPosition, ttsTotalDuration);
            } else {
                int calculatedPosition = ttsSimulationOffset + (int)elapsedTime;
                if (currentSpeechRate != 1.0f) {
                    calculatedPosition = ttsSimulationOffset + (int)(elapsedTime * currentSpeechRate);
                }
                if (calculatedPosition % 5000 < 100) { 
                    Log.d(TAG, "Position (standard): " + calculatedPosition + "ms of " + ttsTotalDuration + "ms");
                }
                return Math.min(calculatedPosition, ttsTotalDuration);
            }
        }
        return 0;
    }
    public int getTotalDuration() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception e) {
                Log.e(TAG, "Error getting duration from MediaPlayer: " + e.getMessage());
            }
        } else if (ttsTotalDuration > 0) {
            return ttsTotalDuration;
        } else if (ttsText != null && !ttsText.isEmpty()) {
            ttsTotalDuration = estimateTTSDuration(ttsText);
            return ttsTotalDuration;
        } else if (isChunkedPlayback && currentChunks != null && !currentChunks.isEmpty()) {
            int totalDuration = 0;
            for (String chunk : currentChunks) {
                totalDuration += estimateTTSDuration(chunk);
            }
            ttsTotalDuration = totalDuration > 0 ? totalDuration : 60000;
            return ttsTotalDuration;
        }
        return 60000; 
    }
    public void shutdown() {
        stop();
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        handler.removeCallbacksAndMessages(null);
    }
    public boolean isSpeaking() {
        if (mediaPlayer != null) {
            return mediaPlayer.isPlaying();
        } else if (tts != null && isInitialized) {
            return tts.isSpeaking() || isChunkedPlayback;
        }
        return false;
    }
    public TextToSpeech getTts() {
        return tts;
    }
    public float getCurrentSpeechRate() {
        return currentSpeechRate;
    }
    public void skipBackward(int milliseconds) {
        Log.d(TAG, "Skipping backward by " + milliseconds + "ms");
        int currentPosition = getCurrentPosition();
        int newPosition = Math.max(0, currentPosition - milliseconds);
        Log.d(TAG, "Current position: " + currentPosition + "ms, new position: " + newPosition + "ms");
        seekTo(newPosition);
    }
    public void skipForward(int milliseconds) {
        Log.d(TAG, "Skipping forward by " + milliseconds + "ms");
        int currentPosition = getCurrentPosition();
        int totalDuration = getTotalDuration();
        int newPosition = Math.min(totalDuration, currentPosition + milliseconds);
        Log.d(TAG, "Current position: " + currentPosition + "ms, new position: " + newPosition + "ms");
        seekTo(newPosition);
    }
} 