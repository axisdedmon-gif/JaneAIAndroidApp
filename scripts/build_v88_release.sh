#!/usr/bin/env bash
set -euo pipefail

# Generate the fully reviewed V88 build without executing it yet.
JANE_GENERATE_ONLY=1 bash scripts/build_v88_manual.sh

python3 - <<'PY'
from pathlib import Path

path = Path('/tmp/build_v88_manual.sh')
text = path.read_text(encoding='utf-8')
old = '''  keytool -printcert -jarfile app/build/outputs/apk/debug/app-debug.apk \\
    | grep -q "Owner: CN=Jane AI Assistant"'''
new = '''  SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  test -n "$SDK_ROOT"
  APKSIGNER="$(find "$SDK_ROOT/build-tools" -type f -name apksigner -perm -u+x | sort -V | tail -n 1)"
  test -x "$APKSIGNER"
  "$APKSIGNER" verify --verbose --print-certs \\
    app/build/outputs/apk/debug/app-debug.apk'''
count = text.count(old)
if count != 1:
    raise SystemExit(f'Expected one V88 certificate pipeline, found {count}')
text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')
PY

bash -n /tmp/build_v88_manual.sh
bash /tmp/build_v88_manual.sh
