package app.reelify.feature.autoedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.reelify.core.designsystem.BottomActionBar
import app.reelify.core.designsystem.CinematicCard
import app.reelify.core.designsystem.ErrorBanner
import app.reelify.core.designsystem.GlowProgressBar
import app.reelify.core.designsystem.InfoBanner
import app.reelify.core.designsystem.PrimaryButton
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.ScreenFrame
import app.reelify.core.designsystem.SecondaryButton
import app.reelify.core.designsystem.Spinner
import app.reelify.core.designsystem.ThinkingOrb
import app.reelify.core.pipeline.AutoEditOptions
import app.reelify.core.pipeline.AutoEditProgress
import app.reelify.core.pipeline.AutoEditStage
import app.reelify.feature.common.appContainer
import kotlinx.coroutines.delay

@Composable
fun AutoEditScreen(pid: String, force: Boolean, onBack: () -> Unit, onDone: () -> Unit, onFixFootage: () -> Unit) {
    val c = appContainer()
    var state by remember { mutableStateOf(AutoEditProgress(AutoEditStage.VALIDATE)) }
    var runId by remember { mutableIntStateOf(0) }
    var retryCaptions by remember { mutableStateOf(false) }
    LaunchedEffect(runId) {
        c.autoEditor.run(pid, AutoEditOptions(forceRetranscribe = (force && runId == 0) || retryCaptions)).collect { state = it }
    }
    val transcriptionFailed = state.warnings.any { it.startsWith("Transcription unavailable") }
    LaunchedEffect(state.done) { if (state.done && state.warnings.isEmpty()) { delay(700); onDone() } }
    val stages = AutoEditStage.entries.filter { it != AutoEditStage.DONE }
    ScreenFrame(app.reelify.feature.common.tr("Preparing your reel", "نجهّز الريل"), onBack, subtitle = app.reelify.feature.common.tr("Step 4 of 4 · processed on this phone", "الخطوة 4 من 4 · المعالجة على التلفون"), bottomBar = {
        BottomActionBar {
            when {
                state.done && transcriptionFailed -> {
                    SecondaryButton(app.reelify.feature.common.tr("Retry captions", "إعادة الترجمة"), { retryCaptions = true; runId++ }, icon = Icons.Rounded.Refresh)
                    PrimaryButton(app.reelify.feature.common.tr("Open preview", "افتح المعاينة"), onDone, Modifier.weight(1f))
                }
                state.done -> PrimaryButton(app.reelify.feature.common.tr("Open preview", "افتح المعاينة"), onDone, Modifier.weight(1f))
                state.error != null && state.issues.any { it.blocking } -> PrimaryButton(app.reelify.feature.common.tr("Fix footage", "صلّح اللقطات"), onFixFootage, Modifier.weight(1f))
                state.error != null -> PrimaryButton(app.reelify.feature.common.tr("Retry", "إعادة المحاولة"), { runId++ }, Modifier.weight(1f), icon = Icons.Rounded.Refresh)
                else -> SecondaryButton(app.reelify.feature.common.tr("Cancel", "إلغاء"), onBack, Modifier.weight(1f))
            }
        }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { ThinkingOrb(size = 120.dp) }
            Text(state.message, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (!state.done && state.error == null) GlowProgressBar(state.fraction)
            state.error?.let { ErrorBanner(it) }
            CinematicCard {
                stages.forEach { st ->
                    val done = st in state.completed
                    val current = st == state.stage && !state.done && state.error == null
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(if (done) Rf.Success.copy(alpha = 0.2f) else Rf.Surface3), contentAlignment = Alignment.Center) {
                            when {
                                done -> Icon(Icons.Rounded.Check, null, tint = Rf.Success, modifier = Modifier.size(18.dp))
                                current -> Spinner(18.dp, stroke = 2.dp)
                                else -> Box(Modifier.size(6.dp).clip(CircleShape).background(Rf.TextTertiary))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(st.label, style = MaterialTheme.typography.titleSmall, color = if (done || current) Rf.Text else Rf.TextTertiary)
                    }
                }
            }
            if (state.warnings.isNotEmpty()) InfoBanner(state.warnings.joinToString("\n• ", prefix = "• "), color = Rf.Warning)
            Spacer(Modifier.height(20.dp))
        }
    }
}
