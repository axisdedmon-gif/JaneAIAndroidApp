# Jane AI Assistant APK, phone-only build guide

This package contains the Android project for Jane AI Assistant.

It is already wired to use the ElevenLabs voice ID:

wScwPA1qCkWo5R2dmlS8

You still need to host the backend endpoint and insert that endpoint URL into the Android project before building the APK.

## What you will do from your phone

1. Host the backend API.
2. Put your ElevenLabs API key into the backend host.
3. Put your backend URL into the APK project.
4. Build the APK with GitHub Actions.
5. Download and install the APK on your phone.

## Files that matter

Backend files:
- server.mjs
- package.json

Android file to edit:
- app/src/main/java/com/example/janeai/MainActivity.java

Find this line:

private static final String BACKEND_TTS_URL = "https://YOUR-DOMAIN.com/api/tts";

Replace it with your hosted backend URL, for example:

private static final String BACKEND_TTS_URL = "https://jane-backend.onrender.com/api/tts";

## Important

Do not put your ElevenLabs API key into the APK or HTML.
Only put it into your backend host environment variables.
