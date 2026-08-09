from __future__ import annotations

from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
apks = sorted(DIST.glob("*.apk"))
if len(apks) != 1:
    raise SystemExit(f"Expected exactly one packaged Monolith APK in dist/, found {len(apks)}.")

apk = apks[0]
required_assets = {
    "assets/monolith_scene_runtime.js",
    "assets/monolith_core.js",
    "assets/monolith_voice_runtime_patch.js",
    "assets/monolith_final_ui.css",
    "assets/monolith_final_ui.js",
}

with zipfile.ZipFile(apk) as archive:
    names = set(archive.namelist())
    missing = required_assets - names
    if missing:
        raise SystemExit(f"Packaged APK is missing final UI assets: {sorted(missing)}")

    for asset in required_assets:
        if archive.getinfo(asset).file_size < 200:
            raise SystemExit(f"Packaged final UI asset is unexpectedly small: {asset}")

    index = archive.read("assets/index.html").decode("utf-8", errors="replace")
    if "House Dedmon Access" in index:
        raise SystemExit("Legacy House Dedmon visual gate was packaged into the APK.")
    if '"72 BPM"' in index or '"98.6°F"' in index:
        raise SystemExit("Fake biometric values were packaged into the APK.")
    if 'id="ownerGate" class="hidden"' not in index:
        raise SystemExit("Hidden owner-gate compatibility anchor is missing from packaged index.html.")

    core = archive.read("assets/monolith_core.js").decode("utf-8", errors="replace")
    if "ensureOverlay" in core or "state.overlay" in core:
        raise SystemExit("Deprecated Monolith overlay architecture was packaged into the APK.")

    final_css = archive.read("assets/monolith_final_ui.css").decode("utf-8", errors="replace")
    if "54.5%" not in final_css or ".jane-chat-grid" not in final_css:
        raise SystemExit("Final landscape scene geometry is missing from packaged CSS.")

print(f"Packaged final UI validated inside {apk.name}: exclusive scenes, legacy overlay removal, and landscape cybernetic assets are present.")
