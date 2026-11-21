# Reelify 2.1 — Topic, Clinical Knowledge & Script Quality Evaluation

## Summary of Content Architecture Changes

Reelify 2.0 relied on 20 generic `{t}` substitution templates (`"A common misconception about {t}"`, `"Meet the {t}"`) and left `CFU`, `PicoStar`, and `Cleopatra` incomplete.

Reelify 2.1 replaces generic filler with four interlocking quality systems:

1. **Sourced Clinical Knowledge Packs (`TreatmentKnowledgeCatalog`)**:
   - Separated **device specs** (`1064/532 nm`, `<300–400 ps`, `650 µs`, `2940 nm`, `1927 nm`, `Centerless HIFU 4 MHz/2 MHz + ACE cooling`), **manufacturer applications**, **practice guidance**, **patient questions**, and **explicit identity status** (`cleopatra` kept unconfirmed until explicit tap).
   - Structured `FactualContentBrief` injection via `FactualBriefBuilder.build()` feeds exact `VerifiedFact` / `VerifiedQuestion` statements directly into `ScriptWriter.contextBlock()`.
2. **480 Specific Treatment × Template Topics (`catalog/*TopicCatalog.kt` & `TopicLibrary`)**:
   - **60 distinct topics** (`6 templates × 10+`) for each verified profile: `Aerolase`, `PicoStar`, `CFU Èlife`, `Dermablate`, `LaseMD Ultra`, `Diamond Peeling`, and `DiamondGlow®` (total **420** across those 7), plus **60** assessment/protocol-safe topics for unconfirmed `Cleopatra` (`480` total).
   - Every topic pairs an authentic Iraqi Arabic + English hook angle with explicit `factIds` checked by `TreatmentKnowledgeAndTopicCatalogTest`.
3. **Anti-Filler Validation, Iraqi Cadence & Word Budgets (`DialectChecker`, `ScriptValidator`, `ScriptPrompts`)**:
   - `DialectChecker.genericFillerHits()` catches empty promo phrasing in both English (`"transform your skin"`, `"game-changer"`, etc.) and Arabic (`"نضارة لا مثيل لها"`, `"بشرة خالية من العيوب"`, etc.).
   - `DialectChecker.excessiveConsultationHits()` warns/prevents repeating *"depends on individual assessment"* in every shot.
   - `TimingEstimator.wordBudgetForShot()` sets talking-head targets (`82–106%`) vs voiceover/CTA beats so scripts no longer finish halfway through a shot.
   - `ContentRepository.saveOwnerVoiceSample()` + `ScriptAction.SOUND_LIKE_ME` preserve the creator's preferred spoken style.

---

## Side-by-Side Comparison on Priority Scenarios (Verified via `Reelify21ScenariosTest`)

### Scenario A — PicoStar · Tattoo Removal · 30s (3×10s) · Iraqi Arabic · `myth_buster`
- **v2.0 baseline problem**: Generic `"A common misconception about PicoStar"`, marked unconfigured, risked superficial laser fluff.
- **v2.1 factual brief + output**:
  - Anchors to `pico_myth_one_session`, `pico_mech_photoacoustic`, `pico_mech_wavelengths`, `pico_proc_clearance`.
  - Shot 1 challenges the 1-session myth directly in conversational Iraqi (*"فكرة غلط: بيكوستار يمسح التاتو من جلسة وحدة. هواي ناس يتوقعون يطلعون بدون أي أثر، وهذا مو دقيق."*).
  - Shot 2 explains **photoacoustic pressure** (`1064` / `532 nm`) and lymphatic clearance naturally (*"جهاز PicoStar يكسر الحبر بالضغط الصوتي على موجتين 1064 و532 وجسمك يسحبه عبر اللمف تدريجياً."*).
  - Shot 3 closes realistically based on ink depth, color, and anatomical site.

### Scenario B — CFU Èlife · Facial Lifting · 45s (3×15s) · Iraqi Arabic · `patient_question`
- **v2.0 baseline problem**: Marked `needsConfiguration = true`, lacked transducer/depth clarity, risked mislabeling ultrasound as a laser.
- **v2.1 factual brief + output**:
  - Anchors to `cfu_q_laser`, `cfu_mech_hifu`, `cfu_area_zones`, `cfu_mech_neocollagenesis`.
  - Explicitly corrects the laser misconception (*"جهاز CFU Èlife يشتغل بالموجات فوق الصوتية المركّزة HIFU مو بالليزر"*), names **Centerless + ACE cooling** and `1.5 / 3.0 / 4.5 mm` depths, and sets progressive `8–12 week` collagen expectations honestly.

### Scenario C — Aerolase · Acne & Redness · 60s (3×20s) · English · `expert_explains`
- **v2.0 baseline problem**: Could pass validation with very short sections (~10–14 words for 20s shots), finishing far ahead of the teleprompter/timer.
- **v2.1 factual brief + output**:
  - Enforces full speaking-rate content filling 20s shots cleanly (`>= 25` words validated per shot, ~`38–46` word window).
  - Teaches `650-microsecond` `1064 nm` pulse physics targeting melanin, hemoglobin, and sebaceous/dermal water across Fitzpatrick tones, plus honest triage of deeper pitted acne scars to resurfacing.

---

## Automated Verification Status

- Verified offline via `TreatmentKnowledgeAndTopicCatalogTest` (5/5 passing), `DialectAndTopicsTest` (9/9 passing), `ScriptWriterTest` (15/15 passing), and `Reelify21ScenariosTest` (3/3 passing) with `MockWebServer` fixtures.
- **Live OpenAI evaluation (`CreativeEvalHarness.runLiveEvaluation`)**: *Skipped* (`REELIFY_OPENAI_API_KEY` not provided in CI environment; covered by `CreativeEvalSet` expanded to 32 balanced cases including `diamond_glow`). Live LLM outputs and native Iraqi human review on device remain pending credentialed testing.
