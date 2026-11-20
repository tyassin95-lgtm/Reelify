package app.reelify.feature.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reelify.core.designsystem.BottomActionBar
import app.reelify.core.designsystem.Chip
import app.reelify.core.designsystem.CinematicCard
import app.reelify.core.designsystem.ErrorBanner
import app.reelify.core.designsystem.Eyebrow
import app.reelify.core.designsystem.GhostButton
import app.reelify.core.designsystem.InfoBanner
import app.reelify.core.designsystem.LabeledSlider
import app.reelify.core.designsystem.PrimaryButton
import app.reelify.core.designsystem.ReelTextField
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.ScreenFrame
import app.reelify.core.designsystem.SecondaryButton
import app.reelify.core.designsystem.Spinner
import app.reelify.core.designsystem.ToggleRow
import app.reelify.core.library.SoundLibrary
import app.reelify.core.model.BeautyPreset
import app.reelify.core.model.BeautySettings
import app.reelify.core.model.CaptionAnimation
import app.reelify.core.model.CaptionPresetId
import app.reelify.core.model.CaptionSource
import app.reelify.core.model.CaptionStyle
import app.reelify.core.model.ColorLook
import app.reelify.core.model.LogoPosition
import app.reelify.core.model.NoiseReduction
import app.reelify.core.model.ProjectBundle
import app.reelify.core.model.TransitionType
import app.reelify.feature.common.PlayerSurface
import app.reelify.feature.common.appContainer
import app.reelify.feature.common.rememberPreviewController
import app.reelify.feature.common.rfViewModel
import app.reelify.feature.common.strings
import kotlinx.coroutines.delay

private enum class QuickTab(val label: String) { AI("Ask AI"), CAPTIONS("Captions"), MUSIC("Music"), TRANSITIONS("Transitions"), COLOR("Color"), BEAUTY("Beauty"), BRANDING("Branding"), AUDIO("Audio"), EFFECTS("Effects") }

fun fmtTime(ms: Long) = "%d:%02d.%d".format(ms / 60000, (ms / 1000) % 60, (ms % 1000) / 100)

@Composable
fun PreviewScreen(pid: String, onBack: () -> Unit, onAdvanced: () -> Unit, onCaptions: () -> Unit, onFootage: () -> Unit, onScript: () -> Unit, onExport: () -> Unit, onRebuild: () -> Unit) {
    val vm = rfViewModel(pid) { PreviewViewModel(it, pid) }
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val s = strings()
    val controller = rememberPreviewController(ctx)
    val playing by controller.playing.collectAsStateWithLifecycle()
    val playerError by controller.error.collectAsStateWithLifecycle()
    var pos by remember { mutableLongStateOf(0L) }
    var tab by rememberSaveable { mutableStateOf(QuickTab.CAPTIONS) }

    LaunchedEffect(ui.composition) { ui.composition?.let { controller.set(it, controller.position()) } }
    LaunchedEffect(Unit) { while (true) { pos = controller.position(); delay(80) } }
    val b = bundle ?: return
    val total = b.project.duration.totalMs

    ScreenFrame(b.project.title, onBack, subtitle = "${b.project.duration.label} reel preview", actions = {
        IconButton(onAdvanced) { Icon(Icons.Rounded.ViewTimeline, "Advanced editor", tint = Rf.Text) }
    }, bottomBar = {
        BottomActionBar {
            SecondaryButton("Footage", onFootage, icon = Icons.Rounded.VideoLibrary)
            PrimaryButton(s.export, onExport, Modifier.weight(1f), icon = Icons.Rounded.IosShare, enabled = ui.timeline?.isRenderable == true)
        }
    }) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxHeight().aspectRatio(9f / 16f).clip(RoundedCornerShape(22.dp)).background(Color.Black).clickable { controller.toggle() }) {
                    PlayerSurface(controller, Modifier.fillMaxSize())
                    if (ui.building) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { Spinner(22.dp) }
                    if (!playing) Box(Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(Color(0x88000000)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                ui.buildError?.let { ErrorBanner(it) }
                playerError?.let { ErrorBanner(it) }
                ui.timeline?.blocking?.takeIf { it.isNotEmpty() }?.let { InfoBanner(it.joinToString("\n") { i -> i.message }, color = Rf.Warning) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ controller.toggle() }) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Rf.Text) }
                    Scrubber(pos, total, Modifier.weight(1f)) { controller.seek(it) }
                    Text(fmtTime(pos), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(QuickTab.entries) { t -> Chip(t.label, t == tab, { tab = t }, leading = if (t == QuickTab.AI) Icons.Rounded.AutoAwesome else null) }
            }
            Box(Modifier.fillMaxWidth().height(250.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickPanel(tab, b, vm, ui, onCaptions, onScript, onRebuild)
                }
            }
        }
    }
}

