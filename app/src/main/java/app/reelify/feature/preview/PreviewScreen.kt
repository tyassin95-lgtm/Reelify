package app.reelify.feature.preview

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reelify.core.designsystem.BottomActionBar
import app.reelify.core.designsystem.Chip
import app.reelify.core.designsystem.CinematicCard
import app.reelify.core.designsystem.ErrorBanner
import app.reelify.core.designsystem.Eyebrow
import app.reelify.core.designsystem.GhostButton
import app.reelify.core.designsystem.InfoBanner
import app.reelify.core.designsystem.LabeledSlider
import app.reelify.core.designsystem.PrimaryButton
import app.reelify.core.designsystem.ReelTextField
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.ScreenFrame
import app.reelify.core.designsystem.SecondaryButton
import app.reelify.core.designsystem.Spinner
import app.reelify.core.designsystem.ToggleRow
import app.reelify.core.library.SoundLibrary
import app.reelify.core.model.BeautyPreset
import app.reelify.core.model.BeautySettings
import app.reelify.core.model.CaptionAnimation
import app.reelify.core.model.CaptionPresetId
import app.reelify.core.model.CaptionSource
import app.reelify.core.model.CaptionStyle
import app.reelify.core.model.ColorLook
import app.reelify.core.model.LogoPosition
import app.reelify.core.model.NoiseReduction
import app.reelify.core.model.ProjectBundle
import app.reelify.core.model.TransitionType
import app.reelify.feature.common.PlayerSurface
import app.reelify.feature.common.appContainer
import app.reelify.feature.common.rememberPreviewController
import app.reelify.feature.common.rfViewModel
import app.reelify.feature.common.strings
import kotlinx.coroutines.delay

private enum class QuickTab(val label: String) { AI("Ask AI"), CAPTIONS("Captions"), MUSIC("Music"), TRANSITIONS("Transitions"), COLOR("Color"), BEAUTY("Beauty"), BRANDING("Branding"), AUDIO("Audio"), EFFECTS("Effects") }

fun fmtTime(ms: Long) = "%d:%02d.%d".format(ms / 60000, (ms / 1000) % 60, (ms % 1000) / 100)

private enum class Adjust(val en: String, val ar: String) {
    CAPTIONS("Captions", "الترجمة"), MUSIC("Music", "الموسيقى"), BEAUTY("Beauty", "التجميل"), COLOR("Color", "الألوان"),
    BRANDING("Branding", "الهوية"), REPLACE("Replace Shot", "استبدال لقطة"), TRIM("Trim Shot", "قص لقطة"), ALL("All controls", "كل الإعدادات")
}

