package app.reelify.feature.flow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reelify.core.designsystem.BottomActionBar
import app.reelify.core.designsystem.Chip
import app.reelify.core.designsystem.ErrorBanner
import app.reelify.core.designsystem.Eyebrow
import app.reelify.core.designsystem.GhostButton
import app.reelify.core.designsystem.HookSelector
import app.reelify.core.designsystem.InfoBanner
import app.reelify.core.designsystem.PillAction
import app.reelify.core.designsystem.PrimaryButton
import app.reelify.core.designsystem.ReelTextField
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.RfDims
import app.reelify.core.designsystem.ScreenFrame
import app.reelify.core.designsystem.ScriptReviewCard
import app.reelify.core.designsystem.ScriptText
import app.reelify.core.designsystem.ShotProgressCard
import app.reelify.core.designsystem.StageHeading
import app.reelify.core.designsystem.ToggleRow
import app.reelify.core.model.ClaimSeverity
import app.reelify.core.model.ClipRole
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelSection
import app.reelify.core.template.MediaRequirement
import app.reelify.core.timing.TimingFit
import app.reelify.feature.common.mediaModel
import app.reelify.feature.common.rfViewModel
import app.reelify.feature.common.text
import app.reelify.feature.common.tr
import app.reelify.integration.openai.ScriptAction
import coil3.compose.AsyncImage

private fun hookStyleLabel(style: String, arabic: Boolean): String = when (style) {
    "curiosity" -> if (arabic) "فضول" else "Curiosity"
    "common_mistake" -> if (arabic) "غلطة شائعة" else "Common mistake"
    "expert_insight" -> if (arabic) "رأي خبير" else "Expert insight"
    "relatable_concern" -> if (arabic) "مشكلة قريبة" else "Relatable concern"
    "myth" -> if (arabic) "فكرة غلط" else "Myth"
    "question" -> if (arabic) "سؤال" else "Question"
    "surprising_fact" -> if (arabic) "معلومة مفاجئة" else "Surprising fact"
    else -> style
}

