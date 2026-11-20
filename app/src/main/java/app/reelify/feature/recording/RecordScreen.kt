package app.reelify.feature.recording

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.reelify.core.camera.CameraController
import app.reelify.core.camera.RecState
import app.reelify.core.data.CameraDefaults
import app.reelify.core.data.CaptureResolution
import app.reelify.core.designsystem.Chip
import app.reelify.core.designsystem.ErrorBanner
import app.reelify.core.designsystem.Eyebrow
import app.reelify.core.designsystem.LabeledSlider
import app.reelify.core.designsystem.PrimaryButton
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.ScriptText
import app.reelify.core.designsystem.SecondaryButton
import app.reelify.core.designsystem.Tajawal
import app.reelify.core.designsystem.ToggleRow
import app.reelify.core.media.ImportOptions
import app.reelify.core.media.engine.LookMath
import app.reelify.core.model.BeautyPreset
import app.reelify.core.model.BeautySettings
import app.reelify.core.model.ClipOrigin
import app.reelify.core.model.ClipRole
import app.reelify.core.model.ProjectBundle
import app.reelify.core.model.ReelSection
import app.reelify.feature.common.appContainer
import app.reelify.feature.review.BeautyControls
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2

@Composable
fun RecordScreen(pid: String, sid: String, role: String, onBack: () -> Unit, onAccepted: (String) -> Unit) {
    val c = appContainer()
    val ctx = LocalContext.current
    var bundle by remember { mutableStateOf<ProjectBundle?>(null) }
    LaunchedEffect(pid) { bundle = c.projects.getBundle(pid) }
    val voice = role == "VOICEOVER"
    val needed = if (voice) arrayOf(Manifest.permission.RECORD_AUDIO) else arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    var cameraOk by remember { mutableStateOf(voice || granted(Manifest.permission.CAMERA)) }
    var micOk by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        cameraOk = voice || r[Manifest.permission.CAMERA] == true || granted(Manifest.permission.CAMERA)
        micOk = r[Manifest.permission.RECORD_AUDIO] == true || granted(Manifest.permission.RECORD_AUDIO)
        asked = true
    }
    LaunchedEffect(Unit) { if (needed.any { !granted(it) }) launcher.launch(needed) }
    val b = bundle ?: return Box(Modifier.fillMaxSize().background(Color.Black))
    val section = b.section(sid) ?: return

    when {
        !cameraOk || (voice && !micOk) -> PermissionNeeded(asked, voice, onBack) { launcher.launch(needed) }
        voice -> VoiceoverRecorder(b, section, onBack, onAccepted)
        else -> CameraRecorder(b, section, micOk, onBack, onAccepted)
    }
}

@Composable
private fun PermissionNeeded(asked: Boolean, voice: Boolean, onBack: () -> Unit, onRequest: () -> Unit) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(Rf.Background).windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (voice) Icons.Rounded.Mic else Icons.Rounded.CameraFront, null, tint = Rf.RoseSoft, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(if (voice) "Microphone access needed" else "Camera access needed", style = MaterialTheme.typography.headlineSmall)
            Text("Reelify records on this device only. You can still import existing footage without granting access.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 10.dp))
            PrimaryButton("Allow access", onRequest)
            if (asked) SecondaryButton("Open app settings", { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) }, Modifier.padding(top = 10.dp))
            SecondaryButton("Back", onBack, Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun rememberRollDegrees(): Float {
    val ctx = LocalContext.current
    var roll by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { roll = roll * 0.8f + 0.2f * Math.toDegrees(atan2(e.values[0].toDouble(), e.values[1].toDouble())).toFloat() }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensor?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(l) }
    }
    return roll
}