@Composable
fun PreviewScreen(pid: String, onBack: () -> Unit, onAdvanced: () -> Unit, onCaptions: () -> Unit, onFootage: () -> Unit, onScript: () -> Unit, onExport: () -> Unit, onRebuild: () -> Unit, onTrim: (String) -> Unit = {}) {
    val vm = rfViewModel(pid) { PreviewViewModel(it, pid) }
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val s = strings()
    val controller = rememberPreviewController(ctx)
    val playing by controller.playing.collectAsStateWithLifecycle()
    val playerError by controller.error.collectAsStateWithLifecycle()
    var pos by remember { mutableLongStateOf(0L) }
    var adjust by remember { mutableStateOf<Adjust?>(null) }
    var showAdjust by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(ui.proxyFile, ui.composition, ui.buildError) {
        val proxy = ui.proxyFile
        if (proxy != null && proxy.exists() && proxy.length() > 1024) {
            controller.setFile(proxy, controller.position())
        } else if (!ui.building && ui.buildError != null && ui.exportedFile != null) {
            controller.setFile(ui.exportedFile!!, controller.position())
        }
    }
    LaunchedEffect(Unit) { while (true) { pos = controller.position(); delay(80) } }
    val b = bundle ?: return
    val total = b.project.duration.totalMs
    val renderedVideo = ui.exportedFile ?: ui.proxyFile
    fun openExternal(file: java.io.File) {
        runCatching {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }

    ScreenFrame(b.project.title, onBack, subtitle = app.reelify.feature.common.tr("Step 4 of 4 · Preview & export", "الخطوة 4 من 4 · المعاينة والتصدير"), actions = {
        GhostButton(app.reelify.feature.common.tr("Advanced", "متقدم"), onAdvanced, icon = Icons.Rounded.ViewTimeline, color = Rf.TextSecondary)
    }, bottomBar = {
        BottomActionBar {
            SecondaryButton(app.reelify.feature.common.tr("Adjust", "تعديل"), { showAdjust = true; adjust = null }, icon = Icons.Rounded.Tune)
            PrimaryButton(s.export, onExport, Modifier.weight(1f), icon = Icons.Rounded.IosShare, enabled = ui.timeline?.isRenderable == true)
        }
    }) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxHeight().aspectRatio(9f / 16f).clip(RoundedCornerShape(22.dp)).background(Color.Black).clickable { controller.toggle() }) {
                    PlayerSurface(controller, Modifier.fillMaxSize())
                    if (ui.building) {
                        Row(
                            Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xAA000000)).padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spinner(16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${ui.renderProgress ?: 0}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                            )
                        }
                    }
                    if (!playing && !ui.building) Box(Modifier.align(Alignment.Center).size(68.dp).clip(CircleShape).background(Color(0x88000000)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                ui.buildError?.let { err ->
                    ErrorBanner("Preview is unavailable. Open the rendered video. ($err)", onRetry = vm::retryPreview)
                }
                playerError?.let { err ->
                    ErrorBanner("Preview is unavailable. Open the rendered video. ($err)", onRetry = vm::retryPreview)
                }
                if ((ui.buildError != null || playerError != null) && renderedVideo != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(app.reelify.feature.common.tr("Play rendered video", "تشغيل الفيديو الجاهز"), { controller.setFile(renderedVideo, 0L, play = true) })
                        SecondaryButton(app.reelify.feature.common.tr("External player", "مشغّل خارجي"), { openExternal(renderedVideo) })
                    }
                } else if (ui.exportedFile != null && !ui.building) {
                    GhostButton(app.reelify.feature.common.tr("Open exported MP4", "افتح الفيديو المصدّر"), { openExternal(ui.exportedFile!!) })
                }
                ui.timeline?.blocking?.takeIf { it.isNotEmpty() }?.let { InfoBanner(it.joinToString("\n") { i -> i.message }, color = Rf.Warning) }
                if (b.project.captions?.source == CaptionSource.SCRIPT_ESTIMATE && b.project.edit.captions.enabled) Text(
                    app.reelify.feature.common.tr("Captions are approximate — estimated from the script, not transcribed.", "الترجمة تقريبية — مأخوذة من السكربت، مو من التفريغ الصوتي."),
                    style = MaterialTheme.typography.labelMedium, color = Rf.Warning, modifier = Modifier.padding(bottom = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ controller.toggle() }) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Rf.Text) }
                    Scrubber(pos, total, Modifier.weight(1f)) { controller.seek(it) }
                    Text(fmtTime(pos), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (showAdjust) ModalBottomSheet({ showAdjust = false }, containerColor = Rf.Surface) {
        QuickAdjustSheet(b, vm, ui, adjust, { adjust = it }, onCaptions, onScript, onRebuild, onFootage = { showAdjust = false; onFootage() }, onTrim = { showAdjust = false; onTrim(it) })
    }
}

