#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
SOURCE="$ROOT/source"
rm -rf "$SOURCE" "$ROOT/unpacked"
mkdir -p "$ROOT/unpacked"

if [[ -f JaneAIAndroidSource_v73_launch_restored_hide_vitals_only.zip ]]; then
  unzip -q JaneAIAndroidSource_v73_launch_restored_hide_vitals_only.zip -d "$SOURCE"
elif [[ -f JaneV73LaunchRestoredHideVitalsOnlyPackage.zip ]]; then
  unzip -q JaneV73LaunchRestoredHideVitalsOnlyPackage.zip -d "$ROOT/unpacked"
  unzip -q "$ROOT/unpacked/JaneAIAndroidSource_v73_launch_restored_hide_vitals_only.zip" -d "$SOURCE"
else
  echo "Jane V73 base source package is missing." >&2
  exit 1
fi

test -s "$SOURCE/app/src/main/assets/index.html"
test -s "$SOURCE/app/src/main/java/com/example/janeai/MainActivity.java"
mkdir -p "$SOURCE/scripts"
cp scripts/configure_stable_signing.py "$SOURCE/scripts/configure_stable_signing.py"

python3 - <<'PY'
from pathlib import Path
import base64
import gzip
import re

def decode(text: str, label: str) -> bytes:
    clean = re.sub(r"[^A-Za-z0-9+/=]", "", text)
    if "=" in clean:
        clean = clean.split("=", 1)[0]
    clean += "=" * ((4 - len(clean) % 4) % 4)
    try:
        return gzip.decompress(base64.b64decode(clean))
    except Exception as exc:
        raise SystemExit(f"{label} patch decode failed: {exc}") from exc

payloads = {
    "v76": Path("patches/v76.patch.gz.b64").read_text(),
    "v78": Path("patches/v78.patch.gz.b64").read_text(),
    "v79": Path("patches/v79.patch.gz.b64").read_text(),
    "v80": Path("patches/v80.patch.gz.b64").read_text(),
    "v81": Path("patches/v81.patch.gz.b64").read_text(),
    "v82": Path("patches/v82.patch.gz.b64").read_text(),
    "v83": Path("patches/v83.patch.gz.b64").read_text(),
    "v84": "".join(p.read_text() for p in sorted(Path("patches").glob("v84.part*.b64"))),
    "v85": Path("patches/v85.patch.gz.b64").read_text(),
    "v86": "".join(p.read_text() for p in sorted(Path("patches").glob("v86fix.part*.b64"))),
}
for version, encoded in payloads.items():
    patch = decode(encoded, version).decode()
    if not patch.startswith("diff "):
        raise SystemExit(f"{version} is not a unified patch")
    if version == "v86":
        marker = "\ndiff -ruN v86old/scripts/configure_stable_signing.py"
        patch = patch.split(marker, 1)[0].rstrip() + "\n"
        if "app/src/main/assets/index.html" not in patch:
            raise SystemExit("V86 response patch is missing")
        if "scripts/configure_stable_signing.py" in patch:
            raise SystemExit("V86 signing hunk was not removed")
    Path(f"/tmp/{version}.patch").write_text(patch)
PY

apply_patch() {
  local level="$1"
  local file="$2"
  (
    cd "$SOURCE"
    patch --batch --forward "-p${level}" < "$file"
  )
}

apply_patch 2 /tmp/v76.patch
(
  cd "$SOURCE"
  patch --batch --forward -p1 < "$ROOT/patches/v77-settings-menu-knowledge.patch"
)
apply_patch 1 /tmp/v78.patch
apply_patch 1 /tmp/v79.patch
apply_patch 1 /tmp/v80.patch
apply_patch 1 /tmp/v81.patch
apply_patch 4 /tmp/v82.patch
apply_patch 1 /tmp/v83.patch
apply_patch 1 /tmp/v84.patch
apply_patch 1 /tmp/v85.patch
(
  cd "$SOURCE"
  patch --dry-run --batch -p1 < /tmp/v86.patch
)
apply_patch 1 /tmp/v86.patch

python3 - <<'PY'
from pathlib import Path
import re
path = Path("source/scripts/configure_stable_signing.py")
text = path.read_text()
updated, count = re.subn(
    r'v\d+\.\{run_number\}-stable-update',
    'v86.{run_number}-stable-update',
    text,
    count=1,
)
if count != 1:
    raise SystemExit("Could not set V86 in the signing script")
