from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Remove the former product name while preserving Jane as the female character name.
for path in ROOT.rglob("*"):
    if not path.is_file() or ".git" in path.parts:
        continue
    if path.suffix.lower() not in {".java", ".xml", ".html", ".js", ".css", ".gradle", ".md", ".txt", ".sh", ".py", ".json", ".yml", ".yaml"}:
        continue
    try:
        source = path.read_text(encoding="utf-8")
    except Exception:
        continue
    updated = source.replace("Jane AI Assistant", "Monolith AI").replace("JaneAIAndroid", "MonolithAIAndroid")
    if updated != source:
        path.write_text(updated, encoding="utf-8")

# Move the preserved Java core into a Monolith-owned package at build time. The source
# paths remain stable so the large proven files do not need destructive path rewrites,
# while the compiled APK no longer carries the old application package namespace.
legacy_sources = [
    ROOT / "app/src/main/java/com/example/janeai/MainActivity.java",
    ROOT / "app/src/main/java/com/example/janeai/HudMainActivity.java",
    ROOT / "app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java",
]
for legacy in legacy_sources:
    legacy_text = legacy.read_text(encoding="utf-8")
    if "package com.example.janeai;" in legacy_text:
        legacy_text = legacy_text.replace("package com.example.janeai;", "package ai.monolith.app.legacy;", 1)
    elif "package ai.monolith.app.legacy;" not in legacy_text:
        raise SystemExit(f"Could not normalize Java package for {legacy.name}.")
    legacy.write_text(legacy_text, encoding="utf-8")

# Adaptive local-RAG personality policy. Jane remains the female character, while
# the active character name is resolved independently from the application identity.
engine = legacy_sources[2]
text = engine.read_text(encoding="utf-8")
old = '''        StringBuilder out = new StringBuilder();
        out.append("You are Jane, C.J.'s personal AI companion running completely offline. ");'''
new = '''        StringBuilder out = new StringBuilder();
        String activeCharacter = ai.monolith.app.CharacterRegistry.activeName(appContext);
        out.append("You are ").append(activeCharacter).append(", the active AI character hosted by Monolith AI and running completely offline. ");'''
if old in text:
    text = text.replace(old, new, 1)
elif "CharacterRegistry.activeName(appContext)" not in text:
    raise SystemExit("Could not locate the primary offline personality instruction.")

text = text.replace(
    '        out.append("The PRIVATE KNOWLEDGE is memory Jane has been taught. Treat it as knowledge you already understand, not as a book you are reading aloud. ")',
    '        out.append("The PRIVATE KNOWLEDGE is memory the active character has been taught. Treat it as your own understood memory, not as an external book you are reading aloud. ")',
    1,
)
text = text.replace(
    '.append("Lead with the actual answer. Use natural transitions. Keep details useful rather than padded. ")',
    '.append("Lead with the actual answer. Scan retrieved memory for only the point required by the request and ignore unrelated surrounding material. Use natural transitions. Keep details useful rather than padded. ")\n            .append("For a direct prompt that does not request a list, deep explanation, quotation, or sources, answer in 1 to 3 sentences. ")',
    1,
)
text = text.replace(
    'String shortSystem = "You are Jane, C.J.\'s offline AI companion. Answer in a natural conversational voice. "',
    'String shortSystem = "You are " + ai.monolith.app.CharacterRegistry.activeName(appContext) + ", the active offline AI character hosted by Monolith AI. Answer in a natural conversational voice. "',
    1,
)
text = text.replace("Jane's on-device model did not produce a usable response.", "The active on-device model did not produce a usable response.")
engine.write_text(text, encoding="utf-8")

# Extend the preserved Archives ingestion path to local audio/video transcription.
main = legacy_sources[0]
source = main.read_text(encoding="utf-8")

# Android 16 / API 36 startup correctness: Window.getInsetsController() delegates into
# DecorView internals that are not valid until the decor view is attached. The inherited
# shell previously called immersive mode before setContentView(), which caused an NPE in
# PhoneWindow.getInsetsController() on Samsung Android 16. Remove the pre-attach call and
# make the controller path attachment-aware. This is a lifecycle correction, not a catch.
early_immersive = '''        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        applyImmersiveMode();

        webView = new WebView(this);'''