/** Focused, simple adjustment panels; "All controls" keeps every previous setting available. */
@Composable
private fun QuickAdjustSheet(
    b: ProjectBundle, vm: PreviewViewModel, ui: PreviewUi, current: Adjust?, onPick: (Adjust?) -> Unit,
    onCaptions: () -> Unit, onScript: () -> Unit, onRebuild: () -> Unit, onFootage: () -> Unit, onTrim: (String) -> Unit,
) {
    val arabic = app.reelify.feature.common.LocalArabicUi.current
    val e = b.project.edit
    val tpl = b.project.template.takeIf { b.project.isTemplate }
    Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (current != null) IconButton({ onPick(null) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Rf.Text) }
            Text(current?.let { if (arabic) it.ar else it.en } ?: app.reelify.feature.common.tr("Adjust", "تعديل"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        if (current == null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Adjust.entries.forEach { a ->
                    Column(Modifier.width(96.dp).clip(RoundedCornerShape(18.dp)).background(Rf.Surface2).clickable { onPick(a) }.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(adjustIcon(a), null, tint = Rf.RoseSoft)
                        Spacer(Modifier.height(6.dp))
                        Text(if (arabic) a.ar else a.en, style = MaterialTheme.typography.labelMedium, color = Rf.Text, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            return@Column
        }
        fun resetRow(group: app.reelify.core.model.EditGroup): @Composable () -> Unit = {
            if (tpl != null && group in e.userOverrides) GhostButton(app.reelify.feature.common.tr("Reset to template", "رجوع لإعدادات القالب"), { vm.resetToTemplate(group) }, icon = Icons.Rounded.Refresh)
        }
        when (current) {
            Adjust.CAPTIONS -> {
                ToggleRow(app.reelify.feature.common.tr("Captions", "الترجمة"), e.captions.enabled, { v -> vm.edit { it.copy(captions = it.captions.copy(enabled = v)) } },
                    subtitle = when (b.project.captions?.source) {
                        CaptionSource.DEEPGRAM -> app.reelify.feature.common.tr("Synced from transcription", "متزامنة من التفريغ الصوتي")
                        CaptionSource.SCRIPT_ESTIMATE -> app.reelify.feature.common.tr("Approximate — estimated from the script", "تقريبية — من السكربت")
                        CaptionSource.MANUAL -> app.reelify.feature.common.tr("Manually edited", "معدّلة يدوياً"); null -> app.reelify.feature.common.tr("Not generated yet", "ما انعملت بعد")
                    })
                val st = e.captions.style
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CaptionPresetId.PREMIUM_MINIMAL, CaptionPresetId.BOLD_ENGAGEMENT, CaptionPresetId.CLEAN_CLINICAL).forEach { id ->
                        Chip(id.label + if (tpl?.captionPresetId == id) " ★" else "", st.preset == id, { vm.edit { it.copy(captions = it.captions.copy(style = CaptionStyle.preset(id).copy(timingOffsetMs = st.timingOffsetMs))) } })
                    }
                }
                LabeledSlider(app.reelify.feature.common.tr("Size", "الحجم"), st.sizeSp, { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(sizeSp = v, preset = CaptionPresetId.CUSTOM))) } }, range = 36f..96f, valueText = "${st.sizeSp.toInt()}")
                SecondaryButton(app.reelify.feature.common.tr("Edit caption text", "تعديل نص الترجمة"), onCaptions, icon = Icons.Rounded.Subtitles)
                resetRow(app.reelify.core.model.EditGroup.CAPTIONS)()
            }
            Adjust.MUSIC -> {
                ToggleRow(app.reelify.feature.common.tr("Background music", "موسيقى خلفية"), e.music.trackId != null, { v -> vm.edit { it.copy(music = it.music.copy(trackId = if (v) (tpl?.let { t -> app.reelify.core.template.ProductionPlanner.musicPreset(t).trackId } ?: SoundLibrary.match(b.project.plan?.musicMood, 0.6f, true).id) else null)) } })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoundLibrary.music.forEach { t -> Chip(t.title + if (tpl != null && app.reelify.core.template.ProductionPlanner.musicPreset(tpl).trackId == t.id) " ★" else "", e.music.trackId == t.id, { vm.edit { it.copy(music = it.music.copy(trackId = t.id)) } }) }
                }
                LabeledSlider(app.reelify.feature.common.tr("Music volume", "صوت الموسيقى"), e.music.volume, { v -> vm.edit { it.copy(music = it.music.copy(volume = v)) } }, range = 0f..0.8f)
                resetRow(app.reelify.core.model.EditGroup.MUSIC)()
            }
            Adjust.BEAUTY -> {
                Text(app.reelify.feature.common.tr("Applies to talking shots only. Treatment footage is never beautified.", "ينطبق على لقطات الكلام فقط. لقطات العلاج ما تتجمّل أبداً."), style = MaterialTheme.typography.bodySmall)
                val talking = b.clips.filter { !it.isClinical && it.role == app.reelify.core.model.ClipRole.PRIMARY && it.kind == app.reelify.core.model.MediaKind.VIDEO && !it.muted }
                val currentB = talking.firstOrNull()?.beauty
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(app.reelify.feature.common.tr("Off", "إطفاء"), currentB?.enabled == false, { vm.applyBeautyToTalkingClips(BeautySettings(enabled = false)) })
                    BeautyPreset.entries.filter { it != BeautyPreset.CUSTOM }.forEach { p -> Chip(p.label, currentB?.enabled == true && currentB.preset == p, { vm.applyBeautyToTalkingClips(BeautySettings.forPreset(p, currentB?.intensity ?: 0.7f)) }) }
                }
                if (currentB?.enabled == true) LabeledSlider(app.reelify.feature.common.tr("Intensity", "الشدة"), currentB.intensity, { v -> vm.applyBeautyToTalkingClips(currentB.copy(intensity = v)) })
            }
            Adjust.COLOR -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorLook.entries.forEach { l -> Chip(l.label + if (tpl?.colorLook == l) " ★" else "", e.color.look == l, { vm.edit { it.copy(color = it.color.copy(look = l)) } }) }
                }
                LabeledSlider(app.reelify.feature.common.tr("Look intensity", "شدة اللون"), e.color.intensity, { v -> vm.edit { it.copy(color = it.color.copy(intensity = v)) } })
                resetRow(app.reelify.core.model.EditGroup.COLOR)()
            }
            Adjust.BRANDING -> {
                val br = e.branding
                ToggleRow(app.reelify.feature.common.tr("Branding", "الهوية"), br.enabled, { v -> vm.edit { it.copy(branding = br.copy(enabled = v)) } }, subtitle = if (b.project.brandSnapshot?.isConfigured == true) null else app.reelify.feature.common.tr("Set up your brand in Studio → Branding", "جهّز هويتك من الاستوديو ← الهوية"))
                ToggleRow(app.reelify.feature.common.tr("Logo watermark", "شعار"), br.watermark, { v -> vm.edit { it.copy(branding = br.copy(watermark = v)) } })
                ToggleRow(app.reelify.feature.common.tr("Name lower-third", "اسم المقدم"), br.lowerThird, { v -> vm.edit { it.copy(branding = br.copy(lowerThird = v)) } })
                ToggleRow(app.reelify.feature.common.tr("Call to action at the end", "دعوة بالنهاية"), br.ctaOverlay, { v -> vm.edit { it.copy(branding = br.copy(ctaOverlay = v)) } })
                ToggleRow(app.reelify.feature.common.tr("Logo reveal outro", "ظهور الشعار بالنهاية"), br.logoReveal, { v -> vm.edit { it.copy(branding = br.copy(logoReveal = v)) } })
                resetRow(app.reelify.core.model.EditGroup.BRANDING)()
            }
            Adjust.REPLACE -> {
                Text(app.reelify.feature.common.tr("Open the shot list to retake or re-import a shot.", "افتح قائمة اللقطات حتى تعيد تصوير أو تستورد لقطة."), style = MaterialTheme.typography.bodySmall)
                PrimaryButton(app.reelify.feature.common.tr("Open shot list", "افتح اللقطات"), onFootage, Modifier.fillMaxWidth(), icon = Icons.Rounded.VideoLibrary)
            }
            Adjust.TRIM -> {
                b.sections.sortedBy { it.index }.forEach { sec ->
                    val clip = b.clipsFor(sec.id, app.reelify.core.model.ClipRole.PRIMARY).firstOrNull { it.kind == app.reelify.core.model.MediaKind.VIDEO }
                    SecondaryButton(app.reelify.feature.common.tr("Trim shot ${sec.index}", "قص اللقطة ${sec.index}"), { clip?.let { onTrim(it.id) } }, Modifier.fillMaxWidth(), icon = Icons.Rounded.ContentCut, enabled = clip != null)
                }
            }
            Adjust.ALL -> QuickPanelTabs(b, vm, ui, onCaptions, onScript, onRebuild)
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun adjustIcon(a: Adjust) = when (a) {
    Adjust.CAPTIONS -> Icons.Rounded.Subtitles; Adjust.MUSIC -> Icons.Rounded.MusicNote; Adjust.BEAUTY -> Icons.Rounded.Face
    Adjust.COLOR -> Icons.Rounded.Palette; Adjust.BRANDING -> Icons.Rounded.Verified; Adjust.REPLACE -> Icons.Rounded.VideoLibrary
    Adjust.TRIM -> Icons.Rounded.ContentCut; Adjust.ALL -> Icons.Rounded.Tune
}

