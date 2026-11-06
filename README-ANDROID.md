# Capri Mobile — native Android port

A Kotlin/Android port of [Capri AI](https://github.com/Capri-cmd/capri-ai), built for a Xiaomi Redmi Note 12
(Android 15 / HyperOS). Capri keeps her original voice, persona text, examples, language handling and sprite art;
the PySide6 desktop shell is replaced with plain Android Views.

## APK

The debug build produced in this workspace is:

```
/root/capri/app/build/outputs/apk/debug/app-debug.apk
/root/capri/capri-mobile-1.0.0-debug.apk        (convenience copy)
```

6,781,051 bytes · SHA-256 `aeda62c73f0f2878a2b0c88c5c048ba23bd81f5238d1e1c5410be71cdcec0f49`

Install it with:

```sh
adb install -r /root/capri/app/build/outputs/apk/debug/app-debug.apk
```

or copy the APK to the phone and open it (allow "Install unknown apps" for the file manager first).

The APK is debug-signed (`APK Signature Scheme v2`, Android debug certificate). It is installable, but not
Play-Store/release signed.

Build verification performed in this workspace:

- `./gradlew assembleDebug` → `BUILD SUCCESSFUL`; APK is 6.78 MB (6.47 MiB).
- `aapt2 dump badging` confirms package `com.capri.mobile`, version 1.0.0, `minSdk 26`, `targetSdk 35`, the
  launcher activity and the declared permissions.
- `apksigner verify` → `Verifies`, v2 scheme, 1 signer (Android Debug).
- APK contents include `assets/capri/*.png` (all sprite strips), `assets/fonts/ShareTechMono-Regular.ttf` and
  `assets/persona.json`.
- `./gradlew testDebugUnitTest` → `BUILD SUCCESSFUL`, 11 tests, 0 failures, 0 errors.

## What works

- **Original Capri character** — `assets/persona.json` is generated from upstream `capri/brain/persona.py`.
  Character sheet, example dialogue, acknowledgements, language markers and the reply-language reminder are
  preserved. Only desktop references are adapted to a phone (desktop → phone, taskbar/windows → apps/screen).
- **Chat screen** — native chat list, sprite stage, speech bubble, mic button, settings.
- **Sprite animation** — original `idle`, `blink`, `blink_glitch`, `grin`, `glitch`, `sleep`, `wake`, `mouth`,
  `dangle`, `lay`, `lay_awake`, `lay_talk`, `lay_talk2`, `perch` and `perch_*` strips, frame-accurate, with
  random blinks/grins, entrance glitch and talking mouth. Bitmaps are kept in an
  `LruCache` and evicted on `onTrimMemory`.
- **Floating overlay** — `TYPE_APPLICATION_OVERLAY` window above other apps; drag to move, tap to open the chat
  panel, long-press for the full app. Optional roaming with glitch teleports. Position is remembered.
- **Persistent memory** — SQLite (`SQLiteOpenHelper`, WAL): facts, chat turns and summarized episodes.
  Explicit "remember that…" commands, first-person fact extraction, relevant-fact retrieval, and background
  summarization/archiving of old turns.
- **AI brain** — OpenAI-compatible HTTP API. Presets for OpenAI, OpenRouter, Groq, Ollama (LAN), LM Studio (LAN)
  and any custom/local endpoint. Cleartext HTTP is enabled for LAN model servers.
- **Offline fallback** — if no endpoint is configured, Capri still replies with persona-consistent lines; an
  endpoint failure produces an in-character error instead of crashing.
- **Voice input** — Android `SpeechRecognizer` (system recognition service), partial results, language follows
  the app language setting.
- **Voice output** — Android system TTS by default, with pitch/rate; optional remote GPT-SoVITS endpoint for the
  original cloned voice.
- **Mobile actions** — alarms, timers, app launch, web search, music links, volume, Wi-Fi/Bluetooth/settings,
  sleep/wake. Deterministic intent routing runs before the model.
- **Settings** — identity, reply language, brain endpoint/model/key, temperature/tokens, TTS, overlay size and
  roaming, memory clearing, HyperOS setup shortcuts (overlay permission, battery optimization, notifications,
  autostart).
- **Secure API key** — AES/GCM encrypted with a key generated in Android Keystore; never stored in plain
  SharedPreferences.
- **Lifecycle** — foreground service notification, boot/package-replaced receiver, screen-on/off animation
  pause, low-memory bitmap eviction.

## What is limited or device-only

Not verifiable in this build environment (no phone/emulator attached):

- Overlay permission flow, draggable overlay and panel positioning on HyperOS.
- Microphone permission, speech recognition availability/quality.
- System TTS voices and the optional GPT-SoVITS audio path.
- HyperOS background killing, autostart, battery optimization and notification behavior.
- Real network calls to OpenAI/Ollama/etc. from the phone (the HTTP client compiles and was unit-reviewed, but
  no live key/server was used here).

Known design limits:

- **No bundled on-device LLM.** "Local model" means an OpenAI-compatible server on your LAN or `localhost`
  (Ollama, LM Studio, llama.cpp server). Running a GGUF model inside the APK is not implemented.
- Speech recognition depends on a system recognition service (usually Google). Some de-Googled ROMs have none.
- The remote GPT-SoVITS call targets the common `/tts` JSON API; exact field names vary between servers.
- Memory extraction is intentionally rule-based English (same style as the original project's lightweight
  memory), while persona replies follow the selected conversation language.
- No streaming responses; each turn is one HTTP request.
- `minSdk 26`, `targetSdk 35`, package `com.capri.mobile`.

## First-run checklist on HyperOS (device-only)

1. Install the APK, open Capri once and grant microphone and notification permissions.
2. Settings → **Display over other apps** → allow. Then flip **Floating overlay** on (main screen or Settings).
3. Settings → **Battery** → allow unrestricted; Settings → **Autostart** → allow Capri (HyperOS kills
   background services otherwise).
4. Tap her sprite: the quick chat panel opens; drag to move; long-press for the full app.
5. Settings → **Brain** → pick a preset or paste an OpenAI-compatible base URL and model, then Save.
6. Without a brain she answers offline; with one she uses the model, memory and intents.

## Build

Requirements: JDK 17 and Android SDK platform 35 / build-tools 35.0.0.

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The build is plain Android Views, no AndroidX, no Compose, no image libraries. Only JUnit is a test dependency.

This workspace is an aarch64 Android host, so its local build uses an x86_64 `aapt2` under `qemu-x86_64-static`
via `android.aapt2FromMavenOverride`. That override lives in `~/.gradle/gradle.properties` and is **not** needed
on a normal x86_64/arm64 Linux or macOS machine with a native SDK. The GitHub Actions workflow in
`.github/workflows/android.yml` builds and tests on a standard x86_64 runner.

## Permissions

- `INTERNET` — AI endpoint, web search and remote TTS.
- `SYSTEM_ALERT_WINDOW` — floating overlay (user-granted).
- `RECORD_AUDIO` — speech recognition (user-granted).
- `POST_NOTIFICATIONS` — foreground-service notification (Android 13+).
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` — keep the overlay alive.
- `RECEIVE_BOOT_COMPLETED` — restore the overlay after reboot if it was on.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — HyperOS setup shortcut.

## Licenses and artwork

- Code: MIT License, © 2026 yume_mud (Capri-cmd). See `LICENSE`.
- Capri character design/artwork: drawn for Capri by **xilia_world00**; included from the upstream project with
  attribution. **The MIT code license does not cover the artwork.** Do not redistribute the art separately.
  See `ART-CREDITS.md`.
- Share Tech Mono font: SIL Open Font License.
- Capri is a non-commercial fan project. Cyberpunk and its characters are trademarks of CD PROJEKT RED. This
  app is not affiliated with or endorsed by them.
