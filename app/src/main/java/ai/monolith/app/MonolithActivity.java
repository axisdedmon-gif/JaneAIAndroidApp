package ai.monolith.app;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.example.janeai.HudMainActivity;
import com.example.janeai.MainActivity;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;

import ai.monolith.app.assistant.AssistSnapshotStore;

/**
 * Monolith AI application shell. Jane is a character hosted by this application,
 * while the stable legacy core remains inherited during the package migration.
 */
public class MonolithActivity extends HudMainActivity {
    private static final int PICK_VOICE_ASSETS = 8802;
    private static final int EXPORT_VOICE_DATASET = 8803;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private VoiceModelStore voiceStore;
    private WebView monolithWebView;
    private String pendingMode = "home";
    private File pendingVoiceExport;

    @SuppressLint("AddJavascriptInterface")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        CharacterRegistry.ensureDefaults(this);
        super.onCreate(savedInstanceState);
        voiceStore = new VoiceModelStore(this);
        pendingMode = readMode(getIntent());
        monolithWebView = resolveWebView();
        if (monolithWebView != null) monolithWebView.addJavascriptInterface(new MonolithBridge(), "AndroidMonolith");
        PermissionCoordinator.requestRuntimePermissions(this);
        scheduleInjection();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingMode = readMode(intent);
        scheduleInjection();
        handler.postDelayed(() -> notifyMode(pendingMode), 400L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleInjection();
        handler.postDelayed(() -> notifyMode(pendingMode), 300L);
    }

    private static String readMode(Intent intent) {
        if (intent == null) return "home";
        String monolith = intent.getStringExtra("monolith_mode");
        if (monolith != null && !monolith.trim().isEmpty()) return monolith;
        String legacy = intent.getStringExtra("jane_mode");
        return legacy == null || legacy.trim().isEmpty() ? "home" : legacy;
    }

    private WebView resolveWebView() {
        try {
            Field field = MainActivity.class.getDeclaredField("webView");
            field.setAccessible(true);
            Object value = field.get(this);
            return value instanceof WebView ? (WebView) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void scheduleInjection() {
        handler.postDelayed(this::injectMonolithLayer, 260L);
        handler.postDelayed(this::injectMonolithLayer, 900L);
        handler.postDelayed(this::injectMonolithLayer, 1800L);
    }

    private void injectMonolithLayer() {
        if (monolithWebView == null) monolithWebView = resolveWebView();
        if (monolithWebView == null) return;
        String script = "(function(){if(!document||!document.head)return;" +
            "if(!document.getElementById('monolith-core-css')){var l=document.createElement('link');l.id='monolith-core-css';l.rel='stylesheet';l.href='file:///android_asset/monolith_core.css';document.head.appendChild(l);}" +
            "if(!document.getElementById('monolith-core-js')){var s=document.createElement('script');s.id='monolith-core-js';s.src='file:///android_asset/monolith_core.js';document.head.appendChild(s);}" +
            "else if(window.MonolithCore&&window.MonolithCore.refresh){window.MonolithCore.refresh();}})();";
        monolithWebView.evaluateJavascript(script, null);
        notifyMode(pendingMode);
    }

    private void notifyMode(String mode) {
        if (monolithWebView == null) return;
        String safe = (mode == null ? "home" : mode).replace("\\", "\\\\").replace("'", "\\'");
        monolithWebView.evaluateJavascript("window.MonolithReceiveLaunchMode&&window.MonolithReceiveLaunchMode('" + safe + "');", null);
    }

    private String jsString(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void notifyVoiceNotice(String message) {
        if (monolithWebView == null) return;
        monolithWebView.evaluateJavascript(
            "window.MonolithVoiceNotice&&window.MonolithVoiceNotice('" + jsString(message) + "');",
            null
        );
    }

    private void pickVoiceAssets() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "audio/wav", "audio/x-wav", "audio/*", "application/json",
            "application/octet-stream", "text/plain", "text/csv", "application/zip"
        });
        startActivityForResult(intent, PICK_VOICE_ASSETS);
    }

    private void beginDatasetExport(String datasetId) {
        new Thread(() -> {
            try {
                File export = voiceStore.exportDataset(datasetId);
                runOnUiThread(() -> {
                    pendingVoiceExport = export;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/zip");
                    intent.putExtra(Intent.EXTRA_TITLE, export.getName());
                    startActivityForResult(intent, EXPORT_VOICE_DATASET);
                });
            } catch (Exception error) {
                runOnUiThread(() -> notifyVoiceNotice("ERROR:" + (error.getMessage() == null ? "Dataset export failed." : error.getMessage())));
            }
        }, "MonolithVoiceExportPrepare").start();
    }

