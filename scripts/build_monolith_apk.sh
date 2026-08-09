#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
MODEL_DIR="$ROOT/app/src/main/assets/offline_ai"
MODEL_FILE="$MODEL_DIR/qwen2_5_0_5b_q8.task"
MODEL_URL="https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true"
MODEL_BYTES="546660344"
MODEL_SHA="e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2"

SHERPA_VERSION="1.13.4"
SHERPA_DIR="$ROOT/app/libs"
SHERPA_AAR="$SHERPA_DIR/sherpa-onnx-${SHERPA_VERSION}.aar"
SHERPA_AAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/sherpa-onnx-${SHERPA_VERSION}.aar"
SHERPA_AAR_BYTES="48847529"
SHERPA_AAR_SHA="03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780"
ESPEAK_ARCHIVE="$ROOT/.monolith-espeak-ng-data.tar.bz2"
ESPEAK_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2"
TTS_ASSET_ROOT="$ROOT/app/src/main/assets/monolith_tts"
ESPEAK_ASSET_DIR="$TTS_ASSET_ROOT/espeak-ng-data"

EXPECTED_APP_ID="ai.monolith.app"
EXPECTED_CERT_SHA256="a1e4ab83fa08381ff109f0cdfb33ade18e9300b73b98b2ee0e8e42133a7879c6"
BETA_VERSION="2.0.02"
EXPECTED_VERSION_CODE="200002"
EXPECTED_VERSION_NAME="Beta ${BETA_VERSION}"
DIST_DIR="$ROOT/dist"
FINAL_APK="$DIST_DIR/MonolithAI-Beta-${BETA_VERSION}.apk"
SIGNING_FILE="$ROOT/app/monolith-update-key.jks"
SIGNING_COMMIT="fbad016ea0d1ceaee341d39d6969484568f8e1dd"
SIGNING_REPO_PATH="signing/jane-update-key.b64"

mkdir -p "$MODEL_DIR" "$DIST_DIR" "$SHERPA_DIR" "$TTS_ASSET_ROOT"
rm -f "$MODEL_FILE" "$FINAL_APK" "$SIGNING_FILE" "$SHERPA_AAR" "$ESPEAK_ARCHIVE"
rm -rf "$ESPEAK_ASSET_DIR"
cleanup(){
  rm -f "$SIGNING_FILE" "$MODEL_FILE" "$SHERPA_AAR" "$ESPEAK_ARCHIVE"
  rm -rf "$ESPEAK_ASSET_DIR"
}
trap cleanup EXIT

python3 scripts/apply_monolith_refactor.py

# Fast source validation before any large model transfer.
grep -q 'applicationId = "ai.monolith.app"' app/build.gradle
grep -q 'namespace = "ai.monolith.app"' app/build.gradle
grep -q 'versionName = "Beta 2.0.02"' app/build.gradle
grep -q 'versionCode = 200002' app/build.gradle
grep -q 'org.jetbrains.kotlin.android' app/build.gradle
grep -q 'kotlinx-coroutines-android:1.11.0' app/build.gradle
grep -q 'sherpa-onnx-1.13.4.aar' app/build.gradle
grep -q '<string name="app_name">Monolith AI</string>' app/src/main/res/values/strings.xml
grep -q 'android.service.voice.VoiceInteractionService' app/src/main/AndroidManifest.xml
grep -q 'BIND_VOICE_INTERACTION' app/src/main/AndroidManifest.xml
grep -q 'MonolithAccessibilityService' app/src/main/AndroidManifest.xml
grep -q 'MonolithSearchWidgetProvider' app/src/main/AndroidManifest.xml
grep -q 'MonolithCrashGuard.beginLaunch' app/src/main/java/ai/monolith/app/MonolithActivity.java
grep -q 'MonolithCoroutineScope' app/src/main/java/ai/monolith/app/MonolithActivity.java
grep -q 'deferred-until-voice-module' app/src/main/java/ai/monolith/app/MonolithActivity.java
grep -q 'requestAssistantRestrictedPermissions' app/src/main/java/ai/monolith/app/PermissionCoordinator.java
grep -q 'RoleManager.ROLE_ASSISTANT' app/src/main/java/ai/monolith/app/PermissionCoordinator.java
if grep -q 'PermissionCoordinator.requestRuntimePermissions(this);' app/src/main/java/ai/monolith/app/MonolithActivity.java; then
  echo 'Unsafe automatic permission request remains in MonolithActivity.onCreate().' >&2
  exit 1
