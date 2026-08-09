from __future__ import annotations

import os
import re
from pathlib import Path

SEMVER_RE = re.compile(
    r"^(?P<x>0|[1-9]\d*)\.(?P<y>0|[1-9]\d*)\.(?P<z>0|[1-9]\d*)"
    r"-beta\.(?P<build>0|[1-9]\d*)\+gen(?P<gx>0|[1-9]\d*)\.ui(?P<gy>0|[1-9]\d*)$"
)
ANDROID_VERSION_CODE_MAX = 2_100_000_000


def required_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise SystemExit(f"{name} is required.")
    return value


def replace_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        raise SystemExit(f"Could not update {label}; expected exactly one match, found {count}.")
    return updated


version_name = required_env("VERSION_NAME")
version_code_text = required_env("VERSION_CODE")
match = SEMVER_RE.fullmatch(version_name)
if not match:
    raise SystemExit(
        "VERSION_NAME must match X.Y.Z-beta.B+genX.uiY, for example "
        "1.1.0-beta.200005+gen1.ui1."
    )

if match.group("x") != match.group("gx") or match.group("y") != match.group("gy"):
    raise SystemExit("VERSION_NAME generation metadata does not match its X/Y version components.")

if match.group("build") != version_code_text:
    raise SystemExit("VERSION_CODE must equal the beta build counter B embedded in VERSION_NAME.")

try:
    version_code = int(version_code_text)
except ValueError as error:
    raise SystemExit("VERSION_CODE must be a base-10 integer.") from error

if not 1 <= version_code <= ANDROID_VERSION_CODE_MAX:
    raise SystemExit(
        f"VERSION_CODE must be between 1 and {ANDROID_VERSION_CODE_MAX}; got {version_code}."
    )

root = Path(__file__).resolve().parents[1]
gradle_path = root / "app/build.gradle"
build_script_path = root / "scripts/build_monolith_apk.sh"
bootstrap_path = root / "app/src/main/java/ai/monolith/app/MonolithBootstrapActivity.java"

gradle_text = gradle_path.read_text(encoding="utf-8")
gradle_text = replace_once(
    gradle_text,
    r"versionCode\s*=\s*\d+",
    f"versionCode = {version_code}",
    "Gradle versionCode",
)
gradle_text = replace_once(
    gradle_text,
    r'versionName\s*=\s*"[^"]+"',
    f'versionName = "{version_name}"',
    "Gradle versionName",
)
gradle_path.write_text(gradle_text, encoding="utf-8")

build_text = build_script_path.read_text(encoding="utf-8")
build_text = replace_once(
    build_text,
    r'^BETA_VERSION="[^"]*"$',
    f'BETA_VERSION="{version_name}"',
    "build-script version label",
)
build_text = replace_once(
    build_text,
    r'^EXPECTED_VERSION_CODE="\d+"$',
    f'EXPECTED_VERSION_CODE="{version_code}"',
    "build-script versionCode",
)
build_text = replace_once(
    build_text,
    r'^EXPECTED_VERSION_NAME="[^"]*"$',
    f'EXPECTED_VERSION_NAME="{version_name}"',
    "build-script versionName",
)
build_text = replace_once(
    build_text,
    r'^FINAL_APK=.*$',
    'FINAL_APK="$DIST_DIR/MonolithAI-${BETA_VERSION}.apk"',
    "build-script APK filename",
)

# The existing validated build script contains literal release assertions in shell and in its
# embedded APK verifier. CI version injection must update those assertions atomically so the
# build cannot accidentally validate one version and package another.
build_text = re.sub(r'Beta 2\.0\.04', version_name, build_text)
build_text = re.sub(r'\b200004\b', str(version_code), build_text)
build_text = re.sub(
    r"DETERMINISTIC STARTUP BOUNDARY // BETA 2\.0\.04",
    f"DETERMINISTIC STARTUP BOUNDARY // {version_name}",
    build_text,
)
build_script_path.write_text(build_text, encoding="utf-8")

bootstrap_text = bootstrap_path.read_text(encoding="utf-8")
bootstrap_text = replace_once(
    bootstrap_text,
    r'DETERMINISTIC STARTUP BOUNDARY // [^"]+',
    f'DETERMINISTIC STARTUP BOUNDARY // {version_name}',
    "BIOS version banner",
)
bootstrap_path.write_text(bootstrap_text, encoding="utf-8")

# Final cross-file integrity checks. These fail before any dependency/model download.
final_gradle = gradle_path.read_text(encoding="utf-8")
final_build = build_script_path.read_text(encoding="utf-8")
final_bootstrap = bootstrap_path.read_text(encoding="utf-8")
checks = {
    f'versionCode = {version_code}': final_gradle,
    f'versionName = "{version_name}"': final_gradle,
    f'EXPECTED_VERSION_CODE="{version_code}"': final_build,
    f'EXPECTED_VERSION_NAME="{version_name}"': final_build,
    f'DETERMINISTIC STARTUP BOUNDARY // {version_name}': final_bootstrap,
}
for token, haystack in checks.items():
    if token not in haystack:
        raise SystemExit(f"CI version integrity check failed: missing {token!r}.")

print(f"Injected Monolith AI {version_name} / versionCode {version_code} across build metadata and BIOS diagnostics.")
