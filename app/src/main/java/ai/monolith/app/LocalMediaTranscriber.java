package ai.monolith.app;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Android 13+ local media transcription path. Compressed audio/video is decoded to PCM
 * with MediaCodec and streamed into Android's explicitly on-device SpeechRecognizer.
 */
public final class LocalMediaTranscriber {
    private LocalMediaTranscriber() {}

    private static final class AudioTrack {
        final int index;
        final String mime;
        final int sampleRate;
        final int channels;
        AudioTrack(int index, String mime, int sampleRate, int channels) {
            this.index = index; this.mime = mime; this.sampleRate = sampleRate; this.channels = channels;
        }
    }

    private static AudioTrack inspect(Activity activity, Uri uri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(activity, uri, null);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("audio/")) continue;
                int rate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 16000;
                int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
                return new AudioTrack(i, mime, rate, channels);
            }
            throw new IllegalStateException("No audio track was found in that media file.");
        } finally {
            extractor.release();
        }
    }

    private static void decodeToPipe(Activity activity, Uri uri, AudioTrack selected, ParcelFileDescriptor writeSide, AtomicReference<String> error) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try (FileOutputStream output = new FileOutputStream(writeSide.getFileDescriptor())) {
            extractor.setDataSource(activity, uri, null);
            extractor.selectTrack(selected.index);
            MediaFormat format = extractor.getTrackFormat(selected.index);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            codec = MediaCodec.createDecoderByType(selected.mime);
            codec.configure(format, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = codec.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(inputIndex);
                        if (buffer != null) {
                            buffer.clear();
                            int size = extractor.readSampleData(buffer, 0);
                            if (size < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                long time = extractor.getSampleTime();
                                codec.queueInputBuffer(inputIndex, 0, size, time, 0);
                                extractor.advance();
                            }
                        }
                    }
                }
                int outputIndex = codec.dequeueOutputBuffer(info, 10000);
                if (outputIndex >= 0) {
                    ByteBuffer decoded = codec.getOutputBuffer(outputIndex);
                    if (decoded != null && info.size > 0) {
                        decoded.position(info.offset);
                        decoded.limit(info.offset + info.size);
                        byte[] bytes = new byte[info.size];
                        decoded.get(bytes);
                        output.write(bytes);
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(outputIndex, false);
                }
            }
            output.flush();
        } catch (Exception e) {
            error.compareAndSet(null, e.getMessage() == null ? "Media decode failed." : e.getMessage());
        } finally {
            try { if (codec != null) { codec.stop(); codec.release(); } } catch (Exception ignored) {}
            extractor.release();
            try { writeSide.close(); } catch (Exception ignored) {}
        }
    }

    public static String transcribeBlocking(Activity activity, Uri uri) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            throw new UnsupportedOperationException("Local archived-media transcription requires Android 13 or newer.");
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)) {
            throw new UnsupportedOperationException("No on-device speech recognition service is installed on this device.");
        }
        AudioTrack track = inspect(activity, uri);
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor readSide = pipe[0];
        ParcelFileDescriptor writeSide = pipe[1];
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>("");
        AtomicReference<String> error = new AtomicReference<>(null);
        AtomicReference<SpeechRecognizer> recognizerRef = new AtomicReference<>();
        StringBuilder segments = new StringBuilder();
        Handler main = new Handler(Looper.getMainLooper());

        main.post(() -> {
            try {
                SpeechRecognizer recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
                recognizerRef.set(recognizer);
                recognizer.setRecognitionListener(new RecognitionListener() {
                    private void append(Bundle bundle) {
                        if (bundle == null) return;
                        ArrayList<String> rows = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (rows == null || rows.isEmpty()) return;
                        String text = rows.get(0) == null ? "" : rows.get(0).trim();
                        if (text.isEmpty()) return;
                        if (segments.length() > 0) segments.append(' ');
                        segments.append(text);
                    }
                    @Override public void onReadyForSpeech(Bundle params) {}
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() {}
                    @Override public void onError(int code) { error.compareAndSet(null, "On-device transcription error " + code); done.countDown(); }
                    @Override public void onResults(Bundle results) { if (segments.length() == 0) append(results); result.set(segments.toString().trim()); done.countDown(); }
                    @Override public void onPartialResults(Bundle partialResults) {}
                    @Override public void onEvent(int eventType, Bundle params) {}
                    @Override public void onSegmentResults(Bundle segmentResults) { append(segmentResults); }
                    @Override public void onEndOfSegmentedSession() { result.set(segments.toString().trim()); done.countDown(); }
                });
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readSide);
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, track.channels);
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, track.sampleRate);
                intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE);
                recognizer.startListening(intent);
                new Thread(() -> decodeToPipe(activity, uri, track, writeSide, error), "MonolithMediaDecoder").start();
            } catch (Exception e) {
                error.compareAndSet(null, e.getMessage() == null ? "On-device recognizer failed to start." : e.getMessage());
                done.countDown();
                try { writeSide.close(); } catch (Exception ignored) {}
            }
        });

        boolean completed = done.await(12, TimeUnit.MINUTES);
        main.post(() -> {
            SpeechRecognizer recognizer = recognizerRef.get();
            if (recognizer != null) recognizer.destroy();
            try { readSide.close(); } catch (Exception ignored) {}
        });
        if (!completed) throw new IllegalStateException("Local media transcription timed out.");
        if (error.get() != null && result.get().trim().isEmpty()) throw new IllegalStateException(error.get());
        String text = result.get().trim();
        if (text.length() < 2) throw new IllegalStateException("No speech could be transcribed from that media file.");
        return text;
    }
}
