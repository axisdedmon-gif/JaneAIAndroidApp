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
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import ai.monolith.app.assistant.AssistSnapshotStore;
import ai.monolith.app.runtime.MonolithCoroutineScope;

/**
 * Monolith AI application shell. Jane is a character hosted by this application.
 *
 * The inherited legacy host still owns the durable WebView/RAG/voice infrastructure, but Monolith
 * owns the scene bootstrap contract explicitly. The Android host injects the exclusive scene
 * runtime first, then the Monolith module runtime, then the voice patch. A launch is marked stable
 * only after the Command Chamber has measurable, computed, foreground visibility.
 */
public class MonolithActivity extends HudMainActivity {
    private static final int PICK_VOICE_ASSETS = 8802;
    private static final int EXPORT_VOICE_DATASET = 8803;
    private static final long FIRST_SCENE_VERIFY_MS = 2200L;
    private static final long RETRY_SCENE_VERIFY_MS = 800L;
    private static final int MAX_SCENE_VERIFY_ATTEMPTS = 5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private VoiceModelStore voiceStore;
    private WebView monolithWebView;
    private MonolithCoroutineScope backgroundScope;
    private String pendingMode = "home";
    private File pendingVoiceExport;
    private boolean safeMode;
    private boolean sceneMounted;
    private int sceneVerifyAttempts;

    @Override
    public void setContentView(View view) {
        if (view instanceof WebView) monolithWebView = (WebView) view;
        super.setContentView(view);
    }

