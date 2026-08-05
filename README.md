# Jane AI Android — V88 flattened source

This is the complete V88 Android source tree. It no longer reconstructs V73 or
applies the V76–V88 patch chain. The workflow builds this source directly.

The on-device Qwen2.5 0.5B task model is intentionally excluded from Git because
it exceeds GitHub's per-file limit. `scripts/build_v88_direct.sh` downloads the
exact pinned model, verifies its byte length and SHA-256 digest, packages it
uncompressed in the APK, builds with Java 21, and validates the APK.

The historical patch chain remains available in Git history only.