@Composable
fun ScriptReviewScreen(pid: String, onBack: () -> Unit, onApproved: () -> Unit, onChangeTopic: () -> Unit) {
    val vm = rfViewModel(pid) { ScriptReviewViewModel(it, pid) }
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val timing by vm.timing.collectAsStateWithLifecycle()
    val arabicUi = app.reelify.feature.common.LocalArabicUi.current
    var editing by remember { mutableStateOf<ReelSection?>(null) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    val b = bundle ?: return
    val tpl = b.project.template ?: return
    val meta = b.project.script
    val sectionMs = b.project.duration.sectionMs
    val unlocked = b.sections.filter { !it.locked }.map { it.index }.toSet()
    val flags = vm.localFlags(b) + meta?.medicalReview.orEmpty()

    ScreenFrame("", onBack, bottomBar = {
        BottomActionBar {
            PrimaryButton(tr("Use This Script", "اعتمد السكربت"), { vm.approve(onApproved) }, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowForward,
                enabled = ui.busy == null && b.sections.all { it.spokenScript.isNotBlank() })
        }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StageHeading(2, tr("Your script", "السكربت"), meta?.topicTitle?.takeIf { it.isNotBlank() } ?: tpl.name.text(), stepLabel = stepLabel(2))

            if (meta != null && meta.hookOptions.isNotEmpty()) Column(Modifier.padding(horizontal = RfDims.gutter)) {
                Eyebrow(tr("Opening hook", "الافتتاحية"))
                Spacer(Modifier.height(8.dp))
                val selected = meta.hookOptions.indexOfFirst { it.text == meta.activeHook }
                HookSelector(meta.hookOptions.map { hookStyleLabel(it.style, arabicUi) to it.text }, selected, { vm.selectHook(it) })
                if (selected < 0 && meta.activeHook.isNotBlank()) Text(tr("Current opening: ", "الافتتاحية الحالية: ") + meta.activeHook, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }

            // Quick AI actions (whole script, unlocked shots only)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = RfDims.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val enabled = ui.busy == null
                Chip(tr("Try different hook", "جرّب افتتاحية ثانية"), false, { if (enabled) vm.tryDifferentHooks() }, leading = Icons.Rounded.Refresh)
                Chip(tr("More engaging", "أكثر جذب"), false, { if (enabled) vm.action(ScriptAction.MORE_ENGAGING, unlocked) }, leading = Icons.Rounded.AutoAwesome)
                Chip(tr("Sound like me", "يشبه أسلوبي"), false, { if (enabled) vm.action(ScriptAction.SOUND_LIKE_ME, unlocked) })
                if (b.project.language == ReelLanguage.IRAQI_ARABIC) Chip(tr("More natural Iraqi", "عراقي أكثر"), false, { if (enabled) vm.action(ScriptAction.MORE_NATURAL_IRAQI, unlocked) })
                Chip(tr("Shorten", "اختصر"), false, { if (enabled) vm.action(ScriptAction.SHORTEN, unlocked) })
                Chip(tr("More professional", "أكثر احترافية"), false, { if (enabled) vm.action(ScriptAction.MORE_PROFESSIONAL, unlocked) })
            }
            ui.busy?.let { InfoBanner(it, Modifier.padding(horizontal = RfDims.gutter), color = Rf.Info, icon = Icons.Rounded.AutoAwesome) }
            ui.summary?.let { InfoBanner(it, Modifier.padding(horizontal = RfDims.gutter), color = Rf.Success) }
            ui.error?.let { ErrorBanner(it, Modifier.padding(horizontal = RfDims.gutter), onDismiss = vm::dismissError) }

            b.sections.sortedBy { it.index }.forEach { s ->
                val shot = tpl.shot(s.index)
                val shotType = if (shot.requiredMedia == MediaRequirement.VOICEOVER_BROLL) app.reelify.core.model.ShotType.VOICEOVER_BROLL else app.reelify.core.model.ShotType.TALKING_HEAD
                val sb = timing.wordBudgetForShot(b.project.language, sectionMs, shot.role.purpose, shotType)
                val wc = timing.countWords(s.spokenScript)
                val est = timing.estimate(s.spokenScript, b.project.language, sectionMs, if (shot.requiredMedia == MediaRequirement.VOICEOVER_BROLL) 0.6f else 0f)
                var revs by remember(s.updatedAt) { mutableIntStateOf(0) }
                LaunchedEffect(s.updatedAt) { revs = vm.revisionCount(s.id) }
                var details by rememberSaveable(s.id) { mutableStateOf(false) }
                val baseTiming = when (est.fit) {
                    TimingFit.TOO_LONG -> tr("≈ %.0fs — likely too long".format(est.totalMs / 1000f), "≈ %.0f ثا — غالباً أطول من اللازم".format(est.totalMs / 1000f))
                    TimingFit.TOO_SHORT -> tr("≈ %.0fs — short for ${sectionMs / 1000}s shot".format(est.totalMs / 1000f), "≈ %.0f ثا — قصير على ${sectionMs / 1000} ثا".format(est.totalMs / 1000f))
                    TimingFit.FITS -> tr("≈ %.0fs spoken".format(est.totalMs / 1000f), "≈ %.0f ثا كلام".format(est.totalMs / 1000f))
                    TimingFit.EMPTY -> null
                }
                ScriptReviewCard(
                    s.index, shot.role.label.text(), sectionMs / 1000, s.spokenScript, shot.instruction.text(), s.onScreenText, s.locked,
                    busy = s.index in ui.busySections,
                    timingNote = baseTiming?.let { "$it · ${wc}/${sb.promptLabel()}" },
                    timingWarn = est.fit == TimingFit.TOO_LONG || est.fit == TimingFit.TOO_SHORT,
                    onToggleLock = { vm.toggleLock(s) },
                    modifier = Modifier.padding(horizontal = RfDims.gutter),
                ) {
                    PillAction(tr("Edit", "تعديل"), Icons.Rounded.Edit, { editing = s }, enabled = ui.busy == null)
                    PillAction(tr("Try another", "كلام ثاني"), Icons.Rounded.Refresh, { vm.action(ScriptAction.REGENERATE_SHOT, setOf(s.index)) }, enabled = ui.busy == null && !s.locked)
                    if (revs > 0) PillAction(tr("Undo", "تراجع"), Icons.AutoMirrored.Rounded.Undo, { vm.undo(s) }, enabled = ui.busy == null)
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.Icon(if (details) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, tr("Details", "تفاصيل"), tint = Rf.TextTertiary,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { details = !details }.padding(8.dp))
                }
                if (details) Column(Modifier.padding(horizontal = RfDims.gutter + 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Framing: ", "اللقطة: ") + shot.framing.label.text(), style = MaterialTheme.typography.bodySmall)
                    Text(tr("Light: ", "الإضاءة: ") + shot.lighting.text(), style = MaterialTheme.typography.bodySmall)
                    Text(tr("Delivery: ", "الأداء: ") + shot.delivery.text(), style = MaterialTheme.typography.bodySmall)
                    shot.brollHint?.let { Text(tr("Footage: ", "اللقطات: ") + it.text(), style = MaterialTheme.typography.bodySmall) }
                    Text(tr("These come from the template and stay the same every time.", "هاي من القالب وتبقى ثابتة كل مرة."), style = MaterialTheme.typography.labelMedium, color = Rf.TextTertiary)
                }
            }

            if (flags.isNotEmpty()) Column(Modifier.padding(horizontal = RfDims.gutter), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow(tr("Medical wording to check", "عبارات طبية للمراجعة"), color = Rf.Warning)
                flags.distinctBy { it.claim.lowercase() }.take(6).forEach { f ->
                    InfoBanner("“${f.claim}” — ${f.issue} ${f.suggestion}".trim(), color = if (f.severity == ClaimSeverity.BLOCKER) Rf.Danger else Rf.Warning, icon = Icons.Rounded.WarningAmber)
                }
            }
            val notes = meta?.qualityNotes.orEmpty() + ui.notes
            if (notes.isNotEmpty()) Column(Modifier.padding(horizontal = RfDims.gutter)) {
                Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { showNotes = !showNotes }.padding(vertical = 6.dp)) {
                    Text(tr("Writing notes (${notes.distinct().size})", "ملاحظات الكتابة (${notes.distinct().size})"), style = MaterialTheme.typography.labelLarge, color = Rf.TextSecondary)
                }
                if (showNotes) notes.distinct().forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                if (b.project.language == ReelLanguage.IRAQI_ARABIC) Text(
                    tr("Dialect checks are automatic heuristics — they can't guarantee authentic Iraqi. Trust your ear and fix anything that doesn't sound like you.",
                        "فحص اللهجة تلقائي وتقريبي — ما يضمن إن الكلام عراقي أصلي. اعتمد على أذنك وعدّل أي شي ما يشبهك."),
                    style = MaterialTheme.typography.labelMedium, color = Rf.TextTertiary, modifier = Modifier.padding(top = 4.dp))
            }
            GhostButton(tr("Choose a different topic", "اختار موضوع ثاني"), onChangeTopic, Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(20.dp))
        }
    }

    editing?.let { s ->
        var text by remember(s.id) { mutableStateOf(s.spokenScript) }
        var headline by remember(s.id) { mutableStateOf(s.onScreenText) }
        ModalBottomSheet({ editing = null }, containerColor = Rf.Surface) {
            Column(Modifier.padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Edit shot ${s.index}", "تعديل اللقطة ${s.index}"), style = MaterialTheme.typography.headlineSmall)
                ReelTextField(text, { text = it }, label = tr("Spoken words", "الكلام"), minLines = 4)
                ReelTextField(headline, { headline = it }, label = tr("On-screen headline", "العنوان على الشاشة"), singleLine = true)
                PrimaryButton(tr("Save", "حفظ"), { vm.saveManual(s, text, headline); editing = null }, Modifier.fillMaxWidth())
                GhostButton(tr("Save line to my voice guide", "احفظ هذا الأسلوب كمرجع لكلامي"), {
                    vm.saveManual(s, text, headline)
                    vm.saveToMyVoice(text)
                    editing = null
                }, Modifier.fillMaxWidth())
            }
        }
    }
}

// =========================================================================================== Stage 3

@Composable
fun FilmScreen(
    pid: String,
    onBack: () -> Unit,
    onRecord: (sectionId: String, role: String, muted: Boolean, replace: Boolean) -> Unit,
    onTrim: (String) -> Unit,
    onPrepare: () -> Unit,
    onScript: () -> Unit,
) {
    val vm = rfViewModel(pid) { FilmViewModel(it, pid) }
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    var pickTarget by remember { mutableStateOf<Triple<ReelSection, ClipRole, Pair<Boolean, Boolean>>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pickTarget?.let { (s, role, flags) -> vm.picked(s, role, uri, flags.first, flags.second) }
        pickTarget = null
    }
    fun pick(s: ReelSection, role: ClipRole, voiceShot: Boolean, replace: Boolean, video: Boolean = true) {
        pickTarget = Triple(s, role, voiceShot to replace)
        picker.launch(PickVisualMediaRequest(if (video) ActivityResultContracts.PickVisualMedia.VideoOnly else ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    val b = bundle ?: return
    val tpl = b.project.template ?: return
    val shots = vm.shots(b)
    val current = shots.firstOrNull { !it.done }?.section?.index
    val allDone = shots.all { it.done }

    ScreenFrame("", onBack, bottomBar = {
        BottomActionBar {
            PrimaryButton(if (allDone) tr("Prepare my reel", "جهّز الريل") else tr("${shots.count { it.done }} of 3 shots ready", "${shots.count { it.done }} من 3 لقطات جاهزة"),
                onPrepare, Modifier.weight(1f), icon = Icons.Rounded.AutoAwesome, enabled = allDone)
        }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StageHeading(3, tr("Film your reel", "صوّر الريل"), tr("Three familiar shots. Record or import each one.", "ثلاث لقطات ثابتة. سجّل أو استورد كل وحدة."), stepLabel = stepLabel(3))
            ui.error?.let { ErrorBanner(it, Modifier.padding(horizontal = RfDims.gutter), onDismiss = vm::dismiss) }
            shots.forEach { st ->
                val s = st.section
                val shot = tpl.shot(s.index)
                val voiceShot = shot.requiredMedia == MediaRequirement.VOICEOVER_BROLL
                val status = when {
                    ui.importing == s.index -> tr("Importing…", "جاري الاستيراد…")
                    st.done -> tr("Ready", "جاهزة")
                    voiceShot && st.footageDone -> tr("Footage added · voice needed", "اللقطات جاهزة · ناقص الصوت")
                    voiceShot && st.voiceDone -> tr("Voice added · footage needed", "الصوت جاهز · ناقص اللقطات")
                    else -> tr("Not filmed yet", "ما تصورت بعد")
                }
                ShotProgressCard(
                    s.index, shot.role.label.text(), b.project.duration.sectionMs / 1000, shot.instruction.text(), s.spokenScript,
                    done = st.done, current = s.index == current, statusText = status, modifier = Modifier.padding(horizontal = RfDims.gutter),
                    thumbnail = st.footage.firstOrNull()?.let { clip -> { AsyncImage(mediaModel(clip.thumbnailPath ?: clip.uri), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } },
                ) {
                    val primaryClip = st.footage.firstOrNull()
                    if (primaryClip != null && primaryClip.kind == app.reelify.core.model.MediaKind.VIDEO) {
                        val effectiveEnd = if (primaryClip.trimEndMs > primaryClip.trimStartMs) primaryClip.trimEndMs else primaryClip.sourceDurationMs
                        val clipDurMs = (effectiveEnd - primaryClip.trimStartMs).coerceAtLeast(0L)
                        val targetMs = b.project.duration.sectionMs
                        if (clipDurMs in 1L..(targetMs - 1500L) && st.broll.isEmpty()) {
                            val gapSec = ((targetMs - clipDurMs) / 1000L).coerceAtLeast(1L)
                            InfoBanner(
                                tr(
                                    "Clip is ${gapSec}s shorter than ${targetMs / 1000}s shot — Retake, Add cutaway B-roll, or Auto-Edit extends the closing beat cleanly.",
                                    "المقطع أقصر بـ $gapSec ثا من مدة اللقطة (${targetMs / 1000} ثا) — تكدر تعيد التصوير، تضيف لقطة B-roll، أو المعالجة تكمل الباقي بهدوء.",
                                ),
                                color = Rf.Warning,
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!voiceShot) {
                            PillAction(if (st.footageDone) tr("Retake", "إعادة") else tr("Record", "سجّل"), Icons.Rounded.FiberManualRecord, { onRecord(s.id, "PRIMARY", false, st.footageDone) }, primary = !st.footageDone)
                            PillAction(tr("Import", "استيراد"), Icons.Rounded.PhotoLibrary, { pick(s, ClipRole.PRIMARY, false, st.footageDone) })
                        } else {
                            PillAction(if (st.footageDone) tr("Refilm footage", "إعادة اللقطات") else tr("Film footage", "صوّر اللقطات"), Icons.Rounded.FiberManualRecord, { onRecord(s.id, "PRIMARY", true, st.footageDone) }, primary = !st.footageDone)
                            PillAction(tr("Import footage", "استيراد لقطات"), Icons.Rounded.PhotoLibrary, { pick(s, ClipRole.PRIMARY, true, st.footageDone) })
                            PillAction(if (st.voiceDone) tr("Re-record voice", "إعادة الصوت") else tr("Record voice", "سجّل الصوت"), Icons.Rounded.Mic, { onRecord(s.id, "VOICEOVER", false, st.voiceDone) }, primary = st.footageDone && !st.voiceDone)
                        }
                        st.footage.firstOrNull()?.takeIf { it.kind == app.reelify.core.model.MediaKind.VIDEO }?.let { clip -> PillAction(tr("Trim", "قص"), Icons.Rounded.ContentCut, { onTrim(clip.id) }) }
                        if (shot.optionalBroll && st.footageDone) PillAction(if (st.broll.isEmpty()) tr("Add cutaway", "ضيف لقطة") else tr("Cutaway ✓", "لقطة ✓"), Icons.Rounded.Add, { pick(s, ClipRole.BROLL, false, false, video = false) })
                    }
                }
            }
            GhostButton(tr("Back to script", "رجوع للسكربت"), onScript, Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(16.dp))
        }
    }

    ui.pending?.let { p ->
        var clinical by remember(p) { mutableStateOf(p.clinicalHint) }
        var identifiable by remember(p) { mutableStateOf(false) }
        var consent by remember(p) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = vm::cancelPending, containerColor = Rf.Surface,
            title = { Text(tr("About this footage", "عن هاي اللقطات")) },
            text = {
                Column {
                    ToggleRow(tr("Shows a patient or treatment area", "تبين مراجع أو منطقة علاج"), clinical, { clinical = it }, subtitle = tr("Beauty filters stay off for honest representation.", "فلاتر التجميل تبقى مطفية حتى تكون الصورة صادقة."))
                    if (clinical) ToggleRow(tr("Patient is identifiable", "المراجع يبين وجهه"), identifiable, { identifiable = it })
                    if (clinical && identifiable) ToggleRow(tr("I have the patient's consent", "عندي موافقة المراجع"), consent, { consent = it })
                    Text(tr("Footage stays on this phone. It is never sent to the AI.", "اللقطات تبقى بالتلفون، وما تنرسل للذكاء الاصطناعي."), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton({ vm.confirm(clinical, clinical && identifiable, consent) }) { Text(tr("Add", "إضافة"), color = Rf.RoseSoft) } },
            dismissButton = { TextButton(vm::cancelPending) { Text(tr("Cancel", "إلغاء"), color = Rf.TextSecondary) } },
        )
    }
}
