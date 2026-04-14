# VideoDecoder Agent Instructions

This repository is an Android application with a significant C++ native component for video decoding via FFmpeg, OpenGL ES, and AAudio.

## Architecture Boundaries
- **Java Layer**: Handles UI and user interaction (`app/src/main/java/com/example/videodecoder/MainActivity.java`).
- **C++ Layer**: Handles core media processing via JNI (`app/src/main/cpp/native-lib.cpp`). Uses a multi-threaded producer-consumer architecture (Demuxer -> Decoder -> Renderer).
- **Audio/Video Sync**: Synchronization logic is intricate and relies on PTS (Presentation Timestamp) tracking. Read `问题.md` and `音画同步解决方案.md` to understand the established A/V sync strategies before modifying playback clocks, threading, or sleep functions.

## Build Requirements & Constraints
- **Strict Architecture Limit**: The project is explicitly configured to build **ONLY for `arm64-v8a`** (via `abiFilters` in `app/build.gradle`).
  - ⚠️ **Do not attempt to run or test on an x86/x86_64 emulator.** It will fail to find or load the prebuilt native FFmpeg library. Testing must be done on an `arm64-v8a` device or compatible emulator.
- **Dependencies**:
  - Gradle uses `gradle/libs.versions.toml` for Java dependency management.
  - C++ uses a pre-compiled FFmpeg library located at `app/src/main/jniLibs/arm64-v8a/libffmpeg.so`.
- **C++ Build**: Managed via CMake (`app/src/main/cpp/CMakeLists.txt`), utilizing standard Android NDK tools and targeting `gnu++11`.

## Essential Commands
- **Build APK (Debug)**: `.\gradlew.bat assembleDebug` (Windows) or `./gradlew assembleDebug` (Unix).
- **Clean Project**: `.\gradlew.bat clean` (Windows) or `./gradlew clean` (Unix).

*Note: The default `ExampleUnitTest` and `ExampleInstrumentedTest` files are present but not heavily utilized; focus testing efforts on successful builds and manual on-device verification.*