# Monolith AI Android

This repository contains the Android source tree for Monolith AI. Jane is the established female AI character hosted by the application; Monolith AI is the Android application, launcher, assistant-service, widget, archive, model, voice-workspace, and RPG platform identity.

## Current architecture

- `ai.monolith.app.MonolithActivity` is the application shell and launcher.
- The proven pre-Monolith core remains as an internal compatibility layer during the package migration.
- `MonolithVoiceInteractionService` and `MonolithVoiceSessionService` provide Android digital-assistant integration and explicit AssistStructure capture.
- `MonolithAccessibilityService` provides opt-in, explicit window-context snapshots and does not continuously archive screen content.
- Archives preserve originals locally, extract PDF/document/image text, and on Android 13+ can transcribe audio/video through the installed on-device speech recognizer.
- `VoiceModelStore` records Piper-compatible WAV datasets and metadata, and preserves imported model targets under `Context.getExternalFilesDir("monolith_voice")`.
- The Monolith Model screen manages character selection, GLB rendering controls, and progression state.
- The RPG screen provides a Starfinder 1e mechanics workspace, editable local character matrix, holographic dice, roll automation hooks, and DM encounter tools.

## Build

The large pinned Qwen2.5 on-device model is intentionally excluded from Git because it exceeds GitHub's per-file limit. The manual GitHub workflow calls `scripts/build_monolith_apk.sh`, which validates source/metadata first, downloads and verifies the pinned model, preserves the established signing lineage, builds the APK with Java 21, and emits `MonolithAI-Beta-*.apk`.

The GitHub Actions workflow remains `workflow_dispatch` only. Source commits do not automatically spend Actions minutes.
