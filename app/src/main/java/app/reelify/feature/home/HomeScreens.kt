package app.reelify.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.reelify.core.designsystem.CinematicCard
import app.reelify.core.designsystem.Eyebrow
import app.reelify.core.designsystem.PrimaryButton
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.SectionHeader
import app.reelify.core.model.ReelStyle
import app.reelify.core.security.AiProvider
import app.reelify.feature.common.ProjectPoster
import app.reelify.feature.common.appContainer
import app.reelify.feature.common.strings
import app.reelify.feature.projects.ProjectsTab
import app.reelify.feature.settings.SettingsTab
import app.reelify.feature.studio.StudioTab
import app.reelify.navigation.Routes
import app.reelify.navigation.openProject
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val container = appContainer()
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        container.library.seedIfNeeded() // initialize local storage & repositories (never blocks on network)
        alpha.animateTo(1f, tween(500))
        scale.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        delay(350)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Rf.Background), contentAlignment = Alignment.Center) {
        Box(Modifier.size(320.dp).background(Brush.radialGradient(listOf(Rf.Rose.copy(alpha = 0.25f * alpha.value), Color.Transparent))))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(scale.value).alpha(alpha.value)) {
            LogoMark(84.dp)
            Spacer(Modifier.height(18.dp))
            Text("Reelify", style = MaterialTheme.typography.displayMedium)
            Text("AI REEL STUDIO", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 4.sp))
        }
    }
}

@Composable
fun LogoMark(size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        drawRoundRect(Rf.PrimaryGradient, cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.28f))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.40f, w * 0.30f); lineTo(w * 0.72f, w * 0.50f); lineTo(w * 0.40f, w * 0.70f); close()
        }
        drawPath(path, Color.White)
    }
}

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun MainScreen(nav: NavHostController) {
    val s = strings()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(Tab(s.home, Icons.Rounded.Home), Tab(s.projects, Icons.Rounded.VideoLibrary), Tab(s.studio, Icons.Rounded.Palette), Tab(s.settings, Icons.Rounded.Settings))
    Column(Modifier.fillMaxSize().background(Rf.Background)) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "tab") { t ->
                when (t) {
                    0 -> HomeTab(nav)
                    1 -> ProjectsTab(nav)
                    2 -> StudioTab(nav)
                    else -> SettingsTab(nav)
                }
            }
        }
        BottomNav(tabs, tab) { tab = it }
    }
}

@Composable
private fun BottomNav(tabs: List<Tab>, selected: Int, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2101016)))).navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEachIndexed { i, t ->
            val active = i == selected
            val bgAlpha by animateFloatAsState(if (active) 1f else 0f, label = "nav")
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) }
                    .background(Rf.Rose.copy(alpha = 0.14f * bgAlpha)).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, t.label, tint = if (active) Rf.RoseSoft else Rf.TextTertiary, modifier = Modifier.size(24.dp))
                Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (active) Rf.Text else Rf.TextTertiary)
            }
        }
    }
}

