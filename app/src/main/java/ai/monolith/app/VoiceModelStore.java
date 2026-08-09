package ai.monolith.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Protected Monolith voice workspace. Datasets and imported model targets live under
 * Context.getExternalFilesDir("monolith_voice"), so an APK update does not replace them.
 */
public final class VoiceModelStore {
    private static final int SAMPLE_RATE = 22050;
    private final Context context;
    private final File root;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private AudioRecord recorder;
    private Thread recorderThread;
    private File activePcm;
    private File activeWav;
    private String activeTranscript = "";
    private String activeDataset = "default";
    private String lastImportedModelId = "";

    public VoiceModelStore(Context context) {
        this.context = context.getApplicationContext();
        File external = context.getExternalFilesDir("monolith_voice");
        root = external != null ? external : new File(context.getFilesDir(), "monolith_voice");
        ensure(new File(root, "datasets"));
        ensure(new File(root, "models"));
        ensure(new File(root, "imports"));
        ensure(new File(root, "exports"));
        ensure(new File(root, "runtime"));
    }

    private static void ensure(File dir) {
        if (dir != null && !dir.exists()) dir.mkdirs();
    }

    private static String safe(String value) {
        String out = value == null ? "item" : value.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        if (out.isEmpty()) out = "item";
        return out.length() > 80 ? out.substring(0, 80) : out;
    }

    private File datasetDir(String id) {
        File dir = new File(new File(root, "datasets"), safe(id));
        ensure(dir);
        ensure(new File(dir, "wav"));
        return dir;
    }

