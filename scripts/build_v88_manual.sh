#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
python3 -m py_compile "$ROOT/scripts/configure_v88_build.py"

# Reuse the last proven V87 reconstruction, but only generate its deterministic
# build script. V88 then adds one reviewed patch and one reviewed configurator.
JANE_GENERATE_ONLY=1 bash "$ROOT/scripts/build_v87_manual.sh"
cp /tmp/build_v87_manual.sh /tmp/build_v88_manual.sh

python3 - <<'PYV88'
from pathlib import Path
import re

path = Path('/tmp/build_v88_manual.sh')
text = path.read_text(encoding='utf-8')


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, found {count}')
    text = text.replace(old, new, 1)


v87_apply = '''  patch --batch --forward -p1 < "$ROOT/patches/v87-ai-rag-response.patch"
)
'''
v88_apply = v87_apply + '''tr -d '[:space:]' < "$ROOT/patches/v88-true-offline-ai.patch.gz.b64" | base64 -d | gzip -dc > /tmp/v88-true-offline-ai.patch
(
  cd "$SOURCE"
  patch --dry-run --batch -p1 < /tmp/v88-true-offline-ai.patch
  patch --batch --forward -p1 < /tmp/v88-true-offline-ai.patch
)
mkdir -p "$SOURCE/scripts"
cp "$ROOT/scripts/configure_v88_build.py" "$SOURCE/scripts/configure_v88_build.py"
python3 "$SOURCE/scripts/configure_v88_build.py" "$SOURCE"
'''
replace_once(v87_apply, v88_apply, 'V88 patch/configuration insertion')

version_pattern = re.compile(r'([\"\'])v87\.\{run_number\}-stable-update\1')
text, count = version_pattern.subn(
    lambda match: f'{match.group(1)}v88.{{run_number}}-stable-update{match.group(1)}',
    text,
    count=1,
)
if count != 1:
    raise SystemExit(f'V88 version assignment: expected exactly one match, found {count}')

replace_once(
    '''for token in \\
  v87-real-ai-rag-responder \\''',
    '''for token in \\
  v88-true-offline-knowledge-ai \\
  JANE_V88_TRUE_OFFLINE_AI \\
  actualOnDeviceLlm:true \\
  networkRequired:false \\
  semanticQueryExpansion:true \\
  localArchiveGrounding:true \\
  rawFragmentFallback:false \\
  exactCountValidation:true \\
  v87-real-ai-rag-responder \\''',
    'V88 browser verification tokens',
)

text = text.replace('versionName = "v87.', 'versionName = "v88.')
text = text.replace(
    'JaneAIAndroidSource_v87_real_ai_rag.zip',
    'JaneAIAndroidSource_v88_true_offline_ai.zip',
)

old_build = '''(
  cd "$SOURCE"
  gradle :app:assembleDebug
  keytool -printcert -jarfile app/build/outputs/apk/debug/app-debug.apk \\
    | grep -q "Owner: CN=Jane AI Assistant"
)'''
new_build = '''(
  cd "$SOURCE"
  MODEL_DIR="app/src/main/assets/offline_ai"
  MODEL_FILE="$MODEL_DIR/qwen2_5_0_5b_q8.task"
  mkdir -p "$MODEL_DIR"
  rm -f "$MODEL_FILE"

  # Resolve the exact MediaPipe dependency and compile every Java source before
  # spending bandwidth on the 546,660,344-byte model.
  gradle :app:compileDebugJavaWithJavac --stacktrace

  curl --fail --location --retry 4 --retry-all-errors \\
    --connect-timeout 30 --max-time 1800 \\
    "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true" \\
    --output "$MODEL_FILE"
  test "$(stat -c%s "$MODEL_FILE")" = "546660344"
  echo "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2  $MODEL_FILE" | sha256sum -c -

  gradle :app:assembleDebug --stacktrace
  keytool -printcert -jarfile app/build/outputs/apk/debug/app-debug.apk \\
    | grep -q "Owner: CN=Jane AI Assistant"

  python3 - <<'PYAPK'
from pathlib import Path
import hashlib
import zipfile

apk = Path('app/build/outputs/apk/debug/app-debug.apk')
asset = 'assets/offline_ai/qwen2_5_0_5b_q8.task'
expected_size = 546_660_344
expected_sha = 'e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2'
if not apk.is_file() or apk.stat().st_size <= expected_size:
    raise SystemExit('V88 APK is missing or too small to contain the on-device model.')
with zipfile.ZipFile(apk) as archive:
    info = archive.getinfo(asset)
    if info.file_size != expected_size:
        raise SystemExit(f'APK model size mismatch: {info.file_size}')
    if info.compress_type != zipfile.ZIP_STORED:
        raise SystemExit('APK model is compressed; MediaPipe requires a directly readable model asset.')
    digest = hashlib.sha256()
    with archive.open(info) as stream:
        while True:
            block = stream.read(1024 * 1024)
            if not block:
                break
            digest.update(block)
    if digest.hexdigest() != expected_sha:
        raise SystemExit('APK model SHA-256 mismatch.')
print('V88 APK contains the exact verified, uncompressed on-device model.')
PYAPK
)'''
replace_once(old_build, new_build, 'V88 compile, model, and APK validation block')

