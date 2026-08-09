#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
MODEL_DIR="$ROOT/app/src/main/assets/offline_ai"
MODEL_FILE="$MODEL_DIR/qwen2_5_0_5b_q8.task"
MODEL_URL="https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true"
MODEL_BYTES="546660344"
MODEL_SHA="e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2"
EXPECTED_APP_ID="ai.monolith.app"
EXPECTED_CERT_SHA256="a1e4ab83fa08381ff109f0cdfb33ade18e9300b73b98b2ee0e8e42133a7879c6"
BETA_VERSION="1.0.01"
RUN_NUMBER="${GITHUB_RUN_NUMBER:-1}"
EXPECTED_VERSION_CODE="$((200000 + RUN_NUMBER))"
EXPECTED_VERSION_NAME="Beta ${BETA_VERSION}"
DIST_DIR="$ROOT/dist"
FINAL_APK="$DIST_DIR/MonolithAI-Beta-${BETA_VERSION}-code${EXPECTED_VERSION_CODE}.apk"
SIGNING_FILE="$ROOT/app/monolith-update-key.jks"

mkdir -p "$MODEL_DIR" "$DIST_DIR"
rm -f "$MODEL_FILE" "$FINAL_APK" "$SIGNING_FILE"
cleanup(){ rm -f "$SIGNING_FILE" "$MODEL_FILE"; }
trap cleanup EXIT

python3 scripts/apply_monolith_refactor.py

# Fast static validation before downloading the large local inference model.
grep -q 'applicationId = "ai.monolith.app"' app/build.gradle
grep -q 'namespace = "ai.monolith.app"' app/build.gradle
grep -q '<string name="app_name">Monolith AI</string>' app/src/main/res/values/strings.xml
grep -q 'android.service.voice.VoiceInteractionService' app/src/main/AndroidManifest.xml
grep -q 'BIND_VOICE_INTERACTION' app/src/main/AndroidManifest.xml
grep -q 'MonolithAccessibilityService' app/src/main/AndroidManifest.xml
grep -q 'MonolithSearchWidgetProvider' app/src/main/AndroidManifest.xml
grep -q 'getExternalFilesDir("monolith_voice")' app/src/main/java/ai/monolith/app/VoiceModelStore.java
grep -q 'CharacterRegistry.activeName' app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java
grep -q '1 to 3 sentences' app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java
grep -q 'Monolith Model' app/src/main/assets/monolith_core.js
grep -q 'Voice Module' app/src/main/assets/monolith_core.js
grep -q 'STARFINDER 1E TABLETOP ENGINE' app/src/main/assets/monolith_core.js
node --check app/src/main/assets/jane_response_surface.js
node --check app/src/main/assets/jane_command_deck.js
node --check app/src/main/assets/jane_qol_runtime.js
node --check app/src/main/assets/monolith_core.js
python3 - <<'PYXML'
from pathlib import Path
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

# Compile native source before the 500+ MB model transfer.
gradle :app:compileDebugJavaWithJavac --stacktrace

curl --fail --location --retry 4 --retry-all-errors --connect-timeout 30 --max-time 1800 "$MODEL_URL" --output "$MODEL_FILE"
test "$(stat -c%s "$MODEL_FILE")" = "$MODEL_BYTES"
echo "$MODEL_SHA  $MODEL_FILE" | sha256sum -c -

export MONOLITH_VERSION_CODE="$EXPECTED_VERSION_CODE"
export MONOLITH_VERSION_NAME="$EXPECTED_VERSION_NAME"
export MONOLITH_KEYSTORE_PASSWORD="JaneUpdate2026"
export MONOLITH_KEY_ALIAS="janeupdate"

# Preserve the existing signing lineage. The historical key object remains at its original immutable repository path.
curl -fsSL \
  https://raw.githubusercontent.com/axisdedmon-gif/JaneAIAndroidApp/fbad016ea0d1ceaee341d39d6969484568f8e1dd/signing/jane-update-key.b64 \
  | tr -d '\r\n' | base64 -d > "$SIGNING_FILE"

keytool -list -keystore "$SIGNING_FILE" -storepass "$MONOLITH_KEYSTORE_PASSWORD" -alias "$MONOLITH_KEY_ALIAS" >/dev/null
python3 scripts/configure_monolith_signing.py

grep -q "applicationId = \"$EXPECTED_APP_ID\"" app/build.gradle
grep -q "versionCode = $EXPECTED_VERSION_CODE" app/build.gradle
grep -q "versionName = \"$EXPECTED_VERSION_NAME\"" app/build.gradle
grep -q 'signingConfig signingConfigs.monolithStable' app/build.gradle

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
 'assets/monolith_core.css','assets/monolith_core.js','assets/TEXTYMCSPEECHY_LICENSE.txt'
}
app_id = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle_text)
if not app_id or app_id.group(1) != 'ai.monolith.app': raise SystemExit('Monolith applicationId validation failed.')
if not apk.is_file() or apk.stat().st_size <= expected_size: raise SystemExit('APK missing or too small for bundled offline model.')
with zipfile.ZipFile(apk) as z:
    names=set(z.namelist()); missing=required-names
    if missing: raise SystemExit(f'APK missing local assets: {sorted(missing)}')
    info=z.getinfo(asset)
    if info.file_size != expected_size or info.compress_type != zipfile.ZIP_STORED: raise SystemExit('Offline model packaging mismatch.')
    h=hashlib.sha256()
    with z.open(info) as f:
        for block in iter(lambda:f.read(1024*1024),b''): h.update(block)
    if h.hexdigest()!=expected_sha: raise SystemExit('Offline model SHA-256 mismatch.')
print('Monolith AI APK identity, services, local model and module assets validated.')
PYAPK

cp app/build/outputs/apk/debug/app-debug.apk "$FINAL_APK"
test -s "$FINAL_APK"
echo "Installable Monolith AI APK: $FINAL_APK"