path.write_text(updated)
PY

rm -rf /tmp/jane-model-viewer
mkdir -p /tmp/jane-model-viewer
(
  cd /tmp/jane-model-viewer
  npm pack @google/model-viewer@4.1.0 --silent
  tar -xzf google-model-viewer-4.1.0.tgz
  cp package/dist/model-viewer-umd.min.js \
    "$SOURCE/app/src/main/assets/model-viewer-umd.min.js"
)
test -s "$SOURCE/app/src/main/assets/model-viewer-umd.min.js"

touch "$SOURCE/gradle.properties"
grep -q '^android.useAndroidX=' "$SOURCE/gradle.properties" \
  || echo 'android.useAndroidX=true' >> "$SOURCE/gradle.properties"
grep -q '^android.enableJetifier=' "$SOURCE/gradle.properties" \
  || echo 'android.enableJetifier=true' >> "$SOURCE/gradle.properties"

export JANE_VERSION_CODE="${GITHUB_RUN_NUMBER:-1}"
export JANE_KEYSTORE_PASSWORD="JaneUpdate2026"
export JANE_KEY_ALIAS="janeupdate"

curl -fsSL \
  https://raw.githubusercontent.com/axisdedmon-gif/JaneAIAndroidApp/fbad016ea0d1ceaee341d39d6969484568f8e1dd/signing/jane-update-key.b64 \
  | tr -d '\r\n' \
  | base64 -d > "$SOURCE/app/jane-update-key.jks"

keytool -list \
  -keystore "$SOURCE/app/jane-update-key.jks" \
  -storepass "$JANE_KEYSTORE_PASSWORD" \
  -alias "$JANE_KEY_ALIAS" >/dev/null

(
  cd "$SOURCE"
  python3 scripts/configure_stable_signing.py
)

INDEX="$SOURCE/app/src/main/assets/index.html"
MAIN="$SOURCE/app/src/main/java/com/example/janeai/MainActivity.java"
for token in \
  v86-reliability-recovery-list-answer \
  JANE_V86_RELIABILITY_RECOVERY \
  fallbackRecovery:true \
  listQuestionSupport:true \
  nativeArchiveRecovery:true \
  composeJaneAnswer \
  requestedCount \
  v86-dialog-scroll-lock \
  MutationObserver \
  overflow-y:auto \
  JANE_V85_FINAL_RESPONSE \
  personalityFirstKnowledgeAnswers:true \
  fullScrollableDialog:true \
  networkIndependentImport:true \
  connectionStateDoesNotModifyArchives:true \
  v67b-transparent-webp-dialog-portrait-router
do
  grep -q "$token" "$INDEX"
done

for token in knowledge_archive catalog.json copyKnowledgeOriginal writeKnowledgeArchiveText searchKnowledgeArchives playAssetVoice; do
  grep -q "$token" "$MAIN"
done

! grep -q '^[-+].*deleteKnowledgeArchive' /tmp/v86.patch
! grep -q '^[-+].*getKnowledgeArchiveDir' /tmp/v86.patch
! grep -q '^[-+].*catalog.json' /tmp/v86.patch
! grep -q '^[-+].*copyKnowledgeOriginal' /tmp/v86.patch
grep -q 'versionName = "v86.' "$SOURCE/scripts/configure_stable_signing.py"
grep -q 'applicationId = "com.example.janeai"' "$SOURCE/app/build.gradle"
grep -q 'versionName = "v86.' "$SOURCE/app/build.gradle"
grep -q 'signingConfig signingConfigs.janeStable' "$SOURCE/app/build.gradle"

(
  cd "$SOURCE"
  gradle :app:assembleDebug
  keytool -printcert -jarfile app/build/outputs/apk/debug/app-debug.apk \
    | grep -q "Owner: CN=Jane AI Assistant"
)

rm -f "$SOURCE/app/jane-update-key.jks"
(
  cd "$SOURCE"
  zip -q -r -9 "$ROOT/JaneAIAndroidSource_v86_reliable_list_answer_scroll.zip" . \
    -x '.gradle/*' 'app/build/*' '**/.DS_Store'
)

test -s "$SOURCE/app/build/outputs/apk/debug/app-debug.apk"
test -s "$ROOT/JaneAIAndroidSource_v86_reliable_list_answer_scroll.zip"
