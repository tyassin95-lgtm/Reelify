# Reelify 2.2 — Product & UX Design Architecture

## Design Objective

Turn Reelify's 4-stage production workflow (**Choose -> Message -> Script -> Film -> Preview/Export**) into a calm, dependable daily creative workspace where empty databases stay clean, AI suggestions feel specialist-authored, and core video trimming never exposes experimental OpenGL complexity.

---

## 1. Ephemeral Stage 1–2 Creation Session (`CreationDraftSession`)

```
Home / ChooseReelScreen (Stage 1)
   │  Choose treatment, template, language (en / ar-IQ), duration (30/45/60s)
   ▼  tap Continue -> ChooseReelViewModel.startSession() [NO Room insert]
Navigation route: message/draft_<sessionId>?treatment=...&template=...&lang=...&dur=...
   │
   ▼
MessageScreen (Stage 2)
   ├─ Browse curated category topics (`TopicUiCategory`)
   ├─ Generate 5 grounded AI ideas / "More like this" / "Different angle"
   ├─ Save favorite ideas locally (`ContentRepository.saveIdeaForLater`)
   ├─ Press Back -> discards ephemeral session, Room stays 100% clean
   │
   ▼  tap "Use idea" / generate script
ScriptWriter.generate() succeeds
   │
   ▼
ProjectRepository.createFromTemplateWithScriptAtomically(...)
   └─ Single Room transaction inserts Project + 3 ReelSections + ScriptMeta + updates session
```

- **Why**: Browsing ideas or changing your mind mid-funnel must never pollute **Projects** with `"Draft"` shells. Process-death resilience on Stage 2 is preserved via URL query parameters (`treatment`, `template`, `lang`, `dur`) rebuilding the ephemeral draft seamlessly if `creationSessions` was cleared.

---

## 2. Primary vs. Secondary Separation in Trim (`TrimScreen.kt`)

Real device usage showed clinicians open Trim mostly for one fast task: **adjusting in/out cut points of a single recorded take or B-roll clip**.

- **Primary workspace (always visible, effect-free `TrimPlayerController`)**:
  - Clean 9:16 video preview (hardware-decoded via plain `ExoPlayer`, framing transform rendered via Compose `graphicsLayer`)
  - Live trimmed duration counter & target section comparison
  - Play / Pause & `"Preview selection"` (seeks to `trimStartMs`)
  - Filmstrip frame strip & speech audio envelope waveform
  - Safe non-inverting `RangeSlider`, `-0.1s` / `+0.1s` start/end nudge controls, Reset, Cancel, and `"Save trim"`
  - Explicit recovery card when metadata duration is unknown (`Retry metadata read` / `Remove clip` with `Save trim` safely disabled)
- **Secondary controls (collapsed behind `"Optional adjustments"`)**:
  - Framing zoom / rotation / flip controls
  - Clip audio volume & mute toggle
  - Clinical footage protection toggle (`isClinical`) & consent confirmation
  - Beauty preset overrides (applied at render/export time; optional guarded preview only)

---

## 3. Multi-Select Project Management & Cleanup (`ProjectsTab.kt`)

- Long-pressing any project card or tapping **"Select"** enters selection mode (`BackHandler(enabled = selectionMode)` exits cleanly).
- Action bar exposes **Select all**, **Deselect all**, and **Delete (N)** with confirmation dialog backed by `ProjectRepository.deleteMany(ids)` and per-project managed file cleanup (leaving external gallery files untouched).
- If empty legacy drafts exist from v2.0/v2.1 (`findEmptyLegacyDrafts()`), an explicit non-destructive banner offers one-tap selection (`Select empty drafts`) so users stay in control.

---

## 4. Navigation, Back Semantics & Restrained Motion

- Secondary tabs (**Projects**, **Studio**, **Settings**) intercept Android Back when `tab != 0` and switch cleanly to **Home** first (`MainScreen`).
- Screen transitions use calm directional slides (`FastOutSlowInEasing`, `240 ms` enter / `200 ms` exit, subtle fade) with no bouncy springs.
- Any stale back-stack route referencing a deleted project auto-exits cleanly to Home via `DeletedProjectGuard`.
