#!/usr/bin/env bash
set -euo pipefail

TMP_SCRIPT="/tmp/build_v88_fixed.$$.sh"
cp scripts/build_v88_manual.sh "$TMP_SCRIPT"

python3 - "$TMP_SCRIPT" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
old = '''replace_once(
    "'v87.{run_number}-stable-update'",
    "'v88.{run_number}-stable-update'",
    'V88 version assignment',
)'''
new = '''replace_once(
    '\"v87.{run_number}-stable-update\"',
    '\"v88.{run_number}-stable-update\"',
    'V88 version assignment',
)'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"V88 wrapper expected one legacy version block, found {count}")
text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
PY

bash -n "$TMP_SCRIPT"
bash "$TMP_SCRIPT"
rm -f "$TMP_SCRIPT"
