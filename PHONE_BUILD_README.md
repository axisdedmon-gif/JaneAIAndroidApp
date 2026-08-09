# Monolith AI APK, phone-only build guide

Monolith AI is built from this repository through the manual GitHub Actions workflow. Jane is the established female character inside Monolith AI rather than the application name.

## Phone workflow

1. Open the repository's **Actions** tab.
2. Choose **Monolith AI APK — Beta Builds**.
3. Use **Run workflow** only when you intentionally want a new APK build.
4. Download the `MonolithAI-Beta-1.0.01-apk` artifact after the run succeeds.
5. Install the generated APK.

The workflow is deliberately `workflow_dispatch` only. Normal source commits do not start builds.

## Offline systems

The APK contains the pinned local language model downloaded and verified during the build. Archives, character state, RPG state, and the Voice Module are local-first. Piper-compatible voice datasets and imported model targets are kept in the app's protected external-files area so APK updates do not replace those files.

The legacy hosted speech endpoint remains available to the inherited female-character voice path during the migration. The new Voice Module does not upload training datasets and does not depend on that endpoint for dataset capture/import/export.

## Android identity

The application ID is `ai.monolith.app`. This is a different Android package identity from earlier builds that used the legacy package, so Android does not treat the first Monolith package install as an in-place update of that old package.
