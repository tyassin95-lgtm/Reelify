package app.reelify.feature.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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
import app.reelify.core.designsystem.StatusPill
import app.reelify.core.designsystem.StepIndicator
import app.reelify.core.designsystem.Tajawal
import app.reelify.core.designsystem.ThinkingOrb
import app.reelify.core.model.ClaimSeverity
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelStyle
import app.reelify.feature.common.appContainer
import app.reelify.feature.common.rfViewModel
import app.reelify.feature.common.strings
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(presetTreatment: String, presetStyle: String, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val c = appContainer()
    val s = strings()
    val vm = rfViewModel { SetupViewModel(it) }
    val scope = rememberCoroutineScope()
    val treatments by vm.treatments.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by c.settingsState.collectAsStateWithLifecycle()
    var language by rememberSaveable { mutableStateOf<ReelLanguage?>(null) }
    var treatment by rememberSaveable { mutableStateOf(presetTreatment.ifBlank { null }) }
    var duration by rememberSaveable { mutableStateOf(ReelDuration.S30) }
    var style by rememberSaveable { mutableStateOf(ReelStyle.fromWire(presetStyle.ifBlank { null })) }
    var creating by remember { mutableStateOf(false) }
    val lang = language ?: settings.defaultReelLanguage

    ScreenFrame("New reel", onBack, actions = { StepIndicator(0, 4, Modifier.padding(end = 16.dp)) }, bottomBar = {
        BottomActionBar {
            PrimaryButton(s.next, {
                creating = true
                scope.launch { val p = vm.create(lang, treatment!!, duration, style); onCreated(p.id) }
            }, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowForward, enabled = treatment != null, loading = creating)
        }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Column {
                Eyebrow("Reel language")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReelLanguage.entries.forEach { l ->
                        CinematicCard(Modifier.weight(1f), onClick = { language = l }, selected = l == lang) {
                            Text(l.nativeName, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = if (l.isRtl) Tajawal else MaterialTheme.typography.headlineSmall.fontFamily))
                            Text(l.displayName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Column {
                Eyebrow("Treatment or topic")
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    treatments.forEach { t ->
                        val sel = treatment == t.id
                        Column(
                            Modifier.width(158.dp).clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(Color(t.accentColor).copy(alpha = if (sel) 0.6f else 0.28f), Rf.Surface)))
                                .border(if (sel) 1.5.dp else 1.dp, if (sel) Rf.Rose else Rf.Border, RoundedCornerShape(20.dp))
                                .clickable { treatment = t.id }.padding(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                if (sel) Icon(Icons.Rounded.CheckCircle, null, tint = Rf.RoseSoft, modifier = Modifier.size(18.dp))
                            }
                            if (t.arabicName.isNotBlank()) Text(t.arabicName, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Tajawal))
                            if (t.needsConfiguration) Text("Needs setup", style = MaterialTheme.typography.labelMedium, color = Rf.Warning)
                        }
                    }
                }
            }
            Column {
                Eyebrow("Length")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReelDuration.entries.forEach { d ->
                        CinematicCard(Modifier.weight(1f), onClick = { duration = d }, selected = d == duration) {
                            Text("${d.seconds}s", style = MaterialTheme.typography.headlineMedium)
                            Text("3 × ${d.sectionMs / 1000}s", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Column {
                Eyebrow("Style")
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReelStyle.entries.forEach { st -> Chip(st.label, st == style, { style = st }) }
                }
                Text(style.blurb + " — you can still adjust the style later.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun IdeaScreen(pid: String, onBack: () -> Unit, onAnalyzed: () -> Unit, onSkipToScript: () -> Unit) {
    val vm = rfViewModel(pid) { IdeaViewModel(it, pid) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = strings()
    var showNotes by rememberSaveable { mutableStateOf(false) }
    val concept = ui.concept
    val arabic = ui.project?.language == ReelLanguage.IRAQI_ARABIC

    if (ui.loading) { AiWorking("Reading your idea like a creative director…", onCancel = vm::cancel); return }

    ScreenFrame("Your idea", onBack, subtitle = ui.project?.let { "${it.duration.label} · ${it.language.displayName}" }, actions = { StepIndicator(1, 4, Modifier.padding(end = 16.dp)) }, bottomBar = {
        BottomActionBar {
            SecondaryButton("Skip", onSkipToScript)
            PrimaryButton(s.analyze, { vm.analyze(onAnalyzed) }, Modifier.weight(1f), icon = Icons.Rounded.AutoAwesome, enabled = concept.summary.isNotBlank())
        }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Describe what you want this reel to communicate.", style = MaterialTheme.typography.headlineSmall)
            ReelTextField(concept.summary, { v -> vm.edit { it.copy(summary = v) } }, minLines = 6,
                placeholder = if (arabic) "مثال: أريد فيديو يشرح متى ممكن نستخدم Aerolase لحب الشباب وليش الاستشارة مهمة" else "e.g. Explain how Aerolase can be considered for certain acne and pigmentation concerns, and why a consultation matters.")
            ui.error?.let { ErrorBanner(it, onRetry = { vm.analyze(onAnalyzed) }, onDismiss = vm::dismissError) }
            GhostButton(if (showNotes) "Hide optional notes" else "Add optional notes", { showNotes = !showNotes }, icon = Icons.Rounded.Edit)
            AnimatedVisibility(showNotes, enter = fadeIn(), exit = fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReelTextField(concept.desiredMessage, { v -> vm.edit { it.copy(desiredMessage = v) } }, label = "Desired message")
                    ReelTextField(concept.audience, { v -> vm.edit { it.copy(audience = v) } }, label = "Intended audience")
                    ReelTextField(concept.clinicalPoints, { v -> vm.edit { it.copy(clinicalPoints = v) } }, label = "Clinical points to include", minLines = 2)
                    ReelTextField(concept.footageAvailability, { v -> vm.edit { it.copy(footageAvailability = v) } }, label = "Footage you already have", placeholder = "e.g. a treatment clip from last week")
                    ReelTextField(concept.tone, { v -> vm.edit { it.copy(tone = v) } }, label = "Preferred tone")
                    ReelTextField(concept.cta, { v -> vm.edit { it.copy(cta = v) } }, label = "Call to action")
                    ReelTextField(concept.mustInclude.joinToString("\n"), { v -> vm.edit { it.copy(mustInclude = v.lines().filter { l -> l.isNotBlank() }) } }, label = "Specific points to add (one per line)", minLines = 2)
                    ReelTextField(concept.excludedClaims.joinToString("\n"), { v -> vm.edit { it.copy(excludedClaims = v.lines().filter { l -> l.isNotBlank() }) } }, label = "Claims to exclude (one per line)", minLines = 2)
                }
            }
            InfoBanner("Only this text and your treatment profile are sent to OpenAI. No photos or patient footage.", icon = Icons.Rounded.Lightbulb)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun AiWorking(message: String, onCancel: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().background(Rf.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            ThinkingOrb(size = 150.dp)
            Spacer(Modifier.height(28.dp))
            Text(message, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Reelify Director is working with OpenAI", style = MaterialTheme.typography.bodySmall)
            if (onCancel != null) { Spacer(Modifier.height(24.dp)); GhostButton("Cancel", onCancel) }
        }
    }
}

@Composable
fun FeedbackScreen(pid: String, onBack: () -> Unit, onScriptReady: () -> Unit) {
    val vm = rfViewModel(pid) { FeedbackViewModel(it, pid) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = strings()
    var instruction by remember { mutableStateOf("") }
    ui.busy?.let { AiWorking(it, vm::cancel); return }
    val f = ui.feedback
    ScreenFrame("Creative direction", onBack, actions = { StepIndicator(2, 4, Modifier.padding(end = 16.dp)) }, bottomBar = {
        BottomActionBar { PrimaryButton(s.generateScript, { vm.generateScript(onScriptReady) }, Modifier.weight(1f), icon = Icons.Rounded.AutoAwesome, enabled = f != null) }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ui.error?.let { ErrorBanner(it, onDismiss = vm::dismissError) }
            if (f == null) { InfoBanner("No AI analysis yet. Go back and tap Analyze Idea, or generate the script directly."); return@Column }
            if (f.interpretation.isNotBlank()) Text(f.interpretation, style = MaterialTheme.typography.bodyLarge)
            CinematicCard {
                Eyebrow("Suggested hook")
                Spacer(Modifier.height(8.dp))
                ReelTextField(f.hook, { v -> vm.edit { it.copy(hook = v) } }, textStyle = MaterialTheme.typography.titleLarge)
                if (f.alternativeHooks.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Alternatives — tap to use", style = MaterialTheme.typography.labelMedium)
                    f.alternativeHooks.forEach { h ->
                        Box(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Rf.Surface2).clickable { vm.edit { it.copy(hook = h, alternativeHooks = it.alternativeHooks - h + it.hook) } }.padding(12.dp)) {
                            ScriptText(h, style = MaterialTheme.typography.bodyMedium.copy(color = Rf.Text))
                        }
                    }
                }
            }
            CinematicCard {
                Eyebrow("Main message"); Spacer(Modifier.height(8.dp))
                ReelTextField(f.mainMessage, { v -> vm.edit { it.copy(mainMessage = v) } })
                Spacer(Modifier.height(12.dp))
                Eyebrow("Creative angle"); Spacer(Modifier.height(8.dp))
                ReelTextField(f.creativeAngle, { v -> vm.edit { it.copy(creativeAngle = v) } })
                Spacer(Modifier.height(12.dp))
                Eyebrow("Audience"); Spacer(Modifier.height(8.dp))
                ReelTextField(f.targetAudience, { v -> vm.edit { it.copy(targetAudience = v) } })
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Recommended style: ", style = MaterialTheme.typography.bodyMedium); StatusPill(f.recommendedStyle.label, Rf.Info) }
            }
            CinematicCard {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Visibility, null, tint = Rf.RoseSoft); Spacer(Modifier.width(8.dp)); Eyebrow("Recommended visual approach") }
                Spacer(Modifier.height(8.dp))
                ReelTextField(f.visualApproach, { v -> vm.edit { it.copy(visualApproach = v) } })
            }
            if (f.improvements.isNotEmpty()) CinematicCard {
                Eyebrow("Creative improvements")
                f.improvements.forEachIndexed { i, imp ->
                    val on = i in ui.acceptedImprovements
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.toggleImprovement(i) }.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (on) Rf.Success else Rf.TextTertiary)
                        Spacer(Modifier.width(10.dp))
                        ScriptText(imp, style = MaterialTheme.typography.bodyMedium.copy(color = Rf.Text))
                    }
                }
            }
            if (f.cautions.isNotEmpty()) CinematicCard {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.WarningAmber, null, tint = Rf.Warning); Spacer(Modifier.width(8.dp)); Eyebrow("Important cautions", color = Rf.Warning) }
                f.cautions.forEach { cflag ->
                    Column(Modifier.padding(top = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(cflag.severity.name.lowercase(), if (cflag.severity == ClaimSeverity.BLOCKER) Rf.Danger else Rf.Warning)
                            Spacer(Modifier.width(8.dp))
                            ScriptText("“${cflag.claim}”", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(cflag.issue, style = MaterialTheme.typography.bodySmall)
                        ScriptText("→ ${cflag.suggestion}", style = MaterialTheme.typography.bodySmall.copy(color = Rf.Success))
                    }
                }
            }
            CinematicCard {
                Eyebrow("Want something different?")
                Spacer(Modifier.height(8.dp))
                ReelTextField(instruction, { instruction = it }, placeholder = "e.g. make it more engaging, add a myth angle, exclude pricing…", minLines = 2)
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Regenerate suggestions", { vm.regenerate(instruction) }, icon = Icons.Rounded.Refresh)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
