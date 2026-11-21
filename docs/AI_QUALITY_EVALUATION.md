# Reelify 2.2 — AI Quality Evaluation & Regression Safeguards

## Overview

In Reelify 2.2, AI quality assurance combines two complementary layers:

1. **Automated Deterministic Guardrails & 9 Bad-Output Regression Fixtures (`CreativeQualityEvaluationTest`)**: `Unit tested` offline without API keys.
2. **Live Model + Human Iraqi Clinician Benchmark (`CreativeEvalHarness`)**: `Unverified` in this build environment because no live `REELIFY_OPENAI_API_KEY` is configured (`runLiveEvaluation` skipped in Gradle).

---

## Automated Bad-Output Regression Fixtures (`CreativeQualityEvaluationTest`)

All 9 targeted failure modes are asserted in `app/src/test/java/app/reelify/eval/CreativeQualityEvaluationTest.kt` (`10/10` tests passing):

| Fixture # | Defect Class Tested | Expected Guardrail Outcome | Verification Status |
|---|---|---|---|
| **Fixture 1** | Cross-section conceptual repetition across Shots 1, 2, 3 | Rejected by `ScriptValidator.validateCrossSectionCoherence` (`repeats content`, triggers `targetedRevisions`) | `Unit tested` |
| **Fixture 2** | Empty promotional filler (*"Unlock glowing skin..."*, *"cutting-edge"*) | Rejected via `DialectChecker.genericFillerHits` | `Unit tested` |
| **Fixture 3** | Off-focus device indication bleed (`hyperhidrosis` inside a facial `SMAS` brief) | Rejected via `ClinicalKnowledgeManager.incompatibleTopicAreas` + `validateCrossSectionCoherence` | `Unit tested` |
| **Fixture 4 & 5** | Unsafe guarantee/cure claims + mislabeling `CFU Èlife` as a laser | Rejected via `ClaimChecker` blocker rule & HIFU physics guard | `Unit tested` |
| **Fixture 6** | Generic / nonsensical AI idea outputs (`Why X is the best`, `(propose fresh ideas)`, unknown `fact_id`) | Rejected by `IdeaValidator.evaluateAndRank` (`0` accepted out of `3` candidates) | `Unit tested` |
| **Fixture 7 & 8** | Arabic medical transliterations (`بيكوستار`, `كولاجين`, `الكلف`) + stiff MSA | Rejected by `MedicalGlossary.validate` and `DialectChecker.analyze().likelyFormal` | `Unit tested` |
| **Fixture 9** | Missing Shot 3 educational payoff ending only in *"Every patient is different, book an assessment"* | Rejected by `validateCrossSectionCoherence` (`payoff` check) | `Unit tested` |

In addition, `CreativeQualityEvaluationTest` verifies:
- Every `VerifiedQuestion` across all verified treatment packs resolves real bibliographic `KnowledgeSource` entries (`src_*`) without confusing mechanism fact IDs for sources.
- `FactualBriefBuilder.build` keeps facial `CFU Èlife` briefs strictly free of `cfu_app_hyperhidrosis` and `cfu_app_body_contour`.
- `ClinicalKnowledgeManager.selectClustersForIdeaGeneration` prioritizes underused mechanism clusters (`picosecond_mechanism`, `wavelengths`, `lymphatic_clearance`, etc.).

---

## Live OpenAI Benchmark (`CreativeEvalHarness` — `Unverified`)

`CreativeEvalSet` holds **32 balanced scenarios** covering all treatments (`Aerolase`, `PicoStar`, `Dermablate`, `CFU Èlife`, `LaseMD`, `Cleopatra`, `DiamondGlow`, `Diamond Peeling`, `General`), all 6 built-in templates, all 3 durations (`30s`, `45s`, `60s`), English (`en`), and Iraqi Arabic (`ar-IQ`).

In Reelify 2.2, `CreativeEvalHarness.runLiveEvaluation` additionally logs per-shot `MedicalGlossary.validate(...)` violation counts alongside `DialectChecker` formal-MSA hits and cross-hook similarity metrics.

To run the live evaluation with your own OpenAI key and score outputs by hand:

```bash
REELIFY_OPENAI_API_KEY=sk-... ./gradlew :app:testDebugUnitTest --tests 'app.reelify.eval.*'
```

Inspect the generated report at `app/build/reports/creative-eval.md` against the human rubric in `docs/eval/CREATIVE_EVAL.md`. Live GPT responses (`gpt-6.2-sol`) and native Iraqi dialect human scoring remain **pending credentialed execution**.
