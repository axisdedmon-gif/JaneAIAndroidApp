#!/usr/bin/env bash
set -euo pipefail

cp scripts/build_v86_manual.sh /tmp/build_v87_manual.sh

python3 - <<'PY'
from pathlib import Path

path = Path('/tmp/build_v87_manual.sh')
text = path.read_text(encoding='utf-8')

def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one match, found {count}')
    text = text.replace(old, new, 1)

replace_once(
    "apply_patch 1 /tmp/v86.patch\n\npython3 - <<'PY'",
    '''apply_patch 1 /tmp/v86.patch
(
  cd "$SOURCE"
  patch --dry-run --batch -p1 < "$ROOT/patches/v87-ai-rag-response.patch"
  patch --batch --forward -p1 < "$ROOT/patches/v87-ai-rag-response.patch"
)

python3 - <<'PY' ''',
    'V87 patch insertion',
)

replace_once(
    '"v86.{run_number}-stable-update"',
    '"v87.{run_number}-stable-update"',
    'V87 version assignment',
)

replace_once(
    '''for token in \\
  v86-reliability-recovery-list-answer \\''',
    '''for token in \\
  v87-real-ai-rag-responder \\
  JANE_V87_REAL_AI_RAG \\
  semanticQueryExpansion:true \\
  multiQueryRetrieval:true \\
  aiOnlySynthesis:true \\
  noFragmentStitching:true \\
  exactCountAnswers:true \\
  v86-reliability-recovery-list-answer \\''',
    'V87 verification tokens',
)

text = text.replace('versionName = "v86.', 'versionName = "v87.')
text = text.replace(
    'JaneAIAndroidSource_v86_reliable_list_answer_scroll.zip',
    'JaneAIAndroidSource_v87_real_ai_rag.zip',
)

if 'patches/v87-ai-rag-response.patch' not in text:
    raise SystemExit('V87 patch path missing after transformation')
if 'versionName = "v87.' not in text:
    raise SystemExit('V87 version verification missing after transformation')
if 'JaneAIAndroidSource_v87_real_ai_rag.zip' not in text:
    raise SystemExit('V87 source package name missing after transformation')

path.write_text(text, encoding='utf-8')
PY

bash -n /tmp/build_v87_manual.sh
if [[ "${JANE_GENERATE_ONLY:-0}" != "1" ]]; then
  bash /tmp/build_v87_manual.sh
fi
