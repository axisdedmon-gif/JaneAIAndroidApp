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
RUN_NUMBER="${GITHUB_RUN_NUMBER:-1}"
EXPECTED_VERSION_CODE="$((100000 + RUN_NUMBER))"
EXPECTED_VERSION_NAME="v89.${RUN_NUMBER}-stable-update"
DIST_DIR="$ROOT/dist"
FINAL_APK="$DIST_DIR/JaneAI-V89-final-v${RUN_NUMBER}-code${EXPECTED_VERSION_CODE}.apk"

mkdir -p "$MODEL_DIR" "$DIST_DIR"
rm -f "$MODEL_FILE" "$FINAL_APK"

# Load the final response CSS/JS after every historical inline patch so the
# final response-screen behavior wins without rewriting the large index file.
python3 - <<'PYINJECT'
from pathlib import Path

path = Path('app/src/main/assets/index.html')
text = path.read_text(encoding='utf-8')
marker = '<!-- V89_FINAL_ASSET_INJECTION -->'
block = '''\n<!-- V89_FINAL_ASSET_INJECTION -->\n<link rel="stylesheet" href="v89-final-response.css">\n<script src="v89-final-response.js"></script>\n'''
if marker not in text:
    if '</body>' not in text:
        raise SystemExit('Could not locate </body> for V89 final asset injection.')
    text = text.replace('</body>', block + '</body>', 1)
    path.write_text(text, encoding='utf-8')
PYINJECT

# Static scope checks before spending time downloading the bundled model.
grep -q 'V89 final offline knowledge responder' app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java
grep -q "Treat it as knowledge you already understand, not as a book you are reading aloud" app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java
grep -q "I don't know that from the knowledge you've given me yet" app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java
grep -q 'coerceNumberedAnswer' app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java
grep -q 'V89 final response-screen scrolling' app/src/main/assets/v89-final-response.css
grep -q 'JANE_V89_FINAL_RESPONSE' app/src/main/assets/v89-final-response.js
grep -q 'V89_FINAL_ASSET_INJECTION' app/src/main/assets/index.html
grep -q 'v89-final-response.css' app/src/main/assets/index.html
grep -q 'v89-final-response.js' app/src/main/assets/index.html
test -s app/src/main/assets/model-viewer-umd.min.js
grep -q 'data:model/gltf-binary;base64,' app/src/main/assets/index.html
grep -q 'knowledge_archive' app/src/main/java/com/example/janeai/MainActivity.java
grep -q 'catalog.json' app/src/main/java/com/example/janeai/MainActivity.java
grep -q 'copyKnowledgeOriginal' app/src/main/java/com/example/janeai/MainActivity.java
grep -q 'playAssetVoice' app/src/main/java/com/example/janeai/MainActivity.java

# Compile Java before the large model download. This catches final source errors
# without consuming an unnecessary full APK build.
gradle :app:compileDebugJavaWithJavac --stacktrace

curl --fail --location --retry 4 --retry-all-errors \
  --connect-timeout 30 --max-time 1800 \
  "$MODEL_URL" --output "$MODEL_FILE"
test "$(stat -c%s "$MODEL_FILE")" = "$MODEL_BYTES"
echo "$MODEL_SHA  $MODEL_FILE" | sha256sum -c -

export JANE_VERSION_CODE="$RUN_NUMBER"
export JANE_KEYSTORE_PASSWORD="JaneUpdate2026"
export JANE_KEY_ALIAS="janeupdate"

curl -fsSL \
  https://raw.githubusercontent.com/axisdedmon-gif/JaneAIAndroidApp/fbad016ea0d1ceaee341d39d6969484568f8e1dd/signing/jane-update-key.b64 \
  | tr -d '\r\n' \
  | base64 -d > "$ROOT/app/jane-update-key.jks"

keytool -list \
  -keystore "$ROOT/app/jane-update-key.jks" \
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
  > /tmp/jane-v89-apksigner.txt
cat /tmp/jane-v89-apksigner.txt
grep -Eq "(Signer #1 certificate DN:|V2 Signer: certificate DN:) .*CN=Jane AI Assistant" /tmp/jane-v89-apksigner.txt
grep -Fqi "$EXPECTED_CERT_SHA256" /tmp/jane-v89-apksigner.txt

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
asset = 'assets/offline_ai/qwen2_5_0_5b_q8.task'
required_assets = {
    'assets/model-viewer-umd.min.js',
    'assets/v89-final-response.css',
    'assets/v89-final-response.js',
}

app_id = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle_text)
version_code = re.search(r'versionCode\s*=\s*(\d+)', gradle_text)
version_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_text)
if not app_id or app_id.group(1) != expected_app_id:
    raise SystemExit('Package/applicationId drift would break updating.')
if not version_code or int(version_code.group(1)) < 100001:
    raise SystemExit('VersionCode is missing or invalid for APK updates.')
if not version_name or not version_name.group(1).startswith('v89.'):
    raise SystemExit('VersionName is missing or not V89.')

if not apk.is_file() or apk.stat().st_size <= expected_size:
    raise SystemExit('V89 APK is missing or too small to contain the offline AI model.')
with zipfile.ZipFile(apk) as archive:
    names = set(archive.namelist())
    missing = required_assets - names
    if missing:
        raise SystemExit(f'V89 APK missing required local assets: {sorted(missing)}')
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
print('V89 final APK updateability, local AI model, and response assets validated.')
PYAPK

cp app/build/outputs/apk/debug/app-debug.apk "$FINAL_APK"
test -s "$FINAL_APK"
rm -f app/jane-update-key.jks "$MODEL_FILE"
echo "Final installable APK: $FINAL_APK"
