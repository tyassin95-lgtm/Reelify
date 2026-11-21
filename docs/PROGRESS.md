# Reelify — implementation progress

## Reelify 2.0 (branch `reelify-v2-refactor`)

| Phase | Status |
|---|---|
| 1 Baseline audit | Done — `docs/V2_BASELINE_AUDIT.md` (HEAD 2490cb1, 80/80 tests) |
| 2 Template architecture | Done — models, 6 built-in templates, presets, planner, repository, versioned snapshots, Room v2 migration, legacy mode |
| 3 AI refactor | Done — script-only schema, ScriptWriter (generate/ideas/hooks/5 rewrite modes/editorial review), dedicated Iraqi prompt, 24-line style guide with owner review, 7 validation layers, normalizer, topic library with history |
| 4 Workflow | Done — Choose → Message/Script → Film → Preview & Export; legacy screens kept for 1.x projects |
| 5 Camera | Done — coordinated overlay scaffold, teleprompter band, template camera presets, recording feedback |
| 6 Beauty | Done — root-cause fixes (sensor-space face mapping, input texel size, face axes), calibrated presets, effect states, no silent fallback, hold-to-compare, filtered take review |
| 7 Auto editor | Done — template rules, no AI music/SFX/keyword calls, overrides respected, caption retry |
| 8 UI polish | Done for the main journey; older detail screens unchanged |
| 9 Regression | 150 JVM tests pass; on-device GPU/layout/Arabic tests pass; real on-device 1.x→2.0 upgrade verified; export results in TESTING.md |
| 10 Delivery | Debug APK `dist/Reelify-2.0.0-debug.apk`; release build unsigned (no keystore configured) |

## Next steps on your phone
1. Install the debug APK over 1.x (projects migrate automatically).
2. Add keys in Settings → AI Connections, then *Test connection*.
3. Review the Iraqi style guide lines (Studio).
4. Make one reel per template; check beauty with *Hold to compare*, front and rear camera.
5. Optionally run the live creative evaluation (`docs/eval/CREATIVE_EVAL.md`).

---

# Reelify — implementation progress

| Phase | Scope | Status |
|---|---|---|
| 1 | Gradle (AGP 9.5.2, Kotlin 2.4.21, compileSdk 37.2), Room, DataStore, Keystore secrets, domain model, design system | ✅ Done, compiles |
| 2 | Home, Projects, Studio, Settings, New Reel wizard, idea editor, script viewer | ✅ Done |
| 3 | OpenAI Responses API client, versioned prompts, strict schemas, validation + repair, selective regeneration, claims review | ✅ Done, mock-server tested |
| 4 | CameraX camera + GPU beauty preview, teleprompter, guides, voiceover recorder, photo-picker import, trim/reframe | ✅ Implemented — **not hardware-tested** |
| 5 | Timeline resolver (3 equal sections), Composition builder, CompositionPlayer preview, Transformer export | ✅ Done, unit + emulator tested (see TESTING.md) |
| 6 | Spectral denoiser/EQ/compressor/limiter, ducking, Deepgram client, caption segmentation/editing/remapping, SRT | ✅ Done |
| 7 | Beauty/colour shader, transitions, vignette/light, captions & branding overlay, brand templates | ✅ Done |
| 8 | Auto-edit pipeline, AI sound design / caption emphasis / edit guidance, quick adjustments | ✅ Done |
| 9 | Advanced timeline (split/trim/reorder/duplicate/delete/mute, SFX cues, snapping, undo/redo), caption editor | ✅ Done |
| 10 | Tests, docs, APK | ✅ Debug APK built and run on an emulator; minified release builds (sign with your keystore) |

## Deliverables

* Source: this repository.
* Debug APK: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
  (also delivered with the session hand-off).
* Docs: `README.md`, `docs/ARCHITECTURE.md`, `docs/TESTING.md`, `docs/LIMITATIONS.md`.
* Audio generator: `tools/generate_audio.py`.

## Suggested next steps on a real phone

1. Install the debug APK, add OpenAI + Deepgram keys, press *Test connection* for each.
2. Run the mandatory scenario (Iraqi Arabic · Aerolase · 30 s) end to end.
3. Run `./gradlew :app:connectedDebugAndroidTest` with the phone connected (full-resolution
   export tests).
4. Review every built-in treatment profile (Studio → Treatments) and fill in CFU / Cleopatra.
