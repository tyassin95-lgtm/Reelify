# Reelify 2.2 — Verified Clinical Knowledge & Provenance Architecture

## Verification & Provenance Principles

Every built-in treatment knowledge pack in `app/src/main/java/app/reelify/core/content/TreatmentKnowledgeCatalog.kt` separates **device engineering specifications**, **manufacturer applications**, **clinical practice guidance**, and **unresolved clinic identity**, managed in v2.2 by `ClinicalKnowledgeManager`.

### Rules Enforced in Code

1. **Strict Provenance Separation (`sourceIds` vs. `supportingFactIds`)**:
   - In v2.2 (`TreatmentKnowledge.kt`), `VerifiedFact.supportingFactIds` stores referenced mechanism/clinical fact IDs while `VerifiedFact.sourceIds` holds strictly verified bibliographic `KnowledgeSource` IDs (`src_*`).
   - `TreatmentKnowledgePack.resolveFactOrQuestion(id)` and `ClinicalKnowledgeManager.verifyFactProvenance(pack, id)` guarantee that mechanism fact IDs (`*_mech_*`) never leak into bibliographic `sourceIds`.
2. **Focus Isolation (`FactualBriefBuilder.build` & `ClinicalKnowledgeManager.incompatibleTopicAreas`)**:
   - Focused topics no longer get padded with unrelated indications (e.g., `CFU Èlife` facial SMAS lifting briefs exclude `hyperhidrosis` and `body_contouring` facts; `PicoStar` tattoo removal briefs exclude `scars_rejuvenation`, and vice versa; `Dermablate` resurfacing excludes `benign_lesions`).
3. **Underused Clinical Knowledge Clusters (`KnowledgeCluster`)**:
   - `ClinicalKnowledgeManager.clustersFor(pack)` organizes verified facts and patient questions by `topicArea`, and `selectClustersForIdeaGeneration(...)` prioritizes clusters whose facts have not appeared in `CreativeHistoryRecord`.
4. **No Invented Physics or Unconfirmed Identities**:
   - Explicit distinction between **HIFU focused ultrasound** (`CFU Èlife`) and **optical lasers** (`Aerolase`, `PicoStar`, `Dermablate`, `LaseMD Ultra`).
   - **Cleopatra ambiguity guard**: `cleopatra` stays marked `needsConfiguration = true, identityConfirmed = false` until the creator explicitly confirms whether it maps to `DiamondGlow®` or enters a local clinic protocol.
5. **Non-Destructive Room Upgrades (`TreatmentSeed.CURRENT_SEED_VERSION = 3`)**:
   - `LibraryRepository.seedIfNeeded()` upgrades v1/v2 seeded fields field-by-field (`mergeUpgrade`) only where unmodified from previous baselines, and upgrades v2.x `TerminologyEntry` rows so `arabic = ""` and old Arabic transliterations move into `misrecognitions` for automatic ASR normalization to canonical English.

---

## Built-in Profiles & Primary Sources

| Profile ID | Service Name | Manufacturer & Hardware | Primary Sources (Retrieved 2026-11-21) | Status in v2.2 |
|---|---|---|---|---|
| **`cfu`** | CFU Èlife | Biotec Italia · Centerless HIFU (4 MHz face: `1.5/3.0/4.5 mm`; 2 MHz body: `6/9/11/13 mm`; EasyZoom, EasyFlow, ACE cooling) + axillary `Hyperhidrosis` option | Biotec Italia CFU Èlife Official Product Page (`https://www.biotecitalia.com/medical/en/cfu-elife/`) | **Verified (`needsConfiguration = false`)**. Explicitly HIFU (not laser); topic areas isolated cleanly. |
| **`picostar`** | PicoStar | Asclepion · Dual-wavelength `1064 nm` / `532 nm` picosecond `Nd:YAG` (`<300–400 ps` photoacoustic pulses), Zoom ST, Flat-Top, `MicroSpot` PICO | Asclepion UK PicoStar System Page (`https://asclepionuk.com/medical-aesthetic-devices/picostar-pico-laser-system/`) | **Verified (`needsConfiguration = false`)**. |
| **`diamond_glow`** | DiamondGlow | Allergan Aesthetics / Cleopatra Laser Center reference · 3-in-1 diamond-tip exfoliation, vacuum extraction, `Pro-Infusion` serum delivery + `Lymphatic drainage` | Cleopatra Laser Center — DiamondGlow® (`https://cleopatra-lasercenter.com/diamond-glow/`) | **Verified (`needsConfiguration = false`)**. |
| **`cleopatra`** | Cleopatra | Unconfirmed clinic menu label — may map to `DiamondGlow` or a custom protocol | Cleopatra Laser Center reference (`https://cleopatra-lasercenter.com/diamond-glow/`) | **Pending explicit confirmation (`needsConfiguration = true`, `identityConfirmed = false`)**. |
| **`aerolase`** | Aerolase | Aerolase · Neo Elite `1064 nm` `Nd:YAG`, `650-microsecond` contactless pulse targeting `Melanin`, `Hemoglobin` & water (Fitzpatrick I–VI) | Aerolase Neo Elite Official Page (`https://aerolase.com/neo-elite/`) | **Verified (`seedVersion = 3`)**. |
| **`dermablate`** | Dermablate | Asclepion · MCL31 `2940 nm` `Er:YAG` ablative & `MicroSpot` `Fractional laser` `Skin resurfacing` | Asclepion Dermablate Official Page (`https://asclepion.com/en/product/dermablate/`) | **Verified (`seedVersion = 3`)**. |
| **`lasemd`** | LaseMD | Lutronic · `1927 nm` non-ablative `Fractional laser` (`Thulium laser`) with `IntelliTrak` magnetic roller & `Micro-channels` serum delivery | Lutronic LaseMD Ultra Provider Page (`https://www.lutronic.com/us/provider/product/lasemd-ultra/`) | **Verified (`seedVersion = 3`)**. |
| **`diamond_peeling`** | Diamond Peeling | Crystal-free diamond `Microdermabrasion` + vacuum suction (`Stratum corneum` only) | Clinical Dermatology Overview (`https://www.ncbi.nlm.nih.gov/books/NBK535383/`) | **Verified (`seedVersion = 3`)**. |

---

## Verification Status

- `Implemented`: `ClinicalKnowledgeManager`, `CreativeTopic`, v2.2 `TreatmentKnowledgePack.resolveFactOrQuestion`, `FactualBriefBuilder` focus filtering, and `TreatmentSeed` v3 English terminology cleanup.
- `Unit tested`: `TreatmentKnowledgeAndTopicCatalogTest`, `CreativeQualityEvaluationTest` (provenance check asserting every resolved fact traces to real `src_*` entries without mixing mechanism IDs, and verifying `CFU` / `PicoStar` topic isolation), and `MigrationTest`.
- `Unverified`: Automated live web-scraping/refresh is intentionally omitted (offline/client-only design); `ClinicalKnowledgeManager.auditLocalSources()` reports local source provenance transparently without fabricating remote scrapes.