fixed_early_immersive = '''        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        webView = new WebView(this);'''
if early_immersive in source:
    source = source.replace(early_immersive, fixed_early_immersive, 1)
elif "setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);\n\n        webView = new WebView(this);" not in source:
    raise SystemExit("Could not locate the pre-attach immersive-mode call in MainActivity.")

post_content_immersive = '''        setContentView(webView);
        applyImmersiveMode();'''
fixed_post_content_immersive = '''        setContentView(webView);
        scheduleImmersiveMode();'''
if post_content_immersive in source:
    source = source.replace(post_content_immersive, fixed_post_content_immersive, 1)
elif fixed_post_content_immersive not in source:
    raise SystemExit("Could not normalize the post-content immersive-mode scheduling.")

resume_immersive = '''    protected void onResume() {
        super.onResume();
        applyImmersiveMode();'''
fixed_resume_immersive = '''    protected void onResume() {
        super.onResume();
        scheduleImmersiveMode();'''
if resume_immersive in source:
    source = source.replace(resume_immersive, fixed_resume_immersive, 1)
elif fixed_resume_immersive not in source:
    raise SystemExit("Could not normalize onResume immersive-mode scheduling.")

old_immersive_method = '''    private void applyImmersiveMode() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(params);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }
'''
new_immersive_method = '''    private void scheduleImmersiveMode() {
        final android.view.Window window = getWindow();
        if (window == null) return;
        final View decorView = window.getDecorView();
        if (decorView == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !decorView.isAttachedToWindow()) {
            decorView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    view.removeOnAttachStateChangeListener(this);
                    view.post(MainActivity.this::applyImmersiveMode);
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    // No action. A later resume/focus event will schedule immersive mode again.
                }
            });
            return;
        }

        decorView.post(this::applyImmersiveMode);
    }

    private void applyImmersiveMode() {
        final android.view.Window window = getWindow();
        if (window == null) return;
        final View decorView = window.getDecorView();
        if (decorView == null) return;

        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(params);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            if (!decorView.isAttachedToWindow()) return;
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }
'''
if old_immersive_method in source:
    source = source.replace(old_immersive_method, new_immersive_method, 1)
elif "private void scheduleImmersiveMode()" not in source or "decorView.getWindowInsetsController()" not in source:
    raise SystemExit("Could not replace MainActivity immersive-mode lifecycle implementation.")

if '"audio/*"' not in source:
    source = source.replace(
        '            "image/*",\n            "text/*",',
        '            "image/*",\n            "audio/*",\n            "video/*",\n            "text/*",',
        1,
    )
media_anchor = '            byte[] bytes = readUriBytes(uri, 50 * 1024 * 1024);'
media_block = '''            if (lowerMime.startsWith("audio/") || lowerMime.startsWith("video/") ||
                lowerName.matches(".*\\\\.(wav|mp3|m4a|aac|ogg|flac|mp4|mkv|webm|mov)$")) {
                notifyKnowledgeJs("JaneKnowledgeImportProgress", importId, name, "1", "1", "Local media transcription");
                return ai.monolith.app.LocalMediaTranscriber.transcribeBlocking(this, uri);
            }
            byte[] bytes = readUriBytes(uri, 50 * 1024 * 1024);'''
if "LocalMediaTranscriber.transcribeBlocking" not in source:
    if media_anchor not in source:
        raise SystemExit("Could not locate Archive binary extraction insertion point.")
    source = source.replace(media_anchor, media_block, 1)