    public synchronized String startRecording(String datasetId, String transcript) throws Exception {
        if (recording.get()) throw new IllegalStateException("A voice sample is already recording.");
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Microphone access has not been granted.");
        }
        activeDataset = safe(datasetId == null || datasetId.trim().isEmpty() ? "default" : datasetId);
        activeTranscript = transcript == null ? "" : transcript.replaceAll("[\\r\\n|]+", " ").trim();
        File dir = datasetDir(activeDataset);
        String id = String.format(Locale.US, "sample_%d", System.currentTimeMillis());
        activePcm = new File(dir, id + ".pcm");
        activeWav = new File(new File(dir, "wav"), id + ".wav");

        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) min = 8192;
        recorder = new AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            Math.max(min * 2, 16384)
        );
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release();
            recorder = null;
            throw new IllegalStateException("The local voice recorder could not initialize.");
        }

        recording.set(true);
        recorder.startRecording();
        final int bufferSize = Math.max(min, 8192);
        recorderThread = new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            try (FileOutputStream out = new FileOutputStream(activePcm, false)) {
                while (recording.get()) {
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read > 0) out.write(buffer, 0, read);
                }
                out.flush();
            } catch (Exception ignored) {}
        }, "MonolithVoiceRecorder");
        recorderThread.start();
        return id;
    }

    public synchronized String stopRecording() throws Exception {
        if (!recording.get()) return "";
        recording.set(false);
        try { if (recorder != null) recorder.stop(); } catch (Exception ignored) {}
        try { if (recorderThread != null) recorderThread.join(1800L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        try { if (recorder != null) recorder.release(); } catch (Exception ignored) {}
        recorder = null;
        recorderThread = null;
        if (activePcm == null || !activePcm.exists() || activePcm.length() < 512) throw new IllegalStateException("Voice sample was empty.");
        writeWav(activePcm, activeWav);
        activePcm.delete();
        String sampleId = activeWav.getName().replaceFirst("\\.wav$", "");
        appendMetadata(datasetDir(activeDataset), sampleId, activeTranscript);
        return activeWav.getAbsolutePath();
    }

    private static void writeWav(File pcm, File wav) throws Exception {
        long pcmBytes = pcm.length();
        try (FileInputStream in = new FileInputStream(pcm); FileOutputStream out = new FileOutputStream(wav, false)) {
            int channels = 1;
            int bits = 16;
            long byteRate = SAMPLE_RATE * channels * bits / 8L;
            writeAscii(out, "RIFF"); writeLeInt(out, 36 + pcmBytes); writeAscii(out, "WAVE");
            writeAscii(out, "fmt "); writeLeInt(out, 16); writeLeShort(out, 1); writeLeShort(out, channels);
            writeLeInt(out, SAMPLE_RATE); writeLeInt(out, byteRate); writeLeShort(out, channels * bits / 8); writeLeShort(out, bits);
            writeAscii(out, "data"); writeLeInt(out, pcmBytes);
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private static void writeAscii(FileOutputStream out, String value) throws Exception { out.write(value.getBytes(StandardCharsets.US_ASCII)); }
    private static void writeLeShort(FileOutputStream out, long value) throws Exception { out.write((int) value & 0xff); out.write((int) (value >> 8) & 0xff); }
    private static void writeLeInt(FileOutputStream out, long value) throws Exception { out.write((int) value & 0xff); out.write((int) (value >> 8) & 0xff); out.write((int) (value >> 16) & 0xff); out.write((int) (value >> 24) & 0xff); }

    private static void appendMetadata(File dataset, String sampleId, String transcript) throws Exception {
        File metadata = new File(dataset, "metadata.csv");
        String line = sampleId + "|" + (transcript == null ? "" : transcript) + "\n";
        try (FileOutputStream out = new FileOutputStream(metadata, true)) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        return "imported_file";
    }

    private String deriveModelId(String name) {
        String id = name
            .replaceAll("(?i)\\.onnx\\.json$", "")
            .replaceAll("(?i)\\.onnx$", "")
            .replaceAll("(?i)\\.json$", "");
        if (id.equalsIgnoreCase("tokens.txt") || id.equalsIgnoreCase("tokens")) id = "";
        return safe(id);
    }

    private File bestTokensTarget() {
        if (!lastImportedModelId.isEmpty()) {
            File dir = new File(new File(root, "models"), safe(lastImportedModelId));
            if (dir.isDirectory()) return dir;
        }
        String active = activeModel();
        if (active != null && !active.isEmpty()) {
            File dir = new File(new File(root, "models"), safe(active));
            if (dir.isDirectory()) return dir;
        }
        File[] modelDirs = new File(root, "models").listFiles(File::isDirectory);
        File singleMissingTokens = null;
        if (modelDirs != null) {
            for (File dir : modelDirs) {
                if (new File(dir, "model.onnx").isFile() && !new File(dir, "tokens.txt").isFile()) {
                    if (singleMissingTokens != null) return null;
                    singleMissingTokens = dir;
                }
            }
        }
        return singleMissingTokens;
    }

    public synchronized String importAsset(Uri uri) throws Exception {
        String name = safe(displayName(uri));
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".zip")) return importDatasetZip(uri, name);

        File target;
        if (lower.endsWith("tokens.txt")) {
            File modelDir = bestTokensTarget();
            if (modelDir == null) throw new IllegalStateException("Import model.onnx first so tokens.txt can be attached to the correct voice model.");
            target = new File(modelDir, "tokens.txt");
        } else if (lower.endsWith(".onnx") || lower.endsWith(".onnx.json")) {
            String modelId = deriveModelId(name);
            File modelDir = new File(new File(root, "models"), modelId);
            ensure(modelDir);
            lastImportedModelId = modelId;
            if (lower.endsWith(".onnx")) target = new File(modelDir, "model.onnx");
            else target = new File(modelDir, "model.onnx.json");
        } else {
            target = new File(new File(root, "imports"), name);
        }
        copy(uri, target);
        PiperTtsEngine.invalidate();
        return target.getAbsolutePath();
    }

    private String importDatasetZip(Uri uri, String fileName) throws Exception {
        String base = safe(fileName.replaceFirst("(?i)\\.zip$", ""));
        File destination = new File(new File(root, "datasets"), base);
        if (destination.exists()) destination = new File(new File(root, "datasets"), base + "_" + System.currentTimeMillis());
        ensure(destination);
        File wavDir = new File(destination, "wav");
        ensure(wavDir);
        boolean metadataFound = false;
        int wavCount = 0;
        long total = 0L;
        try (InputStream raw = context.getContentResolver().openInputStream(uri); ZipInputStream zip = new ZipInputStream(raw)) {
            if (raw == null) throw new IllegalStateException("Selected dataset archive could not be opened.");
            ZipEntry entry;
            byte[] buffer = new byte[16384];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) { zip.closeEntry(); continue; }
                String normalized = entry.getName().replace('\\', '/');
                String leaf = normalized.substring(normalized.lastIndexOf('/') + 1);
                if (leaf.isEmpty()) { zip.closeEntry(); continue; }
                File target = null;
                if (leaf.equalsIgnoreCase("metadata.csv")) {
                    target = new File(destination, "metadata.csv");
                    metadataFound = true;
                } else if (leaf.toLowerCase(Locale.US).endsWith(".wav")) {
                    target = new File(wavDir, safe(leaf));
                    wavCount++;
                }
                if (target != null) {
                    try (FileOutputStream out = new FileOutputStream(target, false)) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            total += read;
                            if (total > 4L * 1024L * 1024L * 1024L) throw new IllegalStateException("Dataset archive exceeds the 4 GB import limit.");
                            out.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
        if (!metadataFound || wavCount == 0) {
            deleteTree(destination);
            throw new IllegalStateException("Dataset ZIP must contain metadata.csv and at least one WAV file.");
        }
        return destination.getAbsolutePath();
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        try { file.delete(); } catch (Exception ignored) {}
    }

    public synchronized File exportDataset(String datasetId) throws Exception {
        File dataset = new File(new File(root, "datasets"), safe(datasetId));
        File metadata = new File(dataset, "metadata.csv");
        File wavDir = new File(dataset, "wav");
        File[] wavs = wavDir.listFiles((d, n) -> n.toLowerCase(Locale.US).endsWith(".wav"));
        if (!metadata.isFile() || wavs == null || wavs.length == 0) throw new IllegalStateException("Dataset requires metadata.csv and WAV samples before export.");
        File target = new File(new File(root, "exports"), safe(datasetId) + "_piper_dataset.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(target, false))) {
            addZipFile(zip, metadata, "metadata.csv");
            for (File wav : wavs) addZipFile(zip, wav, "wav/" + wav.getName());
        }
        return target;
    }

    private static void addZipFile(ZipOutputStream zip, File source, String entryName) throws Exception {
        zip.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream in = new FileInputStream(source)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) zip.write(buffer, 0, read);
        }
        zip.closeEntry();
    }

    private void copy(Uri uri, File target) throws Exception {
        ensure(target.getParentFile());
        try (InputStream in = context.getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(target, false)) {
            if (in == null) throw new IllegalStateException("Selected file could not be opened.");
            byte[] buffer = new byte[16384];
            int read;
            long total = 0L;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > 2L * 1024L * 1024L * 1024L) throw new IllegalStateException("Voice asset exceeds the 2 GB workspace limit.");
                out.write(buffer, 0, read);
            }
        }
    }

    public synchronized boolean setActiveModel(String id) {
        File dir = new File(new File(root, "models"), safe(id));
        File model = new File(dir, "model.onnx");
        File tokens = new File(dir, "tokens.txt");
        if (!model.isFile() || !tokens.isFile()) return false;
        context.getSharedPreferences("monolith.voice", Context.MODE_PRIVATE).edit().putString("active_model", safe(id)).apply();
        PiperTtsEngine.invalidate();
        return true;
    }

    public String activeModel() {
        return context.getSharedPreferences("monolith.voice", Context.MODE_PRIVATE).getString("active_model", "");
    }

    public String stateJson() {
        try {
            JSONObject out = new JSONObject();
            out.put("root", root.getAbsolutePath());
            out.put("recording", recording.get());
            out.put("sampleRate", SAMPLE_RATE);
            out.put("activeModel", activeModel());
            out.put("runtime", "sherpa-onnx-piper");
            out.put("runtimeState", PiperTtsEngine.activeRuntimeState(context));
            out.put("training", "external-offline-piper-training");
            out.put("conversion", "piper-onnx-plus-json-to-sherpa-onnx-plus-tokens");
            JSONArray datasets = new JSONArray();
            File[] dataDirs = new File(root, "datasets").listFiles(File::isDirectory);
            if (dataDirs != null) for (File dir : dataDirs) {
                JSONObject row = new JSONObject();
                row.put("id", dir.getName());
                File wav = new File(dir, "wav");
                File[] clips = wav.listFiles((d, n) -> n.toLowerCase(Locale.US).endsWith(".wav"));
                row.put("clips", clips == null ? 0 : clips.length);
                row.put("metadata", new File(dir, "metadata.csv").isFile());
                row.put("exportable", new File(dir, "metadata.csv").isFile() && clips != null && clips.length > 0);
                datasets.put(row);
            }
            out.put("datasets", datasets);
            JSONArray models = new JSONArray();
            File[] modelDirs = new File(root, "models").listFiles(File::isDirectory);
            if (modelDirs != null) for (File dir : modelDirs) {
                boolean hasOnnx = new File(dir, "model.onnx").isFile();
                boolean hasTokens = new File(dir, "tokens.txt").isFile();
                JSONObject row = new JSONObject();
                row.put("id", dir.getName());
                row.put("onnx", hasOnnx);
                row.put("config", new File(dir, "model.onnx.json").isFile());
                row.put("tokens", hasTokens);
                row.put("runnable", hasOnnx && hasTokens);
                row.put("active", dir.getName().equals(activeModel()));
                models.put(row);
            }
            out.put("models", models);
            return out.toString();
        } catch (Exception error) {
            return "{\"datasets\":[],\"models\":[],\"runtimeState\":\"error\"}";
        }
    }
}
