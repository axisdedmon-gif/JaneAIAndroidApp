#!/usr/bin/env bash
set -euo pipefail

ROOT="$GITHUB_WORKSPACE"
ARTIFACT_ZIP="/tmp/jane-v88-source-artifact.zip"
ARTIFACT_DIR="/tmp/jane-v88-source-artifact"
FLAT="/tmp/jane-v88-flat"
SOURCE_ARCHIVE="/tmp/JaneAIAndroidSource_v88_flattened.zip"
ARTIFACT_ID="8939885334"
REPO="axisdedmon-gif/JaneAIAndroidApp"

rm -rf "$ARTIFACT_DIR" "$FLAT"
rm -f "$ARTIFACT_ZIP" "$SOURCE_ARCHIVE"
mkdir -p "$ARTIFACT_DIR" "$FLAT"

curl --fail --location --retry 4 --retry-all-errors \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "https://api.github.com/repos/$REPO/actions/artifacts/$ARTIFACT_ID/zip" \
  --output "$ARTIFACT_ZIP"

unzip -q "$ARTIFACT_ZIP" -d "$ARTIFACT_DIR"
INNER_ZIP="$(find "$ARTIFACT_DIR" -type f -name 'JaneAIAndroidSource_v88_true_offline_ai.zip' -print -quit)"
if [[ -z "$INNER_ZIP" || ! -s "$INNER_ZIP" ]]; then
  echo "Validated V88 source archive was not found in artifact $ARTIFACT_ID." >&2
  exit 1
fi
unzip -q "$INNER_ZIP" -d "$FLAT"

# Confirm this is the completed V88 source, not the older patch-chain base.
test -s "$FLAT/app/src/main/assets/index.html"
test -s "$FLAT/app/src/main/java/com/example/janeai/MainActivity.java"
test -s "$FLAT/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'versionName = "v88.' "$FLAT/app/build.gradle"
grep -q 'com.google.mediapipe:tasks-genai:0.10.27' "$FLAT/app/build.gradle"
grep -q 'LlmInferenceSession.createFromOptions' \
  "$FLAT/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'actualOnDeviceLlm:true' "$FLAT/app/src/main/assets/index.html"

# Remove generated files and the model. The direct workflow downloads and
# verifies the exact model before every release build.
rm -rf "$FLAT/.github" "$FLAT/.gradle" "$FLAT/build" "$FLAT/app/build"
rm -f \
  "$FLAT/app/jane-update-key.jks" \
  "$FLAT/app/src/main/assets/index.html.orig" \
  "$FLAT/scripts/configure_stable_signing.py.orig" \
  "$FLAT/scripts/configure_v88_build.py" \
  "$FLAT/app/src/main/assets/offline_ai/qwen2_5_0_5b_q8.task"
find "$FLAT" -name '.DS_Store' -delete

mkdir -p "$FLAT/.github/workflows" "$FLAT/scripts"
# Keep the protected workflow byte-for-byte identical to main for this commit.
git fetch origin main --depth=1
git show origin/main:.github/workflows/build-apk.yml \
  > "$FLAT/.github/workflows/build-apk.yml"

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
EOF
chmod +x "$FLAT/scripts/build_v88_direct.sh"

cat > "$FLAT/README.md" <<'EOF'
# Jane AI Android — V88 flattened source

This is the complete V88 Android source tree. It no longer reconstructs V73 or
applies the V76–V88 patch chain. The workflow builds this source directly.

The on-device Qwen2.5 0.5B task model is intentionally excluded from Git because
it exceeds GitHub's per-file limit. `scripts/build_v88_direct.sh` downloads the
exact pinned model, verifies its byte length and SHA-256 digest, packages it
uncompressed in the APK, builds with Java 21, and validates the APK.

The historical patch chain remains available in Git history only.
EOF

(
  cd "$FLAT"
  zip -q -r -9 "$SOURCE_ARCHIVE" . \
    -x '.git/*' '.gradle/*' 'build/*' 'app/build/*' \
       'app/jane-update-key.jks' 'app/src/main/assets/offline_ai/*.task' \
       '**/.DS_Store'
)
test -s "$SOURCE_ARCHIVE"

BASE_SHA="$(git rev-parse origin/main)"
cd "$ROOT"
git reset --hard "$BASE_SHA"
find "$ROOT" -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} +
cp -a "$FLAT/." "$ROOT/"

git add -A
TREE_SHA="$(git write-tree)"
COMMIT_SHA="$(printf '%s\n' 'Flatten V88 into direct Android source tree' | \
  git -c user.name='axisdedmon-gif' \
      -c user.email='axisdedmon@gmail.com' \
      commit-tree "$TREE_SHA" -p "$BASE_SHA")"
git push origin "$COMMIT_SHA:refs/heads/main"

# Make the clean source archive available to the workflow without committing it.
cp "$SOURCE_ARCHIVE" "$ROOT/JaneAIAndroidSource_v88_flattened.zip"
