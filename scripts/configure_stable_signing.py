from pathlib import Path
import os
import re


gradle = Path("app/build.gradle")
text = gradle.read_text(encoding="utf-8", errors="ignore")

version_code = int(os.environ.get("JANE_VERSION_CODE", "200001"))
version_name = os.environ.get("JANE_VERSION_NAME", "Beta 1.0.01").strip()
keystore_password = os.environ.get("JANE_KEYSTORE_PASSWORD", "")
key_alias = os.environ.get("JANE_KEY_ALIAS", "janeupdate")

if not keystore_password:
    raise SystemExit("JANE_KEYSTORE_PASSWORD is missing.")
if not version_name:
    raise SystemExit("JANE_VERSION_NAME is missing.")

text = re.sub(
    r'applicationId\s*=\s*"[^"]+"',
    'applicationId = "com.example.janeai"',
    text,
    count=1,
)
text = re.sub(
    r'versionCode\s*=\s*\d+',
    f'versionCode = {version_code}',
    text,
    count=1,
)
text = re.sub(
    r'versionName\s*=\s*"[^"]+"',
    f'versionName = "{version_name}"',
    text,
    count=1,
)

if "janeStable" not in text:
    signing = f'''android {{
    signingConfigs {{
        janeStable {{
            storeFile file("jane-update-key.jks")
            storePassword "{keystore_password}"
            keyAlias "{key_alias}"
            keyPassword "{keystore_password}"
        }}
    }}
'''
    text = text.replace("android {\n", signing, 1)

    marker = "\n}\n\n\n\ndependencies"
    replacement = '''

    buildTypes {
        debug {
            signingConfig signingConfigs.janeStable
        }
    }
}


dependencies'''
    if marker not in text:
        raise SystemExit("Could not locate Android block ending for signing configuration.")
    text = text.replace(marker, replacement, 1)

gradle.write_text(text, encoding="utf-8")
