package app.reelify.feature.review

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reelify.core.designsystem.BottomActionBar
import app.reelify.core.designsystem.Chip
import app.reelify.core.designsystem.CinematicCard
import app.reelify.core.designsystem.ErrorBanner
import app.reelify.core.designsystem.Eyebrow
import app.reelify.core.designsystem.GhostButton
import app.reelify.core.designsystem.GlowProgressBar
import app.reelify.core.designsystem.InfoBanner
import app.reelify.core.designsystem.PrimaryButton
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.ScreenFrame
import app.reelify.core.designsystem.ScriptText
import app.reelify.core.designsystem.SecondaryButton
import app.reelify.core.designsystem.StatusPill
import app.reelify.core.media.engine.IssueKind
import app.reelify.core.media.engine.ResolvedTimeline
import app.reelify.core.model.ClipRole
import app.reelify.core.model.ConsentState
import app.reelify.core.model.FillStrategy
import app.reelify.core.model.MediaClip
import app.reelify.core.model.MediaKind
import app.reelify.core.model.MediaPlan
import app.reelify.core.model.ProjectBundle
import app.reelify.core.model.ReelSection
import app.reelify.feature.common.ClipThumb
import app.reelify.feature.common.rfViewModel
import app.reelify.feature.common.strings
import app.reelify.feature.script.fmtRange

fun fmtSec(ms: Long) = "%.1fs".format(ms / 1000f)

/** Picker wiring shared by checklist & review. */
@Composable
private fun rememberPickers(vm: FootageViewModel): Pair<(ReelSection, ClipRole, String?) -> Unit, (MediaClip) -> Unit> {
    var target by remember { mutableStateOf<Triple<ReelSection, ClipRole, String?>?>(null) }
    var relinkTarget by remember { mutableStateOf<MediaClip?>(null) }
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris -> target?.let { (s, r, replace) -> vm.picked(s, r, uris, replace) } }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { target?.let { (s, r, replace) -> vm.picked(s, r, listOf(uri), replace) } ?: relinkTarget?.let { vm.relink(it, uri) } } }
    val pick: (ReelSection, ClipRole, String?) -> Unit = { s, r, replace ->
        target = Triple(s, r, replace); relinkTarget = null
        if (replace != null) single.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        else multi.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    val relink: (MediaClip) -> Unit = { clip -> target = null; relinkTarget = clip; single.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    return pick to relink
}

@Composable
private fun ConsentDialog(vm: FootageViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val p = ui.pending ?: return
    var clinical by remember(p) { mutableStateOf(p.clinicalHint) }
    var identifiable by remember(p) { mutableStateOf(false) }
    var consent by remember(p) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = vm::cancelPending, containerColor = Rf.Surface,
        title = { Text("Before adding this footage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CheckRow("Shows treatment / clinically representative skin", clinical) { clinical = it }
                Text("Beauty filters stay OFF for clinical footage so results aren't misrepresented.", style = MaterialTheme.typography.bodySmall)
                CheckRow("Shows an identifiable patient", identifiable) { identifiable = it }
                if (identifiable) {
                    CheckRow("I have the patient's consent to publish this", consent) { consent = it }
                    if (!consent) Text("You can import now, but the reel can't be exported until consent is confirmed.", style = MaterialTheme.typography.bodySmall.copy(color = Rf.Warning))
                }
                Text("Footage stays on this device. It is never sent to OpenAI.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ vm.confirmImport(clinical, identifiable, consent) }) { Text("Add ${p.uris.size} item${if (p.uris.size > 1) "s" else ""}") } },
        dismissButton = { TextButton(vm::cancelPending) { Text("Cancel") } },
    )
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = Rf.Rose))
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(color = Rf.Text))
    }
}