@Composable
private fun HomeTab(nav: NavHostController) {
    val c = appContainer()
    val s = strings()
    val projects by c.projects.observeProjects().collectAsStateWithLifecycle(initialValue = emptyList())
    val treatments by c.library.observeTreatments().collectAsStateWithLifecycle(initialValue = emptyList())
    val creds by c.credentials.statuses.collectAsStateWithLifecycle()
    val recentDraft = projects.firstOrNull { it.status != app.reelify.core.model.ProjectStatus.EXPORTED }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                LogoMark(34.dp)
                Spacer(Modifier.width(10.dp))
                Text("Reelify", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Settings, s.settings, tint = Rf.TextSecondary, modifier = Modifier.clip(CircleShape).clickable { nav.navigate(Routes.AI_SETTINGS) }.padding(8.dp))
            }
        }
        item { HeroCard(s.createReel, s.createReelSub) { nav.navigate(Routes.choose()) } }
        if (creds.values.any { !it.configured }) item {
            val missing = creds.values.filter { !it.configured }.joinToString(" & ") { it.provider.label }
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Rf.Warning.copy(alpha = 0.09f))
                    .border(1.dp, Rf.Warning.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).clickable { nav.navigate(Routes.AI_SETTINGS) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.WarningAmber, null, tint = Rf.Warning)
                Spacer(Modifier.width(10.dp))
                Text("$missing not connected. Local editing works; AI scripting/captions need keys.", style = MaterialTheme.typography.bodySmall.copy(color = Rf.Text), modifier = Modifier.weight(1f))
            }
        }
        if (recentDraft != null) item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Eyebrow(s.continueProject)
                Spacer(Modifier.height(8.dp))
                CinematicCard(onClick = { nav.openProject(recentDraft) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProjectPoster(recentDraft, Modifier.width(74.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(recentDraft.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Text("${recentDraft.status.label} · ${recentDraft.duration.label}", style = MaterialTheme.typography.bodySmall)
                        }
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Rf.PrimaryGradient), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White) }
                    }
                }
            }
        }
        if (projects.isNotEmpty()) {
            item { SectionHeader(s.recent, Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(projects.take(10), key = { it.id }) { p -> ProjectPoster(p, Modifier.width(140.dp).clickable { nav.openProject(p) }) }
                }
            }
        }
        item { SectionHeader(s.treatments, Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(treatments, key = { it.id }) { t ->
                    Column(
                        Modifier.width(150.dp).height(112.dp).clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(Color(t.accentColor).copy(alpha = 0.55f), Rf.Surface)))
                            .border(1.dp, Rf.Border, RoundedCornerShape(20.dp)).clickable { nav.navigate(Routes.choose(treatment = t.id)) }.padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                        Column {
                            Text(t.name, style = MaterialTheme.typography.titleSmall)
                            if (t.arabicName.isNotBlank()) Text(t.arabicName, style = MaterialTheme.typography.bodySmall.copy(fontFamily = app.reelify.core.designsystem.Tajawal))
                        }
                    }
                }
            }
        }
        item { SectionHeader(s.templates, Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(app.reelify.core.template.BuiltInTemplates.all, key = { it.id }) { tp ->
                    Column(Modifier.width(176.dp).clip(RoundedCornerShape(20.dp)).clickable { nav.navigate(Routes.choose(template = tp.id)) }) {
                        app.reelify.core.designsystem.TemplateThumbnail(tp, Modifier.fillMaxWidth().height(112.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(tp.name.resolve(app.reelify.feature.common.LocalArabicUi.current), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(tp.description.resolve(app.reelify.feature.common.LocalArabicUi.current), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "hero")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse), label = "h")
    Box(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(230.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3B0A1F), Rf.Burgundy, Color(0xFF1B0B14)), start = androidx.compose.ui.geometry.Offset(600f * shift, 0f), end = androidx.compose.ui.geometry.Offset(1200f, 900f)))
            .border(1.dp, Rf.BorderStrong, RoundedCornerShape(28.dp)).clickable(onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Rf.Rose.copy(alpha = 0.55f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width * (0.75f + 0.1f * shift), size.height * 0.2f), radius = size.width * 0.6f), size.width * 0.6f, center = androidx.compose.ui.geometry.Offset(size.width * (0.75f + 0.1f * shift), size.height * 0.2f))
            for (i in 0 until 3) {
                val x = size.width * (0.62f + i * 0.11f); val h = size.height * (0.5f - i * 0.08f)
                drawRoundRect(Color.White.copy(alpha = 0.06f + i * 0.03f), topLeft = androidx.compose.ui.geometry.Offset(x, size.height * 0.18f + i * 14f), size = androidx.compose.ui.geometry.Size(size.width * 0.16f, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
            Eyebrow("AI creative director", color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.8f)))
            Spacer(Modifier.height(14.dp))
            PrimaryButton(title, onClick, icon = Icons.Rounded.Add, height = 48.dp)
        }
    }
}