@Composable
fun Scrubber(pos: Long, total: Long, modifier: Modifier = Modifier, onSeek: (Long) -> Unit) {
    Canvas(modifier.height(28.dp).pointerInput(total) { detectTapGestures { o -> onSeek((o.x / size.width * total).toLong()) } }) {
        val y = size.height / 2
        drawRoundRect(Rf.Surface3, Offset(0f, y - 3f), Size(size.width, 6f), androidx.compose.ui.geometry.CornerRadius(3f))
        for (i in 1..2) drawLine(Rf.Gold.copy(alpha = 0.7f), Offset(size.width * i / 3, y - 8f), Offset(size.width * i / 3, y + 8f), 2f)
        val x = size.width * (pos.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
        drawRoundRect(Rf.Rose, Offset(0f, y - 3f), Size(x, 6f), androidx.compose.ui.geometry.CornerRadius(3f))
        drawCircle(Color.White, 8f, Offset(x, y))
    }
}

@Composable
private fun QuickPanel(tab: QuickTab, b: ProjectBundle, vm: PreviewViewModel, ui: PreviewUi, onCaptions: () -> Unit, onScript: () -> Unit, onRebuild: () -> Unit) {
    val e = b.project.edit
    when (tab) {
        QuickTab.AI -> {
            var req by remember { mutableStateOf("") }
            Text("Describe a change — e.g. “make it more cinematic”, “softer music”, “highlight more keywords”, “calmer clinic style”.", style = MaterialTheme.typography.bodySmall)
            ReelTextField(req, { req = it }, placeholder = "What should change?", minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(if (ui.aiBusy) "Thinking…" else "Apply", { vm.aiEdit(req) }, loading = ui.aiBusy, enabled = req.isNotBlank(), height = 46.dp)
                if (ui.canUndoAi) GhostButton("Undo AI change", vm::undoAi, icon = Icons.AutoMirrored.Rounded.Undo)
            }
            ui.aiSummary?.let { InfoBanner(it, color = Rf.Success) }
            ui.aiError?.let { ErrorBanner(it) }
            Text("The AI returns a declarative edit plan; Reelify applies it locally and deterministically.", style = MaterialTheme.typography.bodySmall)
        }
        QuickTab.CAPTIONS -> {
            val st = e.captions.style
            ToggleRow("Captions", e.captions.enabled, { v -> vm.edit { it.copy(captions = it.captions.copy(enabled = v)) } },
                subtitle = when (b.project.captions?.source) { CaptionSource.DEEPGRAM -> "Synced from Deepgram transcription"; CaptionSource.SCRIPT_ESTIMATE -> "Estimated from script — connect Deepgram for exact sync"; CaptionSource.MANUAL -> "Manually edited"; null -> "Not generated yet" })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptionPresetId.entries.filter { it != CaptionPresetId.CUSTOM }.forEach { id -> Chip(id.label, st.preset == id, { vm.edit { it.copy(captions = it.captions.copy(style = CaptionStyle.preset(id).copy(timingOffsetMs = st.timingOffsetMs))) } }) }
            }
            Eyebrow("Animation")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptionAnimation.entries.forEach { a -> Chip(a.label, st.animation == a, { vm.edit { it.copy(captions = it.captions.copy(style = st.copy(animation = a, preset = CaptionPresetId.CUSTOM))) } }) }
            }
            LabeledSlider("Size", st.sizeSp, { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(sizeSp = v, preset = CaptionPresetId.CUSTOM))) } }, range = 36f..96f, valueText = "${st.sizeSp.toInt()}")
            LabeledSlider("Vertical position", st.positionY, { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(positionY = v))) } }, range = 0.2f..0.8f)
            LabeledSlider("Words per caption", st.wordsPerCaption.toFloat(), { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(wordsPerCaption = v.toInt()))) } }, range = 1f..8f, valueText = "${st.wordsPerCaption}")
            LabeledSlider("Timing offset", st.timingOffsetMs.toFloat(), { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(timingOffsetMs = v.toLong()))) } }, range = -500f..500f, valueText = "${st.timingOffsetMs} ms")
            ToggleRow("Text background", st.backgroundEnabled, { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(backgroundEnabled = v, backgroundColor = if (v && st.backgroundColor == 0L) 0xCC000000 else st.backgroundColor))) } })
            LabeledSlider("Outline", st.strokeWidth, { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(strokeWidth = v))) } }, range = 0f..10f, valueText = "%.0f".format(st.strokeWidth))
            ToggleRow("Shadow", st.shadow, { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(shadow = v))) } })
            ToggleRow("Also export .srt subtitle file", e.captions.exportSrt, { v -> vm.edit { it.copy(captions = it.captions.copy(exportSrt = v)) } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Edit caption text", onCaptions, icon = Icons.Rounded.Subtitles)
                GhostButton("Save style", { vm.saveCaptionPreset("Style ${System.currentTimeMillis() % 1000}") })
            }
        }
        QuickTab.MUSIC -> {
            ToggleRow("Background music", e.music.trackId != null, { v -> vm.edit { it.copy(music = it.music.copy(trackId = if (v) SoundLibrary.match(b.project.plan?.musicMood, 0.6f, true).id else null)) } })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundLibrary.music.forEach { t -> Chip("${t.title} · ${t.mood.label}", e.music.trackId == t.id, { vm.edit { it.copy(music = it.music.copy(trackId = t.id)) } }) }
                val ctx = LocalContext.current
                appContainer().storage.musicDir().listFiles()?.forEach { f -> Chip("♪ ${f.nameWithoutExtension}", e.music.trackId == "file:${f.absolutePath}", { vm.edit { it.copy(music = it.music.copy(trackId = "file:${f.absolutePath}")) } }) }
            }
            LabeledSlider("Music volume", e.music.volume, { v -> vm.edit { it.copy(music = it.music.copy(volume = v)) } }, range = 0f..0.8f)
            ToggleRow("Duck under speech", e.music.ducking, { v -> vm.edit { it.copy(music = it.music.copy(ducking = v)) } })
            if (e.music.ducking) LabeledSlider("Ducking depth", e.music.duckDepth, { v -> vm.edit { it.copy(music = it.music.copy(duckDepth = v)) } }, range = 0.2f..0.9f)
            LabeledSlider("Fade in", e.music.fadeInMs / 1000f, { v -> vm.edit { it.copy(music = it.music.copy(fadeInMs = (v * 1000).toLong())) } }, range = 0f..4f, valueText = "%.1fs".format(e.music.fadeInMs / 1000f))
            LabeledSlider("Fade out", e.music.fadeOutMs / 1000f, { v -> vm.edit { it.copy(music = it.music.copy(fadeOutMs = (v * 1000).toLong())) } }, range = 0f..5f, valueText = "%.1fs".format(e.music.fadeOutMs / 1000f))
        }
        QuickTab.TRANSITIONS -> {
            Eyebrow("Between sections")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TransitionType.entries.forEach { t -> Chip(t.label, e.transitions.defaultType == t && e.transitions.overrides.isEmpty(), { vm.edit { it.copy(transitions = it.transitions.copy(defaultType = t, overrides = emptyMap())) } }) }
            }
            Eyebrow("Within sections (B-roll cuts)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TransitionType.entries.forEach { t -> Chip(t.label, e.transitions.withinSection == t, { vm.edit { it.copy(transitions = it.transitions.copy(withinSection = t)) } }) }
            }
            LabeledSlider("Transition length", e.transitions.durationMs.toFloat(), { v -> vm.edit { it.copy(transitions = it.transitions.copy(durationMs = v.toLong())) } }, range = 200f..900f, valueText = "${e.transitions.durationMs} ms")
            Text("Transitions happen inside the existing timeline, so the reel length never changes.", style = MaterialTheme.typography.bodySmall)
        }
        QuickTab.COLOR -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorLook.entries.forEach { l -> Chip(l.label, e.color.look == l, { vm.edit { it.copy(color = it.color.copy(look = l)) } }) }
            }
            LabeledSlider("Look intensity", e.color.intensity, { v -> vm.edit { it.copy(color = it.color.copy(intensity = v)) } })
            LabeledSlider("Brightness", e.color.brightness, { v -> vm.edit { it.copy(color = it.color.copy(brightness = v)) } }, range = -1f..1f, valueText = "%.2f".format(e.color.brightness))
            LabeledSlider("Contrast", e.color.contrast, { v -> vm.edit { it.copy(color = it.color.copy(contrast = v)) } }, range = -1f..1f, valueText = "%.2f".format(e.color.contrast))
            LabeledSlider("Saturation", e.color.saturation, { v -> vm.edit { it.copy(color = it.color.copy(saturation = v)) } }, range = -1f..1f, valueText = "%.2f".format(e.color.saturation))
            LabeledSlider("Warmth", e.color.warmth, { v -> vm.edit { it.copy(color = it.color.copy(warmth = v)) } }, range = -1f..1f, valueText = "%.2f".format(e.color.warmth))
            LabeledSlider("Highlights", e.color.highlights, { v -> vm.edit { it.copy(color = it.color.copy(highlights = v)) } }, range = -1f..1f, valueText = "%.2f".format(e.color.highlights))
            LabeledSlider("Shadows", e.color.shadows, { v -> vm.edit { it.copy(color = it.color.copy(shadows = v)) } }, range = -1f..1f, valueText = "%.2f".format(e.color.shadows))
        }
        QuickTab.BEAUTY -> {
            Text("Applies to talking footage only. Clinical / treatment clips are never beautified automatically.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Off", false, { vm.applyBeautyToTalkingClips(BeautySettings(enabled = false)) })
                BeautyPreset.entries.filter { it != BeautyPreset.CUSTOM }.forEach { p -> Chip(p.label, false, { vm.applyBeautyToTalkingClips(BeautySettings.forPreset(p)) }) }
            }
            Text("Fine-tune per clip from Footage → Trim. Faces are tracked on-device for face-localized smoothing and under-eye brightening.", style = MaterialTheme.typography.bodySmall)
        }
        QuickTab.BRANDING -> {
            val br = e.branding
            ToggleRow("Branding", br.enabled, { v -> vm.edit { it.copy(branding = br.copy(enabled = v)) } }, subtitle = if (b.project.brandSnapshot?.isConfigured == true) "Using your saved brand" else "Set up your brand in Studio → Branding")
            ToggleRow("Logo watermark", br.watermark, { v -> vm.edit { it.copy(branding = br.copy(watermark = v)) } })
            ToggleRow("Watermark only during treatment footage", br.watermarkOnlyOnTreatment, { v -> vm.edit { it.copy(branding = br.copy(watermarkOnlyOnTreatment = v)) } })
            ToggleRow("Name lower-third", br.lowerThird, { v -> vm.edit { it.copy(branding = br.copy(lowerThird = v)) } })
            ToggleRow("Booking CTA in final seconds", br.ctaOverlay, { v -> vm.edit { it.copy(branding = br.copy(ctaOverlay = v)) } })
            ToggleRow("Logo reveal outro", br.logoReveal, { v -> vm.edit { it.copy(branding = br.copy(logoReveal = v)) } })
            LabeledSlider("CTA duration", br.ctaDurationMs / 1000f, { v -> vm.edit { it.copy(branding = br.copy(ctaDurationMs = (v * 1000).toLong())) } }, range = 2f..8f, valueText = "%.1fs".format(br.ctaDurationMs / 1000f))
            Eyebrow("Logo position")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LogoPosition.entries.forEach { p -> Chip(p.name.replace('_', ' ').lowercase(), (br.logoPosition ?: b.project.brandSnapshot?.logoPosition) == p, { vm.edit { it.copy(branding = br.copy(logoPosition = p)) } }) }
            }
            LabeledSlider("Nudge logo horizontally", br.logoOffsetX, { v -> vm.edit { it.copy(branding = br.copy(logoOffsetX = v)) } }, range = -0.1f..0.1f, valueText = "%.2f".format(br.logoOffsetX))
            LabeledSlider("Nudge logo vertically", br.logoOffsetY, { v -> vm.edit { it.copy(branding = br.copy(logoOffsetY = v)) } }, range = -0.1f..0.1f, valueText = "%.2f".format(br.logoOffsetY))
        }
        QuickTab.AUDIO -> {
            val a = e.audio
            Eyebrow("Noise reduction (classical DSP, not generative)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NoiseReduction.entries.forEach { n -> Chip(n.label, a.noiseReduction == n, { vm.edit { it.copy(audio = a.copy(noiseReduction = n)) } }) } }
            LabeledSlider("Dialogue volume", a.dialogueVolume, { v -> vm.edit { it.copy(audio = a.copy(dialogueVolume = v)) } }, range = 0f..2f, valueText = "${(a.dialogueVolume * 100).toInt()}%")
            LabeledSlider("Sound effects volume", a.sfxVolume, { v -> vm.edit { it.copy(audio = a.copy(sfxVolume = v)) } })
            ToggleRow("Speech EQ (clarity)", a.speechEq, { v -> vm.edit { it.copy(audio = a.copy(speechEq = v)) } })
            ToggleRow("Compression", a.compression, { v -> vm.edit { it.copy(audio = a.copy(compression = v)) } })
            ToggleRow("Loudness normalization + limiter", a.normalize, { v -> vm.edit { it.copy(audio = a.copy(normalize = v)) } })
            Text("${e.sfx.size} sound effect cue(s). Add, move or remove them in the Advanced editor.", style = MaterialTheme.typography.bodySmall)
        }
        QuickTab.EFFECTS -> {
            val fx = e.effects
            ToggleRow("Slow digital push-in on talking shots", fx.pushIn, { v -> vm.edit { it.copy(effects = fx.copy(pushIn = v)) } })
            ToggleRow("Intro title animation", fx.introTitle, { v -> vm.edit { it.copy(effects = fx.copy(introTitle = v)) } })
            ToggleRow("Section title emphasis", fx.sectionTitles, { v -> vm.edit { it.copy(effects = fx.copy(sectionTitles = v)) } })
            ToggleRow("Soft light overlay", fx.lightOverlay, { v -> vm.edit { it.copy(effects = fx.copy(lightOverlay = v)) } })
            LabeledSlider("Vignette", fx.vignette, { v -> vm.edit { it.copy(effects = fx.copy(vignette = v)) } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Edit script", onScript, icon = Icons.Rounded.Edit)
                GhostButton("Rebuild automatically", onRebuild, icon = Icons.Rounded.Refresh)
            }
        }
    }
}
