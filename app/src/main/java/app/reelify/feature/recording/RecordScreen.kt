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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.platform.LocalConfiguration
import app.reelify.core.camera.BeautyEffectState
import app.reelify.core.designsystem.GhostButton
import app.reelify.core.designsystem.InfoBanner
import app.reelify.core.media.engine.BeautyColorEffect
import app.reelify.core.template.CameraFacing
import app.reelify.core.template.ProductionPlanner
import app.reelify.core.template.TeleprompterPosition
import app.reelify.feature.common.text
import app.reelify.feature.common.tr
import androidx.media3.common.util.UnstableApi

@Composable
fun RecordScreen(pid: String, sid: String, role: String, muted: Boolean, replace: Boolean, onBack: () -> Unit, onAccepted: (clipId: String, templateProject: Boolean) -> Unit) {
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
        voice -> VoiceoverRecorder(b, section, replace, onBack, onAccepted)
        else -> CameraRecorder(b, section, micOk && !muted, muted, replace, onBack, onAccepted)
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

/** Deletes the shot's existing takes of [role] before a replacement is accepted. */
private suspend fun replaceExisting(c: app.reelify.AppContainer, b: ProjectBundle, section: ReelSection, voice: Boolean) {
    val existing = if (voice) b.clipsFor(section.id, ClipRole.VOICEOVER) + b.clipsFor(section.id, ClipRole.PRIMARY).filter { it.kind == app.reelify.core.model.MediaKind.AUDIO }
        else b.clipsFor(section.id, ClipRole.PRIMARY).filter { it.isVisual }
    existing.forEach { c.projects.deleteClip(b.project.id, it.id) }
}

private enum class CamSheet { NONE, BEAUTY, SCRIPT, SETTINGS }

@Composable
private fun CameraRecorder(b: ProjectBundle, section: ReelSection, withAudio: Boolean, brollFootage: Boolean, replace: Boolean, onBack: () -> Unit, onAccepted: (String, Boolean) -> Unit) {
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
    val effectState by controller.effectState.collectAsStateWithLifecycle()
    val effectMessage by controller.effectMessage.collectAsStateWithLifecycle()
    val torch by controller.torch.collectAsStateWithLifecycle()
    val previewView = remember { PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val template = b.project.template
    val setup = template?.let { ProductionPlanner.cameraSetup(it, b.project.templateOverrides, section.index) }
    val recipe = template?.shot(section.index)
    // Clinical integrity: treatment / B-roll footage never gets beauty by default.
    val clinical = section.shotType.isClinical || brollFootage
    var cam by remember { mutableStateOf<CameraDefaults?>(null) }
    LaunchedEffect(settings) {
        if (cam == null) {
            val wantBeauty = setup?.beauty ?: !clinical
            val beauty = when {
                clinical || !wantBeauty -> settings.camera.beauty.copy(enabled = false)
                settings.camera.beauty.enabled -> settings.camera.beauty
                template != null -> BeautySettings.forPreset(template.talkingBeauty)
                else -> settings.camera.beauty
            }
            cam = settings.camera.copy(beauty = beauty, teleprompterSize = setup?.teleprompterPreset?.sizeSp?.takeIf { settings.camera.teleprompterSize == CameraDefaults().teleprompterSize } ?: settings.camera.teleprompterSize)
        }
    }
    val cd = cam ?: return Box(Modifier.fillMaxSize().background(Color.Black))
    var front by remember { mutableStateOf(setup?.let { it.facing == CameraFacing.FRONT } ?: (cd.frontCamera && !clinical)) }
    var showPrompter by remember { mutableStateOf(section.spokenScript.isNotBlank() && !brollFootage && (setup?.teleprompter ?: true)) }
    var prompterPos by remember { mutableStateOf(setup?.teleprompterPreset?.position ?: if (cd.teleprompterTop) TeleprompterPosition.NEAR_LENS else TeleprompterPosition.LOWER) }
    var sheet by remember { mutableStateOf(CamSheet.NONE) }
    var countdown by remember { mutableIntStateOf(0) }
    var exposure by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var prompterRunning by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf(false) }
    var showTip by remember { mutableStateOf(true) }
    val target = b.project.duration.sectionMs
    val roll = rememberRollDegrees()
    val recording = rec is RecState.Recording
    val elapsed = (rec as? RecState.Recording)?.elapsedMs ?: 0L
    val timingEstimator by c.timingState.collectAsStateWithLifecycle()
    val estimatedSpeechMs = remember(section.spokenScript, b.project.language, timingEstimator) {
        timingEstimator.estimatedDurationMs(section.spokenScript, b.project.language)
    }
    var prompterResetNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(front, cd.resolution, cd.fps60) { controller.bind(owner, previewView.surfaceProvider, front, cd.resolution == CaptureResolution.UHD, cd.fps60) }
    LaunchedEffect(cd.beauty) { controller.beautyProcessor.params = LookMath.params(cd.beauty, app.reelify.core.model.ColorGrade(look = app.reelify.core.model.ColorLook.NONE), allowBeauty = !clinical) }
    LaunchedEffect(comparing) { controller.beautyProcessor.bypass = comparing }
    LaunchedEffect(cd.faceGuide) { controller.faceGuideEnabled = cd.faceGuide }
    LaunchedEffect(cd.beauty.enabled) { while (true) { controller.refreshEffectState(cd.beauty.enabled && !clinical); delay(500) } }
    LaunchedEffect(Unit) { delay(5000); showTip = false }
    DisposableEffect(Unit) { onDispose { controller.release() } }
    LaunchedEffect(cam) { cam?.let { v -> c.settings.update { s -> s.copy(camera = v.copy(beauty = if (clinical) s.camera.beauty else v.beauty, frontCamera = if (clinical) s.camera.frontCamera else front, teleprompterTop = prompterPos == TeleprompterPosition.NEAR_LENS)) } } }
    fun persistOverride(t: (app.reelify.core.template.ShotOverride) -> app.reelify.core.template.ShotOverride) {
        if (template != null) scope.launch { c.projects.updateTemplateOverrides(b.project.id) { it.withShot(section.index, t) } }
    }

    fun startWithCountdown() {
        prompterResetNonce++
        prompterRunning = false
        scope.launch {
            for (i in cd.countdownSec downTo 1) { countdown = i; delay(1000) }
            countdown = 0
            controller.startRecording(c.storage.newRecordingFile(b.project.id, section.index), withAudio = withAudio)
            prompterRunning = true
        }
    }

    val shotLabel = tr("Shot ${section.index} of 3", "اللقطة ${section.index} من 3")
    val roleLabel = recipe?.role?.label?.text() ?: section.purpose.label

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // ---------------------------------------------------------------- preview (9:16, letterboxed)
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val tall = maxHeight / maxWidth >= 16f / 9f
            Box(if (tall) Modifier.fillMaxWidth().aspectRatio(9f / 16f) else Modifier.fillMaxHeight().aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)) {
                AndroidView({ previewView }, Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { o -> controller.focusAt(previewView.meteringPointFactory.createPoint(o.x, o.y)) }
                })
                Canvas(Modifier.fillMaxSize()) {
                    if (cd.grid) for (i in 1..2) {
                        drawLine(Color(0x40FFFFFF), Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1.2f)
                        drawLine(Color(0x40FFFFFF), Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1.2f)
                    }
                    val guide = (setup?.framing?.faceGuide ?: if (section.shotType == app.reelify.core.model.ShotType.TALKING_HEAD) listOf(0.28f, 0.17f, 0.44f, 0.29f) else null)
                    if (cd.faceGuide && guide != null && !recording) {
                        val band = setup?.framing?.faceHeight ?: (0.15f..0.45f)
                        val ok = face?.let { abs(it.cx - (guide[0] + guide[2] / 2)) < 0.1f && it.h in band } ?: false
                        drawOval(if (ok) Color(0xB334D399) else Color(0x80FFFFFF), topLeft = Offset(size.width * guide[0], size.height * guide[1]), size = Size(size.width * guide[2], size.height * guide[3]),
                            style = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))))
                    }
                    if (cd.safeZones && !recording) {
                        drawRect(Color(0x22000000), size = Size(size.width, size.height * 0.11f))
                        drawRect(Color(0x22000000), topLeft = Offset(0f, size.height * 0.80f), size = Size(size.width, size.height * 0.20f))
                    }
                }
                if (cd.level && !recording) {
                    val level = abs(roll) < 1.5f
                    Box(Modifier.align(Alignment.Center).width(96.dp).height(2.dp).rotate(-roll).background(if (level) Rf.Success else Color.White.copy(alpha = 0.55f)))
                }
            }
        }

        // ---------------------------------------------------------------- coordinated overlay regions
        val lineH = (cd.teleprompterSize * 1.5f).dp
        CameraOverlayScaffold(
            position = prompterPos,
            desiredPrompterHeight = lineH * (setup?.teleprompterPreset?.visibleLines ?: 3) + 24.dp,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            top = {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!recording) IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back", "رجوع"), tint = Color.White) } else Spacer(Modifier.size(48.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (recording || countdown > 0) RecTimerPill(elapsed, target, recording)
                        else {
                            Text(shotLabel, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                            Text(roleLabel.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Rf.RoseSoft, maxLines = 1)
                        }
                    }
                    if (!recording) {
                        if (caps.hasFront && caps.hasBack) IconButton({ front = !front; persistOverride { it.copy(facing = if (front) CameraFacing.FRONT else CameraFacing.BACK) } }) { Icon(Icons.Rounded.Cameraswitch, tr("Switch camera", "تبديل الكاميرا"), tint = Color.White) }
                        IconButton({ sheet = CamSheet.SETTINGS }) { Icon(Icons.Rounded.MoreHoriz, tr("More settings", "إعدادات أكثر"), tint = Color.White) }
                    } else Spacer(Modifier.size(96.dp))
                }
            },
            prompter = if (showPrompter && section.spokenScript.isNotBlank()) { h ->
                TeleprompterPanel(
                    text = section.spokenScript,
                    sizeSp = cd.teleprompterSize,
                    speed = cd.teleprompterSpeed,
                    opacity = cd.teleprompterOpacity,
                    mirror = cd.teleprompterMirror,
                    running = prompterRunning,
                    onToggle = { prompterRunning = !prompterRunning },
                    height = h,
                    targetDurationMs = target,
                    estimatedSpeechMs = estimatedSpeechMs,
                    mode = cd.teleprompterMode,
                    elapsedRecordingMs = if (recording) elapsed else null,
                    resetKey = prompterResetNonce to section.id,
                )
            } else null,
            chips = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!recording) {
                        val hint = framingHint(face, setup?.framing?.faceGuide != null || section.shotType == app.reelify.core.model.ShotType.TALKING_HEAD, cd.faceGuide)
                        hint?.let { CameraChip(it.first, dot = if (it.second) Rf.Success else Rf.Warning) }
                        if (showTip) (setup?.exposureHint?.text() ?: recipe?.instruction?.text())?.let { CameraChip(it) }
                        beautyChip(effectState, cd.beauty.enabled && !clinical, clinical)?.let { (t, col) -> CameraChip(t, dot = col) }
                        if (cd.beauty.enabled && !clinical && effectState == BeautyEffectState.ACTIVE) {
                            Box(Modifier.clip(CircleShape).background(if (comparing) Rf.Rose else Color.Black.copy(alpha = 0.55f))
                                .pointerInput(Unit) { detectTapGestures(onPress = { comparing = true; tryAwaitRelease(); comparing = false }) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text(if (comparing) tr("Original", "الأصلي") else tr("Hold to compare", "اضغط مطوّل للمقارنة"), style = MaterialTheme.typography.labelMedium, color = Color.White)
                            }
                        }
                    }
                }
            },
            bottom = {
                Column {
                    camError?.let { ErrorBanner(it, Modifier.padding(horizontal = 14.dp, vertical = 4.dp), onDismiss = controller::clearError) }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        if (!recording) {
                            CameraTool(Icons.Rounded.Face, tr("Beauty", "تجميل"), cd.beauty.enabled && !clinical, { sheet = CamSheet.BEAUTY }, enabled = !clinical)
                            CameraTool(Icons.Rounded.Subject, tr("Script", "السكربت"), showPrompter, { sheet = CamSheet.SCRIPT }, enabled = section.spokenScript.isNotBlank())
                        } else {
                            Spacer(Modifier.width(68.dp)); MicLevelBar(((rec as RecState.Recording).amplitude).toFloat()); Spacer(Modifier.width(60.dp))
                        }
                        RecordButton(recording, countdown > 0, elapsed.toFloat() / target) {
                            if (recording) { controller.stopRecording(); prompterRunning = false } else if (countdown == 0) startWithCountdown()
                        }
                        if (!recording) {
                            CameraTool(Icons.Rounded.Tune, tr("Camera", "الكاميرا"), false, { sheet = CamSheet.SETTINGS })
                            if (caps.hasFlash) CameraTool(if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, tr("Light", "الضوء"), torch, { controller.setTorch(!torch) }) else Spacer(Modifier.width(68.dp))
                        } else Spacer(Modifier.width(136.dp))
                    }
                }
            },
        )

        if (countdown > 0) Box(Modifier.fillMaxSize().background(Color(0x55000000)), contentAlignment = Alignment.Center) {
            Text("$countdown", style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp, fontWeight = FontWeight.ExtraBold))
        }

        when (val r = rec) {
            is RecState.Finished -> TakeReview(r.file, r.durationMs, target, r.warning, if (cd.beauty.enabled && !clinical) cd.beauty else null,
                onRetake = { r.file.delete(); controller.resetRecState() },
                onAccept = {
                    scope.launch {
                        val clip = runCatching {
                            if (replace) replaceExisting(c, b, section, voice = false)
                            c.importer.import(b.project.id, section.id, Uri.fromFile(r.file), ImportOptions(ClipRole.PRIMARY, clinical = clinical, beauty = if (clinical) BeautySettings() else cd.beauty, muteOriginal = brollFootage), origin = ClipOrigin.CAMERA, mimeHint = "video/mp4")
                        }
                        clip.onSuccess { onAccepted(it.id, template != null) }.onFailure { controller.resetRecState() }
                    }
                })
            is RecState.Failed -> ErrorBanner(r.message, Modifier.align(Alignment.Center).padding(24.dp), onDismiss = controller::resetRecState)
            else -> {}
        }
    }

    when (sheet) {
        CamSheet.BEAUTY -> ModalBottomSheet({ sheet = CamSheet.NONE }, containerColor = Rf.Surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Beauty", "التجميل"), style = MaterialTheme.typography.headlineSmall)
                val (stateText, stateColor) = beautyChip(effectState, cd.beauty.enabled, clinical, verbose = true) ?: (tr("Off", "مطفي") to Rf.TextTertiary)
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(stateColor)); Spacer(Modifier.width(8.dp)); Text(stateText, style = MaterialTheme.typography.bodyMedium.copy(color = Rf.Text)) }
                effectMessage?.takeIf { effectState == BeautyEffectState.FAILED || effectState == BeautyEffectState.UNSUPPORTED }?.let { InfoBanner(it, color = Rf.Warning) }
                ToggleRow(tr("Beauty for this shot", "التجميل لهاي اللقطة"), cd.beauty.enabled, { v ->
                    cam = cd.copy(beauty = if (v) BeautySettings.forPreset(cd.beauty.preset.takeIf { it != BeautyPreset.CUSTOM } ?: (template?.talkingBeauty ?: BeautyPreset.NATURAL), cd.beauty.intensity) else cd.beauty.copy(enabled = false))
                    persistOverride { it.copy(beauty = v) }
                })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BeautyPreset.entries.filter { it != BeautyPreset.CUSTOM }.forEach { p -> Chip(p.label, cd.beauty.preset == p && cd.beauty.enabled, { cam = cd.copy(beauty = BeautySettings.forPreset(p, cd.beauty.intensity)) }) }
                }
                LabeledSlider(tr("Intensity", "الشدة"), cd.beauty.intensity, { v -> cam = cd.copy(beauty = cd.beauty.copy(intensity = v, enabled = true)) })
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(if (comparing) Rf.Rose else Rf.Surface2)
                    .pointerInput(Unit) { detectTapGestures(onPress = { comparing = true; tryAwaitRelease(); comparing = false }) }, contentAlignment = Alignment.Center) {
                    Text(if (comparing) tr("Showing original", "يعرض الأصلي") else tr("Hold to compare with original", "اضغط مطوّل للمقارنة بالأصلي"), style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
                var fine by remember { mutableStateOf(false) }
                GhostButton(if (fine) tr("Hide fine-tuning", "إخفاء التفاصيل") else tr("Fine-tune", "تعديل دقيق"), { fine = !fine })
                if (fine) BeautyControls(cd.beauty) { nb -> cam = cd.copy(beauty = nb.copy(enabled = true, preset = BeautyPreset.CUSTOM)) }
                Text(tr("Your recording is kept original. The same filter is applied when the reel is rendered and can be changed later.", "التسجيل يبقى أصلي. نفس الفلتر ينطبق وقت المونتاج وتكدر تغيره بعدين."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
            }
        }
        CamSheet.SCRIPT -> ModalBottomSheet({ sheet = CamSheet.NONE }, containerColor = Rf.Surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Teleprompter", "التلقين"), style = MaterialTheme.typography.headlineSmall)
                ToggleRow(tr("Show teleprompter", "إظهار التلقين"), showPrompter, { v -> showPrompter = v; persistOverride { it.copy(teleprompter = v) } })
                Eyebrow(tr("Position", "المكان"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(tr("Near lens", "قرب العدسة"), prompterPos == TeleprompterPosition.NEAR_LENS, { prompterPos = TeleprompterPosition.NEAR_LENS })
                    Chip(tr("Middle", "النص"), prompterPos == TeleprompterPosition.MIDDLE, { prompterPos = TeleprompterPosition.MIDDLE })
                    Chip(tr("Lower", "تحت"), prompterPos == TeleprompterPosition.LOWER, { prompterPos = TeleprompterPosition.LOWER })
                }
                LabeledSlider(tr("Text size", "حجم الخط"), cd.teleprompterSize, { v -> cam = cd.copy(teleprompterSize = v) }, range = 16f..40f, valueText = "${cd.teleprompterSize.toInt()}")
                Eyebrow(tr("Pacing mode", "نمط التمرير"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.reelify.core.model.TeleprompterMode.entries.forEach { m ->
                        Chip(m.label.text(), cd.teleprompterMode == m, { cam = cd.copy(teleprompterMode = m) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0.85f to tr("Slower", "أبطأ"),
                        1.0f to tr("Normal", "طبيعي"),
                        1.15f to tr("Faster", "أسرع"),
                    ).forEach { (preset, lbl) ->
                        Chip("$lbl (${preset}×)", kotlin.math.abs(cd.teleprompterSpeed - preset) < 0.04f, { cam = cd.copy(teleprompterSpeed = preset) })
                    }
                }
                LabeledSlider(tr("Fine scroll pace", "ضبط السرعة الدقيق"), cd.teleprompterSpeed, { v -> cam = cd.copy(teleprompterSpeed = v) }, range = 0.5f..1.8f, valueText = "%.2f×".format(cd.teleprompterSpeed))
                LabeledSlider(tr("Background", "الخلفية"), cd.teleprompterOpacity, { v -> cam = cd.copy(teleprompterOpacity = v) })
                ToggleRow(tr("Mirror text (beam-splitter rigs)", "عكس النص (لأجهزة التلقين)"), cd.teleprompterMirror, { v -> cam = cd.copy(teleprompterMirror = v) })
                Text(tr("Short scripts stay stationary without scrolling away. Longer scripts pace automatically to match your shot duration.", "النص القصير يثبت بدون ما يطير، والنص الطويل يتماشى أوتوماتيكياً ويا مدة اللقطة."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
            }
        }
        CamSheet.SETTINGS -> ModalBottomSheet({ sheet = CamSheet.NONE }, containerColor = Rf.Surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Camera", "الكاميرا"), style = MaterialTheme.typography.headlineSmall)
                Eyebrow(tr("Resolution", "الدقة"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("1080p", cd.resolution == CaptureResolution.FHD, { cam = cd.copy(resolution = CaptureResolution.FHD) })
                    if (caps.supports4k) Chip("4K", cd.resolution == CaptureResolution.UHD, { cam = cd.copy(resolution = CaptureResolution.UHD) })
                }
                if (caps.supports60fps) ToggleRow("60 FPS", cd.fps60, { v -> cam = cd.copy(fps60 = v) }) else Text(tr("60 FPS isn't supported on this camera — recording at 30 FPS.", "هاي الكاميرا ما تدعم 60 إطار — التسجيل 30."), style = MaterialTheme.typography.bodySmall)
                if (caps.exposureRange.last > caps.exposureRange.first) LabeledSlider(tr("Exposure", "الإضاءة"), exposure.toFloat(), { exposure = it.toInt(); controller.setExposure(exposure) },
                    range = caps.exposureRange.first.toFloat()..caps.exposureRange.last.toFloat(), valueText = "%+.1f EV".format(exposure * caps.exposureStep))
                if (caps.maxZoom > 1.05f) LabeledSlider(tr("Zoom", "التقريب"), zoom, { zoom = it; controller.setZoom(it) }, range = 1f..caps.maxZoom.coerceAtMost(5f), valueText = "%.1f×".format(zoom))
                if (caps.awbModes.size > 1) {
                    Eyebrow(tr("White balance", "توازن الأبيض"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(CaptureRequest.CONTROL_AWB_MODE_AUTO to "Auto", CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT to "Daylight", CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT to "Cloudy",
                            CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT to "Fluorescent", CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT to "Warm light")
                            .filter { it.first in caps.awbModes }.forEach { (m, l) -> Chip(l, false, { controller.setWhiteBalance(m) }) }
                    }
                }
                ToggleRow(tr("Grid", "الشبكة"), cd.grid, { v -> cam = cd.copy(grid = v) })
                ToggleRow(tr("Level guide", "مؤشر الاستواء"), cd.level, { v -> cam = cd.copy(level = v) })
                ToggleRow(tr("Safe zones", "المناطق الآمنة"), cd.safeZones, { v -> cam = cd.copy(safeZones = v) })
                ToggleRow(tr("Framing guide", "دليل الكادر"), cd.faceGuide, { v -> cam = cd.copy(faceGuide = v) })
                LabeledSlider(tr("Countdown", "العد التنازلي"), cd.countdownSec.toFloat(), { v -> cam = cd.copy(countdownSec = v.toInt()) }, range = 0f..10f, valueText = "${cd.countdownSec}s")
                Spacer(Modifier.height(12.dp))
            }
        }
        CamSheet.NONE -> {}
    }
}

/** Live framing hint (text, ok) or null when no guidance applies. */
@Composable
private fun framingHint(face: app.reelify.core.camera.FaceInfo?, talking: Boolean, enabled: Boolean): Pair<String, Boolean>? {
    if (!talking || !enabled) return null
    val f = face ?: return tr("Looking for your face…", "ديدور على وجهك…") to false
    return when {
        f.cx < 0.4f -> tr("Move a little to your left", "تحرك شوية لليسار") to false
        f.cx > 0.6f -> tr("Move a little to your right", "تحرك شوية لليمين") to false
        f.h > 0.48f -> tr("Step back slightly", "ارجع لورا شوية") to false
        f.h < 0.14f -> tr("Come a little closer", "تقرب شوية") to false
        f.cy > 0.48f -> tr("Raise the camera to eye level", "ارفع الكاميرا لمستوى عينك") to false
        else -> tr("Framing looks good", "الكادر ممتاز") to true
    }
}

@Composable
private fun beautyChip(state: BeautyEffectState, on: Boolean, clinical: Boolean, verbose: Boolean = false): Pair<String, Color>? = when {
    clinical -> tr("Treatment footage · beauty off", "لقطات علاج · التجميل مطفي") to Rf.Info
    state == BeautyEffectState.FAILED -> tr("Beauty preview failed · applied at render", "معاينة التجميل فشلت · ينطبق بالمونتاج") to Rf.Warning
    state == BeautyEffectState.UNSUPPORTED -> tr("Beauty preview unavailable here · applied at render", "معاينة التجميل غير متاحة · ينطبق بالمونتاج") to Rf.Warning
    !on -> if (verbose) tr("Beauty off for this shot", "التجميل مطفي لهاي اللقطة") to Rf.TextTertiary else null
    state == BeautyEffectState.STARTING -> tr("Starting beauty…", "ديشغّل التجميل…") to Rf.TextTertiary
    state == BeautyEffectState.ACTIVE -> if (verbose) tr("Live beauty active", "التجميل شغّال") to Rf.Success else null
    else -> null
}

@Composable
private fun TakeReview(file: File, durationMs: Long, target: Long, warning: String?, beauty: BeautySettings?, onRetake: () -> Unit, onAccept: () -> Unit) {
    val ctx = LocalContext.current
    var withBeauty by remember { mutableStateOf(beauty != null) }
    val player = remember(withBeauty) {
        ExoPlayer.Builder(ctx).build().apply {
            // Same shader as the final render, so the review honestly shows the selected look.
            if (withBeauty && beauty != null) runCatching { setVideoEffects(listOf(BeautyColorEffect(LookMath.params(beauty, app.reelify.core.model.ColorGrade(look = app.reelify.core.model.ColorLook.NONE))))) }
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file))); repeatMode = Player.REPEAT_MODE_ONE; prepare(); play()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    var accepting by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.runtime.key(player) {
            AndroidView({ PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; this.player = player } }, Modifier.fillMaxSize())
        }
        if (beauty != null) Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(4.dp)) {
            listOf(true to tr("With ${beauty.preset.label}", "مع ${beauty.preset.label}"), false to tr("Original", "الأصلي")).forEach { (v, l) ->
                Text(l, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.clip(CircleShape).background(if (withBeauty == v) Rf.Rose else Color.Transparent).clickable { withBeauty = v }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE09090D)))).navigationBarsPadding().padding(20.dp)) {
            Text(tr("Take: %.1fs of %ds".format(durationMs / 1000f, target / 1000), "المدة: %.1f من %d ثا".format(durationMs / 1000f, target / 1000)), style = MaterialTheme.typography.titleMedium)
            Text(when {
                durationMs > target -> tr("You can choose the best ${target / 1000}s with Trim.", "تكدر تختار أحسن ${target / 1000} ثانية من القص.")
                durationMs < target - 1500 -> tr("Shorter than the shot — record again or add a cutaway.", "أقصر من اللقطة — سجّل مرة ثانية أو ضيف لقطة.")
                else -> tr("Great length.", "المدة ممتازة.")
            }, style = MaterialTheme.typography.bodySmall)
            if (beauty != null) Text(tr("Beauty preview uses the final render filter; the recording itself stays original.", "معاينة التجميل نفس فلتر المونتاج النهائي؛ التسجيل نفسه يبقى أصلي."), style = MaterialTheme.typography.labelMedium, color = Rf.TextTertiary)
            warning?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(color = Rf.Warning)) }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(tr("Retake", "إعادة"), onRetake, Modifier.weight(1f))
                PrimaryButton(tr("Use this take", "استخدم هاي"), { accepting = true; onAccept() }, Modifier.weight(1.4f), loading = accepting)
            }
        }
    }
}