fi
grep -q 'getExternalFilesDir("monolith_voice")' app/src/main/java/ai/monolith/app/VoiceModelStore.java
grep -q 'exportDataset' app/src/main/java/ai/monolith/app/VoiceModelStore.java
grep -q 'PiperTtsEngine.speakAsync' app/src/main/java/com/example/janeai/MainActivity.java
grep -q 'LocalMediaTranscriber.transcribeBlocking' app/src/main/java/com/example/janeai/MainActivity.java
grep -q 'CharacterRegistry.activeName' app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java
grep -q '1 to 3 sentences' app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java
grep -q 'Monolith Model' app/src/main/assets/monolith_core.js
grep -q 'Voice Module' app/src/main/assets/monolith_core.js
grep -q 'STARFINDER 1E TABLETOP ENGINE' app/src/main/assets/monolith_core.js
grep -q 'monolith_voice_runtime_patch.js' app/src/main/java/ai/monolith/app/MonolithActivity.java
node --check app/src/main/assets/jane_response_surface.js
node --check app/src/main/assets/jane_command_deck.js
node --check app/src/main/assets/jane_qol_runtime.js
node --check app/src/main/assets/monolith_core.js
node --check app/src/main/assets/monolith_voice_runtime_patch.js
python3 -m py_compile scripts/apply_monolith_refactor.py scripts/configure_monolith_signing.py scripts/convert_piper_for_android.py
python3 - <<'PYXML'
import xml.etree.ElementTree as ET
for p in [
    'app/src/main/AndroidManifest.xml',
    'app/src/main/res/xml/monolith_voice_interaction.xml',
    'app/src/main/res/xml/monolith_accessibility_service.xml',
    'app/src/main/res/xml/monolith_search_widget_info.xml',
    'app/src/main/res/layout/monolith_search_widget.xml',
]:
    ET.parse(p)
print('Monolith XML metadata parsed successfully.')
PYXML

# Pin the Android-local TTS runtime without committing a large third-party AAR.
curl --fail --location --retry 4 --retry-all-errors --connect-timeout 30 --max-time 900 "$SHERPA_AAR_URL" --output "$SHERPA_AAR"
test "$(stat -c%s "$SHERPA_AAR")" = "$SHERPA_AAR_BYTES"
echo "$SHERPA_AAR_SHA  $SHERPA_AAR" | sha256sum -c -
test -s "$SHERPA_AAR"

# Piper phonemization data is shared by converted Piper voices.
curl --fail --location --retry 4 --retry-all-errors --connect-timeout 30 --max-time 600 "$ESPEAK_URL" --output "$ESPEAK_ARCHIVE"
tar -xjf "$ESPEAK_ARCHIVE" -C "$TTS_ASSET_ROOT"
test -d "$ESPEAK_ASSET_DIR"
test "$(find "$ESPEAK_ASSET_DIR" -type f | wc -l)" -gt 20

# Compile Kotlin and Java before the 500+ MB LLM transfer so source/API mistakes fail cheaply.
gradle :app:compileDebugKotlin :app:compileDebugJavaWithJavac --stacktrace

export MONOLITH_VERSION_CODE="$EXPECTED_VERSION_CODE"
export MONOLITH_VERSION_NAME="$EXPECTED_VERSION_NAME"
export MONOLITH_KEYSTORE_PASSWORD="JaneUpdate2026"
export MONOLITH_KEY_ALIAS="janeupdate"

# Recover the established update key from immutable repository history through Git.
git fetch --no-tags --depth=1 origin "$SIGNING_COMMIT"
git cat-file -e "$SIGNING_COMMIT:$SIGNING_REPO_PATH"
git show "$SIGNING_COMMIT:$SIGNING_REPO_PATH" | tr -d '\r\n' | base64 -d > "$SIGNING_FILE"
test "$(stat -c%s "$SIGNING_FILE")" -gt 1000
keytool -list -v -keystore "$SIGNING_FILE" -storepass "$MONOLITH_KEYSTORE_PASSWORD" -alias "$MONOLITH_KEY_ALIAS" \
  | tr '[:upper:]' '[:lower:]' | tr -d ':' > /tmp/monolith-keytool.txt
