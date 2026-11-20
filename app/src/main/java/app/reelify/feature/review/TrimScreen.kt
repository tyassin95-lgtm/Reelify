package app.reelify.feature.review

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reelify.core.audio.Envelope
import app.reelify.core.audio.SpeechAnalysis
import app.reelify.core.designsystem.BottomActionBar
import app.reelify.core.designsystem.Chip
import app.reelify.core.designsystem.CinematicCard
import app.reelify.core.designsystem.ErrorBanner
import app.reelify.core.designsystem.Eyebrow
import app.reelify.core.designsystem.Expandable
import app.reelify.core.designsystem.GhostButton
import app.reelify.core.designsystem.InfoBanner
import app.reelify.core.designsystem.LabeledSlider
import app.reelify.core.designsystem.PrimaryButton
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.ScreenFrame
import app.reelify.core.designsystem.SecondaryButton
import app.reelify.core.designsystem.StatusPill
import app.reelify.core.designsystem.ToggleRow
import app.reelify.core.media.engine.singleClipPreview
import app.reelify.core.model.BeautyPreset
import app.reelify.core.model.BeautySettings
import app.reelify.core.model.ClipRole
import app.reelify.core.model.ConsentState
import app.reelify.core.model.MediaClip
import app.reelify.core.model.MediaKind
import app.reelify.core.model.Project
import app.reelify.feature.common.PlayerSurface
import app.reelify.feature.common.appContainer
import app.reelify.feature.common.rememberPreviewController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TrimScreen(cid: String, onBack: () -> Unit) {
    val c = appContainer()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var clip by remember { mutableStateOf<MediaClip?>(null) }
    var project by remember { mutableStateOf<Project?>(null) }
    var frames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var envelope by remember { mutableStateOf<Envelope?>(null) }
    var suggestionNote by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }
    var analyzingFace by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val controller = rememberPreviewController(ctx)
    val playing by controller.playing.collectAsStateWithLifecycle()
    val playerError by controller.error.collectAsStateWithLifecycle()
    var pos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cid) {
        val cl = c.projects.getClip(cid) ?: return@LaunchedEffect
        clip = cl
        project = c.projects.getProject(cl.projectId)
        val uri = c.storage.toUri(cl.uri)
        if (cl.kind == MediaKind.VIDEO) frames = c.thumbnails.filmstrip(uri, 0, cl.sourceDurationMs, 10)
        if (cl.hasAudio || cl.kind == MediaKind.AUDIO) envelope = c.audioAnalyzer.envelope(uri, hopMs = 40)
    }
    val cl = clip ?: return ScreenFrame("Trim", onBack) {}
    val p = project
    val sectionMs = p?.duration?.sectionMs ?: 10_000L

    // Rebuild the effect preview when look/transform/audio settings change (debounced); trims don't need a rebuild.
    val lookKey = listOf(cl.beauty, cl.transform, cl.muted, cl.volume, cl.faceTrack?.sampledAt, p?.edit?.color)
    LaunchedEffect(lookKey) {
        delay(250)
        val keep = controller.position().takeIf { it > 0 } ?: cl.trimStartMs
        controller.set(c.compositionFactory.singleClipPreview(cl, p?.edit?.color ?: app.reelify.core.model.ColorGrade()), keep)
    }
    LaunchedEffect(playing) {
        while (playing) {
            pos = controller.position()
            if (cl.kind != MediaKind.IMAGE && cl.trimEndMs > 0 && pos >= cl.trimEndMs) { controller.pause(); controller.seek(cl.trimStartMs) }
            delay(50)
        }
    }
    // Analyze face once when beauty gets enabled so face-localized effects work in preview.
    LaunchedEffect(cl.beauty.enabled) {
        if (cl.beauty.enabled && cl.faceTrack == null && cl.kind != MediaKind.AUDIO) {
            analyzingFace = true
            val track = c.faceAnalyzer.analyze(cl, c.storage.toUri(cl.uri))
            clip = clip?.copy(faceTrack = track)
            analyzingFace = false
        }
    }
    fun upd(t: (MediaClip) -> MediaClip) { clip = t(cl) }

    val len = cl.trimmedDurationMs
    ScreenFrame(
        when (cl.role) { ClipRole.PRIMARY -> "Trim & frame"; ClipRole.BROLL -> "B-roll"; ClipRole.VOICEOVER -> "Voiceover" }, onBack,
        subtitle = cl.displayName.ifBlank { null },
        bottomBar = { BottomActionBar { PrimaryButton("Accept clip", { saving = true; scope.launch { c.projects.upsertClip(cl); onBack() } }, Modifier.weight(1f), loading = saving) } },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (cl.kind != MediaKind.AUDIO) {
                Box(Modifier.fillMaxWidth(0.62f).align(Alignment.CenterHorizontally).aspectRatio(9f / 16f).clip(RoundedCornerShape(22.dp)).background(Color.Black)) {
                    PlayerSurface(controller, Modifier.fillMaxSize())
                    Box(Modifier.align(Alignment.BottomCenter).padding(10.dp).size(52.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                        IconButton({ if (!playing && cl.kind == MediaKind.VIDEO && (pos < cl.trimStartMs || pos >= cl.trimEndMs)) controller.seek(cl.trimStartMs); controller.toggle() }) {
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White)
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ if (!playing) controller.seek(cl.trimStartMs); controller.toggle() }) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Rf.Text) }
                    Text("Play selection", style = MaterialTheme.typography.titleSmall)
                }
            }
            playerError?.let { ErrorBanner(it) }

            if (cl.beauty.enabled) InfoBanner("Appearance-altering beauty effects are ON for this clip. The original file is kept untouched; effects are applied when rendering.", color = Rf.RoseSoft)

            if (cl.kind == MediaKind.VIDEO || cl.kind == MediaKind.AUDIO) CinematicCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Trim", modifier = Modifier.weight(1f))
                    val delta = len - sectionMs
                    if (cl.role == ClipRole.PRIMARY) StatusPill(
                        when { delta > 300 -> "${fmtSec(delta)} over section"; delta < -300 -> "${fmtSec(-delta)} short"; else -> "Fits ${fmtSec(sectionMs)}" },
                        when { delta > 300 -> Rf.Info; delta < -300 -> Rf.Warning; else -> Rf.Success },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(Rf.Surface3)) {
                    Row(Modifier.fillMaxSize()) { frames.forEach { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight()) } }
                    envelope?.let { env ->
                        Canvas(Modifier.fillMaxSize()) {
                            val n = env.values.size.coerceAtLeast(1); val mx = (env.values.maxOrNull() ?: 1f).coerceAtLeast(0.01f)
                            for (i in 0 until n step 2) {
                                val x = size.width * i / n; val h = size.height * 0.9f * env.values[i] / mx
                                drawLine(Color(0xAAFB7185), Offset(x, size.height / 2 - h / 2), Offset(x, size.height / 2 + h / 2), strokeWidth = 2f)
                            }
                        }
                    }
                    val dur = cl.sourceDurationMs.coerceAtLeast(1)
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Color(0xB3000000), size = androidx.compose.ui.geometry.Size(size.width * cl.trimStartMs / dur, size.height))
                        drawRect(Color(0xB3000000), topLeft = Offset(size.width * cl.trimEndMs / dur, 0f), size = androidx.compose.ui.geometry.Size(size.width * (1 - cl.trimEndMs.toFloat() / dur), size.height))
                        drawLine(Color.White, Offset(size.width * pos / dur, 0f), Offset(size.width * pos / dur, size.height), strokeWidth = 3f)
                    }
                }
                RangeSlider(
                    value = cl.trimStartMs.toFloat()..cl.trimEndMs.toFloat(),
                    onValueChange = { r -> val start = r.start.toLong(); val end = r.endInclusive.toLong().coerceAtLeast(start + 200)
                        if (start != cl.trimStartMs) controller.seek(start) else controller.seek(end)
                        upd { it.copy(trimStartMs = start, trimEndMs = end.coerceAtMost(it.sourceDurationMs)) } },
                    valueRange = 0f..cl.sourceDurationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Rf.Rose, inactiveTrackColor = Rf.Surface3),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("In ${fmtSec(cl.trimStartMs)}", style = MaterialTheme.typography.labelMedium)
                    Nudge { d -> upd { it.copy(trimStartMs = (it.trimStartMs + d).coerceIn(0, it.trimEndMs - 200)) }; controller.seek(cl.trimStartMs + d) }
                    Spacer(Modifier.weight(1f))
                    Text("Uses ${fmtSec(len)}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Nudge { d -> upd { it.copy(trimEndMs = (it.trimEndMs + d).coerceIn(it.trimStartMs + 200, it.sourceDurationMs)) }; controller.seek(cl.trimEndMs + d) }
                    Text("Out ${fmtSec(cl.trimEndMs)}", style = MaterialTheme.typography.labelMedium)
                }
                if (envelope != null && cl.role != ClipRole.BROLL) {
                    GhostButton("Suggest trim from speech", {
                        val s = SpeechAnalysis.suggestTrim(envelope!!, cl.sourceDurationMs, if (cl.role == ClipRole.PRIMARY) sectionMs else cl.sourceDurationMs)
                        upd { it.copy(trimStartMs = s.startMs, trimEndMs = s.endMs) }; controller.seek(s.startMs); suggestionNote = s.reason
                    }, icon = Icons.Rounded.AutoFixHigh)
                    suggestionNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            if (cl.kind == MediaKind.IMAGE) CinematicCard {
                Eyebrow("Still image timing")
                ToggleRow("Fill remaining section time", cl.trimEndMs == 0L, { v -> upd { it.copy(trimStartMs = 0, trimEndMs = if (v) 0 else 3000) } })
                if (cl.trimEndMs > 0) LabeledSlider("Duration", cl.trimEndMs / 1000f, { v -> upd { it.copy(trimEndMs = (v * 1000).toLong()) } }, range = 1f..(sectionMs / 1000f), valueText = fmtSec(cl.trimEndMs))
                Text("Stills get a subtle, controlled pan/zoom so they never look frozen.", style = MaterialTheme.typography.bodySmall)
            }

            if (cl.role == ClipRole.BROLL) CinematicCard {
                Eyebrow("Placement in section")
                ToggleRow("Automatic placement", cl.placementMs == null, { v -> upd { it.copy(placementMs = if (v) null else 0L) } }, subtitle = "Fills a gap at the end, otherwise cuts away after the opening")
                cl.placementMs?.let { pm -> LabeledSlider("Starts at", pm / 1000f, { v -> upd { it.copy(placementMs = (v * 1000).toLong()) } }, range = 0f..((sectionMs - 500) / 1000f), valueText = fmtSec(pm)) }
                Text("B-roll replaces the picture only — your dialogue continues underneath.", style = MaterialTheme.typography.bodySmall)
            }

            if (cl.kind != MediaKind.AUDIO) CinematicCard {
                Eyebrow("Frame (9:16)")
                LabeledSlider("Zoom", cl.transform.zoom, { v -> upd { it.copy(transform = it.transform.copy(zoom = v)) } }, range = 1f..2.5f, valueText = "%.2f×".format(cl.transform.zoom))
                if (cl.transform.zoom > 1.01f) {
                    LabeledSlider("Horizontal position", cl.transform.panX, { v -> upd { it.copy(transform = it.transform.copy(panX = v)) } }, range = -1f..1f, valueText = "%.2f".format(cl.transform.panX))
                    LabeledSlider("Vertical position", cl.transform.panY, { v -> upd { it.copy(transform = it.transform.copy(panY = v)) } }, range = -1f..1f, valueText = "%.2f".format(cl.transform.panY))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Rotate ${cl.transform.rotation}°", { upd { it.copy(transform = it.transform.copy(rotation = (it.transform.rotation + 90) % 360)) } }, icon = Icons.Rounded.RotateRight)
                    SecondaryButton(if (cl.transform.flipHorizontal) "Unflip" else "Mirror", { upd { it.copy(transform = it.transform.copy(flipHorizontal = !it.transform.flipHorizontal)) } }, icon = Icons.Rounded.Flip)
                }
            }

            if (cl.hasAudio || cl.kind == MediaKind.AUDIO) CinematicCard {
                Eyebrow("Audio")
                ToggleRow("Mute original audio", cl.muted, { v -> upd { it.copy(muted = v) } })
                LabeledSlider("Clip volume", cl.volume, { v -> upd { it.copy(volume = v) } }, range = 0f..2f, valueText = "${(cl.volume * 100).toInt()}%")
            }

            if (cl.kind != MediaKind.AUDIO) CinematicCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Beauty & skin", modifier = Modifier.weight(1f))
                    if (analyzingFace) StatusPill("Detecting face…", Rf.Info)
                }
                if (cl.isClinical) Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = Rf.Warning, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clinical footage: keep beauty OFF so skin and results are shown honestly.", style = MaterialTheme.typography.bodySmall.copy(color = Rf.Warning))
                }
                ToggleRow("Enable beauty filter", cl.beauty.enabled, { v -> upd { it.copy(beauty = if (v) BeautySettings.forPreset(it.beauty.preset.takeIf { p -> p != BeautyPreset.CUSTOM } ?: BeautyPreset.NATURAL, it.beauty.intensity) else it.beauty.copy(enabled = false)) } })
                if (cl.beauty.enabled) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BeautyPreset.entries.forEach { bp -> Chip(bp.label, cl.beauty.preset == bp, { upd { it.copy(beauty = if (bp == BeautyPreset.CUSTOM) it.beauty.copy(preset = bp) else BeautySettings.forPreset(bp, it.beauty.intensity)) } }) }
                    }
                    LabeledSlider("Intensity", cl.beauty.intensity, { v -> upd { it.copy(beauty = it.beauty.copy(intensity = v)) } })
                    GhostButton(if (advanced) "Hide fine controls" else "Fine controls", { advanced = !advanced })
                    Expandable(advanced) { BeautyControls(cl.beauty) { b -> upd { it.copy(beauty = b.copy(preset = BeautyPreset.CUSTOM)) } } }
                }
            }

            CinematicCard {
                Eyebrow("Clinical & consent")
                ToggleRow("Clinical / treatment footage", cl.isClinical, { v -> upd { it.copy(isClinical = v, beauty = if (v) it.beauty.copy(enabled = false) else it.beauty) } })
                ToggleRow("Shows an identifiable patient", cl.identifiablePatient, { v -> upd { it.copy(identifiablePatient = v, consent = if (v) ConsentState.NOT_CONFIRMED else ConsentState.NOT_REQUIRED) } })
                if (cl.identifiablePatient) ToggleRow("Patient consent obtained", cl.consent == ConsentState.CONFIRMED, { v -> upd { it.copy(consent = if (v) ConsentState.CONFIRMED else ConsentState.NOT_CONFIRMED) } })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Nudge(onNudge: (Long) -> Unit) {
    Row {
        Text("−", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clip(CircleShape).padding(horizontal = 8.dp).clickableNoRipple { onNudge(-100) })
        Text("+", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clip(CircleShape).padding(horizontal = 8.dp).clickableNoRipple { onNudge(100) })
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))

@Composable
fun BeautyControls(b: BeautySettings, onChange: (BeautySettings) -> Unit) {
    Column {
        LabeledSlider("Skin smoothing", b.smoothing, { onChange(b.copy(smoothing = it)) })
        LabeledSlider("Tone balancing", b.toneEven, { onChange(b.copy(toneEven = it)) })
        LabeledSlider("Under-eye", b.underEye, { onChange(b.copy(underEye = it)) })
        LabeledSlider("Facial refinement (mild)", b.faceRefine, { onChange(b.copy(faceRefine = it)) }, range = 0f..0.6f)
        LabeledSlider("Brightness", b.brightness, { onChange(b.copy(brightness = it)) }, range = -0.5f..0.5f)
        LabeledSlider("Exposure", b.exposure, { onChange(b.copy(exposure = it)) }, range = -0.5f..0.5f)
        LabeledSlider("Contrast", b.contrast, { onChange(b.copy(contrast = it)) }, range = -0.5f..0.5f)
        LabeledSlider("Saturation", b.saturation, { onChange(b.copy(saturation = it)) }, range = -0.5f..0.5f)
        LabeledSlider("Warmth", b.warmth, { onChange(b.copy(warmth = it)) }, range = -0.5f..0.5f)
        LabeledSlider("Highlight reduction", b.highlightReduction, { onChange(b.copy(highlightReduction = it)) })
        LabeledSlider("Shadows", b.shadowLift, { onChange(b.copy(shadowLift = it)) }, range = -0.5f..0.5f)
        LabeledSlider("Clarity", b.clarity, { onChange(b.copy(clarity = it)) })
        LabeledSlider("Sharpening", b.sharpen, { onChange(b.copy(sharpen = it)) })
    }
}
