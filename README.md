# Monolith AI Android

Monolith AI is the Android application, launcher, assistant-service, widget, archive, local model, voice-workspace, and RPG platform. Jane is the established female AI character hosted by Monolith AI.

## Current architecture

- `MonolithBootstrapActivity` is the deterministic native BIOS/startup boundary.
- `HouseDedmonAccessActivity` is the native owner-access scene. It renders the House Dedmon crest from the bundled asset, removes only edge-connected black background pixels at runtime, and owns the real Android ENTER control.
- The public `ai.monolith.app.MonolithActivity` component is a compatibility alias routed through `MonolithEntryActivity`.
- `MonolithEntryActivity` sends normal owner launches through House Dedmon Access, while assistant/search/voice entry can route directly to Core.
- `MonolithCoreActivity` is the concrete `:core` process activity and inherits the proven Monolith WebView/RAG/GLB implementation from `MonolithActivity`.
- `MonolithSafeBaseActivity` is a native `:safe` recovery console with WebView disabled.
- `MonolithVoiceInteractionService` and `MonolithVoiceSessionService` provide Android digital-assistant integration and explicit `AssistStructure` capture.
- `MonolithAccessibilityService` provides opt-in, explicit window-context snapshots and does not continuously archive screen content.
- Archives preserve originals locally, extract PDF/document/image text, and on Android 13+ can transcribe audio/video through the installed on-device speech recognizer.
- `VoiceModelStore` records Piper-compatible WAV datasets plus `metadata.csv`, imports/exports dataset ZIPs, and preserves model targets under `Context.getExternalFilesDir("monolith_voice")`.
- `PiperTtsEngine` uses sherpa-onnx to run activated converted Piper models locally.
- The Monolith Model screen manages character selection, GLB rendering controls, and progression state.
- The RPG screen provides a Starfinder 1e mechanics workspace, editable local character matrix, dice tools, and DM encounter controls.

## Build and versioning

GitHub Actions calculates Monolith SemVer from Conventional Commit subjects, injects the generated Android `versionName` / `versionCode`, validates native startup boundaries and exclusive scene architecture, compiles and signs the APK, then inspects the finished APK before publishing the artifact.

Structural generation commits use `feat(mono):` or `feat(ui):`. Runtime/UI repairs use `fix(mono):` or `fix(ui):`. CI-only and implementation staging commits use `chore(...)` so they do not accidentally advance a generation or patch counter.

Large build payloads such as the pinned offline language model and sherpa-onnx Android runtime are verified and supplied during CI instead of being stored as ordinary source files.
