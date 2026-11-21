# Reelify 2.1 — template-first reel studio (Android)

Reelify (`2.1.0`, `versionCode = 3`) is a native, local-first Android studio (Kotlin + Jetpack Compose) for aesthetic skin & laser specialists:
**choose a template → pick a clinical topic → film three timed shots → export a branded 30/45/60s reel**.

**New in 2.1:**
- **Reliable Trim & Preview playback**: `TrimScreen` uses standard Media3 `ExoPlayer` (`TrimPlayerController`) with GPU-effect fallback and validated bounds (`TrimBounds`); finished-reel preview renders deterministic proxy MP4s (`PreviewProxyManager`) for smooth playback without multi-track `CompositionPlayer` instability, plus explicit `EditedMediaItem.durationUs` across all Media3 sequences.
- **Clinical knowledge & 480+ specific topics**: Verified knowledge packs (`TreatmentKnowledgeCatalog`) for **CFU Èlife** (Biotec Italia HIFU — explicitly not laser), **PicoStar** (Asclepion `1064/532 nm` picosecond), **DiamondGlow®** (with explicit unconfirmed-identity safeguard on **Cleopatra**), **Aerolase**, **Dermablate**, **LaseMD Ultra**, and **Diamond Peeling**, with non-destructive versioned Room upgrades and **480** treatment-and-template-specific EN/Iraqi-Arabic topics (`TopicLibrary`).
- **Smarter AI writing & teleprompter pacing**: Factual briefs (`FactualBriefBuilder`), anti-filler checks, Instagram/Snapchat selection, `"Save line to my voice guide"` + `"Sound like me"`, layout-measured `TeleprompterPacer` keeping short scripts stationary and syncing long scripts to shot duration, and interactive voice-pace calibration.

**2.0 foundation:** filming and editing come from **versioned production templates** (six built-in formats).
The AI writes only words — fresh angles, three distinct hooks, dialogue and headlines, with a
dedicated Iraqi Arabic scriptwriter. See [`docs/REELIFY_V2_DESIGN.md`](docs/REELIFY_V2_DESIGN.md).

* **No backend, no account.** Projects, media and settings stay on the device. The only network
  calls go directly from the phone to **OpenAI** (Responses API, text only) and **Deepgram**
  (dialogue audio for captions) — and only when you configure keys.
* **Local media engine.** Rendering, beauty/colour, transitions, captions, branding, denoising and
  mixing all run on-device with AndroidX Media3 + OpenGL ES.
* **English and natural Iraqi Arabic** scripting, transcription (`ar-IQ`) and correctly shaped,
  right‑to‑left burned‑in captions.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), [`docs/TESTING.md`](docs/TESTING.md),
[`docs/LIMITATIONS.md`](docs/LIMITATIONS.md) and [`docs/PROGRESS.md`](docs/PROGRESS.md).

---

## Requirements

| Tool | Version used |
|---|---|
| JDK | 17+ (built with OpenJDK 21) |
| Android Gradle Plugin | 9.5.2 (built‑in Kotlin) |
| Gradle | 9.9.2 (wrapper included) |
| Kotlin | 2.4.21, KSP 2.3.14 |
| compileSdk | 37.2 (`platforms;android-37.2`), targetSdk 36, **minSdk 29 (Android 10)** |
| Android Studio | a 2026 release that supports AGP 9.5 |

Key libraries: Compose BOM 2026.11.01, Media3 1.11.1 (Transformer, CompositionPlayer, effects),
CameraX 1.7.0, Room 2.8.5, DataStore 1.3.0, OkHttp 5.5.1, kotlinx.serialization 1.13.0,
ML Kit face detection 16.1.7 (on-device), Coil 3.6.3.

## Build

```bash
# 1. Point Gradle at your SDK (untracked file)
echo "sdk.dir=/path/to/Android/sdk" > local.properties

# 2. Debug APK
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# 3. Unit tests (JVM + Robolectric)
./gradlew :app:testDebugUnitTest

# 4. Instrumented tests (device or emulator connected)
./gradlew :app:connectedDebugAndroidTest
```

A prebuilt debug APK is attached to the delivery (see `dist/` notes in `docs/PROGRESS.md`).

### Private release APK

Release builds are minified (R8) and signed only when a `keystore.properties` file exists in the
project root (it and any `*.jks` are git‑ignored — never commit them):

```properties
storeFile=reelify-release.jks
storePassword=…
keyAlias=reelify
keyPassword=…
```