grep -Fq "$EXPECTED_CERT_SHA256" /tmp/monolith-keytool.txt

python3 scripts/configure_monolith_signing.py
grep -q "applicationId = \"$EXPECTED_APP_ID\"" app/build.gradle
grep -q "versionCode = $EXPECTED_VERSION_CODE" app/build.gradle
grep -q "versionName = \"$EXPECTED_VERSION_NAME\"" app/build.gradle
grep -q 'signingConfig signingConfigs.monolithStable' app/build.gradle

# Only after source and signing preflight pass do we fetch the bundled offline LLM.
curl --fail --location --retry 4 --retry-all-errors --connect-timeout 30 --max-time 1800 "$MODEL_URL" --output "$MODEL_FILE"
test "$(stat -c%s "$MODEL_FILE")" = "$MODEL_BYTES"
echo "$MODEL_SHA  $MODEL_FILE" | sha256sum -c -

gradle :app:assembleDebug --stacktrace

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
test -n "$SDK_ROOT"
APKSIGNER="$(find "$SDK_ROOT/build-tools" -type f -name apksigner -perm -u+x | sort -V | tail -n 1)"
test -x "$APKSIGNER"
"$APKSIGNER" verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk > /tmp/monolith-apksigner.txt
cat /tmp/monolith-apksigner.txt
grep -Fqi "$EXPECTED_CERT_SHA256" /tmp/monolith-apksigner.txt

python3 - <<'PYAPK'
from pathlib import Path
import hashlib, re, zipfile
apk = Path('app/build/outputs/apk/debug/app-debug.apk')
gradle_text = Path('app/build.gradle').read_text(encoding='utf-8')
expected_size = 546_660_344
expected_sha = 'e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2'
asset = 'assets/offline_ai/qwen2_5_0_5b_q8.task'
required = {
 'assets/model-viewer-umd.min.js','assets/jane_response_surface.css','assets/jane_response_surface.js',
 'assets/jane_command_deck.css','assets/jane_command_deck.js','assets/jane_qol_hud.css','assets/jane_qol_runtime.js',
 'assets/monolith_core.css','assets/monolith_core.js','assets/monolith_voice_runtime_patch.js',
 'assets/TEXTYMCSPEECHY_LICENSE.txt'
}
app_id = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle_text)
version_code = re.search(r'versionCode\s*=\s*(\d+)', gradle_text)
version_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_text)
if not app_id or app_id.group(1) != 'ai.monolith.app': raise SystemExit('Monolith applicationId validation failed.')
if not version_code or version_code.group(1) != '200002': raise SystemExit('Monolith Beta 2.0.02 versionCode validation failed.')
if not version_name or version_name.group(1) != 'Beta 2.0.02': raise SystemExit('Monolith Beta 2.0.02 version validation failed.')
if not apk.is_file() or apk.stat().st_size <= expected_size: raise SystemExit('APK missing or too small for bundled offline model.')
with zipfile.ZipFile(apk) as z:
    names=set(z.namelist()); missing=required-names
    if missing: raise SystemExit(f'APK missing local assets: {sorted(missing)}')
    if not any(n.startswith('assets/monolith_tts/espeak-ng-data/') for n in names):
        raise SystemExit('APK is missing Piper espeak-ng runtime data.')
    if not any('sherpa' in n.lower() and n.endswith('.so') for n in names):
        raise SystemExit('APK is missing sherpa-onnx native runtime libraries.')
    info=z.getinfo(asset)
    if info.file_size != expected_size or info.compress_type != zipfile.ZIP_STORED: raise SystemExit('Offline model packaging mismatch.')
    h=hashlib.sha256()
    with z.open(info) as f:
        for block in iter(lambda:f.read(1024*1024),b''): h.update(block)
    if h.hexdigest()!=expected_sha: raise SystemExit('Offline model SHA-256 mismatch.')
print('Monolith AI Beta 2.0.02 startup hardening, identity, assistant services, local RAG, Piper runtime, modules, and APK assets validated.')
PYAPK

cp app/build/outputs/apk/debug/app-debug.apk "$FINAL_APK"
test -s "$FINAL_APK"
echo "Installable Monolith AI Beta 2.0.02 APK: $FINAL_APK"
