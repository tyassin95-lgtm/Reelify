# Reelify 2.2 — Trim Crash Investigation, Effect-Free Player Architecture & Honest Duration Handling

## Background

In Reelify 2.0 and 2.1, opening the clip trimmer (`TrimScreen`) caused repeatable crashes on real user phones. While Reelify 2.1 switched primary trim playback from `CompositionPlayer` to `ExoPlayer`, it still constructed and passed Media3 GL effects (`Presentation`, `BeautyColorEffect`, color LUTs, crop/rotate matrices) via `player.setVideoEffects(...)` during initial load when a clip had non-identity transforms, beauty presets, or color grading, and `TrimBounds` fabricated a `500 ms` duration when metadata probing returned `<= 0L`.

---

## Root Causes Addressed in Reelify 2.2

1. **Unnecessary Media3 `VideoFrameProcessor` / OpenGL Effects Pipeline on Core Trim Entry**:
   - *Prior behavior*: `TrimScreen` called `buildTrimEffects(clip)` and `controller.load(..., effects = ...)`, invoking `ExoPlayer.setVideoEffects(...)` before first frame readiness. On devices with strict hardware codec/GL surface format combinations or unready decoder buffers, Media3's effect pipeline threw synchronously or asynchronously inside `VideoFrameProcessor`.
   - *v2.2 fix (`TrimPlayerController` in `feature/common/PreviewPlayer.kt`)*:
     - `TrimPlayerController.load(uri, startPositionMs, volume, play)` **never calls `player.setVideoEffects(...)`** and guarantees `isEffectFree == true`.
     - Core trimming runs solely on plain Media3 `ExoPlayer` decoding directly to `PlayerView`.
     - Framing adjustments (crop-zoom, rotation, horizontal flip) render smoothly in the UI layer via Compose `Modifier.graphicsLayer` on `TrimPlayerSurface` (`TrimScreen.kt`), keeping Media3 GL shaders out of the default trimmer path.
     - Optional GPU preview (`applyOptionalPreviewEffects`) exists only behind an isolated secondary action inside the collapsible `"Optional adjustments"` section and immediately reverts to effect-free playback if effect construction or `PlaybackException` occurs.
2. **Fabricated `500 ms` Fallback Duration (`TrimBounds.kt`)**:
   - *Prior behavior*: `TrimBounds.validate(0L, 0L, 0L)` substituted `FALLBACK_MIN_SOURCE_MS = 500L`, letting users manipulate sliders over fake bounds and persist corrupt clip durations into Room.
   - *v2.2 fix*:
     - `ValidatedTrimBounds.durationKnown: Boolean` explicitly distinguishes verified positive durations from unknown metadata (`durationKnown = false`, `sourceDurationMs = 0L`, `TrimBounds.canSave(bounds) == false`).
     - Multi-step honest duration discovery runs in `TrimScreen`: `MediaProbe.probe(uri)` -> `MediaProbe.probeDurationFallback(uri)` (`MediaMetadataRetriever` then `MediaExtractor`) -> `TrimPlayerController.discoveredDurationMs` populated on `Player.STATE_READY`.
     - While duration is unknown, `RangeSlider`, trim nudge controls, and `"Save trim"` are disabled and an explicit recovery card offers `"Retry metadata read"` and `"Remove clip"` without inventing numbers.
3. **Cluttered Trim Screen & Unscoped Coroutine Cancellation**:
   - *v2.2 fix*: Separated primary Trim workspace (9:16 preview player, current trimmed duration counter, play/pause + preview-selection buttons, filmstrip thumbnails, speech envelope waveform, trim `RangeSlider`, start/end nudges, reset, cancel, save) from secondary `"Optional adjustments"` (framing, audio volume/mute, clinical consent toggle, optional beauty filter controls).
   - Fixed `CancellationException` swallowing in `MediaProbe.probeDurationFallback`, `Thumbnails.kt`, and `AudioAnalyzer.kt` so structured coroutine cancellation propagates cleanly when leaving `TrimScreen`.
4. **Preview Stale-Cache Detection (`PreviewProxyManager.kt`)**:
   - Added source file `length` + `lastModified` and `edit.editVersion` to `PreviewProxyManager.fingerprint()`, plus `isProxyValid(file)` (`MediaMetadataRetriever` decodability check) so edited/replaced clips never serve corrupt or stale proxy MP4s.

---

## Verification Status

- `Implemented`: Effect-free `TrimPlayerController`, Compose `graphicsLayer` framing preview, honest `TrimBounds.durationKnown` + `canSave`, `MediaProbe.probeDurationFallback`, collapsible optional adjustments, `PreviewProxyManager` file-metadata fingerprinting & `MediaMetadataRetriever` validation.
- `Unit tested`: `TrimBoundsTest`, `NavigationAndBackSemanticsTest` (verifying `isEffectFree == true` on initialization and load even when beauty/color settings exist), `PreviewAndCompositionSafetyTest`.
- `Instrumented tested`: `ExportPipelineTest.effectFreeTrimPlayerLoadsDiscoversDurationAndSeeksCleanly` added and compiled against Android instrumentation APIs; **not executed on physical device / emulator in this build environment**.
- `Physical-device tested`: **Unverified** — real OEM decoder/GL surface behavior on user phones with CameraX recordings requires running the checklist in [`DEVICE_ACCEPTANCE_CHECKLIST.md`](DEVICE_ACCEPTANCE_CHECKLIST.md).
