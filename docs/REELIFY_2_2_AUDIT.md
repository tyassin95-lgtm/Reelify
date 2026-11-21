# Reelify 2.2 — Engineering & Product Audit

## Scope

This audit records every systemic reliability, data-lifecycle, clinical-knowledge, terminology, and AI-intelligence flaw identified in Reelify 2.1 (`3dd30faf671db9ea4fff580f5006faf48a4879e5`) and maps each defect to the concrete structural fix implemented in Reelify 2.2 (`versionCode = 4`, `versionName = "2.2.0"`).

---

## Findings & Root Causes

### 1. Core Trimmer GL Coupling & Fabricated Duration (`P0 Release Blocker`)
- **Finding A (`TrimScreen.kt`, `PreviewPlayer.kt`)**: Even after moving from `CompositionPlayer` to `ExoPlayer`, `TrimScreen` built Media3 GL effects (`Presentation`, `BeautyColorEffect`, color LUTs, crop/rotate matrices) and called `player.setVideoEffects(...)` on initial clip load. Any device/driver snag in `VideoFrameProcessor` crashed or froze basic trimming.
- **Finding B (`TrimBounds.kt`)**: When container probing returned `<= 0L`, `TrimBounds.validate()` fabricated a `500 ms` fallback duration (`FALLBACK_MIN_SOURCE_MS`), letting users scrub and save corrupted clip bounds.
- **Fix**: `TrimPlayerController.load()` never invokes `setVideoEffects()` (`isEffectFree == true` by default); framing zoom/rotation/mirror preview uses Compose `Modifier.graphicsLayer` on `TrimPlayerSurface`; `ValidatedTrimBounds.durationKnown` disables editing/saving until honest duration discovery (`MediaProbe.probe`, `MediaProbe.probeDurationFallback`, or `ExoPlayer STATE_READY`) yields a real positive duration.

### 2. Weak AI Ideas & Lossy Idea-to-Script Handoff
- **Finding (`ScriptWriter.kt`, `MessageScreen`)**: Generating fresh ideas sent an almost empty `(propose fresh ideas)` placeholder without curated benchmarks or structured knowledge clusters, discarded question/fact/takeaway metadata after Stage 2, and persisted no user-saved concepts or richer anti-repetition history.
- **Fix**: Unified `CreativeTopic`, `ClinicalKnowledgeManager` underused `KnowledgeCluster` selection, top-4 curated benchmark injection, `IdeaValidator` 4-stage pipeline, full `CreativeTopic` preservation into `ScriptContext` and `ScriptMeta.creativeTopic`, local saved ideas, and `"More like this"` / `"Different angle"` / `"Save"` actions.

### 3. Script Repetition & Generic Payoffs
- **Finding (`ScriptPrompts.kt`, `ScriptValidator.kt`)**: Generated scripts frequently introduced the device twice, repeated `"results vary / consult an expert"` across multiple shots, or spilled unrelated device indications (such as CFU hyperhidrosis or body contouring) into facial lifting scripts.
- **Fix**: Template-specific `templateNarrativeBlueprint`, required `narrative_outline` planning step (`NarrativeOutlineDto`), `ScriptValidator.validateCrossSectionCoherence()` (checking Jaccard similarity `> 0.45f`, `>= 2` shared trigrams, repeated consultation disclaimers, Shot 2/3 re-intros, missing Shot 3 payoff, and off-topic bleed), plus bounded `performTargetedSectionRevision()`.

### 4. Arabic Transliterations of Medical Terminology
- **Finding (`TreatmentSeed.kt`, `DialectChecker`, `TerminologyCorrector`)**: Prior v2.x releases allowed or prescribed Arabic transliterations (`بيكوستار`, `إيروليز`, `هايفو`, `كولاجين`, `الكلف`) in scripts, seeds, and ASR correction.
- **Fix**: Created `MedicalGlossary.kt` enforcing canonical English Latin spelling across all generated outputs, ASR corrections (`TerminologyCorrector`, `AutoEditor`), seed terminology (`CURRENT_SEED_VERSION = 3`, `arabic = ""`), and mixed-script RTL direction (`GeneratedContentNormalizer.isArabicDominant`).

### 5. Knowledge Provenance Bug & Off-Focus Brief Padding
- **Finding (`TreatmentKnowledge.kt`)**: `TreatmentKnowledgeCatalog.resolveFactOrQuestion` placed `q.factIds` inside `VerifiedFact.sourceIds`, conflating mechanism fact IDs with bibliographic `KnowledgeSource` IDs (`src_*`). Meanwhile `FactualBriefBuilder.build` padded briefs with top mechanism/application facts without filtering unrelated anatomical zones.
- **Fix**: Added `supportingFactIds` on `VerifiedFact` and `sourceIds` on `VerifiedQuestion` so `resolveFactOrQuestion` keeps `sourceIds` (`src_*`) and `supportingFactIds` (`*_mech_*`) strictly separate, and filtered unrelated areas using `ClinicalKnowledgeManager.incompatibleTopicAreas`.

### 6. Premature Draft Creation & Missing Bulk Project Management
- **Finding (`ChooseReelViewModel`, `ProjectsTab.kt`)**: Stage 1 (`Choose Reel`) immediately inserted an empty `Project` row into Room even if the user pressed Back without generating a script, leaving dozens of empty drafts; `ProjectsTab` lacked multi-select batch deletion.
- **Fix**: Introduced ephemeral in-memory/route-restorable `CreationDraftSession` across Stage 1–2, atomic `ProjectRepository.createFromTemplateWithScriptAtomically(...)` on script generation success, multi-select bulk deletion (`ProjectRepository.deleteMany`) with clean confirmation, and a non-destructive banner to select/clean legacy empty drafts (`findEmptyLegacyDrafts`).

### 7. Navigation Back Semantics, Motion Polish & Proxy Freshness
- **Finding**: Android system Back on secondary tabs (`Projects`, `Studio`, `Settings`) exited the app instead of returning to `Home`; deep stale back-stack entries needed deletion safety; `PreviewProxyManager.fingerprint` ignored source-file mtime/size and `editVersion` and didn't verify decodability before cache hit.
- **Fix**: Added `BackHandler(enabled = tab != 0) { tab = 0 }` in `MainScreen`, `DeletedProjectGuard` in `ReelifyNavHost`, restrained slide/fade transitions, file-metadata + `editVersion` fingerprinting, and `MediaMetadataRetriever` decodability verification in `PreviewProxyManager.isProxyValid()`.

---

## Verification Boundary Summary

All structural fixes above are `Implemented` in Kotlin source and exercised in JVM/Robolectric unit tests and compiled Android instrumented tests. Hardware camera capture, live OpenGL shader rendering, real-device `TrimScreen` playback, and live OpenAI/Deepgram API responses remain `Unverified` until tested on a physical Android phone with credentials.
