#!/usr/bin/env bash
set -euo pipefail

ROOT="$GITHUB_WORKSPACE"
SOURCE="$ROOT/source"
FLAT="/tmp/jane-v88-flat"
SOURCE_ARCHIVE="/tmp/JaneAIAndroidSource_v88_flattened.zip"

if [[ ! -s "$SOURCE/app/src/main/assets/index.html" ]]; then
  echo "The completed V88 source tree is missing." >&2
  exit 1
fi
if [[ ! -s "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java" ]]; then
  echo "The V88 offline AI engine is missing." >&2
  exit 1
fi

grep -q 'versionName = "v88.' "$SOURCE/app/build.gradle"
grep -q 'com.google.mediapipe:tasks-genai:0.10.27' "$SOURCE/app/build.gradle"
grep -q 'LlmInferenceSession.createFromOptions' \
  "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'actualOnDeviceLlm:true' "$SOURCE/app/src/main/assets/index.html"

rm -rf "$FLAT"
mkdir -p "$FLAT"
cp -a "$SOURCE/." "$FLAT/"

# Remove generated files and the large model. The direct build downloads the
# exact verified model once and packages it without compression.
rm -rf "$FLAT/.github" "$FLAT/.gradle" "$FLAT/build" "$FLAT/app/build"
rm -f \
  "$FLAT/app/jane-update-key.jks" \
  "$FLAT/app/src/main/assets/index.html.orig" \
  "$FLAT/scripts/configure_stable_signing.py.orig" \
  "$FLAT/scripts/configure_v88_build.py" \
  "$FLAT/app/src/main/assets/offline_ai/qwen2_5_0_5b_q8.task"
find "$FLAT" -name '.DS_Store' -delete

mkdir -p "$FLAT/.github/workflows" "$FLAT/scripts"
cat > "$FLAT/.gitignore" <<'EOF'
.gradle/
build/
app/build/
app/jane-update-key.jks
app/src/main/assets/offline_ai/*.task
*.iml
.idea/
.DS_Store
EOF

cat > "$FLAT/scripts/build_v88_direct.sh" <<'EOF'
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

# Compile the real source before downloading the large model.
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
        while block := stream.read(1024 * 1024):
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
EOF
chmod +x "$FLAT/scripts/build_v88_direct.sh"

cat > "$FLAT/.github/workflows/build-apk.yml" <<'EOF'
name: Build Jane Android APK

on:
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: jane-v88-direct-build
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-24.04
    timeout-minutes: 45

    steps:
      - name: Check out flattened V88 source
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - name: Verify Java 21 toolchain
        run: |
          JAVA_SPEC="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.specification.version = //p')"
          test "$JAVA_SPEC" = "21"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build flattened V88 directly
        run: bash scripts/build_v88_direct.sh

      - name: Upload signed V88 APK
        uses: actions/upload-artifact@v4
        with:
          name: JaneAI-V88-true-offline-ai-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          compression-level: 0
          retention-days: 14

      - name: Upload flattened V88 source
        uses: actions/upload-artifact@v4
        with:
          name: JaneAIAndroidSource-v88-flattened
          path: JaneAIAndroidSource_v88_flattened.zip
          retention-days: 14
EOF

cat > "$FLAT/README.md" <<'EOF'
# Jane AI Android — V88 flattened source

This branch is the complete V88 Android source tree. It no longer rebuilds V73
or applies the V76–V88 patch chain. GitHub Actions builds this source directly.

The on-device Qwen2.5 0.5B task model is intentionally not stored in Git because
it exceeds GitHub's per-file limit. `scripts/build_v88_direct.sh` downloads the
exact pinned model, verifies its byte length and SHA-256 digest, packages it
uncompressed inside the APK, builds with Java 21, and verifies the completed APK.

The historical patch chain remains available in Git history, but it is not used
by the current source or workflow.
EOF

# Produce a clean source archive for this run without placing it in Git.
rm -f "$SOURCE_ARCHIVE"
(
  cd "$FLAT"
  zip -q -r -9 "$SOURCE_ARCHIVE" . \
    -x '.git/*' '.gradle/*' 'build/*' 'app/build/*' \
       'app/jane-update-key.jks' 'app/src/main/assets/offline_ai/*.task' \
       '**/.DS_Store'
)
test -s "$SOURCE_ARCHIVE"

# Replace the patch-chain repository tree with the completed V88 source tree.
find "$ROOT" -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} +
cp -a "$FLAT/." "$ROOT/"

cd "$ROOT"
git config user.name "axisdedmon-gif"
git config user.email "axisdedmon@gmail.com"
git add -A
git commit -m "Flatten V88 into direct Android source tree"
git push origin HEAD:main

# Keep the source archive available to the still-running transition workflow.
cp "$SOURCE_ARCHIVE" "$ROOT/JaneAIAndroidSource_v88_flattened.zip"
