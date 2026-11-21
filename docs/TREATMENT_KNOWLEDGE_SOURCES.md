# Reelify 2.1 — Verified Treatment Knowledge & Source Attribution

Every built-in treatment knowledge pack in `app/src/main/java/app/reelify/core/content/TreatmentKnowledgeCatalog.kt` separates **device engineering specifications**, **manufacturer applications**, **clinical practice guidance**, and **unresolved clinic identity**.

---

## Verification Rules Enforced in Code

1. No invented FDA/CE indications beyond verified documentation.
2. No universal session count guarantees or miracle permanence wording.
3. Explicit distinction between **HIFU focused ultrasound** (`CFU Èlife`) and **optical lasers** (`Aerolase`, `PicoStar`, `Dermablate`, `LaseMD Ultra`).
4. **Ambiguity protection on `Cleopatra`**: `https://cleopatra-lasercenter.com/diamond-glow/` describes **DiamondGlow®** at Cleopatra Laser Center, NOT a universal synonym for every service titled "Cleopatra". Accordingly:
   - `diamond_glow` ships as an independent verified profile (`identityConfirmed = true`).
   - `cleopatra` stays marked `needsConfiguration = true, identityConfirmed = false` until a creator explicitly taps one-tap confirmation (`confirmCleopatraAsDiamondGlow()`) or edits its custom device details in Studio.
5. **Non-destructive Room upgrades (`LibraryRepository.seedIfNeeded`)**: seeded profiles upgrade from `seedVersion = 1` -> `2` strictly field-by-field for unmodified defaults; any customized field (`userEditedFields` or diverged values) and any user-created custom treatment remain untouched.

---

## Built-in Profiles & Primary Sources

| Profile ID | Service Name | Manufacturer & Hardware | Primary Sources (Retrieved 2026-11-21) | Status in v2.1 |
|---|---|---|---|---|
| **`cfu`** | CFU Èlife | Biotec Italia · Centerless HIFU (4 MHz face: `1.5/3.0/4.5 mm`; 2 MHz body: `6/9/11/13 mm`; EasyZoom, EasyFlow, ACE cooling) + medical axillary hyperhidrosis option | Biotec Italia CFU Èlife Official Product Page (`https://www.biotecitalia.com/medical/en/cfu-elife/`) | **Verified (`needsConfiguration = false`)**. Explicitly NOT a laser. |
| **`picostar`** | PicoStar | Asclepion · Dual-wavelength `1064 nm` / `532 nm` picosecond Nd:YAG (`<300–400 ps` photoacoustic pulses), Zoom ST, Flat-Top, MicroSpot PICO handpieces | Asclepion UK PicoStar System Page (`https://asclepionuk.com/medical-aesthetic-devices/picostar-pico-laser-system/`) | **Verified (`needsConfiguration = false`)**. |
| **`diamond_glow`** | DiamondGlow® | Allergan Aesthetics / Cleopatra Laser Center reference · 3-in-1 simultaneous diamond-tip exfoliation, high-powered vacuum extraction, SkinMedica® Pro-Infusion serum infusion + lymphatic drainage | Cleopatra Laser Center — DiamondGlow® (`https://cleopatra-lasercenter.com/diamond-glow/`) | **Verified (`needsConfiguration = false`)**. |
| **`cleopatra`** | Cleopatra | Unconfirmed clinic menu label — may map to DiamondGlow® (`https://cleopatra-lasercenter.com/diamond-glow/`) or a local facial protocol | Cleopatra Laser Center — DiamondGlow® (`https://cleopatra-lasercenter.com/diamond-glow/`) | **Pending explicit confirmation (`needsConfiguration = true`, `identityConfirmed = false`)**. |
| **`aerolase`** | Aerolase Neo Elite | Aerolase · `1064 nm` Nd:YAG, `650-microsecond` contactless pulse targeting melanin, hemoglobin & water (Fitzpatrick I–VI) | Aerolase Neo Elite Official Page (`https://aerolase.com/neo-elite/`) | **Verified & enriched (`seedVersion = 2`)**. |
| **`dermablate`** | Dermablate | Asclepion · MCL31 `2940 nm` Er:YAG ablative & MicroSpot fractional resurfacing laser | Asclepion Dermablate Official Page (`https://asclepion.com/en/product/dermablate/`) | **Verified & enriched (`seedVersion = 2`)**. |
| **`lasemd`** | LaseMD Ultra | Lutronic · `1927 nm` non-ablative fractional thulium fiber laser with IntelliTrak magnetic roller & LADD ampoule delivery | Lutronic LaseMD Ultra Provider Page (`https://www.lutronic.com/us/provider/product/lasemd-ultra/`) | **Verified & enriched (`seedVersion = 2`)**. |
| **`diamond_peeling`** | Diamond Peeling | Mechanical crystal-free diamond microdermabrasion wand + vacuum suction (stratum corneum only) | Clinical Dermatology Microdermabrasion Overview (`https://www.ncbi.nlm.nih.gov/books/NBK535383/`) | **Verified & enriched (`seedVersion = 2`)**. |

---

## Automated Safeguards Verified by Unit Tests

- `TreatmentKnowledgeAndTopicCatalogTest`: asserts 420+ verified topic entries (`7 treatments × 6 templates × 10+`), resolves every referenced `factId` back to `TreatmentKnowledgeCatalog`, asserts `cfu` prohibits laser claims, asserts `picostar` includes wavelengths/handpieces/photoacoustic specs, and asserts `cleopatra` remains isolated until confirmed.
- `MigrationTest`: verifies v2.0 -> v2.1 upgrade preserves user-customized talking points and custom services while populating new clinical metadata cleanly.
