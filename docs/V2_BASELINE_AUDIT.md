# Reelify 2.0 — baseline audit (before refactor)

* Repository `tyassin95-lgtm/Reelify`, branch `ccr-8e1f1393-o8wow2`, HEAD `2490cb1`
  ("Fix dropped characters in text fields…"), identical to `origin`. Work continues on
  `reelify-v2-refactor` (history preserved).
* Baseline build: `:app:assembleDebug` OK. Baseline unit tests: **80/80 pass**.
* Codebase: ~14.3k lines of Kotlin, single module, Room v1 schema (6 tables, JSON columns).

## Findings that drove the redesign

| # | Area | Finding |
|---|---|---|
| 1 | Workflow | 9 screens from "New reel" to preview (setup, idea with 8 fields, AI feedback, plan, checklist, record, trim, footage review, auto-edit). |
| 2 | AI scope | `PlanDto` made the model invent camera direction, framing, lighting, delivery, B-roll, SFX, transitions, media plan and music mood for every reel; `soundDesign` and `captionEmphasis` were extra AI calls during auto-edit → inconsistent results and extra cost. |
| 3 | Consistency | `ReelStyle` was only a label; nothing fixed the shots, captions, music or look per format. |
| 4 | Iraqi Arabic | Only a single sentence of dialect guidance inside a shared prompt; validation was an Arabic/Latin character ratio, which MSA passes. |
| 5 | Text | No normalisation of AI text (markdown, escapes, invisible characters). Compose `ScriptText` used Tajawal only when >25 % of characters were Arabic, so mixed lines fell back to system fonts. **Bidi bug:** captions (`FIRSTSTRONG_LTR`) and Compose (`TextDirection.Content`) laid out Arabic lines starting with a Latin device name left-to-right. |
| 6 | Beauty — weak effect | Live shader samples the camera texture in **sensor orientation**, but face geometry was supplied in **display orientation** (rotated & mirrored) → face mask in the wrong place, face-weighted smoothing at ~35 %; texel size derived from the output surface instead of the input texture; small preset values (e.g. brightness 0.03 × 0.22 ≈ 0.7 % change) → visually negligible. |
| 7 | Beauty — silent fallback | `CameraController.bind` silently dropped the effect in its last fallback; GL init exceptions on the processor thread were unhandled; UI always said "Beauty preview ON". |
| 8 | Camera UI | Independently positioned overlays with hard-coded paddings (teleprompter top 96 dp, hint bottom 140 dp, vertical exposure slider) → collisions on short screens / large fonts / long Arabic. |
| 9 | Review | Take review showed the raw (unfiltered) file, implying the beauty filter failed. |
| 10 | Data | No template fields; destructive migration would have been needed to add them without a Migration. |
