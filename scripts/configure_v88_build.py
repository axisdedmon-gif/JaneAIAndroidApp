#!/usr/bin/env python3
"""Configure and validate Jane V88's real on-device LLM build."""
from __future__ import annotations

import argparse
import re
from pathlib import Path

DEPENDENCY = 'implementation("com.google.mediapipe:tasks-genai:0.10.27")'
MODEL_NAME = 'qwen2_5_0_5b_q8.task'
MODEL_BYTES = '546660344'
MODEL_SHA256 = 'e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2'


def fail(message: str) -> None:
    raise SystemExit(f"V88 configuration error: {message}")


def replace_once(pattern: str, replacement: str, text: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        fail(f"{label}: expected exactly one match, found {count}")
    return updated


def configure_gradle(source_root: Path) -> None:
    build_file = source_root / 'app' / 'build.gradle'
    if not build_file.is_file():
        fail(f"missing {build_file}")
    text = build_file.read_text(encoding='utf-8')

    if re.search(r'(?m)^\s*minSdk\s*=\s*23\s*$', text):
        text = replace_once(
            r'(?m)^(\s*)minSdk\s*=\s*23\s*$',
            r'\1minSdk = 24',
            text,
            'minSdk upgrade',
        )
    elif not re.search(r'(?m)^\s*minSdk\s*=\s*24\s*$', text):
        fail('expected minSdk 23 or 24')

    if 'androidResources {' not in text:
        match = re.search(r'(?m)^android\s*\{\s*$', text)
        if not match:
            fail('could not find the android configuration block')
        block = '\n    androidResources {\n        noCompress += ["task"]\n    }\n'
        text = text[:match.end()] + block + text[match.end():]

    if DEPENDENCY not in text:
        match = re.search(r'(?m)^dependencies\s*\{\s*$', text)
        if not match:
            fail('could not find the dependencies block')
        text = text[:match.end()] + '\n    ' + DEPENDENCY + '\n' + text[match.end():]

    build_file.write_text(text, encoding='utf-8')

    properties_file = source_root / 'gradle.properties'
    properties = properties_file.read_text(encoding='utf-8') if properties_file.exists() else ''
    if not re.search(r'(?m)^org\.gradle\.jvmargs=', properties):
        properties = properties.rstrip() + '\norg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8\n'
    properties_file.write_text(properties, encoding='utf-8')


def validate_source(source_root: Path) -> None:
    build = (source_root / 'app' / 'build.gradle').read_text(encoding='utf-8')
    required_build = [
        'minSdk = 24',
        'androidResources {',
        'noCompress += ["task"]',
        DEPENDENCY,
        'applicationId = "com.example.janeai"',
    ]
    for token in required_build:
        if token not in build:
            fail(f"build.gradle is missing {token!r}")
    if build.count(DEPENDENCY) != 1:
        fail('MediaPipe dependency must appear exactly once')
    if build.count('noCompress += ["task"]') != 1:
        fail('the task no-compress rule must appear exactly once')
    if build.count('{') != build.count('}'):
        fail('build.gradle braces are unbalanced')

    engine = source_root / 'app' / 'src' / 'main' / 'java' / 'com' / 'example' / 'janeai' / 'OfflineKnowledgeEngine.java'
    activity = source_root / 'app' / 'src' / 'main' / 'java' / 'com' / 'example' / 'janeai' / 'MainActivity.java'
    index = source_root / 'app' / 'src' / 'main' / 'assets' / 'index.html'
    model_info = source_root / 'app' / 'src' / 'main' / 'assets' / 'offline_ai' / 'MODEL_INFO.txt'
    for path in (engine, activity, index, model_info):
        if not path.is_file() or path.stat().st_size == 0:
            fail(f"missing or empty {path}")

    engine_text = engine.read_text(encoding='utf-8')
    for token in (
        'class OfflineKnowledgeEngine',
        'LlmInference.createFromOptions',
        'generateResponse(prompt)',
        'sizeInTokens(prompt)',
        'expandSearchQuery',
        'raw PDF/OCR fragments are never presented',
        f'private static final String MODEL_FILE = "{MODEL_NAME}"',
        'private static final long EXPECTED_MODEL_BYTES = 546_660_344L',
    ):
        if token not in engine_text:
            fail(f"offline engine is missing {token!r}")
    for forbidden in ('HttpURLConnection', 'api/chat', 'gemini', 'placeholder'):
        if forbidden.lower() in engine_text.lower():
            fail(f"offline engine contains forbidden network/placeholder token {forbidden!r}")

    activity_text = activity.read_text(encoding='utf-8')
    for token in ('answerKnowledgeOffline', 'JaneNativeOfflineKnowledgeAnswerResult', 'OfflineKnowledgeEngine.getInstance'):
        if token not in activity_text:
            fail(f"MainActivity is missing {token!r}")

    index_text = index.read_text(encoding='utf-8')
    for token in (
        'v88-true-offline-knowledge-ai',
        'JANE_V88_TRUE_OFFLINE_AI',
        'actualOnDeviceLlm:true',
        'networkRequired:false',
        'rawFragmentFallback:false',
        'window.AndroidJane.answerKnowledgeOffline',
    ):
        if token not in index_text:
            fail(f"index.html is missing {token!r}")

    info = model_info.read_text(encoding='utf-8')
    for token in (MODEL_NAME, MODEL_BYTES, MODEL_SHA256, 'Qwen2.5-0.5B-Instruct', 'LiteRT task bundle'):
        if token not in info:
            fail(f"MODEL_INFO.txt is missing {token!r}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('source_root', type=Path)
    args = parser.parse_args()
    root = args.source_root.resolve()
    configure_gradle(root)
    validate_source(root)
    print('V88 source configuration and static validation passed.')


if __name__ == '__main__':
    main()
