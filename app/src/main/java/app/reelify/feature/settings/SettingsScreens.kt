package app.reelify.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.reelify.core.data.CaptureResolution
import app.reelify.core.data.UiLanguage
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
import app.reelify.core.designsystem.StatusPill
import app.reelify.core.designsystem.ToggleRow
import app.reelify.core.model.ExportQuality
import app.reelify.core.model.ReelLanguage
import app.reelify.core.security.AiProvider
import app.reelify.core.security.CredentialSource
import app.reelify.feature.common.appContainer
import app.reelify.feature.common.strings
import app.reelify.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun SettingsTab(nav: NavHostController) {
    val c = appContainer()
    val s = strings()
    val scope = rememberCoroutineScope()
    val settings by c.settingsState.collectAsStateWithLifecycle()
    val creds by c.credentials.statuses.collectAsStateWithLifecycle()
    fun upd(t: (app.reelify.core.data.AppSettings) -> app.reelify.core.data.AppSettings) = scope.launch { c.settings.update(t) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(s.settings, style = MaterialTheme.typography.displaySmall, modifier = Modifier.statusBarsPadding()) }
        item {
            NavRow(Icons.Rounded.Key, s.aiConnections, creds.values.joinToString(" · ") { "${it.provider.label}: ${if (it.configured) "connected" else "not set"}" }) { nav.navigate(Routes.AI_SETTINGS) }
        }
        item {
            CinematicCard {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Language, null, tint = Rf.RoseSoft); Spacer(Modifier.width(10.dp)); Text(s.language, style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(10.dp))
                Eyebrow("Interface language")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    UiLanguage.entries.forEach { l -> Chip(when (l) { UiLanguage.SYSTEM -> "System"; UiLanguage.ENGLISH -> "English"; UiLanguage.ARABIC -> "العربية" }, settings.uiLanguage == l, { upd { it.copy(uiLanguage = l) } }) }
                }
                Eyebrow("Default reel language")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    ReelLanguage.entries.forEach { l -> Chip(l.displayName, settings.defaultReelLanguage == l, { upd { it.copy(defaultReelLanguage = l) } }) }
                }
            }
        }
        item {
            CinematicCard {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.PhotoCamera, null, tint = Rf.RoseSoft); Spacer(Modifier.width(10.dp)); Text("Camera defaults", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CaptureResolution.entries.forEach { r -> Chip(r.label, settings.camera.resolution == r, { upd { it.copy(camera = it.camera.copy(resolution = r)) } }) } }
                ToggleRow("60 FPS when supported", settings.camera.fps60, { v -> upd { it.copy(camera = it.camera.copy(fps60 = v)) } })
                ToggleRow("Grid overlay", settings.camera.grid, { v -> upd { it.copy(camera = it.camera.copy(grid = v)) } })
                ToggleRow("Level / horizon guide", settings.camera.level, { v -> upd { it.copy(camera = it.camera.copy(level = v)) } })
                ToggleRow("Instagram/Snapchat safe zones", settings.camera.safeZones, { v -> upd { it.copy(camera = it.camera.copy(safeZones = v)) } })
                ToggleRow("Face framing guide", settings.camera.faceGuide, { v -> upd { it.copy(camera = it.camera.copy(faceGuide = v)) } })
                LabeledSlider("Countdown", settings.camera.countdownSec.toFloat(), { v -> upd { it.copy(camera = it.camera.copy(countdownSec = v.toInt())) } }, range = 0f..10f, valueText = "${settings.camera.countdownSec}s")
            }
        }
        item {
            CinematicCard {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Videocam, null, tint = Rf.RoseSoft); Spacer(Modifier.width(10.dp)); Text("Export", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(8.dp))
                ExportQuality.entries.forEach { q -> Chip(q.label, settings.exportQuality == q, { upd { it.copy(exportQuality = q) } }, Modifier.padding(vertical = 3.dp)) }
                ToggleRow("Save exports to gallery (Movies/Reelify)", settings.saveToGallery, { v -> upd { it.copy(saveToGallery = v) } })
                ToggleRow("Lower-resolution live preview", settings.proxyPreview, { v -> upd { it.copy(proxyPreview = v) } }, subtitle = "Smoother preview on less powerful phones. Export is always full quality.")
            }
        }
        item {
            CinematicCard {
                Text("Speaking pace (timing estimates)", style = MaterialTheme.typography.titleMedium)
                LabeledSlider("English words / second", settings.englishWordsPerSecond, { v -> upd { it.copy(englishWordsPerSecond = v) } }, range = 1.6f..3.4f, valueText = "%.1f".format(settings.englishWordsPerSecond))
                LabeledSlider("Arabic words / second", settings.arabicWordsPerSecond, { v -> upd { it.copy(arabicWordsPerSecond = v) } }, range = 1.4f..3.0f, valueText = "%.1f".format(settings.arabicWordsPerSecond))
            }
        }
        item { NavRow(Icons.Rounded.Storage, "Storage & cleanup", "Project media, caches and free space") { nav.navigate(Routes.STORAGE) } }
        item { NavRow(Icons.Rounded.Description, "Licenses & privacy", "Fonts, bundled audio, data handling") { nav.navigate(Routes.LICENSES) } }
        item { Text("Reelify ${app.reelify.BuildConfig.VERSION_NAME} · No account, no backend — projects stay on this device.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp)) }
    }
}