@Composable
private fun CameraRecorder(b: ProjectBundle, section: ReelSection, micOk: Boolean, onBack: () -> Unit, onAccepted: (String) -> Unit) {
    val c = appContainer()
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings by c.settingsState.collectAsStateWithLifecycle()
    val controller = remember { CameraController(ctx) }
    val caps by controller.caps.collectAsStateWithLifecycle()
    val rec by controller.rec.collectAsStateWithLifecycle()
    val face by controller.face.collectAsStateWithLifecycle()
    val camError by controller.errors.collectAsStateWithLifecycle()
    val torch by controller.torch.collectAsStateWithLifecycle()
    val previewView = remember { PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val clinical = section.shotType.isClinical
    var cam by remember { mutableStateOf<CameraDefaults?>(null) }
    LaunchedEffect(settings) { if (cam == null) cam = settings.camera.copy(beauty = if (clinical) settings.camera.beauty.copy(enabled = false) else settings.camera.beauty) }
    val cd = cam ?: return
    var front by remember { mutableStateOf(cd.frontCamera && !clinical) }
    var showPrompter by remember { mutableStateOf(section.spokenScript.isNotBlank()) }
    var showDirection by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBeauty by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var exposure by remember { mutableIntStateOf(0) }
    var prompterRunning by remember { mutableStateOf(false) }
    val target = b.project.duration.sectionMs
    val roll = rememberRollDegrees()

    LaunchedEffect(front, cd.resolution, cd.fps60) {
        controller.bind(owner, previewView.surfaceProvider, front, cd.resolution == CaptureResolution.UHD, cd.fps60)
    }
    LaunchedEffect(cd.beauty) { controller.beautyProcessor.params = LookMath.params(cd.beauty, app.reelify.core.model.ColorGrade(look = app.reelify.core.model.ColorLook.NONE)) }
    LaunchedEffect(cd.faceGuide) { controller.faceGuideEnabled = cd.faceGuide }
    DisposableEffect(Unit) { onDispose { controller.release() } }
    LaunchedEffect(cam) { cam?.let { v -> c.settings.update { s -> s.copy(camera = v.copy(beauty = if (clinical) s.camera.beauty else v.beauty, frontCamera = if (clinical) s.camera.frontCamera else front)) } } }

    fun startWithCountdown() {
        scope.launch {
            for (i in cd.countdownSec downTo 1) { countdown = i; delay(1000) }
            countdown = 0
            controller.startRecording(c.storage.newRecordingFile(b.project.id, section.index), withAudio = micOk)
            prompterRunning = true
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // ---------- camera preview (9:16) with guides
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(9f / 16f).align(Alignment.Center)) {
            AndroidView({ previewView }, Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { o -> controller.focusAt(previewView.meteringPointFactory.createPoint(o.x, o.y)) }
            })
            Canvas(Modifier.fillMaxSize()) {
                if (cd.safeZones) {
                    drawRect(Color(0x55000000), size = Size(size.width, size.height * 0.11f))
                    drawRect(Color(0x55000000), topLeft = Offset(0f, size.height * 0.80f), size = Size(size.width, size.height * 0.20f))
                }
                if (cd.grid) for (i in 1..2) {
                    drawLine(Color(0x55FFFFFF), Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1.5f)
                    drawLine(Color(0x55FFFFFF), Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1.5f)
                }
                if (cd.faceGuide && section.shotType == app.reelify.core.model.ShotType.TALKING_HEAD) {
                    val ok = face?.let { abs(it.cx - 0.5f) < 0.1f && it.cy in 0.25f..0.45f && it.h in 0.15f..0.45f } ?: false
                    drawOval(if (ok) Color(0xAA34D399) else Color(0x88FFFFFF), topLeft = Offset(size.width * 0.27f, size.height * 0.17f), size = Size(size.width * 0.46f, size.height * 0.30f),
                        style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))))
                }
            }
            if (cd.level) {
                val level = abs(roll) < 1.5f
                Box(Modifier.align(Alignment.Center).width(120.dp).height(2.dp).rotate(-roll).background(if (level) Rf.Success else Color.White.copy(alpha = 0.7f)))
            }
            face?.takeIf { cd.faceGuide && section.shotType == app.reelify.core.model.ShotType.TALKING_HEAD && rec !is RecState.Recording }?.let { f ->
                val hint = when {
                    f.cx < 0.4f -> "Move a little to your left"
                    f.cx > 0.6f -> "Move a little to your right"
                    f.h > 0.45f -> "Step back slightly"
                    f.h < 0.15f -> "Come a little closer"
                    f.cy > 0.48f -> "Raise the camera to eye level"
                    else -> "Framing looks good"
                }
                Text(hint, style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp).clip(CircleShape).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }

        // ---------- teleprompter (overlay only — never part of the recording)
        if (showPrompter && section.spokenScript.isNotBlank()) {
            Teleprompter(section.spokenScript, cd, running = prompterRunning, onToggle = { prompterRunning = !prompterRunning },
                modifier = Modifier.align(if (cd.teleprompterTop) Alignment.TopCenter else Alignment.BottomCenter).padding(top = if (cd.teleprompterTop) 96.dp else 0.dp, bottom = if (cd.teleprompterTop) 0.dp else 150.dp))
        }

        // ---------- top bar
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text("SECTION ${section.index} — ${section.purpose.label.uppercase()}", style = MaterialTheme.typography.labelSmall, color = Rf.RoseSoft)
                    TimerText(rec, target)
                }
                if (caps.hasFlash) IconButton({ controller.setTorch(!torch) }) { Icon(if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, "Torch", tint = Color.White) }
                if (caps.hasFront && caps.hasBack && rec !is RecState.Recording) IconButton({ front = !front }) { Icon(Icons.Rounded.CameraFront, "Switch camera", tint = Color.White) }
                IconButton({ showDirection = !showDirection }) { Icon(Icons.Rounded.Info, "Direction", tint = if (showDirection) Rf.RoseSoft else Color.White) }
            }
            AnimatedVisibility(showDirection, enter = fadeIn(), exit = fadeOut()) { DirectionPanel(section) }
            if (cd.beauty.enabled && !clinical) Text("Beauty preview ON · applied non-destructively when rendering", style = MaterialTheme.typography.labelMedium, color = Rf.RoseSoft, modifier = Modifier.padding(start = 12.dp))
            if (clinical) Text("Clinical footage · beauty disabled for honest representation", style = MaterialTheme.typography.labelMedium, color = Rf.Info, modifier = Modifier.padding(start = 12.dp))
            camError?.let { ErrorBanner(it, Modifier.padding(8.dp), onDismiss = controller::clearError) }
        }

        // ---------- exposure
        if (caps.exposureRange.last > caps.exposureRange.first) {
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("☀", color = Color.White)
                androidx.compose.material3.Slider(
                    value = exposure.toFloat(), onValueChange = { exposure = it.toInt(); controller.setExposure(exposure) },
                    valueRange = caps.exposureRange.first.toFloat()..caps.exposureRange.last.toFloat(),
                    modifier = Modifier.width(180.dp).graphicsLayer { rotationZ = -90f }.padding(vertical = 70.dp),
                    colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Rf.Gold, inactiveTrackColor = Color(0x55FFFFFF)),
                )
            }
        }

        // ---------- bottom controls
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 22.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundTool(Icons.Rounded.Subject, "Script", showPrompter) { showPrompter = !showPrompter }
            if (!clinical) RoundTool(Icons.Rounded.Face, "Beauty", cd.beauty.enabled) { showBeauty = true } else Spacer(Modifier.size(56.dp))
            RecordButton(rec is RecState.Recording, countdown > 0) {
                if (rec is RecState.Recording) { controller.stopRecording(); prompterRunning = false } else if (countdown == 0) startWithCountdown()
            }
            RoundTool(Icons.Rounded.Tune, "Camera", showSettings) { showSettings = true }
            if (rec is RecState.Recording) MicLevel((rec as RecState.Recording).amplitude) else Spacer(Modifier.size(56.dp))
        }

        if (countdown > 0) Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
            Text("$countdown", style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp, fontWeight = FontWeight.ExtraBold))
        }

        when (val r = rec) {
            is RecState.Finished -> TakeReview(r.file, r.durationMs, target, r.warning,
                onRetake = { r.file.delete(); controller.resetRecState() },
                onAccept = {
                    scope.launch {
                        val clip = runCatching {
                            c.importer.import(b.project.id, section.id, Uri.fromFile(r.file), ImportOptions(ClipRole.PRIMARY, clinical = clinical, beauty = if (clinical) BeautySettings() else cd.beauty), origin = ClipOrigin.CAMERA, mimeHint = "video/mp4")
                        }
                        clip.onSuccess { onAccepted(it.id) }.onFailure { controller.resetRecState() }
                    }
                })
            is RecState.Failed -> ErrorBanner(r.message, Modifier.align(Alignment.Center).padding(24.dp), onDismiss = controller::resetRecState)
            else -> {}
        }
    }

    if (showBeauty) ModalBottomSheet({ showBeauty = false }, containerColor = Rf.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Beauty", style = MaterialTheme.typography.headlineSmall)
            Text("Subtle, natural enhancement. Your original recording is kept; the same filter is applied in the final render and can be changed later.", style = MaterialTheme.typography.bodySmall)
            ToggleRow("Enable", cd.beauty.enabled, { v -> cam = cd.copy(beauty = if (v) BeautySettings.forPreset(cd.beauty.preset.takeIf { it != BeautyPreset.CUSTOM } ?: BeautyPreset.NATURAL, cd.beauty.intensity) else cd.beauty.copy(enabled = false)) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BeautyPreset.entries.forEach { p -> Chip(p.label, cd.beauty.preset == p && cd.beauty.enabled, { cam = cd.copy(beauty = if (p == BeautyPreset.CUSTOM) cd.beauty.copy(enabled = true, preset = p) else BeautySettings.forPreset(p, cd.beauty.intensity)) }) }
            }
            LabeledSlider("Intensity", cd.beauty.intensity, { v -> cam = cd.copy(beauty = cd.beauty.copy(intensity = v)) })
            BeautyControls(cd.beauty) { nb -> cam = cd.copy(beauty = nb.copy(enabled = true, preset = BeautyPreset.CUSTOM)) }
        }
    }
    if (showSettings) ModalBottomSheet({ showSettings = false }, containerColor = Rf.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Camera", style = MaterialTheme.typography.headlineSmall)
            Eyebrow("Resolution")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("1080p", cd.resolution == CaptureResolution.FHD, { cam = cd.copy(resolution = CaptureResolution.FHD) })
                if (caps.supports4k) Chip("4K", cd.resolution == CaptureResolution.UHD, { cam = cd.copy(resolution = CaptureResolution.UHD) })
            }
            if (caps.supports60fps) ToggleRow("60 FPS", cd.fps60, { v -> cam = cd.copy(fps60 = v) }) else Text("60 FPS isn't supported on this camera — recording at 30 FPS.", style = MaterialTheme.typography.bodySmall)
            if (caps.awbModes.size > 1) {
                Eyebrow("White balance")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CaptureRequest.CONTROL_AWB_MODE_AUTO to "Auto", CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT to "Daylight", CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT to "Cloudy",
                        CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT to "Fluorescent", CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT to "Warm light")
                        .filter { it.first in caps.awbModes }.forEach { (m, l) -> Chip(l, false, { controller.setWhiteBalance(m) }) }
                }
            }
            if (caps.maxZoom > 1.05f) { var z by remember { mutableFloatStateOf(1f) }; LabeledSlider("Zoom", z, { z = it; controller.setZoom(it) }, range = 1f..caps.maxZoom.coerceAtMost(5f), valueText = "%.1f×".format(z)) }
            ToggleRow("Grid", cd.grid, { v -> cam = cd.copy(grid = v) })
            ToggleRow("Level guide", cd.level, { v -> cam = cd.copy(level = v) })
            ToggleRow("Safe zones", cd.safeZones, { v -> cam = cd.copy(safeZones = v) })
            ToggleRow("Face framing guide", cd.faceGuide, { v -> cam = cd.copy(faceGuide = v) })
            LabeledSlider("Countdown", cd.countdownSec.toFloat(), { v -> cam = cd.copy(countdownSec = v.toInt()) }, range = 0f..10f, valueText = "${cd.countdownSec}s")
            Eyebrow("Teleprompter")
            LabeledSlider("Text size", cd.teleprompterSize, { v -> cam = cd.copy(teleprompterSize = v) }, range = 16f..44f, valueText = "${cd.teleprompterSize.toInt()}")
            LabeledSlider("Scroll speed", cd.teleprompterSpeed, { v -> cam = cd.copy(teleprompterSpeed = v) }, range = 0.2f..3f, valueText = "%.1f×".format(cd.teleprompterSpeed))
            LabeledSlider("Background opacity", cd.teleprompterOpacity, { v -> cam = cd.copy(teleprompterOpacity = v) })
            ToggleRow("Mirror text (for beam-splitter rigs)", cd.teleprompterMirror, { v -> cam = cd.copy(teleprompterMirror = v) })
            ToggleRow("Place at top (near lens)", cd.teleprompterTop, { v -> cam = cd.copy(teleprompterTop = v) })
        }
    }
}

