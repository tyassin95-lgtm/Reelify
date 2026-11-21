# Reelify 2.2 — Medical Terminology Policy

## Rule Statement

Regardless of whether a reel is written in **English (`en`)** or **conversational Iraqi Arabic (`ar-IQ`)**, **all medical, anatomical, modality, device, wavelength, pulse-width, and diagnostic terminology must use approved English spellings in Latin characters**.

Spoken Iraqi Arabic grammar, connectives, pronouns, and everyday clinical explanations remain natural and conversational around those English terms. Transliterated Arabic phonetic spellings (such as `بيكوستار`, `إيروليز`, `هايفو`, `كولاجين`, `سماس`) and translated clinical disease nouns in place of the canonical term (`الكلف`, `فرط التصبغ`) are prohibited in generated outputs.

Example of intended mixed Iraqi Arabic cadence:

> "بـ PicoStar نستخدم Picosecond laser، بس اختيار الـ Wavelength يعتمد على نوع الـ Pigmentation، مو كل التصبغات نتعامل وياها بنفس الطريقة."

---

## Single Source of Truth in Code

The canonical glossary is implemented in `app/src/main/java/app/reelify/core/content/MedicalGlossary.kt` (`object MedicalGlossary`, data class `CanonicalMedicalTerm`), seeded cleanly into Room in `TreatmentSeed.kt` (`CURRENT_SEED_VERSION = 3`), and editable by the creator via Studio → Terminology (`TerminologyEntry`).

### Core Built-in Canonical Terms

| Category | Canonical English Form | Prohibited Arabic Transliteration / Translation Examples |
|---|---|---|
| Devices & Systems | `PicoStar`, `Aerolase`, `Neo Elite`, `CFU Èlife`, `Dermablate`, `LaseMD Ultra`, `LaseMD`, `DiamondGlow`, `Diamond Peeling`, `MicroSpot`, `Pro-Infusion`, `IntelliTrak` | `بيكوستار`, `إيروليز`, `سي إف يو إيلايف`, `ديرمابليت`, `ليز إم دي`, `دايموند جلو`, `التقشير الماسي` |
| Modalities & Physics | `HIFU`, `Nd:YAG`, `Er:YAG`, `Picosecond laser`, `Nanosecond laser`, `Fractional laser`, `Thulium laser`, `Photoacoustic`, `Photothermal`, `Wavelength` | `هايفو` / `الموجات فوق الصوتية المركزة`, `ند ياغ`, `إربيوم ياغ`, `فراكشنال ليزر` |
| Conditions & Procedures | `Melasma`, `Hyperpigmentation`, `Post-inflammatory hyperpigmentation` (`PIH`), `Post-inflammatory erythema` (`PIE`), `Hyperhidrosis`, `Skin resurfacing`, `Microdermabrasion` | `الكلف`, `فرط التصبغ`, `فرط التعرق`, `إعادة تسطيح البشرة` |
| Anatomy & Biology | `Collagen`, `Neocollagenesis`, `Epidermis`, `Dermis`, `Stratum corneum`, `SMAS`, `Melanin`, `Hemoglobin`, `Sebaceous glands`, `Lymphatic drainage`, `Micro-channels`, `Fitzpatrick` | `الكولاجين`, `الأدمة`, `سماس`, `صبغة الميلانين`, `الهيموغلوبين` |
| Clinical Measurements | `1064 nm`, `532 nm`, `1927 nm`, `2940 nm`, `650 microseconds` | `نانومتر`, `مايكروثانية` |

---

## Where the Policy Is Enforced

1. **AI Prompt Instructions (`ScriptPrompts.kt`, `ScriptWriter.kt`)**:
   - `MedicalGlossary.promptBlock(treatmentId, userTerms)` injects exact mandatory English terms and banned Arabic forms into both `ideas()` and `generate()` / `hooks()` / `rewrite()` prompts.
2. **Pre-Repair Validation (`ScriptValidator.kt`, `IdeaValidator.kt`, `PlanValidator.kt`)**:
   - `MedicalGlossary.validate(text, userTerms)` rejects Arabic medical transliterations/translations and uncanonical Latin spellings during AI validation so repair rounds correct them.
   - `PlanValidator.languageMatches(...)` accepts mixed `ar-IQ` scripts containing legitimate English medical terms in Latin characters while still catching accidental all-English responses when `ar-IQ` is required.
3. **Deterministic Post-Processing Normalization (`GeneratedContentNormalizer.kt`, `MedicalGlossary.enforceEnglishMedicalTerms`)**:
   - Normalizes Arabic metric units (`1064 نانومتر` -> `1064 nm`, `650 مايكروثانية` -> `650 microseconds`), maps prohibited Arabic medical phrases while keeping Iraqi grammatical proclitics (`و`, `بـ`, `لـ`, `الـ`) attached naturally, and canonicalizes Latin casing (`picostar` -> `PicoStar`, `smas` -> `SMAS`).
   - Invoked across titles, hooks, spoken scripts, headlines, curated `TopicLibrary` titles, `FactualBriefBuilder.toPromptBlock`, and optional one-tap normalization (`ScriptViewModel.normalizeSavedMedicalTerminology`) on existing saved scripts.
4. **Captions & Transcription Correction (`CaptionBuilder.kt`, `AutoEditor.kt`)**:
   - `TerminologyCorrector.correct(...)` merges ASR split tokens and normalizes recognized terms to `entry.english` regardless of whether `language` is `ENGLISH` or `IRAQI_ARABIC`.
5. **RTL Rendering Compatibility (`GeneratedContentNormalizer.isArabicDominant`, `ReelOverlayRenderer.kt`)**:
   - `isArabicDominant(...)` strips canonical English medical terms before computing Arabic-vs-Latin character dominance so Iraqi Arabic captions beginning with `"PicoStar ..."` or `"Aerolase ..."` keep `TextDirectionHeuristics.RTL` alignment and contextual Arabic letter joining intact.

---

## Verification Status

- `Implemented`: Prompt injection, regex proclitic normalization, Room v3 seed cleanup (`arabic = ""`), ASR correction, UI action, and RTL dominance heuristic.
- `Unit tested`: `MedicalGlossaryTest`, `CaptionsTest`, `DialectAndTopicsTest`, `NormalizerCorpusTest`, `ScriptWriterTest`, `Reelify21ScenariosTest` (JVM + Robolectric).
- `Instrumented tested`: Source compiles with mixed Iraqi Arabic + English RTL shaping test in `ArabicRenderingTest.mixedIraqiArabicAndEnglishMedicalTerminologyStaysRtlAndShaped`; **not hardware-executed in this environment**.
- `Physical-device tested` / `Human reviewed`: **Unverified** — native Iraqi clinician spot-check of proclitic attachment (`بـ PicoStar`, `للـ Epidermis`) on-device caption rendering remains pending hardware testing.