@Composable
fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    CinematicCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Rf.RoseSoft)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Rf.TextTertiary)
        }
    }
}

@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val creds by c.credentials.statuses.collectAsStateWithLifecycle()
    val settings by c.settingsState.collectAsStateWithLifecycle()
    val input = remember { mutableStateMapOf<AiProvider, String>() }
    val result = remember { mutableStateMapOf<AiProvider, Pair<Boolean, String>>() }
    val testing = remember { mutableStateMapOf<AiProvider, Boolean>() }
    var model by remember(settings.openAiModel) { mutableStateOf(settings.openAiModel) }
    var dgModel by remember(settings.deepgramModel) { mutableStateOf(settings.deepgramModel) }

    ScreenFrame("AI Connections", onBack, subtitle = "Direct HTTPS to providers · no backend") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoBanner("Keys you enter here are encrypted with an Android Keystore key and override any key embedded at build time. This protects keys at rest, not on a compromised/rooted device. Use dedicated, budget-limited keys.")
            AiProvider.entries.forEach { p ->
                val st = creds.getValue(p)
                CinematicCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Cloud, null, tint = Rf.RoseSoft)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.label, style = MaterialTheme.typography.titleMedium)
                            Text(if (p == AiProvider.OPENAI) "Script, direction & editing guidance" else "Transcription & synchronized captions", style = MaterialTheme.typography.bodySmall)
                        }
                        StatusPill(when (st.source) { CredentialSource.USER -> "In-app key"; CredentialSource.EMBEDDED -> "Embedded key"; CredentialSource.NONE -> "Not set" }, if (st.configured) Rf.Success else Rf.Warning)
                    }
                    if (st.configured) Text("Active key: ${st.masked}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(12.dp))
                    ReelTextField(input[p].orEmpty(), { input[p] = it }, placeholder = if (st.configured) "Replace key…" else "Paste ${p.label} API key", singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Save key", {
                            input[p]?.takeIf { it.isNotBlank() }?.let { c.credentials.setUserKey(p, it); input[p] = ""; result[p] = true to "Key saved securely" }
                        }, enabled = !input[p].isNullOrBlank())
                        SecondaryButton(if (testing[p] == true) "Testing…" else "Test connection", {
                            testing[p] = true
                            scope.launch {
                                val r = runCatching { if (p == AiProvider.OPENAI) c.openAi.testConnection() else c.deepgram.testConnection() }
                                result[p] = r.isSuccess to (r.getOrNull() ?: r.exceptionOrNull()?.message ?: "Failed")
                                testing[p] = false
                            }
                        }, enabled = st.configured && testing[p] != true)
                        if (st.source == CredentialSource.USER) GhostButton("Remove", { c.credentials.removeUserKey(p); result.remove(p) }, color = Rf.Danger)
                    }
                    result[p]?.let { (ok, msg) ->
                        Spacer(Modifier.height(10.dp))
                        if (ok) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.CheckCircle, null, tint = Rf.Success, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(msg, style = MaterialTheme.typography.bodySmall.copy(color = Rf.Success)) }
                        else ErrorBanner(msg)
                    }
                }
            }
            CinematicCard {
                Text("Advanced", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                ReelTextField(model, { model = it }, label = "OpenAI model identifier", singleLine = true)
                Spacer(Modifier.height(10.dp))
                ReelTextField(dgModel, { dgModel = it }, label = "Deepgram model", singleLine = true)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Save models", { scope.launch { c.settings.update { it.copy(openAiModel = model.trim(), deepgramModel = dgModel.trim()) } } }, height = 46.dp)
                    GhostButton("Reset defaults", { model = app.reelify.BuildConfig.DEFAULT_OPENAI_MODEL; dgModel = app.reelify.BuildConfig.DEFAULT_DEEPGRAM_MODEL })
                }
                ToggleRow("Send terminology as Deepgram keyterms", settings.sendKeytermsToDeepgram, { v -> scope.launch { c.settings.update { it.copy(sendKeytermsToDeepgram = v) } } }, subtitle = "Improves recognition of device names (retries without if unsupported).")
            }
        }
    }
}

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<app.reelify.core.storage.StorageUsage?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val projects by c.projects.observeProjects().collectAsStateWithLifecycle(initialValue = emptyList())
    suspend fun refresh() { usage = c.storage.usage() }
    LaunchedEffect(Unit) { refresh() }
    fun mb(b: Long) = "%.1f MB".format(b / 1048576.0)
    ScreenFrame("Storage", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            usage?.let { u ->
                CinematicCard {
                    Text("Project media: ${mb(u.projectsBytes)}", style = MaterialTheme.typography.titleMedium)
                    Text("Render caches: ${mb(u.cacheBytes)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Free on device: ${mb(u.freeBytes)}", style = MaterialTheme.typography.bodyMedium)
                }
                u.perProject.forEach { (id, bytes) ->
                    val title = projects.firstOrNull { it.id == id }?.title ?: "Orphaned files"
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text(mb(bytes), style = MaterialTheme.typography.labelMedium) }
                }
            }
            SecondaryButton("Clear render caches", { scope.launch { c.storage.clearRenderCaches(); message = "Caches cleared"; refresh() } }, icon = Icons.Rounded.DeleteSweep)
            SecondaryButton("Remove unused media files", {
                scope.launch {
                    val referenced = c.projects.allClips().map { it.uri }.toSet() + projects.mapNotNull { it.export?.path }.toSet()
                    val freed = c.storage.cleanupOrphans(referenced, projects.map { it.id }.toSet())
                    message = "Freed ${mb(freed)}"; refresh()
                }
            }, icon = Icons.Rounded.DeleteSweep)
            message?.let { InfoBanner(it, color = Rf.Success) }
            Text("Recordings are never deleted while a project references them. Exports saved to the gallery are unaffected.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val files = remember { ctx.assets.list("licenses")?.sorted().orEmpty() }
    var open by remember { mutableStateOf<String?>(null) }
    ScreenFrame("Licenses & privacy", onBack) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                InfoBanner("Privacy: scripts and directions are generated from TEXT you provide. Patient footage is never sent to OpenAI. For captions, only the extracted dialogue audio of the reel is uploaded to Deepgram when you run automatic editing with Deepgram connected.")
            }
            items(files) { f ->
                CinematicCard(onClick = { open = if (open == f) null else f }) {
                    Text(f.removeSuffix(".txt"), style = MaterialTheme.typography.titleSmall)
                    if (open == f) Text(remember(f) { ctx.assets.open("licenses/$f").bufferedReader().readText() }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