@Composable
private fun TimerText(rec: RecState, target: Long) {
    val elapsed = (rec as? RecState.Recording)?.elapsedMs ?: 0L
    val over = elapsed - target
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (rec is RecState.Recording) Box(Modifier.size(10.dp).clip(CircleShape).background(Rf.Rose))
        Spacer(Modifier.width(6.dp))
        Text("%d.%ds / %ds".format(elapsed / 1000, (elapsed % 1000) / 100, target / 1000), style = MaterialTheme.typography.titleMedium, color = if (over > 0) Rf.Warning else Color.White)
        if (over > 0) Text("  +%.1fs — keep going, trim later".format(over / 1000f), style = MaterialTheme.typography.labelMedium, color = Rf.Warning)
    }
}

@Composable
private fun DirectionPanel(section: ReelSection) {
    Column(Modifier.padding(8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xCC101016)).padding(14.dp).heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
        listOf("Camera" to section.cameraDirection, "Framing" to section.framing, "Lighting" to section.lightingTip, "Delivery" to section.deliveryDirection)
            .filter { it.second.isNotBlank() }.forEach { (k, v) ->
                Text(k.uppercase(), style = MaterialTheme.typography.labelSmall, color = Rf.RoseSoft)
                ScriptText(v, style = MaterialTheme.typography.bodyMedium.copy(color = Color.White))
                Spacer(Modifier.height(6.dp))
            }
    }
}

