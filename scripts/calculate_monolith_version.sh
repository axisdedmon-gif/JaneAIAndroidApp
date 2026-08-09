#!/usr/bin/env bash
set -euo pipefail

BASE_MONO_GEN=1
BASE_UI_GEN=0
BASE_ANDROID_BUILD=200004
BASELINE_SUBJECT='chore(release): monolith-semver-baseline'

: "${GITHUB_RUN_NUMBER:?GITHUB_RUN_NUMBER is required.}"

if ! [[ "$GITHUB_RUN_NUMBER" =~ ^[1-9][0-9]*$ ]]; then
  echo "GITHUB_RUN_NUMBER must be a positive base-10 integer; got: $GITHUB_RUN_NUMBER" >&2
  exit 1
fi

BASELINE_SHA="$(git log --format='%H%x09%s' --all | awk -F '\t' -v subject="$BASELINE_SUBJECT" '$2 == subject {print $1; exit}')"
if [[ -z "$BASELINE_SHA" ]]; then
  echo "Version baseline commit not found: $BASELINE_SUBJECT" >&2
  exit 1
fi

RANGE="${BASELINE_SHA}..HEAD"
SUBJECTS="$(git log "$RANGE" --format='%s')"

count_subject() {
  local pattern="$1"
  if [[ -z "$SUBJECTS" ]]; then
    printf '0\n'
    return
  fi
  printf '%s\n' "$SUBJECTS" | grep -Ec "$pattern" || true
}

MONO_BUMPS="$(count_subject '^feat\(mono\):')"
UI_BUMPS="$(count_subject '^feat\(ui\):')"
X=$((BASE_MONO_GEN + MONO_BUMPS))
Y=$((BASE_UI_GEN + UI_BUMPS))

LATEST_STRUCTURAL_SHA="$(
  git log "$RANGE" --format='%H%x09%s' \
    | awk -F '\t' '$2 ~ /^feat\((mono|ui)\):/ {print $1; exit}'
)"

if [[ -n "$LATEST_STRUCTURAL_SHA" ]]; then
  FIX_RANGE="${LATEST_STRUCTURAL_SHA}..HEAD"
else
  FIX_RANGE="$RANGE"
fi

Z="$(git log "$FIX_RANGE" --format='%s' | grep -Ec '^fix\((mono|ui)\):' || true)"
B=$((BASE_ANDROID_BUILD + GITHUB_RUN_NUMBER))

if (( B > 2100000000 )); then
  echo "Calculated Android versionCode exceeds the platform maximum: $B" >&2
  exit 1
fi

VERSION_NAME="${X}.${Y}.${Z}-beta.${B}+gen${X}.ui${Y}"
VERSION_CODE="$B"

printf 'Calculated VersionName: %s\n' "$VERSION_NAME"
printf 'Calculated VersionCode: %s\n' "$VERSION_CODE"
printf 'Generation source: mono=%s ui=%s patch=%s\n' "$X" "$Y" "$Z"
printf 'Baseline: %s\n' "$BASELINE_SHA"

if [[ -n "${GITHUB_ENV:-}" ]]; then
  {
    echo "VERSION_NAME=$VERSION_NAME"
    echo "VERSION_CODE=$VERSION_CODE"
    echo "MONOLITH_GEN=$X"
    echo "UI_GEN=$Y"
    echo "PATCH_GEN=$Z"
  } >> "$GITHUB_ENV"
fi

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "version_name=$VERSION_NAME"
    echo "version_code=$VERSION_CODE"
    echo "monolith_gen=$X"
    echo "ui_gen=$Y"
    echo "patch_gen=$Z"
  } >> "$GITHUB_OUTPUT"
fi
