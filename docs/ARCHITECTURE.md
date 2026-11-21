# Reelify architecture

> **2.2 additions** (details in [`REELIFY_2_2_AUDIT.md`](REELIFY_2_2_AUDIT.md), [`REELIFY_2_2_PRODUCT_DESIGN.md`](REELIFY_2_2_PRODUCT_DESIGN.md), [`TRIM_CRASH_INVESTIGATION.md`](TRIM_CRASH_INVESTIGATION.md), [`AI_CREATIVE_PIPELINE.md`](AI_CREATIVE_PIPELINE.md), [`MEDICAL_TERMINOLOGY_POLICY.md`](MEDICAL_TERMINOLOGY_POLICY.md)):
> Effect-free default `TrimPlayerController` (`ExoPlayer` with zero default `setVideoEffects`), Compose `graphicsLayer` trim framing preview, honest `TrimBounds.durationKnown` + `canSave` + `MediaProbe.probeDurationFallback` (never inventing `500 ms`), `PreviewProxyManager` source-file mtime/length + `editVersion` fingerprinting & `MediaMetadataRetriever` decodability validation (`isProxyValid`), `MedicalGlossary` canonical English medical terminology enforcement across EN/`ar-IQ`, `ClinicalKnowledgeManager` (`KnowledgeCluster` selection, provenance isolation, `incompatibleTopicAreas`), `CreativeTopic` + `IdeaValidator` 4-stage pipeline, `NarrativeOutlineDto` + `ScriptValidator.validateCrossSectionCoherence` + `ScriptWriter.performTargetedSectionRevision`, ephemeral Stage 1–2 `CreationDraftSession` + `ProjectRepository.createFromTemplateWithScriptAtomically` + bulk `deleteMany`, secondary-tab `BackHandler`, and `DeletedProjectGuard`.

> **2.1 additions** (details in [`REELIFY_2_1_CRASH_AUDIT.md`](REELIFY_2_1_CRASH_AUDIT.md), [`TREATMENT_KNOWLEDGE_SOURCES.md`](TREATMENT_KNOWLEDGE_SOURCES.md), [`CLINIC_CONTENT_RESEARCH.md`](CLINIC_CONTENT_RESEARCH.md)):
> `core/media/TrimBounds`, `core/media/engine/PreviewProxyManager`, clipped `TemplateFrameGeometry` artwork, `TreatmentKnowledgeCatalog`, `catalog/*TopicCatalog` (`480` EN/ar-IQ topics), non-destructive versioned seed upgrades, `ShotType`-aware budgets in `TimingEstimator`, `TeleprompterPacer` in `feature/recording/CameraComponents`, and live voice pace calibration in `SettingsScreens`.

> **2.0 foundation** (details in [`REELIFY_V2_DESIGN.md`](REELIFY_V2_DESIGN.md)):
> `core/template`, `core/content` (`IraqiStyleGuide` + `DialectChecker`), `integration/openai/ScriptWriter`, `core/camera/CameraFaceMapping`, `feature/flow`, Room schema **v2** (`MIGRATION_1_2`).

Single Gradle module (`:app`) organised by layer and feature. A manual DI container (`AppContainer`) wires repositories, media engines, AI clients, and the ephemeral `creationSessions` store.

```
app/src/main/java/app/reelify/
├── AppContainer.kt, ReelifyApp.kt, MainActivity.kt
├── navigation/            Routes (including messageDraft) + ReelifyNavHost + DeletedProjectGuard
├── core/
│   ├── model/             Domain model: Project, ReelSection, MediaClip, EditSettings (editVersion),
│   │                      CaptionTrack/Style, BrandProfile, Treatment, ScriptMeta (creativeTopic/sourceIds)
│   ├── database/          Room entities/DAOs (v2 additive schema; no destructive fallback)
│   ├── data/              ProjectRepository (atomic template+script creation, bulk deleteMany, empty legacy detection),
│   │                      LibraryRepository (+ TreatmentSeed v3 merge upgrade), SettingsRepository
│   ├── content/           MedicalGlossary, ClinicalKnowledgeManager, CreativeTopic, TreatmentKnowledgeCatalog,
│   │                      FactualBriefBuilder, TopicLibrary, IraqiStyleGuide, DialectChecker, GeneratedContentNormalizer, ContentRepository
│   ├── security/          KeystoreSecretStore (AES-GCM), CredentialRepository
│   ├── storage/           MediaStorage (per-project managed media + bulk cleanup)
│   ├── media/             MediaProbe (+ probeDurationFallback), TrimBounds, Thumbnails, MediaImporter
│   ├── media/engine/      TimelineResolver, RenderPreparer, CompositionFactory, PreviewProxyManager, TransformerRunner, ExportValidator
│   ├── audio/             Dsp.kt, AudioAnalyzer, SpeechAnalysis
│   ├── captions/          CaptionBuilder (+ English medical TerminologyCorrector), CaptionRemapper, SrtWriter
│   ├── camera/            CameraController, BeautySurfaceProcessor
│   ├── pipeline/          AutoEditor, ExportManager + ExportService
│   ├── timing/            TimingEstimator
│   ├── policy/            ClaimChecker
│   ├── template/          BuiltInTemplates, ProductionPlanner, TemplateRepository
│   └── designsystem/      Theme, components, TemplateFrameGeometry
├── integration/
│   ├── openai/            OpenAiClient, ScriptDtos (+ NarrativeOutlineDto), ScriptPrompts (template arcs + v2.2 policy),
│   │                      ScriptValidator (+ cross-section coherence), IdeaValidator, ScriptWriter, ReelDirector, EditGuidanceApplier
│   └── deepgram/          DeepgramClient
└── feature/               home (MainScreen + BackHandler), projects (multi-select bulk delete), flow (Choose, Message, Script, Film, CreationDraftSession),
                           review (effect-free TrimScreen + collapsible Optional adjustments), recording (TeleprompterPacer), preview, editor, export, studio, settings
```

## Key Architectural Guarantees

* **Zero Empty Draft Persistence**: Stage 1 (`ChooseReelViewModel.startSession`) creates an in-memory/route-restorable `CreationDraftSession`. Room persistence happens in a single transaction (`ProjectRepository.createFromTemplateWithScriptAtomically`) only after `ScriptWriter.generate()` validates the script.
* **Effect-Free Core Trimmer**: `TrimPlayerController` runs plain `ExoPlayer` with zero default `setVideoEffects` invocations; Compose `graphicsLayer` handles zoom/rotation/mirror preview so OEM Media3 GL surface issues cannot crash trimming.
* **Canonical English Terminology**: `MedicalGlossary` enforces English Latin medical/device/measurement spellings across prompts, idea validation, script validation, ASR correction, and RTL dominance classification.
* **Grounded Clinical Topics & Anti-Repetition**: `ClinicalKnowledgeManager` isolates single-subject clusters and prevents indication cross-contamination; `ScriptValidator.validateCrossSectionCoherence` rejects duplicated shots, repeated disclaimers, missing Shot 3 payoffs, and identity errors, triggering bounded section-level revision.
