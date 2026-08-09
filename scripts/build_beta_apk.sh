#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
MODEL_DIR="$ROOT/app/src/main/assets/offline_ai"
MODEL_FILE="$MODEL_DIR/qwen2_5_0_5b_q8.task"
MODEL_URL="https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true"
MODEL_BYTES="546660344"
MODEL_SHA="e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2"
EXPECTED_APP_ID="com.example.janeai"
EXPECTED_CERT_SHA256="a1e4ab83fa08381ff109f0cdfb33ade18e9300b73b98b2ee0e8e42133a7879c6"
BETA_VERSION="1.0.01"
RUN_NUMBER="${GITHUB_RUN_NUMBER:-1}"
EXPECTED_VERSION_CODE="$((200000 + RUN_NUMBER))"
EXPECTED_VERSION_NAME="Beta ${BETA_VERSION}"
DIST_DIR="$ROOT/dist"
FINAL_APK="$DIST_DIR/JaneAI-Beta-${BETA_VERSION}-code${EXPECTED_VERSION_CODE}.apk"
SIGNING_FILE="$ROOT/app/jane-update-key.jks"

mkdir -p "$MODEL_DIR" "$DIST_DIR"
rm -f "$MODEL_FILE" "$FINAL_APK" "$SIGNING_FILE"

cleanup() {
  rm -f "$SIGNING_FILE" "$MODEL_FILE"
}
trap cleanup EXIT

# Load the response surface before the landscape scene system. The marker
# keeps repeated local or Actions builds idempotent.
python3 - <<'PYINJECT'
from pathlib import Path

path = Path('app/src/main/assets/index.html')
text = path.read_text(encoding='utf-8')

# Normalize earlier response-asset labels and replace the exact owner-phrase
# patch body with a descendant-safe implementation. This lets the Beta build
# remain safe even when Actions starts from a source snapshot made before the
# blank-screen correction landed.
text = text.replace('<!-- V89_FINAL_ASSET_INJECTION -->', '<!-- JANE_RESPONSE_ASSET_INJECTION -->')
text = text.replace('v89-final-response.css', 'jane_response_surface.css')
text = text.replace('v89-final-response.js', 'jane_response_surface.js')

function_start = '  function patchOwnerPhraseUI(){'
listener_start = '\n\n  document.addEventListener("DOMContentLoaded", patchOwnerPhraseUI);'
start = text.find(function_start)
end = text.find(listener_start, start)
if start < 0 or end < 0:
    raise SystemExit('Could not locate the owner-phrase UI patch for safety normalization.')
safe_function = '''  function patchOwnerPhraseUI(){
    document.querySelectorAll("p, label").forEach(el => {
      if (/^Create a private phrase\\. The phrase is not stored directly; only a local hash is saved on this phone\\.$/i.test((el.textContent || "").trim())) {
        el.textContent = "Sensitive fields are hidden by default. Enter the owner phrase to reveal or edit them.";
      }
    });
    document.querySelectorAll("button").forEach(el => {
      if (/^Reveal\\s*\\/\\s*Edit$/i.test((el.textContent || "").trim())) {
        el.textContent = "Reveal / Edit";
      }
    });

    document.querySelectorAll("input").forEach(inp => {
      const ph = inp.getAttribute("placeholder") || "";
      if (/Enter private phrase|Repeat private phrase|New owner phrase|Confirm owner phrase/i.test(ph)) {
        inp.setAttribute("placeholder", "Owner phrase");
        inp.type = "password";
      }
    });

    const labels = Array.from(document.querySelectorAll("label, p, span"));
    labels.forEach(el => {
      el.childNodes.forEach(node => {
        if (node.nodeType !== Node.TEXT_NODE) return;
        node.nodeValue = node.nodeValue.replace(/New owner phrase/gi, "Owner phrase").replace(/Confirm owner phrase/gi, "");
      });
    });
  }'''
text = text[:start] + safe_function + text[end:]