replace_once(
    "    -x '.gradle/*' 'app/build/*' '**/.DS_Store'",
    "    -x '.gradle/*' 'app/build/*' '**/.DS_Store' 'app/src/main/assets/offline_ai/qwen2_5_0_5b_q8.task'",
    'V88 source package model exclusion',
)

replace_once(
    '''for token in knowledge_archive catalog.json copyKnowledgeOriginal writeKnowledgeArchiveText searchKnowledgeArchives playAssetVoice; do''',
    '''for token in knowledge_archive catalog.json copyKnowledgeOriginal writeKnowledgeArchiveText searchKnowledgeArchives answerKnowledgeOffline JaneNativeOfflineKnowledgeAnswerResult OfflineKnowledgeEngine playAssetVoice; do''',
    'V88 native bridge verification',
)

verification_anchor = '''grep -q 'signingConfig signingConfigs.janeStable' "$SOURCE/app/build.gradle"'''
verification = verification_anchor + '''
grep -q 'minSdk = 24' "$SOURCE/app/build.gradle"
grep -q 'com.google.mediapipe:tasks-genai:0.10.27' "$SOURCE/app/build.gradle"
grep -Fq 'noCompress += ["task"]' "$SOURCE/app/build.gradle"
grep -q 'class OfflineKnowledgeEngine' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'LlmInference.createFromOptions' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'LlmInferenceSession.createFromOptions' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'LlmInferenceSession.LlmInferenceSessionOptions.builder' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'session.addQueryChunk(prompt)' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'session.generateResponse()' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'setMaxTopK(30)' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'sizeInTokens(prompt)' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'expandSearchQuery' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'raw PDF/OCR fragments are never presented' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'org.gradle.jvmargs=-Xmx4g' "$SOURCE/gradle.properties"
grep -q '546660344' "$SOURCE/app/src/main/assets/offline_ai/MODEL_INFO.txt"
grep -q 'e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2' "$SOURCE/app/src/main/assets/offline_ai/MODEL_INFO.txt"
! grep -qi 'placeholder' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
! grep -q 'api/chat' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
! grep -q 'HttpURLConnection' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"'''
replace_once(verification_anchor, verification, 'V88 native engine verification')

required = (
    'patches/v88-true-offline-ai.patch.gz.b64',
    'scripts/configure_v88_build.py',
    'versionName = "v88.',
    'JaneAIAndroidSource_v88_true_offline_ai.zip',
    'tasks-genai:0.10.27',
    'LlmInferenceSession.createFromOptions',
    'qwen2_5_0_5b_q8.task',
    '546660344',
    'e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2',
)
for token in required:
    if token not in text:
        raise SystemExit(f'Generated V88 build script is missing {token!r}')

path.write_text(text, encoding='utf-8')
PYV88

bash -n /tmp/build_v88_manual.sh
if [[ "${JANE_GENERATE_ONLY:-0}" != "1" ]]; then
  bash /tmp/build_v88_manual.sh
fi