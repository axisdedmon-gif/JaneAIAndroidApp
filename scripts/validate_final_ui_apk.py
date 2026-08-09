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
    "assets/monolith_landscape_gen2.css",
    "assets/house_dedmon_crest.webp",
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
        raise SystemExit("Legacy House Dedmon overlay was packaged in index.html.")
    if '"72 BPM"' in index or '"98.6°F"' in index:
        raise SystemExit("Fake biometric values were packaged into the APK.")
    if 'id="ownerGate" class="hidden"' not in index:
        raise SystemExit("Hidden owner-gate compatibility anchor is missing from packaged index.html.")

    core = archive.read("assets/monolith_core.js").decode("utf-8", errors="replace")
    if "ensureOverlay" in core or "state.overlay" in core:
        raise SystemExit("Deprecated Monolith overlay architecture was packaged into the APK.")

    runtime = archive.read("assets/monolith_scene_runtime.js").decode("utf-8", errors="replace")
    for token in (
        "MONOLITH-SCENE-3",
        "monolith-launch",
        "House Dedmon Access",
        "house_dedmon_crest.webp",
        "monolithEnterButton",
        "__monolithExclusiveRouter",
    ):
        if token not in runtime:
            raise SystemExit(f"Packaged scene runtime is missing dedicated launch token: {token}")

    landscape = archive.read("assets/monolith_landscape_gen2.css").decode("utf-8", errors="replace")
    for token in (
        ".monolith-launch-scene",
        ".dedmon-launch-shell",
        '#home[data-jane-scene="command"]',
        "55%",
        '#vn[data-jane-scene="chat"]',
    ):
        if token not in landscape:
            raise SystemExit(f"Generation-2 landscape geometry is missing from packaged CSS: {token}")

    # Validate compiled Java/Kotlin string pools too. This closes the gap that allowed a scene
    # runtime to be packaged but never referenced by the Android host in the previous build.
    dex_names = sorted(name for name in names if name.endswith(".dex"))
    if not dex_names:
        raise SystemExit("Packaged APK contains no DEX bytecode.")
    dex_blob = b"\n".join(archive.read(name) for name in dex_names)
    for token in (
        b"monolith_scene_runtime.js",
        b"exclusive scene did not mount",
        b"MonolithSafeBaseActivity",
        b"NATIVE RECOVERY CONSOLE // NO WEBVIEW",
        b"disabled-safe-native",
        b"clearStartupState",
    ):
        if token not in dex_blob:
            raise SystemExit(f"Compiled APK is missing runtime-host recovery token: {token.decode('utf-8')}")

print(
    f"Packaged Monolith runtime validated inside {apk.name}: Android scene-loader references are "
    "compiled, House Dedmon owns a dedicated landscape scene, and Safe Base is native/WebView-disabled."
)
