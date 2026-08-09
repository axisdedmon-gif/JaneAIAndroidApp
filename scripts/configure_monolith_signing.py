from pathlib import Path
import os
import re

path = Path("app/build.gradle")
text = path.read_text(encoding="utf-8")
version_code = int(os.environ.get("MONOLITH_VERSION_CODE", "200002"))
version_name = os.environ.get("MONOLITH_VERSION_NAME", "Beta 2.0.02").strip()
password = os.environ.get("MONOLITH_KEYSTORE_PASSWORD", "")
alias = os.environ.get("MONOLITH_KEY_ALIAS", "janeupdate").strip()

if not password:
    raise SystemExit("MONOLITH_KEYSTORE_PASSWORD is missing.")
if not version_name:
    raise SystemExit("MONOLITH_VERSION_NAME is missing.")

text = re.sub(r'applicationId\s*=\s*"[^"]+"', 'applicationId = "ai.monolith.app"', text, count=1)
text = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {version_code}', text, count=1)
text = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{version_name}"', text, count=1)
text = re.sub(r'storePassword\s+"[^"]*"', f'storePassword "{password}"', text, count=1)
text = re.sub(r'keyAlias\s+"[^"]*"', f'keyAlias "{alias}"', text, count=1)
text = re.sub(r'keyPassword\s+"[^"]*"', f'keyPassword "{password}"', text, count=1)
path.write_text(text, encoding="utf-8")
print(f"Configured Monolith AI {version_name} ({version_code}) signing inputs.")
