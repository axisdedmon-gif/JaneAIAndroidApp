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
    "assets/index.html",
    "assets/jane_command_deck.js",
    "assets/monolith_scene_runtime.js",
    "assets/monolith_core.js",
    "assets/monolith_voice_runtime_patch.js",
    "assets/monolith_final_ui.css",
    "assets/monolith_final_ui.js",
    "assets/monolith_landscape_gen2.css",
    "assets/monolith_hardware_gen3.css",
}
gate_tokens = (
    "HouseDedmonAccessActivity",
    "House Dedmon Access",
    "HOUSE DEDMON ACCESS",
    "ownerGate",
    "launchJaneButton",
    "monolith-launch",
    "monolithEnterButton",
    "dedmon-launch",
    "startupCurtain",
    "VERIFYING VISIBILITY",
    "MOUNTING SCENE",
)

with zipfile.ZipFile(apk) as archive:
    names = set(archive.namelist())
    missing = required_assets - names
    if missing:
        raise SystemExit(f"Packaged APK is missing final UI assets: {sorted(missing)}")
    for asset in required_assets:
        if archive.getinfo(asset).file_size < 200:
            raise SystemExit(f"Packaged final UI asset is unexpectedly small: {asset}")

    index = archive.read("assets/index.html").decode("utf-8", errors="replace")
    if '"72 BPM"' in index or '"98.6°F"' in index:
        raise SystemExit("Fake biometric values were packaged into the APK.")

    command_deck = archive.read("assets/jane_command_deck.js").decode("utf-8", errors="replace")
    for token in (
        'currentScene: "command"',
        'document.body.classList.add("jane-deck-launched", "monolith-owner-authorized")',
        'notifyInterfaceReady?.("command-ready")',
    ):
        if token not in command_deck:
            raise SystemExit(f"Packaged command deck is missing direct-start token: {token}")

    core = archive.read("assets/monolith_core.js").decode("utf-8", errors="replace")
    if "ensureOverlay" in core or "state.overlay" in core:
        raise SystemExit("Deprecated Monolith overlay architecture was packaged into the APK.")

    runtime = archive.read("assets/monolith_scene_runtime.js").decode("utf-8", errors="replace")
    for token in (
        "MONOLITH-SCENE-5",
        "__monolithExclusiveRouter",
        "activateInitialCommandScene()",
        'dataset.monolithSceneMounted = "command"',
        'dataset.monolithLoadState = "loaded"',
    ):
        if token not in runtime:
            raise SystemExit(f"Packaged scene runtime is missing direct-command token: {token}")

    final_js = archive.read("assets/monolith_final_ui.js").decode("utf-8", errors="replace")
    if "MONOLITH-FINAL-UI-4" not in final_js or "forceCommandScene" not in final_js:
        raise SystemExit("Packaged final UI is missing the direct Command Chamber fallback.")

    landscape = archive.read("assets/monolith_landscape_gen2.css").decode("utf-8", errors="replace")
    for token in ('#home[data-jane-scene="command"]', "55%", '#vn[data-jane-scene="chat"]'):
        if token not in landscape:
            raise SystemExit(f"Generation-2 landscape geometry is missing from packaged CSS: {token}")

    gate_surfaces = {
        "index.html": index,
        "jane_command_deck.js": command_deck,
        "monolith_scene_runtime.js": runtime,
        "monolith_final_ui.js": final_js,
        "monolith_landscape_gen2.css": landscape,
        "monolith_hardware_gen3.css": archive.read("assets/monolith_hardware_gen3.css").decode("utf-8", errors="replace"),
    }
    for surface_name, text in gate_surfaces.items():
        for forbidden in gate_tokens:
            if forbidden in text:
                raise SystemExit(f"Removed verification-gate token was packaged in {surface_name}: {forbidden}")

    dex_names = sorted(name for name in names if name.endswith(".dex"))
    if not dex_names:
        raise SystemExit("Packaged APK contains no DEX bytecode.")
    dex_blob = b"\n".join(archive.read(name) for name in dex_names)
    for token in (
        b"monolith_scene_runtime.js",
        b"Command Chamber did not become visibly painted",
        b"elementFromPoint",
        b"hitInCommand",
        b"MonolithCoreActivity",
        b"MonolithSafeBaseActivity",
        b"NATIVE RECOVERY CONSOLE // NO WEBVIEW",
        b"disabled-safe-native",
        b"clearStartupState",
    ):
        if token not in dex_blob:
            raise SystemExit(f"Compiled APK is missing runtime-host recovery token: {token.decode('utf-8')}")
    for forbidden in (
        b"HouseDedmonAccessActivity",
        b"HOUSE DEDMON ACCESS // VERIFYING VISIBILITY",
        b"startupCurtain",
        b"hitInLaunch",
    ):
        if forbidden in dex_blob:
            raise SystemExit(f"Removed verification-gate bytecode token survived: {forbidden.decode('utf-8')}")

print(
    f"Packaged Monolith runtime validated inside {apk.name}: scene generation 5 opens the Command "
    "Chamber directly, Android requires its visible foreground paint, and Safe Base remains native/WebView-disabled."
)
