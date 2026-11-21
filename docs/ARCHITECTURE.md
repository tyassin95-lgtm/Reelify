# Reelify architecture

> **2.0 additions** (details in [`REELIFY_V2_DESIGN.md`](REELIFY_V2_DESIGN.md)):
> `core/template` (ReelTemplate/ShotRecipe models, BuiltInTemplates, ProductionPlanner,
> TemplateRepository), `core/content` (GeneratedContentNormalizer, IraqiStyleGuide +
> DialectChecker, TopicLibrary, ContentRepository), `integration/openai/ScriptWriter` (+
> ScriptDtos, ScriptPrompts, ScriptValidator), `core/camera/CameraFaceMapping`,
> `feature/flow` (four-stage screens), `feature/recording/CameraComponents`
> (CameraOverlayScaffold, TeleprompterPanel), `core/designsystem/ReelComponents`.
> Room schema **v2** (`MIGRATION_1_2`, additive): `projects.mode/templateId/templateVersion/
> templateJson/overridesJson/scriptJson`, tables `custom_templates`, `style_examples`,
> `topic_history`. No destructive fallback is configured.

Single Gradle module (`:app`) organised by layer and feature. A manual DI container
(`AppContainer`) wires everything; ViewModels are created through `rfViewModel { container -> … }`.

```
app/src/main/java/app/reelify/
├── AppContainer.kt, ReelifyApp.kt, MainActivity.kt
├── navigation/            Routes + NavHost (string routes, animated transitions)
├── core/
│   ├── model/             Domain model: Project, ReelSection (stable ids), MediaClip, EditSettings,
│   │                      CaptionTrack/Style, BrandProfile, Treatment, enums (ReelDuration, ShotType…)
│   ├── database/          Room entities/DAOs (projects, sections, section_revisions, clips,
│   │                      treatments, terminology) — nested settings stored as typed JSON columns
│   ├── data/              ProjectRepository (serialised writes, revisions/undo, lock-respecting plan
│   │                      application), LibraryRepository (+ TreatmentSeed), SettingsRepository (DataStore)
│   ├── security/          KeystoreSecretStore (AES‑GCM, Android Keystore), CredentialRepository
│   ├── storage/           MediaStorage: per-project dirs, persisted URI grants or managed copies,
│   │                      relink checks, orphan cleanup, usage
│   ├── media/             MediaProbe (decoder-capability check), Thumbnails, MediaImporter
│   ├── media/engine/      ★ Rendering engine (see below)
│   ├── audio/             Dsp.kt (FFT, SpectralDenoiser, Biquad, Compressor, Limiter, GainEnvelope),
│   │                      AudioAnalyzer (MediaCodec PCM → loudness envelope), SpeechAnalysis
│   ├── captions/          CaptionBuilder (segmentation/edit/emphasis), CaptionRemapper (source
│   │                      anchoring), TerminologyCorrector, SrtWriter
│   ├── camera/            CameraController (CameraX + Camera2 interop), BeautySurfaceProcessor (EGL)
│   ├── pipeline/          AutoEditor (staged auto-edit), ExportManager + ExportService (foreground)
│   ├── timing/            TimingEstimator (word-rate + pauses → Timing Fit)
│   ├── policy/            ClaimChecker (EN/AR medical-claim content policy)
│   ├── library/           SoundLibrary catalogue (bundled CC0 audio)
│   └── designsystem/      Theme (palette, Manrope/Tajawal), custom Compose components
├── integration/
│   ├── openai/            OpenAiClient (Responses API), PromptTemplates (versioned), AiSchemas
│   │                      (strict JSON schemas), PlanValidator + AiMappers, ReelDirector (use cases),
│   │                      EditGuidanceApplier (declarative EDL → EditSettings)
│   ├── deepgram/          DeepgramClient (nova-3 pre-recorded, word timestamps, keyterms)
│   └── common/            AiError taxonomy, OkHttp helpers (no logging interceptor)
└── feature/               home, projects, studio, settings, create, script, review (checklist,
                           footage, trim), recording, autoedit, preview, editor, export, common
```

## Data model and persistence

* A `Project` owns exactly **three `ReelSection`s** created up front with stable ids (`sec-<uuid>`).
  AI output never chooses ids; the director maps sections by index onto the existing ids.
* `ProjectRepository.applyPlan` never overwrites **locked** sections; `replaceSection` writes a
  `section_revisions` snapshot first, so every AI or manual change is undoable (`undoSection`).
* `MediaClip`s reference a section and a role (`PRIMARY`, `BROLL`, `VOICEOVER`). Splitting a clip
  creates another clip on the same source with a different trim. Clip assignments are independent
  of the script, so rewriting a section's dialogue never detaches footage.
* All writes go through a mutex; screens autosave (debounced) on every change.

## AI pipeline

