from __future__ import annotations

from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
RES = ROOT / "app/src/main/res"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

required_files = [
    ASSETS / "monolith_scene_runtime.js",
    ASSETS / "monolith_core.js",
    ASSETS / "monolith_voice_runtime_patch.js",
    ASSETS / "monolith_final_ui.css",
    ASSETS / "monolith_final_ui.js",
    ASSETS / "monolith_landscape_gen2.css",
    ASSETS / "house_dedmon_crest.webp",
    RES / "drawable/scifi_hardware_frame.xml",
    RES / "drawable/scifi_reactor_button.xml",
]
for path in required_files:
    if not path.is_file() or path.stat().st_size == 0:
        raise SystemExit(f"Required final UI asset is missing or empty: {path.relative_to(ROOT)}")

scene_runtime = (ASSETS / "monolith_scene_runtime.js").read_text(encoding="utf-8")
core = (ASSETS / "monolith_core.js").read_text(encoding="utf-8")
voice = (ASSETS / "monolith_voice_runtime_patch.js").read_text(encoding="utf-8")
final_css = (ASSETS / "monolith_final_ui.css").read_text(encoding="utf-8")
landscape_css = (ASSETS / "monolith_landscape_gen2.css").read_text(encoding="utf-8")
final_js = (ASSETS / "monolith_final_ui.js").read_text(encoding="utf-8")
index = (ASSETS / "index.html").read_text(encoding="utf-8")
manifest = MANIFEST.read_text(encoding="utf-8")

for route in ("monolith-launch", "monolith-model", "monolith-voice", "monolith-rpg"):
    if route not in scene_runtime:
        raise SystemExit(f"Exclusive scene runtime is missing route: {route}")

for token in (
    "MONOLITH-SCENE-3",
    "__monolithExclusiveRouter",
    "buildLaunchScene()",
    'sceneFor(name)',
    'house_dedmon_crest.webp',
    'House Dedmon Access',
    'monolithEnterButton',
):
    if token not in scene_runtime:
        raise SystemExit(f"Scene runtime generation-2 token is missing: {token}")

# The production Monolith module core must render directly into dedicated scene roots.
for forbidden in ("ensureOverlay", "state.overlay", 'document.createElement("div");\n    overlay.id = "monolithModuleOverlay"'):
    if forbidden in core:
        raise SystemExit(f"Deprecated module-overlay architecture survived in monolith_core.js: {forbidden}")

if 'data-jane-scene="monolith-voice"' not in voice and 'sceneFor?.("voice")' not in voice:
    raise SystemExit("Voice runtime is not bound to the dedicated Voice scene.")

# The previous final stylesheet still styles the secondary scenes, but viewport ownership now belongs
# to monolith_landscape_gen2.css. This prevents the portrait-era #home max-width rules from owning
# landscape geometry even if the legacy source remains in index.html for Safe Base compatibility.
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

if "auditExclusiveScene" not in final_js:
    raise SystemExit("Final UI runtime does not contain its scene-exclusivity audit.")

# The original House Dedmon gate must not survive inside index.html as a full-screen overlay. Its
# replacement is the dedicated monolith-launch scene built by MonolithSceneRuntime.
for forbidden in ('"72 BPM"', '"98.6°F"'):
    if forbidden in index:
        raise SystemExit(f"Fake telemetry payload survived cleanup: {forbidden}")
if 'id="ownerGate" class="hidden"' not in index:
    raise SystemExit("Hidden owner-gate compatibility anchor is missing from index.html.")
if "House Dedmon Access" in index:
    raise SystemExit("Legacy House Dedmon overlay survived in index.html instead of the dedicated launch scene.")

for activity in (
    "ai.monolith.app.MonolithBootstrapActivity",
    "ai.monolith.app.MonolithActivity",
    "ai.monolith.app.legacy.HudMainActivity",
):
    pattern = re.compile(
        rf'<activity\b(?=[^>]*android:name="{re.escape(activity)}")(?=[^>]*android:screenOrientation="sensorLandscape")[^>]*>',
        re.DOTALL,
    )
    if not pattern.search(manifest):
        raise SystemExit(f"Landscape lock missing for activity: {activity}")

if 'android:resizeableActivity="false"' not in manifest:
    raise SystemExit("Application resizeability must be disabled for deterministic landscape geometry.")

for xml_path in (
    RES / "drawable/scifi_hardware_frame.xml",
    RES / "drawable/scifi_reactor_button.xml",
    MANIFEST,
):
    ET.parse(xml_path)

print(
    "Final Monolith UI source architecture validated: dedicated House Dedmon launch scene, "
    "exclusive landscape scene ownership, cybernetic resources, and legacy overlay removal are intact."
)