@Composable
private fun QuickPanelTabs(b: ProjectBundle, vm: PreviewViewModel, ui: PreviewUi, onCaptions: () -> Unit, onScript: () -> Unit, onRebuild: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(QuickTab.CAPTIONS) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(QuickTab.entries) { t -> Chip(t.label, t == tab, { tab = t }, leading = if (t == QuickTab.AI) Icons.Rounded.AutoAwesome else null) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { QuickPanel(tab, b, vm, ui, onCaptions, onScript, onRebuild) }
}

@Composable
fun Scrubber(pos: Long, total: Long, modifier: Modifier = Modifier, onSeek: (Long) -> Unit) {
    Canvas(modifier.height(28.dp).pointerInput(total) { detectTapGestures { o -> onSeek((o.x / size.width * total).toLong()) } }) {
        val y = size.height / 2
        drawRoundRect(Rf.Surface3, Offset(0f, y - 3f), Size(size.width, 6f), androidx.compose.ui.geometry.CornerRadius(3f))
        for (i in 1..2) drawLine(Rf.Gold.copy(alpha = 0.7f), Offset(size.width * i / 3, y - 8f), Offset(size.width * i / 3, y + 8f), 2f)
        val x = size.width * (pos.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
        drawRoundRect(Rf.Rose, Offset(0f, y - 3f), Size(x, 6f), androidx.compose.ui.geometry.CornerRadius(3f))
        drawCircle(Color.White, 8f, Offset(x, y))
    }
}

@Composable
private fun QuickPanel(tab: QuickTab, b: ProjectBundle, vm: PreviewViewModel, ui: PreviewUi, onCaptions: () -> Unit, onScript: () -> Unit, onRebuild: () -> Unit) {
    val e = b.project.edit
    when (tab) {
        QuickTab.AI -> {
            var req by remember { mutableStateOf("") }
            Text("Describe a change — e.g. “make it more cinematic”, “softer music”, “highlight more keywords”, “calmer clinic style”.", style = MaterialTheme.typography.bodySmall)
            ReelTextField(req, { req = it }, placeholder = "What should change?", minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(if (ui.aiBusy) "Thinking…" else "Apply", { vm.aiEdit(req) }, loading = ui.aiBusy, enabled = req.isNotBlank(), height = 46.dp)
                if (ui.canUndoAi) GhostButton("Undo AI change", vm::undoAi, icon = Icons.AutoMirrored.Rounded.Undo)
            }
            ui.aiSummary?.let { InfoBanner(it, color = Rf.Success) }
            ui.aiError?.let { ErrorBanner(it) }
            Text("The AI returns a declarative edit plan; Reelify applies it locally and deterministically.", style = MaterialTheme.typography.bodySmall)
        }
        QuickTab.CAPTIONS -> {
            val st = e.captions.style
            ToggleRow("Captions", e.captions.enabled, { v -> vm.edit { it.copy(captions = it.captions.copy(enabled = v)) } },
                subtitle = when (b.project.captions?.source) { CaptionSource.DEEPGRAM -> "Synced from Deepgram transcription"; CaptionSource.SCRIPT_ESTIMATE -> "Estimated from script — connect Deepgram for exact sync"; CaptionSource.MANUAL -> "Manually edited"; null -> "Not generated yet" })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptionPresetId.entries.filter { it != CaptionPresetId.CUSTOM }.forEach { id -> Chip(id.label, st.preset == id, { vm.edit { it.copy(captions = it.captions.copy(style = CaptionStyle.preset(id).copy(timingOffsetMs = st.timingOffsetMs))) } }) }
            }
            Eyebrow("Animation")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptionAnimation.entries.forEach { a -> Chip(a.label, st.animation == a, { vm.edit { it.copy(captions = it.captions.copy(style = st.copy(animation = a, preset = CaptionPresetId.CUSTOM))) } }) }
            }
            LabeledSlider("Size", st.sizeSp, { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(sizeSp = v, preset = CaptionPresetId.CUSTOM))) } }, range = 36f..96f, valueText = "${st.sizeSp.toInt()}")
            LabeledSlider("Vertical position", st.positionY, { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(positionY = v))) } }, range = 0.2f..0.8f)
            LabeledSlider("Words per caption", st.wordsPerCaption.toFloat(), { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(wordsPerCaption = v.toInt()))) } }, range = 1f..8f, valueText = "${st.wordsPerCaption}")
            LabeledSlider("Timing offset", st.timingOffsetMs.toFloat(), { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(timingOffsetMs = v.toLong()))) } }, range = -500f..500f, valueText = "${st.timingOffsetMs} ms")
            ToggleRow("Text background", st.backgroundEnabled, { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(backgroundEnabled = v, backgroundColor = if (v && st.backgroundColor == 0L) 0xCC000000 else st.backgroundColor))) } })
            LabeledSlider("Outline", st.strokeWidth, { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(strokeWidth = v))) } }, range = 0f..10f, valueText = "%.0f".format(st.strokeWidth))
            ToggleRow("Shadow", st.shadow, { v -> vm.edit { it.copy(captions = it.captions.copy(style = st.copy(shadow = v))) } })
            ToggleRow("Also export .srt subtitle file", e.captions.exportSrt, { v -> vm.edit { it.copy(captions = it.captions.copy(exportSrt = v)) } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Edit caption text", onCaptions, icon = Icons.Rounded.Subtitles)
                GhostButton("Save style", { vm.saveCaptionPreset("Style ${System.currentTimeMillis() % 1000}") })
            }
        }
        QuickTab.MUSIC -> {
            ToggleRow("Background music", e.music.trackId != null, { v -> vm.edit { it.copy(music = it.music.copy(trackId = if (v) SoundLibrary.match(b.project.plan?.musicMood, 0.6f, true).id else null)) } })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundLibrary.music.forEach { t -> Chip("${t.title} · ${t.mood.label}", e.music.trackId == t.id, { vm.edit { it.copy(music = it.music.copy(trackId = t.id)) } }) }
                val ctx = LocalContext.current
                appContainer().storage.musicDir().listFiles()?.forEach { f -> Chip("♪ ${f.nameWithoutExtension}", e.music.trackId == "file:${f.absolutePath}", { vm.edit { it.copy(music = it.music.copy(trackId = "file:${f.absolutePath}")) } }) }
            }
            LabeledSlider("Music volume", e.music.volume, { v -> vm.edit { it.copy(music = it.music.copy(volume = v)) } }, range = 0f..0.8f)
            ToggleRow("Duck under speech", e.music.ducking, { v -> vm.edit { it.copy(music = it.music.copy(ducking = v)) } })
            if (e.music.ducking) LabeledSlider("Ducking depth", e.music.duckDepth, { v -> vm.edit { it.copy(music = it.music.copy(duckDepth = v)) } }, range = 0.2f..0.9f)
            LabeledSlider("Fade in", e.music.fadeInMs / 1000f, { v -> vm.edit { it.copy(music = it.music.copy(fadeInMs = (v * 1000).toLong())) } }, range = 0f..4f, valueText = "%.1fs".format(e.music.fadeInMs / 1000f))
            LabeledSlider("Fade out", e.music.fadeOutMs / 1000f, { v -> vm.edit { it.copy(music = it.music.copy(fadeOutMs = (v * 1000).toLong())) } }, range = 0f..5f, valueText = "%.1fs".format(e.music.fadeOutMs / 1000f))
        }
        QuickTab.TRANSITIONS -> {
            Eyebrow("Between sections")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TransitionType.entries.forEach { t -> Chip(t.label, e.transitions.defaultType == t && e.transitions.overrides.isEmpty(), { vm.edit { it.copy(transitions = it.transitions.copy(defaultType = t, overrides = emptyMap())) } }) }
            }
            Eyebrow("Within sections (B-roll cuts)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TransitionType.entries.forEach { t -> Chip(t.label, e.transitions.withinSection == t, { vm.edit { it.copy(transitions = it.transitions.copy(withinSection = t)) } }) }
            }
            LabeledSlider("Transition length", e.transitions.durationMs.toFloat(), { v -> vm.edit { it.copy(transitions = it.transitions.copy(durationMs = v.toLong())) } }, range = 200f..900f, valueText = "${e.transitions.durationMs} ms")
            Text("Transitions happen inside the existing timeline, so the reel length never changes.", style = MaterialTheme.typography.bodySmall)
        }
        QuickTab.COLOR -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorLook.entries.forEach { l -> Chip(l.label, e.color.look == l, { vm.edit { it.copy(color = it.color.copy(look = l)) } }) }
            }
            LabeledSlider("Look intensity", e.color.intensity, { v -> vm.edit { it.copy(color = it.color.copy(intensity = v)) } })
            LabeledSlider("Brightness", e.color.brightness, { v -> vm.edit { it.copy(color = it.color.copy(brightness = v)) } }, range = -1f..1f, valueText = "%.2f".format(e.color.brightness))
            LabeledSlider("Contrast", e.color.contrast, { v -> vm.edit { it.copy(color = it.color.copy(contrast = v)) } }, range = -1f..1f, valueText = "%.2f".format(e.color.contrast))
            LabeledSlider("Saturation", e.color.saturation, { v -> vm.edit { it.copy(color = it.color.copy(saturation = v)) } }, range = -1f..1f, valueText = "%.2f".format(e.color.saturation))
            LabeledSlider("Warmth", e.color.warmth, { v -> vm.edit { it.copy(color = it.color.copy(warmth = v)) } }, range = -1f..1f, valueText = "%.2f".format(e.color.warmth))
            LabeledSlider("Highlights", e.color.highlights, { v -> vm.edit { it.copy(color = it.color.copy(highlights = v)) } }, range = -1f..1f, valueText = "%.2f".format(e.color.highlights))
            LabeledSlider("Shadows", e.color.shadows, { v -> vm.edit { it.copy(color = it.color.copy(shadows = v)) } }, range = -1f..1f, valueText = "%.2f".format(e.color.shadows))
        }
        QuickTab.BEAUTY -> {
            Text("Applies to talking footage only. Clinical / treatment clips are never beautified automatically.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Off", false, { vm.applyBeautyToTalkingClips(BeautySettings(enabled = false)) })
                BeautyPreset.entries.filter { it != BeautyPreset.CUSTOM }.forEach { p -> Chip(p.label, false, { vm.applyBeautyToTalkingClips(BeautySettings.forPreset(p)) }) }
            }
            Text("Fine-tune per clip from Footage → Trim. Faces are tracked on-device for face-localized smoothing and under-eye brightening.", style = MaterialTheme.typography.bodySmall)
        }
        QuickTab.BRANDING -> {
            val br = e.branding
            ToggleRow("Branding", br.enabled, { v -> vm.edit { it.copy(branding = br.copy(enabled = v)) } }, subtitle = if (b.project.brandSnapshot?.isConfigured == true) "Using your saved brand" else "Set up your brand in Studio → Branding")
            ToggleRow("Logo watermark", br.watermark, { v -> vm.edit { it.copy(branding = br.copy(watermark = v)) } })
            ToggleRow("Watermark only during treatment footage", br.watermarkOnlyOnTreatment, { v -> vm.edit { it.copy(branding = br.copy(watermarkOnlyOnTreatment = v)) } })
            ToggleRow("Name lower-third", br.lowerThird, { v -> vm.edit { it.copy(branding = br.copy(lowerThird = v)) } })
            ToggleRow("Booking CTA in final seconds", br.ctaOverlay, { v -> vm.edit { it.copy(branding = br.copy(ctaOverlay = v)) } })
            ToggleRow("Logo reveal outro", br.logoReveal, { v -> vm.edit { it.copy(branding = br.copy(logoReveal = v)) } })
            LabeledSlider("CTA duration", br.ctaDurationMs / 1000f, { v -> vm.edit { it.copy(branding = br.copy(ctaDurationMs = (v * 1000).toLong())) } }, range = 2f..8f, valueText = "%.1fs".format(br.ctaDurationMs / 1000f))
            Eyebrow("Logo position")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LogoPosition.entries.forEach { p -> Chip(p.name.replace('_', ' ').lowercase(), (br.logoPosition ?: b.project.brandSnapshot?.logoPosition) == p, { vm.edit { it.copy(branding = br.copy(logoPosition = p)) } }) }
            }
            LabeledSlider("Nudge logo horizontally", br.logoOffsetX, { v -> vm.edit { it.copy(branding = br.copy(logoOffsetX = v)) } }, range = -0.1f..0.1f, valueText = "%.2f".format(br.logoOffsetX))
            LabeledSlider("Nudge logo vertically", br.logoOffsetY, { v -> vm.edit { it.copy(branding = br.copy(logoOffsetY = v)) } }, range = -0.1f..0.1f, valueText = "%.2f".format(br.logoOffsetY))
        }
        QuickTab.AUDIO -> {
            val a = e.audio
            Eyebrow("Noise reduction (classical DSP, not generative)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NoiseReduction.entries.forEach { n -> Chip(n.label, a.noiseReduction == n, { vm.edit { it.copy(audio = a.copy(noiseReduction = n)) } }) } }
            LabeledSlider("Dialogue volume", a.dialogueVolume, { v -> vm.edit { it.copy(audio = a.copy(dialogueVolume = v)) } }, range = 0f..2f, valueText = "${(a.dialogueVolume * 100).toInt()}%")
            LabeledSlider("Sound effects volume", a.sfxVolume, { v -> vm.edit { it.copy(audio = a.copy(sfxVolume = v)) } })
            ToggleRow("Speech EQ (clarity)", a.speechEq, { v -> vm.edit { it.copy(audio = a.copy(speechEq = v)) } })
            ToggleRow("Compression", a.compression, { v -> vm.edit { it.copy(audio = a.copy(compression = v)) } })
            ToggleRow("Loudness normalization + limiter", a.normalize, { v -> vm.edit { it.copy(audio = a.copy(normalize = v)) } })
            Text("${e.sfx.size} sound effect cue(s). Add, move or remove them in the Advanced editor.", style = MaterialTheme.typography.bodySmall)
        }
        QuickTab.EFFECTS -> {
            val fx = e.effects
            ToggleRow("Slow digital push-in on talking shots", fx.pushIn, { v -> vm.edit { it.copy(effects = fx.copy(pushIn = v)) } })
            ToggleRow("Intro title animation", fx.introTitle, { v -> vm.edit { it.copy(effects = fx.copy(introTitle = v)) } })
            ToggleRow("Section title emphasis", fx.sectionTitles, { v -> vm.edit { it.copy(effects = fx.copy(sectionTitles = v)) } })
            ToggleRow("Soft light overlay", fx.lightOverlay, { v -> vm.edit { it.copy(effects = fx.copy(lightOverlay = v)) } })
            LabeledSlider("Vignette", fx.vignette, { v -> vm.edit { it.copy(effects = fx.copy(vignette = v)) } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Edit script", onScript, icon = Icons.Rounded.Edit)
                GhostButton("Rebuild automatically", onRebuild, icon = Icons.Rounded.Refresh)
            }
        }
    }
}