@Composable
private fun Teleprompter(text: String, cd: CameraDefaults, running: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(running, cd.teleprompterSpeed) {
        var last = 0L
        while (running) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1e9f
                    scroll.dispatchRawDeltaCompat(dt * 38f * cd.teleprompterSpeed * (cd.teleprompterSize / 26f))
                }
                last = now
            }
        }
    }
    val arabic = text.count { it in '؀'..'ۿ' } > text.length / 4
    Box(modifier.padding(horizontal = 14.dp).fillMaxWidth().height(170.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = cd.teleprompterOpacity))) {
        Text(
            text, modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 18.dp, vertical = 60.dp).graphicsLayer { scaleX = if (cd.teleprompterMirror) -1f else 1f },
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = cd.teleprompterSize.sp, lineHeight = (cd.teleprompterSize * 1.5f).sp, color = Color.White, fontFamily = if (arabic) Tajawal else null,
                textDirection = androidx.compose.ui.text.style.TextDirection.Content, textAlign = if (arabic) androidx.compose.ui.text.style.TextAlign.Right else androidx.compose.ui.text.style.TextAlign.Center),
        )
        Box(Modifier.fillMaxWidth().height(2.dp).align(Alignment.Center).background(Rf.Rose.copy(alpha = 0.6f)))
        IconButton(onToggle, Modifier.align(Alignment.TopEnd)) { Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Teleprompter", tint = Color.White) }
    }
}

