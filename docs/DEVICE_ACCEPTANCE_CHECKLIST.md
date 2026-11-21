# Reelify 2.2 — Physical Device & Live API Acceptance Checklist

Before publishing or relying on Reelify 2.2 in daily clinical production, run through this on-device checklist on your target Android phone(s). Items below could not be hardware-verified in the headless CI build environment.

---

## 1. Ephemeral Session Lifecycle & Back Navigation (`Unverified on device`)

- [ ] Launch the installed APK (`Reelify-2.2.0-debug.apk` or signed release).
- [ ] Tap **New Reel**, step through **Choose Reel** (treatment, template, language, duration) and browse **Choose Message** (categories, suggested topics, *Save*, back navigation).
- [ ] Press system Back to Home **without** generating a script -> open **Projects** tab and confirm **no empty draft project** was created.
- [ ] Navigate to **Projects**, **Studio**, and **Settings** tabs -> press Android system **Back** on any non-Home tab -> verify Back switches to **Home** first before exiting the app.
- [ ] Screen transitions feel snappy and clean (no jarring bounce/elastic springs).

## 2. Bulk Project Management (`Unverified on device`)

- [ ] Long-press a project card in **Projects** (or tap `"Select"` / `"تحديد"`) to enter multi-selection mode.
- [ ] Select multiple old/empty projects, tap `"Delete selected"`, confirm the dialog -> verify deleted projects and their managed files disappear while remaining projects and Gallery originals stay intact.
- [ ] If legacy empty drafts exist from v2.0/v2.1, confirm the cleanup banner offers safe selection/removal and never auto-deletes anything silently.

## 3. Core Clip Trimmer Reliability & Honest Duration (`Unverified on device`)

- [ ] Record a 1080p vertical clip using the built-in CameraX screen (front and rear camera, beauty on and beauty off).
- [ ] Import an external clip from Gallery (including high-bitrate MP4 or HEVC, plus a static clinical photo).
- [ ] Open **Trim** on each video clip:
  - [ ] Preview loads cleanly without crash or freeze (`TrimPlayerController` effect-free plain `ExoPlayer`).
  - [ ] Filmstrip thumbnails and audio waveform populate.
  - [ ] Dragging `RangeSlider` start/end handles and tapping `-0.1s` / `+0.1s` nudge buttons scrubs smoothly without `IllegalArgumentException` or inverted slider thumbs.
  - [ ] Expand **Optional adjustments** -> toggle framing zoom/rotation/mirror (verify Compose `graphicsLayer` updates live without Media3 shader stalls), test audio gain/mute and clinical toggle.
  - [ ] Save trim, reopen **Trim**, and confirm saved in/out points persisted accurately.

## 4. Finished Reel Preview & Proxy Invalidation (`Unverified on device`)

- [ ] Complete all 3 sections for a 30s (`ar-IQ`), 45s (`ar-IQ`), and 60s (`en`) reel (including at least one B-roll cutaway and captions).
- [ ] Run Auto-Edit -> open **PreviewScreen**:
  - [ ] Preview proxy renders via `PreviewProxyManager` and plays cleanly.
  - [ ] Change a clip trim or music/color edit -> reopen **PreviewScreen** -> verify proxy re-renders cleanly for the new edit version (`editVersion` + file mtime invalidation).
- [ ] Export finished MP4 to Gallery (`Movies/Reelify`) and inspect playback outside Reelify:
  - [ ] Mixed Iraqi Arabic + English medical captions (`PicoStar`, `HIFU`, `1064 nm`, `Collagen`) appear right-to-left with proper Arabic letter joining and upright English Latin tokens.

## 5. Live OpenAI & Deepgram Verification (`Unverified — requires API keys`)

- [ ] Add OpenAI & Deepgram keys in *Settings → AI Connections* and tap **Test connection** for both.
- [ ] Test Stage 2 **"Generate 5 fresh ideas"**, **"More like this"**, **"Different angle"**, and **"Save"** on:
  - `PicoStar` · `myth_buster` · `ar-IQ` · 30s
  - `CFU Èlife` · `patient_question` · `ar-IQ` · 45s (confirm HIFU is never called a laser and body/hyperhidrosis claims do not leak into facial lifting)
  - `Aerolase` · `expert_explains` · `en` · 60s
- [ ] Confirm generated scripts:
  - Use approved English Latin spellings for medical terms in `ar-IQ` (`Collagen`, `Melasma`, `Epidermis`, `SMAS`, etc.) inside natural conversational Iraqi sentences.
  - Do not repeat points across Shots 1, 2, and 3 or end every shot with `"individual assessment"`.
- [ ] Optionally run the 32-scenario live creative eval benchmark:
  ```bash
  REELIFY_OPENAI_API_KEY=sk-... ./gradlew :app:testDebugUnitTest --tests 'app.reelify.eval.*'
  ```
  Review `app/build/reports/creative-eval.md` and save any corrected Iraqi phrasing in *Studio → Iraqi style guide*.