@Composable
fun ChecklistScreen(pid: String, onBack: () -> Unit, onRecord: (String, String) -> Unit, onReview: () -> Unit, onTrim: (String) -> Unit) {
    val vm = rfViewModel(pid) { FootageViewModel(it, pid) }
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val (pick, _) = rememberPickers(vm)
    val b = bundle ?: return
    ScreenFrame("Production checklist", onBack, subtitle = "Your reel needs three sections", bottomBar = {
        BottomActionBar { PrimaryButton("Review footage", onReview, Modifier.weight(1f), enabled = b.clips.isNotEmpty()) }
    }) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (ui.importing) item { GlowProgressBar(null) }
            ui.error?.let { item { ErrorBanner(it, onDismiss = vm::dismiss) } }
            item { Text("Sections don't need the same media type. Choose how you'll cover each one.", style = MaterialTheme.typography.bodyMedium) }
            b.sections.forEach { sec ->
                item(key = sec.id) {
                    val clips = b.clipsFor(sec.id)
                    CinematicCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Section ${sec.index} · ${sec.purpose.label}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusPill(if (clips.any { it.role == ClipRole.PRIMARY || it.role == ClipRole.BROLL }) "${clips.size} item${if (clips.size > 1) "s" else ""}" else "Needed", if (clips.isEmpty()) Rf.Warning else Rf.Success)
                        }
                        Text("${fmtRange(b, sec)} · ${sec.shotType.label}", style = MaterialTheme.typography.bodySmall)
                        if (sec.spokenScript.isNotBlank()) ScriptText(sec.spokenScript, style = MaterialTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MediaPlan.entries.forEach { mp -> Chip(mp.label, sec.mediaPlan == mp, { vm.setMediaPlan(sec, mp) }) }
                        }
                        Spacer(Modifier.height(12.dp))
                        SectionActions(sec, onRecord, pick)
                        if (clips.isNotEmpty()) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            clips.take(5).forEach { ClipThumb(it, Modifier.size(54.dp, 96.dp).clickable { onTrim(it.id) }) }
                        }
                    }
                }
            }
        }
    }
    ConsentDialog(vm)
}

@Composable
private fun SectionActions(sec: ReelSection, onRecord: (String, String) -> Unit, pick: (ReelSection, ClipRole, String?) -> Unit) {
    val s = strings()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (sec.mediaPlan) {
            MediaPlan.RECORD_TALKING -> { SecondaryButton(s.record, { onRecord(sec.id, "PRIMARY") }, icon = Icons.Rounded.Videocam); SecondaryButton(s.importMedia, { pick(sec, ClipRole.PRIMARY, null) }, icon = Icons.Rounded.FileUpload) }
            MediaPlan.IMPORT_FOOTAGE, MediaPlan.TREATMENT_BROLL, MediaPlan.STILL_IMAGE -> { SecondaryButton(s.importMedia, { pick(sec, ClipRole.PRIMARY, null) }, icon = Icons.Rounded.FileUpload); SecondaryButton(s.record, { onRecord(sec.id, "PRIMARY") }, icon = Icons.Rounded.Videocam) }
            MediaPlan.VOICEOVER -> { SecondaryButton("Record voiceover", { onRecord(sec.id, "VOICEOVER") }, icon = Icons.Rounded.Mic); SecondaryButton("Import visuals", { pick(sec, ClipRole.PRIMARY, null) }, icon = Icons.Rounded.FileUpload) }
        }
        GhostButton("Add B-roll", { pick(sec, ClipRole.BROLL, null) }, icon = Icons.Rounded.AddPhotoAlternate)
    }
}

@Composable
fun FootageReviewScreen(pid: String, onBack: () -> Unit, onRecord: (String, String) -> Unit, onTrim: (String) -> Unit, onAutoEdit: () -> Unit) {
    val vm = rfViewModel(pid) { FootageViewModel(it, pid) }
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val timeline by vm.timeline.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = strings()
    val (pick, relink) = rememberPickers(vm)
    val b = bundle ?: return
    val tl = timeline
    ScreenFrame("Footage review", onBack, subtitle = "${b.project.duration.label} · each section exactly ${b.project.duration.sectionMs / 1000}s", bottomBar = {
        BottomActionBar { PrimaryButton(s.autoEdit, onAutoEdit, Modifier.weight(1f), icon = Icons.Rounded.AutoFixHigh, enabled = tl?.blocking?.isEmpty() == true) }
    }) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (ui.importing) item { GlowProgressBar(null) }
            ui.error?.let { item { ErrorBanner(it, onDismiss = vm::dismiss) } }
            if (tl != null && tl.blocking.isNotEmpty()) item { InfoBanner("Fix the highlighted items to continue:\n• " + tl.blocking.joinToString("\n• ") { it.message }, color = Rf.Warning) }
            b.sections.forEach { sec -> item(key = sec.id) { ReviewSection(b, sec, tl, vm, onRecord, onTrim, pick, relink) } }
        }
    }
    ConsentDialog(vm)
}

