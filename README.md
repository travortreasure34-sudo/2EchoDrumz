# EchoDrumz (MVP source, v0.1.0)

Offline drum pad + 16-step sequencer for Android 7.0+ (API 24). Kotlin + Jetpack Compose UI, C++ audio engine on Oboe.
No INTERNET permission, no storage permissions, no export of any kind.

## Build
1. Install Android Studio (Koala or newer) with SDK Platform 35, **NDK (Side by side)** and **CMake 3.22.1** (SDK Manager > SDK Tools).
2. Open this folder. Studio downloads Gradle 8.9 from `gradle/wrapper/gradle-wrapper.properties`.
   (Or run `gradle wrapper` once to generate `gradlew` if you prefer the command line.)
3. Run on a device/emulator, or `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`.
Gradle needs internet once to fetch dependencies (including Oboe); the app itself never uses the network.

## What is in this MVP
- PADS home screen: 16 multitouch pads, WAV import via system file picker (copied into private storage),
  per-pad volume / pitch / pan, one-shot / gate, mute, choke groups. Synthesized starter kit on first launch.
- SEQUENCER: 16 steps x 16 pad tracks, BPM 40-240, swing, play/stop, per-step velocity (long-press), sample-accurate
  timing inside the audio callback.
- PROJECT: new / save / save as / open / rename / duplicate / delete, autosave + session recovery, 6 themes
  (Midnight Black, Graphite, Studio Dark, Light Studio, Neon, Custom hue). Theme changes do not touch the audio engine.

## Not yet built (later phases in the spec)
Sample editor / waveform / chopping, Library tab, 32/64 steps, multiple patterns, pad banks, solo, track mute UI,
pad renaming/recoloring, drum kit save/load.

## Architecture
- `app/src/main/cpp/engine.cpp`: Oboe stream, 32 voices, lock-free event queue, sequencer. The callback does no
  allocation, locking, disk or JNI work. Samples are decoded in Kotlin and handed over via an atomic pointer swap;
  retired buffers are freed 30 s later (voices on them fade out in ~6 ms).
- `audio/`: JNI bridge, WAV decoder (PCM 8/16/24/32, float32; >2 channels are cut to first two).
- `data/`: models, JSON project codec, private-storage `Storage`, synthesized `DefaultKit`.
- `AppViewModel.kt` owns state; `ui/` holds the screens and themes.

## Testing status (honest report)
This source was written in a sandbox with no Android SDK/NDK/Gradle or network. Only `engine.cpp` was syntax-checked
(g++ against stub headers). **Nothing has been built, run, or heard on a device.** Expect to fix a few compile errors
on first build. Checklist to run on hardware:
- [ ] Builds; installs on API 24 device/emulator and a current Android version
- [ ] Pads play, multitouch works, no clicks, gate release is clean
- [ ] Import 16/24-bit and float WAVs, mono and stereo; bad file shows an error and doesn't crash
- [ ] Sequencer holds tempo for several minutes; swing and BPM changes are glitch-free
- [ ] Rotate / background / reopen: autosave and session recovery restore pads, pattern, BPM
- [ ] Theme switch during playback does not interrupt audio
- [ ] Manifest has no INTERNET permission (check merged manifest)

Notes: targetSdk is 35; raise it to the current stable level when you build. Version pins (AGP 8.6.1, Kotlin 1.9.24,
Compose BOM 2024.06, Oboe 1.9.0) were chosen from memory for API 24 compatibility and not verified online.

## Building the APK without installing anything
Push this project to a GitHub repository. The workflow in `.github/workflows/build-apk.yml` builds a debug APK
automatically: open the repo's Actions tab, open the latest run, and download `EchoDrumz-debug-apk`.
Copy `app-debug.apk` to your phone and install it (allow "install unknown apps" when prompted).
If the build fails, the run log shows the compile errors; paste them to me and I'll fix them.
