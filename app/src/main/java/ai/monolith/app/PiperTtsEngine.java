package ai.monolith.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local Piper/VITS speech runtime backed by sherpa-onnx.
 *
 * Runnable models are stored in the Voice Module protected workspace as:
 * models/<id>/model.onnx
 * models/<id>/tokens.txt
 *
 * The ONNX must contain the Piper metadata required by sherpa-onnx. The original
 * model.onnx.json may remain beside it for provenance but is not required at runtime.
 */
public final class PiperTtsEngine {
    public interface Listener {
        void onStarted();
        void onDone();
        void onError(String message);
    }

    private static final Object LOCK = new Object();
    private static final Object PLAYBACK_LOCK = new Object();
    private static final String RUNTIME_ASSET = "monolith_tts/espeak-ng-data";
    private static final AtomicInteger PLAYBACK_SEQUENCE = new AtomicInteger(0);
    private static String loadedModelId = "";
    private static OfflineTts cachedTts;
    private static AudioTrack activeTrack;

    private PiperTtsEngine() {}

    private static File voiceRoot(Context context) {
        File root = context.getExternalFilesDir("monolith_voice");
        return root != null ? root : new File(context.getFilesDir(), "monolith_voice");
    }

    private static String activeModel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("monolith.voice", Context.MODE_PRIVATE);
        return prefs.getString("active_model", "");
    }

    private static File modelDir(Context context, String id) {
        return new File(new File(voiceRoot(context), "models"), id);
    }

    public static boolean hasRunnableActiveModel(Context context) {
        String id = activeModel(context);
        if (id == null || id.trim().isEmpty()) return false;
        File dir = modelDir(context, id);
        return new File(dir, "model.onnx").isFile() && new File(dir, "tokens.txt").isFile();
    }

    public static String activeRuntimeState(Context context) {
        String id = activeModel(context);
        if (id == null || id.trim().isEmpty()) return "inactive";
        File dir = modelDir(context, id);
        if (!new File(dir, "model.onnx").isFile()) return "model-missing";
        if (!new File(dir, "tokens.txt").isFile()) return "conversion-required";
        return "ready";
    }

    private static void copyAssetTree(AssetManager assets, String assetPath, File target) throws Exception {
        String[] children = assets.list(assetPath);
        if (children != null && children.length > 0) {
            if (!target.exists() && !target.mkdirs()) throw new IllegalStateException("Could not create Piper runtime directory.");
            for (String child : children) copyAssetTree(assets, assetPath + "/" + child, new File(target, child));
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Could not create Piper runtime parent directory.");
        try (InputStream in = assets.open(assetPath); FileOutputStream out = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private static File ensureEspeakData(Context context) throws Exception {
        File runtime = new File(voiceRoot(context), "runtime/espeak-ng-data");
        File marker = new File(runtime, ".monolith-ready");
        if (marker.isFile()) return runtime;
        if (runtime.exists()) deleteTree(runtime);
        copyAssetTree(context.getAssets(), RUNTIME_ASSET, runtime);
        if (!runtime.isDirectory()) throw new IllegalStateException("Piper phoneme runtime is missing.");
        try (FileOutputStream out = new FileOutputStream(marker, false)) {
            out.write("sherpa-onnx espeak runtime\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return runtime;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        try { file.delete(); } catch (Exception ignored) {}
    }

    private static OfflineTts getOrCreate(Context context) throws Exception {
        String id = activeModel(context);
        if (id == null || id.trim().isEmpty()) throw new IllegalStateException("No local voice model is active.");
        synchronized (LOCK) {
            if (cachedTts != null && id.equals(loadedModelId)) return cachedTts;
            releaseLocked();

            File dir = modelDir(context, id);
            File model = new File(dir, "model.onnx");
            File tokens = new File(dir, "tokens.txt");
            if (!model.isFile()) throw new IllegalStateException("Active local voice model.onnx is missing.");
            if (!tokens.isFile()) throw new IllegalStateException("Active Piper model requires sherpa conversion and tokens.txt.");
            File dataDir = ensureEspeakData(context);

            // The Android AAR ships sherpa's Kotlin API. From Java these data classes
            // are configured through their generated no-arg constructors and setters,
            // not the separate java-api Builder classes.
            OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
            vits.setModel(model.getAbsolutePath());
            vits.setTokens(tokens.getAbsolutePath());
            vits.setDataDir(dataDir.getAbsolutePath());
            vits.setNoiseScale(0.667f);
            vits.setNoiseScaleW(0.8f);
            vits.setLengthScale(1.0f);

            OfflineTtsModelConfig modelConfig = new OfflineTtsModelConfig();
            modelConfig.setVits(vits);
            modelConfig.setNumThreads(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)));
            modelConfig.setDebug(false);
            modelConfig.setProvider("cpu");

            OfflineTtsConfig config = new OfflineTtsConfig();
            config.setModel(modelConfig);
            config.setMaxNumSentences(1);
            config.setSilenceScale(0.18f);

            // Null AssetManager selects sherpa's filesystem loader, required because
            // Voice Module models live under getExternalFilesDir rather than assets.
            cachedTts = new OfflineTts(null, config);
            loadedModelId = id;
            return cachedTts;
        }
    }

    private static GeneratedAudio synthesize(Context context, String text) throws Exception {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("Speech text is empty.");
        OfflineTts tts = getOrCreate(context.getApplicationContext());
        GeneratedAudio audio;
        synchronized (LOCK) { audio = tts.generate(text.trim(), 0, 1.0f); }
        if (audio == null || audio.getSamples() == null || audio.getSamples().length == 0) {
            throw new IllegalStateException("Local Piper runtime returned no audio.");
        }
        return audio;
    }

    public static void speakAsync(Context context, String text, Listener listener) {
        final int playbackId = PLAYBACK_SEQUENCE.incrementAndGet();
        new Thread(() -> {
            try {
                GeneratedAudio generated = synthesize(context, text);
                if (playbackId != PLAYBACK_SEQUENCE.get()) return;
                float[] samples = generated.getSamples();
                int sampleRate = generated.getSampleRate();
                int minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
                if (minBuffer <= 0) minBuffer = Math.max(4096, samples.length * 4);

                AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(Math.max(minBuffer, 16384))
                    .build();

                synchronized (PLAYBACK_LOCK) {
                    releaseTrackLocked();
                    if (playbackId != PLAYBACK_SEQUENCE.get()) {
                        track.release();
                        return;
                    }
                    activeTrack = track;
                }

                track.play();
                if (listener != null) listener.onStarted();
                int offset = 0;
                while (offset < samples.length && playbackId == PLAYBACK_SEQUENCE.get()) {
                    int written = track.write(samples, offset, samples.length - offset, AudioTrack.WRITE_BLOCKING);
                    if (written < 0) throw new IllegalStateException("Local audio output error " + written);
                    offset += written;
                }
                try { track.stop(); } catch (Exception ignored) {}
                synchronized (PLAYBACK_LOCK) {
                    if (activeTrack == track) activeTrack = null;
                }
                track.release();
                if (playbackId == PLAYBACK_SEQUENCE.get() && listener != null) listener.onDone();
            } catch (Throwable error) {
                synchronized (PLAYBACK_LOCK) { releaseTrackLocked(); }
                if (playbackId == PLAYBACK_SEQUENCE.get() && listener != null) {
                    listener.onError(error.getMessage() == null ? "Local Piper speech failed." : error.getMessage());
                }
            }
        }, "MonolithPiperTts").start();
    }

    public static void stop() {
        PLAYBACK_SEQUENCE.incrementAndGet();
        synchronized (PLAYBACK_LOCK) { releaseTrackLocked(); }
    }

    private static void releaseTrackLocked() {
        if (activeTrack != null) {
            try { activeTrack.pause(); } catch (Exception ignored) {}
            try { activeTrack.flush(); } catch (Exception ignored) {}
            try { activeTrack.stop(); } catch (Exception ignored) {}
            try { activeTrack.release(); } catch (Exception ignored) {}
            activeTrack = null;
        }
    }

    public static void invalidate() {
        stop();
        synchronized (LOCK) { releaseLocked(); }
    }

    private static void releaseLocked() {
        if (cachedTts != null) {
            try { cachedTts.release(); } catch (Exception ignored) {}
            cachedTts = null;
        }
        loadedModelId = "";
    }
}