# Route systemic speech through the selected local Piper model before the inherited
# hosted voice path. If a local model is active but fails, report the local failure;
# do not silently upload that text to a server.
if "PiperTtsEngine.speakAsync" not in source:
    speak_anchor = '''            if (requestedText.isEmpty()) {
                notifyJs("JaneNativeAudioDone", "");
                return;
            }

            final int requestId = voiceRequestCounter.incrementAndGet();'''
    speak_block = '''            if (requestedText.isEmpty()) {
                notifyJs("JaneNativeAudioDone", "");
                return;
            }

            if (ai.monolith.app.PiperTtsEngine.hasRunnableActiveModel(MainActivity.this)) {
                activeVoiceRequest = voiceRequestCounter.incrementAndGet();
                ai.monolith.app.PiperTtsEngine.speakAsync(
                    MainActivity.this,
                    requestedText,
                    new ai.monolith.app.PiperTtsEngine.Listener() {
                        @Override public void onStarted() { notifyJs("JaneNativeAudioStarted", ""); }
                        @Override public void onDone() { notifyJs("JaneNativeAudioDone", ""); }
                        @Override public void onError(String message) {
                            notifyJs("JaneNativeAudioError", message == null ? "Local Piper speech failed." : message);
                        }
                    }
                );
                return;
            }

            final int requestId = voiceRequestCounter.incrementAndGet();'''
    if speak_anchor not in source:
        raise SystemExit("Could not locate the inherited speak() bridge for local Piper routing.")
    source = source.replace(speak_anchor, speak_block, 1)

if "PiperTtsEngine.stop();" not in source:
    stop_anchor = '''    private void stopAudio() {
        activeVoiceRequest = voiceRequestCounter.incrementAndGet();'''
    stop_block = '''    private void stopAudio() {
        ai.monolith.app.PiperTtsEngine.stop();
        activeVoiceRequest = voiceRequestCounter.incrementAndGet();'''
    if stop_anchor not in source:
        raise SystemExit("Could not locate stopAudio() for local Piper cancellation.")
    source = source.replace(stop_anchor, stop_block, 1)

source = source.replace(
    'intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Jane");',
    'intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to " + ai.monolith.app.CharacterRegistry.activeName(MainActivity.this));',
    1,
)
if "PiperTtsEngine.invalidate();" not in source:
    destroy_anchor = '''    protected void onDestroy() {
        try { OfflineKnowledgeEngine.getInstance(getApplicationContext()).close();'''
    destroy_block = '''    protected void onDestroy() {
        ai.monolith.app.PiperTtsEngine.invalidate();
        try { OfflineKnowledgeEngine.getInstance(getApplicationContext()).close();'''
    if destroy_anchor in source:
        source = source.replace(destroy_anchor, destroy_block, 1)
main.write_text(source, encoding="utf-8")

# Point the new application shell at the normalized Monolith-owned compatibility package
# and load the Voice Module behavior patch after the primary UI layer.
activity = ROOT / "app/src/main/java/ai/monolith/app/MonolithActivity.java"
activity_text = activity.read_text(encoding="utf-8")
activity_text = activity_text.replace("import com.example.janeai.HudMainActivity;", "import ai.monolith.app.legacy.HudMainActivity;")
activity_text = activity_text.replace("import com.example.janeai.MainActivity;", "import ai.monolith.app.legacy.MainActivity;")
if "monolith-voice-runtime-js" not in activity_text:
    activity_anchor = '''            "if(!document.getElementById('monolith-core-js')){var s=document.createElement('script');s.id='monolith-core-js';s.src='file:///android_asset/monolith_core.js';document.head.appendChild(s);}" +
            "else if(window.MonolithCore&&window.MonolithCore.refresh){window.MonolithCore.refresh();}})();";'''
    activity_block = '''            "if(!document.getElementById('monolith-core-js')){var s=document.createElement('script');s.id='monolith-core-js';s.src='file:///android_asset/monolith_core.js';document.head.appendChild(s);}" +
            "if(!document.getElementById('monolith-voice-runtime-js')){var v=document.createElement('script');v.id='monolith-voice-runtime-js';v.src='file:///android_asset/monolith_voice_runtime_patch.js';document.head.appendChild(v);}" +
            "else if(window.MonolithVoiceRuntimePatch&&window.MonolithVoiceRuntimePatch.apply){window.MonolithVoiceRuntimePatch.apply();}" +
            "if(window.MonolithCore&&window.MonolithCore.refresh){window.MonolithCore.refresh();}})();";'''
    if activity_anchor not in activity_text:
        raise SystemExit("Could not locate Monolith WebView asset injection.")
    activity_text = activity_text.replace(activity_anchor, activity_block, 1)
activity.write_text(activity_text, encoding="utf-8")

print("Monolith package migration, Android 16 lifecycle hardening, adaptive RAG, media Archives, local Piper speech, and Voice Module integration applied.")