private fun androidx.compose.foundation.ScrollState.dispatchRawDeltaCompat(px: Float) { dispatchRawDelta(px) }

@Composable
private fun RoundTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(if (active) Rf.Rose.copy(alpha = 0.35f) else Color(0x66000000)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, label, tint = Color.White) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun RecordButton(recording: Boolean, counting: Boolean, onClick: () -> Unit) {
    val inner by animateFloatAsState(if (recording) 0.42f else 0.82f, label = "rec")
    Box(Modifier.size(84.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).clickable(enabled = !counting, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size((84 * inner).dp).clip(RoundedCornerShape(if (recording) 10.dp else 50.dp)).background(Rf.Rose))
    }
}

@Composable
private fun MicLevel(amplitude: Double) {
    val level = (amplitude.coerceIn(0.0, 1.0)).toFloat()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(52.dp, 70.dp)) {
        Box(Modifier.width(10.dp).height(44.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x55FFFFFF)), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().height((44 * level).dp).background(if (level > 0.9f) Rf.Danger else Rf.Success))
        }
        Text("MIC", style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun TakeReview(file: File, durationMs: Long, target: Long, warning: String?, onRetake: () -> Unit, onAccept: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(Uri.fromFile(file))); repeatMode = Player.REPEAT_MODE_ONE; prepare(); play() } }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var accepting by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; this.player = player } }, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC09090D)).navigationBarsPadding().padding(20.dp)) {
            Text("Take: %.1fs (section %ds)".format(durationMs / 1000f, target / 1000), style = MaterialTheme.typography.titleMedium)
            Text(if (durationMs > target) "You'll choose the best ${target / 1000}s in the trim step." else if (durationMs < target - 1500) "This take is shorter than the section — you can add B-roll later." else "Great length.", style = MaterialTheme.typography.bodySmall)
            warning?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(color = Rf.Warning)) }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Retake", onRetake, Modifier.weight(1f))
                PrimaryButton("Accept & trim", { accepting = true; onAccept() }, Modifier.weight(1.4f), loading = accepting)
            }
        }
    }
}

