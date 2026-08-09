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
final_js = (ASSETS / "monolith_final_ui.js").read_text(encoding="utf-8")
index = (ASSETS / "index.html").read_text(encoding="utf-8")
manifest = MANIFEST.read_text(encoding="utf-8")

for route in ("monolith-model", "monolith-voice", "monolith-rpg"):
    if route not in scene_runtime:
        raise SystemExit(f"Exclusive scene runtime is missing route: {route}")

if "__monolithExclusiveRouter" not in scene_runtime:
    raise SystemExit("Exclusive scene router marker is missing.")
if "sceneFor(name)" not in scene_runtime:
    raise SystemExit("Scene runtime does not expose dedicated module scene lookup.")

# The production Monolith module core must render directly into dedicated scene roots.
for forbidden in ("ensureOverlay", "state.overlay", 'document.createElement("div");\n    overlay.id = "monolithModuleOverlay"'):
    if forbidden in core:
        raise SystemExit(f"Deprecated module-overlay architecture survived in monolith_core.js: {forbidden}")

if 'data-jane-scene="monolith-voice"' not in voice and 'sceneFor?.("voice")' not in voice:
    raise SystemExit("Voice runtime is not bound to the dedicated Voice scene.")

for token in (
    "grid-template-columns:minmax(215px,21.5%) minmax(520px,54.5%) minmax(238px,24%)",
    ".jane-chat-grid",
    ".monolith-model-grid",
    ".monolith-voice-grid",
    ".monolith-rpg-grid",
    "#monolithModuleOverlay{display:none",
):
    if token not in final_css:
        raise SystemExit(f"Final cybernetic layout token missing: {token}")

if "auditExclusiveScene" not in final_js:
    raise SystemExit("Final UI runtime does not contain its scene-exclusivity audit.")

for forbidden in ("House Dedmon Access", '"72 BPM"', '"98.6°F"'):
    if forbidden in index:
        raise SystemExit(f"Deprecated overlay/fake telemetry payload survived cleanup: {forbidden}")

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

print("Final Monolith UI source architecture validated: exclusive scenes, landscape geometry, cybernetic resources, and legacy overlay removal are intact.")