marker = '<!-- JANE_COMMAND_DECK_ASSET_INJECTION -->'
block = '''\n<!-- JANE_RESPONSE_ASSET_INJECTION -->\n<link rel="stylesheet" href="jane_response_surface.css">\n<script src="jane_response_surface.js"></script>\n\n<!-- JANE_COMMAND_DECK_ASSET_INJECTION -->\n<link rel="stylesheet" href="jane_command_deck.css">\n<script src="jane_command_deck.js"></script>\n'''
if marker not in text:
    if '</body>' not in text:
        raise SystemExit('Could not locate </body> for Jane scene assets.')
    text = text.replace('</body>', block + '</body>', 1)
path.write_text(text, encoding='utf-8')
PYINJECT

# Static checks run before the large bundled-model download.
grep -q 'On-device knowledge responder' app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java
grep -q "Treat it as knowledge you already understand, not as a book you are reading aloud" app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java
grep -q "I don't know that from the knowledge you've given me yet" app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java
grep -q 'coerceNumberedAnswer' app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java
grep -q 'Complete response-screen scrolling' app/src/main/assets/jane_response_surface.css
grep -q 'JANE_RESPONSE_SURFACE' app/src/main/assets/jane_response_surface.js
grep -q 'JANE_RESPONSE_ASSET_INJECTION' app/src/main/assets/index.html
grep -q 'jane_response_surface.css' app/src/main/assets/index.html
grep -q 'jane_response_surface.js' app/src/main/assets/index.html
grep -q 'JANE_COMMAND_DECK_ASSET_INJECTION' app/src/main/assets/index.html
grep -q 'jane_command_deck.css' app/src/main/assets/index.html
grep -q 'jane_command_deck.js' app/src/main/assets/index.html
grep -Eq 'querySelectorAll\("p,[[:space:]]*label"\)' app/src/main/assets/index.html
! grep -Eq 'querySelectorAll\("p,[[:space:]]*div,[[:space:]]*span,[[:space:]]*label"\)' app/src/main/assets/index.html
! grep -Eq '#ownerGate|#launchJaneButton|\.owner-card|\.owner-crest|\.owner-title|\.owner-sub|\.owner-pill' app/src/main/assets/jane_command_deck.css
grep -q 'notifyInterfaceReady' app/src/main/java/com/example/janeai/MainActivity.java
grep -q 'applyImmersiveMode' app/src/main/java/com/example/janeai/MainActivity.java
grep -q 'WindowInsetsController' app/src/main/java/com/example/janeai/MainActivity.java
grep -q 'screenOrientation="sensorLandscape"' app/src/main/AndroidManifest.xml
test -s app/src/main/assets/model-viewer-umd.min.js
grep -q 'data:model/gltf-binary;base64,' app/src/main/assets/index.html
grep -q 'knowledge_archive' app/src/main/java/com/example/janeai/MainActivity.java
grep -q 'catalog.json' app/src/main/java/com/example/janeai/MainActivity.java
grep -q 'copyKnowledgeOriginal' app/src/main/java/com/example/janeai/MainActivity.java
grep -q 'playAssetVoice' app/src/main/java/com/example/janeai/MainActivity.java
for sound in cursor cancel popup-open popup-close select-primary select-confirm swipe error; do
  test -s "app/src/main/assets/ui_sfx/${sound}.ogg"
done
node --check app/src/main/assets/jane_response_surface.js
node --check app/src/main/assets/jane_command_deck.js

# Compile Java before the large model download so source errors fail quickly.
gradle :app:compileDebugJavaWithJavac --stacktrace

curl --fail --location --retry 4 --retry-all-errors \
  --connect-timeout 30 --max-time 1800 \
  "$MODEL_URL" --output "$MODEL_FILE"
test "$(stat -c%s "$MODEL_FILE")" = "$MODEL_BYTES"
echo "$MODEL_SHA  $MODEL_FILE" | sha256sum -c -

export JANE_VERSION_CODE="$EXPECTED_VERSION_CODE"
export JANE_VERSION_NAME="$EXPECTED_VERSION_NAME"
export JANE_KEYSTORE_PASSWORD="JaneUpdate2026"
export JANE_KEY_ALIAS="janeupdate"

curl -fsSL \
  https://raw.githubusercontent.com/axisdedmon-gif/JaneAIAndroidApp/fbad016ea0d1ceaee341d39d6969484568f8e1dd/signing/jane-update-key.b64 \
  | tr -d '\r\n' \
  | base64 -d > "$SIGNING_FILE"

