package app.reelify.feature.script

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reelify.core.designsystem.BottomActionBar
import app.reelify.core.designsystem.Chip
import app.reelify.core.designsystem.CinematicCard
import app.reelify.core.designsystem.ErrorBanner
import app.reelify.core.designsystem.Eyebrow
import app.reelify.core.designsystem.GhostButton
import app.reelify.core.designsystem.InfoBanner
import app.reelify.core.designsystem.PrimaryButton
import app.reelify.core.designsystem.ReelTextField
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.ScreenFrame
import app.reelify.core.designsystem.ScriptText
import app.reelify.core.designsystem.SecondaryButton
import app.reelify.core.designsystem.Spinner
import app.reelify.core.designsystem.StatusPill
import app.reelify.core.designsystem.StepIndicator
import app.reelify.core.model.ClaimFlag
import app.reelify.core.model.ClaimSeverity
import app.reelify.core.model.ProjectBundle
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelSection
import app.reelify.core.model.ShotType
import app.reelify.core.timing.TimingEstimate
import app.reelify.core.timing.TimingFit
import app.reelify.feature.common.rfViewModel
import app.reelify.feature.common.strings
import app.reelify.feature.create.AiWorking
import app.reelify.integration.openai.SectionTask

fun fitColor(f: TimingFit) = when (f) { TimingFit.FITS -> Rf.Success; TimingFit.TOO_LONG -> Rf.Danger; TimingFit.TOO_SHORT -> Rf.Warning; TimingFit.EMPTY -> Rf.TextTertiary }

@Composable
fun TimingBadge(est: TimingEstimate) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusPill(est.fit.label, fitColor(est.fit))
        Spacer(Modifier.width(8.dp))
        Text("≈ %.1fs of %ds · %d words".format(est.totalMs / 1000f, est.targetMs / 1000, est.words), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ScriptScreen(pid: String, onBack: () -> Unit, onApproved: () -> Unit, onEditIdea: () -> Unit) {
    val vm = rfViewModel(pid) { ScriptViewModel(it, pid) }
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val timing by vm.timing.collectAsStateWithLifecycle()
    val s = strings()
    var fullScript by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ReelSection?>(null) }
    var regenFor by remember { mutableStateOf<ReelSection?>(null) }
    val b = bundle ?: return

    if (ui.busy != null && ui.busySection == null) { AiWorking(ui.busy!!, vm::cancelWork); return }
    val hasPlan = b.sections.any { it.spokenScript.isNotBlank() || it.cameraDirection.isNotBlank() }
    val flags = vm.localFlags(b) + ui.aiFlags

    ScreenFrame(b.project.plan?.title?.ifBlank { null } ?: "Script & shot plan", onBack,
        subtitle = "${b.project.duration.label} · 3 × ${b.project.duration.sectionMs / 1000}s · ${b.project.language.displayName}",
        actions = {
            IconButton({ fullScript = !fullScript }) { Icon(if (fullScript) Icons.Rounded.ViewAgenda else Icons.Rounded.Notes, "Toggle full script", tint = Rf.Text) }
        },
        bottomBar = {
            BottomActionBar {
                if (!hasPlan) PrimaryButton(s.generateScript, { vm.generatePlan() }, Modifier.weight(1f), icon = Icons.Rounded.AutoAwesome)
                else PrimaryButton(if (b.project.planApproved) "Continue to filming" else s.approvePlan, { vm.approve(onApproved) }, Modifier.weight(1f), icon = Icons.Rounded.CheckCircle,
                    enabled = flags.none { it.severity == ClaimSeverity.BLOCKER })
            }
        },
    ) {
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { StepIndicator(3, 4) }
            ui.error?.let { e -> item { ErrorBanner(e, onDismiss = vm::dismissError) } }
            if (!hasPlan) item {
                CinematicCard {
                    Text("No script yet", style = MaterialTheme.typography.titleLarge)
                    Text("Generate a complete plan with Reelify Director, or write each section yourself using Edit.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Back to idea", onEditIdea)
                }
            }
            b.project.plan?.let { plan ->
                if (plan.hook.isNotBlank()) item {
                    CinematicCard {
                        Eyebrow("Hook"); Spacer(Modifier.height(6.dp))
                        ScriptText(plan.hook, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (plan.targetAudience.isNotBlank()) StatusPill("Audience: ${plan.targetAudience}".take(48), Rf.Info)
                            StatusPill("Music: ${plan.musicMood.label}", Rf.RoseSoft)
                            if (plan.creativeStyle.isNotBlank()) StatusPill(plan.creativeStyle.take(40), Rf.Gold)
                        }
                    }
                }
            }
            if (flags.isNotEmpty()) item { ClaimsCard(flags) }
            if (fullScript) item { FullScript(b) }
            else b.sections.forEach { sec ->
                item(key = sec.id) {
                    SectionCard(
                        b, sec, timing.estimate(sec.spokenScript, b.project.language, b.project.duration.sectionMs, if (sec.shotType.needsSpeech) 0f else 1f),
                        busy = ui.busySection == sec.id,
                        onEdit = { editing = sec }, onRegenerate = { regenFor = sec }, onLock = { vm.toggleLock(sec) },
                        onShot = { vm.changeShot(sec, it) }, onUndo = { vm.undo(sec) },
                        onShorten = { vm.regenerate(sec.id, "Shorten this dialogue while retaining the main message.", SectionTask.SHORTEN) },
                        revisionCount = { vm.revisionCount(sec.id) },
                    )
                }
            }
            if (hasPlan) item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Review claims", { vm.reviewClaims() }, Modifier.weight(1f), icon = Icons.Rounded.Gavel)
                    SecondaryButton("Regenerate unlocked", { vm.generatePlan() }, Modifier.weight(1f), icon = Icons.Rounded.AutoAwesome)
                }
            }
            if (ui.claimReviewDone && ui.aiFlags.isEmpty()) item { InfoBanner("AI claim review found no issues.", color = Rf.Success) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editing?.let { sec -> SectionEditorSheet(sec, b.project.language, onDismiss = { editing = null }, onSave = { vm.saveSection(it); editing = null }) }
    regenFor?.let { sec -> RegenerateSheet(sec, b.project.language, onDismiss = { regenFor = null }, onGo = { instr, task -> vm.regenerate(sec.id, instr, task); regenFor = null }) }
    ui.proposal?.let { p -> ProposalSheet(p, onAccept = vm::acceptProposal, onRetry = vm::retryProposal, onCancel = vm::cancelProposal, busy = ui.busySection != null) }
}

