# Reelify 2.0 & 2.1 — template-first product architecture

> **Reelify 2.1 Reliability & Clinical Content Addendum:** Building directly on 2.0's deterministic template foundation, 2.1 separates single-clip trim playback (`ExoPlayer` via `TrimPlayerController` + `TrimBounds`), moves finished-reel multi-track/effect preview to cached rendered MP4 proxies (`PreviewProxyManager`) with `try/catch` fallbacks and explicit `EditedMediaItem.durationUs`, clips template card artwork cleanly (`TemplateFrameGeometry`), introduces sourced `TreatmentKnowledgeCatalog` packs + non-destructive `seedVersion` Room upgrades + `480` treatment/template topics, strengthens `FactualBriefBuilder` / anti-filler script validation + Instagram/Snapchat tone + owner voice persistence, and replaces fixed teleprompter scroll velocity with duration-synced `TeleprompterPacer` (`AUTO_FIT`, `MATCH_SHOT`, `MANUAL`).

Reelify 1.x behaved like an AI filmmaking workstation: the model invented a full production plan
(camera, framing, lighting, SFX, editing) for every reel, the creator answered up to eight idea
fields, and a reel took ~9 screens. Reelify 2.0 makes **filming and editing deterministic and
reusable**, and narrows the AI to what it is good at: **fresh angles, hooks and natural dialogue**.

```
Selected template (versioned, fixed)  +  approved script (AI or user)  +  treatment profile  +  brand
                       │                               │                         │              │
                       └──────────────► ProductionPlanner (pure, no network) ◄───┴──────────────┘
                                         │ sections (template-owned fields)
                                         │ edit settings (captions, music, SFX, transitions,
                                         │                colour, effects, branding)
                                         ▼
                         Camera presets · Auto editor · Media3 render (unchanged engine)
```

## 1. Responsibilities

| Owner | Decides |
|---|---|
| **Template** (`core/template`) | 3 shot roles, durations (always total/3), media requirement (talking / voiceover over B-roll), framing guide, camera preset (facing, teleprompter, beauty default, focus assist), teleprompter preset, filming instruction, lighting & delivery notes, B-roll hint, transition out, SFX strategy & positions, caption preset, music preset, transition preset, colour look, intro/push-in/vignette/light overlay, branding preset, beauty preset for talking shots |
| **AI** (`integration/openai/ScriptWriter`) | creative angle, 3 hooks (3 different techniques), spoken dialogue per shot, ≤5-word headline, emphasis words, CTA style (from an approved list), self-reported medical-review flags, optional editorial review |
| **User** | language, treatment, duration, template, topic (library / AI idea / own idea), hook choice, script edits & approval, recording/importing, optional adjustments (which become *overrides*), export |

The AI JSON schema (`ScriptSchemas.script`) **cannot express** camera, framing, lighting,
transitions, music, SFX, shot type or media plan (unit-tested). Even if a response were crafted to
include them, `ProductionPlanner.mergeScript` copies only `spokenScript / onScreenText /
captionEmphasis` and re-applies every template-owned field from the snapshot (unit-tested with a
tampered section).

## 2. Template model

* `ReelTemplate(id, version, name, description, previewAsset, supportedDurations, shots[3],
  captionPresetId, musicPresetId, transitionPresetId, brandingPresetId, colorLook, …,
  structureBrief)`
* `ShotRecipe(index, role, requiredMedia, framing, cameraPresetId, teleprompterPresetId,
  instruction, lighting, delivery, writingBrief, optionalBroll, transitionOut, sfxIn, brollHint)`
* Shared preset tables (`BuiltInTemplates`): camera, teleprompter, music, transition, branding.
* All instruction/label text is `LocalizedText(en, ar)`.

### Built-in templates (v1)

| Template | Shot 1 | Shot 2 | Shot 3 | Captions | Look | Music | Transitions |
|---|---|---|---|---|---|---|---|
| Expert Explains | Hook · talking MCU | Explanation · **VO over device B-roll** (rear cam) | Conclusion & CTA · tighter | Premium Minimal | Clean | Silk Atelier | subtle zoom |
| Myth Buster | Myth · talking | Correction · talking (+ optional cutaway) | Takeaway · close | Bold Engagement | Bright Social | Glass Pulse | light flash |
| Treatment Showcase | Introduction · medium | Showcase · **VO over treatment B-roll** | Who it's for & CTA | Premium Minimal | Warm + light leak | Velvet Hour | soft dissolve |
| Three Things to Know | Insight 1 | Insight 2 (+ cutaway) | Insight 3 & CTA | Bold Engagement | Clean | Glass Pulse | clean cut + text-reveal SFX |
| Common Patient Question | Question | Answer (+ cutaway) | Qualification & CTA | Clinical Clean | Cool Clinical, no push-in | Clear Skin | soft dissolve |
| Problem / Approach | Concern | Approach · **VO over device B-roll** | Expectations & CTA | Premium Minimal | Cinematic + vignette | Quiet Horizon | soft blur |

A test asserts every pair of templates differs in at least three of {roles, media, captions,
colour, transitions, music} — they are formats, not renames.

### Versioning & snapshots

* Changing anything in a built-in template requires bumping `version` (a golden test pins the
  structure of v1).
* A project stores the **full template JSON** (`projects.templateJson`) plus `templateId`,
  `templateVersion`. Rendering, filming and AI context always read the snapshot, so updating a
  template never alters an existing project.
* Customising (Studio → Templates) saves a **new** template (`custom_templates`, `builtIn=false`,
  `baseTemplateId`), incrementing its own version on each save. Built-ins never change.

