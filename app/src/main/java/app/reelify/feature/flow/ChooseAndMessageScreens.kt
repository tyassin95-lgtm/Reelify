package app.reelify.feature.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.horizontalScroll
import app.reelify.core.content.CreativeTopic
import app.reelify.core.content.IdeaGenerationMode
import app.reelify.core.content.TopicUiCategory
import app.reelify.core.designsystem.BottomActionBar
import app.reelify.core.designsystem.Chip
import app.reelify.core.designsystem.CinematicCard
import app.reelify.core.designsystem.ErrorBanner
import app.reelify.core.designsystem.Eyebrow
import app.reelify.core.designsystem.GhostButton
import app.reelify.core.designsystem.InfoBanner
import app.reelify.core.designsystem.PillAction
import app.reelify.core.designsystem.PrimaryButton
import app.reelify.core.designsystem.ProgressOverlay
import app.reelify.core.designsystem.ReelTemplateCard
import app.reelify.core.designsystem.ReelTextField
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.RfDims
import app.reelify.core.designsystem.ScreenFrame
import app.reelify.core.designsystem.ScriptText
import app.reelify.core.designsystem.SegmentedControl
import app.reelify.core.designsystem.Shimmer
import app.reelify.core.designsystem.Spinner
import app.reelify.core.designsystem.StageHeading
import app.reelify.core.designsystem.TopicSuggestionCard
import app.reelify.core.designsystem.TreatmentSelectorCard
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.SocialPlatform
import app.reelify.core.model.Treatment
import app.reelify.core.template.ReelTemplate
import app.reelify.feature.common.LocalArabicUi
import app.reelify.feature.common.appContainer
import app.reelify.feature.common.rfViewModel
import app.reelify.feature.common.text
import app.reelify.feature.common.tr
import kotlinx.coroutines.launch