@Composable
private fun VoiceoverRecorder(b: ProjectBundle, section: ReelSection, replace: Boolean, onBack: () -> Unit, onAccepted: (String, Boolean) -> Unit) {
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
            Column { Text(tr("Voice · Shot ${section.index} of 3", "الصوت · اللقطة ${section.index} من 3"), style = MaterialTheme.typography.titleLarge); Text(tr("Quiet room, phone close to your mouth. Read naturally.", "غرفة هادية والتلفون قريب من حلكك. اقرا بشكل طبيعي."), style = MaterialTheme.typography.bodySmall) }
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
                RecordButton(recording, false, elapsed.toFloat() / target) {
                    if (!recording) runCatching { recorder.start(c.storage.newVoiceoverFile(b.project.id, section.index)); recording = true }.onFailure { error = "Couldn't start the microphone: ${it.message}" }
                    else { recording = false; val dur = recorder.stop(); if (dur > 300) done = recorder.file!! to dur else error = "Recording too short." }
                }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(tr("Retake", "إعادة"), { d.first.delete(); done = null; elapsed = 0 }, Modifier.weight(1f))
            PrimaryButton(tr("Use this take", "استخدم هاي"), {
                scope.launch {
                    runCatching {
                        if (replace) replaceExisting(c, b, section, voice = true)
                        c.importer.import(b.project.id, section.id, Uri.fromFile(d.first), ImportOptions(ClipRole.VOICEOVER), ClipOrigin.CAMERA, "audio/mp4")
                    }.onSuccess { onAccepted(it.id, b.project.template != null) }.onFailure { error = it.message }
                }
            }, Modifier.weight(1.4f))
        }
    }
}
