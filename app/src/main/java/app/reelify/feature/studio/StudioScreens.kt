package app.reelify.feature.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavHostController
import app.reelify.core.data.SavedBrandTemplate
import app.reelify.core.designsystem.Chip
import app.reelify.core.designsystem.CinematicCard
import app.reelify.core.designsystem.Eyebrow
import app.reelify.core.designsystem.GhostButton
import app.reelify.core.designsystem.InfoBanner
import app.reelify.core.designsystem.LabeledSlider
import app.reelify.core.designsystem.PrimaryButton
import app.reelify.core.designsystem.ReelTextField
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.ScreenFrame
import app.reelify.core.designsystem.SecondaryButton
import app.reelify.core.designsystem.StatusPill
import app.reelify.core.library.SoundLibrary
import app.reelify.core.model.BrandFont
import app.reelify.core.model.BrandProfile
import app.reelify.core.model.BrandTemplate
import app.reelify.core.model.BrandingUse
import app.reelify.core.model.CaptionPresetId
import app.reelify.core.model.CaptionSettings
import app.reelify.core.model.CaptionStyle
import app.reelify.core.model.LogoPosition
import app.reelify.core.model.LowerThirdStyle
import app.reelify.core.model.OutroStyle
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.TerminologyEntry
import app.reelify.core.model.Treatment
import app.reelify.core.util.Ids
import app.reelify.feature.common.OverlayPreview
import app.reelify.feature.common.appContainer
import app.reelify.feature.settings.NavRow
import app.reelify.feature.common.strings
import app.reelify.navigation.Routes
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun StudioTab(nav: NavHostController) {
    val c = appContainer()
    val s = strings()
    val brand by c.settings.brand.collectAsStateWithLifecycle(initialValue = BrandProfile())
    val treatments by c.library.observeTreatments().collectAsStateWithLifecycle(initialValue = emptyList())
    val presets by c.settings.captionPresets.collectAsStateWithLifecycle(initialValue = emptyList())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(s.studio, style = MaterialTheme.typography.displaySmall, modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp)) }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                NavRow(Icons.Rounded.Badge, "Branding", if (brand.isConfigured) "${brand.template.label} · ${brand.clinicName.ifBlank { brand.brandName }}" else "Set up logo, colors, CTA and handles") { nav.navigate(Routes.BRANDING) }
            }
        }
        item { Eyebrow("Caption styles", modifier = Modifier.padding(horizontal = 20.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(CaptionPresetId.entries.filter { it != CaptionPresetId.CUSTOM }) { id ->
                    Column(Modifier.width(130.dp)) {
                        OverlayPreview(1300, ReelLanguage.IRAQI_ARABIC, CaptionSettings(style = CaptionStyle.preset(id)), null, BrandingUse(enabled = false))
                        Text(id.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                items(presets) { p ->
                    Column(Modifier.width(130.dp)) {
                        OverlayPreview(1300, ReelLanguage.ENGLISH, CaptionSettings(style = p.style), null, BrandingUse(enabled = false))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            val scope = rememberCoroutineScope()
                            IconButton({ scope.launch { c.settings.deleteCaptionPreset(p.id) } }) { Icon(Icons.Rounded.Delete, null, tint = Rf.TextTertiary) }
                        }
                    }
                }
            }
        }
        item { Text("Custom caption styles you save from the reel preview appear here.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp)) }
        item { Column(Modifier.padding(horizontal = 20.dp)) { NavRow(Icons.Rounded.LibraryMusic, "Music & SFX library", "${SoundLibrary.music.size} original tracks · ${SoundLibrary.sfx.size} effects · import your own") { nav.navigate(Routes.LIBRARY) } } }
        item { Column(Modifier.padding(horizontal = 20.dp)) { NavRow(Icons.Rounded.Spellcheck, "Terminology dictionary", "Preferred spellings for scripts & caption correction") { nav.navigate(Routes.TERMINOLOGY) } } }
        item { Column(Modifier.padding(horizontal = 20.dp)) { NavRow(Icons.Rounded.Translate, app.reelify.feature.common.tr("Iraqi style guide", "دليل اللهجة العراقية"), app.reelify.feature.common.tr("Approve or correct the lines that teach the AI your voice", "وافق أو صحّح الجمل اللي تعلّم الذكاء الاصطناعي أسلوبك")) { nav.navigate(Routes.IRAQI_STYLE) } } }
        item { Column(Modifier.padding(horizontal = 20.dp)) { NavRow(Icons.Rounded.ViewCarousel, app.reelify.feature.common.tr("Templates", "القوالب"), app.reelify.feature.common.tr("Six built-in formats · customise your own copy", "ستة قوالب أساسية · عدّل نسخة خاصة")) { nav.navigate(Routes.TEMPLATES) } } }
        item { Eyebrow("Treatment profiles", modifier = Modifier.padding(horizontal = 20.dp)) }
        items(treatments, key = { it.id }) { t ->
            Column(Modifier.padding(horizontal = 20.dp)) {
                CinematicCard(onClick = { nav.navigate(Routes.treatment(t.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(t.accentColor)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text(t.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                        if (t.needsConfiguration) StatusPill("Needs setup", Rf.Warning)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                val scope = rememberCoroutineScope()
                SecondaryButton("Add custom topic", {
                    scope.launch {
                        val id = "custom-" + Ids.new().take(8)
                        c.library.saveTreatment(Treatment(id, "New topic", builtIn = false, sortOrder = 50, needsConfiguration = true, description = "Describe this topic."))
                        nav.navigate(Routes.treatment(id))
                    }
                }, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private val swatches = listOf(0xFF9D174D, 0xFFE11D48, 0xFFFB7185, 0xFFC8A97E, 0xFFD4AF37, 0xFF0EA5E9, 0xFF14B8A6, 0xFF8B5CF6, 0xFF0F172A, 0xFF1C1917, 0xFFF8FAFC, 0xFF111111)

@Composable
private fun ColorRow(label: String, value: Long, onPick: (Long) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(swatches) { sw ->
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(sw)).border(if (sw == value) 3.dp else 1.dp, if (sw == value) Color.White else Rf.Border, CircleShape).clickable { onPick(sw) })
            }
        }
    }
}

@Composable
fun BrandingScreen(onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val saved by c.settings.brand.collectAsStateWithLifecycle(initialValue = null)
    val templates by c.settings.brandTemplates.collectAsStateWithLifecycle(initialValue = emptyList())
    var b by remember { mutableStateOf<BrandProfile?>(null) }
    LaunchedEffect(saved) { if (b == null && saved != null) b = saved }
    var previewCta by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf(ReelLanguage.IRAQI_ARABIC) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val f = File(c.storage.brandDir(), "logo_${System.currentTimeMillis()}.png")
            runCatching { c.storage.copyInto(f, uri) }.onSuccess { b = b?.copy(logoPath = it.absolutePath) }
        }
    }
    val brand = b ?: return ScreenFrame("Branding", onBack) {}
    fun set(t: (BrandProfile) -> BrandProfile) { b = t(brand) }

    ScreenFrame("Branding", onBack, subtitle = "Reused across every reel", bottomBar = {
        app.reelify.core.designsystem.BottomActionBar {
            PrimaryButton("Save branding", { scope.launch { c.settings.updateBrand { brand }; onBack() } }, Modifier.weight(1f))
        }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverlayPreview(if (previewCta) 28_500 else 2_500, lang, CaptionSettings(enabled = true), brand,
                    BrandingUse(), modifier = Modifier.width(150.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow("Live preview")
                    Chip("Lower third", !previewCta, { previewCta = false })
                    Chip("CTA & outro", previewCta, { previewCta = true })
                    Chip(if (lang == ReelLanguage.ENGLISH) "English" else "عراقي", true, { lang = if (lang == ReelLanguage.ENGLISH) ReelLanguage.IRAQI_ARABIC else ReelLanguage.ENGLISH })
                    Text("Rendered by the same engine as your exports.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Eyebrow("Templates")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BrandTemplate.entries) { t -> Chip(t.label, brand.template == t, { b = BrandProfile.applyTemplate(brand, t) }) }
                items(templates) { t -> Chip("★ " + t.name, false, { b = t.profile.copy(logoPath = brand.logoPath) }) }
            }
            CinematicCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Rf.Surface3), contentAlignment = Alignment.Center) {
                        if (brand.logoPath != null) coil3.compose.AsyncImage(File(brand.logoPath), null, Modifier.fillMaxSize().padding(6.dp))
                        else Icon(Icons.Rounded.Image, null, tint = Rf.TextTertiary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Logo", style = MaterialTheme.typography.titleMedium)
                        Text("Transparent PNG recommended", style = MaterialTheme.typography.bodySmall)
                    }
                    SecondaryButton(if (brand.logoPath == null) "Import" else "Replace", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = Icons.Rounded.Upload)
                }
                if (brand.logoPath != null) GhostButton("Remove logo", { set { it.copy(logoPath = null) } }, color = Rf.Danger)
            }
            ReelTextField(brand.brandName, { v -> set { it.copy(brandName = v) } }, label = "Brand name", singleLine = true)
            ReelTextField(brand.clinicName, { v -> set { it.copy(clinicName = v) } }, label = "Clinic name", singleLine = true)
            ReelTextField(brand.presenterName, { v -> set { it.copy(presenterName = v) } }, label = "Presenter name (lower third)", singleLine = true)
            ReelTextField(brand.professionalTitle, { v -> set { it.copy(professionalTitle = v) } }, label = "Professional title", singleLine = true)
            ReelTextField(brand.instagram, { v -> set { it.copy(instagram = v) } }, label = "Instagram handle", singleLine = true)
            ReelTextField(brand.snapchat, { v -> set { it.copy(snapchat = v) } }, label = "Snapchat handle", singleLine = true)
            ReelTextField(brand.booking, { v -> set { it.copy(booking = v) } }, label = "Booking details (phone / link)", singleLine = true)
            ReelTextField(brand.ctaText, { v -> set { it.copy(ctaText = v) } }, label = "CTA text (English)", singleLine = true, placeholder = "Book your skin evaluation")
            ReelTextField(brand.ctaTextArabic, { v -> set { it.copy(ctaTextArabic = v) } }, label = "CTA text (Arabic)", singleLine = true, placeholder = "احجز تقييم بشرتك")
            CinematicCard {
                ColorRow("Primary color", brand.primaryColor) { v -> set { it.copy(primaryColor = v) } }
                ColorRow("Accent color", brand.secondaryColor) { v -> set { it.copy(secondaryColor = v) } }
                ColorRow("Text color", brand.textColor) { v -> set { it.copy(textColor = v) } }
            }
            CinematicCard {
                Eyebrow("Font"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) { BrandFont.entries.forEach { f -> Chip(f.label, brand.font == f, { set { it.copy(font = f) } }) } }
                Eyebrow("Lower third"); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) { LowerThirdStyle.entries.forEach { f -> Chip(f.label, brand.lowerThird == f, { set { it.copy(lowerThird = f) } }) } }
                Eyebrow("Outro"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) { OutroStyle.entries.forEach { f -> Chip(f.label, brand.outro == f, { set { it.copy(outro = f) } }) } }
                Eyebrow("Logo position"); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) { LogoPosition.entries.forEach { f -> Chip(f.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, brand.logoPosition == f, { set { it.copy(logoPosition = f) } }) } }
                LabeledSlider("Watermark opacity", brand.watermarkOpacity, { v -> set { it.copy(watermarkOpacity = v) } }, range = 0.15f..1f)
            }
            CinematicCard {
                Text("Before/after disclosure", style = MaterialTheme.typography.titleMedium)
                Text("Shown discreetly on sections planned as Before / After.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                ReelTextField(brand.beforeAfterDisclosure, { v -> set { it.copy(beforeAfterDisclosure = v) } })
                Spacer(Modifier.height(8.dp))
                ReelTextField(brand.beforeAfterDisclosureArabic, { v -> set { it.copy(beforeAfterDisclosureArabic = v) } })
            }
            var name by remember { mutableStateOf("") }
            CinematicCard {
                Text("Save as custom template", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ReelTextField(name, { name = it }, placeholder = "Template name", singleLine = true)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Save template", { scope.launch { c.settings.saveBrandTemplate(SavedBrandTemplate(Ids.new(), name.ifBlank { "My template" }, brand)); name = "" } })
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun TreatmentEditorScreen(id: String, onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var t by remember { mutableStateOf<Treatment?>(null) }
    LaunchedEffect(id) { t = c.library.getTreatment(id) }
    val tr = t ?: return ScreenFrame("Treatment", onBack) {}
    fun lines(l: List<String>) = l.joinToString("\n")
    fun parse(s: String) = s.lines().map { it.trim() }.filter { it.isNotEmpty() }
    ScreenFrame(tr.name, onBack, subtitle = "Grounds every AI script for this treatment", bottomBar = {
        app.reelify.core.designsystem.BottomActionBar {
            if (tr.builtIn) SecondaryButton("Reset", { scope.launch { c.library.resetTreatment(tr.id); t = c.library.getTreatment(id) } })
            else SecondaryButton("Delete", { scope.launch { c.library.deleteTreatment(tr.id); onBack() } })
            PrimaryButton("Save profile", { scope.launch { c.library.saveTreatment(tr); onBack() } }, Modifier.weight(1f))
        }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (tr.id == "cleopatra" && !tr.identityConfirmed) {
                InfoBanner(
                    "Identity confirmation needed: the reference URL (cleopatra-lasercenter.com/diamond-glow/) describes DiamondGlow® 3-in-1 dermabrasion, which does not automatically mean your clinic's 'Cleopatra' service is DiamondGlow®.",
                    color = Rf.Warning,
                    icon = Icons.Rounded.WarningAmber,
                )
                SecondaryButton("Confirm Cleopatra = DiamondGlow® (3-in-1)", {
                    scope.launch {
                        t = c.library.confirmCleopatraAsDiamondGlow()
                    }
                })
            } else if (tr.needsConfiguration) {
                InfoBanner("This profile needs your configuration. Until you describe it, the AI stays generic and won't invent mechanisms, indications or results.", color = Rf.Warning, icon = Icons.Rounded.WarningAmber)
            }
            if (tr.manufacturer.isNotBlank() || tr.deviceModel.isNotBlank()) {
                InfoBanner("Verified device: ${listOf(tr.manufacturer, tr.deviceModel, tr.technologyType).filter { it.isNotBlank() }.joinToString(" · ")}", color = Rf.Info)
            }
            var researchReport by remember(tr.id) { mutableStateOf<app.reelify.core.content.ResearchRefreshResult?>(null) }
            GhostButton("Audit verified research & sources", {
                researchReport = app.reelify.core.content.ClinicalKnowledgeManager.auditLocalSources(tr)
            })
            researchReport?.let { rep ->
                InfoBanner("${rep.statusNote}\nSources: ${rep.sourceTitles.joinToString(" · ").ifBlank { "None attached" }}", color = Rf.Info)
            }
            app.reelify.core.designsystem.ToggleRow("Needs configuration", tr.needsConfiguration, { v -> t = tr.copy(needsConfiguration = v) }, subtitle = "Turn off once the facts below are accurate for your clinic")
            ReelTextField(tr.name, { v -> t = tr.copy(name = v) }, label = "Display name", singleLine = true)
            ReelTextField(tr.arabicName, { v -> t = tr.copy(arabicName = v) }, label = "Arabic name", singleLine = true)
            ReelTextField(tr.alternativeNames.joinToString(", "), { v -> t = tr.copy(alternativeNames = v.split(",").map { it.trim() }.filter { it.isNotEmpty() }) }, label = "Alternative names (comma separated)")
            ReelTextField(tr.description, { v -> t = tr.copy(description = v) }, label = "Description", minLines = 3)
            ReelTextField(lines(tr.approvedIndications), { v -> t = tr.copy(approvedIndications = parse(v)) }, label = "Approved indications (one per line)", minLines = 3)
            ReelTextField(lines(tr.talkingPoints), { v -> t = tr.copy(talkingPoints = parse(v)) }, label = "Approved talking points", minLines = 3)
            ReelTextField(lines(tr.precautions), { v -> t = tr.copy(precautions = parse(v)) }, label = "Contraindications / precautions", minLines = 2)
            ReelTextField(lines(tr.preferredTerminology), { v -> t = tr.copy(preferredTerminology = parse(v)) }, label = "Preferred terminology", minLines = 2)
            ReelTextField(lines(tr.preferredHooks), { v -> t = tr.copy(preferredHooks = parse(v)) }, label = "Preferred hooks", minLines = 2)
            ReelTextField(lines(tr.commonQuestions), { v -> t = tr.copy(commonQuestions = parse(v)) }, label = "Common patient questions", minLines = 2)
            ReelTextField(lines(tr.forbiddenClaims), { v -> t = tr.copy(forbiddenClaims = parse(v)) }, label = "Claims NOT to make", minLines = 3)
            ReelTextField(lines(tr.brollSuggestions), { v -> t = tr.copy(brollSuggestions = parse(v)) }, label = "Suitable B-roll", minLines = 2)
            ReelTextField(tr.ctaStyle, { v -> t = tr.copy(ctaStyle = v) }, label = "Preferred CTA style")
            ReelTextField(tr.referenceNotes, { v -> t = tr.copy(referenceNotes = v) }, label = "Reference notes (paste approved material)", minLines = 4)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun TerminologyScreen(onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val terms by c.library.observeTerminology().collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<TerminologyEntry?>(null) }
    ScreenFrame("Terminology", onBack, subtitle = "Preferred spellings & caption corrections", actions = {
        IconButton({ editing = TerminologyEntry(Ids.new(), "") }) { Icon(Icons.Rounded.Add, "Add", tint = Rf.Text) }
    }) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { InfoBanner("Used in AI prompts, as Deepgram keyterms, and to auto-correct common misrecognitions in captions (e.g. “aero lase” → Aerolase / إيروليز).") }
            items(terms, key = { it.id }) { t ->
                CinematicCard(onClick = { editing = t }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.english, style = MaterialTheme.typography.titleMedium)
                            if (t.arabic.isNotBlank()) app.reelify.core.designsystem.ScriptText(t.arabic, style = MaterialTheme.typography.bodyMedium)
                            if (t.misrecognitions.isNotEmpty()) Text("Fixes: " + t.misrecognitions.joinToString(", "), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                        IconButton({ scope.launch { c.library.deleteTerm(t.id) } }) { Icon(Icons.Rounded.Delete, null, tint = Rf.TextTertiary) }
                    }
                }
            }
        }
    }
    editing?.let { e ->
        var en by remember(e.id) { mutableStateOf(e.english) }
        var ar by remember(e.id) { mutableStateOf(e.arabic) }
        var pr by remember(e.id) { mutableStateOf(e.pronunciation) }
        var mis by remember(e.id) { mutableStateOf(e.misrecognitions.joinToString(", ")) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null }, containerColor = Rf.Surface,
            title = { Text(if (e.english.isBlank()) "New term" else "Edit term") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReelTextField(en, { en = it }, label = "English spelling", singleLine = true)
                    ReelTextField(ar, { ar = it }, label = "Arabic spelling", singleLine = true)
                    ReelTextField(pr, { pr = it }, label = "Pronunciation hint", singleLine = true)
                    ReelTextField(mis, { mis = it }, label = "Common misrecognitions (comma separated)")
                }
            },
            confirmButton = { androidx.compose.material3.TextButton({ if (en.isNotBlank()) scope.launch { c.library.saveTerm(e.copy(english = en.trim(), arabic = ar.trim(), pronunciation = pr.trim(), misrecognitions = mis.split(",").map { it.trim() }.filter { it.isNotEmpty() })) }; editing = null }) { Text("Save") } },
            dismissButton = { androidx.compose.material3.TextButton({ editing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SoundLibraryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val player = remember { ExoPlayer.Builder(ctx).build() }
    var playing by remember { mutableStateOf<String?>(null) }
    val imported = remember { mutableStateListOf<File>().apply { addAll(c.storage.musicDir().listFiles()?.sortedBy { it.name }.orEmpty()) } }
    DisposableEffect(Unit) { onDispose { player.release() } }
    fun toggle(id: String, uri: String) {
        if (playing == id) { player.stop(); playing = null; return }
        player.setMediaItem(MediaItem.fromUri(uri)); player.prepare(); player.play(); playing = id
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val name = c.storage.displayName(uri).replace(Regex("[^A-Za-z0-9._-]"), "_")
            runCatching { c.storage.copyInto(File(c.storage.musicDir(), name), uri) }.onSuccess { imported.add(it) }
        }
    }
    ScreenFrame("Music & SFX", onBack, subtitle = "Original, royalty-free (CC0) bundled audio") {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Eyebrow("Music") }
            items(SoundLibrary.music) { t ->
                CinematicCard(onClick = { toggle(t.id, "asset:///${t.assetPath}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (playing == t.id) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Rf.RoseSoft)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(t.title, style = MaterialTheme.typography.titleMedium); Text("${t.mood.label} · ${t.bpm} BPM", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Eyebrow("Your music", modifier = Modifier.weight(1f)); GhostButton("Import", { importer.launch(arrayOf("audio/*")) }, icon = Icons.Rounded.Upload) } }
            if (imported.isEmpty()) item { Text("Imported tracks are copied into Reelify and can be chosen in any reel. Only import music you have the rights to use.", style = MaterialTheme.typography.bodySmall) }
            items(imported) { f ->
                CinematicCard(onClick = { toggle(f.path, android.net.Uri.fromFile(f).toString()) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (playing == f.path) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Rf.RoseSoft)
                        Spacer(Modifier.width(12.dp))
                        Text(f.nameWithoutExtension, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton({ f.delete(); imported.remove(f) }) { Icon(Icons.Rounded.Delete, null, tint = Rf.TextTertiary) }
                    }
                }
            }
            item { Eyebrow("Sound effects") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoundLibrary.sfx.forEach { s -> Chip(s.title, playing == s.id, { toggle(s.id, "asset:///${s.assetPath}") }, leading = Icons.Rounded.GraphicEq) }
                }
            }
        }
    }
}
