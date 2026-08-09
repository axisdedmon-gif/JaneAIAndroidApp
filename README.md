# Monolith AI Android

Current release line: **Beta 2.0.01**.

This repository contains the Android source tree for Monolith AI. Jane is the established female AI character hosted by the application; Monolith AI is the Android application, launcher, assistant-service, widget, archive, model, voice-workspace, and RPG platform identity.

## Current architecture

- `ai.monolith.app.MonolithActivity` is the application shell and launcher.
- The proven pre-Monolith core remains as an internal compatibility layer during the package migration.
- `MonolithVoiceInteractionService` and `MonolithVoiceSessionService` provide Android digital-assistant integration and explicit `AssistStructure` capture.
- `MonolithAccessibilityService` provides opt-in, explicit window-context snapshots and does not continuously archive screen content.
- Archives preserve originals locally, extract PDF/document/image text, and on Android 13+ can transcribe audio/video through the installed on-device speech recognizer.
- `VoiceModelStore` records Piper-compatible WAV datasets plus `metadata.csv`, imports/exports dataset ZIPs, and preserves model targets under `Context.getExternalFilesDir("monolith_voice")`.
- `PiperTtsEngine` uses sherpa-onnx v1.13.4 to run an activated converted Piper model fully locally. Systemic speech uses that model before the inherited hosted voice path.
- `scripts/convert_piper_for_android.py` converts the standard Piper `.onnx + .onnx.json` export into the sherpa-compatible `.onnx + tokens.txt` pair used by the APK.
- The Monolith Model screen manages character selection, GLB rendering controls, and progression state.
- The RPG screen provides a Starfinder 1e mechanics workspace, editable local character matrix, holographic dice, roll automation hooks, and DM encounter tools.

## Build

The pinned Qwen2.5 on-device model and sherpa-onnx Android AAR are intentionally excluded from Git because they are large build payloads. The manual GitHub workflow calls `scripts/build_monolith_apk.sh`, which validates source/metadata first, downloads and verifies the pinned sherpa runtime, packages `espeak-ng-data`, compiles Java before transferring the large LLM, then downloads/verifies the LLM, signs, assembles, and validates the finished APK.

The artifact for this release line is `MonolithAI-Beta-2.0.01-apk`, containing an APK named `MonolithAI-Beta-2.0.01-code<versionCode>.apk`.

The GitHub Actions workflow remains `workflow_dispatch` only. Source commits do not automatically spend Actions minutes.
