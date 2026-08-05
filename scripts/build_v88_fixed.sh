#!/usr/bin/env bash
set -euo pipefail

TMP_SCRIPT="/tmp/build_v88_fixed.$$.sh"
cp scripts/build_v88_manual.sh "$TMP_SCRIPT"

python3 - "$TMP_SCRIPT" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    text = text.replace(old, new, 1)

replace_once(
    '''replace_once(
    "'v87.{run_number}-stable-update'",
    "'v88.{run_number}-stable-update'",
    'V88 version assignment',
)''',
    '''replace_once(
    '\"v87.{run_number}-stable-update\"',
    '\"v88.{run_number}-stable-update\"',
    'V88 version assignment',
)''',
    "V88 version wrapper",
)

replace_once(
    '''if 'aaptOptions {' not in source:
    anchor = "    buildTypes {"
    if anchor not in source:
        raise SystemExit("V88 could not place the no-compress model rule")
    source = source.replace(anchor, '    aaptOptions {\\n        noCompress "task"\\n    }\\n\\n' + anchor, 1)''',
    '''if 'aaptOptions {' not in source:
    anchor = "android {\\n"
    if anchor not in source:
        raise SystemExit("V88 could not find the Android configuration block")
    source = source.replace(anchor, anchor + '    aaptOptions {\\n        noCompress "task"\\n    }\\n\\n', 1)''',
    "V88 no-compress wrapper",
)

text = text.replace(
    'com.google.mediapipe:tasks-genai:0.10.27',
    'com.google.mediapipe:tasks-genai:0.10.25',
)

path.write_text(text, encoding="utf-8")
PY

bash -n "$TMP_SCRIPT"
bash "$TMP_SCRIPT"
rm -f "$TMP_SCRIPT"
