package com.example.janeai;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.Location;
import android.speech.RecognizerIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.Manifest;
import android.database.Cursor;
import android.content.ClipData;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.GeolocationPermissions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Calendar;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

public class MainActivity extends Activity {
    private static final String BACKEND_TTS_URL = "https://jane-elevenlabs-backend.onrender.com/api/tts";
    private static final int PICK_FILE_REQUEST = 7042;
    private static final int SPEECH_REQUEST = 7043;

    private WebView webView;
    private static final int JANE_FILE_PICK_REQUEST = 7713;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer mediaPlayer;
    private final AtomicInteger voiceRequestCounter = new AtomicInteger(0);
    private int activeVoiceRequest = 0;

    // V79: durable native Knowledge Base archive. Originals and extracted text live
    // under app-private storage, outside WebView/IndexedDB, and survive APK updates.
    private final Object knowledgeArchiveLock = new Object();
    private final Map<String, LegacyArchiveBuffer> legacyArchiveBuffers = new ConcurrentHashMap<>();

    private static class LegacyArchiveBuffer {
        final String archiveId;
        final String indexedDocId;
        final String name;
        final String mimeType;
        final String importedAt;
        final String metaJson;
        final StringBuilder text = new StringBuilder();

        LegacyArchiveBuffer(String archiveId, String indexedDocId, String name, String mimeType, String importedAt, String metaJson) {
            this.archiveId = archiveId;
            this.indexedDocId = indexedDocId;
            this.name = name;
            this.mimeType = mimeType;
            this.importedAt = importedAt;
            this.metaJson = metaJson;
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PDFBoxResourceLoader.init(getApplicationContext());
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        webView = new WebView(this);
        setContentView(webView);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.addJavascriptInterface(new JaneBridge(), "AndroidJane");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }, 7044);
                }
                callback.invoke(origin, true, false);
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
        handleLaunchIntent(getIntent());
        mainHandler.postDelayed(this::resumePendingKnowledgeImports, 1800);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            mainHandler.postDelayed(() -> webView.evaluateJavascript(
                "window.JaneDeviceVitalsChanged && window.JaneDeviceVitalsChanged();",
                null
            ), 300);
        }
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent == null || webView == null) return;
        String mode = intent.getStringExtra("jane_mode");
        if (mode == null) return;

        mainHandler.postDelayed(() -> {
            if ("voice".equals(mode)) {
                webView.evaluateJavascript("window.JaneOpenVoice && window.JaneOpenVoice();", null);
            } else if ("image".equals(mode)) {
                webView.evaluateJavascript("window.JaneOpenImage && window.JaneOpenImage();", null);
            } else {
                webView.evaluateJavascript("window.JaneFocusPrompt && window.JaneFocusPrompt();", null);
            }
        }, 700);
    }

    private String jsString(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private void notifyJs(String functionName, String message) {
        mainHandler.post(() -> {
            webView.evaluateJavascript(
                "window." + functionName + " && window." + functionName + "('" + jsString(message) + "');",
                null
            );
        });
    }

    private void releaseMediaPlayer(boolean notifyDone) {
        mainHandler.post(() -> {
            try {
                if (mediaPlayer != null) {
                    try { mediaPlayer.stop(); } catch (Exception ignored) {}
                    try { mediaPlayer.release(); } catch (Exception ignored) {}
                    mediaPlayer = null;
                }
            } catch (Exception ignored) {}
            if (notifyDone) notifyJs("JaneNativeAudioDone", "");
        });
    }


    private void playAssetVoice(String assetPath) {
        final int requestId = voiceRequestCounter.incrementAndGet();
        activeVoiceRequest = requestId;

        new Thread(() -> {
            File audioFile = null;
            try {
                if (assetPath == null || assetPath.trim().isEmpty()) {
                    throw new RuntimeException("No local voice asset path.");
                }

                String safeName = assetPath.replace("/", "_").replace("\\", "_");
                audioFile = new File(getCacheDir(), "jane_local_" + requestId + "_" + safeName);

                try (InputStream input = getAssets().open(assetPath);
                     FileOutputStream output = new FileOutputStream(audioFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }

                if (!audioFile.exists() || audioFile.length() < 1024) {
                    throw new RuntimeException("Local voice asset copied too small.");
                }

                File finalAudioFile = audioFile;
                mainHandler.post(() -> {
                    try {
                        if (requestId != activeVoiceRequest) {
                            try { finalAudioFile.delete(); } catch (Exception ignored) {}
                            return;
                        }

                        try {
                            if (mediaPlayer != null) {
                                try { mediaPlayer.stop(); } catch (Exception ignored) {}
                                try { mediaPlayer.release(); } catch (Exception ignored) {}
                                mediaPlayer = null;
                            }
                        } catch (Exception ignored) {}

                        mediaPlayer = new MediaPlayer();
                        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build());
                        mediaPlayer.setDataSource(finalAudioFile.getAbsolutePath());
                        mediaPlayer.setOnPreparedListener(player -> {
                            if (requestId != activeVoiceRequest) {
                                try { player.release(); } catch (Exception ignored) {}
                                try { finalAudioFile.delete(); } catch (Exception ignored) {}
                                return;
                            }
                            notifyJs("JaneNativeAudioStarted", "");
                            player.start();
                        });
                        mediaPlayer.setOnCompletionListener(player -> {
                            try { player.release(); } catch (Exception ignored) {}
                            mediaPlayer = null;
                            try { finalAudioFile.delete(); } catch (Exception ignored) {}
                            if (requestId == activeVoiceRequest) notifyJs("JaneNativeAudioDone", "");
                        });
                        mediaPlayer.setOnErrorListener((player, what, extra) -> {
                            try { player.release(); } catch (Exception ignored) {}
                            mediaPlayer = null;
                            try { finalAudioFile.delete(); } catch (Exception ignored) {}
                            if (requestId == activeVoiceRequest) notifyJs("JaneNativeAudioError", "Local asset playback error " + what + "/" + extra);
                            return true;
                        });
                        mediaPlayer.prepareAsync();
                    } catch (Exception error) {
                        try { finalAudioFile.delete(); } catch (Exception ignored) {}
                        if (requestId == activeVoiceRequest) {
                            notifyJs("JaneNativeAudioError", error.getMessage() == null ? "Local voice playback failed." : error.getMessage());
                        }
                    }
                });
            } catch (Exception error) {
                try { if (audioFile != null) audioFile.delete(); } catch (Exception ignored) {}
                if (requestId == activeVoiceRequest) {
                    notifyJs("JaneNativeAudioError", error.getMessage() == null ? "Local voice asset missing." : error.getMessage());
                }
            }
        }).start();
    }

    private void stopAudio() {
        activeVoiceRequest = voiceRequestCounter.incrementAndGet();
        releaseMediaPlayer(true);
    }

    private String getFileName(Uri uri) {
        String result = "uploaded-file";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String name = cursor.getString(nameIndex);
                    if (name != null && !name.trim().isEmpty()) result = name;
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private String getMimeType(Uri uri) {
        String type = getContentResolver().getType(uri);
        return type == null || type.trim().isEmpty() ? "application/octet-stream" : type;
    }

    private long getFileSize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) return cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private File getKnowledgeArchiveDir() throws IOException {
        File dir = new File(getFilesDir(), "knowledge_archive");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create Jane's native Archives folder.");
        return dir;
    }

    private File getKnowledgeCatalogFile() throws IOException {
        return new File(getKnowledgeArchiveDir(), "catalog.json");
    }

    private JSONArray readKnowledgeCatalog() {
        synchronized (knowledgeArchiveLock) {
            try {
                File file = getKnowledgeCatalogFile();
                if (!file.exists() || file.length() == 0) return new JSONArray();
                byte[] bytes;
                try (FileInputStream input = new FileInputStream(file)) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                    bytes = output.toByteArray();
                }
                return new JSONArray(new String(bytes, StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                return new JSONArray();
            }
        }
    }

    private void writeKnowledgeCatalog(JSONArray catalog) throws IOException {
        synchronized (knowledgeArchiveLock) {
            File target = getKnowledgeCatalogFile();
            File temp = new File(target.getParentFile(), "catalog.tmp");
            try (FileOutputStream output = new FileOutputStream(temp, false)) {
                output.write(catalog.toString().getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
            if (target.exists() && !target.delete()) throw new IOException("Could not replace the Archives catalog.");
            if (!temp.renameTo(target)) throw new IOException("Could not save the Archives catalog.");
        }
    }

    private JSONObject findKnowledgeArchiveRecord(String archiveId) {
        JSONArray catalog = readKnowledgeCatalog();
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject row = catalog.optJSONObject(i);
            if (row != null && archiveId.equals(row.optString("id"))) return row;
        }
        return null;
    }

    private void upsertKnowledgeArchiveRecord(JSONObject record) throws IOException {
        synchronized (knowledgeArchiveLock) {
            JSONArray catalog = readKnowledgeCatalog();
            JSONArray updated = new JSONArray();
            boolean replaced = false;
            String id = record.optString("id");
            for (int i = 0; i < catalog.length(); i++) {
                JSONObject row = catalog.optJSONObject(i);
                if (row == null) continue;
                if (id.equals(row.optString("id"))) {
                    updated.put(record);
                    replaced = true;
                } else {
                    updated.put(row);
                }
            }
            if (!replaced) updated.put(record);
            writeKnowledgeCatalog(updated);
        }
    }

    private String safeArchiveName(String name) {
        String value = name == null ? "archive-file" : name.trim();
        if (value.isEmpty()) value = "archive-file";
        value = value.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (value.length() > 110) value = value.substring(value.length() - 110);
        return value;
    }

    private File copyKnowledgeOriginal(Uri uri, String archiveId, String originalName) throws Exception {
        File target = new File(getKnowledgeArchiveDir(), archiveId + "__" + safeArchiveName(originalName));
        long total = 0L;
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target, false)) {
            if (input == null) throw new IOException("Could not open the selected source file.");
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > 120L * 1024L * 1024L) throw new IOException("Archive files are limited to 120 MB each.");
                output.write(buffer, 0, read);
            }
        } catch (Exception error) {
            try { target.delete(); } catch (Exception ignored) {}
            throw error;
        }
        return target;
    }

    private File writeKnowledgeArchiveText(String archiveId, String text) throws Exception {
        File target = new File(getKnowledgeArchiveDir(), archiveId + ".extracted.txt");
        try (FileOutputStream output = new FileOutputStream(target, false)) {
            output.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        }
        return target;
    }

    private JSONObject createKnowledgeArchiveRecord(String archiveId, String indexedDocId, String name, String mimeType, File originalFile, File textFile, long fileBytes, long importedAt, int extractedChars, boolean legacy) throws Exception {
        JSONObject row = new JSONObject();
        row.put("id", archiveId);
        row.put("indexedDocId", indexedDocId == null ? "" : indexedDocId);
        row.put("name", name == null ? "Archive entry" : name);
        row.put("mime", mimeType == null ? "application/octet-stream" : mimeType);
        row.put("storedFile", originalFile == null ? "" : originalFile.getName());
        row.put("textFile", textFile == null ? "" : textFile.getName());
        row.put("fileBytes", fileBytes);
        row.put("importedAt", importedAt);
        row.put("extractedChars", extractedChars);
        row.put("originalAvailable", originalFile != null && originalFile.exists());
        row.put("legacy", legacy);
        row.put("storage", "native-private-archive");
        return row;
    }

    private JSONObject buildKnowledgeMeta(JSONObject row, int fileIndex, int totalFiles) throws Exception {
        JSONObject meta = new JSONObject();
        meta.put("source", "native-persistent-archive");
        meta.put("nativeArchiveId", row.optString("id"));
        meta.put("nativeStored", true);
        meta.put("originalAvailable", row.optBoolean("originalAvailable"));
        meta.put("legacy", row.optBoolean("legacy"));
        meta.put("fileBytes", row.optLong("fileBytes"));
        meta.put("importedAt", row.optLong("importedAt"));
        meta.put("fileIndex", fileIndex);
        meta.put("totalFiles", totalFiles);
        return meta;
    }

    private void linkKnowledgeArchive(String archiveId, String indexedDocId) {
        if (archiveId == null || archiveId.trim().isEmpty()) return;
        try {
            JSONObject row = findKnowledgeArchiveRecord(archiveId);
            if (row == null) return;
            row.put("indexedDocId", indexedDocId == null ? "" : indexedDocId);
            upsertKnowledgeArchiveRecord(row);
        } catch (Exception ignored) {}
    }

    private void deleteKnowledgeArchive(String archiveId) {
        String indexedDocId = "";
        boolean deleted = false;
        try {
            synchronized (knowledgeArchiveLock) {
                JSONArray catalog = readKnowledgeCatalog();
                JSONArray updated = new JSONArray();
                File dir = getKnowledgeArchiveDir();
                for (int i = 0; i < catalog.length(); i++) {
                    JSONObject row = catalog.optJSONObject(i);
                    if (row == null) continue;
                    if (archiveId.equals(row.optString("id"))) {
                        indexedDocId = row.optString("indexedDocId", "");
                        String storedFile = row.optString("storedFile", "");
                        String textFile = row.optString("textFile", "");
                        if (!storedFile.isEmpty()) try { new File(dir, storedFile).delete(); } catch (Exception ignored) {}
                        if (!textFile.isEmpty()) try { new File(dir, textFile).delete(); } catch (Exception ignored) {}
                        deleted = true;
                    } else {
                        updated.put(row);
                    }
                }
                if (deleted) writeKnowledgeCatalog(updated);
            }
            notifyKnowledgeJs("JaneNativeArchiveDeleted", archiveId, indexedDocId);
        } catch (Exception error) {
            notifyKnowledgeJs("JaneNativeArchivePreviewError", archiveId, error.getMessage() == null ? "Could not delete that archive entry." : error.getMessage());
        }
    }

    private void sendTextChunks(String startFunction, String chunkFunction, String completeFunction, String archiveId, String name, String mimeType, String text) {
        final int chunkChars = 60000;
        String value = text == null ? "" : text;
        int total = Math.max(1, (value.length() + chunkChars - 1) / chunkChars);
        notifyKnowledgeJs(startFunction, archiveId, name, mimeType, String.valueOf(total));
        for (int i = 0; i < total; i++) {
            int start = i * chunkChars;
            int end = Math.min(value.length(), start + chunkChars);
            String base64 = Base64.encodeToString(value.substring(start, end).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            notifyKnowledgeJs(chunkFunction, archiveId, String.valueOf(i), String.valueOf(total), base64);
        }
        notifyKnowledgeJs(completeFunction, archiveId);
    }

    private String readArchiveText(JSONObject row) throws Exception {
        String textFile = row.optString("textFile", "");
        if (textFile.isEmpty()) return "";
        File file = new File(getKnowledgeArchiveDir(), textFile);
        if (!file.exists()) return "";
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void restoreKnowledgeArchives(String archiveIdsJson) {
        new Thread(() -> {
            Set<String> requested = new HashSet<>();
            try {
                JSONArray ids = new JSONArray(archiveIdsJson == null ? "[]" : archiveIdsJson);
                for (int i = 0; i < ids.length(); i++) {
                    String id = ids.optString(i, "");
                    if (!id.isEmpty()) requested.add(id);
                }
            } catch (Exception ignored) {}
            if (requested.isEmpty()) return;

            JSONArray catalog = readKnowledgeCatalog();
            for (int i = 0; i < catalog.length(); i++) {
                JSONObject row = catalog.optJSONObject(i);
                if (row == null) continue;
                String archiveId = row.optString("id");
                if (!requested.contains(archiveId)) continue;
                try {
                    String text = readArchiveText(row);
                    if (text.trim().length() < 30) continue;
                    JSONObject meta = buildKnowledgeMeta(row, i + 1, catalog.length());
                    final int chunkChars = 60000;
                    int total = Math.max(1, (text.length() + chunkChars - 1) / chunkChars);
                    notifyKnowledgeJs("JaneNativeArchiveRestoreStart", archiveId, row.optString("name"), row.optString("mime"), String.valueOf(total));
                    for (int c = 0; c < total; c++) {
                        int start = c * chunkChars;
                        int end = Math.min(text.length(), start + chunkChars);
                        String base64 = Base64.encodeToString(text.substring(start, end).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                        notifyKnowledgeJs("JaneNativeArchiveRestoreChunk", archiveId, String.valueOf(c), String.valueOf(total), base64);
                    }
                    notifyKnowledgeJs("JaneNativeArchiveRestoreComplete", archiveId, meta.toString());
                } catch (Exception error) {
                    notifyKnowledgeJs("JaneNativeArchivePreviewError", archiveId, "Could not rebuild the searchable index for " + row.optString("name") + ".");
                }
            }
        }).start();
    }

    private void archiveLegacyKnowledgeStart(String archiveId, String indexedDocId, String name, String mimeType, String importedAt, String metaJson) {
        legacyArchiveBuffers.put(archiveId, new LegacyArchiveBuffer(archiveId, indexedDocId, name, mimeType, importedAt, metaJson));
    }

    private void archiveLegacyKnowledgeChunk(String archiveId, String base64) {
        LegacyArchiveBuffer buffer = legacyArchiveBuffers.get(archiveId);
        if (buffer == null) return;
        try {
            byte[] decoded = Base64.decode(base64, Base64.DEFAULT);
            buffer.text.append(new String(decoded, StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private void archiveLegacyKnowledgeComplete(String archiveId) {
        LegacyArchiveBuffer buffer = legacyArchiveBuffers.remove(archiveId);
        if (buffer == null) return;
        try {
            String text = buffer.text.toString();
            File textFile = writeKnowledgeArchiveText(archiveId, text);
            long importedAt;
            try { importedAt = Long.parseLong(buffer.importedAt); } catch (Exception ignored) { importedAt = System.currentTimeMillis(); }
            JSONObject row = createKnowledgeArchiveRecord(archiveId, buffer.indexedDocId, buffer.name, buffer.mimeType, null, textFile, textFile.length(), importedAt, text.length(), true);
            upsertKnowledgeArchiveRecord(row);
            notifyKnowledgeJs("JaneNativeArchiveCatalogChanged", archiveId);
        } catch (Exception error) {
            notifyKnowledgeJs("JaneNativeArchivePreviewError", archiveId, error.getMessage() == null ? "Could not preserve legacy knowledge." : error.getMessage());
        }
    }

    private void sendArchivePreview(String archiveId, int requestedPage) {
        new Thread(() -> {
            try {
                JSONObject row = findKnowledgeArchiveRecord(archiveId);
                if (row == null) throw new IOException("Archive entry not found.");
                String name = row.optString("name", "Archive entry");
                String mime = row.optString("mime", "application/octet-stream");
                String storedName = row.optString("storedFile", "");
                File original = storedName.isEmpty() ? null : new File(getKnowledgeArchiveDir(), storedName);
                String lower = (name + " " + mime).toLowerCase();

                if (original != null && original.exists() && (mime.toLowerCase().contains("pdf") || lower.endsWith(".pdf"))) {
                    try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(original, ParcelFileDescriptor.MODE_READ_ONLY);
                         PdfRenderer renderer = new PdfRenderer(descriptor)) {
                        int totalPages = renderer.getPageCount();
                        int pageIndex = Math.max(0, Math.min(requestedPage, totalPages - 1));
                        try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
                            float scale = Math.min(2.2f, Math.min(1500f / Math.max(1, page.getWidth()), 2200f / Math.max(1, page.getHeight())));
                            scale = Math.max(1.0f, scale);
                            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(page.getWidth() * scale)), Math.max(1, Math.round(page.getHeight() * scale)), Bitmap.Config.ARGB_8888);
                            bitmap.eraseColor(Color.WHITE);
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                            ByteArrayOutputStream image = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, image);
                            bitmap.recycle();
                            String base64 = Base64.encodeToString(image.toByteArray(), Base64.NO_WRAP);
                            notifyKnowledgeJs("JaneNativeArchivePreviewReady", archiveId, "pdf", name, mime, String.valueOf(pageIndex), String.valueOf(totalPages), base64);
                        }
                    }
                    return;
                }

                if (original != null && original.exists() && (mime.toLowerCase().startsWith("image/") || lower.matches(".*\\.(png|jpe?g|webp|bmp|gif|heic|heif).*$"))) {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(original.getAbsolutePath(), bounds);
                    int sample = 1;
                    while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 2200) sample *= 2;
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = Math.max(1, sample);
                    Bitmap bitmap = BitmapFactory.decodeFile(original.getAbsolutePath(), options);
                    if (bitmap == null) throw new IOException("Could not preview that image.");
                    ByteArrayOutputStream image = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, image);
                    bitmap.recycle();
                    String base64 = Base64.encodeToString(image.toByteArray(), Base64.NO_WRAP);
                    notifyKnowledgeJs("JaneNativeArchivePreviewReady", archiveId, "image", name, "image/jpeg", "0", "1", base64);
                    return;
                }

                String text = readArchiveText(row);
                sendTextChunks("JaneNativeArchiveTextStart", "JaneNativeArchiveTextChunk", "JaneNativeArchiveTextComplete", archiveId, name, mime, text);
            } catch (Exception error) {
                notifyKnowledgeJs("JaneNativeArchivePreviewError", archiveId, error.getMessage() == null ? "Could not open that archive entry." : error.getMessage());
            }
        }).start();
    }


    // V80: imports are cataloged immediately, then indexed with visible and persistent progress.
    private boolean isPdfFile(String name, String mimeType) {
        String lowerName = name == null ? "" : name.toLowerCase();
        String lowerMime = mimeType == null ? "" : mimeType.toLowerCase();
        return lowerName.endsWith(".pdf") || lowerMime.contains("pdf");
    }

    private void updateKnowledgeArchiveIndexState(String archiveId, String status, String message, int current, int total) {
        try {
            JSONObject row = findKnowledgeArchiveRecord(archiveId);
            if (row == null) return;
            row.put("indexStatus", status == null ? "indexing" : status);
            row.put("indexMessage", message == null ? "" : message);
            row.put("pagesIndexed", Math.max(0, current));
            row.put("totalPages", Math.max(0, total));
            row.put("updatedAt", System.currentTimeMillis());
            upsertKnowledgeArchiveRecord(row);
            notifyKnowledgeJs("JaneNativeArchiveIndexProgress", archiveId, row.optString("indexStatus"), row.optString("indexMessage"), String.valueOf(current), String.valueOf(total));
            notifyKnowledgeJs("JaneNativeArchiveCatalogChanged", archiveId);
        } catch (Exception ignored) {}
    }

    private String extractEmbeddedPdfText(File originalFile, String archiveId, String name) {
        updateKnowledgeArchiveIndexState(archiveId, "indexing", "Reading embedded PDF text", 0, 0);
        try (PDDocument document = PDDocument.load(originalFile)) {
            int pages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            text = text == null ? "" : text.trim();
            if (text.length() >= 200) {
                updateKnowledgeArchiveIndexState(archiveId, "indexing", "Extracted text from " + pages + " PDF pages", pages, pages);
                return text;
            }
        } catch (Exception error) {
            notifyKnowledgeJs("JaneKnowledgeImportProgress", archiveId, name, "0", "0", "Embedded text unavailable; switching to OCR");
        }
        return "";
    }

    private String recognizePdfFile(File originalFile, String archiveId, String name, TextRecognizer recognizer) throws Exception {
        StringBuilder out = new StringBuilder();
        JSONObject row = findKnowledgeArchiveRecord(archiveId);
        int resumePage = row == null ? 0 : Math.max(0, row.optInt("pagesIndexed", 0));
        File partialText = new File(getKnowledgeArchiveDir(), archiveId + ".extracted.txt");
        boolean append = resumePage > 0 && partialText.exists() && partialText.length() > 0;

        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(originalFile, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(descriptor);
             FileOutputStream partialOut = new FileOutputStream(partialText, append)) {
            int total = renderer.getPageCount();
            if (resumePage >= total) resumePage = 0;
            if (resumePage == 0 && append) {
                partialOut.getChannel().truncate(0);
                append = false;
            }
            updateKnowledgeArchiveIndexState(archiveId, "indexing", "OCR indexing PDF", resumePage, total);
            for (int pageIndex = resumePage; pageIndex < total; pageIndex++) {
                notifyKnowledgeJs("JaneKnowledgeImportProgress", archiveId, name, String.valueOf(pageIndex + 1), String.valueOf(total), "OCR indexing PDF");
                String pageText = "";
                try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
                    float scale = Math.min(2.25f, Math.min(1650f / Math.max(1, page.getWidth()), 2250f / Math.max(1, page.getHeight())));
                    scale = Math.max(1.25f, scale);
                    int width = Math.max(1, Math.round(page.getWidth() * scale));
                    int height = Math.max(1, Math.round(page.getHeight() * scale));
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    try {
                        Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 120, TimeUnit.SECONDS);
                        pageText = text.getText() == null ? "" : text.getText().trim();
                    } finally {
                        bitmap.recycle();
                    }
                }
                String block = "\n\n--- Page " + (pageIndex + 1) + " ---\n" + pageText;
                partialOut.write(block.getBytes(StandardCharsets.UTF_8));
                partialOut.flush();
                out.append(block);
                updateKnowledgeArchiveIndexState(archiveId, "indexing", "OCR page " + (pageIndex + 1) + " of " + total, pageIndex + 1, total);
            }
        }
        try (FileInputStream input = new FileInputStream(partialText)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
        }
    }

    private void indexStoredKnowledgeArchive(String archiveId, int fileIndex, int totalFiles) {
        try {
            JSONObject row = findKnowledgeArchiveRecord(archiveId);
            if (row == null) throw new IOException("Archive catalog entry is missing.");
            String name = row.optString("name", "Archive entry");
            String mimeType = row.optString("mime", "application/octet-stream");
            File originalFile = new File(getKnowledgeArchiveDir(), row.optString("storedFile", ""));
            if (!originalFile.exists()) throw new IOException("The preserved original file is missing.");

            String text;
            if (isPdfFile(name, mimeType)) {
                text = extractEmbeddedPdfText(originalFile, archiveId, name);
                if (text.length() < 200) {
                    TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                    try { text = recognizePdfFile(originalFile, archiveId, name, recognizer); }
                    finally { recognizer.close(); }
                }
            } else {
                text = extractKnowledgeText(Uri.fromFile(originalFile), name, mimeType, archiveId);
            }

            if (text == null || text.trim().length() < 30) throw new IOException("No readable text was found. Try a clearer scan or another file.");
            File textFile = writeKnowledgeArchiveText(archiveId, text);
            row = findKnowledgeArchiveRecord(archiveId);
            if (row == null) throw new IOException("Archive catalog entry disappeared during indexing.");
            row.put("textFile", textFile.getName());
            row.put("extractedChars", text.length());
            row.put("indexStatus", "indexed");
            row.put("indexMessage", "Ready");
            if (row.optInt("totalPages", 0) > 0) row.put("pagesIndexed", row.optInt("totalPages", 0));
            row.put("updatedAt", System.currentTimeMillis());
            upsertKnowledgeArchiveRecord(row);
            JSONObject meta = buildKnowledgeMeta(row, fileIndex, totalFiles);
            sendKnowledgeTextToWeb(archiveId, name, mimeType, text, meta.toString());
            notifyKnowledgeJs("JaneNativeArchiveCatalogChanged", archiveId);
        } catch (Exception error) {
            try {
                JSONObject row = findKnowledgeArchiveRecord(archiveId);
                if (row != null) {
                    row.put("indexStatus", "failed");
                    row.put("indexMessage", error.getMessage() == null ? "Indexing failed." : error.getMessage());
                    row.put("updatedAt", System.currentTimeMillis());
                    upsertKnowledgeArchiveRecord(row);
                }
            } catch (Exception ignored) {}
            JSONObject row = findKnowledgeArchiveRecord(archiveId);
            String name = row == null ? "Knowledge file" : row.optString("name", "Knowledge file");
            notifyKnowledgeJs("JaneKnowledgeImportError", archiveId, name, error.getMessage() == null ? "Indexing failed." : error.getMessage());
            notifyKnowledgeJs("JaneNativeArchiveCatalogChanged", archiveId);
        }
    }

    private void resumePendingKnowledgeImports() {
        new Thread(() -> {
            JSONArray catalog = readKnowledgeCatalog();
            for (int i = 0; i < catalog.length(); i++) {
                JSONObject row = catalog.optJSONObject(i);
                if (row == null) continue;
                if (!"indexing".equals(row.optString("indexStatus"))) continue;
                indexStoredKnowledgeArchive(row.optString("id"), i + 1, catalog.length());
            }
        }).start();
    }

    private void retryKnowledgeArchive(String archiveId) {
        new Thread(() -> {
            try {
                JSONObject row = findKnowledgeArchiveRecord(archiveId);
                if (row == null) return;
                row.put("indexStatus", "indexing");
                row.put("indexMessage", "Retrying index");
                row.put("pagesIndexed", 0);
                upsertKnowledgeArchiveRecord(row);
                File textFile = new File(getKnowledgeArchiveDir(), archiveId + ".extracted.txt");
                if (textFile.exists()) textFile.delete();
                notifyKnowledgeJs("JaneNativeArchiveCatalogChanged", archiveId);
                indexStoredKnowledgeArchive(archiveId, 1, 1);
            } catch (Exception error) {
                notifyKnowledgeJs("JaneKnowledgeImportError", archiveId, "Knowledge file", error.getMessage() == null ? "Retry failed." : error.getMessage());
            }
        }).start();
    }


    // V76: dedicated multi-file offline knowledge picker for PDFs, documents, text, and photos.
    private void openJaneFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "application/pdf",
            "image/*",
            "text/*",
            "application/json",
            "application/xml",
            "application/rtf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        });
        try {
            startActivityForResult(intent, JANE_FILE_PICK_REQUEST);
        } catch (ActivityNotFoundException error) {
            notifyKnowledgeJs("JaneKnowledgeImportError", "knowledge-picker", "Knowledge files", "No file picker is available on this device.");
        }
    }

    private void notifyKnowledgeJs(String functionName, String... args) {
        mainHandler.post(() -> {
            StringBuilder js = new StringBuilder("window.").append(functionName).append(" && window.").append(functionName).append("(");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) js.append(',');
                js.append('\'').append(jsString(args[i] == null ? "" : args[i])).append('\'');
            }
            js.append(");");
            webView.evaluateJavascript(js.toString(), null);
        });
    }

    private byte[] readUriBytes(Uri uri, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Could not open selected file.");
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IOException("File exceeds the supported import size.");
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    private String extractDocxText(byte[] bytes) throws Exception {
        StringBuilder out = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName()) && !entry.getName().startsWith("word/header") && !entry.getName().startsWith("word/footer")) continue;
                ByteArrayOutputStream xmlOut = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) xmlOut.write(buffer, 0, read);
                String xml = xmlOut.toString(StandardCharsets.UTF_8.name());
                xml = xml.replaceAll("<w:tab[^>]*/>", "\t")
                         .replaceAll("</w:p>", "\n")
                         .replaceAll("<[^>]+>", "")
                         .replace("&amp;", "&")
                         .replace("&lt;", "<")
                         .replace("&gt;", ">")
                         .replace("&quot;", "\"")
                         .replace("&apos;", "'");
                out.append(xml).append('\n');
            }
        }
        return out.toString().trim();
    }

    private String recognizeImage(Uri uri, TextRecognizer recognizer) throws Exception {
        InputImage image = InputImage.fromFilePath(this, uri);
        Text result = Tasks.await(recognizer.process(image), 120, TimeUnit.SECONDS);
        return result.getText() == null ? "" : result.getText().trim();
    }

    private String recognizePdf(Uri uri, String importId, String name, TextRecognizer recognizer) throws Exception {
        StringBuilder out = new StringBuilder();
        try (ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor == null) throw new IOException("Could not open PDF.");
            try (PdfRenderer renderer = new PdfRenderer(descriptor)) {
                int total = renderer.getPageCount();
                for (int pageIndex = 0; pageIndex < total; pageIndex++) {
                    notifyKnowledgeJs("JaneKnowledgeImportProgress", importId, name, String.valueOf(pageIndex + 1), String.valueOf(total), "OCR page");
                    try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
                        float scale = Math.min(2.5f, Math.min(1800f / Math.max(1, page.getWidth()), 2400f / Math.max(1, page.getHeight())));
                        scale = Math.max(1.35f, scale);
                        int width = Math.max(1, Math.round(page.getWidth() * scale));
                        int height = Math.max(1, Math.round(page.getHeight() * scale));
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        try {
                            Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 120, TimeUnit.SECONDS);
                            String pageText = text.getText() == null ? "" : text.getText().trim();
                            if (!pageText.isEmpty()) out.append("\n\n--- Page ").append(pageIndex + 1).append(" ---\n").append(pageText);
                        } finally {
                            bitmap.recycle();
                        }
                    }
                }
            }
        }
        return out.toString().trim();
    }

    private String extractKnowledgeText(Uri uri, String name, String mimeType, String importId) throws Exception {
        String lowerName = name == null ? "" : name.toLowerCase();
        String lowerMime = mimeType == null ? "" : mimeType.toLowerCase();
        TextRecognizer recognizer = null;
        try {
            if (lowerMime.startsWith("image/") || lowerName.matches(".*\\.(png|jpe?g|webp|bmp|gif|heic|heif)$")) {
                recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                notifyKnowledgeJs("JaneKnowledgeImportProgress", importId, name, "1", "1", "OCR image");
                return recognizeImage(uri, recognizer);
            }
            if ("application/pdf".equals(lowerMime) || lowerName.endsWith(".pdf")) {
                recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                return recognizePdf(uri, importId, name, recognizer);
            }
            byte[] bytes = readUriBytes(uri, 50 * 1024 * 1024);
            if (lowerName.endsWith(".docx") || lowerMime.contains("wordprocessingml")) return extractDocxText(bytes);
            if (lowerMime.startsWith("text/") || lowerName.matches(".*\\.(txt|md|markdown|json|csv|tsv|xml|html?|rtf|log|ini|yaml|yml)$") || lowerMime.contains("json") || lowerMime.contains("xml") || lowerMime.contains("rtf")) {
                return new String(bytes, StandardCharsets.UTF_8).trim();
            }
            String fallback = new String(bytes, StandardCharsets.UTF_8).replaceAll("[^\\x09\\x0A\\x0D\\x20-\\x7E\\p{L}\\p{N}\\p{Punct}]", " ").trim();
            if (fallback.length() >= 40) return fallback;
            throw new IOException("This file type does not contain readable text that Jane can index.");
        } finally {
            if (recognizer != null) recognizer.close();
        }
    }

    private void sendKnowledgeTextToWeb(String importId, String name, String mimeType, String text, String metadataJson) {
        final int chunkChars = 60000;
        int total = Math.max(1, (text.length() + chunkChars - 1) / chunkChars);
        notifyKnowledgeJs("JaneKnowledgeImportStart", importId, name, mimeType, "1");
        for (int i = 0; i < total; i++) {
            int start = i * chunkChars;
            int end = Math.min(text.length(), start + chunkChars);
            String chunk = text.substring(start, end);
            String base64 = Base64.encodeToString(chunk.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            notifyKnowledgeJs("JaneKnowledgeImportChunk", importId, String.valueOf(i), String.valueOf(total), base64);
        }
        notifyKnowledgeJs("JaneKnowledgeImportComplete", importId, metadataJson);
    }

    private void processKnowledgeUri(Uri uri, int fileIndex, int totalFiles) {
        String name = getFileName(uri);
        String mimeType = getMimeType(uri);
        String archiveId = "archive_" + System.currentTimeMillis() + "_" + fileIndex + "_" + Integer.toHexString(name.hashCode());
        long sourceBytes = getFileSize(uri);
        File originalFile = null;
        notifyKnowledgeJs("JaneKnowledgeImportStart", archiveId, name, mimeType, String.valueOf(totalFiles));
        try {
            notifyKnowledgeJs("JaneKnowledgeImportProgress", archiveId, name, String.valueOf(fileIndex), String.valueOf(totalFiles), "Copying to native Archives");
            originalFile = copyKnowledgeOriginal(uri, archiveId, name);
            long importedAt = System.currentTimeMillis();
            JSONObject row = createKnowledgeArchiveRecord(archiveId, "", name, mimeType, originalFile, null, sourceBytes > 0 ? sourceBytes : originalFile.length(), importedAt, 0, false);
            row.put("indexStatus", "indexing");
            row.put("indexMessage", "Stored safely. Waiting to index.");
            row.put("pagesIndexed", 0);
            row.put("totalPages", 0);
            row.put("updatedAt", importedAt);
            upsertKnowledgeArchiveRecord(row);
            notifyKnowledgeJs("JaneNativeArchiveImportAccepted", archiveId, name);
            notifyKnowledgeJs("JaneNativeArchiveCatalogChanged", archiveId);
            indexStoredKnowledgeArchive(archiveId, fileIndex, totalFiles);
        } catch (Exception error) {
            try { if (originalFile != null) originalFile.delete(); } catch (Exception ignored) {}
            notifyKnowledgeJs("JaneKnowledgeImportError", archiveId, name, error.getMessage() == null ? "Import failed." : error.getMessage());
        }
    }

    // V84: knowledge import is deliberately network-independent. Selection, native copying,
    // PDF text extraction, image OCR, catalog updates, and WebView indexing are all local.
    // Connectivity changes never alter, replace, or delete preserved Archive information.
    private void handleKnowledgePickerResult(Intent data) {
        ArrayList<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            ClipData clip = data.getClipData();
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) {
            notifyKnowledgeJs("JaneKnowledgeImportError", "knowledge-picker", "Knowledge files", "No file was selected.");
            return;
        }
        new Thread(() -> {
            for (int i = 0; i < uris.size(); i++) processKnowledgeUri(uris.get(i), i + 1, uris.size());
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) {
                    notifyJs("JaneReceiveSpeech", results.get(0));
                } else {
                    notifyJs("JaneSpeechError", "I did not catch that.");
                }
            } else {
                notifyJs("JaneSpeechError", "Speech cancelled.");
            }
            return;
        }

        if (requestCode == JANE_FILE_PICK_REQUEST) {
            if (resultCode != RESULT_OK || data == null || (data.getData() == null && data.getClipData() == null)) {
                notifyJs("JaneFilePickCancelled", "");
                return;
            }
            handleKnowledgePickerResult(data);
            return;
        }

        if (requestCode != PICK_FILE_REQUEST) return;

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            notifyJs("JaneFilePickCancelled", "");
            return;
        }

        Uri uri = data.getData();
        new Thread(() -> {
            try {
                String name = getFileName(uri);
                String mimeType = getMimeType(uri);
                ByteArrayOutputStream output = new ByteArrayOutputStream();

                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new RuntimeException("Could not open selected file.");
                    byte[] buffer = new byte[8192];
                    int read;
                    int total = 0;
                    int maxBytes = 18 * 1024 * 1024;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > maxBytes) throw new RuntimeException("File is too large. Please choose a file under 18MB.");
                        output.write(buffer, 0, read);
                    }
                }

                String base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
                mainHandler.post(() -> {
                    String js = "window.JaneReceiveFile && window.JaneReceiveFile('" +
                        jsString(name) + "','" + jsString(mimeType) + "','" + jsString(base64) + "');";
                    webView.evaluateJavascript(js, null);
                });
            } catch (Exception error) {
                notifyJs("JaneFilePickError", error.getMessage());
            }
        }).start();
    }


    // V83: native, fully offline fallback search over preserved Archive text files.
    // This is used only when the WebView index has no usable match. It never mutates,
    // deletes, moves, or rebuilds the Archive originals or catalog.
    private List<String> knowledgeSearchTerms(String query) {
        Set<String> stop = new HashSet<>();
        String stopWords = "the a an and or but if then else when where why how what which who whom this that these those is are was were be been being to of in on for from with as by at into about over under after before it its i you me my your our we they them he she his her their not no yes can could should would will just than also have has had do does did tell give quick summary summarize explain define please something anything briefly short simple fact facts random reason reasons point points idea ideas example examples thing things basic basics list name ways way";
        for (String word : stopWords.split(" ")) stop.add(word);
        List<String> terms = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String lower = query == null ? "" : query.toLowerCase();
        for (String raw : lower.split("[^a-z0-9_-]+")) {
            if (raw.length() < 3 || stop.contains(raw)) continue;
            if (seen.add(raw)) terms.add(raw);
            String stem = raw;
            if (stem.endsWith("ies") && stem.length() > 5) stem = stem.substring(0, stem.length() - 3) + "y";
            else if (stem.endsWith("es") && stem.length() > 5) stem = stem.substring(0, stem.length() - 2);
            else if (stem.endsWith("s") && stem.length() > 4) stem = stem.substring(0, stem.length() - 1);
            else if (stem.endsWith("ing") && stem.length() > 6) stem = stem.substring(0, stem.length() - 3);
            else if (stem.endsWith("ed") && stem.length() > 5) stem = stem.substring(0, stem.length() - 2);
            if (stem.length() >= 3 && seen.add(stem)) terms.add(stem);
        }
        return terms;
    }

    private String compactKnowledgeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String knowledgeExcerpt(String text, int position) {
        if (text == null || text.isEmpty()) return "";
        int start = Math.max(0, position - 420);
        int end = Math.min(text.length(), position + 1250);
        int sentenceStart = Math.max(text.lastIndexOf('.', Math.max(0, position - 420)), text.lastIndexOf('\n', Math.max(0, position - 420)));
        if (sentenceStart >= start && sentenceStart < position) start = sentenceStart + 1;
        int sentenceEnd = text.indexOf('.', Math.min(text.length() - 1, position + 650));
        if (sentenceEnd > position && sentenceEnd < end) end = sentenceEnd + 1;
        return compactKnowledgeText(text.substring(start, end));
    }

    private int knowledgeWindowScore(String excerpt, String lowerTitle, List<String> terms) {
        String lower = excerpt == null ? "" : excerpt.toLowerCase();
        int score = 0;
        for (String term : terms) {
            if (lower.contains(term)) score += 14;
            if (lowerTitle.contains(term)) score += 24;
        }
        if (lower.matches(".*\\b(is|are|means|refers to|study of|branch of|concerned with|deals with|focuses on)\\b.*")) score += 20;
        for (String term : terms) {
            if (lower.contains(term + " is ") || lower.contains(term + " are ") || lower.contains(term + " refers to ")) score += 45;
        }
        if (lower.matches(".*\\b(contents?|copyright|isbn|this page intentionally left blank|designed for teaching|this textbook|preface|acknowledgements?|chapter overview)\\b.*")) score -= 90;
        return score;
    }

    private JSONArray nativeKnowledgeSearch(String query, int maxResults) throws Exception {
        List<String> terms = knowledgeSearchTerms(query);
        JSONArray output = new JSONArray();
        if (terms.isEmpty()) return output;
        JSONArray catalog = readKnowledgeCatalog();
        List<JSONObject> ordered = new ArrayList<>();
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject row = catalog.optJSONObject(i);
            if (row != null && !row.optString("textFile", "").isEmpty()) ordered.add(row);
        }
        java.util.Collections.sort(ordered, (a, b) -> {
            String at = a.optString("name", "").toLowerCase();
            String bt = b.optString("name", "").toLowerCase();
            int as = 0, bs = 0;
            for (String term : terms) { if (at.contains(term)) as++; if (bt.contains(term)) bs++; }
            return Integer.compare(bs, as);
        });

        List<JSONObject> hits = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        for (JSONObject row : ordered) {
            String title = row.optString("name", "Archive source");
            String lowerTitle = title.toLowerCase();
            boolean titleMatch = false;
            for (String term : terms) if (lowerTitle.contains(term)) { titleMatch = true; break; }
            String text;
            try { text = compactKnowledgeText(readArchiveText(row)); }
            catch (Exception error) { continue; }
            if (text.length() < 20) continue;
            String lower = text.toLowerCase();
            List<Integer> positions = new ArrayList<>();
            Set<Integer> preferredPositions = new java.util.LinkedHashSet<>();
            for (String term : terms) {
                String[] definitionPatterns = new String[] {
                    term + " is ", term + " are ", term + " refers to ",
                    "study of " + term, "branch of " + term,
                    term + " deals with ", term + " is concerned with "
                };
                for (String pattern : definitionPatterns) {
                    int from = 0;
                    for (int n = 0; n < 12; n++) {
                        int pos = lower.indexOf(pattern, from);
                        if (pos < 0) break;
                        preferredPositions.add(pos);
                        from = pos + Math.max(1, pattern.length());
                    }
                }
            }
            positions.addAll(preferredPositions);
            for (String term : terms) {
                int from = 0;
                for (int n = 0; n < 40; n++) {
                    int pos = lower.indexOf(term, from);
                    if (pos < 0) break;
                    if (!preferredPositions.contains(pos)) positions.add(pos);
                    from = pos + Math.max(1, term.length());
                }
            }
            if (positions.isEmpty() && titleMatch) {
                // Broad questions such as "give me facts about biology" should sample
                // the book, not return only its cover, preface, or copyright page.
                int[] fractions = new int[] { 8, 24, 42, 61, 79, 92 };
                for (int fraction : fractions) {
                    int position = Math.max(0, Math.min(text.length() - 1, (text.length() * fraction) / 100));
                    positions.add(position);
                }
            }
            for (Integer position : positions) {
                String excerpt = knowledgeExcerpt(text, position);
                if (excerpt.length() < 20) continue;
                String key = title + "|" + excerpt.substring(0, Math.min(180, excerpt.length())).toLowerCase();
                if (!dedupe.add(key)) continue;
                JSONObject hit = new JSONObject();
                hit.put("archiveId", row.optString("id", ""));
                hit.put("source", title);
                hit.put("text", excerpt);
                hit.put("score", knowledgeWindowScore(excerpt, lowerTitle, terms) + (titleMatch ? 35 : 0));
                hits.add(hit);
            }
            if (titleMatch && hits.size() >= Math.max(4, maxResults)) break;
        }
        java.util.Collections.sort(hits, (a, b) -> Integer.compare(b.optInt("score", 0), a.optInt("score", 0)));
        int count = Math.min(Math.max(1, maxResults), hits.size());
        for (int i = 0; i < count; i++) output.put(hits.get(i));
        return output;
    }

    private void searchKnowledgeArchives(String requestId, String query, int maxResults) {
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("results", nativeKnowledgeSearch(query, Math.max(1, Math.min(16, maxResults))));
                String base64 = Base64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                notifyKnowledgeJs("JaneNativeKnowledgeSearchResult", requestId, base64);
            } catch (Exception error) {
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("results", new JSONArray());
                    payload.put("error", error.getMessage() == null ? "Native Archive search failed." : error.getMessage());
                    String base64 = Base64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                    notifyKnowledgeJs("JaneNativeKnowledgeSearchResult", requestId, base64);
                } catch (Exception ignored) {}
            }
        }).start();
    }

    // V88: complete offline RAG. The on-device model first expands the user's
    // meaning into retrieval terms, then synthesizes the retrieved local text.
    // There is no HTTP request and no fragment-stitching fallback in this path.
    private JSONArray mergeKnowledgeResults(JSONArray... batches) throws Exception {
        List<JSONObject> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JSONArray batch : batches) {
            if (batch == null) continue;
            for (int i = 0; i < batch.length(); i++) {
                JSONObject row = batch.optJSONObject(i);
                if (row == null) continue;
                String text = row.optString("text", "");
                String key = row.optString("archiveId", "") + "|"
                    + text.substring(0, Math.min(220, text.length())).toLowerCase();
                if (seen.add(key)) merged.add(row);
            }
        }
        java.util.Collections.sort(merged, (a, b) -> Integer.compare(b.optInt("score", 0), a.optInt("score", 0)));
        JSONArray output = new JSONArray();
        int count = Math.min(16, merged.size());
        for (int i = 0; i < count; i++) output.put(merged.get(i));
        return output;
    }

    private void answerKnowledgeOffline(String requestId, String question, boolean ownerVerified) {
        new Thread(() -> {
            JSONObject payload = new JSONObject();
            try {
                OfflineKnowledgeEngine engine = OfflineKnowledgeEngine.getInstance(getApplicationContext());
                String expansion = "";
                try { expansion = engine.expandSearchQuery(question); }
                catch (Exception ignored) { expansion = ""; }

                JSONArray direct = nativeKnowledgeSearch(question, 12);
                JSONArray expanded = expansion.isEmpty()
                    ? new JSONArray()
                    : nativeKnowledgeSearch(question + " " + expansion, 16);
                JSONArray results = mergeKnowledgeResults(direct, expanded);
                String reply = engine.answer(question, results, ownerVerified);
                payload.put("reply", reply);
                payload.put("offline", true);
                payload.put("retrieved", results.length());
                payload.put("expanded", !expansion.isEmpty());
            } catch (Throwable error) {
                try {
                    payload.put("error", error.getMessage() == null
                        ? "Jane's on-device AI could not complete that answer."
                        : error.getMessage());
                    payload.put("offline", true);
                } catch (Exception ignored) {}
            }
            String base64 = Base64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            notifyKnowledgeJs("JaneNativeOfflineKnowledgeAnswerResult", requestId, base64);
        }, "JaneOfflineKnowledge").start();
    }

    private boolean hasUsageAccessInternal() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    getPackageName()
                );
            } else {
                mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    getPackageName()
                );
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String batteryHealthLabel(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "nominal";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "thermal-alert";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "critical";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "voltage-alert";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "service-required";
            case BatteryManager.BATTERY_HEALTH_COLD: return "cold-limit";
            default: return "unverified";
        }
    }

    private String batteryStatusLabel(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "charging";
            case BatteryManager.BATTERY_STATUS_FULL: return "charged";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "active";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "holding";
            default: return "unknown";
        }
    }

    private String thermalStatusLabel(int status) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "unavailable";
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "nominal";
            case PowerManager.THERMAL_STATUS_LIGHT: return "elevated";
            case PowerManager.THERMAL_STATUS_MODERATE: return "warm";
            case PowerManager.THERMAL_STATUS_SEVERE: return "restricted";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "critical";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "emergency";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "shutdown";
            default: return "unverified";
        }
    }

    private long startOfTodayMillis() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return start.getTimeInMillis();
    }

    private JSONObject readUsageVitals(boolean usageAccess) throws Exception {
        JSONObject usage = new JSONObject();
        usage.put("granted", usageAccess);
        usage.put("screenTimeMs", 0L);
        usage.put("janeTimeMs", 0L);
        usage.put("foregroundTimeMs", 0L);
        if (!usageAccess) return usage;

        long endTime = System.currentTimeMillis();
        long beginTime = startOfTodayMillis();
        UsageStatsManager manager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) return usage;

        long janeTime = 0L;
        long foregroundTime = 0L;
        List<UsageStats> stats = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            beginTime,
            endTime
        );
        if (stats != null) {
            for (UsageStats stat : stats) {
                long duration = Math.max(0L, stat.getTotalTimeInForeground());
                foregroundTime += duration;
                if (getPackageName().equals(stat.getPackageName())) janeTime += duration;
            }
        }

        long screenTime = 0L;
        boolean interactive = false;
        boolean sawScreenEvent = false;
        long interactiveSince = beginTime;
        UsageEvents events = manager.queryEvents(beginTime, endTime);
        if (events != null) {
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                if (type == UsageEvents.Event.SCREEN_INTERACTIVE) {
                    if (!interactive) interactiveSince = Math.max(beginTime, event.getTimeStamp());
                    interactive = true;
                    sawScreenEvent = true;
                } else if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                    long eventTime = Math.min(endTime, event.getTimeStamp());
                    if (interactive) {
                        screenTime += Math.max(0L, eventTime - interactiveSince);
                    } else if (!sawScreenEvent) {
                        screenTime += Math.max(0L, eventTime - beginTime);
                    }
                    interactive = false;
                    sawScreenEvent = true;
                }
            }
        }
        if (interactive) screenTime += Math.max(0L, endTime - interactiveSince);
        if (!sawScreenEvent) {
            PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (power != null && power.isInteractive()) {
                screenTime = Math.max(0L, endTime - beginTime);
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                screenTime = foregroundTime;
            }
        }

        usage.put("screenTimeMs", screenTime);
        usage.put("janeTimeMs", janeTime);
        usage.put("foregroundTimeMs", foregroundTime);
        return usage;
    }

    private String buildDeviceVitalsJson() {
        JSONObject vitals = new JSONObject();
        try {
            vitals.put("capturedAt", System.currentTimeMillis());
            vitals.put("uptimeMs", SystemClock.elapsedRealtime());

            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int health = battery == null ? BatteryManager.BATTERY_HEALTH_UNKNOWN
                : battery.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
            int status = battery == null ? BatteryManager.BATTERY_STATUS_UNKNOWN
                : battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            int plugged = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            int temperature = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            int voltage = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            int batteryPercent = level >= 0 && scale > 0 ? Math.round((level * 100f) / scale) : -1;
            vitals.put("batteryPercent", batteryPercent);
            vitals.put("batteryHealth", batteryHealthLabel(health));
            vitals.put("batteryStatus", batteryStatusLabel(status));
            vitals.put("batteryTemperatureC", temperature / 10.0d);
            vitals.put("batteryVoltageMv", voltage);
            vitals.put("externalPower", plugged != 0);

            StatFs storage = new StatFs(getFilesDir().getAbsolutePath());
            long storageTotal = storage.getTotalBytes();
            long storageAvailable = storage.getAvailableBytes();
            long storageUsed = Math.max(0L, storageTotal - storageAvailable);
            vitals.put("storageTotalBytes", storageTotal);
            vitals.put("storageAvailableBytes", storageAvailable);
            vitals.put("storageUsedBytes", storageUsed);
            vitals.put("storageUsedPercent", storageTotal > 0
                ? Math.round((storageUsed * 1000.0d) / storageTotal) / 10.0d
                : 0.0d);

            ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            if (activityManager != null) activityManager.getMemoryInfo(memory);
            long memoryUsed = Math.max(0L, memory.totalMem - memory.availMem);
            vitals.put("memoryTotalBytes", memory.totalMem);
            vitals.put("memoryAvailableBytes", memory.availMem);
            vitals.put("memoryUsedPercent", memory.totalMem > 0
                ? Math.round((memoryUsed * 1000.0d) / memory.totalMem) / 10.0d
                : 0.0d);
            vitals.put("memoryPressure", memory.lowMemory);

            PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
            int thermal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && power != null
                ? power.getCurrentThermalStatus()
                : -1;
            vitals.put("thermalState", thermalStatusLabel(thermal));
            vitals.put("powerReserve", power != null && power.isPowerSaveMode());

            boolean connected = false;
            String linkType = "offline";
            ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null) {
                Network network = connectivity.getActiveNetwork();
                NetworkCapabilities caps = network == null ? null : connectivity.getNetworkCapabilities(network);
                if (caps != null) {
                    connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) linkType = "wifi";
                    else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) linkType = "cellular";
                    else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) linkType = "ethernet";
                    else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) linkType = "vpn";
                    else linkType = connected ? "linked" : "offline";
                }
            }
            vitals.put("linkConnected", connected);
            vitals.put("linkType", linkType);

            boolean usageAccess = hasUsageAccessInternal();
            vitals.put("usage", readUsageVitals(usageAccess));
        } catch (Exception error) {
            try { vitals.put("telemetryError", error.getMessage() == null ? "Telemetry unavailable" : error.getMessage()); }
            catch (Exception ignored) {}
        }
        return vitals.toString();
    }

    public class JaneBridge {
        @JavascriptInterface
        public String getDeviceVitals() {
            return MainActivity.this.buildDeviceVitalsJson();
        }

        @JavascriptInterface
        public boolean hasUsageAccess() {
            return MainActivity.this.hasUsageAccessInternal();
        }

        @JavascriptInterface
        public void openUsageAccessSettings() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception packageScreenUnavailable) {
                    try {
                        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    } catch (Exception ignored) {
                        notifyJs("JaneDeviceVitalsError", "Android Usage Access settings are unavailable on this device.");
                    }
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void pickKnowledgeFile() {
            // V54 compile fix: JaneBridge must call the outer MainActivity method.
            runOnUiThread(() -> MainActivity.this.openJaneFilePicker());
        }

        @JavascriptInterface
        public String listKnowledgeArchives() {
            return MainActivity.this.readKnowledgeCatalog().toString();
        }

        @JavascriptInterface
        public void restoreKnowledgeArchives(String archiveIdsJson) {
            MainActivity.this.restoreKnowledgeArchives(archiveIdsJson);
        }

        @JavascriptInterface
        public void searchKnowledgeArchives(String requestId, String query, int maxResults) {
            MainActivity.this.searchKnowledgeArchives(requestId, query, maxResults);
        }

        @JavascriptInterface
        public void answerKnowledgeOffline(String requestId, String question, boolean ownerVerified) {
            MainActivity.this.answerKnowledgeOffline(requestId, question, ownerVerified);
        }

        @JavascriptInterface
        public void restoreNativeKnowledgeIndex() {
            // V81 compatibility shim: full-library restoration is intentionally disabled.
            // The WebView requests only archive IDs whose IndexedDB records are missing.
        }

        @JavascriptInterface
        public void linkKnowledgeArchive(String archiveId, String indexedDocId) {
            new Thread(() -> MainActivity.this.linkKnowledgeArchive(archiveId, indexedDocId)).start();
        }

        @JavascriptInterface
        public void previewKnowledgeArchive(String archiveId, int pageIndex) {
            MainActivity.this.sendArchivePreview(archiveId, pageIndex);
        }

        @JavascriptInterface
        public void deleteKnowledgeArchive(String archiveId) {
            new Thread(() -> MainActivity.this.deleteKnowledgeArchive(archiveId)).start();
        }

        @JavascriptInterface
        public void retryKnowledgeArchive(String archiveId) {
            MainActivity.this.retryKnowledgeArchive(archiveId);
        }

        @JavascriptInterface
        public void archiveLegacyKnowledgeStart(String archiveId, String indexedDocId, String name, String mimeType, String importedAt, String metaJson) {
            MainActivity.this.archiveLegacyKnowledgeStart(archiveId, indexedDocId, name, mimeType, importedAt, metaJson);
        }

        @JavascriptInterface
        public void archiveLegacyKnowledgeChunk(String archiveId, String base64) {
            MainActivity.this.archiveLegacyKnowledgeChunk(archiveId, base64);
        }

        @JavascriptInterface
        public void archiveLegacyKnowledgeComplete(String archiveId) {
            MainActivity.this.archiveLegacyKnowledgeComplete(archiveId);
        }

@JavascriptInterface
        public void playAssetVoice(String assetPath) {
            MainActivity.this.playAssetVoice(assetPath);
        }

        @JavascriptInterface
        public void speak(String text) {
            final String requestedText = text == null ? "" : text.trim();
            if (requestedText.isEmpty()) {
                notifyJs("JaneNativeAudioDone", "");
                return;
            }

            final int requestId = voiceRequestCounter.incrementAndGet();
            activeVoiceRequest = requestId;

            new Thread(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(BACKEND_TTS_URL);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(30000);
                    connection.setReadTimeout(60000);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    connection.setRequestProperty("Accept", "audio/mpeg");

                    String escapedText = requestedText
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r");

                    String body = "{\"text\":\"" + escapedText + "\"}";
                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }

                    int status = connection.getResponseCode();
                    InputStream inputStream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();

                    if (status < 200 || status >= 300) {
                        StringBuilder error = new StringBuilder();
                        if (inputStream != null) {
                            byte[] buffer = new byte[1024];
                            int read;
                            while ((read = inputStream.read(buffer)) != -1) {
                                error.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                            }
                        }
                        throw new RuntimeException("Voice server error " + status + ": " + error);
                    }

                    File audioFile = new File(getCacheDir(), "jane_voice_" + requestId + "_" + System.currentTimeMillis() + ".mp3");
                    try (FileOutputStream fos = new FileOutputStream(audioFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while (inputStream != null && (read = inputStream.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    }

                    if (requestId != activeVoiceRequest) {
                        try { audioFile.delete(); } catch (Exception ignored) {}
                        return;
                    }

                    mainHandler.post(() -> {
                        try {
                            if (requestId != activeVoiceRequest) {
                                try { audioFile.delete(); } catch (Exception ignored) {}
                                return;
                            }

                            try {
                                if (mediaPlayer != null) {
                                    try { mediaPlayer.stop(); } catch (Exception ignored) {}
                                    try { mediaPlayer.release(); } catch (Exception ignored) {}
                                    mediaPlayer = null;
                                }
                            } catch (Exception ignored) {}

                            mediaPlayer = new MediaPlayer();
                            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build());
                            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
                            mediaPlayer.setOnPreparedListener(player -> {
                                if (requestId != activeVoiceRequest) {
                                    try { player.release(); } catch (Exception ignored) {}
                                    try { audioFile.delete(); } catch (Exception ignored) {}
                                    return;
                                }
                                notifyJs("JaneNativeAudioStarted", "");
                                player.start();
                            });
                            mediaPlayer.setOnCompletionListener(player -> {
                                try { player.release(); } catch (Exception ignored) {}
                                mediaPlayer = null;
                                try { audioFile.delete(); } catch (Exception ignored) {}
                                if (requestId == activeVoiceRequest) notifyJs("JaneNativeAudioDone", "");
                            });
                            mediaPlayer.setOnErrorListener((player, what, extra) -> {
                                try { player.release(); } catch (Exception ignored) {}
                                mediaPlayer = null;
                                try { audioFile.delete(); } catch (Exception ignored) {}
                                if (requestId == activeVoiceRequest) notifyJs("JaneNativeAudioError", "Paid voice playback error.");
                                return true;
                            });
                            mediaPlayer.prepareAsync();
                        } catch (Exception playbackError) {
                            try { audioFile.delete(); } catch (Exception ignored) {}
                            if (requestId == activeVoiceRequest) notifyJs("JaneNativeAudioError", playbackError.getMessage());
                        }
                    });
                } catch (Exception error) {
                    if (requestId == activeVoiceRequest) notifyJs("JaneNativeAudioError", error.getMessage());
                } finally {
                    try { if (connection != null) connection.disconnect(); } catch (Exception ignored) {}
                }
            }).start();
        }


        @JavascriptInterface
        public void stop() {
            stopAudio();
        }


        @JavascriptInterface
        public void getGpsLocation() {
            mainHandler.post(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 23 &&
                        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }, 7044);
                        notifyJs("JaneGpsError", "Location permission requested. Tap Get GPS Location again after allowing it.");
                        return;
                    }

                    LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    Location best = null;

                    try {
                        Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (gps != null) best = gps;
                    } catch (Exception ignored) {}

                    try {
                        Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if (network != null && (best == null || network.getAccuracy() < best.getAccuracy())) best = network;
                    } catch (Exception ignored) {}

                    if (best != null) {
                        String json = "{\"lat\":" + best.getLatitude() +
                            ",\"lon\":" + best.getLongitude() +
                            ",\"accuracy\":" + best.getAccuracy() +
                            ",\"time\":" + System.currentTimeMillis() + "}";
                        webView.evaluateJavascript("window.JaneReceiveGps && window.JaneReceiveGps(" + json + ");", null);
                        return;
                    }

                    LocationListener listener = new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            try {
                                locationManager.removeUpdates(this);
                            } catch (Exception ignored) {}

                            String json = "{\"lat\":" + location.getLatitude() +
                                ",\"lon\":" + location.getLongitude() +
                                ",\"accuracy\":" + location.getAccuracy() +
                                ",\"time\":" + System.currentTimeMillis() + "}";
                            webView.evaluateJavascript("window.JaneReceiveGps && window.JaneReceiveGps(" + json + ");", null);
                        }

                        @Override public void onProviderEnabled(String provider) {}
                        @Override public void onProviderDisabled(String provider) {}
                    };

                    boolean requested = false;
                    try {
                        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, null);
                        requested = true;
                    } catch (Exception ignored) {}

                    if (!requested) {
                        try {
                            locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, null);
                            requested = true;
                        } catch (Exception ignored) {}
                    }

                    if (!requested) {
                        notifyJs("JaneGpsError", "No GPS or network location provider is enabled.");
                    }
                } catch (Exception error) {
                    notifyJs("JaneGpsError", error.getMessage() == null ? "GPS failed." : error.getMessage());
                }
            });
        }


        private LocationManager liveLocationManager = null;
        private LocationListener liveLocationListener = null;

        private void emitGps(Location location) {
            if (location == null) return;
            String json = "{\"lat\":" + location.getLatitude() +
                ",\"lon\":" + location.getLongitude() +
                ",\"accuracy\":" + location.getAccuracy() +
                ",\"time\":" + System.currentTimeMillis() + "}";
            webView.evaluateJavascript("window.JaneReceiveGps && window.JaneReceiveGps(" + json + ");", null);
        }

        private boolean hasLocationPermission() {
            return Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void startGpsTracking() {
            mainHandler.post(() -> {
                try {
                    if (!hasLocationPermission()) {
                        requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }, 7044);
                        notifyJs("JaneGpsError", "Location permission requested. Allow it, then reopen Travel Mode.");
                        return;
                    }

                    liveLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

                    Location best = null;
                    try {
                        Location gps = liveLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (gps != null) best = gps;
                    } catch (Exception ignored) {}
                    try {
                        Location network = liveLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if (network != null && (best == null || network.getAccuracy() < best.getAccuracy())) best = network;
                    } catch (Exception ignored) {}
                    if (best != null) emitGps(best);

                    if (liveLocationListener != null) {
                        try { liveLocationManager.removeUpdates(liveLocationListener); } catch (Exception ignored) {}
                    }

                    liveLocationListener = new LocationListener() {
                        @Override public void onLocationChanged(Location location) { emitGps(location); }
                        @Override public void onProviderEnabled(String provider) {}
                        @Override public void onProviderDisabled(String provider) {}
                    };

                    boolean requested = false;
                    try {
                        liveLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3500, 4, liveLocationListener);
                        requested = true;
                    } catch (Exception ignored) {}
                    try {
                        liveLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4500, 8, liveLocationListener);
                        requested = true;
                    } catch (Exception ignored) {}

                    if (!requested) notifyJs("JaneGpsError", "No GPS or network location provider is enabled.");
                } catch (Exception error) {
                    notifyJs("JaneGpsError", error.getMessage() == null ? "GPS failed." : error.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void stopGpsTracking() {
            mainHandler.post(() -> {
                try {
                    if (liveLocationManager != null && liveLocationListener != null) liveLocationManager.removeUpdates(liveLocationListener);
                } catch (Exception ignored) {}
                liveLocationListener = null;
            });
        }

        @JavascriptInterface
        public void startVoice() {
            mainHandler.post(() -> {
                try {
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Jane");
                    intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                    startActivityForResult(intent, SPEECH_REQUEST);
                } catch (ActivityNotFoundException error) {
                    notifyJs("JaneSpeechError", "No speech recognizer is available on this device.");
                }
            });
        }

        @JavascriptInterface
        public void pickFile() {
            mainHandler.post(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "application/pdf", "text/plain"});
                try {
                    startActivityForResult(intent, PICK_FILE_REQUEST);
                } catch (ActivityNotFoundException error) {
                    notifyJs("JaneFilePickError", "No file picker is available on this device.");
                }
            });
        }
    }
    @Override
    protected void onDestroy() {
        try { OfflineKnowledgeEngine.getInstance(getApplicationContext()).close(); }
        catch (Exception ignored) {}
        super.onDestroy();
    }

}
