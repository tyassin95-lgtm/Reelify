# Reelify 2.2 — AI Creative Pipeline & Anti-Repetition Architecture

## Overview

Reelify 2.2 rebuilds both **Stage 2 idea generation (`ScriptWriter.ideas`)** and **Stage 2/3 scriptwriting (`ScriptWriter.generate`)** so AI outputs match or exceed curated specialist concepts and read like cohesive three-act spoken education rather than repetitive clinic brochures.

---

## 1. End-to-End Flow

```
Curated Benchmarks + Underused KnowledgeCluster Selection (ClinicalKnowledgeManager)
         │
         ▼
   ScriptWriter.ideas(mode = FRESH | MORE_LIKE_THIS | DIFFERENT_ANGLE)
         │  (strict json_schema: title, angle, hook, hook_style, category,
         │   educational_question, learning_objective, supporting_fact_ids,
         │   specific_takeaway, visual_emphasis, template_fit_reason)
         ▼
   IdeaValidator.evaluateAndRank()
     1. Normalization + English medical terminology (MedicalGlossary)
     2. Language match, 3s hook budget, generic filler & unsafe claim guard, CFU != laser physics
     3. Fact ID resolution + semantic overlap check against TreatmentKnowledgePack
     4. Originality vs curated benchmarks & CreativeHistoryRecord deduplication
     5. Editorial scoring & ranking (0..100)
         │
         ▼
   Full CreativeTopic object preserved -> CreationDraftSession -> ScriptContext
         │
         ▼
   FactualBriefBuilder.build() (single-subject focus via incompatibleTopicAreas filter)
         │
         ▼
   ScriptWriter.generate()
     1. Template-specific Narrative Blueprint (ScriptPrompts.templateNarrativeBlueprint)
     2. Model plans `narrative_outline` (NarrativeOutlineDto) first, then Shots 1..3
     3. ScriptValidator.validateScript() + validateCrossSectionCoherence()
     4. Bounded targeted section-level revision (`performTargetedSectionRevision`) if specific
        shots repeat prior shots, duplicate disclaimers, lack Shot 3 payoff, or break rules
     5. MedicalGlossary + GeneratedContentNormalizer cleanup
         │
         ▼
   Atomic persistence (`ProjectRepository.createFromTemplateWithScriptAtomically`)
```

---

## 2. Idea Generation Engine (`ScriptWriter.ideas`, `IdeaValidator.kt`)

- **No more empty `(propose fresh ideas)` prompts**:
  - `ScriptWriter.ideas()` extracts the top 4 curated `CreativeTopic` benchmarks for the exact `(treatment, template)` pair so the model sees concrete target specificity without copying them.
  - `ClinicalKnowledgeManager.selectClustersForIdeaGeneration()` selects up to 5 underused `KnowledgeCluster`s sorted by fewest recently-used facts and highest mechanistic richness.
- **Interactive Stage 2 Modes (`IdeaGenerationMode`)**:
  - `FRESH`: explores underused clinical clusters across the treatment pack.
  - `MORE_LIKE_THIS`: anchors onto the seed topic's `topicArea` cluster to explore deeper mechanisms or follow-up questions in that domain.
  - `DIFFERENT_ANGLE`: excludes the seed topic's `topicArea` cluster and pushes completely distinct clinical mechanisms/indications.
- **Persistence**: Users can tap **Save** on any generated `CreativeTopic` (`ContentRepository.saveIdeaForLater`), stored locally under `"saved_idea::*"`. Accepted topics record `CreativeHistoryRecord` entries (`recordCreativeTopicUse`) for future anti-repetition.

---

## 3. Script Coherence & Anti-Repetition Enforcement (`ScriptValidator.kt`)

`ScriptValidator.validateCrossSectionCoherence` rejects scripts (and populates `targetedRevisions`) when any of these defects occur:

1. **High cross-section lexical overlap**: content-word Jaccard similarity `> 0.45f` or `>= 2` shared 3-word trigrams across any pair `(1,2)`, `(2,3)`, or `(1,3)` (excluding treatment names).
2. **Repeated disclaimer clichés**: `variabilityOrConsultClauses` (`"depends on individual assessment"`, `"results vary"`, `"يختلف من شخص"`, `"حسب تقييم"`, etc.) appearing in `>= 2` sections.
3. **Device re-introduction in Shot 2 or Shot 3**: generic `"Today we're talking about / هذا جهاز ..."` openers in later shots instead of progressing the narrative.
4. **Empty Shot 3 payoff**: Shot 3 containing a consultation/variability clause without concrete clinical reasoning signals (`because`, `layers`, `wavelength`, `collagen`, `sunscreen`, `تصبغ`, `حبر`, `عمق`, etc.) and fewer than 14 content tokens.
5. **Treatment identity / focus violation**: e.g., claiming `CFU Èlife` is a laser, or pulling excluded topics (`hyperhidrosis`, `body_contouring`, `tattoos_pmu` during scar focus, `scars_rejuvenation` during tattoo focus) into the script.

When issues are isolated to specific sections (`targetedRevisions`), `ScriptWriter.performTargetedSectionRevision` rewrites **only the offending section indexes** cleanly rather than discarding already-strong shots.

---

## Verification Status

- `Implemented`: Full `NarrativeOutlineDto`, `IdeaValidator`, `ClinicalKnowledgeManager` cluster selection, targeted section revision, Stage 2 `Use idea` / `More like this` / `Different angle` / `Save` actions, `CreativeHistoryRecord` persistence.
- `Unit tested`: `CreativeQualityEvaluationTest` (verifying rejection of all 9 curated bad-output fixtures including duplicate shots, empty disclaimers, generic promos, Arabic transliterations, off-focus CFU fat leakage, and `CFU = laser` falsehoods), `ScriptWriterTest`, `Reelify21ScenariosTest` (mocked JSON responses via `MockWebServer`).
- `Live API tested` / `Human reviewed`: **Unverified** — live OpenAI `gpt-6.2-sol` generation and native Iraqi specialist rating via `CreativeEvalHarness.runLiveEvaluation` were skipped because no OpenAI API key is present in the build environment.
