package ai.monolith.app;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.example.janeai.HudMainActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import ai.monolith.app.assistant.AssistSnapshotStore;
import ai.monolith.app.runtime.MonolithCoroutineScope;

/**
 * Monolith AI application shell. Jane is a character hosted by this application.
 *
 * Startup deliberately keeps optional permissions, Piper inspection, and module work outside the
 * critical Activity launch path. The inherited proven UI host remains available even if an
 * optional Monolith module reports a runtime failure.
 */
public class MonolithActivity extends HudMainActivity {
    private static final int PICK_VOICE_ASSETS = 8802;
    private static final int EXPORT_VOICE_DATASET = 8803;
    private static final long STABLE_CHECKPOINT_MS = 5000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private VoiceModelStore voiceStore;
    private WebView monolithWebView;
    private MonolithCoroutineScope backgroundScope;
    private String pendingMode = "home";
    private File pendingVoiceExport;
    private boolean safeMode;

    /**
     * Captures the inherited Activity content host through Android's normal setContentView contract.
     * This removes MonolithActivity's former reflective access to MainActivity.webView.
     */
    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        if (view instanceof WebView) monolithWebView = (WebView) view;
    }

    @SuppressLint("AddJavascriptInterface")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MonolithCrashGuard.install(this);
        safeMode = MonolithCrashGuard.beginLaunch(this);

        // The proven legacy core owns the base WebView and must initialize first.
        super.onCreate(savedInstanceState);

        try {
            CharacterRegistry.ensureDefaults(this);
            voiceStore = new VoiceModelStore(this);
            backgroundScope = new MonolithCoroutineScope((name, error) -> {
                MonolithCrashGuard.recordStartupFailure(MonolithActivity.this, error);
                runOnUiThread(() -> notifyVoiceNotice(
                    "ERROR:" + name + ":" + safeMessage(error, "background operation failed")
                ));
            });
            pendingMode = readMode(getIntent());
            if (monolithWebView != null) {
                monolithWebView.addJavascriptInterface(new MonolithBridge(), "AndroidMonolith");
            }

            // Permissions are user-triggered through the bridge. Never launch a permission storm
            // from Activity.onCreate(), especially before Android has granted the assistant role.
            if (safeMode) injectSafeModeIdentity();
            else scheduleInjection();
        } catch (Throwable error) {
            safeMode = true;
            MonolithCrashGuard.recordStartupFailure(this, error);
            injectSafeModeIdentity();
        }

        handler.postDelayed(() -> MonolithCrashGuard.markStable(MonolithActivity.this), STABLE_CHECKPOINT_MS);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingMode = readMode(intent);
        if (!safeMode) scheduleInjection();
        handler.postDelayed(() -> notifyMode(pendingMode), 400L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!safeMode) scheduleInjection();
        handler.postDelayed(() -> notifyMode(pendingMode), 300L);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (backgroundScope != null) {
            backgroundScope.close();
            backgroundScope = null;
        }
        super.onDestroy();
    }

    private static String safeMessage(Throwable error, String fallback) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message.trim();
    }

    private static String readMode(Intent intent) {
        if (intent == null) return "home";
        String monolith = intent.getStringExtra("monolith_mode");
        if (monolith != null && !monolith.trim().isEmpty()) return monolith;
        String legacy = intent.getStringExtra("jane_mode");
        return legacy == null || legacy.trim().isEmpty() ? "home" : legacy;
    }

    private void scheduleInjection() {
        handler.postDelayed(this::injectMonolithLayer, 260L);
        handler.postDelayed(this::injectMonolithLayer, 900L);
        handler.postDelayed(this::injectMonolithLayer, 1800L);
    }

    private void injectSafeModeIdentity() {
        if (monolithWebView == null) return;
        handler.postDelayed(() -> {
            if (monolithWebView == null) return;
            monolithWebView.evaluateJavascript(
                "(function(){document.title='Monolith AI';" +
                "var a=document.querySelector('.deck-identity strong');if(a)a.textContent='Monolith AI';" +
                "var b=document.querySelector('.deck-identity span');if(b)b.textContent='SAFE STARTUP // OPTIONAL MODULES DEFERRED';" +
                "})();",
                null
            );
        }, 350L);
    }

    private void injectMonolithLayer() {
        if (safeMode || monolithWebView == null) return;
        final String script = "(function(){if(!document||!document.head)return;" +
            "if(!document.getElementById('monolith-core-css')){var l=document.createElement('link');l.id='monolith-core-css';l.rel='stylesheet';l.href='file:///android_asset/monolith_core.css';document.head.appendChild(l);}" +
            "if(!document.getElementById('monolith-core-js')){var s=document.createElement('script');s.id='monolith-core-js';s.src='file:///android_asset/monolith_core.js';document.head.appendChild(s);}" +
            "if(!document.getElementById('monolith-voice-runtime-js')){var v=document.createElement('script');v.id='monolith-voice-runtime-js';v.src='file:///android_asset/monolith_voice_runtime_patch.js';document.head.appendChild(v);}" +
            "else if(window.MonolithVoiceRuntimePatch&&window.MonolithVoiceRuntimePatch.apply){window.MonolithVoiceRuntimePatch.apply();}" +
            "if(window.MonolithCore&&window.MonolithCore.refresh){window.MonolithCore.refresh();}})();";
        try {
            monolithWebView.evaluateJavascript(script, null);
            notifyMode(pendingMode);
        } catch (RuntimeException error) {
            MonolithCrashGuard.recordStartupFailure(this, error);
            safeMode = true;
            injectSafeModeIdentity();
        }
    }

    private void notifyMode(String mode) {
        if (monolithWebView == null || safeMode) return;
        String safe = (mode == null ? "home" : mode).replace("\\", "\\\\").replace("'", "\\'");
        try {
            monolithWebView.evaluateJavascript(
                "window.MonolithReceiveLaunchMode&&window.MonolithReceiveLaunchMode('" + safe + "');",
                null
            );
        } catch (RuntimeException error) {
            MonolithCrashGuard.recordStartupFailure(this, error);
        }
    }

    private String jsString(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void notifyVoiceNotice(String message) {
        if (monolithWebView == null) return;
        try {
            monolithWebView.evaluateJavascript(
                "window.MonolithVoiceNotice&&window.MonolithVoiceNotice('" + jsString(message) + "');",
                null
            );
        } catch (RuntimeException ignored) {}
    }

    private void launchIo(String name, Runnable task) {
        MonolithCoroutineScope scope = backgroundScope;
        if (scope == null) {
            notifyVoiceNotice("ERROR:Background runtime unavailable.");
            return;
        }
        scope.launchIo(name, task);
    }

    private JSONObject bootstrapVoiceState() throws Exception {
        JSONObject voice = new JSONObject();
        String active = voiceStore == null ? "" : voiceStore.activeModel();
        voice.put("activeModel", active == null ? "" : active);
        voice.put("runtime", "sherpa-onnx-piper");
        voice.put("runtimeState", active == null || active.trim().isEmpty() ? "inactive" : "deferred-until-voice-module");
        voice.put("datasets", new JSONArray());
        voice.put("models", new JSONArray());
        voice.put("startupDeferred", true);
        return voice;
    }

    private String fullVoiceWorkspace() {
        if (voiceStore == null) return "{\"datasets\":[],\"models\":[],\"runtimeState\":\"unavailable\"}";
        try {
            return voiceStore.stateJson();
        } catch (Throwable error) {
            MonolithCrashGuard.recordStartupFailure(this, error);
            return "{\"datasets\":[],\"models\":[],\"runtimeState\":\"runtime-error\"}";
        }
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
        launchIo("MonolithVoiceExportPrepare", () -> {
            try {
                if (voiceStore == null) throw new IllegalStateException("Voice workspace is unavailable.");
                File export = voiceStore.exportDataset(datasetId);
                runOnUiThread(() -> {
                    pendingVoiceExport = export;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/zip");
                    intent.putExtra(Intent.EXTRA_TITLE, export.getName());
                    startActivityForResult(intent, EXPORT_VOICE_DATASET);
                });
            } catch (Throwable error) {
                runOnUiThread(() -> notifyVoiceNotice("ERROR:" + safeMessage(error, "Dataset export failed.")));
            }
        });
    }

    private void finishDatasetExport(Uri destination) {
        File source = pendingVoiceExport;
        pendingVoiceExport = null;
        if (source == null || !source.isFile() || destination == null) {
            notifyVoiceNotice("ERROR:Dataset export target was unavailable.");
            return;
        }
        launchIo("MonolithVoiceExportWrite", () -> {
            try (FileInputStream in = new FileInputStream(source);
                 OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                if (out == null) throw new IllegalStateException("Export destination could not be opened.");
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                out.flush();
                runOnUiThread(() -> notifyVoiceNotice("DATASET EXPORT COMPLETE"));
            } catch (Throwable error) {
                runOnUiThread(() -> notifyVoiceNotice("ERROR:" + safeMessage(error, "Dataset export failed.")));
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PermissionCoordinator.REQUEST_ASSISTANT_ROLE) {
            notifyPermissionState();
            return;
        }
        if (requestCode == EXPORT_VOICE_DATASET) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) finishDatasetExport(data.getData());
            else {
                pendingVoiceExport = null;
                notifyVoiceNotice("DATASET EXPORT CANCELLED");
            }
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
        launchIo("MonolithVoiceImport", () -> {
            int imported = 0;
            String lastError = "";
            for (Uri uri : uris) {
                try {
                    if (voiceStore == null) throw new IllegalStateException("Voice workspace is unavailable.");
                    voiceStore.importAsset(uri);
                    imported++;
                } catch (Throwable error) {
                    lastError = safeMessage(error, "Voice asset import failed.");
                }
            }
            final int importedCount = imported;
            final String errorMessage = lastError;
            runOnUiThread(() -> {
                notifyVoiceWorkspace();
                if (importedCount > 0) {
                    notifyVoiceNotice("IMPORTED " + importedCount + " VOICE ASSET" + (importedCount == 1 ? "" : "S"));
                } else if (!errorMessage.isEmpty()) {
                    notifyVoiceNotice("ERROR:" + errorMessage);
                }
            });
        });
    }

    private void notifyPermissionState() {
        if (monolithWebView == null) return;
        String json = PermissionCoordinator.stateJson(this);
        try {
            monolithWebView.evaluateJavascript(
                "window.MonolithPermissionStateChanged&&window.MonolithPermissionStateChanged(" + json + ");",
                null
            );
        } catch (RuntimeException ignored) {}
    }

    private void notifyVoiceWorkspace() {
        if (monolithWebView == null) return;
        String json = fullVoiceWorkspace();
        try {
            monolithWebView.evaluateJavascript(
                "window.MonolithVoiceWorkspaceChanged&&window.MonolithVoiceWorkspaceChanged(" + json + ");",
                null
            );
        } catch (RuntimeException ignored) {}
    }

    public class MonolithBridge {
        @JavascriptInterface
        public String getSystemState() {
            try {
                JSONObject out = new JSONObject();
                out.put("application", "Monolith AI");
                out.put("version", "Beta 2.0.02");
                out.put("characters", new JSONObject(CharacterRegistry.stateJson(MonolithActivity.this)));
                out.put("permissions", new JSONObject(PermissionCoordinator.stateJson(MonolithActivity.this)));
                out.put("voice", bootstrapVoiceState());
                out.put("assist", new JSONObject(AssistSnapshotStore.read(MonolithActivity.this)));
                out.put("accessibility", new JSONObject(MonolithAccessibilityService.snapshotJson()));
                out.put("startup", new JSONObject(MonolithCrashGuard.diagnosticJson(MonolithActivity.this)));
                out.put("safeMode", safeMode);
                out.put("launchMode", pendingMode);
                return out.toString();
            } catch (Throwable error) {
                return "{\"application\":\"Monolith AI\",\"version\":\"Beta 2.0.02\",\"safeMode\":true}";
            }
        }

        @JavascriptInterface
        public String getCharacterState() {
            return CharacterRegistry.stateJson(MonolithActivity.this);
        }

        @JavascriptInterface
        public boolean setActiveCharacter(String id) {
            boolean changed = CharacterRegistry.setActive(MonolithActivity.this, id);
            if (changed) handler.post(() -> {
                if (monolithWebView == null) return;
                String state = CharacterRegistry.stateJson(MonolithActivity.this);
                monolithWebView.evaluateJavascript(
                    "window.MonolithCharacterChanged&&window.MonolithCharacterChanged(" + state + ");",
                    null
                );
            });
            return changed;
        }

        @JavascriptInterface
        public void addCharacterExperience(long amount) {
            CharacterRegistry.addExperience(MonolithActivity.this, amount);
        }

        @JavascriptInterface
        public String getPermissionState() {
            return PermissionCoordinator.stateJson(MonolithActivity.this);
        }

        @JavascriptInterface
        public void requestRuntimePermissions() {
            runOnUiThread(() -> {
                PermissionCoordinator.requestRuntimePermissions(MonolithActivity.this);
                handler.postDelayed(MonolithActivity.this::notifyPermissionState, 800L);
            });
        }

        @JavascriptInterface
        public void requestAssistantRestrictedPermissions() {
            runOnUiThread(() -> {
                PermissionCoordinator.requestAssistantRestrictedPermissions(MonolithActivity.this);
                handler.postDelayed(MonolithActivity.this::notifyPermissionState, 800L);
            });
        }

        @JavascriptInterface
        public void openSpecialAccess(String id) {
            runOnUiThread(() -> {
                if ("accessibility".equals(id)) PermissionCoordinator.openAccessibilitySettings(MonolithActivity.this);
                else if ("overlay".equals(id)) PermissionCoordinator.openOverlaySettings(MonolithActivity.this);
                else if ("notification_policy".equals(id)) PermissionCoordinator.openPolicySettings(MonolithActivity.this);
                else if ("assistant".equals(id)) PermissionCoordinator.openAssistantSettings(MonolithActivity.this);
                else {
                    try {
                        startActivity(new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())
                        ));
                    } catch (RuntimeException error) {
                        notifyVoiceNotice("ERROR:" + safeMessage(error, "Application settings are unavailable."));
                    }
                }
            });
        }

        @JavascriptInterface
        public String getAssistSnapshot() {
            return AssistSnapshotStore.read(MonolithActivity.this);
        }

        @JavascriptInterface
        public String getAccessibilitySnapshot() {
            return MonolithAccessibilityService.snapshotJson();
        }

        @JavascriptInterface
        public String getStartupDiagnostics() {
            return MonolithCrashGuard.diagnosticJson(MonolithActivity.this);
        }

        @JavascriptInterface
        public String getVoiceWorkspace() {
            return fullVoiceWorkspace();
        }

        @JavascriptInterface
        public String startVoiceSample(String datasetId, String transcript) {
            try {
                if (voiceStore == null) throw new IllegalStateException("Voice workspace is unavailable.");
                return voiceStore.startRecording(datasetId, transcript);
            } catch (Throwable error) {
                return "ERROR:" + safeMessage(error, "recording failed");
            }
        }

        @JavascriptInterface
        public String stopVoiceSample() {
            try {
                if (voiceStore == null) throw new IllegalStateException("Voice workspace is unavailable.");
                String result = voiceStore.stopRecording();
                handler.post(MonolithActivity.this::notifyVoiceWorkspace);
                return result;
            } catch (Throwable error) {
                return "ERROR:" + safeMessage(error, "recording failed");
            }
        }

        @JavascriptInterface
        public void pickVoiceAssets() {
            runOnUiThread(MonolithActivity.this::pickVoiceAssets);
        }

        @JavascriptInterface
        public void exportVoiceDataset(String datasetId) {
            runOnUiThread(() -> beginDatasetExport(datasetId));
        }

        @JavascriptInterface
        public boolean setActiveVoiceModel(String id) {
            try {
                if (voiceStore == null) return false;
                boolean active = voiceStore.setActiveModel(id);
                if (active) handler.post(MonolithActivity.this::notifyVoiceWorkspace);
                return active;
            } catch (Throwable error) {
                MonolithCrashGuard.recordStartupFailure(MonolithActivity.this, error);
                return false;
            }
        }
    }
}
