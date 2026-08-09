# Monolith AI APK, phone-only build guide

Current release: **Beta 2.0.01**.

Monolith AI is built from this repository through the manual GitHub Actions workflow. Jane is the established female character inside Monolith AI rather than the application name.

## Phone workflow

1. Remove the legacy installed app when you are ready to move to the new Monolith package identity.
2. Open the repository's **Actions** tab.
3. Choose **Monolith AI APK — Beta Builds**.
4. Use **Run workflow** only when you intentionally want a new APK build.
5. Download the `MonolithAI-Beta-2.0.01-apk` artifact after the run succeeds.
6. Install the generated `MonolithAI-Beta-2.0.01-code*.apk`.

The workflow is deliberately `workflow_dispatch` only. Normal source commits do not start builds.

## Offline systems

The APK contains the pinned local language model downloaded and verified during the build. Archives, character state, RPG state, and the Voice Module are local-first. Piper-compatible voice datasets and imported model targets are kept in the app's protected external-files area so later Monolith APK updates do not replace those files.

The Voice Module records and imports the standard Piper dataset shape (`metadata.csv` plus `wav/`) and can export it as a ZIP through Android's document picker. Standard Piper model exports (`.onnx + .onnx.json`) remain preserved; run `scripts/convert_piper_for_android.py` in the offline training workspace to produce the converted ONNX metadata plus `tokens.txt` required by the Android runtime.

When a runnable local model is activated, systemic character speech uses that local Piper/sherpa-onnx model first. If no runnable local voice is active, the inherited hosted speech path remains available during the migration.

## Android identity

The application ID is `ai.monolith.app`. This is a different Android package identity from earlier builds that used the legacy package, so the first Monolith install begins the new app line rather than updating the legacy package in place.
