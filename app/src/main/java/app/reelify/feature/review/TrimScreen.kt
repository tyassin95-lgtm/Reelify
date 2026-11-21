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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import app.reelify.core.media.TrimBounds
import app.reelify.core.model.BeautyPreset
import app.reelify.core.model.BeautySettings
import app.reelify.core.model.ClipRole
import app.reelify.core.model.ConsentState
import app.reelify.core.model.MediaClip
import app.reelify.core.model.MediaKind
import app.reelify.core.model.Project
import app.reelify.feature.common.TrimPlayerSurface
import app.reelify.feature.common.appContainer
import app.reelify.feature.common.mediaModel
import app.reelify.feature.common.rememberTrimPlayerController
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TrimScreen(cid: String, onBack: () -> Unit) {
    val c = appContainer()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var clip by remember { mutableStateOf<MediaClip?>(null) }
    var project by remember { mutableStateOf<Project?>(null) }
    var missingOrBroken by remember { mutableStateOf<String?>(null) }
    var frames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var envelope by remember { mutableStateOf<Envelope?>(null) }
    var suggestionNote by remember { mutableStateOf<String?>(null) }
    var showAdjustments by rememberSaveable { mutableStateOf(false) }
    var advancedBeauty by rememberSaveable { mutableStateOf(false) }
    var analyzingFace by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var loadingClip by remember { mutableStateOf(true) }
    var probeAttempt by remember { mutableIntStateOf(0) }

    val controller = rememberTrimPlayerController(ctx)
    val playing by controller.playing.collectAsStateWithLifecycle()
    val playerError by controller.error.collectAsStateWithLifecycle()
    val playerNotice by controller.notice.collectAsStateWithLifecycle()
    val playerDiscoveredDuration by controller.discoveredDurationMs.collectAsStateWithLifecycle()
    var pos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cid, probeAttempt) {
        loadingClip = true
        missingOrBroken = null
        val initial = c.projects.getClip(cid)
        if (initial == null) {
            missingOrBroken = "Clip could not be found in this project."
            loadingClip = false
            return@LaunchedEffect
        }
        project = c.projects.getProject(initial.projectId)
        val accessible = c.storage.isAccessible(initial.uri)
        if (!accessible) {
            clip = initial
            missingOrBroken = "This media file is missing or permission expired. Remove it and pick a replacement in the shot list."
            loadingClip = false
            return@LaunchedEffect
        }
        val uri = c.storage.toUri(initial.uri)
        var resolved: MediaClip = initial
        if (resolved.kind != MediaKind.IMAGE && resolved.sourceDurationMs <= 0L) {
            val probedInfo = runCatching { c.probe.probe(uri) }.getOrNull()
            val durMs = probedInfo?.durationMs?.takeIf { it > 0L } ?: c.probe.probeDurationFallback(uri)
            if (durMs > 0L) {
                val bounds = TrimBounds.validate(durMs, resolved.trimStartMs, resolved.trimEndMs)
                resolved = resolved.copy(
                    sourceDurationMs = bounds.sourceDurationMs,
                    trimStartMs = bounds.trimStartMs,
                    trimEndMs = bounds.trimEndMs,
                    width = if (resolved.width <= 0 && probedInfo != null) probedInfo.width else resolved.width,
                    height = if (resolved.height <= 0 && probedInfo != null) probedInfo.height else resolved.height,
                    hasAudio = resolved.hasAudio || (probedInfo?.hasAudio == true),
                )
            }
        } else if (resolved.kind != MediaKind.IMAGE) {
            val bounds = TrimBounds.validate(resolved.sourceDurationMs, resolved.trimStartMs, resolved.trimEndMs)
            resolved = resolved.copy(
                sourceDurationMs = bounds.sourceDurationMs,
                trimStartMs = bounds.trimStartMs,
                trimEndMs = bounds.trimEndMs,
            )
        }
        clip = resolved
        loadingClip = false
        if (resolved.kind == MediaKind.VIDEO && resolved.sourceDurationMs > 0L) {
            frames = c.thumbnails.filmstrip(uri, 0L, resolved.sourceDurationMs, 10)
        }
        if (resolved.hasAudio || resolved.kind == MediaKind.AUDIO) {
            envelope = c.audioAnalyzer.envelope(uri, hopMs = 40)
        }
    }

    // If initial probe returned 0L but ExoPlayer discovered valid duration on STATE_READY, adopt it cleanly.
    LaunchedEffect(playerDiscoveredDuration) {
        val cur = clip ?: return@LaunchedEffect
        if (cur.kind != MediaKind.IMAGE && cur.sourceDurationMs <= 0L && playerDiscoveredDuration > 0L) {
            val bounds = TrimBounds.validate(playerDiscoveredDuration, cur.trimStartMs, cur.trimEndMs)
            clip = cur.copy(
                sourceDurationMs = bounds.sourceDurationMs,
                trimStartMs = bounds.trimStartMs,
                trimEndMs = bounds.trimEndMs,
            )
            if (cur.kind == MediaKind.VIDEO && frames.isEmpty()) {
                frames = c.thumbnails.filmstrip(c.storage.toUri(cur.uri), 0L, bounds.sourceDurationMs, 10)
            }
        }
    }

    if (loadingClip && clip == null) {
        return ScreenFrame("Trim", onBack) {}
    }

    val cl = clip
    if (cl == null) {
        return ScreenFrame("Trim", onBack, bottomBar = {
            BottomActionBar { SecondaryButton("Return safely", onBack, Modifier.weight(1f)) }
        }) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ErrorBanner(missingOrBroken ?: "Clip unavailable.")
            }
        }
    }

    val p = project
    val sectionMs = p?.duration?.sectionMs ?: 10_000L
    val bounds = TrimBounds.validate(cl.sourceDurationMs, cl.trimStartMs, cl.trimEndMs)

    // Effect-free core ExoPlayer load (never touches GL/Presentation video effects).
    LaunchedEffect(cl.id, cl.uri, missingOrBroken, probeAttempt) {
        if (missingOrBroken != null || cl.kind == MediaKind.IMAGE) return@LaunchedEffect
        val uri = c.storage.toUri(cl.uri)
        val startAt = pos.takeIf { bounds.durationKnown && it in bounds.trimStartMs..bounds.trimEndMs }
            ?: bounds.trimStartMs
        controller.load(
            uri = uri,
            startPositionMs = startAt,
            volume = if (cl.muted) 0f else cl.volume,
            play = false,
        )
    }

    LaunchedEffect(cl.muted, cl.volume) {
        controller.setVolume(if (cl.muted) 0f else cl.volume)
    }

    LaunchedEffect(playing, bounds.trimStartMs, bounds.trimEndMs) {
        while (playing) {
            pos = controller.position()
            if (cl.kind != MediaKind.IMAGE && bounds.durationKnown && bounds.trimEndMs > bounds.trimStartMs && pos >= bounds.trimEndMs) {
                controller.pause()
                controller.seek(bounds.trimStartMs)
                pos = bounds.trimStartMs
            }
            delay(50)
        }
    }

    // Analyze face once when beauty gets enabled so face-localized effects work at export/preview.
    LaunchedEffect(cl.beauty.enabled) {
        if (cl.beauty.enabled && cl.faceTrack == null && cl.kind != MediaKind.AUDIO && missingOrBroken == null) {
            analyzingFace = true
            runCatching { c.faceAnalyzer.analyze(cl, c.storage.toUri(cl.uri)) }
                .onSuccess { track -> clip = clip?.copy(faceTrack = track) }
            analyzingFace = false
        }
    }

    fun upd(t: (MediaClip) -> MediaClip) {
        clip = clip?.let(t)
    }

    fun applyBounds(b: app.reelify.core.media.ValidatedTrimBounds, seekToMs: Long? = null) {
        if (!b.durationKnown) return
        upd { it.copy(sourceDurationMs = b.sourceDurationMs, trimStartMs = b.trimStartMs, trimEndMs = b.trimEndMs) }
        seekToMs?.let { s ->
            pos = s
            controller.seek(s)
        }
    }

    val len = if (cl.kind == MediaKind.IMAGE) cl.trimmedDurationMs else bounds.trimmedDurationMs
    val canSaveClip = missingOrBroken == null && !saving && (cl.kind == MediaKind.IMAGE || TrimBounds.canSave(bounds))

    ScreenFrame(
        when (cl.role) {
            ClipRole.PRIMARY -> "Trim clip"
            ClipRole.BROLL -> "Trim B-roll"
            ClipRole.VOICEOVER -> "Trim voiceover"
        },
        onBack,
        subtitle = cl.displayName.ifBlank { null },
        bottomBar = {
            BottomActionBar {
                SecondaryButton("Cancel", onBack)
                PrimaryButton(
                    "Save trim",
                    {
                        saving = true
                        scope.launch {
                            val toSave = if (cl.kind == MediaKind.IMAGE) cl else {
                                cl.copy(sourceDurationMs = bounds.sourceDurationMs, trimStartMs = bounds.trimStartMs, trimEndMs = bounds.trimEndMs)
                            }
                            c.projects.upsertClip(toSave)
                            onBack()
                        }
                    },
                    Modifier.weight(1f),
                    enabled = canSaveClip,
                    loading = saving,
                )
            }
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (missingOrBroken != null) {
                ErrorBanner(missingOrBroken!!)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Remove clip", {
                        scope.launch { c.projects.deleteClip(cl.projectId, cl.id); onBack() }
                    }, icon = Icons.Rounded.DeleteOutline)
                    SecondaryButton("Return safely", onBack)
                }
            } else if (cl.kind == MediaKind.IMAGE) {
                Box(
                    Modifier.fillMaxWidth(0.68f).align(Alignment.CenterHorizontally).aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(22.dp)).background(Color.Black),
                ) {
                    AsyncImage(
                        model = mediaModel(cl.uri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = cl.transform.zoom * if (cl.transform.flipHorizontal) -1f else 1f
                            scaleY = cl.transform.zoom
                            rotationZ = cl.transform.rotation.toFloat()
                        },
                    )
                }
            } else if (cl.kind != MediaKind.AUDIO) {
                Box(
                    Modifier.fillMaxWidth(0.68f).align(Alignment.CenterHorizontally).aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(22.dp)).background(Color.Black),
                ) {
                    TrimPlayerSurface(
                        controller,
                        Modifier.fillMaxSize().graphicsLayer {
                            scaleX = cl.transform.zoom * if (cl.transform.flipHorizontal) -1f else 1f
                            scaleY = cl.transform.zoom
                            translationX = cl.transform.panX * 48f
                            translationY = cl.transform.panY * 48f
                            rotationZ = cl.transform.rotation.toFloat()
                        },
                    )
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(10.dp).size(52.dp).clip(CircleShape).background(Color(0x99000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton({
                            if (!playing && bounds.durationKnown && (pos < bounds.trimStartMs || pos >= bounds.trimEndMs)) {
                                controller.seek(bounds.trimStartMs)
                                pos = bounds.trimStartMs
                            }
                            controller.toggle()
                        }) {
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White)
                        }
                    }
                    StatusPill(
                        if (bounds.durationKnown) "${fmtSec(pos)} / ${fmtSec(bounds.sourceDurationMs)}" else "${fmtSec(pos)} / --",
                        Color.White,
                        Modifier.align(Alignment.TopStart).padding(10.dp),
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({
                        if (!playing && bounds.durationKnown && (pos < bounds.trimStartMs || pos >= bounds.trimEndMs)) {
                            controller.seek(bounds.trimStartMs)
                        }
                        controller.toggle()
                    }) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Rf.Text)
                    }
                    val durTxt = if (bounds.durationKnown) fmtSec(bounds.sourceDurationMs) else "--"
                    Text("Play selection (${fmtSec(pos)} / $durTxt)", style = MaterialTheme.typography.titleSmall)
                }
            }

            playerError?.let { err ->
                ErrorBanner(err)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Replace / remove clip", {
                        scope.launch { c.projects.deleteClip(cl.projectId, cl.id); onBack() }
                    }, icon = Icons.Rounded.DeleteOutline)
                    SecondaryButton("Retry load", { probeAttempt++ }, icon = Icons.Rounded.Refresh)
                }
            }
            playerNotice?.let { InfoBanner(it, color = Rf.Info) }

            if ((cl.kind == MediaKind.VIDEO || cl.kind == MediaKind.AUDIO) && missingOrBroken == null && !bounds.durationKnown) {
                ErrorBanner("Duration metadata could not be read for this file yet. Tap Retry duration check or replace the clip — invalid trimming stays disabled.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Retry duration check", { probeAttempt++ }, icon = Icons.Rounded.Refresh)
                    SecondaryButton("Remove clip", {
                        scope.launch { c.projects.deleteClip(cl.projectId, cl.id); onBack() }
                    }, icon = Icons.Rounded.DeleteOutline)
                }
            }

            if ((cl.kind == MediaKind.VIDEO || cl.kind == MediaKind.AUDIO) && missingOrBroken == null && bounds.durationKnown) CinematicCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Trim", modifier = Modifier.weight(1f))
                    val delta = len - sectionMs
                    if (cl.role == ClipRole.PRIMARY) StatusPill(
                        when {
                            delta > 300 -> "${fmtSec(delta)} over section"
                            delta < -300 -> "${fmtSec(-delta)} short"
                            else -> "Fits ${fmtSec(sectionMs)}"
                        },
                        when {
                            delta > 300 -> Rf.Info
                            delta < -300 -> Rf.Warning
                            else -> Rf.Success
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(Rf.Surface3)) {
                    Row(Modifier.fillMaxSize()) {
                        frames.forEach { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight()) }
                    }
                    envelope?.let { env ->
                        Canvas(Modifier.fillMaxSize()) {
                            val n = env.values.size.coerceAtLeast(1)
                            val mx = (env.values.maxOrNull() ?: 1f).coerceAtLeast(0.01f)
                            for (i in 0 until n step 2) {
                                val x = size.width * i / n
                                val h = size.height * 0.9f * env.values[i] / mx
                                drawLine(Color(0xAAFB7185), Offset(x, size.height / 2 - h / 2), Offset(x, size.height / 2 + h / 2), strokeWidth = 2f)
                            }
                        }
                    }
                    val dur = bounds.sourceDurationMs.coerceAtLeast(1L)
                    Canvas(Modifier.fillMaxSize()) {
                        val startX = (size.width * bounds.trimStartMs / dur).coerceIn(0f, size.width)
                        val endX = (size.width * bounds.trimEndMs / dur).coerceIn(startX, size.width)
                        val playX = (size.width * pos / dur).coerceIn(0f, size.width)
                        drawRect(Color(0xB3000000), size = Size(startX, size.height))
                        drawRect(Color(0xB3000000), topLeft = Offset(endX, 0f), size = Size(size.width - endX, size.height))
                        drawLine(Rf.Rose, Offset(startX, 0f), Offset(startX, size.height), strokeWidth = 5f)
                        drawLine(Rf.Rose, Offset(endX, 0f), Offset(endX, size.height), strokeWidth = 5f)
                        drawLine(Color.White, Offset(playX, 0f), Offset(playX, size.height), strokeWidth = 3f)
                    }
                }
                RangeSlider(
                    value = bounds.sliderValue,
                    onValueChange = { r ->
                        val next = bounds.updateRange(r.start.toLong(), r.endInclusive.toLong())
                        val seek = if (next.trimStartMs != bounds.trimStartMs) next.trimStartMs else next.trimEndMs
                        applyBounds(next, seek)
                    },
                    valueRange = bounds.sliderRange,
                    enabled = bounds.durationKnown,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Rf.Rose, inactiveTrackColor = Rf.Surface3),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("In ${fmtSec(bounds.trimStartMs)}", style = MaterialTheme.typography.labelMedium)
                    Nudge { d ->
                        val next = bounds.nudgeStart(d)
                        applyBounds(next, next.trimStartMs)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Uses ${fmtSec(len)}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Nudge { d ->
                        val next = bounds.nudgeEnd(d)
                        applyBounds(next, next.trimEndMs)
                    }
                    Text("Out ${fmtSec(bounds.trimEndMs)}", style = MaterialTheme.typography.labelMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GhostButton("Preview selection", {
                        pos = bounds.trimStartMs
                        controller.previewSelection(bounds.trimStartMs)
                    }, icon = Icons.Rounded.PlayArrow)
                    GhostButton("Reset trim", {
                        val reset = bounds.reset()
                        applyBounds(reset, reset.trimStartMs)
                        suggestionNote = null
                    }, icon = Icons.Rounded.Refresh)
                    if (envelope != null && cl.role != ClipRole.BROLL) {
                        GhostButton("Suggest trim from speech", {
                            val s = SpeechAnalysis.suggestTrim(envelope!!, bounds.sourceDurationMs, if (cl.role == ClipRole.PRIMARY) sectionMs else bounds.sourceDurationMs)
                            val next = bounds.updateRange(s.startMs, s.endMs)
                            applyBounds(next, next.trimStartMs)
                            suggestionNote = s.reason
                        }, icon = Icons.Rounded.AutoFixHigh)
                    }
                }
                suggestionNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }

            if (cl.kind == MediaKind.IMAGE) CinematicCard {
                Eyebrow("Still image timing")
                ToggleRow("Fill remaining section time", cl.trimEndMs == 0L, { v -> upd { it.copy(trimStartMs = 0, trimEndMs = if (v) 0 else 3000) } })
                if (cl.trimEndMs > 0) {
                    val maxSec = (sectionMs / 1000f).coerceAtLeast(2f)
                    val curSec = (cl.trimEndMs / 1000f).coerceIn(1f, maxSec)
                    LabeledSlider("Duration", curSec, { v -> upd { it.copy(trimEndMs = (v * 1000).toLong()) } }, range = 1f..maxSec, valueText = fmtSec(cl.trimEndMs))
                }
                Text("Stills get a subtle, controlled pan/zoom so they never look frozen.", style = MaterialTheme.typography.bodySmall)
            }

            // Secondary optional adjustments kept cleanly behind an expandable section.
            CinematicCard(onClick = { showAdjustments = !showAdjustments }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, null, tint = Rf.RoseSoft, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Optional adjustments", style = MaterialTheme.typography.titleSmall)
                        val activeSummary = buildList {
                            if (cl.beauty.enabled) add("Beauty: ${cl.beauty.preset.label}")
                            if (cl.muted) add("Muted")
                            if (cl.transform.zoom > 1.01f || cl.transform.rotation != 0 || cl.transform.flipHorizontal) add("Reframed")
                            if (cl.isClinical) add("Clinical")
                        }.joinToString(" · ").ifBlank { "Framing, audio, beauty, B-roll & consent" }
                        Text(activeSummary, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(if (showAdjustments) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Rf.TextSecondary)
                }
            }

            Expandable(showAdjustments) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (cl.beauty.enabled) {
                        InfoBanner("Appearance-altering beauty effects are ON for this clip. The original file is kept untouched; effects render cleanly in preview/export.", color = Rf.RoseSoft)
                    }

                    if (cl.role == ClipRole.BROLL) CinematicCard {
                        Eyebrow("Placement in section")
                        ToggleRow("Automatic placement", cl.placementMs == null, { v -> upd { it.copy(placementMs = if (v) null else 0L) } }, subtitle = "Fills a gap at the end, otherwise cuts away after the opening")
                        cl.placementMs?.let { pm ->
                            val maxPlacementSec = ((sectionMs - 500L).coerceAtLeast(500L) / 1000f)
                            val curPlacementSec = (pm / 1000f).coerceIn(0f, maxPlacementSec)
                            LabeledSlider("Starts at", curPlacementSec, { v -> upd { it.copy(placementMs = (v * 1000).toLong()) } }, range = 0f..maxPlacementSec, valueText = fmtSec(pm))
                        }
                        Text("B-roll replaces the picture only — your dialogue continues underneath.", style = MaterialTheme.typography.bodySmall)
                    }

                    if (cl.kind != MediaKind.AUDIO) CinematicCard {
                        Eyebrow("Frame (9:16)")
                        LabeledSlider("Zoom", cl.transform.zoom.coerceIn(1f, 2.5f), { v -> upd { it.copy(transform = it.transform.copy(zoom = v)) } }, range = 1f..2.5f, valueText = "%.2f×".format(cl.transform.zoom))
                        if (cl.transform.zoom > 1.01f) {
                            LabeledSlider("Horizontal position", cl.transform.panX.coerceIn(-1f, 1f), { v -> upd { it.copy(transform = it.transform.copy(panX = v)) } }, range = -1f..1f, valueText = "%.2f".format(cl.transform.panX))
                            LabeledSlider("Vertical position", cl.transform.panY.coerceIn(-1f, 1f), { v -> upd { it.copy(transform = it.transform.copy(panY = v)) } }, range = -1f..1f, valueText = "%.2f".format(cl.transform.panY))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("Rotate ${cl.transform.rotation}°", { upd { it.copy(transform = it.transform.copy(rotation = (it.transform.rotation + 90) % 360)) } }, icon = Icons.Rounded.RotateRight)
                            SecondaryButton(if (cl.transform.flipHorizontal) "Unflip" else "Mirror", { upd { it.copy(transform = it.transform.copy(flipHorizontal = !it.transform.flipHorizontal)) } }, icon = Icons.Rounded.Flip)
                        }
                    }

                    if (cl.hasAudio || cl.kind == MediaKind.AUDIO) CinematicCard {
                        Eyebrow("Audio")
                        ToggleRow("Mute original audio", cl.muted, { v -> upd { it.copy(muted = v) } })
                        LabeledSlider("Clip volume", cl.volume.coerceIn(0f, 2f), { v -> upd { it.copy(volume = v) } }, range = 0f..2f, valueText = "${(cl.volume * 100).toInt()}%")
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
                            LabeledSlider("Intensity", cl.beauty.intensity.coerceIn(0f, 1f), { v -> upd { it.copy(beauty = it.beauty.copy(intensity = v)) } })
                            GhostButton(if (advancedBeauty) "Hide fine controls" else "Fine controls", { advancedBeauty = !advancedBeauty })
                            Expandable(advancedBeauty) { BeautyControls(cl.beauty) { b -> upd { it.copy(beauty = b.copy(preset = BeautyPreset.CUSTOM)) } } }
                        }
                    }

                    CinematicCard {
                        Eyebrow("Clinical & consent")
                        ToggleRow("Clinical / treatment footage", cl.isClinical, { v -> upd { it.copy(isClinical = v, beauty = if (v) it.beauty.copy(enabled = false) else it.beauty) } })
                        ToggleRow("Shows an identifiable patient", cl.identifiablePatient, { v -> upd { it.copy(identifiablePatient = v, consent = if (v) ConsentState.NOT_CONFIRMED else ConsentState.NOT_REQUIRED) } })
                        if (cl.identifiablePatient) ToggleRow("Patient consent obtained", cl.consent == ConsentState.CONFIRMED, { v -> upd { it.copy(consent = if (v) ConsentState.CONFIRMED else ConsentState.NOT_CONFIRMED) } })
                    }
                }
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
        LabeledSlider("Skin smoothing", b.smoothing.coerceIn(0f, 1f), { onChange(b.copy(smoothing = it)) })
        LabeledSlider("Tone balancing", b.toneEven.coerceIn(0f, 1f), { onChange(b.copy(toneEven = it)) })
        LabeledSlider("Under-eye", b.underEye.coerceIn(0f, 1f), { onChange(b.copy(underEye = it)) })
        LabeledSlider("Facial refinement (mild)", b.faceRefine.coerceIn(0f, 0.6f), { onChange(b.copy(faceRefine = it)) }, range = 0f..0.6f)
        LabeledSlider("Brightness", b.brightness.coerceIn(-0.5f, 0.5f), { onChange(b.copy(brightness = it)) }, range = -0.5f..0.5f)
        LabeledSlider("Exposure", b.exposure.coerceIn(-0.5f, 0.5f), { onChange(b.copy(exposure = it)) }, range = -0.5f..0.5f)
        LabeledSlider("Contrast", b.contrast.coerceIn(-0.5f, 0.5f), { onChange(b.copy(contrast = it)) }, range = -0.5f..0.5f)
        LabeledSlider("Saturation", b.saturation.coerceIn(-0.5f, 0.5f), { onChange(b.copy(saturation = it)) }, range = -0.5f..0.5f)
        LabeledSlider("Warmth", b.warmth.coerceIn(-0.5f, 0.5f), { onChange(b.copy(warmth = it)) }, range = -0.5f..0.5f)
        LabeledSlider("Highlight reduction", b.highlightReduction.coerceIn(0f, 1f), { onChange(b.copy(highlightReduction = it)) })
        LabeledSlider("Shadows", b.shadowLift.coerceIn(-0.5f, 0.5f), { onChange(b.copy(shadowLift = it)) }, range = -0.5f..0.5f)
        LabeledSlider("Clarity", b.clarity.coerceIn(0f, 1f), { onChange(b.copy(clarity = it)) })
        LabeledSlider("Sharpening", b.sharpen.coerceIn(0f, 1f), { onChange(b.copy(sharpen = it)) })
    }
}