### Separation of layers

1. *Fixed template definition* — `BuiltInTemplates` / `custom_templates`.
2. *Project snapshot* — `Project.template`.
3. *AI script* — section text + `Project.script: ScriptMeta` (topic, hooks, active hook, CTA style,
   medical review, quality notes, prompt version).
4. *User overrides* — `Project.templateOverrides` (per-shot camera facing / teleprompter / beauty)
   and `EditSettings.userOverrides` (edit groups the user changed). `ProductionPlanner
   .applyTemplateDefaults` re-applies the template **only** to groups not in `userOverrides`;
   "Reset to template" removes a group from the set.

## 3. The four-stage workflow

| Stage | Screen | What the creator does |
|---|---|---|
| 1 · Choose your reel | `ChooseReelScreen` | treatment card, English/عراقي, 30/45/60, template card (code-drawn 3-shot thumbnail). Last choices are remembered. |
| 2 · Choose your message | `MessageScreen` → `ScriptReviewScreen` | tap a suggested topic **or** "Surprise me with a fresh idea" **or** type one line. Script is generated immediately (no feedback screen). Review: 3 hooks, 3 shot cards, chips (Try different hook / More engaging / More natural Iraqi / Shorten / More professional), per-shot Edit / Try another / Undo / Lock, optional details, **Use This Script**. |
| 3 · Film your reel | `FilmScreen` → `RecordScreen` | three shot cards with instruction, script, Record/Import (VO shots: footage + voice), Trim, optional cutaway. Accepting a take returns to the list. |
| 4 · Preview & export | `AutoEditScreen` → `PreviewScreen` → `ExportScreen` | automatic template edit, full-height preview, **Export**, **Adjust** (Captions, Music, Beauty, Color, Branding, Replace Shot, Trim Shot, All controls), **Advanced** (timeline editor). |

Legacy (1.x) projects keep the original screens (idea → feedback → plan → checklist → review)
and their own per-section settings; `openProject` routes by `Project.mode`.

## 4. AI architecture (2.0)

* `ScriptWriter` use cases: `generate`, `ideas` (4 fresh angles), `hooks` (3 new, must differ from
  current), `rewrite(action, targets)` for MORE_ENGAGING / MORE_NATURAL_IRAQI / SHORTEN /
  MORE_PROFESSIONAL / REGENERATE_SHOT, `editorialReview` (optional, advisory).
* Separate system prompts: `ScriptPrompts.SYSTEM_EN` and `ScriptPrompts.systemIraqi(examples)` —
  the Iraqi prompt is written for the dialect (tone rules, formal→Iraqi substitutions, overused
  phrases, CTA examples, reference lines with owner-approved ones first).
* Context: template structure + per-shot writing briefs and media type, word budget (section 1
  includes the hook), approved treatment facts only, terminology, brand CTA, topic, recent hooks
  and topics (variety).
* The app owns the hook placement: section 1 is stored as `hook + body`; switching hooks swaps only
  that prefix (`ScriptValidator.swapHook`).
* Validation layers (`ScriptValidator`), each failing response gets one repair round:
  1. structure (3 sections, indexes, 3 hooks, CTA style enum)
  2. language/script per field
  3. dialect heuristics for `ar-IQ` (`DialectChecker`: formal markers vs Iraqi markers; "likely
     formal" = ≥2 formal markers and no Iraqi markers → repair)
  4. word budget / speaking time (≤115 %, ≥35 % of budget)
  5. hook diversity (different techniques, Jaccard similarity ≤ 0.6)
  6. artifacts / stage directions
  7. local medical-claim rules + the profile's "claims not to make" (blocker → repair, except inside
     a *myth* shot where it is shown to the creator instead)
  None of these proves dialect authenticity; the owner's review does (Studio → Iraqi style guide).

## 5. Text quality pipeline

`GeneratedContentNormalizer` runs on every AI field before validation and storage; `displaySafe`
runs at display time (ScriptText, teleprompter) for older/user text. Paragraph direction now follows
the **dominant script** everywhere (captions renderer, Compose text, text fields, teleprompter) —
1.x used first-strong detection, which laid out Iraqi lines that start with a Latin device name
left-to-right. Any Arabic letter selects Tajawal (which also has Latin glyphs) so mixed lines never
fall back to a random system font.

## 6. Camera & beauty

* `CameraOverlayScaffold`: TOP (back · "Shot 1 of 3" / timer · flip · more) / MIDDLE (teleprompter
  ≤48 % + status chips, same column) / BOTTOM (Beauty · Script · Record ring · Camera · Light). One
  column ⇒ no overlap; verified by an instrumented test at 7 sizes × 3 font scales × 3 positions.
* Template camera presets choose facing, teleprompter, beauty default, framing guide, focus assist;
  the creator can override (persisted per shot).
* Beauty root causes fixed: face coordinates now mapped into **sensor texture space**
  (`CameraFaceMapping`), texel size/aspect taken from the **input** texture, face-axis-aware
  under-eye/refine (`uUp`), stronger but texture-preserving smoothing, perceptual gain, glow and
  split-tone, recalibrated presets.
* Honest effect state (`BeautyEffectState`: STARTING / DISABLED / ACTIVE / UNSUPPORTED / FAILED);
  ACTIVE only when frames are actually rendered through the shader; GPU failures rebind the camera
  without the effect and say so. Hold-to-compare uses the same pipeline with `uBypass`. The take
  review plays the recording through the export shader ("With Natural / Original").
* Clinical footage (VO B-roll, treatment shots, imports marked as patient/treatment) never gets
  beauty by default.
