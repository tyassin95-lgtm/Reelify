# Testing & Verification Report

All numbers and checks below reflect commands directly executed in this repository environment. Unexecuted hardware or live-API checks are labeled explicitly.

---

## 1. Reelify 2.2 JVM & Robolectric Unit Tests — `./gradlew :app:testDebugUnitTest`

* **Outcome (`Unit tested`)**: **196 total tests across 30 suites — 195 passed, 1 skipped, 0 failed, 0 errors**.
  * Skipped test (`Unverified — requires API key`): `CreativeEvalHarness.runLiveEvaluation` (skipped via `assumeTrue` because `REELIFY_OPENAI_API_KEY` is unset).
* **New / Updated Suites in Reelify 2.2**:
  * `content.MedicalGlossaryTest` (`7/7` passed) — prohibited Arabic transliteration/translation cleanup, proclitic attachment (`بـ`, `للـ`, `وللـ`), everyday Iraqi non-corruption (`يكلف`, `تصبغات`), validator detection, mixed `ar-IQ` + English headline RTL dominance check, seed `arabic == ""` invariant, normalizer integration.
  * `flow.EphemeralCreationAndBulkDeleteTest` (`4/4` passed, Robolectric + Room) — Stage 1 `startSession` + Stage 2 browsing/failed generation leaves Room project count untouched; atomic `createFromTemplateWithScriptAtomically` preserves `Project`, 3 sections, full `CreativeTopic`, and `sourceIds`; `deleteMany` removes only selected projects & directories while preserving external files; `findEmptyLegacyDrafts` detects legacy empty shells accurately.
  * `eval.CreativeQualityEvaluationTest` (`10/10` passed) — knowledge provenance separation (`src_*` vs `supportingFactIds`), CFU facial brief focus isolation (`hyperhidrosis`/`body_contouring` excluded), underused `KnowledgeCluster` selection, and all **9 bad-output fixtures** (repetition, promotional filler, off-focus indication bleed, `CFU = laser` falsehood, unsafe guarantee/cure claim, generic/nonsensical AI ideas, prohibited Arabic medical terms, formal MSA, missing Shot 3 payoff).
  * `navigation.NavigationAndBackSemanticsTest` (`2/2` passed, Robolectric) — ephemeral draft route encoding without DB writes, `TrimPlayerController` starting cleanly in effect-free mode (`isEffectFree == true`) even with non-identity beauty/color settings.
  * Updated v2.2 contract assertions in `review.TrimBoundsTest` (`4/4` passed — honest `durationKnown = false`, `canSave == false` on `0L` metadata, no `500 ms` fabrication), `captions.CaptionsTest` (`9/9` passed — English canonical ASR correction for `ar-IQ`), `content.DialectAndTopicsTest` (`9/9` passed), `ai.ScriptWriterTest` (`15/15` passed — `MockWebServer`), and `e2e.Reelify21ScenariosTest` (`3/3` passed — `PicoStar`, `CFU Èlife`, `Aerolase` scenarios).
* **Full Unit Test Breakdown (all 30 suites)**:
  * `ai.DeepgramClientTest` (`9/9`), `ai.OpenAiClientTest` (`11/11`), `ai.ReelDirectorTest` (`8/8`), `ai.ScriptWriterTest` (`15/15`), `audio.AudioProcessorTest` (`2/2`), `audio.DspTest` (`8/8`), `beauty.BeautyCalibrationTest` (`8/8`), `captions.CaptionsTest` (`9/9`), `content.DialectAndTopicsTest` (`9/9`), `content.MedicalGlossaryTest` (`7/7`), `content.NormalizerCorpusTest` (`15/15`), `content.TreatmentKnowledgeAndTopicCatalogTest` (`5/5`), `data.MigrationTest` (`2/2`), `data.ProjectRepositoryTest` (`5/5`), `data.TemplateProjectTest` (`7/7`), `designsystem.TemplateArtworkGeometryTest` (`3/3`), `e2e.Reelify21ScenariosTest` (`3/3`), `editor.EditorOpsTest` (`8/8`), `eval.CreativeEvalHarness` (`1/1` passed, `1` skipped), `eval.CreativeQualityEvaluationTest` (`10/10`), `flow.EphemeralCreationAndBulkDeleteTest` (`4/4`), `media.LookMathTest` (`3/3`), `media.PreviewAndCompositionSafetyTest` (`4/4`), `media.TimelineResolverTest` (`9/9`), `navigation.NavigationAndBackSemanticsTest` (`2/2`), `policy.ClaimCheckerTest` (`4/4`), `policy.TimingEstimatorTest` (`4/4`), `recording.TeleprompterPacingTest` (`3/3`), `review.TrimBoundsTest` (`4/4`), `template.TemplateEngineTest` (`13/13`).

---

## 2. Android Instrumented Tests & APK Packaging

* **Instrumented Test Updates (`Implemented` & compiled via `:app:compileDebugAndroidTestKotlin`)**:
  * Added `ArabicRenderingTest.mixedIraqiArabicAndEnglishMedicalTerminologyStaysRtlAndShaped` verifying RTL direction and contextual shaping on mixed Iraqi Arabic + English medical captions (`PicoStar`, `Picosecond laser`, `1064 nm`, `Melasma`, `CFU Èlife`, `SMAS`).
  * Added `ExportPipelineTest.effectFreeTrimPlayerLoadsDiscoversDurationAndSeeksCleanly` verifying plain effect-free `TrimPlayerController` loading, duration discovery, seeking, and release on a generated MP4 fixture.
* **APK Builds**:
  * `:app:assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk` (packaged to `dist/Reelify-2.2.0-debug.apk`).
  * `:app:assembleRelease` -> `app/build/outputs/apk/release/app-release-unsigned.apk` (packaged to `dist/Reelify-2.2.0-release-unsigned.apk`).

---

## 3. Not Executed Here (`Physical-device tested: Unverified`, `Live API tested: Unverified`, `Human reviewed: Unverified`)

Because no physical Android handset, connected ADB device/emulator, or OpenAI/Deepgram API credentials were present in this headless Windows build environment:
* **On-device playback & CameraX**: Real phone `TrimScreen` playback across CameraX/HEVC clips, `PreviewProxyManager` hardware encoding, GPU beauty shaders, and back-stack/tab interactions must be checked manually using [`DEVICE_ACCEPTANCE_CHECKLIST.md`](DEVICE_ACCEPTANCE_CHECKLIST.md) and `./gradlew :app:connectedDebugAndroidTest`.
* **Live OpenAI (`gpt-6.2-sol`) & Deepgram (`nova-3`)**: Run `CreativeEvalHarness.runLiveEvaluation` with `REELIFY_OPENAI_API_KEY` set and spot-check conversational Iraqi clinical phrasing in-app.
