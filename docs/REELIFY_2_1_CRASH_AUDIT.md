# Reelify 2.1 — Trim & Preview Crash Audit

## Scope & Methodology

This audit covers the device-reported crashes on Reelify 2.0 when opening the video trimmer (`TrimScreen`) and the finished-reel player (`PreviewScreen`), traced across commit `9fa22e8b12bed238f1981e5e4472903e76500daa`.

Where hardware Logcat was unavailable in the sandboxed CI environment, root causes were established via code-path analysis against Jetpack Media3 `1.11.1` `CompositionPlayer` / `EditedMediaItem` invariants, Jetpack Compose `RangeSlider` / `coerceIn` preconditions, and automated unit + Robolectric composition verification (`TrimBoundsTest`, `PreviewAndCompositionSafetyTest`). Physical hardware verification items remain explicitly tracked in [`TESTING.md`](TESTING.md).

---

## Root Causes Identified

### Crash Path A — `TrimScreen` Opening Crash

1. **Unnecessary `CompositionPlayer` pipeline for single-clip trimming**
   - *Prior flow*: `TrimScreen` -> `CompositionFactory.singleClipPreview()` -> `PreviewController` -> `CompositionPlayer`.
   - *Failure mode*: Media3 `CompositionPlayer` enforces strict composition validation and GL surface initialization across the effects pipeline, making simple trim scrubbing fragile on devices with strict codec/surface constraints or zero-audio/still sources.
2. **Missing `EditedMediaItem` durations (`C.TIME_UNSET`)**
   - *File*: `core/media/engine/CompositionFactory.kt` (`singleClipPreview`).
   - *Failure mode*: Video and audio `EditedMediaItem.Builder` calls omitted `.setDurationUs(...)`, leaving duration unset (`C.TIME_UNSET`), violating Media3 `CompositionPlayer` item duration requirements.
3. **Unprotected UI slider bounds & `coerceIn` throws (`IllegalArgumentException`)**
   - *File*: `feature/review/TrimScreen.kt`.
   - *Failure mode*: Short, unprobed, or `sourceDurationMs <= 200L` clips caused `trimEndMs - 200L < 0`, causing `(trimStartMs + d).coerceIn(0, trimEndMs - 200)` and `RangeSlider` ranges to throw `IllegalArgumentException: Cannot coerce value to an empty range`.
4. **Uncaught preparation exceptions on main thread**
   - *File*: `feature/common/PreviewPlayer.kt`.
   - *Failure mode*: Player preparation lacked `try/catch` guards around media binding, allowing codec, file-missing, or URI permission errors to crash the UI thread.

### Crash Path B — `PreviewScreen` Finished-Reel Crash

1. **Missing `EditedMediaItem` durations across multi-track sequences**
   - *File*: `core/media/engine/CompositionFactory.kt` (`videoSequence`, `dialogueSequence`, `musicSequence`, `sfxSequence`).
   - *Failure mode*: Video, dialogue, music loop, and SFX sequences omitted explicit `.setDurationUs(...)`, and `sfxSequence` also omitted `.setRemoveVideo(true)`. Passing multi-sequence graphs with unset item durations triggers `CompositionPlayer` validation exceptions (`IllegalStateException` / `IllegalArgumentException`).
2. **Real-time multi-track + custom GL effect `CompositionPlayer` instability**
   - *File*: `feature/preview/PreviewViewModel.kt`, `feature/common/PreviewPlayer.kt`.
   - *Failure mode*: Complex preview timelines combining beauty color shaders, reframe matrices, bitmap caption/brand overlays, ducked music, and SFX cues directly in live `CompositionPlayer` risk GL thread / decoder contention across OEM devices.
3. **Synchronous uncaught setup errors**
   - `PreviewController.set()` called `setComposition()` and `prepare()` without catching runtime exceptions or presenting graceful recovery controls.

---

## Implemented Fixes in Reelify 2.1

| Area | Change | Target Files |
|---|---|---|
| **Trim Player Architecture** | Replaced `CompositionPlayer` primary playback with standard Media3 `ExoPlayer` (`TrimPlayerController`) with automatic GPU effect fallback to plain effect-free `ExoPlayer` if a device effect throws, plus still-image `AsyncImage` preview handling and missing-duration metadata probing. | `feature/common/PreviewPlayer.kt`, `feature/review/TrimScreen.kt` |
| **Safe Trim Bounds Math** | Extracted pure `TrimBounds.validate()` + `ValidatedTrimBounds` guaranteeing `0 <= trimStartMs < trimEndMs <= sourceDurationMs`, safe nudges, non-empty `sliderRange`, and one-tap Reset / Cancel recovery. | `core/media/TrimBounds.kt`, `feature/review/TrimScreen.kt` |
| **Explicit Media3 Durations** | Added `itemSourceDurationUs()` and set `.setDurationUs(...)` on every `EditedMediaItem` across `videoSequence`, `dialogueSequence`, `musicSequence`, `sfxSequence` (+ `.setRemoveVideo(true)`), and `singleClipPreview`. | `core/media/engine/CompositionFactory.kt` |
| **Rendered Preview Proxy Architecture** | Added `PreviewProxyManager` using SHA-256 deterministic fingerprinting (`fingerprint(inputs)`), atomic `.tmp.mp4` -> `preview_proxy_<fp>.mp4` export via `TransformerRunner`, stale proxy reuse/cleanup, and standard `ExoPlayer` playback (`PreviewController.setFile()`). | `core/media/engine/PreviewProxyManager.kt`, `feature/preview/PreviewViewModel.kt`, `feature/preview/PreviewScreen.kt`, `AppContainer.kt` |
| **Fallback & Crash Containment** | Added guarded error recovery banners, retry trigger, and fallback to direct rendered MP4 if proxy rendering fails rather than crashing the Activity. | `feature/common/PreviewPlayer.kt`, `feature/preview/PreviewScreen.kt` |

---

## Automated Verification Coverage

- `app/src/test/java/app/reelify/review/TrimBoundsTest.kt`: verifies zero-duration, `<200ms` ultra-short, inverted bounds, rapid nudge stress, and reset invariants.
- `app/src/test/java/app/reelify/media/PreviewAndCompositionSafetyTest.kt`: verifies non-`C.TIME_UNSET` positive `durationUs` across single clips (audio/muted/trimmed/image/voiceover) and full multi-track compositions (beauty + B-roll + voiceover + music + SFX + captions + brand fill card), plus SHA-256 proxy fingerprint invalidation.
- **Remaining physical-device checks**: live Surface playback of CameraX-recorded HEVC/H.264 clips, real OEM GPU shader ShaderProgram compilation, and hardware `TransformerRunner` throughput must still be smoke-tested on a physical Android device (`TESTING.md`).
