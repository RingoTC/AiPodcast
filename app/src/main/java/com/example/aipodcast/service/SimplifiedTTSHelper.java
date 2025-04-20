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
    // Optimal TTS chunk size (characters)
    private static final int OPTIMAL_CHUNK_SIZE = 2000;

    // Word count and speaking rate constants
    private static final float DEFAULT_WORDS_PER_SECOND = 2.33f;
    private String cleanTextForTTS(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Remove §HOST§ markers
        String cleaned = text.replace("§HOST§", "");

        // Remove any other markers like "HOST:" if present
        cleaned = cleaned.replaceAll("\\bHOST:\\s*", "");

        // Fix any double spaces created by removing markers
        cleaned = cleaned.replaceAll("\\s+", " ");

        return cleaned.trim();
    }
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
                    currentSpeechRate = 1.0f;
                    tts.setSpeechRate(currentSpeechRate);
                    tts.setPitch(1.0f);

                    // Enable playback progress callbacks
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        try {
                            // Some devices may not support this feature
                            tts.setOnUtteranceProgressListener(createUtteranceProgressListener());
                        } catch (Exception e) {
                            Log.e(TAG, "Error setting utterance progress listener: " + e.getMessage());
                        }
                    }

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
    private android.speech.tts.UtteranceProgressListener createUtteranceProgressListener() {
        return new android.speech.tts.UtteranceProgressListener() {
            private int wordIndex = 0;

            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "TTS started speaking utterance: " + utteranceId);
                wordIndex = 0;
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "TTS finished speaking utterance: " + utteranceId);

                if (isChunkedPlayback && currentChunks != null &&
                        currentChunkIndex < currentChunks.size() - 1) {
                    // Move to next chunk
                    currentChunkIndex++;
                    playNextChunk();
                } else if (progressCallback != null) {
                    handler.post(() -> progressCallback.onComplete());
                }
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS Engine error for utteranceId: " + utteranceId);
                String errorMsg = "TTS error occurred";

                if (progressCallback != null) {
                    handler.post(() -> progressCallback.onError(errorMsg));
                }

                // Try to continue with next chunk if in chunked mode
                if (isChunkedPlayback && currentChunks != null &&
                        currentChunkIndex < currentChunks.size() - 1) {
                    currentChunkIndex++;
                    playNextChunk();
                }
            }

            @Override
            public void onRangeStart(String utteranceId, int start, int end, int frame) {
                if (wordTrackingCallback != null) {
                    try {
                        String text = isChunkedPlayback && currentChunks != null ?
                                currentChunks.get(currentChunkIndex) : ttsText;

                        if (text != null && start >= 0 && end > start && end <= text.length()) {
                            String word = text.substring(start, end);
                            wordIndex++;

                            // Calculate global word index for chunked playback
                            int globalWordIndex = wordIndex;
                            if (isChunkedPlayback && currentChunkIndex > 0) {
                                // Add approximate word count from previous chunks
                                for (int i = 0; i < currentChunkIndex; i++) {
                                    String chunk = currentChunks.get(i);
                                    globalWordIndex += chunk.split("\\s+").length;
                                }
                            }

                            final int finalGlobalWordIndex = globalWordIndex;
                            handler.post(() -> wordTrackingCallback.onWordSpoken(word, finalGlobalWordIndex));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error tracking word: " + e.getMessage());
                    }
                }
            }
        };
    }
    private void playNextChunk() {
        if (!isChunkedPlayback || currentChunks == null ||
                currentChunkIndex >= currentChunks.size()) {
            return;
        }

        String chunk = currentChunks.get(currentChunkIndex);
        String utteranceId = "CHUNK_" + currentChunkIndex;

        Log.d(TAG, "Playing chunk " + (currentChunkIndex + 1) + " of " +
                currentChunks.size() + " (length: " + chunk.length() + " chars)");

        // Update offset for progress tracking
        if (currentChunkIndex > 0) {
            // Calculate approximate duration of all chunks played so far
            int totalCharsPlayed = 0;
            for (int i = 0; i < currentChunkIndex; i++) {
                totalCharsPlayed += currentChunks.get(i).length();
            }

            float progress = (float) totalCharsPlayed /
                    getTotalCharsInChunks(currentChunks);
            ttsSimulationOffset = (int) (progress * ttsTotalDuration);
        }

        ttsStartTime = System.currentTimeMillis();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            tts.speak(chunk, TextToSpeech.QUEUE_FLUSH, params);
        }
    }
    private int getTotalCharsInChunks(List<String> chunks) {
        int total = 0;
        for (String chunk : chunks) {
            total += chunk.length();
        }
        return total;
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
        String cleanedText = cleanTextForTTS(text);

        Log.d(TAG, "Speaking text of length: " + text.length() + " characters");
        stop();

        if (text.length() > OPTIMAL_CHUNK_SIZE) {
            return speakLargeContent(text, OPTIMAL_CHUNK_SIZE);
        }

        ttsText = cleanedText;
        ttsTotalDuration = estimateTTSDuration(cleanedText);
        ttsStartTime = System.currentTimeMillis();
        ttsSimulationOffset = 0;

        String utteranceId = "SPEECH_" + System.currentTimeMillis();
        int result;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            result = tts.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            result = tts.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, params);
        }

        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS speak() call failed with error code: " + result);

            if (progressCallback != null) {
                String errorMessage = "Failed to start TTS playback (error code: " + result + ")";
                handler.post(() -> progressCallback.onError(errorMessage));
            }

            return false;
        }

        startTTSProgressUpdates(text);
        return true;
    }
    private void startTTSProgressUpdates(String text) {
        final int totalDuration = ttsTotalDuration > 0 ?
                ttsTotalDuration : estimateTTSDuration(text);

        Log.d(TAG, "Starting TTS progress tracking. Estimated duration: " +
                totalDuration + "ms");

        Runnable progressUpdater = new Runnable() {
            @Override
            public void run() {
                if (tts != null && (tts.isSpeaking() || isChunkedPlayback) && !isTtsSeeking) {
                    updateProgress();
                    handler.postDelayed(this, 250); // Update 4 times per second
                } else if (!tts.isSpeaking() && !isTtsSeeking && !isChunkedPlayback) {
                    if (progressCallback != null) {
                        handler.post(() -> progressCallback.onComplete());
                    }
                }
            }
        };

        handler.post(progressUpdater);
    }
    private void updateProgress() {
        try {
            int currentPosition = 0;
            int totalDuration = ttsTotalDuration;

            // Calculate current position
            if (isChunkedPlayback && currentChunks != null && currentChunks.size() > 0) {
                // For chunked playback, scale based on chunk position
                long elapsedTime = System.currentTimeMillis() - ttsStartTime;

                // Calculate raw progress as percentage through all chunks
                float chunkProgress = (float)currentChunkIndex / currentChunks.size();

                // Add progress within current chunk
                if (currentChunkIndex < currentChunks.size()) {
                    String currentChunk = currentChunks.get(currentChunkIndex);
                    int chunkDuration = estimateTTSDuration(currentChunk);

                    // Account for speech rate
                    long adjustedElapsedTime = (long)(elapsedTime * currentSpeechRate);
                    float intraChunkProgress = Math.min(1.0f, adjustedElapsedTime / (float)chunkDuration);

                    // Each chunk represents a portion of the total
                    float chunkPortion = 1.0f / currentChunks.size();
                    chunkProgress += intraChunkProgress * chunkPortion;
                }

                // Scale to our total duration
                currentPosition = (int)(chunkProgress * totalDuration);
            } else {
                // For non-chunked playback
                long elapsedTime = System.currentTimeMillis() - ttsStartTime;
                long adjustedElapsedTime = (long)(elapsedTime * currentSpeechRate);

                currentPosition = ttsSimulationOffset + (int)adjustedElapsedTime;
                currentPosition = Math.min(currentPosition, totalDuration);
            }

            // Determine current segment
            int segmentIndex = 0;
            if (segments != null && !segments.isEmpty()) {
                float progress = totalDuration > 0 ? (float)currentPosition / totalDuration : 0;
                segmentIndex = Math.min((int)(progress * segments.size()), segments.size() - 1);
            }

            if (progressCallback != null) {
                progressCallback.onProgress(currentPosition, totalDuration, segmentIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating progress: " + e.getMessage());
        }
    }

    public int estimateTTSDuration(String text) {
        if (text == null || text.isEmpty()) return 0;

        String[] words = text.split("\\s+");
        int wordCount = words.length;

        // Calculate sentence count for pauses
        int sentenceCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                sentenceCount++;
            }
        }
        sentenceCount = Math.max(sentenceCount, 1);

        // Calculate speaking time based on word count
        int speakingTimeMs = Math.round(wordCount / DEFAULT_WORDS_PER_SECOND * 1000);

        // Add time for pauses between sentences (average 300ms per sentence)
        int pauseTimeMs = sentenceCount * 300;

        // Apply speech rate adjustment
        int adjustedDuration = Math.round((speakingTimeMs + pauseTimeMs) / currentSpeechRate);

        return adjustedDuration;
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
        String cleanedFullText = cleanTextForTTS(fullText);

        // Use the explicitly set duration from the PodcastContent
        ttsTotalDuration = content.getTotalDuration() * 1000; // Convert to ms
        Log.d(TAG, "Using forced podcast duration of " + ttsTotalDuration +
                "ms for TTS playback timing");

        // Preprocess to add speaker markers if needed
        if (!fullText.contains("§HOST§") && !fullText.contains("HOST:")) {
            fullText = addSpeakerMarkers(fullText);
        }

        return speakLargeContent(cleanedFullText, OPTIMAL_CHUNK_SIZE);
    }
    private String addSpeakerMarkers(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder markedText = new StringBuilder();
        String[] paragraphs = text.split("\n\n");

        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) {
                continue;
            }

            // Add marker if not already present
            if (!paragraph.trim().startsWith("§HOST§") &&
                    !paragraph.trim().startsWith("HOST:")) {
                markedText.append("§HOST§ ");
            }

            markedText.append(paragraph).append("\n\n");
        }

        return markedText.toString();
    }

    private boolean speakLargeContent(String text, int maxChunkSize) {
        stop();

        if (text == null || text.isEmpty()) {
            Log.e(TAG, "Cannot speak empty text");
            return false;
        }
        String cleanedText = cleanTextForTTS(text);

        try {
            List<String> chunks = splitTextIntoChunks(cleanedText, maxChunkSize);

            if (chunks.isEmpty()) {
                Log.e(TAG, "Failed to split text into chunks");
                return false;
            }

            Log.d(TAG, "Split podcast text into " + chunks.size() + " chunks for TTS");

            ttsText = cleanedText;
            if (ttsTotalDuration == 0) {
                ttsTotalDuration = estimateTTSDuration(cleanedText);
            }

            isChunkedPlayback = true;
            currentChunks = chunks;
            currentChunkIndex = 0;
            ttsStartTime = System.currentTimeMillis();
            ttsSimulationOffset = 0;

            playNextChunk();
            startTTSProgressUpdates(cleanedText);

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
        int estimatedIndex = Math.min((int)(progress * segments.size()), segments.size() - 1);

        // Find more precise segment based on accumulated durations
        int accumulatedDuration = 0;
        for (int i = 0; i < segments.size(); i++) {
            int segmentDuration = segments.get(i).getEstimatedDuration() * 1000; // Convert to ms

            if (currentPosition < accumulatedDuration + segmentDuration) {
                return i;
            }

            accumulatedDuration += segmentDuration;
        }

        return estimatedIndex;
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

        // First clean the text
        String cleanedText = cleanTextForTTS(text);

        // Split on paragraphs first
        String[] paragraphs = cleanedText.split("\n\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (paragraph == null || paragraph.trim().isEmpty()) {
                continue;
            }

            // If adding this paragraph exceeds chunk size, finalize current chunk
            if (currentChunk.length() + paragraph.length() > maxChunkSize) {
                // Add current chunk if not empty
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }

                // If single paragraph is too large, split it further - but respect sentence boundaries
                if (paragraph.length() > maxChunkSize) {
                    splitByPreservingSentences(paragraph, maxChunkSize, chunks);
                } else {
                    currentChunk.append(paragraph);
                }
            } else {
                // Paragraph fits in current chunk
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(paragraph);
            }
        }

        // Add final chunk if not empty
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }
    // Helper method to split text while preserving complete sentences
    private void splitByPreservingSentences(String text, int maxChunkSize, List<String> chunks) {
        // Split on sentence boundaries
        List<String> sentences = new ArrayList<>();
        StringBuilder sentenceBuilder = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sentenceBuilder.append(c);

            // Check for sentence end
            if ((c == '.' || c == '!' || c == '?') &&
                    (i == text.length() - 1 || Character.isWhitespace(text.charAt(i + 1)))) {
                sentences.add(sentenceBuilder.toString());
                sentenceBuilder = new StringBuilder();
            }
        }

        // Add any remaining text as a sentence
        if (sentenceBuilder.length() > 0) {
            sentences.add(sentenceBuilder.toString());
        }

        // Group sentences into chunks
        StringBuilder chunkBuilder = new StringBuilder();

        for (String sentence : sentences) {
            // If this sentence would make the chunk too big, finalize current chunk
            if (chunkBuilder.length() + sentence.length() > maxChunkSize && chunkBuilder.length() > 0) {
                chunks.add(chunkBuilder.toString());
                chunkBuilder = new StringBuilder();
            }

            // If a single sentence is somehow bigger than max size, we have to split it
            if (sentence.length() > maxChunkSize) {
                if (chunkBuilder.length() > 0) {
                    chunks.add(chunkBuilder.toString());
                    chunkBuilder = new StringBuilder();
                }

                // Split on word boundaries if we must
                String[] words = sentence.split("\\s+");
                StringBuilder wordChunk = new StringBuilder();

                for (String word : words) {
                    if (wordChunk.length() + word.length() + 1 > maxChunkSize) {
                        chunks.add(wordChunk.toString());
                        wordChunk = new StringBuilder();
                    }

                    if (wordChunk.length() > 0) {
                        wordChunk.append(" ");
                    }
                    wordChunk.append(word);
                }

                if (wordChunk.length() > 0) {
                    chunks.add(wordChunk.toString());
                }
            } else {
                // Add sentence to current chunk
                if (chunkBuilder.length() > 0) {
                    chunkBuilder.append(" ");
                }
                chunkBuilder.append(sentence);
            }
        }

        // Add final chunk if not empty
        if (chunkBuilder.length() > 0) {
            chunks.add(chunkBuilder.toString());
        }
    }
    private void splitLargeParagraph(String paragraph, int maxChunkSize, List<String> chunks) {
        // Try to split on sentences
        String[] sentences = paragraph.split("(?<=[.!?])\\s+");

        StringBuilder currentChunk = new StringBuilder();
        boolean isFirstSentence = true;

        // Try to preserve speaker markers
        String speakerMarker = "";
        if (paragraph.startsWith("§HOST§")) {
            speakerMarker = "§HOST§ ";
        }

        for (String sentence : sentences) {
            if (sentence == null || sentence.trim().isEmpty()) {
                continue;
            }

            // If adding this sentence exceeds chunk size, finalize current chunk
            if (currentChunk.length() + sentence.length() > maxChunkSize) {
                // Add current chunk if not empty
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();

                    // Add speaker marker to next chunk
                    if (!speakerMarker.isEmpty()) {
                        currentChunk.append(speakerMarker);
                    }

                    isFirstSentence = true;
                }

                // If single sentence is too large, split it on words
                if (sentence.length() > maxChunkSize) {
                    // Just take chunks of appropriate size
                    int start = 0;
                    while (start < sentence.length()) {
                        int end = Math.min(start + maxChunkSize, sentence.length());

                        StringBuilder chunkWithMarker = new StringBuilder();
                        if (!speakerMarker.isEmpty()) {
                            chunkWithMarker.append(speakerMarker);
                        }
                        chunkWithMarker.append(sentence.substring(start, end));

                        chunks.add(chunkWithMarker.toString());
                        start = end;
                    }
                } else {
                    // Add speaker marker if this is first sentence in chunk
                    if (isFirstSentence && !speakerMarker.isEmpty() &&
                            !currentChunk.toString().startsWith(speakerMarker)) {
                        currentChunk.insert(0, speakerMarker);
                    }
                    currentChunk.append(sentence).append(" ");
                    isFirstSentence = false;
                }
            } else {
                // Sentence fits in current chunk
                // Add speaker marker if this is first sentence in chunk
                if (isFirstSentence && currentChunk.length() == 0 &&
                        !speakerMarker.isEmpty()) {
                    currentChunk.append(speakerMarker);
                }
                currentChunk.append(sentence).append(" ");
                isFirstSentence = false;
            }
        }

        // Add final chunk if not empty
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
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
        } else if (tts != null && (tts.isSpeaking() || isChunkedPlayback) && ttsTotalDuration > 0) {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - ttsStartTime;

            // Adjust for speech rate
            elapsedTime = Math.round(elapsedTime * currentSpeechRate);

            // Handle chunked playback
            if (isChunkedPlayback && currentChunks != null && currentChunks.size() > 0) {
                int calculatedPosition = calculateChunkedPlaybackPosition(elapsedTime);
                return Math.min(calculatedPosition, ttsTotalDuration);
            } else {
                // Standard playback
                int calculatedPosition = ttsSimulationOffset + (int)elapsedTime;
                return Math.min(calculatedPosition, ttsTotalDuration);
            }
        }

        return 0;
    }

    private int calculateChunkedPlaybackPosition(long elapsedTime) {
        // Calculate total characters
        int totalChars = 0;
        for (String chunk : currentChunks) {
            totalChars += chunk.length();
        }

        // Calculate chars processed so far
        int charsProcessed = 0;
        for (int i = 0; i < currentChunkIndex; i++) {
            charsProcessed += currentChunks.get(i).length();
        }

        // Add portion of current chunk
        if (currentChunkIndex < currentChunks.size()) {
            String currentChunk = currentChunks.get(currentChunkIndex);
            int currentChunkDuration = estimateTTSDuration(currentChunk);
            float chunkProgress = Math.min(1.0f, elapsedTime / (float)currentChunkDuration);
            charsProcessed += Math.round(currentChunk.length() * chunkProgress);
        }

        // Calculate overall progress
        float progress = totalChars > 0 ? (float)charsProcessed / totalChars : 0;
        return Math.round(progress * ttsTotalDuration);
    }

    public int getTotalDuration() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception e) {
                Log.e(TAG, "Error getting duration from MediaPlayer: " + e.getMessage());
            }
        } else if (ttsTotalDuration > 0) {
            // Always use our explicit setting if available
            return ttsTotalDuration;
        } else if (segments != null && !segments.isEmpty()) {
            // Only compute from segments if we don't have an explicit total
            int totalDuration = 0;
            for (PodcastSegment segment : segments) {
                totalDuration += segment.getEstimatedDuration() * 1000; // Convert to ms
            }
            ttsTotalDuration = totalDuration;
            return totalDuration;
        } else if (ttsText != null && !ttsText.isEmpty()) {
            ttsTotalDuration = estimateTTSDuration(ttsText);
            return ttsTotalDuration;
        }

        return 60000; // Default fallback
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