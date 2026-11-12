# Mira AI — Android Assistant

A native Java Android assistant starter for Android 8.0+.

## Current starter features
- Native chat interface with conversation bubbles
- Android speech input intent and Android Text-to-Speech output
- Bengali-friendly interface
- Permission handling for microphone
- Clear offline-model status (no fake AI responses)
- Gradle build and GitHub Actions debug-APK workflow

## Important status
This first scaffold is **not yet a Gemini-equivalent assistant**. The local GGUF/llama.cpp inference engine, streaming speech pipeline, web search, image understanding, and cloud-provider integration are planned next phases and are not falsely represented as working here.

## Build
Use Android Studio with JDK 17+ and Android SDK 35, or use a compatible Gradle installation:

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Device permissions
Microphone permission is requested only when voice input is used. Speech recognition availability and offline behavior depend on the installed Android recognition service.

## Model safety
Do not commit model files or API keys to Git. Add a compatible GGUF model locally after the llama.cpp Android runtime is integrated.
