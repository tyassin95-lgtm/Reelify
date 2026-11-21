package app.reelify.feature.recording

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.Tajawal

/**
 * Compact reading band. Text starts at the reading line (≈30 % from the top) so the next words
 * are always close to the lens. Auto-scroll is time-based (frame clock), manual drag always works,
 * tapping toggles pause. Mirroring flips the text for beam-splitter rigs. Never recorded.
 */
@Composable
fun TeleprompterPanel(
    text: String,
    sizeSp: Float,
    speed: Float,
    opacity: Float,
    mirror: Boolean,
    running: Boolean,
    onToggle: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(running, speed, sizeSp) {
        var last = 0L
        while (running) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1e9f
                    // ≈ one line every 2.3 s at speed 1 for the default size.
                    scroll.dispatchRawDelta(dt * with(density) { (sizeSp * 1.55f).sp.toPx() } / 2.3f * speed)
                }
                last = now
            }
        }
    }
    val safe = androidx.compose.runtime.remember(text) { app.reelify.core.content.GeneratedContentNormalizer.displaySafe(text) }
    val arabic = app.reelify.core.content.GeneratedContentNormalizer.isArabicDominant(safe)
    val lead = height * 0.30f
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = opacity.coerceIn(0.15f, 0.9f)))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .semantics { contentDescription = "Teleprompter" },
    ) {
        Text(
            safe,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; scaleX = if (mirror) -1f else 1f }
                .drawWithContent {
                    drawContent()
                    // Fade top and bottom edges so the reading band stays calm.
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.18f to Color.Black, 0.78f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                }
                .verticalScroll(scroll)
                .pointerInput(Unit) { detectTapGestures(onTap = { onToggle() }) }
                .padding(start = 20.dp, end = 20.dp, top = lead, bottom = height),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = sizeSp.sp, lineHeight = (sizeSp * 1.5f).sp, color = Color.White,
                fontFamily = if (arabic) Tajawal else MaterialTheme.typography.headlineSmall.fontFamily,
                textDirection = if (arabic) TextDirection.Rtl else TextDirection.Content, textAlign = TextAlign.Center,
            ),
        )
        // reading line
        Box(Modifier.align(Alignment.TopStart).padding(top = lead + (sizeSp * 0.75f).dp).width(4.dp).height((sizeSp * 1.2f).dp).background(Rf.Rose, RoundedCornerShape(2.dp)))
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
            Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (running) "Pause teleprompter" else "Start teleprompter", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** Large record button with a progress ring showing elapsed time against the shot's target length. */
@Composable
fun RecordButton(recording: Boolean, counting: Boolean, progress: Float, size: Dp = 84.dp, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val inner by animateFloatAsState(if (recording) 0.40f else 0.80f, label = "rec")
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), label = "recp")
    Box(
        Modifier.size(size).clip(CircleShape).clickable(enabled = !counting) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
            .semantics { contentDescription = if (recording) "Stop recording" else "Start recording" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            drawCircle(Color.White.copy(alpha = if (recording) 0.25f else 0.95f), radius = size.toPx() / 2 - stroke / 2, style = Stroke(stroke))
            if (recording) drawArc(if (progress >= 1f) Rf.Warning else Rf.Rose, -90f, 360f * p, false, topLeft = Offset(stroke / 2, stroke / 2), size = Size(this.size.width - stroke, this.size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Box(Modifier.size(size * inner).clip(RoundedCornerShape(if (recording) 10.dp else size)).background(Rf.Rose))
    }
}

/** Round tool button with a short label underneath (fixed width so rows never shift). */
@Composable
fun CameraTool(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(50.dp).clip(CircleShape).background(if (active) Rf.Rose.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.10f))
                .border(1.dp, if (active) Rf.Rose.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Small translucent status chip (framing hint, beauty state, exposure tip). */
@Composable
fun CameraChip(text: String, modifier: Modifier = Modifier, dot: Color? = null) {
    Row(
        modifier.widthIn(max = 320.dp).heightIn(min = 30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        dot?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(it)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Recording timer pill: red dot + elapsed / target. */
@Composable
fun RecTimerPill(elapsedMs: Long, targetMs: Long, recording: Boolean) {
    val over = elapsedMs > targetMs
    Row(Modifier.clip(CircleShape).background(if (recording) Rf.Rose.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (recording) { Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White)); Spacer(Modifier.width(6.dp)) }
        Text("%d:%02d / %ds".format(elapsedMs / 60000, (elapsedMs / 1000) % 60, targetMs / 1000), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = if (over && !recording) Rf.Warning else Color.White)
    }
}

/** Vertical mic level meter. */
@Composable
fun MicLevelBar(level: Float, modifier: Modifier = Modifier) {
    Box(modifier.width(8.dp).height(40.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxWidth().height((40 * level.coerceIn(0f, 1f)).dp).background(if (level > 0.92f) Rf.Danger else Rf.Success))
    }
}
