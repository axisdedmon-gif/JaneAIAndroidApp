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

import java.lang.reflect.Field;
import java.util.ArrayList;

import ai.monolith.app.assistant.AssistSnapshotStore;

/**
 * Monolith AI application shell. Jane is a character hosted by this application,
 * while the stable legacy core remains inherited during the package migration.
 */
public class MonolithActivity extends HudMainActivity {
    private static final int PICK_VOICE_ASSETS = 8802;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private VoiceModelStore voiceStore;
    private WebView monolithWebView;
    private String pendingMode = "home";

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

    private void pickVoiceAssets() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "audio/wav", "audio/x-wav", "audio/*", "application/json",
            "application/octet-stream", "text/plain", "text/csv"
        });
        startActivityForResult(intent, PICK_VOICE_ASSETS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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
            for (Uri uri : uris) {
                try { voiceStore.importAsset(uri); } catch (Exception ignored) {}
            }
            runOnUiThread(this::notifyVoiceWorkspace);
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
                out.put("characters", new JSONObject(CharacterRegistry.stateJson(MonolithActivity.this)));
                out.put("permissions", new JSONObject(PermissionCoordinator.stateJson(MonolithActivity.this)));
                out.put("voice", new JSONObject(voiceStore.stateJson()));
                out.put("assist", new JSONObject(AssistSnapshotStore.read(MonolithActivity.this)));
                out.put("accessibility", new JSONObject(MonolithAccessibilityService.snapshotJson()));
                out.put("launchMode", pendingMode);
                return out.toString();
            } catch (Exception error) {
                return "{\"application\":\"Monolith AI\"}";
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
        public boolean setActiveVoiceModel(String id) {
            boolean active = voiceStore.setActiveModel(id);
            if (active) handler.post(MonolithActivity.this::notifyVoiceWorkspace);
            return active;
        }
    }
}