@Composable
private fun ClaimsCard(flags: List<ClaimFlag>) {
    var open by remember { mutableStateOf(true) }
    CinematicCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, null, tint = if (flags.any { it.severity == ClaimSeverity.BLOCKER }) Rf.Danger else Rf.Warning)
            Spacer(Modifier.width(8.dp))
            Text("${flags.size} claim${if (flags.size == 1) "" else "s"} to review", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Rf.TextSecondary)
        }
        if (open) flags.forEach { f ->
            Column(Modifier.padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(if (f.severity == ClaimSeverity.BLOCKER) "must fix" else f.severity.name.lowercase(), if (f.severity == ClaimSeverity.BLOCKER) Rf.Danger else Rf.Warning)
                    Spacer(Modifier.width(8.dp))
                    ScriptText("“${f.claim}”", style = MaterialTheme.typography.titleSmall)
                }
                Text(f.issue + "  (" + f.source + ")", style = MaterialTheme.typography.bodySmall)
                ScriptText("→ ${f.suggestion}", style = MaterialTheme.typography.bodySmall.copy(color = Rf.Success))
            }
        }
        if (flags.any { it.severity == ClaimSeverity.BLOCKER }) Text("Edit the flagged wording to approve the plan.", style = MaterialTheme.typography.bodySmall.copy(color = Rf.Danger), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun FullScript(b: ProjectBundle) {
    CinematicCard {
        Eyebrow("Full script")
        b.sections.forEach { s ->
            Spacer(Modifier.height(12.dp))
            Text("${s.index}. ${s.purpose.label} · ${fmtRange(b, s)}", style = MaterialTheme.typography.labelMedium, color = Rf.RoseSoft)
            ScriptText(s.spokenScript.ifBlank { "(no dialogue — ${s.shotType.label})" }, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

fun fmtRange(b: ProjectBundle, s: ReelSection): String {
    val start = b.sectionStartMs(s) / 1000; val end = start + b.project.duration.sectionMs / 1000
    return "0:%02d–0:%02d".format(start, end)
}

@Composable
private fun SectionCard(
    b: ProjectBundle, sec: ReelSection, est: TimingEstimate, busy: Boolean,
    onEdit: () -> Unit, onRegenerate: () -> Unit, onLock: () -> Unit, onShot: (ShotType) -> Unit, onUndo: () -> Unit, onShorten: () -> Unit,
    revisionCount: suspend () -> Int,
) {
    var expanded by rememberSaveable(sec.id) { mutableStateOf(false) }
    var shotMenu by remember { mutableStateOf(false) }
    var revisions by remember { mutableIntStateOf(0) }
    LaunchedEffect(sec.updatedAt) { revisions = revisionCount() }
    CinematicCard(Modifier.animateContentSize(), selected = sec.locked) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Rf.PrimaryGradient), contentAlignment = Alignment.Center) { Text("${sec.index}", style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sec.title.ifBlank { sec.purpose.label }, style = MaterialTheme.typography.titleMedium)
                Text("${sec.purpose.label.uppercase()} · ${fmtRange(b, sec)}", style = MaterialTheme.typography.labelSmall)
            }
            if (busy) Spinner(22.dp)
            IconButton(onLock) { Icon(if (sec.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, if (sec.locked) "Unlock" else "Lock", tint = if (sec.locked) Rf.RoseSoft else Rf.TextTertiary) }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x0FFFFFFF)).padding(14.dp)) {
            ScriptText(sec.spokenScript.ifBlank { if (sec.isVoiceover) "(voiceover not written yet)" else "(no dialogue)" }, style = MaterialTheme.typography.bodyLarge.copy(color = Rf.Text))
        }
        Spacer(Modifier.height(10.dp))
        TimingBadge(est)
        if (est.fit == TimingFit.TOO_LONG) GhostButton("Shorten with AI", onShorten, icon = Icons.Rounded.AutoAwesome)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Chip(sec.shotType.label, true, { shotMenu = true }, leading = Icons.Rounded.Videocam)
                DropdownMenu(shotMenu, { shotMenu = false }, containerColor = Rf.Surface2) {
                    ShotType.entries.forEach { st -> DropdownMenuItem({ Text(st.label) }, { shotMenu = false; if (st != sec.shotType) onShot(st) }) }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (sec.onScreenText.isNotBlank()) ScriptText("“${sec.onScreenText}”", style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            DirectionRow("Camera setup", sec.cameraDirection)
            DirectionRow("Framing", sec.framing)
            DirectionRow("Lighting", sec.lightingTip)
            DirectionRow("Delivery", sec.deliveryDirection)
            DirectionRow("Suggested B-roll", sec.bRollSuggestion)
            DirectionRow("On-screen text", sec.onScreenText)
            DirectionRow("Caption emphasis", sec.captionEmphasis.joinToString(", "))
            DirectionRow("Transition", sec.transitionOut.label)
            DirectionRow("SFX", sec.sfxCue)
            DirectionRow("Editing notes", sec.editNotes)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton(if (expanded) "Less" else "Direction", { expanded = !expanded }, icon = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, color = Rf.TextSecondary)
            Spacer(Modifier.weight(1f))
            if (revisions > 0) IconButton(onUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo last change", tint = Rf.TextSecondary) }
            IconButton(onEdit) { Icon(Icons.Rounded.Edit, "Edit", tint = Rf.TextSecondary) }
            GhostButton("Regenerate", onRegenerate, icon = Icons.Rounded.AutoAwesome, color = if (sec.locked) Rf.TextTertiary else Rf.RoseSoft)
        }
        if (sec.locked) Text("Locked — full regenerations keep this section unchanged.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DirectionRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
        ScriptText(value, style = MaterialTheme.typography.bodyMedium.copy(color = Rf.Text))
    }
}

@Composable
private fun SectionEditorSheet(sec: ReelSection, language: ReelLanguage, onDismiss: () -> Unit, onSave: (ReelSection) -> Unit) {
    var d by remember(sec.id) { mutableStateOf(sec) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Rf.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit section ${sec.index}", style = MaterialTheme.typography.headlineSmall)
            ReelTextField(d.spokenScript, { d = d.copy(spokenScript = it) }, label = "Script (${language.displayName})", minLines = 4)
            ReelTextField(d.onScreenText, { d = d.copy(onScreenText = it) }, label = "On-screen text")
            ReelTextField(d.cameraDirection, { d = d.copy(cameraDirection = it) }, label = "Camera setup", minLines = 2)
            ReelTextField(d.framing, { d = d.copy(framing = it) }, label = "Framing")
            ReelTextField(d.lightingTip, { d = d.copy(lightingTip = it) }, label = "Lighting")
            ReelTextField(d.deliveryDirection, { d = d.copy(deliveryDirection = it) }, label = "Delivery style", minLines = 2)
            ReelTextField(d.bRollSuggestion, { d = d.copy(bRollSuggestion = it) }, label = "Suggested B-roll")
            ReelTextField(d.editNotes, { d = d.copy(editNotes = it) }, label = "Editing notes")
            PrimaryButton("Save section", { onSave(d) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RegenerateSheet(sec: ReelSection, language: ReelLanguage, onDismiss: () -> Unit, onGo: (String, SectionTask) -> Unit) {
    var instruction by remember { mutableStateOf("") }
    val quick = buildList {
        add("Make this section more engaging" to SectionTask.REWRITE)
        if (language == ReelLanguage.IRAQI_ARABIC) add("Rewrite in a more natural Iraqi dialect" to SectionTask.NATURALIZE_IRAQI)
        add("Shorten this dialogue while retaining the main message." to SectionTask.SHORTEN)
        add("Make it less sales-oriented" to SectionTask.REWRITE)
        add("Make the delivery more sophisticated" to SectionTask.REWRITE)
        add("Suggest a different camera angle" to SectionTask.CAMERA)
        add("Use patient-treatment footage for this section" to SectionTask.VISUALS)
        if (sec.index == 1) add("Make the hook more provocative without making false claims" to SectionTask.REWRITE)
    }
    ModalBottomSheet(onDismiss, containerColor = Rf.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Regenerate section ${sec.index}", style = MaterialTheme.typography.headlineSmall)
            Text("Only this section changes. You'll compare before and after.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quick.forEach { (q, task) -> Chip(q, false, { onGo(q, task) }) }
            }
            ReelTextField(instruction, { instruction = it }, placeholder = "Or describe the change…", minLines = 2)
            PrimaryButton("Regenerate", { onGo(instruction, SectionTask.REWRITE) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome, enabled = instruction.isNotBlank())
        }
    }
}

@Composable
private fun ProposalSheet(p: app.reelify.integration.openai.SectionProposal, onAccept: () -> Unit, onRetry: () -> Unit, onCancel: () -> Unit, busy: Boolean) {
    ModalBottomSheet(onCancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Rf.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Section ${p.before.index}: compare", style = MaterialTheme.typography.headlineSmall)
            if (p.summary.isNotBlank()) Text(p.summary, style = MaterialTheme.typography.bodyMedium)
            Eyebrow("Before", color = Rf.TextSecondary)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Rf.Surface2).padding(14.dp)) { ScriptText(p.before.spokenScript, style = MaterialTheme.typography.bodyLarge.copy(color = Rf.TextSecondary)) }
            Eyebrow("After")
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Rf.Rose.copy(alpha = 0.08f)).border(1.dp, Rf.Rose.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).padding(14.dp)) {
                ScriptText(p.after.spokenScript, style = MaterialTheme.typography.bodyLarge)
            }
            if (p.after.cameraDirection != p.before.cameraDirection) { Eyebrow("New camera direction"); ScriptText(p.after.cameraDirection, style = MaterialTheme.typography.bodyMedium.copy(color = Rf.Text)) }
            if (p.after.shotType != p.before.shotType) Text("Shot: ${p.before.shotType.label} → ${p.after.shotType.label}", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Cancel", onCancel)
                SecondaryButton(if (busy) "Retrying…" else "Retry", onRetry, enabled = !busy)
                PrimaryButton("Accept", onAccept, Modifier.weight(1f), height = 52.dp, enabled = !busy)
            }
            Text("Accepting keeps the previous version in history — use Undo on the card to restore it.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