/** Lays out [items] in as many equal columns as fit [minCell] (no nested lazy lists). */
@Composable
fun <T> AdaptiveGrid(items: List<T>, minCell: Dp, modifier: Modifier = Modifier, spacing: Dp = RfDims.gap, cell: @Composable (T, Modifier) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = ((maxWidth + spacing) / (minCell + spacing)).toInt().coerceIn(1, 4)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { item -> cell(item, Modifier.weight(1f)) }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun stepLabel(step: Int) = tr("STEP $step OF 4", "الخطوة $step من 4")

// =========================================================================================== Stage 1

@Composable
fun ChooseReelScreen(
    presetTreatment: String?,
    presetTemplate: String?,
    onBack: () -> Unit,
    onContinueSession: (CreationDraftSession) -> Unit,
) {
    val vm = rfViewModel { ChooseReelViewModel(it) }
    val c = appContainer()
    val arabicUi = LocalArabicUi.current
    val scope = rememberCoroutineScope()
    val treatments by vm.treatments.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val settings by c.settingsState.collectAsStateWithLifecycle()
    var treatmentId by rememberSaveable { mutableStateOf(presetTreatment?.takeIf { it.isNotBlank() }) }
    var language by rememberSaveable { mutableStateOf<ReelLanguage?>(null) }
    var duration by rememberSaveable { mutableStateOf<ReelDuration?>(null) }
    var templateId by rememberSaveable { mutableStateOf(presetTemplate?.takeIf { it.isNotBlank() }) }
    var starting by remember { mutableStateOf(false) }
    var restored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!restored) {
            val last = vm.last()
            if (treatmentId == null) treatmentId = last.treatmentId
            language = language ?: last.language ?: settings.defaultReelLanguage
            duration = duration ?: ReelDuration.fromMs(last.durationMs)
            if (templateId == null) templateId = last.templateId
            restored = true
        }
    }
    val lang = language ?: settings.defaultReelLanguage
    val dur = duration ?: ReelDuration.S30
    val treatment = treatments.firstOrNull { it.id == treatmentId }
    val template = templates.firstOrNull { it.id == templateId } ?: templates.firstOrNull().takeIf { templateId == null }

    ScreenFrame("", onBack, bottomBar = {
        BottomActionBar {
            PrimaryButton(tr("Continue", "متابعة"), {
                val t = treatment ?: return@PrimaryButton
                val tp = template ?: return@PrimaryButton
                starting = true
                scope.launch {
                    runCatching { vm.startSession(lang, t, dur, tp) }
                        .onSuccess(onContinueSession)
                    starting = false
                }
            }, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowForward, enabled = treatment != null && template != null, loading = starting)
        }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            StageHeading(1, tr("Choose your reel", "اختار الريل"), tr("Four quick choices. Drafts are saved only after generating your script.", "أربع اختيارات سريعة. المشروع ينحفظ بس بعد كتابة السكربت."), stepLabel = stepLabel(1))

            Column(Modifier.padding(horizontal = RfDims.gutter)) {
                Eyebrow(tr("Treatment", "العلاج"))
                Spacer(Modifier.height(10.dp))
                AdaptiveGrid(treatments, minCell = 150.dp) { t, m ->
                    TreatmentSelectorCard(
                        t, title = if (arabicUi) t.arabicName.ifBlank { t.name } else t.name, subtitle = if (arabicUi) t.name else t.arabicName,
                        selected = t.id == treatmentId, needsSetupLabel = if (t.needsConfiguration) tr("Needs setup", "يحتاج إعداد") else null,
                        onClick = { treatmentId = t.id }, modifier = m,
                    )
                }
            }

            Column(Modifier.padding(horizontal = RfDims.gutter)) {
                Eyebrow(tr("Language", "اللغة"))
                Spacer(Modifier.height(10.dp))
                SegmentedControl(ReelLanguage.entries, lang, { if (it == ReelLanguage.ENGLISH) "English" else "عراقي" }, { language = it })
            }

            Column(Modifier.padding(horizontal = RfDims.gutter)) {
                Eyebrow(tr("Length", "المدة"))
                Spacer(Modifier.height(10.dp))
                SegmentedControl(ReelDuration.entries, dur, { "${it.seconds}s" }, { duration = it }, sublabel = { tr("3 × ${it.sectionMs / 1000}s", "3 × ${it.sectionMs / 1000} ثا") })
            }

            Column(Modifier.padding(horizontal = RfDims.gutter)) {
                Eyebrow(tr("Template", "القالب"))
                Spacer(Modifier.height(4.dp))
                Text(tr("Each template is a fixed, repeatable format with three planned shots.", "كل قالب شكل ثابت ومتكرر بثلاث لقطات مخططة."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                AdaptiveGrid(templates.filter { dur in it.supportedDurations }, minCell = 160.dp) { tp, m ->
                    ReelTemplateCard(
                        tp, tp.name.text(), tp.description.text(), tp.shots.sortedBy { it.index }.map { it.role.label.text() },
                        selected = tp.id == template?.id, onClick = { templateId = tp.id }, modifier = m,
                        badge = if (!tp.builtIn) tr("Custom", "مخصص") else null,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// =========================================================================================== Stage 2

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageScreen(
    pidOrDraftKey: String,
    fallbackTreatmentId: String = "",
    fallbackTemplateId: String = "",
    fallbackLangCode: String = "",
    fallbackDurationMs: Long = 30_000L,
    onBack: () -> Unit,
    onScriptReady: (String) -> Unit,
    onEditTreatment: (String) -> Unit,
) {
    val arabicUi = LocalArabicUi.current
    val vm = rfViewModel(pidOrDraftKey) {
        MessageViewModel(
            c = it,
            pidOrDraftKey = pidOrDraftKey,
            arabicUi = arabicUi,
            fallbackTreatmentId = fallbackTreatmentId,
            fallbackTemplateId = fallbackTemplateId,
            fallbackLangCode = fallbackLangCode,
            fallbackDurationMs = fallbackDurationMs,
        )
    }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var own by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val p = ui.project
    val treatment = ui.treatment

    Box(Modifier.fillMaxSize()) {
        ScreenFrame("", onBack) {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                val sub = listOfNotNull(
                    treatment?.let { if (arabicUi) it.arabicName.ifBlank { it.name } else it.name },
                    p?.template?.name?.text(), p?.duration?.label,
                ).joinToString(" · ")
                StageHeading(2, tr("Choose your message", "اختار الفكرة"), sub, stepLabel = stepLabel(2))

                Column(Modifier.padding(horizontal = RfDims.gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Eyebrow(tr("Platform", "المنصة"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SocialPlatform.entries.forEach { plat ->
                            Chip(
                                plat.label.text(),
                                ui.concept.platform == plat,
                                { vm.setPlatform(plat) },
                            )
                        }
                    }
                    SurpriseButton(ui.ideasLoading, enabled = ui.generating == null) { vm.surpriseMe() }
                    if (ui.ideasLoading) repeat(3) { Shimmer(Modifier.fillMaxWidth().height(84.dp)) }
                    ui.ideas.forEach { idea ->
                        AiIdeaCard(
                            idea = idea,
                            loading = ui.generatingKey == idea.id,
                            enabled = ui.generating == null && !ui.ideasLoading,
                            onUse = { vm.useIdea(idea, onScriptReady) },
                            onMoreLikeThis = { vm.surpriseMe(idea, IdeaGenerationMode.MORE_LIKE_THIS) },
                            onDifferentAngle = { vm.surpriseMe(idea, IdeaGenerationMode.DIFFERENT_ANGLE) },
                            onSaveForLater = { vm.saveIdeaForLater(idea) },
                        )
                    }
                    if (ui.ideas.isNotEmpty() && !ui.ideasLoading) {
                        GhostButton(
                            tr("Regenerate 5 fresh ideas", "اقترح 5 أفكار جديدة ثانية"),
                            { vm.surpriseMe() },
                            icon = Icons.Rounded.Refresh,
                        )
                    }
                    ui.notice?.let { InfoBanner(it, color = Rf.Success) }
                }

                if (ui.savedIdeas.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = RfDims.gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Eyebrow(tr("Saved ideas (${ui.savedIdeas.size})", "أفكار محفوظة (${ui.savedIdeas.size})"))
                        ui.savedIdeas.forEach { saved ->
                            CinematicCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Bookmark, null, tint = Rf.Gold, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        ScriptText(saved.title, style = MaterialTheme.typography.titleSmall)
                                        if (saved.educationalObjective.isNotBlank()) {
                                            Text(saved.educationalObjective, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                        }
                                    }
                                    IconButton({ vm.removeSavedIdea(saved) }) {
                                        Icon(Icons.Rounded.DeleteOutline, tr("Remove saved idea", "حذف الفكرة"), tint = Rf.TextTertiary)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                PillAction(tr("Use saved idea", "استخدم الفكرة"), Icons.AutoMirrored.Rounded.ArrowForward, { vm.useIdea(saved, onScriptReady) }, primary = true, enabled = ui.generating == null)
                            }
                        }
                    }
                }

                Column(Modifier.padding(horizontal = RfDims.gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Eyebrow(tr("Suggested topics", "مواضيع مقترحة"))
                    if (treatment != null && treatment.id == "cleopatra" && !treatment.identityConfirmed) {
                        InfoBanner(
                            tr(
                                "If 'Cleopatra' in your clinic refers to DiamondGlow® (3-in-1 exfoliation, vacuum extraction & Pro-Infusion serum + lymphatic drainage), confirm it with one tap to unlock all DiamondGlow clinical topics.",
                                "إذا جلسة كليوباترا في عيادتك هي جهاز DiamondGlow® الثلاثي (تقشير ماسي + سحب + سيروم Pro-Infusion وتصريف لمفاوي)، ثبّتها بنقرة وحدة حتى تنفتح مواضيع الجهاز.",
                            ),
                            color = Rf.Info,
                        )
                        PrimaryButton(
                            tr("Confirm Cleopatra = DiamondGlow® (3-in-1)", "تثبيت كليوباترا = DiamondGlow®"),
                            { vm.confirmCleopatraIdentity() },
                            Modifier.fillMaxWidth(),
                        )
                    } else if (treatment != null && !treatment.isGeneral && !ui.profileReady) {
                        InfoBanner(tr("${treatment.name} still needs its profile completed, so only safe general topics are shown.", "ملف ${treatment.arabicName.ifBlank { treatment.name }} يحتاج إكمال، فنعرض مواضيع عامة وآمنة فقط."), color = Rf.Warning)
                        Text(tr("Complete profile", "أكمل الملف"), style = MaterialTheme.typography.labelLarge, color = Rf.RoseSoft, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onEditTreatment(treatment.id) }.padding(8.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TopicUiCategory.entries.forEach { cat ->
                            Chip(
                                cat.label.text(),
                                ui.selectedCategory == cat,
                                { vm.selectCategory(cat) },
                            )
                        }
                    }
                    ui.suggestions.take(ui.visibleCount).forEach { s ->
                        TopicSuggestionCard(s.title, s.idea.category.label.text(), onClick = { vm.useTopic(s, onScriptReady) },
                            hook = s.hookAngle.takeIf { it.isNotBlank() },
                            usedLabel = if (s.usedRecently) tr("used recently", "مستخدم مؤخراً") else null,
                            loading = ui.generatingKey == s.idea.key, enabled = ui.generating == null)
                    }
                    if (ui.suggestions.size > ui.visibleCount) {
                        GhostButton(
                            tr("Show more topics (${ui.suggestions.size - ui.visibleCount} remaining)", "عرض مواضيع أكثر (${ui.suggestions.size - ui.visibleCount})"),
                            { vm.showMoreTopics() },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }

                Column(Modifier.padding(horizontal = RfDims.gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Eyebrow(tr("Your own idea", "فكرتك"))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReelTextField(own, { own = it }, Modifier.weight(1f), placeholder = tr("Or describe your own idea…", "أو اكتب فكرتك…"), singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(52.dp).clip(CircleShape).background(if (own.isNotBlank()) Rf.PrimaryGradient else androidx.compose.ui.graphics.SolidColor(Rf.Surface3))
                            .clickable(enabled = own.isNotBlank() && ui.generating == null) { vm.useOwnIdea(own, onScriptReady) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.Send, tr("Write script", "اكتب السكربت"), tint = Color.White)
                        }
                    }
                    Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { advanced = !advanced }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Advanced options", "خيارات متقدمة"), style = MaterialTheme.typography.labelLarge, color = Rf.TextSecondary)
                        Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Rf.TextSecondary)
                    }
                    if (advanced) {
                        val cpt = ui.concept
                        ReelTextField(cpt.audience, { v -> vm.editConcept { it.copy(audience = v) } }, label = tr("Audience", "الجمهور"), singleLine = true)
                        ReelTextField(cpt.tone, { v -> vm.editConcept { it.copy(tone = v) } }, label = tr("Tone", "النبرة"), singleLine = true)
                        ReelTextField(cpt.cta, { v -> vm.editConcept { it.copy(cta = v) } }, label = tr("Call to action", "الدعوة"), singleLine = true)
                        ReelTextField(cpt.mustInclude.joinToString("; "), { v -> vm.editConcept { it.copy(mustInclude = v.split(';').map(String::trim).filter(String::isNotEmpty)) } }, label = tr("Must include (separate with ;)", "لازم يتضمن (افصل بـ ;)"))
                        ReelTextField(cpt.excludedClaims.joinToString("; "), { v -> vm.editConcept { it.copy(excludedClaims = v.split(';').map(String::trim).filter(String::isNotEmpty)) } }, label = tr("Never say (separate with ;)", "لا تقول (افصل بـ ;)"))
                    }
                }
                ui.error?.let { ErrorBanner(it, Modifier.padding(horizontal = RfDims.gutter), onDismiss = vm::dismissError) }
                Spacer(Modifier.height(24.dp))
            }
        }
        ui.generating?.let { topic ->
            ProgressOverlay(tr("Writing your script…", "جاري كتابة السكربت…"), detail = topic, cancelLabel = tr("Cancel", "إلغاء"), onCancel = vm::cancel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiIdeaCard(
    idea: CreativeTopic,
    loading: Boolean,
    enabled: Boolean,
    onUse: () -> Unit,
    onMoreLikeThis: () -> Unit,
    onDifferentAngle: () -> Unit,
    onSaveForLater: () -> Unit,
) {
    CinematicCard(onClick = { if (enabled && !loading) onUse() }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(idea.uiCategory.label.text().uppercase(), style = MaterialTheme.typography.labelSmall, color = Rf.RoseSoft)
                if (idea.primaryFactIds.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Clinical fact grounded", "معلومة موثقة"), style = MaterialTheme.typography.labelSmall, color = Rf.Success)
                }
            }
            ScriptText(idea.title, style = MaterialTheme.typography.titleMedium)
            if (idea.educationalObjective.isNotBlank()) {
                Text(
                    tr("Viewers learn: ${idea.educationalObjective}", "الفائدة للمشاهد: ${idea.educationalObjective}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Rf.TextSecondary,
                    maxLines = 2,
                )
            }
            if (idea.hookAngle.isNotBlank()) {
                ScriptText("“${idea.hookAngle}”", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PillAction(
                    text = if (loading) tr("Writing…", "جاري الكتابة…") else tr("Use idea", "استخدم الفكرة"),
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = onUse,
                    enabled = enabled && !loading,
                    primary = true,
                )
                PillAction(tr("More like this", "مشابهة لها"), Icons.Rounded.AutoAwesome, onMoreLikeThis, enabled = enabled && !loading)
                PillAction(tr("Different angle", "زاوية ثانية"), Icons.Rounded.Refresh, onDifferentAngle, enabled = enabled && !loading)
                PillAction(tr("Save", "حفظ"), Icons.Rounded.BookmarkAdd, onSaveForLater, enabled = enabled && !loading)
            }
        }
    }
}

@Composable
private fun SurpriseButton(loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).clip(RoundedCornerShape(22.dp)).background(Rf.PrimaryGradient)
            .border(1.dp, Rf.BorderStrong, RoundedCornerShape(22.dp)).clickable(enabled = enabled && !loading, onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            if (loading) Spinner(22.dp, Color.White) else Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("Give me fresh ideas", "أعطني أفكار جديدة"), style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(tr("AI generates 5 specific clinical concepts grounded in verified facts", "الذكاء الاصطناعي يقترح 5 أفكار طبية دقيقة من المعلومات المعتمدة"), style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f)))
        }
    }
}
