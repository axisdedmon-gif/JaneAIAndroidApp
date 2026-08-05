#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
MODEL_DIR="$ROOT/app/src/main/assets/offline_ai"
MODEL_FILE="$MODEL_DIR/qwen2_5_0_5b_q8.task"
MODEL_URL="https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true"
MODEL_BYTES="546660344"
MODEL_SHA="e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2"
SOURCE_ZIP="$ROOT/JaneAIAndroidSource_v88_flattened.zip"

mkdir -p "$MODEL_DIR"
rm -f "$MODEL_FILE" "$SOURCE_ZIP"

# Compile the flattened source first. No historical patches are applied.
gradle :app:compileDebugJavaWithJavac --stacktrace

curl --fail --location --retry 4 --retry-all-errors \
  --connect-timeout 30 --max-time 1800 \
  "$MODEL_URL" --output "$MODEL_FILE"
test "$(stat -c%s "$MODEL_FILE")" = "$MODEL_BYTES"
echo "$MODEL_SHA  $MODEL_FILE" | sha256sum -c -

export JANE_VERSION_CODE="${GITHUB_RUN_NUMBER:-1}"
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
grep -q 'versionName = "v88.' app/build.gradle
grep -q 'signingConfig signingConfigs.janeStable' app/build.gradle

gradle :app:assembleDebug --stacktrace

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
test -n "$SDK_ROOT"
APKSIGNER="$(find "$SDK_ROOT/build-tools" -type f -name apksigner -perm -u+x | sort -V | tail -n 1)"
test -x "$APKSIGNER"
"$APKSIGNER" verify --verbose --print-certs \
  app/build/outputs/apk/debug/app-debug.apk

python3 - <<'PYAPK'
from pathlib import Path
import hashlib
import zipfile

apk = Path('app/build/outputs/apk/debug/app-debug.apk')
asset = 'assets/offline_ai/qwen2_5_0_5b_q8.task'
expected_size = 546_660_344
expected_sha = 'e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2'
if not apk.is_file() or apk.stat().st_size <= expected_size:
    raise SystemExit('V88 APK is missing or too small to contain the model.')
with zipfile.ZipFile(apk) as archive:
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
print('V88 APK contains the exact verified uncompressed offline model.')
PYAPK

rm -f app/jane-update-key.jks "$MODEL_FILE"
zip -q -r -9 "$SOURCE_ZIP" . \
  -x '.git/*' '.gradle/*' 'build/*' 'app/build/*' \
     'app/jane-update-key.jks' 'app/src/main/assets/offline_ai/*.task' \
     'JaneAIAndroidSource_v88_flattened.zip' '**/.DS_Store'
test -s "$SOURCE_ZIP"
