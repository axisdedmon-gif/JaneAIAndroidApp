#!/usr/bin/env python3
"""Convert a standard Piper ONNX export for Monolith AI's sherpa-onnx runtime.

Input:
  voice.onnx
  voice.onnx.json

Output:
  voice.onnx      (updated in-place with sherpa Piper metadata)
  tokens.txt

This conversion is intentionally external/offline. It can run in the same Linux/Python
workspace used to train/export the Piper voice, then the resulting ONNX + tokens.txt can
be imported into Monolith AI's Voice Module.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

try:
    import onnx
except ImportError as exc:
    raise SystemExit("Missing dependency: pip install onnx") from exc

META_KEYS = {
    "model_type",
    "comment",
    "language",
    "voice",
    "has_espeak",
    "n_speakers",
    "sample_rate",
}


def load_config(path: Path) -> dict:
    if not path.is_file():
        raise SystemExit(f"Piper config not found: {path}")
    with path.open("r", encoding="utf-8") as stream:
        data = json.load(stream)
    for key in ("phoneme_id_map", "language", "espeak", "audio"):
        if key not in data:
            raise SystemExit(f"Piper config is missing required field: {key}")
    return data


def generate_tokens(config: dict, output: Path) -> None:
    rows: list[tuple[int, str]] = []
    for symbol, raw_ids in config["phoneme_id_map"].items():
        if not isinstance(raw_ids, list) or not raw_ids:
            continue
        rows.append((int(raw_ids[0]), str(symbol)))
    if not rows:
        raise SystemExit("Piper phoneme_id_map did not contain usable token IDs.")
    rows.sort(key=lambda item: item[0])
    with output.open("w", encoding="utf-8", newline="\n") as stream:
        for token_id, symbol in rows:
            stream.write(f"{symbol} {token_id}\n")


def apply_metadata(model_path: Path, config: dict) -> None:
    model = onnx.load(str(model_path))
    preserved = [(m.key, m.value) for m in model.metadata_props if m.key not in META_KEYS]
    del model.metadata_props[:]
    for key, value in preserved:
        meta = model.metadata_props.add()
        meta.key = key
        meta.value = value

    metadata = {
        "model_type": "vits",
        "comment": "piper",
        "language": config["language"]["code"],
        "voice": config["espeak"]["voice"],
        "has_espeak": 1,
        "n_speakers": int(config.get("num_speakers", 1) or 1),
        "sample_rate": int(config["audio"]["sample_rate"]),
    }
    for key, value in metadata.items():
        meta = model.metadata_props.add()
        meta.key = key
        meta.value = str(value)

    temporary = model_path.with_suffix(model_path.suffix + ".monolith.tmp")
    onnx.save(model, str(temporary))
    temporary.replace(model_path)


def main() -> int:
    parser = argparse.ArgumentParser(description="Convert Piper ONNX for Monolith AI Android offline speech.")
    parser.add_argument("model", type=Path, help="Piper .onnx model")
    parser.add_argument("--config", type=Path, help="Piper .onnx.json config; defaults to <model>.json")
    parser.add_argument("--tokens", type=Path, help="Output tokens.txt path; defaults beside the model")
    args = parser.parse_args()

    model = args.model.resolve()
    if not model.is_file() or model.suffix.lower() != ".onnx":
        raise SystemExit(f"Expected a Piper .onnx model: {model}")
    config_path = (args.config or Path(str(model) + ".json")).resolve()
    tokens_path = (args.tokens or (model.parent / "tokens.txt")).resolve()

    config = load_config(config_path)
    generate_tokens(config, tokens_path)
    apply_metadata(model, config)

    print(f"Converted Piper model: {model}")
    print(f"Generated tokens:      {tokens_path}")
    print("Import both files into Monolith AI Voice Module, then activate that model.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
