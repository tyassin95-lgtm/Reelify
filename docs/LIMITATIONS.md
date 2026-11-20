# Limitations & honest notes

This document lists what is **not** done, what is approximated, and what could not be verified in
the build environment. Nothing here is hidden behind a non-functional button in the app.

## Verification limits of this build environment

* **No physical Android device was available.** The camera (CameraX capture, focus, exposure, white
  balance, torch, 4K/60 fps, live beauty preview), the teleprompter UI, real hardware codecs and
  real-world performance have **not** been exercised on hardware. They compile against the real
  APIs and follow documented CameraX/Media3 patterns, but must be validated on your phone.
* The only emulator available ran **without hardware acceleration** (QEMU TCG, SwiftShader GL,
  software codecs). Instrumented tests were run there where feasible; see `TESTING.md` for exactly
  which ones ran and their results. Emulator export tests render at 360×640 for runtime reasons;
  on a physical device the same tests render 1080×1920.
* **Live OpenAI / Deepgram calls were not made** (no credentials in the build environment). Both
  clients were tested against a local HTTP mock server that reproduces the documented request and
  response formats, error codes, retries and malformed output. The model id `gpt-6.2-sol` is used
  as specified; if your account exposes a different id, change it in *AI Connections → Advanced*
  (the *Test connection* button reports whether the configured model is visible to your key).

## Functional limitations / approximations

* **Beauty filter is applied to the preview live, and to recordings at render time** (with the
  same shader, parameters and on-device face tracking) rather than being burned into the camera
  file. This keeps originals clean and editable and makes preview/output consistent. Face tracking
  for rendering is sampled every ~250 ms and interpolated; very fast head motion can make the
  face-weighted mask lag slightly (the skin-colour mask still applies everywhere, so the result
  degrades gracefully rather than producing artifacts).
* Live-preview face coordinates assume preview and analysis share the 16:9 field of view; on
  devices that crop differently the under-eye/face weighting in the *camera preview* may be
  slightly offset. The render-time result uses frame-accurate geometry from the recorded file.
* **Crossfade/dissolve/slide transitions** blend from a snapshot of the outgoing clip's last frame
  into the moving incoming clip (a "freeze crossfade"), because Reelify keeps a single video
  sequence to guarantee exact section lengths. For short (≈0.4 s) transitions this is visually very
  close to a two-stream crossfade. If you seek directly into a transition window in preview, the
  snapshot may not exist yet and that one transition shows as a cut until played through.
* **Captions without Deepgram** are estimated from the approved script (evenly timed per section)
  and clearly labelled as such in the UI; word-accurate sync requires Deepgram.
* Arabic **word-by-word highlight** uses colour/pill emphasis rather than per-word scaling, to keep
  Arabic letter joining intact across word boundaries.
* The **interface-language** setting switches layout direction (RTL) and translates the primary
  navigation and actions; detailed screens are English. Reel *content* language (English / Iraqi
  Arabic) is fully supported independently.
* **Loudness normalization** is a speech-RMS based gain (≈ −20 dBFS speech) plus a −1 dBFS peak
  limiter, not a full ITU-R BS.1770 LUFS meter.
* **Preview at full quality** runs the same GPU pipeline as export (1080×1920). On less powerful
  phones enable *Settings → Export → Lower-resolution live preview* (720×1280 preview; export is
  always full quality).
* Bundled music is original procedurally-synthesized audio (CC0). It is tasteful background music,
  but it is not equivalent to a professionally produced commercial library; import your own
  licensed tracks for a specific sound.
* **60 fps export** is offered; it only adds smoothness if your source footage was recorded at
  60 fps.
* Treatments **CFU** and **Cleopatra** (and Picostar device specifics) are intentionally marked
  *Needs configuration*: the app does not invent what they are. Review all built-in treatment
  profiles before publishing.
* No cloud backup: uninstalling the app deletes projects (exports in the gallery remain).

## Security notes

* Embedded build-time keys can be extracted from the APK. Use dedicated, budget-limited keys and
  don't share the APK.
* In-app keys are encrypted with an Android Keystore AES-GCM key; this protects data at rest but
  not a rooted or otherwise compromised device.
* The encrypted key store is excluded from cloud backup/device transfer.
