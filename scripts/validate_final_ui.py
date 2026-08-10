from __future__ import annotations

from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
RES = ROOT / "app/src/main/res"
JAVA = ROOT / "app/src/main/java/ai/monolith/app"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

required_files = [
    ASSETS / "monolith_scene_runtime.js",
    ASSETS / "monolith_core.js",
    ASSETS / "monolith_voice_runtime_patch.js",
    ASSETS / "monolith_final_ui.css",
    ASSETS / "monolith_final_ui.js",
    ASSETS / "monolith_landscape_gen2.css",
    ASSETS / "monolith_hardware_gen3.css",
    ASSETS / "house_dedmon_crest.webp",
    RES / "drawable/scifi_hardware_frame.xml",
    RES / "drawable/scifi_reactor_button.xml",
    JAVA / "MonolithActivity.java",
    JAVA / "MonolithApplication.java",
    JAVA / "MonolithSafeBaseActivity.java",
    JAVA / "MonolithCrashGuard.java",
]
for path in required_files:
    if not path.is_file() or path.stat().st_size == 0:
        raise SystemExit(f"Required Monolith runtime/UI file is missing or empty: {path.relative_to(ROOT)}")

scene_runtime = (ASSETS / "monolith_scene_runtime.js").read_text(encoding="utf-8")
core = (ASSETS / "monolith_core.js").read_text(encoding="utf-8")
voice = (ASSETS / "monolith_voice_runtime_patch.js").read_text(encoding="utf-8")
final_css = (ASSETS / "monolith_final_ui.css").read_text(encoding="utf-8")
landscape_css = (ASSETS / "monolith_landscape_gen2.css").read_text(encoding="utf-8")
hardware_css = (ASSETS / "monolith_hardware_gen3.css").read_text(encoding="utf-8")
final_js = (ASSETS / "monolith_final_ui.js").read_text(encoding="utf-8")
index = (ASSETS / "index.html").read_text(encoding="utf-8")
manifest = MANIFEST.read_text(encoding="utf-8")
activity_host = (JAVA / "MonolithActivity.java").read_text(encoding="utf-8")
application = (JAVA / "MonolithApplication.java").read_text(encoding="utf-8")
safe_base = (JAVA / "MonolithSafeBaseActivity.java").read_text(encoding="utf-8")
crash_guard = (JAVA / "MonolithCrashGuard.java").read_text(encoding="utf-8")

for route in ("monolith-launch", "monolith-model", "monolith-voice", "monolith-rpg"):
    if route not in scene_runtime:
        raise SystemExit(f"Exclusive scene runtime is missing route: {route}")

for token in (
    "MONOLITH-SCENE-4",
    "__monolithExclusiveRouter",
    "buildLaunchScene()",
    'sceneFor(name)',
    'house_dedmon_crest.webp',
    'House Dedmon Access',
    'monolithEnterButton',
    'document.documentElement.classList.remove("monolith-scene-initializing");',
    '.monolith-launch-scene .dedmon-launch-shell',
    'dataset.monolithLoadState = "loaded"',
):
    if token not in scene_runtime:
        raise SystemExit(f"Scene runtime visible-startup token is missing: {token}")

raf_deadlock = '''requestAnimationFrame(() => {\n      document.documentElement.classList.remove("monolith-scene-initializing")'''
if raf_deadlock in scene_runtime:
    raise SystemExit("Launch visibility still depends on requestAnimationFrame while the native WebView starts hidden.")

for token in (
    "monolith-scene-runtime-js",
    "file:///android_asset/monolith_scene_runtime.js",
    "monolith-core-js",
    "file:///android_asset/monolith_core.js",
    "verifySceneMount()",
    "MAX_SCENE_VERIFY_ATTEMPTS",
    "sceneMounted = true",
    "MonolithCrashGuard.markStable(MonolithActivity.this)",
    "exclusive scene did not become visibly painted",
    "primeCoreSurface()",
    "startupCurtain",
    "getComputedStyle",
    "getBoundingClientRect",
    "elementFromPoint",
    "hitInLaunch",
    "active===launch",
):
    if token not in activity_host:
        raise SystemExit(f"Android scene-paint contract is missing: {token}")

scene_loader_pos = activity_host.index("file:///android_asset/monolith_scene_runtime.js")
core_loader_pos = activity_host.index("file:///android_asset/monolith_core.js")
voice_loader_pos = activity_host.index("file:///android_asset/monolith_voice_runtime_patch.js")
if not (scene_loader_pos < core_loader_pos < voice_loader_pos):
    raise SystemExit("Android host must load scene runtime before core modules and voice runtime.")

if "if (safeMode) injectSafeModeIdentity();\n            else scheduleInjection();" in activity_host:
    raise SystemExit("Safe-mode launch still bypasses the Monolith scene runtime.")

for forbidden in ("ensureOverlay", "state.overlay", 'document.createElement("div");\n    overlay.id = "monolithModuleOverlay"'):
    if forbidden in core:
        raise SystemExit(f"Deprecated module-overlay architecture survived in monolith_core.js: {forbidden}")

if 'data-jane-scene="monolith-voice"' not in voice and 'sceneFor?.("voice")' not in voice:
    raise SystemExit("Voice runtime is not bound to the dedicated Voice scene.")

