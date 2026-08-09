package ai.monolith.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

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
    private static final Object LOCK = new Object();
    private static final String RUNTIME_ASSET = "monolith_tts/espeak-ng-data";
    private static String loadedModelId = "";
    private static OfflineTts cachedTts;

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
            for (String child : children) {
                copyAssetTree(assets, assetPath + "/" + child, new File(target, child));
            }
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

            OfflineTtsVitsModelConfig vits = OfflineTtsVitsModelConfig.builder()
                .setModel(model.getAbsolutePath())
                .setTokens(tokens.getAbsolutePath())
                .setDataDir(dataDir.getAbsolutePath())
                .setNoiseScale(0.667f)
                .setNoiseScaleW(0.8f)
                .setLengthScale(1.0f)
                .build();

            OfflineTtsModelConfig modelConfig = OfflineTtsModelConfig.builder()
                .setVits(vits)
                .setNumThreads(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)))
                .setDebug(false)
                .setProvider("cpu")
                .build();

            OfflineTtsConfig config = OfflineTtsConfig.builder()
                .setModel(modelConfig)
                .setMaxNumSentences(1)
                .setSilenceScale(0.18f)
                .build();

            cachedTts = new OfflineTts(config);
            loadedModelId = id;
            return cachedTts;
        }
    }

    public static File synthesize(Context context, String text, File destination) throws Exception {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("Speech text is empty.");
        OfflineTts tts = getOrCreate(context.getApplicationContext());
        GeneratedAudio audio;
        synchronized (LOCK) {
            audio = tts.generate(text.trim(), 0, 1.0f);
        }
        if (audio == null || audio.getSamples() == null || audio.getSamples().length == 0) {
            throw new IllegalStateException("Local Piper runtime returned no audio.");
        }
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        if (!audio.save(destination.getAbsolutePath()) || !destination.isFile() || destination.length() < 128) {
            throw new IllegalStateException("Local Piper audio could not be written.");
        }
        return destination;
    }

    public static void invalidate() {
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