    @SuppressLint("AddJavascriptInterface")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MonolithCrashGuard.install(this);
        safeMode = MonolithCrashGuard.beginLaunch(this);
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
            if (monolithWebView == null) {
                throw new IllegalStateException("Monolith core WebView host was not captured by setContentView().");
            }
            monolithWebView.addJavascriptInterface(new MonolithBridge(), "AndroidMonolith");
            primeCoreSurface();
            if (safeMode) injectSafeModeIdentity();
            scheduleInjection();
            handler.postDelayed(this::verifySceneMount, FIRST_SCENE_VERIFY_MS);
        } catch (Throwable error) {
            failCoreMount("native startup initialization failed", error);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingMode = readMode(intent);
        scheduleInjection();
        if (!sceneMounted) handler.postDelayed(this::verifySceneMount, 500L);
        handler.postDelayed(() -> notifyMode(pendingMode), 400L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleInjection();
        if (!sceneMounted) handler.postDelayed(this::verifySceneMount, 650L);
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

    private String installedVersionName() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            String value = info.versionName;
            return value == null || value.trim().isEmpty() ? "unknown" : value;
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private void primeCoreSurface() {
        if (monolithWebView == null) return;
        monolithWebView.animate().cancel();
        monolithWebView.setAlpha(1f);
        monolithWebView.setVisibility(View.VISIBLE);
    }

    private void scheduleInjection() {
        handler.postDelayed(this::injectMonolithLayer, 80L);
        handler.postDelayed(this::injectMonolithLayer, 420L);
        handler.postDelayed(this::injectMonolithLayer, 1050L);
        handler.postDelayed(this::injectMonolithLayer, 1900L);
    }

    private void injectSafeModeIdentity() {
        if (monolithWebView == null) return;
        handler.postDelayed(() -> {
            if (monolithWebView == null) return;
            monolithWebView.evaluateJavascript(
                "(function(){window.__MONOLITH_SAFE_START=true;document.title='Monolith AI';" +
                "var a=document.querySelector('.deck-identity strong');if(a)a.textContent='MONOLITH AI';" +
                "var b=document.querySelector('.deck-identity span');if(b)b.textContent='SAFE START // OPTIONAL MODULES DEFERRED';" +
                "})();",
                null
            );
        }, 350L);
    }

    private void injectMonolithLayer() {
        if (monolithWebView == null || isFinishing()) return;
        final String script =
            "(function(){" +
            "if(!document||!document.head)return 'NO_DOCUMENT_HEAD';" +
            "function css(id,href){if(document.getElementById(id))return;var l=document.createElement('link');l.id=id;l.rel='stylesheet';l.href=href;document.head.appendChild(l);}" +
            "function load(id,src,ready,next){" +
                "var old=document.getElementById(id);" +
                "if(old){if(ready()){if(next)next();}else if(next){old.addEventListener('load',next,{once:true});}return;}" +
                "var s=document.createElement('script');s.id=id;s.src=src;s.async=false;" +
                "s.addEventListener('load',function(){s.dataset.loaded='true';if(next)next();},{once:true});" +
                "s.addEventListener('error',function(){document.documentElement.dataset.monolithLoadError=id;},{once:true});" +
                "document.head.appendChild(s);" +
            "}" +
            "css('monolith-core-css','file:///android_asset/monolith_core.css');" +
            "load('monolith-scene-runtime-js','file:///android_asset/monolith_scene_runtime.js',function(){return !!window.MonolithSceneRuntime;},function(){" +
                "load('monolith-core-js','file:///android_asset/monolith_core.js',function(){return !!window.MonolithCore;},function(){" +
                    "load('monolith-voice-runtime-js','file:///android_asset/monolith_voice_runtime_patch.js',function(){return !!window.MonolithVoiceRuntimePatch;},function(){" +
                        "if(window.MonolithVoiceRuntimePatch&&window.MonolithVoiceRuntimePatch.apply)window.MonolithVoiceRuntimePatch.apply();" +
                        "if(window.MonolithCore&&window.MonolithCore.refresh)window.MonolithCore.refresh();" +
                        "if(window.MonolithSceneRuntime&&window.MonolithSceneRuntime.refresh)window.MonolithSceneRuntime.refresh();" +
                    "});" +
                "});" +
            "});" +
            "return 'MONOLITH_INJECTION_SCHEDULED';" +
            "})();";
        try {
            monolithWebView.evaluateJavascript(script, null);
        } catch (RuntimeException error) {
            failCoreMount("WebView scene injection threw on the Android host", error);
        }
    }

    private void verifySceneMount() {
        if (sceneMounted || monolithWebView == null || isFinishing()) return;
        sceneVerifyAttempts++;
        final String probe =
            "(function(){" +
            "var root=document.documentElement;" +
            "var host=document.getElementById('janeSceneHost');" +
            "var active=host&&host.querySelector(':scope > [data-jane-scene][data-jane-active=\"true\"]');" +
            "var chamber=host&&host.querySelector(':scope > [data-jane-scene=\"command\"]');" +
            "var shell=chamber&&chamber.querySelector('.deck-grid');" +
            "var landscape=document.getElementById('monolith-landscape-gen2-css');" +
            "var runtimeStyle=document.getElementById('monolith-scene-runtime-css');" +
            "function snap(el,minW,minH){" +
                "if(!el)return {ok:false,w:0,h:0,display:'missing',visibility:'missing',opacity:0};" +
                "var cs=getComputedStyle(el);var r=el.getBoundingClientRect();var op=parseFloat(cs.opacity||'0');" +
                "return {ok:cs.display!=='none'&&cs.visibility!=='hidden'&&op>.02&&r.width>=minW&&r.height>=minH,w:Math.round(r.width),h:Math.round(r.height),display:cs.display,visibility:cs.visibility,opacity:op};" +
            "}" +
            "var hs=snap(host,200,120);var as=snap(active,200,120);var ss=snap(shell,180,100);" +
            "var cx=Math.max(1,Math.floor(innerWidth/2));var cy=Math.max(1,Math.floor(innerHeight/2));" +
            "var hit=document.elementFromPoint(cx,cy);var hitInCommand=!!(hit&&chamber&&chamber.contains(hit));" +
            "var initializing=!!root.classList.contains('monolith-scene-initializing');" +
            "var activeName=active?active.getAttribute('data-jane-scene'):'';" +
            "var hostAria=host?host.getAttribute('aria-hidden'):'';" +
            "var landscapeState=landscape?(landscape.dataset.monolithLoadState||(landscape.sheet?'sheet-ready':'linked')):'missing';" +
            "var ready=!!(window.MonolithSceneRuntime&&window.JaneSceneRouter&&host&&chamber&&active===chamber&&activeName==='command'&&hostAria!=='true'&&!initializing&&runtimeStyle&&hs.ok&&as.ok&&ss.ok&&hitInCommand);" +
            "return JSON.stringify({ready:ready,sceneRuntime:!!window.MonolithSceneRuntime,router:!!window.JaneSceneRouter,host:!!host,chamber:!!chamber,active:activeName,hostAria:hostAria,initializing:initializing,runtimeStyle:!!runtimeStyle,landscape:landscapeState,hostStyle:hs,activeStyle:as,shellStyle:ss,hitInCommand:hitInCommand,hit:hit?(hit.id||hit.className||hit.tagName):'',loadError:root.dataset.monolithLoadError||'',sceneMountedMarker:root.dataset.monolithSceneMounted||'',bodyScene:document.body&&document.body.dataset?document.body.dataset.janeScene||'':''});" +
            "})();";

        try {
            monolithWebView.evaluateJavascript(probe, value -> {
                if (sceneMounted || isFinishing()) return;
                JSONObject status = decodeJavascriptObject(value);
                if (status != null && status.optBoolean("ready", false)) {
                    sceneMounted = true;
                    revealCoreSurface();
                    MonolithCrashGuard.markStable(MonolithActivity.this);
                    notifyMode(pendingMode);
                    return;
                }

                String detail = status == null ? String.valueOf(value) : status.toString();
                if (sceneVerifyAttempts < MAX_SCENE_VERIFY_ATTEMPTS) {
                    injectMonolithLayer();
                    handler.postDelayed(this::verifySceneMount, RETRY_SCENE_VERIFY_MS);
                    return;
                }
                failCoreMount(
                    "Command Chamber did not become visibly painted after " + sceneVerifyAttempts + " probes; state=" + detail,
                    null
                );
            });
        } catch (RuntimeException error) {
            failCoreMount("scene-paint verification could not execute", error);
        }
    }

    private JSONObject decodeJavascriptObject(String value) {
        if (value == null || "null".equals(value)) return null;
        try {
            Object decoded = new JSONTokener(value).nextValue();
            String json = decoded instanceof String ? (String) decoded : String.valueOf(decoded);
            return new JSONObject(json);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void revealCoreSurface() {
        if (monolithWebView == null) return;
        monolithWebView.animate().cancel();
        monolithWebView.setVisibility(View.VISIBLE);
        monolithWebView.setAlpha(1f);
    }

    private void failCoreMount(String message, Throwable cause) {
        if (isFinishing()) return;
        String detail = message == null ? "Monolith Core scene mount failed." : message;
        IllegalStateException failure = cause == null
            ? new IllegalStateException(detail)
            : new IllegalStateException(detail, cause);
        MonolithCrashGuard.recordStartupFailure(this, failure);
        handler.post(() -> { throw failure; });
    }

    private void notifyMode(String mode) {
        if (monolithWebView == null || !sceneMounted) return;
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
                out.put("version", installedVersionName());
                out.put("characters", new JSONObject(CharacterRegistry.stateJson(MonolithActivity.this)));
                out.put("permissions", new JSONObject(PermissionCoordinator.stateJson(MonolithActivity.this)));
                out.put("voice", bootstrapVoiceState());
                out.put("assist", new JSONObject(AssistSnapshotStore.read(MonolithActivity.this)));
                out.put("accessibility", new JSONObject(MonolithAccessibilityService.snapshotJson()));
                out.put("startup", new JSONObject(MonolithCrashGuard.diagnosticJson(MonolithActivity.this)));
                out.put("safeMode", safeMode);
                out.put("sceneMounted", sceneMounted);
                out.put("sceneVerifyAttempts", sceneVerifyAttempts);
                out.put("launchMode", pendingMode);
                return out.toString();
            } catch (Throwable error) {
                return "{\"application\":\"Monolith AI\",\"version\":\"unknown\",\"safeMode\":true}";
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
