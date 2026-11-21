package app.reelify.feature.export

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reelify.core.designsystem.BottomActionBar
import app.reelify.core.designsystem.Chip
import app.reelify.core.designsystem.CinematicCard
import app.reelify.core.designsystem.ErrorBanner
import app.reelify.core.designsystem.Eyebrow
import app.reelify.core.designsystem.InfoBanner
import app.reelify.core.designsystem.PrimaryButton
import app.reelify.core.designsystem.ProgressRing
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.ScreenFrame
import app.reelify.core.designsystem.SecondaryButton
import app.reelify.core.designsystem.ToggleRow
import app.reelify.core.model.ExportQuality
import app.reelify.core.pipeline.ExportPhase
import app.reelify.feature.common.appContainer
import app.reelify.feature.common.strings
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ExportScreen(pid: String, onBack: () -> Unit, onHome: () -> Unit) {
    val c = appContainer()
    val ctx = LocalContext.current
    val s = strings()
    val state by c.exportManager.state.collectAsStateWithLifecycle()
    val project by remember(pid) { c.projects.observeBundle(pid).map { it?.project } }.collectAsStateWithLifecycle(initialValue = null)
    var quality by remember { mutableStateOf<ExportQuality?>(null) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val p = project ?: return
    val q = quality ?: p.edit.quality
    val mine = state.projectId == pid
    LaunchedEffect(Unit) { if (!state.running) c.exportManager.reset() }

    fun start() {
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        c.exportManager.start(pid, q)
    }
    fun share(file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share reel"))
    }

    ScreenFrame("Export", onBack, subtitle = "MP4 · H.264 · AAC · 9:16", bottomBar = {
        BottomActionBar {
            when {
                mine && state.running -> SecondaryButton(s.cancel, { c.exportManager.cancel() }, Modifier.weight(1f))
                mine && state.phase == ExportPhase.DONE -> {
                    SecondaryButton(s.share, { state.file?.let(::share) }, Modifier.weight(1f), icon = Icons.Rounded.IosShare)
                    PrimaryButton("Done", onHome, Modifier.weight(1f), icon = Icons.Rounded.Home)
                }
                else -> PrimaryButton(if (mine && state.phase == ExportPhase.FAILED) s.retry else "Render reel", ::start, Modifier.weight(1f), icon = if (mine && state.phase == ExportPhase.FAILED) Icons.Rounded.Refresh else Icons.Rounded.RocketLaunch, enabled = !state.running)
            }
        }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (mine && state.phase != ExportPhase.IDLE) {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    ProgressRing(if (state.phase == ExportPhase.DONE) 1f else state.progress / 100f, size = 180.dp,
                        label = when (state.phase) { ExportPhase.DONE -> "✓"; ExportPhase.FAILED -> "!"; ExportPhase.CANCELLED -> "—"; else -> "${state.progress}%" })
                }
                Text(state.message, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                if (state.running) Text("You can leave this screen — export continues in the background.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                state.error?.let { ErrorBanner(it) }
                if (state.checks.isNotEmpty()) CinematicCard {
                    Eyebrow("Output validation")
                    state.checks.forEach { Text(it, style = MaterialTheme.typography.bodyMedium.copy(color = if (it.startsWith("✓")) Rf.Success else Rf.Danger), modifier = Modifier.padding(top = 4.dp)) }
                }
                if (state.phase == ExportPhase.DONE) {
                    state.galleryUri?.let { g ->
                        SecondaryButton("Open in gallery", { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(g, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PhotoLibrary)
                    } ?: InfoBanner("Saved inside Reelify (gallery saving is off in Settings).")
                    SecondaryButton("Export again", ::start, Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh)
                }
            } else {
                CinematicCard {
                    Eyebrow("Quality")
                    ExportQuality.entries.forEach { eq -> Chip(eq.label, eq == q, { quality = eq }, Modifier.padding(top = 8.dp)) }
                    Text("1080 × 1920 · ${q.fps} fps · ${q.bitrate / 1_000_000} Mbps. Exact ${p.duration.seconds}s (±1 frame).", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                CinematicCard {
                    ToggleRow("Burn captions into video", p.edit.captions.burnIn && p.edit.captions.enabled, { v -> c.appScope.launchEdit(c, pid) { e -> e.copy(captions = e.captions.copy(burnIn = v, enabled = e.captions.enabled || v)) } })
                    ToggleRow("Also save .srt subtitles", p.edit.captions.exportSrt, { v -> c.appScope.launchEdit(c, pid) { e -> e.copy(captions = e.captions.copy(exportSrt = v)) } })
                }
                p.export?.let { ex ->
                    CinematicCard {
                        Eyebrow("Last export")
                        Text("%.1fs · %d×%d · %.1f MB".format(ex.durationMs / 1000f, ex.width, ex.height, ex.sizeBytes / 1048576f), style = MaterialTheme.typography.bodyMedium)
                        if (ex.editVersion != p.edit.editVersion) Text("The reel changed since this export — render again to include the changes.", style = MaterialTheme.typography.bodySmall.copy(color = Rf.Warning))
                        ex.path?.let { path -> if (File(path).exists()) SecondaryButton(s.share, { share(File(path)) }, Modifier.padding(top = 8.dp), icon = Icons.Rounded.IosShare) }
                    }
                }
                InfoBanner("Rendering happens entirely on this phone. The output is checked with the Android media stack before it's reported as complete.")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchEdit(c: app.reelify.AppContainer, pid: String, t: (app.reelify.core.model.EditSettings) -> app.reelify.core.model.EditSettings) {
    launch { c.projects.updateEdit(pid, transform = t) }
}
