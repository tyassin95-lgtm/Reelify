# Limitations & Honest Verification Boundaries (Reelify 2.2)

This document records everything that could **not** be executed on physical hardware or live external APIs in this environment, along with architectural constraints of Reelify 2.2.

---

## 1. Environment Verification Boundaries (`Unverified` on Hardware / Live APIs)

* **Physical Android Device & Emulator Execution (`Unverified`)**:
  * No physical Android phone or running ADB emulator was connected during this build.
  * While `:app:testDebugUnitTest` (`195/195` non-skipped JVM + Robolectric tests), `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and `:app:assembleRelease` ran cleanly here, actual hardware execution of `TrimScreen`, `PreviewScreen`, CameraX capture, GL `BeautySurfaceProcessor`, hardware `TransformerRunner` encoding, and system Back navigation remains unverified until executed on a target phone per [`DEVICE_ACCEPTANCE_CHECKLIST.md`](DEVICE_ACCEPTANCE_CHECKLIST.md).
* **Live OpenAI & Deepgram API Calls (`Unverified`)**:
  * No OpenAI or Deepgram API keys exist in the build environment.
  * `ScriptWriter` (`ideas`, `generate`, `hooks`, `rewrite`, targeted section revision) was verified offline against `MockWebServer` fixtures and all 9 negative regression scenarios in `CreativeQualityEvaluationTest`, while `CreativeEvalHarness.runLiveEvaluation` was skipped. Live model adherence to narrative outlines and mixed Iraqi Arabic + English medical spelling must be spot-checked in-app with your OpenAI key.
* **Native Iraqi Clinical Review (`Human reviewed: Unverified`)**:
  * `MedicalGlossary` proclitic attachment rules (`بـ`, `للـ`, `وللـ`, `الـ`) and curated topics are unit-tested, but a native Iraqi dermatology/laser clinician has not yet scored live v2.2 generations using `docs/eval/CREATIVE_EVAL.md`.

---

## 2. Known Functional Constraints & Intentional Design Choices

* **Ephemeral Stage 1–2 `CreationDraftSession` Scope**:
  * `CreationDraftSession` lives in memory inside `AppContainer.creationSessions` plus URL query parameters on the `Routes.messageDraft` navigation route. If you exit back to Home before generating a script, the session is intentionally discarded with zero Room writes. If Android kills the background process *while sitting on Stage 2 (`MessageScreen`)*, the treatment, template, language, and duration restore cleanly from route arguments, while unsaved free-text notes typed into concept fields without persistence are lost (saved AI ideas via `"Save"` persist in Room immediately).
* **Optional Trim GL Preview Guard**:
  * Core `TrimScreen` deliberately keeps Media3 `VideoFrameProcessor` disabled (`isEffectFree == true`), previewing zoom/rotation/mirror via Compose `graphicsLayer`. Beauty and color look filters are always rendered identically during Preview Proxy generation and final Export; optional live GL preview inside Trim stays gated behind `"Optional adjustments"` with automatic fallback on OEM playback errors.
* **Cleopatra Identity Protection**:
  * Because `https://cleopatra-lasercenter.com/diamond-glow/` documents `DiamondGlow` at Cleopatra Laser Center rather than guaranteeing what every local clinic means by `"Cleopatra"`, the built-in `cleopatra` profile intentionally stays marked `needsConfiguration = true, identityConfirmed = false` until confirmed in Studio or Stage 2.
* **Release APK Signing**:
  * Because no clinic keystore (`keystore.properties` / `*.jks`) is checked into version control, `assembleRelease` outputs an unsigned R8-minified APK (`dist/Reelify-2.2.0-release-unsigned.apk`) ready for local signing before sideloading; `dist/Reelify-2.2.0-debug.apk` installs directly for debugging.
* **Automated Local Clinical Research Refresh**:
  * `ClinicalKnowledgeManager.auditLocalSources()` transparently inspects the bundled curated packs (`TreatmentKnowledgeCatalog`) and source dates instead of running background web scraping from the mobile client.
