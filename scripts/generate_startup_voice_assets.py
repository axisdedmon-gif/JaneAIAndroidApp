#!/usr/bin/env python3
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HTML_PATH = ROOT / "app" / "src" / "main" / "assets" / "index.html"
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "startup_voice"

API_KEY = os.environ.get("ELEVENLABS_API_KEY", "").strip()
VOICE_ID = os.environ.get("ELEVENLABS_VOICE_ID", "").strip()

MODEL_ID = os.environ.get("ELEVENLABS_MODEL_ID", "eleven_turbo_v2_5").strip()
OUTPUT_FORMAT = os.environ.get("ELEVENLABS_OUTPUT_FORMAT", "mp3_44100_128").strip()

if not API_KEY:
    print("ERROR: ELEVENLABS_API_KEY is missing.", file=sys.stderr)
    sys.exit(2)
if not VOICE_ID:
    print("ERROR: ELEVENLABS_VOICE_ID is missing.", file=sys.stderr)
    sys.exit(2)

html = HTML_PATH.read_text(encoding="utf-8", errors="ignore")

def extract_array(name: str):
    m = re.search(rf"const\s+{re.escape(name)}\s*=\s*(\[.*?\]);", html, flags=re.S)
    if not m:
        raise RuntimeError(f"Could not find {name} in index.html")
    return json.loads(m.group(1))

sets = [
    ("suspicion", extract_array("STARTUP_LINES"), 40),
    ("cj", extract_array("CJ_VERIFIED_LINES"), 20),
    ("not_cj", extract_array("NOT_CJ_LINES"), 20),
]

OUT_DIR.mkdir(parents=True, exist_ok=True)

def synthesize(text: str, out_path: Path):
    url = (
        "https://api.elevenlabs.io/v1/text-to-speech/"
        + urllib.parse.quote(VOICE_ID)
        + "?output_format="
        + urllib.parse.quote(OUTPUT_FORMAT)
    )

    payload = {
        "text": text,
        "model_id": MODEL_ID,
        "voice_settings": {
            "stability": 0.48,
            "similarity_boost": 0.78,
            "style": 0.28,
            "use_speaker_boost": True
        }
    }

    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={
            "xi-api-key": API_KEY,
            "Content-Type": "application/json",
            "Accept": "audio/mpeg",
        },
    )

    last_error = None
    for attempt in range(1, 4):
        try:
            with urllib.request.urlopen(req, timeout=90) as resp:
                audio = resp.read()
            if len(audio) < 1024:
                raise RuntimeError(f"Audio response too small: {len(audio)} bytes")
            tmp = out_path.with_suffix(".tmp")
            tmp.write_bytes(audio)
            tmp.replace(out_path)
            return
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", errors="replace")
            last_error = f"HTTP {e.code}: {detail[:600]}"
            if e.code in (429, 500, 502, 503, 504):
                time.sleep(6 * attempt)
                continue
            break
        except Exception as e:
            last_error = str(e)
            time.sleep(4 * attempt)

    raise RuntimeError(f"Failed generating {out_path.name}: {last_error}")

total = 0
created = 0
for prefix, lines, expected in sets:
    if len(lines) != expected:
        print(f"WARNING: {prefix} expected {expected} lines, found {len(lines)}.")
    for i, line in enumerate(lines):
        filename = f"{prefix}_{i:02d}.mp3"
        out_path = OUT_DIR / filename
        total += 1
        if out_path.exists() and out_path.stat().st_size > 1024:
            print(f"SKIP {filename}")
            continue
        print(f"CREATE {filename}: {line[:70]}")
        synthesize(line, out_path)
        created += 1
        time.sleep(0.35)

print(f"Startup voice assets ready. Created {created}, total checked {total}.")
