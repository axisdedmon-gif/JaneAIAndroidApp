#!/usr/bin/env bash
set -euo pipefail

# Generate the already proven V87 build script without executing its build.
JANE_GENERATE_ONLY=1 bash scripts/build_v87_manual.sh
cp /tmp/build_v87_manual.sh /tmp/build_v88_manual.sh

python3 - <<'PYV88'
from pathlib import Path

path = Path('/tmp/build_v88_manual.sh')
text = path.read_text(encoding='utf-8')

def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one match, found {count}')
    text = text.replace(old, new, 1)

replace_once(
    '''  patch --batch --forward -p1 < "$ROOT/patches/v87-ai-rag-response.patch"
)

python3 - <<'PY' ''',
    '''  patch --batch --forward -p1 < "$ROOT/patches/v87-ai-rag-response.patch"
)
tr -d '[:space:]' < "$ROOT/patches/v88-true-offline-ai.patch.gz.b64" | base64 -d | gzip -dc > /tmp/v88-true-offline-ai.patch
(
  cd "$SOURCE"
  patch --dry-run --batch -p1 < /tmp/v88-true-offline-ai.patch
  patch --batch --forward -p1 < /tmp/v88-true-offline-ai.patch
)

python3 - <<'PY'
from pathlib import Path

build = Path("source/app/build.gradle")
source = build.read_text(encoding="utf-8")
if "minSdk = 23" not in source:
    raise SystemExit("V88 expected minSdk 23 before upgrading it")
source = source.replace("minSdk = 23", "minSdk = 24", 1)
if 'aaptOptions {' not in source:
    anchor = "    buildTypes {"
    if anchor not in source:
        raise SystemExit("V88 could not place the no-compress model rule")
    source = source.replace(anchor, '    aaptOptions {\\n        noCompress "task"\\n    }\\n\\n' + anchor, 1)
if 'com.google.mediapipe:tasks-genai:' not in source:
    anchor = "dependencies {\\n"
    if anchor not in source:
        raise SystemExit("V88 could not place the on-device inference dependency")
    source = source.replace(anchor, anchor + '    implementation("com.google.mediapipe:tasks-genai:0.10.27")\\n', 1)
build.write_text(source, encoding="utf-8")

props = Path("source/gradle.properties")
properties = props.read_text(encoding="utf-8") if props.exists() else ""
if "org.gradle.jvmargs=" not in properties:
    properties += "\\norg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8\\n"
props.write_text(properties, encoding="utf-8")
PY

python3 - <<'PY' ''',
    'V88 patch and build configuration insertion',
)

replace_once(
    "'v87.{run_number}-stable-update'",
    "'v88.{run_number}-stable-update'",
    'V88 version assignment',
)

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
    'V88 verification tokens',
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

  # Compile all Java first. A zero-byte placeholder avoids spending time and
  # bandwidth on the large model if the source cannot compile.
  : > "$MODEL_FILE"
  gradle :app:compileDebugJavaWithJavac
  rm -f "$MODEL_FILE"

  curl --fail --location --retry 4 --retry-all-errors \\
    --connect-timeout 30 --max-time 1800 \\
    "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true" \\
    --output "$MODEL_FILE"
  test "$(stat -c%s "$MODEL_FILE")" = "546660344"
  echo "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2  $MODEL_FILE" | sha256sum -c -

  gradle :app:assembleDebug
  keytool -printcert -jarfile app/build/outputs/apk/debug/app-debug.apk \\
    | grep -q "Owner: CN=Jane AI Assistant"
)'''
replace_once(old_build, new_build, 'V88 compile/model/build block')

replace_once(
    "    -x '.gradle/*' 'app/build/*' '**/.DS_Store'",
    "    -x '.gradle/*' 'app/build/*' '**/.DS_Store' 'app/src/main/assets/offline_ai/qwen2_5_0_5b_q8.task'",
    'V88 source model exclusion',
)

text = text.replace(
    '''for token in knowledge_archive catalog.json copyKnowledgeOriginal writeKnowledgeArchiveText searchKnowledgeArchives playAssetVoice; do''',
    '''for token in knowledge_archive catalog.json copyKnowledgeOriginal writeKnowledgeArchiveText searchKnowledgeArchives answerKnowledgeOffline JaneNativeOfflineKnowledgeAnswerResult playAssetVoice; do''',
)

verification_anchor = '''grep -q 'signingConfig signingConfigs.janeStable' "$SOURCE/app/build.gradle"'''
verification = verification_anchor + '''
grep -q 'com.google.mediapipe:tasks-genai:0.10.27' "$SOURCE/app/build.gradle"
grep -q 'noCompress "task"' "$SOURCE/app/build.gradle"
grep -q 'class OfflineKnowledgeEngine' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'LlmInference.createFromOptions' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'generateResponse(prompt)' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'expandSearchQuery' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'raw PDF/OCR fragments are never presented' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
grep -q 'org.gradle.jvmargs=-Xmx4g' "$SOURCE/gradle.properties"
! grep -q 'api/chat' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
! grep -q 'HttpURLConnection' "$SOURCE/app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"'''
replace_once(verification_anchor, verification, 'V88 native engine verification')

if 'patches/v88-true-offline-ai.patch.gz.b64' not in text:
    raise SystemExit('V88 patch path missing after transformation')
if 'versionName = "v88.' not in text:
    raise SystemExit('V88 version verification missing after transformation')
if 'JaneAIAndroidSource_v88_true_offline_ai.zip' not in text:
    raise SystemExit('V88 source package name missing after transformation')
if 'qwen2_5_0_5b_q8.task' not in text:
    raise SystemExit('V88 model download is missing')

path.write_text(text, encoding='utf-8')
PYV88

bash -n /tmp/build_v88_manual.sh
if [[ "${JANE_GENERATE_ONLY:-0}" != "1" ]]; then
  bash /tmp/build_v88_manual.sh
fi