`ReelDirector` builds a grounded context (language, duration, per-section word budget from
`TimingEstimator`, treatment profile incl. "claims not to make", terminology, brand CTA, concept,
approved direction) and calls `OpenAiClient.structured()` with a **strict JSON schema**. Every
response is decoded and validated (`PlanValidator`: exactly three sections, indexes 1‑3, enum
values, non-empty dialogue where required, language ratio check for Arabic/English, direction
present). Invalid output triggers **one repair round** (the model sees its validation errors);
if that fails a typed `AiError.Malformed` is raised — malformed output is never rendered.
Editing requests return a declarative edit plan (`EditGuidanceDto`) that `EditGuidanceApplier`
applies deterministically; unknown values are ignored. The model never produces code.

## Rendering engine (`core/media/engine`)

```
ProjectBundle ──► TimelineResolver ──► ResolvedTimeline (video/dialogue segments, issues)
                        │                       │
RenderPreparer ─────────┴── captions remapped ──┼── speech ranges ─ clip loudness gains ─ music
                                                ▼
                                   CompositionFactory.build(purpose)
                                                ▼
      Media3 Composition:  seq0 video (per-segment EditedMediaItems, audio removed)
                           seq  dialogue (audio-only, gaps, VoiceChainProcessor)
                           seq  music (EnvelopeGainProcessor: fades + ducking)
                           seq  sfx (gaps + cues)
                           composition effects: FinishEffect (transitions/vignette/light)
                                                OverlayEffect(ReelBitmapOverlay) captions+branding
                                                MasterLimiterProcessor
                        │                                   │
           CompositionPlayer (preview)            Transformer (export / dialogue m4a)
```

* **Exact durations.** `TimelineResolver` lays each section out at exactly `total/3`. Primary clips
  play consecutively and are cut at the section end (overflow warning); B‑roll is a cutaway that
  replaces picture only (dialogue continues — J/L-cut behaviour via separate audio sequence);
  stills stretch with Ken Burns motion; voiceovers supply dialogue. Remaining gaps are a blocking
  issue unless the user explicitly picks a branded card. Talking-head video is never looped or
  slowed. Transitions are rendered *inside* the timeline, so they can't change total length.
* **Per-segment video effects:** user rotation → `Presentation` 1080×1920 crop-to-fill →
  `BeautyColorEffect` (shared GLSL; skin mask in YCbCr, bilateral smoothing with detail
  re-injection, tone evening, face-ellipse weighting and under-eye brightening from on-device ML Kit
  face tracks, mild face refinement warp, clarity/sharpen, grade) → `ReframeTransformation`
  (zoom/pan/flip, Ken Burns, slow push-in).
* **Transitions** (`FinishEffect`): crossfade/dissolve/slide/zoom blend from a GPU snapshot of the
  outgoing clip's last frame; flash and soft blur are centred on the cut.
* **Captions & branding** (`ReelOverlayRenderer`): drawn with Android `StaticLayout` (HarfBuzz
  shaping + bidi), bundled fonts with explicit Arabic fallback chains, word-level highlight using
  the layout's selection path (works for RTL), intro/section titles, lower third, watermark, CTA
  card, logo reveal, before/after disclosure — all inside configurable safe zones. The bitmap is
  only redrawn when its visual state changes.
* **Audio:** `VoiceChainProcessor` = high-pass → `SpectralDenoiser` (STFT, minimum-statistics
  noise tracking, decision-directed Wiener gain with floor + temporal smoothing; latency
  compensated) → speech EQ → compressor → per-clip normalization gain. Music uses a precomputed
  `GainEnvelope` (fade in/out, look-ahead ducking under word-timed speech). A master limiter
  prevents clipping. Everything is classical DSP — no generative audio.
* **Preview = export.** `PreviewViewModel` and `ExportManager` both call
  `RenderPreparer.inputs()` + `CompositionFactory.build()`; CompositionPlayer passes
  composition-relative timestamps to effects, exactly as Transformer does.
* **Export:** `ExportManager` runs in an application scope while `ExportService`
  (foreground, `mediaProcessing` on Android 15+, `dataSync` before) keeps the process alive; real
  progress from `Transformer.getProgress`, cancellation, free-space check, then `ExportValidator`
  (file, media-stack readability, dimensions, duration ±1 frame, AAC track, decodable frame,
  caption/overlay render counters) before saving to `Movies/Reelify` via MediaStore.

## Camera

`CameraController` binds Preview + VideoCapture(Recorder) + ImageAnalysis (ML Kit faces) with
graceful fallback (drops analysis, then effects) and exposes only supported controls (front/back,
torch, exposure range, 4K/1080p, 60 fps, AWB modes, zoom). `BeautySurfaceProcessor` renders the
preview stream through the same beauty shader compiled for `samplerExternalOES`. Recordings are
kept clean; the identical effect + parameters are applied at render time (non-destructive, editable
later, and identical in preview and output). The teleprompter is a Compose overlay and is never
part of the recording. Recording never auto-stops at the section length.

## Captions sync model

Deepgram transcribes the *extracted dialogue mix* (same trims as the reel). Each word is anchored
to its source clip/time (`CaptionRemapper.anchor`). When trims change, words are re-mapped through
the new timeline (`remap`), so captions stay synchronized without re-transcription; words whose
audio was trimmed away disappear.