@Composable
private fun VoiceoverRecorder(b: ProjectBundle, section: ReelSection, onBack: () -> Unit, onAccepted: (String) -> Unit) {
    val c = appContainer()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var level by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf<Pair<File, Long>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val target = b.project.duration.sectionMs
    DisposableEffect(Unit) { onDispose { recorder.stop() } }
    LaunchedEffect(recording) { while (recording) { elapsed = recorder.elapsedMs(); level = (recorder.amplitude() / 32767f).coerceIn(0f, 1f); delay(60) } }
    Column(Modifier.fillMaxSize().background(Rf.Background).windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Rf.Text) }
            Column { Text("Voiceover · Section ${section.index}", style = MaterialTheme.typography.titleLarge); Text("Speak close to the mic in a quiet room", style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Rf.Surface).padding(20.dp).verticalScroll(rememberScrollState())) {
            ScriptText(section.spokenScript.ifBlank { "No script for this section yet." }, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(16.dp))
        Text("%.1fs / %ds".format(elapsed / 1000f, target / 1000), style = MaterialTheme.typography.displaySmall, color = if (elapsed > target) Rf.Warning else Rf.Text, modifier = Modifier.align(Alignment.CenterHorizontally))
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(8.dp).clip(CircleShape).background(Rf.Surface3)) { Box(Modifier.fillMaxWidth(level).height(8.dp).background(Rf.Success)) }
        error?.let { ErrorBanner(it) }
        val d = done
        if (d == null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RecordButton(recording, false) {
                    if (!recording) runCatching { recorder.start(c.storage.newVoiceoverFile(b.project.id, section.index)); recording = true }.onFailure { error = "Couldn't start the microphone: ${it.message}" }
                    else { recording = false; val dur = recorder.stop(); if (dur > 300) done = recorder.file!! to dur else error = "Recording too short." }
                }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Retake", { d.first.delete(); done = null; elapsed = 0 }, Modifier.weight(1f))
            PrimaryButton("Accept & trim", {
                scope.launch {
                    runCatching { c.importer.import(b.project.id, section.id, Uri.fromFile(d.first), ImportOptions(ClipRole.VOICEOVER), ClipOrigin.CAMERA, "audio/mp4") }
                        .onSuccess { onAccepted(it.id) }.onFailure { error = it.message }
                }
            }, Modifier.weight(1.4f))
        }
    }
}
