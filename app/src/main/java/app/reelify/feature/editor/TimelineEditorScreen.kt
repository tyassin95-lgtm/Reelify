package app.reelify.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reelify.core.designsystem.ErrorBanner
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.ScreenFrame
import app.reelify.core.library.SoundLibrary
import app.reelify.core.model.ClipRole
import app.reelify.feature.common.PlayerSurface
import app.reelify.feature.common.mediaModel
import app.reelify.feature.common.rememberPreviewController
import app.reelify.feature.common.rfViewModel
import app.reelify.feature.preview.fmtTime
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val TRACK_H = 46.dp

@Composable
fun TimelineEditorScreen(pid: String, onBack: () -> Unit) {
    val vm = rfViewModel(pid) { EditorViewModel(it, pid) }
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val controller = rememberPreviewController(ctx)
    val playing by controller.playing.collectAsStateWithLifecycle()
    var pos by remember { mutableLongStateOf(0L) }
    var pxPerSec by remember { mutableFloatStateOf(60f) }
    var sfxMenu by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    LaunchedEffect(ui.composition) { ui.composition?.let { controller.set(it, controller.position()) } }
    LaunchedEffect(Unit) { while (true) { pos = controller.position(); delay(50) } }
    LaunchedEffect(pos, playing) {
        if (playing) { val x = with(density) { (pos / 1000f * pxPerSec).dp.toPx() }.roundToInt(); if (x > scroll.value + scroll.viewportSize * 0.8f || x < scroll.value) scroll.scrollTo((x - scroll.viewportSize / 3).coerceAtLeast(0)) }
    }
    val b = bundle ?: return
    val tl = ui.timeline
    val total = b.project.duration.totalMs
    fun msToDp(ms: Long): Dp = (ms / 1000f * pxPerSec).dp

    ScreenFrame("Advanced editor", onBack, subtitle = "Three sections · ${b.project.duration.label}", actions = {
        IconButton(vm::undo, enabled = ui.canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo", tint = if (ui.canUndo) Rf.Text else Rf.TextTertiary) }
        IconButton(vm::redo, enabled = ui.canRedo) { Icon(Icons.AutoMirrored.Rounded.Redo, "Redo", tint = if (ui.canRedo) Rf.Text else Rf.TextTertiary) }
    }) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(0.9f), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxHeight().aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp)).background(Color.Black)) { PlayerSurface(controller, Modifier.fillMaxSize()) }
            }
            ui.error?.let { ErrorBanner(it, Modifier.padding(8.dp), onDismiss = vm::dismissError) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ controller.toggle() }) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Rf.Text) }
                Text("${fmtTime(pos)} / ${fmtTime(total)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Box {
                    IconButton({ sfxMenu = true }) { Icon(Icons.Rounded.GraphicEq, "Add SFX at playhead", tint = Rf.RoseSoft) }
                    DropdownMenu(sfxMenu, { sfxMenu = false }, containerColor = Rf.Surface2) {
                        SoundLibrary.sfx.forEach { s -> DropdownMenuItem({ Text("Add ${s.title}") }, { sfxMenu = false; vm.addSfx(s.id, pos) }) }
                    }
                }
                IconButton({ vm.splitAt(pos) }) { Icon(Icons.Rounded.ContentCut, "Split at playhead", tint = Rf.Text) }
                IconButton({ pxPerSec = (pxPerSec / 1.4f).coerceAtLeast(15f) }) { Icon(Icons.Rounded.ZoomOut, "Zoom out", tint = Rf.TextSecondary) }
                IconButton({ pxPerSec = (pxPerSec * 1.4f).coerceAtMost(400f) }) { Icon(Icons.Rounded.ZoomIn, "Zoom in", tint = Rf.TextSecondary) }
            }
            // ------------------------------------------------------------- timeline
            Box(Modifier.fillMaxWidth().background(Color(0xFF0E0E14)).horizontalScroll(scroll)) {
                Column(Modifier.width(msToDp(total) + 40.dp).padding(start = 8.dp, end = 32.dp, top = 6.dp, bottom = 6.dp)
                    .pointerInput(pxPerSec) { detectTapGestures { o -> controller.seek((o.x / density.density / pxPerSec * 1000).toLong()) } }) {
                    // ruler + sections
                    Box(Modifier.fillMaxWidth().height(26.dp)) {
                        for (i in 0 until 3) {
                            Box(Modifier.offset(x = msToDp(tl?.sectionMs?.times(i) ?: 0)).width(msToDp(tl?.sectionMs ?: total / 3)).fillMaxHeight()
                                .background(if (i % 2 == 0) Rf.Burgundy.copy(alpha = 0.25f) else Rf.Rose.copy(alpha = 0.12f))) {
                                Text("Section ${i + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
                            }
                        }
                    }
                    Track("Video") {
                        tl?.video?.forEach { seg ->
                            val sel = seg.clip?.id == ui.selectedClip && seg.clip != null
                            Box(Modifier.offset(x = msToDp(seg.timelineStartMs)).width(msToDp(seg.durationMs)).fillMaxHeight().padding(horizontal = 1.dp)
                                .clip(RoundedCornerShape(8.dp)).background(if (seg.clip == null) Rf.Gold.copy(alpha = 0.3f) else Rf.Surface3)
                                .border(if (sel) 2.dp else 0.5.dp, if (sel) Rf.Rose else Rf.Border, RoundedCornerShape(8.dp))
                                .clickable { vm.select(seg.clip?.id); controller.seek(seg.timelineStartMs) }) {
                                seg.clip?.let { c -> AsyncImage(mediaModel(c.thumbnailPath ?: c.uri), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.55f) }
                                Text(if (seg.clip == null) "Brand card" else if (seg.isCutaway) "B-roll" else seg.clip.displayName.ifBlank { "Clip" }, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp))
                                if (sel && seg.clip != null) TrimHandle(Modifier.align(Alignment.CenterEnd)) { dxPx ->
                                    val deltaMs = (dxPx / density.density / pxPerSec * 1000).toLong()
                                    val snappedEnd = TimelineOps.snap(seg.timelineEndMs + deltaMs, TimelineOps.markers(tl), 120) - seg.timelineStartMs
                                    vm.setTrimEnd(seg.clip.id, seg.sourceStartMs + snappedEnd.coerceAtLeast(TimelineOps.MIN_MS))
                                }
                            }
                        }
                    }
                    Track("Voice") {
                        tl?.dialogue?.forEach { seg ->
                            Box(Modifier.offset(x = msToDp(seg.timelineStartMs)).width(msToDp(seg.durationMs)).fillMaxHeight().padding(horizontal = 1.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(6.dp)).background(if (seg.clip.role == ClipRole.VOICEOVER) Rf.Info.copy(alpha = 0.45f) else Rf.Success.copy(alpha = 0.35f)).clickable { vm.select(seg.clip.id) })
                        }
                    }
                    Track("Music") {
                        val t = b.project.edit.music.trackId
                        if (t != null) Box(Modifier.fillMaxWidth().fillMaxHeight().padding(vertical = 8.dp).clip(RoundedCornerShape(6.dp)).background(Rf.Burgundy.copy(alpha = 0.45f))) {
                            Text(SoundLibrary.track(t)?.title ?: "Imported track", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                        }
                    }
                    Track("SFX") {
                        b.project.edit.sfx.forEach { cue ->
                            val sel = cue.id == ui.selectedCue
                            var dragX by remember(cue.id, cue.timeMs) { mutableFloatStateOf(0f) }
                            Box(Modifier.offset { IntOffset((msToDp(cue.timeMs).toPx() + dragX).roundToInt(), 0) }.size(22.dp, TRACK_H).padding(vertical = 10.dp)
                                .clip(CircleShape).background(if (sel) Rf.Rose else Rf.Gold)
                                .pointerInput(cue.id) {
                                    detectDragGestures(onDragEnd = { vm.moveCue(cue.id, cue.timeMs + (dragX / density.density / pxPerSec * 1000).toLong()) }) { ch, d -> ch.consume(); dragX += d.x }
                                }
                                .clickable { vm.selectCue(cue.id) })
                        }
                    }
                    Track("Captions") {
                        b.project.captions?.segments?.forEach { s ->
                            Box(Modifier.offset(x = msToDp(s.startMs)).width(msToDp(s.endMs - s.startMs)).fillMaxHeight().padding(horizontal = 1.dp, vertical = 6.dp).clip(RoundedCornerShape(6.dp)).background(Rf.Surface2)) {
                                Text(s.text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
                            }
                        }
                    }
                    Track("Overlays") {
                        val e = b.project.edit
                        if (e.effects.introTitle) OverlayBlock(msToDp(250), msToDp(2650), "Title")
                        if (e.branding.enabled && e.branding.lowerThird) OverlayBlock(msToDp(1300), msToDp(3600), "Lower third")
                        if (e.branding.enabled && e.branding.ctaOverlay) OverlayBlock(msToDp(total - e.branding.ctaDurationMs), msToDp(e.branding.ctaDurationMs), "CTA")
                    }
                }
                // playhead
                Box(Modifier.offset(x = 8.dp + msToDp(pos)).width(2.dp).height(TRACK_H * 6 + 40.dp).background(Color.White))
            }
            // ------------------------------------------------------------- selection actions
            if (ui.selectedClip != null) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionIcon(Icons.Rounded.ChevronLeft, "Move earlier") { vm.reorderSelected(-1) }
                ActionIcon(Icons.Rounded.ChevronRight, "Move later") { vm.reorderSelected(1) }
                TextAction("In −") { vm.trimSelected(-100, 0) }
                TextAction("In +") { vm.trimSelected(100, 0) }
                TextAction("Out −") { vm.trimSelected(0, -100) }
                TextAction("Out +") { vm.trimSelected(0, 100) }
                ActionIcon(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateSelected() }
                ActionIcon(Icons.AutoMirrored.Rounded.VolumeOff, "Mute") { vm.toggleMuteSelected() }
                ActionIcon(Icons.Rounded.Delete, "Delete") { vm.deleteSelected() }
            }
            if (ui.selectedCue != null) Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                Text("Drag the cue to move (snaps to cuts & sections)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                ActionIcon(Icons.Rounded.Delete, "Delete cue") { vm.deleteCue() }
            }
        }
    }
}

@Composable
private fun Track(label: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(TRACK_H).padding(vertical = 1.dp)) {
        content()
        Text(label, style = MaterialTheme.typography.labelSmall, color = Rf.TextTertiary, modifier = Modifier.align(Alignment.TopStart).padding(2.dp))
    }
}

@Composable
private fun OverlayBlock(x: Dp, w: Dp, label: String) {
    Box(Modifier.offset(x = x).width(w).fillMaxHeight().padding(vertical = 8.dp).clip(RoundedCornerShape(6.dp)).background(Rf.RoseSoft.copy(alpha = 0.3f))) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp, top = 4.dp), maxLines = 1)
    }
}

@Composable
private fun TrimHandle(modifier: Modifier, onDragEnd: (Float) -> Unit) {
    var dx by remember { mutableFloatStateOf(0f) }
    Box(modifier.offset { IntOffset(dx.roundToInt(), 0) }.width(14.dp).fillMaxHeight().background(Rf.Rose)
        .pointerInput(Unit) { detectHorizontalDragGestures(onDragEnd = { onDragEnd(dx); dx = 0f }) { ch, d -> ch.consume(); dx += d } })
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick) { Icon(icon, label, tint = Rf.Text) }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = Rf.Text, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Rf.Surface2).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp))
}