```bash
keytool -genkeypair -v -keystore reelify-release.jks -alias reelify -keyalg RSA -keysize 4096 -validity 10000
./gradlew :app:assembleRelease     # → app/build/outputs/apk/release/app-release.apk
```

Keep the keystore safe: Android only installs updates signed with the same key.

## API credentials

Reelify works fully offline for recording, importing, editing and exporting. AI scripting needs
an **OpenAI** key; synchronized captions need a **Deepgram** key.

### Method A — embedded at build time (private sideloaded builds)

Add to `local.properties` (untracked) **or** export as environment variables before building:

```properties
REELIFY_OPENAI_API_KEY=sk-...
REELIFY_DEEPGRAM_API_KEY=...
```

They are compiled into `BuildConfig`. **Anyone who has the APK can extract them.** Use dedicated
keys with a low spending limit, and never distribute the APK publicly.

### Method B — in the app

*Settings → AI Connections*: paste, replace, test or remove each key. Runtime keys are encrypted
with an AES‑256‑GCM key held in the **Android Keystore**. This protects keys at rest; it is not a
defence against a rooted/compromised device.

**Priority:** a key entered in the app always wins over an embedded key. Keys are never logged:
there is no HTTP logging interceptor, and provider error messages are redacted before display.

### Models

* OpenAI: default model id **`gpt-6.2-sol`**, changeable under *AI Connections → Advanced* without
  rebuilding. Requests use `POST /v1/responses` with strict `json_schema` structured output.
* Deepgram: **`nova-3`** pre-recorded (`POST /v1/listen`), `language=ar-IQ` for Iraqi Arabic or
  `en` for English, `smart_format`, word timestamps, and optional `keyterm` prompting from your
  terminology dictionary (automatically retried without keyterms if a model/language rejects it).

## Install the APK

1. Copy `app-debug.apk` to the phone (or `adb install -r app-debug.apk`).
2. Allow "Install unknown apps" for your file manager when prompted.
3. Open **Reelify**. Grant camera/microphone when you first record (importing works without them).
4. Android 13+: allow notifications so long exports can show progress in the background.

The debug build installs as `app.reelify.debug` and can live alongside a release build.

## Configure branding

*Studio → Branding*: import a transparent PNG logo, set brand/clinic name, presenter name and
title (lower‑third), Instagram/Snapchat handles, booking details, CTA text (English and Arabic),
colours, font, lower‑third and outro styles, logo position and watermark opacity, and the
before/after disclosure text. The live preview is rendered by the same engine used for exports.
Five templates are built in (Luxury Minimal, Clinical Premium, Cinematic Burgundy, Modern Social,
Clean Educational) and you can save your own.

## Music and assets

* 10 bundled music tracks and 9 SFX are **original, procedurally generated, CC0** audio produced
  by [`tools/generate_audio.py`](tools/generate_audio.py) (`python3 tools/generate_audio.py`,
  needs numpy + ffmpeg). No commercial or third‑party recordings are bundled.
* Import your own music in *Studio → Music & SFX → Import* (copied into the app); it then appears
  in every reel's Music panel. Only import music you have rights to publish.
* Fonts: Manrope, Tajawal, Playfair Display, Nunito, Amiri (SIL OFL). Licences are viewable in
  *Settings → Licenses & privacy*.

## Using Reelify (summary)

1. **Choose your reel** — treatment, English / Iraqi Arabic, 30/45/60 s, template (Expert
   Explains, Myth Buster, Treatment Showcase, Three Things to Know, Common Patient Question,
   Problem / Approach). Your last choices are remembered.
2. **Choose your message** — tap a suggested topic, *Surprise me with a fresh idea*, or type one
   line. The script appears straight away: pick one of three hooks, use the chips (*Try different
   hook*, *More engaging*, *More natural Iraqi*, *Shorten*, *More professional*), edit / lock /
   undo any shot, then **Use This Script**.
3. **Film your reel** — three shot cards with one instruction each. Record (camera opens with the
   template's camera, teleprompter and beauty preset) or import; voiceover shots take footage +
   voice. Accepting a take returns to the list.
4. **Preview & export** — Reelify edits automatically with the template's captions, music, SFX,
   transitions, colour and branding (your own changes always win), then shows a full preview:
   **Export**, **Adjust** (captions, music, beauty, colour, branding, replace/trim shot) or
   **Advanced** (the full timeline editor).

Studio → *Iraqi style guide*: approve or correct the reference lines that teach the AI your
voice. Studio → *Templates*: customise a copy of any template. Projects created with Reelify 1.x
open in their original (legacy) workflow with their settings unchanged.
