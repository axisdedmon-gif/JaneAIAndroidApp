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
new = '''  keytool -printcert -jarfile app/build/outputs/apk/debug/app-debug.apk \\
    > /tmp/jane-v88-certificate.txt 2>&1
  grep -Fq "Owner: CN=Jane AI Assistant" /tmp/jane-v88-certificate.txt
  grep -Fq "SHA256: A1:E4:AB:83:FA:08:38:1F:F1:09:F0:CD:FB:33:AD:E1:8E:93:00:B7:3B:98:B2:EE:0E:8E:42:13:3A:78:79:C6" \\
    /tmp/jane-v88-certificate.txt'''
count = text.count(old)
if count != 1:
    raise SystemExit(f'Expected one V88 certificate pipeline, found {count}')
text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')
PY

bash -n /tmp/build_v88_manual.sh
bash /tmp/build_v88_manual.sh