for token in (
    '.monolith-launch-scene',
    '.dedmon-launch-shell',
    '#home[data-jane-scene="command"]',
    'grid-template-columns:minmax(200px,21%) minmax(540px,55%) minmax(225px,24%)',
    '#home #homeJaneModel',
    '#vn[data-jane-scene="chat"]',
    '.jane-chat-grid',
):
    if token not in landscape_css:
        raise SystemExit(f"Generation-2 landscape geometry token missing: {token}")

for token in (
    ".monolith-model-grid",
    ".monolith-voice-grid",
    ".monolith-rpg-grid",
    "#monolithModuleOverlay{display:none",
):
    if token not in final_css:
        raise SystemExit(f"Secondary cybernetic layout token missing: {token}")

for token in (
    "MONOLITH-FINAL-UI-3",
    "monolith_hardware_gen3.css",
    "direct-handoff-v3",
    "monolithActionWired",
    'document.addEventListener("pointerup"',
    'document.addEventListener("touchend"',
    "enterButtonForEvent",
    "forceCommandScene",
    "syncCommandRouter",
    'scene.dataset.janeActive = active ? "true" : "false"',
    'history.replaceState({ janeScene: "command", monolithDirectHandoff: true }',
    'document.documentElement.dataset.monolithDirectHandoff',
    "auditExclusiveScene",
):
    if token not in final_js:
        raise SystemExit(f"Generation-3 direct-handoff/runtime token missing: {token}")

for token in (
    "--hw-brass",
    "repeating-conic-gradient",
    "mix-blend-mode:screen",
    ".dedmon-crest-bay",
    "width:min(68vh,560px)",
    ".dedmon-reactor-button",
    ".telemetry-track",
    ".deck-nav-pod",
):
    if token not in hardware_css:
        raise SystemExit(f"Generation-3 tactile hardware token missing: {token}")

if hardware_css.count("box-shadow") < 15:
    raise SystemExit("Generation-3 hardware skin is too visually shallow; expected layered physical depth.")
if hardware_css.count("border:2px") < 8:
    raise SystemExit("Generation-3 hardware skin is missing multi-layer mechanical housings.")

for forbidden in ('"72 BPM"', '"98.6°F"'):
    if forbidden in index:
        raise SystemExit(f"Fake telemetry payload survived cleanup: {forbidden}")
if 'id="ownerGate" class="hidden"' not in index:
    raise SystemExit("Hidden owner-gate compatibility anchor is missing from index.html.")
if "House Dedmon Access" in index:
    raise SystemExit("Legacy House Dedmon overlay survived in index.html instead of the dedicated launch scene.")

for token in ("WebView.disableWebView();", "disabled-safe-native"):
    if token not in application:
        raise SystemExit(f"Native Safe Base WebView-disable contract is missing: {token}")

for token in (
    "NATIVE RECOVERY CONSOLE // NO WEBVIEW",
    "CLEAR STATE + RETRY CORE",
    "MonolithApplication.readCrashReport(this)",
):
    if token not in safe_base:
        raise SystemExit(f"Native Safe Base recovery contract is missing: {token}")

if "clearStartupState(Context context)" not in crash_guard:
    raise SystemExit("Crash guard does not expose an explicit persisted startup-state reset.")
if "MonolithCrashGuard.clearStartupState(context)" not in application:
    raise SystemExit("Clearing the BIOS diagnostic does not clear crash-guard safe-mode state.")

for activity in (
    "ai.monolith.app.MonolithBootstrapActivity",
    "ai.monolith.app.MonolithActivity",
    "ai.monolith.app.MonolithSafeBaseActivity",
):
    pattern = re.compile(
        rf'<activity(?=\s)(?=[^>]*android:name="{re.escape(activity)}")(?=[^>]*android:screenOrientation="sensorLandscape")[^>]*>',
        re.DOTALL,
    )
    if not pattern.search(manifest):
        raise SystemExit(f"Landscape lock missing for activity: {activity}")

if re.search(r'<activity(?=\s)[^>]*android:name="ai\.monolith\.app\.legacy\.HudMainActivity"', manifest, re.DOTALL):
    raise SystemExit("Legacy HudMainActivity is still registered as a launchable Safe Base activity.")

alias_pattern = re.compile(
    r'<activity-alias(?=\s)(?=[^>]*android:name="ai\.monolith\.app\.legacy\.HudMainActivity")'
    r'(?=[^>]*android:targetActivity="ai\.monolith\.app\.MonolithSafeBaseActivity")[^>]*/>',
    re.DOTALL,
)
if not alias_pattern.search(manifest):
    raise SystemExit("Safe Base compatibility alias does not target MonolithSafeBaseActivity.")

if 'android:process=":safe"' not in manifest:
    raise SystemExit("Native Safe Base is not isolated in the :safe process.")
if 'android:resizeableActivity="false"' not in manifest:
    raise SystemExit("Application resizeability must be disabled for deterministic landscape geometry.")

for xml_path in (
    RES / "drawable/scifi_hardware_frame.xml",
    RES / "drawable/scifi_reactor_button.xml",
    MANIFEST,
):
    ET.parse(xml_path)

print(
    "Final Monolith architecture validated: scene generation 4 releases hidden startup synchronously; "
    "generation-3 UI uses a direct command-scene handoff with coordinate-aware touch capture before router sync; "
    "the House Dedmon crest remains centerpiece scale; Android still requires computed geometry and foreground "
    "hit-testing before Core becomes stable; Safe Base remains native and WebView-disabled."
)
