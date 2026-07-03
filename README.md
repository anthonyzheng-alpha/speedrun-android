<p align="center">
<img alt="" src="docs/graphics/logos/banner_readme.png"/>
</p>

<a href="https://github.com/ankidroid/Anki-Android/releases"><img src="https://img.shields.io/github/v/release/ankidroid/Anki-Android" alt="release"/></a>
<a href="https://github.com/ankidroid/Anki-Android/actions"><img src="https://img.shields.io/github/checks-status/ankidroid/Anki-Android/main?label=build" alt="build"/></a>
<a href="https://opencollective.com/ankidroid"><img src="https://img.shields.io/opencollective/all/ankidroid" alt="Open Collective backers and sponsors"/></a>
<a href="https://github.com/ankidroid/Anki-Android/issues"><img src="https://img.shields.io/github/commit-activity/m/ankidroid/Anki-Android" alt="commit-activity"/></a>
<a href="https://github.com/ankidroid/Anki-Android/network/members"><img src="https://img.shields.io/github/forks/ankidroid/Anki-Android" alt="forks"/></a>
<a href="https://github.com/ankidroid/Anki-Android/stargazers"><img src="https://img.shields.io/github/stars/ankidroid/Anki-Android" alt="stars"/></a>
<a href="https://crowdin.com/project/ankidroid"><img src="https://badges.crowdin.net/ankidroid/localized.svg"></img></a>
<a href="https://github.com/ankidroid/Anki-Android/graphs/contributors"><img src="https://img.shields.io/github/contributors/ankidroid/Anki-Android" alt="contributors"/></a>
<a href="https://discord.gg/qjzcRTx"><img src="https://img.shields.io/discord/368267295601983490"></img></a>
<a href="https://github.com/ankidroid/Anki-Android/blob/main/COPYING"><img src="https://img.shields.io/github/license/ankidroid/Anki-Android" alt="license"/></a>

# AnkiDroid
A semi-official port of the open source [Anki](https://apps.ankiweb.net/index.html) spaced repetition flashcard system to Android. Memorize anything with AnkiDroid! Specifically designed for MCAT exam preparation.

<img src="docs/graphics/logos/ankidroid_logo.png" align="right" width="40%" height="100%"></img>

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
- A performance model (estimates the chance the user will correctly answer an exam-style question)
- A readiness model (estimates the approximate MCAT score the user would get based on their current performance in the app)
- Exam coverage progress
- Practice exam mode

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

**Android Studio:** select the running emulator → click **Run** on the AnkiDroid configuration.

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
