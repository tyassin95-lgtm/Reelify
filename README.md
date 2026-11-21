# Reelify 2.2 — Native Clinical Reel Studio (Android)

Reelify (`2.2.0`, `versionCode = 4`) is a native, local-first Android studio (Kotlin + Jetpack Compose + Media3 + CameraX) for aesthetic skin & laser specialists:
**choose a treatment & template -> explore verified clinical topics or grounded AI ideas -> generate a script -> record or import three timed shots -> trim cleanly -> export a branded 30/45/60s vertical reel**.

---

## What Changed in Reelify 2.2

* **P0 Release Blocker — Effect-Free Core Clip Trimmer & Honest Duration**:
  * `TrimPlayerController` runs plain Media3 `ExoPlayer` with **zero default `setVideoEffects` calls** (`isEffectFree == true`), removing OpenGL `VideoFrameProcessor` initialization crashes during core trimming (`TrimScreen.kt`).
  * Framing preview (crop-zoom, rotation, mirror) renders cleanly via Compose `Modifier.graphicsLayer`.
  * `TrimBounds.validate` no longer fabricates a `500 ms` duration when metadata is missing (`durationKnown = false`, `TrimBounds.canSave` disabled until multi-step fallback probing via `MediaProbe.probe`, `MediaProbe.probeDurationFallback`, or `ExoPlayer STATE_READY` discovers a real duration).
  * Secondary adjustments (framing, audio volume/mute, clinical toggle, beauty filter overrides) are separated inside a collapsible `"Optional adjustments"` panel.
* **Grounded AI Ideas & Full Idea-to-Script Handoff (`ScriptWriter`, `IdeaValidator`, `CreativeTopic`)**:
  * Stage 2 AI idea generation feeds top curated benchmark topics + 5 underused `KnowledgeCluster`s from `ClinicalKnowledgeManager` instead of vague prompts, runs 4-stage `IdeaValidator` filtering/ranking, and supports **Use idea**, **More like this**, **Different angle**, **Save**, and **Generate 5 fresh ideas**.
  * Selected `CreativeTopic` metadata (patient question, objective, primary/supporting fact IDs, takeaway, source IDs) is preserved end-to-end into `ScriptContext`, `FactualBriefBuilder`, and `ScriptMeta.creativeTopic`.
* **Narrative Outlines, Cross-Section Anti-Repetition & Focused Clinical Briefs**:
  * `ScriptWriter.generate()` plans a `NarrativeOutlineDto` tailored to the active template's arc (`ScriptPrompts.templateNarrativeBlueprint`), enforces `ScriptValidator.validateCrossSectionCoherence()` (no repeated shots, no duplicated `"individual assessment"` disclaimers across sections, concrete Shot 3 payoff, topic focus isolation via `ClinicalKnowledgeManager.incompatibleTopicAreas`), and performs bounded section-level revision (`performTargetedSectionRevision`) when specific sections fail.
* **Mandatory English Medical Terminology in All Languages (`MedicalGlossary`, `Seed v3`)**:
  * Both English (`en`) and conversational Iraqi Arabic (`ar-IQ`) reels retain approved English Latin spellings for medical, anatomical, modality, wavelength, and device terms (`PicoStar`, `Aerolase`, `CFU Èlife`, `HIFU`, `Nd:YAG`, `Er:YAG`, `Melasma`, `Hyperpigmentation`, `Collagen`, `SMAS`, `1064 nm`, `650 microseconds`, etc.) across AI scripts, headlines, curated topics, Deepgram ASR correction (`TerminologyCorrector`), and Room seed v3 (`TreatmentSeed.CURRENT_SEED_VERSION = 3`).
* **Ephemeral Creation Sessions & Bulk Project Management**:
  * Stage 1 (`ChooseReelViewModel.startSession`) and Stage 2 (`MessageScreen`) hold an in-memory/route-restorable `CreationDraftSession` with **zero Room inserts** until script generation succeeds, persisting project + 3 sections + script atomically (`createFromTemplateWithScriptAtomically`).
  * `ProjectsTab` adds multi-selection and bulk deletion (`deleteMany`) plus a non-destructive cleanup card for empty legacy drafts.
* **Navigation & Preview Cache Hardening**:
  * Pressing Android Back on secondary tabs (`Projects`, `Studio`, `Settings`) returns to `Home` first (`MainScreen`), `ReelifyNavHost` uses calm directional slide/fade transitions and `DeletedProjectGuard`, and `PreviewProxyManager` hashes source file `length`/`lastModified` + `editVersion` and verifies MP4 decodability via `MediaMetadataRetriever`.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), [`docs/TESTING.md`](docs/TESTING.md), [`docs/LIMITATIONS.md`](docs/LIMITATIONS.md), [`docs/PROGRESS.md`](docs/PROGRESS.md), and [`docs/DEVICE_ACCEPTANCE_CHECKLIST.md`](docs/DEVICE_ACCEPTANCE_CHECKLIST.md).

---

## Requirements & Build

| Tool | Version Used |
|---|---|
| JDK | OpenJDK 21 (17+ required) |
| Android Gradle Plugin | 9.5.2 (built-in Kotlin) |
| Gradle | 9.9.2 (wrapper included) |
| Kotlin | 2.4.21, KSP 2.3.14 |
| compileSdk / targetSdk / minSdk | 37.2 / 36 / **29 (Android 10)** |

```bash
# Point Gradle at your Android SDK (untracked local file)
echo "sdk.dir=/path/to/Android/sdk" > local.properties

# Run JVM + Robolectric unit tests
./gradlew :app:testDebugUnitTest

# Build debug & unsigned release APKs
./gradlew :app:assembleDebug :app:assembleRelease
# Packaged outputs copied to dist/Reelify-2.2.0-debug.apk and dist/Reelify-2.2.0-release-unsigned.apk

# Instrumented tests (when a physical Android phone or emulator is connected)
./gradlew :app:connectedDebugAndroidTest
```

## API Credentials & Privacy Model

* **Zero backend, zero accounts, local-first storage.** Patient consent and local media privacy rules remain unchanged: video/image frames are never sent to OpenAI for scriptwriting.
* **OpenAI** (default model `gpt-6.2-sol`, configurable in *Settings -> AI Connections*) handles script/idea text via `POST /v1/responses` with strict `json_schema`.
* **Deepgram** (`nova-3`, `ar-IQ` / `en`) transcribes extracted dialogue audio when configured; runtime keys are encrypted at rest via Android Keystore AES-256-GCM.
