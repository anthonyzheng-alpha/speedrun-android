# Speedrun Android
A clone of AnkiDroid specifically designed for MCAT exam preparation. Credit to [AnkiDroid](https://github.com/ankidroid/Anki-Android).


### Features

<div style="display:flex;">
 
- Night mode
- Whiteboard 
- Progress widget
- Detailed statistics
- Syncing with AnkiWeb
- Write answers (optional)
- Text-to-speech integration
- More than 10,000 premade decks
- Spaced repetition (AI-optimized [FSRS algorithm](https://github.com/open-spaced-repetition))
- Supported contents: text, images, sounds, MathJax
- Add cards by intent from other applications like dictionaries
- Topic-aware scheduling setting
- A memory model (measures the probability the user will remember the fact on an exam)
  - When reviewing a card, click the three dots in the top right corner of the card and select "Memory model".
- A performance model (located in the home screen, estimates the chance the user will correctly answer an exam-style question)
- A readiness model (located in the home screen, estimates the approximate MCAT score the user would get based on their current performance in the app)
- Exam coverage progress (in the home menu, there are percentages that tell the user the percent of MCAT exam content they have mastered with the cards. There is an overall percentage as well as a breakdown by topic.)
- Practice exam mode (this mode allows the user to take a practice exam with questions from the MCAT exam content and personalize the content to their needs. Problems derived from Kaplan's MCAT test prep books.)

</div>

Run on an Android emulator
--------------------------

This app requires **two sibling repositories**. Gradle hard-codes the backend folder name, so your checkout must look like:

```
parent-folder/
  speedrun-android/          ← this repo (Kotlin UI)
  speedrun-android-backend/  ← MCAT Rust backend (exact name required)
```

The desktop [speedrun](https://github.com/anthonyzheng-alpha/speedrun) repo is not required for Android.

### 1. Install tools (one-time)

| Tool | Notes |
|------|-------|
| [Android Studio](https://developer.android.com/studio) | Must support AGP 9.0.1 (see `gradle/libs.versions.toml`) |
| **JDK 21** | Set in **Settings → Build Tools → Gradle → Gradle JDK**. Do not rely on system Java 8 on PATH. |
| **Android SDK 36** | Install via SDK Manager |
| **NDK 29.0.14206865** | SDK Manager → SDK Tools → show package details |
| **Rust** ([rustup.rs](https://rustup.rs/)) | Required to build the backend |
| **msys2** (Windows only) | `pacman -S git rsync`; add `C:\msys64\usr\bin` to PATH when building the backend |

### 2. Clone both repos side by side

```powershell
mkdir alphaai
cd alphaai
git clone https://github.com/anthonyzheng-alpha/speedrun-android speedrun-android
git clone --recurse-submodules https://github.com/anthonyzheng-alpha/speedrun-android-backend speedrun-android-backend
```

If you already cloned the backend without submodules:

```powershell
cd speedrun-android-backend
git submodule update --init --recursive
```

See [speedrun-android-backend](https://github.com/anthonyzheng-alpha/speedrun-android-backend) for backend-specific build details.

### 3. Build the backend (first time, ~30–60 min)

**Windows (PowerShell):**

```powershell
cd speedrun-android-backend
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_NDK_VERSION = "29.0.14206865"
$env:ANDROID_NDK_HOME = "$env:ANDROID_HOME\ndk\$env:ANDROID_NDK_VERSION"
$env:PATH += ";C:\msys64\usr\bin"
.\build.bat
```

**macOS / Linux:** set the same `ANDROID_HOME`, `ANDROID_NDK_VERSION`, and `ANDROID_NDK_HOME`, then run `./build.sh`.

This must produce `speedrun-android-backend/rsdroid/build/outputs/aar/rsdroid-release.aar`. Rebuild the backend whenever MCAT Rust code changes.

### 4. Create `local.properties`

Copy [`local.properties.example`](local.properties.example) to `local.properties` at the repo root (this file is gitignored). At minimum:

```properties
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
local_backend=true
enable_coverage=false
enable_languages=false
```

- `local_backend=true` is **required** for MCAT features (practice exam recording, memory/readiness models, topic-aware scheduling, etc.).
- `local_backend=false` uses the upstream Maven backend and will **not** include this fork's Rust changes.

### 5. Create and start an emulator (one-time)

1. Android Studio → **Device Manager** → **Create Virtual Device**
2. Pick a Pixel profile
3. System image: **x86_64**, API **34–36** (Windows; use arm64 on Apple Silicon Macs)
4. Start the AVD and wait for the home screen before deploying

### 6. Open the project and select the build variant

1. **File → Open** → the `speedrun-android` repo root (not the `AnkiDroid` subfolder)
2. Wait for Gradle sync (fails if the AAR is missing — rebuild the backend, step 3)
3. **View → Tool Windows → Build Variants** → set `:AnkiDroid` to **`playDebug`**

Use **`playDebug`** for day-to-day work. It installs as `com.ichi2.anki.debug` (red icon) and avoids building `amazonDebug` in parallel, which can cause Kotlin out-of-memory errors on first build. Do not use `./gradlew assembleDebug` for routine development.

### 7. Run on the emulator

**Android Studio:** select the running emulator → click **Run** on the AnkiDroid configuration (green play button).

**CLI alternative:**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # adjust per OS
cd speedrun-android
.\gradlew installPlayDebug
```

The first build can take 10–20+ minutes; incremental rebuilds are much faster.

### 8. First launch

1. Tap **Get started** on the intro screen
2. Dismiss optional dialogs (e.g. backup prompt → **Later**)
3. Complete permissions / storage setup
4. On the deck picker: **⋮ menu → Do Practice Exam**

AnkiWeb sync is optional for local testing. Practice exam questions ship from bundled assets in `AnkiDroid/src/main/assets/practice_exam/questions.json`.

### Troubleshooting

| Symptom | Fix |
|---------|-----|
| Gradle can't find `rsdroid-release.aar` | Build the backend (step 3); confirm the sibling folder is named `speedrun-android-backend` |
| Kotlin OOM during build | Use `playDebug` only; ensure `gradle.properties` has `kotlin.daemon.jvmargs=-Xmx6144M` |
| Wrong Java version | Set Gradle JDK to 21 in Android Studio |
| `cargo not found` | Install Rust; restart Android Studio from a shell where `cargo` works |
| Crash on startup (`UnsatisfiedLinkError` / `rsdroid`) | Rebuild the backend for your emulator arch (x86_64 on Windows emulators) |
| Blue icon instead of red | Wrong build variant — switch to `playDebug` |


## Build and install release APK

These steps produce a **distributable release APK** you can install on a real phone or emulator. They differ from the **Run on an Android emulator** section above, which builds a red-icon `playDebug` dev build for day-to-day work.

**Prerequisites:** complete steps 1–4 above (install tools, clone both repos, build the backend once, create `local.properties` with `local_backend=true`).

### 1. Build the backend for all device architectures

The default `build.bat` / `build.sh` on Windows/Linux only compiles **one** Android ABI (x86_64 on Windows), which is fine for emulators but **not** for phones. For a distributable APK, build all four ABIs.

**macOS / Linux (works end-to-end):**

```bash
export ANDROID_HOME=...          # see step 3 above
export ANDROID_NDK_VERSION=29.0.14206865
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/$ANDROID_NDK_VERSION
export ALL_ARCHS=1
export RELEASE=1                 # optional, faster runtime
./build.sh
```

**Windows (PowerShell):**

```powershell
cd speedrun-android-backend
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_NDK_VERSION = "29.0.14206865"
$env:ANDROID_NDK_HOME = "$env:ANDROID_HOME\ndk\$env:ANDROID_NDK_VERSION"
$env:PATH += ";C:\msys64\usr\bin"
$env:ALL_ARCHS = "1"
.\build.bat
```

On Windows, `ALL_ARCHS=1` may exit with **"Must be on macOS to do a multi-arch build"** after the Android `jniLibs` are built. That is expected — the Robolectric (host test) library step only runs on macOS, but the four phone/emulator ABIs are already compiled. Assemble the AAR from the built `jniLibs`:

```powershell
$env:RUNNING_FROM_BUILD_SCRIPT = "1"
.\gradlew.bat :rsdroid:assembleRelease
```

Verify `speedrun-android-backend/rsdroid/build/outputs/aar/rsdroid-release.aar` exists and contains `jni/arm64-v8a`, `jni/armeabi-v7a`, `jni/x86`, and `jni/x86_64`.

### 2. Build the release APK

From `speedrun-android/` (with `local_backend=true` in `local.properties`):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # adjust per OS
.\gradlew.bat clean :AnkiDroid:assembleFullRelease -Duniversal-apk=true
```

- **Variant:** `fullRelease` — unrestricted GitHub/F-Droid flavor.
- **Output:** `AnkiDroid/build/outputs/apk/full/release/`
  - `AnkiDroid-full-universal-release.apk` — install this on any device
  - Per-ABI split APKs are also produced if you omit `-Duniversal-apk=true`
- **Signing:** uses `tools/fallback-release-keystore.jks` when `KEYSTOREPATH` is unset (fine for personal/testing). For public distribution, set `KEYSTOREPATH`, `KEYSTOREPWD` (or `KSTOREPWD`), `KEYALIAS`, and `KEYPWD` before building.
- **Lint:** if `lintVitalFullRelease` fails on pre-existing MCAT string-resource lint errors, skip that gate:

```powershell
.\gradlew.bat :AnkiDroid:assembleFullRelease -Duniversal-apk=true -x lintVitalFullRelease
```

### 3. Install on a device or emulator

**Emulator** (after starting an AVD from step 5 above):

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r `
  AnkiDroid\build\outputs\apk\full\release\AnkiDroid-full-universal-release.apk
```

**Physical phone:**

1. Enable **Install unknown apps** for your file manager or browser (Settings → Apps → Special access).
2. Copy `AnkiDroid-full-universal-release.apk` to the phone (USB, cloud, or email).
3. Open the APK and confirm install.

**First launch:** same as step 8 above — tap **Get started**, dismiss optional dialogs, complete permissions, then on the deck picker use **⋮ menu → Do Practice Exam**.

Release builds use the blue icon and package id `com.ichi2.anki` (not the red `playDebug` `.debug` suffix).

### Troubleshooting (release builds)

| Symptom | Fix |
|---------|-----|
| `UnsatisfiedLinkError` / `rsdroid` on a real phone | Backend was built x86_64-only; rebuild with all ABIs (step 1) |
| `ALL_ARCHS` build fails on Windows at Robolectric | Expected; run `gradlew :rsdroid:assembleRelease` with `RUNNING_FROM_BUILD_SCRIPT=1` |
| `lintVitalFullRelease` fails | Use `-x lintVitalFullRelease` or fix lint in `practice_exam.xml` / related strings |
| APK won't install (signature conflict) | Uninstall existing AnkiDroid or AnkiDroid debug build first |