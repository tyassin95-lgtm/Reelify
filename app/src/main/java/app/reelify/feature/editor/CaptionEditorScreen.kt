package app.reelify.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reelify.core.captions.CaptionBuilder
import app.reelify.core.captions.CaptionRemapper
import app.reelify.core.captions.TerminologyCorrector
import app.reelify.core.designsystem.CinematicCard
import app.reelify.core.designsystem.EmptyState
import app.reelify.core.designsystem.GhostButton
import app.reelify.core.designsystem.InfoBanner
import app.reelify.core.designsystem.ReelTextField
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.ScreenFrame
import app.reelify.core.designsystem.SecondaryButton
import app.reelify.core.designsystem.StatusPill
import app.reelify.core.model.CaptionSegment
import app.reelify.core.model.CaptionSource
import app.reelify.core.model.CaptionTrack
import app.reelify.feature.common.OverlayPreview
import app.reelify.feature.common.appContainer
import app.reelify.feature.preview.fmtTime
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun CaptionEditorScreen(pid: String, onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val bundle by remember(pid) { c.projects.observeBundle(pid) }.collectAsStateWithLifecycle(initialValue = null)
    var selected by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val b = bundle ?: return
    val stored = b.project.captions
    val tl = remember(b) { c.preparer.resolve(b) }
    // Edit captions as they currently map onto the timeline (survives trims made after transcription).
    val track = remember(stored, tl) { stored?.copy(segments = CaptionRemapper.remap(stored.segments, tl)) }
    fun save(segments: List<CaptionSegment>) = scope.launch {
        // Re-anchor edited words to their source audio so they keep following later trims.
        val anchored = segments.map { s -> s.copy(words = CaptionRemapper.anchor(s.words, tl.dialogue)) }
        c.projects.updateCaptions(pid, (stored ?: CaptionTrack(b.project.language, emptyList(), CaptionSource.MANUAL, System.currentTimeMillis())).copy(segments = anchored.sortedBy { it.startMs }))
    }
    ScreenFrame("Captions", onBack, subtitle = track?.let { "${it.segments.size} captions · ${when (it.source) { CaptionSource.DEEPGRAM -> "Deepgram"; CaptionSource.SCRIPT_ESTIMATE -> "estimated from script"; CaptionSource.MANUAL -> "manual" }}" }) {
        if (track == null || track.segments.isEmpty()) { EmptyState(Icons.Rounded.Spellcheck, "No captions yet", "Run automatic editing to transcribe your dialogue with Deepgram."); return@ScreenFrame }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (track.source == CaptionSource.SCRIPT_ESTIMATE) item { InfoBanner("These captions are timed from the script, not the recording. Connect Deepgram and rebuild for word-accurate sync.", color = Rf.Warning) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Apply terminology fixes", {
                        scope.launch {
                            val terms = c.library.terminology()
                            var count = 0
                            val fixed = track.segments.map { s -> val (w, f) = TerminologyCorrector.correct(s.words, terms, b.project.language); count += f.size; if (f.isEmpty()) s else s.copy(words = w, manuallyEdited = true) }
                            save(fixed); note = if (count == 0) "No terminology corrections needed." else "Corrected $count term(s)."
                        }
                    }, icon = Icons.Rounded.Spellcheck)
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            }
            itemsIndexed(track.segments, key = { _, s -> s.id }) { i, seg ->
                val open = selected == seg.id
                CinematicCard(onClick = { selected = if (open) null else seg.id }, selected = open) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${fmtTime(seg.startMs)} → ${fmtTime(seg.endMs)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        if (seg.manuallyEdited) StatusPill("edited", Rf.Info)
                        if (seg.words.any { it.confidence < 0.6f }) { Spacer(Modifier.width(6.dp)); StatusPill("check", Rf.Warning) }
                    }
                    if (!open) app.reelify.core.designsystem.ScriptText(seg.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
                    else {
                        var text by remember(seg.id, seg.text) { mutableStateOf(seg.text) }
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OverlayPreview((seg.startMs + seg.endMs) / 2 - seg.startMs + 1000, b.project.language, b.project.edit.captions.copy(enabled = true, burnIn = true), null, app.reelify.core.model.BrandingUse(enabled = false),
                                caption = CaptionBuilder.shiftSegment(CaptionBuilder.editSegment(seg, text), 1000 - seg.startMs), modifier = Modifier.width(110.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ReelTextField(text, { text = it }, minLines = 2)
                                Row { GhostButton("Save text", { save(track.segments.map { if (it.id == seg.id) CaptionBuilder.editSegment(it, text) else it }) }) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text("Start", style = MaterialTheme.typography.labelMedium)
                            GhostButton("−0.1", { save(track.segments.map { if (it.id == seg.id) CaptionBuilder.setSegmentRange(it, (it.startMs - 100).coerceAtLeast(0), it.endMs) else it }) })
                            GhostButton("+0.1", { save(track.segments.map { if (it.id == seg.id) CaptionBuilder.setSegmentRange(it, (it.startMs + 100).coerceAtMost(it.endMs - 200), it.endMs) else it }) })
                            Spacer(Modifier.weight(1f))
                            Text("End", style = MaterialTheme.typography.labelMedium)
                            GhostButton("−0.1", { save(track.segments.map { if (it.id == seg.id) CaptionBuilder.setSegmentRange(it, it.startMs, (it.endMs - 100).coerceAtLeast(it.startMs + 200)) else it }) })
                            GhostButton("+0.1", { save(track.segments.map { if (it.id == seg.id) CaptionBuilder.setSegmentRange(it, it.startMs, it.endMs + 100) else it }) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (i < track.segments.lastIndex) GhostButton("Merge with next", {
                                val next = track.segments[i + 1]
                                save(track.segments.filterNot { it.id == next.id }.map { if (it.id == seg.id) it.copy(endMs = next.endMs, words = it.words + next.words, manuallyEdited = true) else it })
                            }, icon = Icons.Rounded.MergeType)
                            Spacer(Modifier.weight(1f))
                            IconButton({ save(track.segments.filterNot { it.id == seg.id }); selected = null }) { Icon(Icons.Rounded.Delete, "Delete caption", tint = Rf.TextTertiary) }
                        }
                    }
                }
            }
        }
    }
}