    private void finishDatasetExport(Uri destination) {
        File source = pendingVoiceExport;
        pendingVoiceExport = null;
        if (source == null || !source.isFile() || destination == null) {
            notifyVoiceNotice("ERROR:Dataset export target was unavailable.");
            return;
        }
        new Thread(() -> {
            try (FileInputStream in = new FileInputStream(source); OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                if (out == null) throw new IllegalStateException("Export destination could not be opened.");
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                out.flush();
                runOnUiThread(() -> notifyVoiceNotice("DATASET EXPORT COMPLETE"));
            } catch (Exception error) {
                runOnUiThread(() -> notifyVoiceNotice("ERROR:" + (error.getMessage() == null ? "Dataset export failed." : error.getMessage())));
            }
        }, "MonolithVoiceExportWrite").start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == EXPORT_VOICE_DATASET) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) finishDatasetExport(data.getData());
            else { pendingVoiceExport = null; notifyVoiceNotice("DATASET EXPORT CANCELLED"); }
            return;
        }
        if (requestCode != PICK_VOICE_ASSETS) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (resultCode != RESULT_OK || data == null) {
            notifyVoiceWorkspace();
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        new Thread(() -> {
            int imported = 0;
            String lastError = "";
            for (Uri uri : uris) {
                try { voiceStore.importAsset(uri); imported++; }
                catch (Exception error) { lastError = error.getMessage() == null ? "Voice asset import failed." : error.getMessage(); }
            }
            final int importedCount = imported;
            final String errorMessage = lastError;
            runOnUiThread(() -> {
                notifyVoiceWorkspace();
                if (importedCount > 0) notifyVoiceNotice("IMPORTED " + importedCount + " VOICE ASSET" + (importedCount == 1 ? "" : "S"));
                else if (!errorMessage.isEmpty()) notifyVoiceNotice("ERROR:" + errorMessage);
            });
        }, "MonolithVoiceImport").start();
    }

    private void notifyVoiceWorkspace() {
        if (monolithWebView == null || voiceStore == null) return;
        String json = voiceStore.stateJson();
        monolithWebView.evaluateJavascript("window.MonolithVoiceWorkspaceChanged&&window.MonolithVoiceWorkspaceChanged(" + json + ");", null);
    }

    public class MonolithBridge {
        @JavascriptInterface
        public String getSystemState() {
            try {
                JSONObject out = new JSONObject();
                out.put("application", "Monolith AI");
                out.put("version", "Beta 2.0.01");
                out.put("characters", new JSONObject(CharacterRegistry.stateJson(MonolithActivity.this)));
                out.put("permissions", new JSONObject(PermissionCoordinator.stateJson(MonolithActivity.this)));
                out.put("voice", new JSONObject(voiceStore.stateJson()));
                out.put("assist", new JSONObject(AssistSnapshotStore.read(MonolithActivity.this)));
                out.put("accessibility", new JSONObject(MonolithAccessibilityService.snapshotJson()));
                out.put("launchMode", pendingMode);
                return out.toString();
            } catch (Exception error) {
                return "{\"application\":\"Monolith AI\",\"version\":\"Beta 2.0.01\"}";
            }
        }

        @JavascriptInterface
        public String getCharacterState() { return CharacterRegistry.stateJson(MonolithActivity.this); }

        @JavascriptInterface
        public boolean setActiveCharacter(String id) {
            boolean changed = CharacterRegistry.setActive(MonolithActivity.this, id);
            if (changed) handler.post(() -> {
                String state = CharacterRegistry.stateJson(MonolithActivity.this);
                monolithWebView.evaluateJavascript("window.MonolithCharacterChanged&&window.MonolithCharacterChanged(" + state + ");", null);
            });
            return changed;
        }

        @JavascriptInterface
        public void addCharacterExperience(long amount) {
            CharacterRegistry.addExperience(MonolithActivity.this, amount);
        }

        @JavascriptInterface
        public String getPermissionState() { return PermissionCoordinator.stateJson(MonolithActivity.this); }

        @JavascriptInterface
        public void requestRuntimePermissions() { runOnUiThread(() -> PermissionCoordinator.requestRuntimePermissions(MonolithActivity.this)); }

        @JavascriptInterface
        public void openSpecialAccess(String id) {
            runOnUiThread(() -> {
                if ("accessibility".equals(id)) PermissionCoordinator.openAccessibilitySettings(MonolithActivity.this);
                else if ("overlay".equals(id)) PermissionCoordinator.openOverlaySettings(MonolithActivity.this);
                else if ("notification_policy".equals(id)) PermissionCoordinator.openPolicySettings(MonolithActivity.this);
                else if ("assistant".equals(id)) PermissionCoordinator.openAssistantSettings(MonolithActivity.this);
                else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            });
        }

        @JavascriptInterface
        public String getAssistSnapshot() { return AssistSnapshotStore.read(MonolithActivity.this); }

        @JavascriptInterface
        public String getAccessibilitySnapshot() { return MonolithAccessibilityService.snapshotJson(); }

        @JavascriptInterface
        public String getVoiceWorkspace() { return voiceStore.stateJson(); }

        @JavascriptInterface
        public String startVoiceSample(String datasetId, String transcript) {
            try { return voiceStore.startRecording(datasetId, transcript); }
            catch (Exception error) { return "ERROR:" + (error.getMessage() == null ? "recording failed" : error.getMessage()); }
        }

        @JavascriptInterface
        public String stopVoiceSample() {
            try {
                String result = voiceStore.stopRecording();
                handler.post(MonolithActivity.this::notifyVoiceWorkspace);
                return result;
            } catch (Exception error) {
                return "ERROR:" + (error.getMessage() == null ? "recording failed" : error.getMessage());
            }
        }

        @JavascriptInterface
        public void pickVoiceAssets() { runOnUiThread(MonolithActivity.this::pickVoiceAssets); }

        @JavascriptInterface
        public void exportVoiceDataset(String datasetId) { runOnUiThread(() -> beginDatasetExport(datasetId)); }

        @JavascriptInterface
        public boolean setActiveVoiceModel(String id) {
            boolean active = voiceStore.setActiveModel(id);
            if (active) handler.post(MonolithActivity.this::notifyVoiceWorkspace);
            return active;
        }
    }
}