keytool -list \
  -keystore "$SIGNING_FILE" \
  -storepass "$JANE_KEYSTORE_PASSWORD" \
  -alias "$JANE_KEY_ALIAS" >/dev/null

python3 scripts/configure_stable_signing.py

grep -q "applicationId = \"$EXPECTED_APP_ID\"" app/build.gradle
grep -q "versionCode = $EXPECTED_VERSION_CODE" app/build.gradle
grep -q "versionName = \"$EXPECTED_VERSION_NAME\"" app/build.gradle
grep -q 'signingConfig signingConfigs.janeStable' app/build.gradle

gradle :app:assembleDebug --stacktrace

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
test -n "$SDK_ROOT"
APKSIGNER="$(find "$SDK_ROOT/build-tools" -type f -name apksigner -perm -u+x | sort -V | tail -n 1)"
test -x "$APKSIGNER"
"$APKSIGNER" verify --verbose --print-certs \
  app/build/outputs/apk/debug/app-debug.apk \
  > /tmp/jane-beta-apksigner.txt
cat /tmp/jane-beta-apksigner.txt
grep -Eq "(Signer #1 certificate DN:|V2 Signer: certificate DN:) .*CN=Jane AI Assistant" /tmp/jane-beta-apksigner.txt
grep -Fqi "$EXPECTED_CERT_SHA256" /tmp/jane-beta-apksigner.txt

python3 - <<'PYAPK'
from pathlib import Path
import hashlib
import re
import zipfile

apk = Path('app/build/outputs/apk/debug/app-debug.apk')
gradle_text = Path('app/build.gradle').read_text(encoding='utf-8')
expected_size = 546_660_344
expected_sha = 'e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2'
expected_app_id = 'com.example.janeai'
expected_version_name = 'Beta 1.0.01'
asset = 'assets/offline_ai/qwen2_5_0_5b_q8.task'
required_assets = {
    'assets/model-viewer-umd.min.js',
    'assets/jane_response_surface.css',
    'assets/jane_response_surface.js',
    'assets/jane_command_deck.css',
    'assets/jane_command_deck.js',
    'assets/ui_sfx/cursor.ogg',
    'assets/ui_sfx/cancel.ogg',
    'assets/ui_sfx/popup-open.ogg',
    'assets/ui_sfx/popup-close.ogg',
    'assets/ui_sfx/select-primary.ogg',
    'assets/ui_sfx/select-confirm.ogg',
    'assets/ui_sfx/swipe.ogg',
    'assets/ui_sfx/error.ogg',
}

app_id = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle_text)
version_code = re.search(r'versionCode\s*=\s*(\d+)', gradle_text)
version_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_text)
if not app_id or app_id.group(1) != expected_app_id:
    raise SystemExit('Package/applicationId drift would break updating.')
if not version_code or int(version_code.group(1)) < 200001:
    raise SystemExit('VersionCode is missing or invalid for Beta APK updates.')
if not version_name or version_name.group(1) != expected_version_name:
    raise SystemExit('VersionName is not Beta 1.0.01.')

if not apk.is_file() or apk.stat().st_size <= expected_size:
    raise SystemExit('Beta APK is missing or too small to contain the offline AI model.')
with zipfile.ZipFile(apk) as archive:
    names = set(archive.namelist())
    missing = required_assets - names
    if missing:
        raise SystemExit(f'Beta APK missing required local assets: {sorted(missing)}')
    info = archive.getinfo(asset)
    if info.file_size != expected_size:
        raise SystemExit(f'APK model size mismatch: {info.file_size}')
    if info.compress_type != zipfile.ZIP_STORED:
        raise SystemExit('APK model is compressed instead of directly readable.')
    digest = hashlib.sha256()
    with archive.open(info) as stream:
        while True:
            block = stream.read(1024 * 1024)
            if not block:
                break
            digest.update(block)
    if digest.hexdigest() != expected_sha:
        raise SystemExit('APK model SHA-256 mismatch.')
print('Beta 1.0.01 APK updateability, offline model, UI scenes, and SFX validated.')
PYAPK

cp app/build/outputs/apk/debug/app-debug.apk "$FINAL_APK"
test -s "$FINAL_APK"
echo "Installable Beta APK: $FINAL_APK"