@Composable
private fun ReviewSection(
    b: ProjectBundle, sec: ReelSection, tl: ResolvedTimeline?, vm: FootageViewModel,
    onRecord: (String, String) -> Unit, onTrim: (String) -> Unit, pick: (ReelSection, ClipRole, String?) -> Unit, relink: (MediaClip) -> Unit,
) {
    val target = b.project.duration.sectionMs
    val issues = tl?.issues?.filter { it.sectionId == sec.id }.orEmpty()
    val covered = tl?.video?.filter { it.sectionIndex == sec.index && it.clip != null }?.sumOf { it.durationMs } ?: 0
    val shortfall = issues.firstOrNull { it.kind == IssueKind.SHORTFALL }
    val clips = b.clipsFor(sec.id)
    val status = when {
        issues.any { it.blocking } -> "Needs attention" to Rf.Warning
        issues.any { it.kind == IssueKind.OVERFLOW } -> "Over — will cut" to Rf.Info
        else -> "Ready" to Rf.Success
    }
    CinematicCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Section ${sec.index} · ${sec.purpose.label}", style = MaterialTheme.typography.titleMedium)
                Text("Covered ${fmtSec(covered)} of ${fmtSec(target)}", style = MaterialTheme.typography.bodySmall)
            }
            StatusPill(status.first, status.second)
        }
        Spacer(Modifier.height(8.dp))
        GlowProgressBar(covered.toFloat() / target)
        issues.forEach { Text(it.message, style = MaterialTheme.typography.bodySmall.copy(color = if (it.blocking) Rf.Warning else Rf.TextSecondary), modifier = Modifier.padding(top = 6.dp)) }
        if (shortfall != null || b.project.edit.fill[sec.id] == FillStrategy.BRAND_CARD) {
            val on = b.project.edit.fill[sec.id] == FillStrategy.BRAND_CARD
            GhostButton(if (on) "Branded card fill: ON (tap to remove)" else "Fill the gap with a branded card", { vm.setFill(sec, if (on) FillStrategy.NONE else FillStrategy.BRAND_CARD) }, icon = Icons.Rounded.Badge)
        }
        Spacer(Modifier.height(10.dp))
        clips.forEach { clip -> ClipRow(clip, vm, onTrim, { pick(sec, clip.role, clip.id) }, relink) }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Record again", { onRecord(sec.id, "PRIMARY") }, icon = Icons.Rounded.Videocam)
            SecondaryButton("Import", { pick(sec, ClipRole.PRIMARY, null) }, icon = Icons.Rounded.FileUpload)
            GhostButton("Add B-roll", { pick(sec, ClipRole.BROLL, null) }, icon = Icons.Rounded.AddPhotoAlternate)
            GhostButton("Voiceover", { onRecord(sec.id, "VOICEOVER") }, icon = Icons.Rounded.Mic)
        }
    }
}

@Composable
private fun ClipRow(clip: MediaClip, vm: FootageViewModel, onTrim: (String) -> Unit, onReplace: () -> Unit, relink: (MediaClip) -> Unit) {
    val accessible = remember(clip.uri) { vm.isAccessible(clip) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(Rf.Surface2).clickable(enabled = accessible) { onTrim(clip.id) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (clip.kind == MediaKind.AUDIO) Box(Modifier.size(44.dp, 78.dp).clip(RoundedCornerShape(10.dp)).background(Rf.Surface3), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Mic, null, tint = Rf.RoseSoft) }
        else ClipThumb(clip, Modifier.size(44.dp, 78.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(when (clip.role) { ClipRole.PRIMARY -> if (clip.kind == MediaKind.IMAGE) "Still image" else "Main footage"; ClipRole.BROLL -> "B-roll cutaway"; ClipRole.VOICEOVER -> "Voiceover" }, style = MaterialTheme.typography.titleSmall)
            Text(
                if (clip.kind == MediaKind.IMAGE) "Fills remaining time" else "Uses ${fmtSec(clip.trimmedDurationMs)} of ${fmtSec(clip.sourceDurationMs)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (clip.beauty.enabled) StatusPill("Beauty on", Rf.RoseSoft)
                if (clip.isClinical) StatusPill("Clinical", Rf.Info)
                if (clip.identifiablePatient) StatusPill(if (clip.consent == ConsentState.CONFIRMED) "Consent ✓" else "Consent needed", if (clip.consent == ConsentState.CONFIRMED) Rf.Success else Rf.Warning)
            }
        }
        if (!accessible) IconButton({ relink(clip) }) { Icon(Icons.Rounded.LinkOff, "Relink missing media", tint = Rf.Warning) }
        else {
            if (clip.identifiablePatient && clip.consent != ConsentState.CONFIRMED) IconButton({ vm.confirmConsent(clip) }) { Icon(Icons.Rounded.VerifiedUser, "Confirm consent", tint = Rf.Warning) }
            if (clip.hasAudio) IconButton({ vm.toggleMute(clip) }) { Icon(if (clip.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, "Mute", tint = Rf.TextSecondary) }
            IconButton({ onTrim(clip.id) }) { Icon(Icons.Rounded.ContentCut, "Trim", tint = Rf.TextSecondary) }
            IconButton(onReplace) { Icon(Icons.Rounded.Movie, "Replace", tint = Rf.TextSecondary) }
        }
        IconButton({ vm.delete(clip) }) { Icon(Icons.Rounded.Delete, "Delete", tint = Rf.TextTertiary) }
    }
}
