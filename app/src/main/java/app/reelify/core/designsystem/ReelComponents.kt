package app.reelify.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.reelify.core.model.Treatment
import app.reelify.core.template.MediaRequirement
import app.reelify.core.template.ReelTemplate
import app.reelify.core.template.ShotRecipe
import app.reelify.core.template.TemplateArt

/** Consistent spacing/radius scale for Reelify 2.0 screens. */
object RfDims {
    val gutter = 20.dp
    val cardRadius = 22.dp
    val chipRadius = 14.dp
    val gap = 12.dp
    val minTouch = 48.dp
}

/** iOS-style segmented control (used for language, duration). */
@Composable
fun <T> SegmentedControl(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, sublabel: (@Composable (T) -> String)? = null) {
    val haptic = LocalHapticFeedback.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Rf.Surface).border(1.dp, Rf.Border, RoundedCornerShape(18.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            val sel = o == selected
            val bg by animateColorAsState(if (sel) Rf.Surface3 else Color.Transparent, label = "seg")
            Column(
                Modifier.weight(1f).heightIn(min = RfDims.minTouch).clip(RoundedCornerShape(14.dp)).background(bg)
                    .then(if (sel) Modifier.border(1.dp, Rf.Rose.copy(alpha = 0.6f), RoundedCornerShape(14.dp)) else Modifier)
                    .semantics { this.selected = sel; role = Role.Tab }
                    .clickable { if (!sel) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(o) }.padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(label(o), style = MaterialTheme.typography.titleSmall, color = if (sel) Rf.Text else Rf.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                sublabel?.let { Text(it(o), style = MaterialTheme.typography.labelMedium, color = if (sel) Rf.RoseSoft else Rf.TextTertiary, maxLines = 1) }
            }
        }
    }
}

/** Stage header: "STEP 2 OF 4" eyebrow + title, used at the top of the four main stages. */
@Composable
fun StageHeading(step: Int, title: String, subtitle: String? = null, modifier: Modifier = Modifier, stepLabel: String) {
    Column(modifier.fillMaxWidth().padding(horizontal = RfDims.gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stepLabel)
            Spacer(Modifier.width(10.dp))
            StepIndicator(step - 1, 4)
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.displaySmall)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp)) }
    }
}

// ------------------------------------------------------------------------------------------ templates

/**
 * Code-drawn template thumbnail: three 9:16 mini-frames showing each shot's framing
 * (silhouette size for talking shots, device/beam motif for B-roll) on the template's accent.
 */
@Composable
fun TemplateThumbnail(template: ReelTemplate, modifier: Modifier = Modifier) {
    val accent = Color(template.accent)
    Canvas(modifier) {
        drawRoundRect(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF16141C)), Offset.Zero, Offset(size.width, size.height)), cornerRadius = CornerRadius(18.dp.toPx()))
        // template motif glow
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), Offset(size.width * 0.85f, size.height * 0.1f), size.width * 0.6f), size.width * 0.6f, Offset(size.width * 0.85f, size.height * 0.1f))
        val pad = size.width * 0.06f
        val gap = size.width * 0.035f
        val fw = (size.width - pad * 2 - gap * 2) / 3f
        val fh = (fw * 16f / 9f).coerceAtMost(size.height - pad * 2)
        val top = (size.height - fh) / 2f
        template.shots.sortedBy { it.index }.forEachIndexed { i, shot ->
            val left = pad + i * (fw + gap)
            drawShotFrame(shot, Offset(left, top), Size(fw, fh), accent, template.previewAsset)
        }
    }
}

private fun DrawScope.drawShotFrame(shot: ShotRecipe, topLeft: Offset, sz: Size, accent: Color, art: TemplateArt) {
    val r = CornerRadius(sz.width * 0.12f)
    drawRoundRect(Color(0xFF0C0B10), topLeft, sz, r)
    drawRoundRect(Color.White.copy(alpha = 0.10f), topLeft, sz, r, style = Stroke(1.2f))
    val cx = topLeft.x + sz.width / 2
    if (shot.requiredMedia == MediaRequirement.TALKING_TO_CAMERA) {
        val scale = when (shot.framing) {
            app.reelify.core.template.FramingPreset.CLOSE_UP -> 1.25f
            app.reelify.core.template.FramingPreset.MEDIUM -> 0.8f
            else -> 1f
        }
        val headR = sz.width * 0.17f * scale
        val headY = topLeft.y + sz.height * 0.38f
        drawCircle(Color(0xFFE8D5C8).copy(alpha = 0.92f), headR, Offset(cx, headY))
        drawRoundRect(accent.copy(alpha = 0.85f), Offset(cx - sz.width * 0.34f * scale, headY + headR * 1.15f), Size(sz.width * 0.68f * scale, sz.height), CornerRadius(sz.width * 0.3f))
        // eye-line guide
        drawLine(Color.White.copy(alpha = 0.25f), Offset(topLeft.x + 4f, headY - headR * 0.1f), Offset(topLeft.x + sz.width - 4f, headY - headR * 0.1f), 1f)
    } else {
        // device / B-roll motif: handpiece + beam
        val hx = cx - sz.width * 0.08f
        drawRoundRect(Color(0xFFCBD5E1).copy(alpha = 0.9f), Offset(hx, topLeft.y + sz.height * 0.22f), Size(sz.width * 0.16f, sz.height * 0.42f), CornerRadius(8f))
        drawLine(accent, Offset(cx, topLeft.y + sz.height * 0.64f), Offset(cx, topLeft.y + sz.height * 0.8f), sz.width * 0.05f)
        drawCircle(accent.copy(alpha = 0.35f), sz.width * 0.22f, Offset(cx, topLeft.y + sz.height * 0.82f))
        // mic glyph (voiceover)
        drawCircle(Color.White.copy(alpha = 0.75f), sz.width * 0.07f, Offset(topLeft.x + sz.width * 0.78f, topLeft.y + sz.height * 0.12f))
    }
    // shot-number badge; numbered motif for "three things"
    val badgeR = sz.width * 0.13f
    val badgeC = Offset(topLeft.x + sz.width * 0.2f, topLeft.y + sz.width * 0.2f)
    drawCircle(if (art == TemplateArt.THREE_THINGS || art == TemplateArt.MYTH && shot.index == 1) accent else Color.Black.copy(alpha = 0.55f), badgeR, badgeC)
    // headline bar
    drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(topLeft.x + sz.width * 0.15f, topLeft.y + sz.height * 0.9f - 6f), Size(sz.width * 0.7f, sz.height * 0.03f), CornerRadius(4f))
}

@Composable
fun ReelTemplateCard(
    template: ReelTemplate,
    name: String,
    description: String,
    shotLabels: List<String>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val border by animateColorAsState(if (selected) Rf.Rose else Rf.Border, label = "tplb")
    val haptic = LocalHapticFeedback.current
    Column(
        modifier.clip(RoundedCornerShape(RfDims.cardRadius)).background(Rf.SurfaceGradient)
            .border(if (selected) 1.8.dp else 1.dp, border, RoundedCornerShape(RfDims.cardRadius))
            .semantics { this.selected = selected; role = Role.RadioButton }
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }.padding(12.dp),
    ) {
        Box {
            TemplateThumbnail(template, Modifier.fillMaxWidth().aspectRatio(1.55f))
            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp))
            badge?.let { StatusPill(it, Rf.Gold, Modifier.align(Alignment.TopStart).padding(8.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 34.dp))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            shotLabels.forEachIndexed { i, l ->
                Text("${i + 1} · $l", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = Rf.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Rf.Surface2).padding(horizontal = 6.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
fun TreatmentSelectorCard(treatment: Treatment, title: String, subtitle: String, selected: Boolean, needsSetupLabel: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = Color(treatment.accentColor)
    val border by animateColorAsState(if (selected) Rf.Rose else Rf.Border, label = "trb")
    val haptic = LocalHapticFeedback.current
    Column(
        modifier.heightIn(min = 92.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = if (selected) 0.62f else 0.30f), Rf.Surface)))
            .border(if (selected) 1.8.dp else 1.dp, border, RoundedCornerShape(20.dp))
            .semantics { this.selected = selected; role = Role.RadioButton }
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }.padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.weight(1f))
            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) ScriptText(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        needsSetupLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Rf.Warning, maxLines = 1) }
    }
}

// ------------------------------------------------------------------------------------------ message

@Composable
fun TopicSuggestionCard(title: String, category: String, onClick: () -> Unit, modifier: Modifier = Modifier, usedLabel: String? = null, hook: String? = null, loading: Boolean = false, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(18.dp)).background(Rf.Surface)
            .border(1.dp, Rf.Border, RoundedCornerShape(18.dp)).clickable(enabled = enabled && !loading, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Rf.RoseSoft)
                usedLabel?.let { Spacer(Modifier.width(8.dp)); Text(it, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Rf.TextTertiary) }
            }
            Spacer(Modifier.height(2.dp))
            ScriptText(title, style = MaterialTheme.typography.titleSmall)
            hook?.takeIf { it.isNotBlank() }?.let { ScriptText("“$it”", style = MaterialTheme.typography.bodySmall, maxLines = 2) }
        }
        Spacer(Modifier.width(10.dp))
        if (loading) Spinner(20.dp) else Box(Modifier.size(32.dp).clip(CircleShape).background(Rf.Surface2), contentAlignment = Alignment.Center) {
            Text("›", style = MaterialTheme.typography.titleLarge, color = Rf.TextSecondary)
        }
    }
}

/** Three hook options with their technique label; tap to choose. */
@Composable
fun HookSelector(hooks: List<Pair<String, String>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        hooks.forEachIndexed { i, (style, text) ->
            val sel = i == selected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (sel) Rf.Rose.copy(alpha = 0.12f) else Rf.Surface2)
                    .border(1.dp, if (sel) Rf.Rose else Rf.Border, RoundedCornerShape(16.dp))
                    .semantics { this.selected = sel; role = Role.RadioButton }
                    .clickable { onSelect(i) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (sel) Rf.Rose else Rf.TextTertiary, CircleShape), contentAlignment = Alignment.Center) {
                    if (sel) Box(Modifier.size(10.dp).clip(CircleShape).background(Rf.Rose))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(style.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = if (sel) Rf.RoseSoft else Rf.TextTertiary)
                    ScriptText(text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ script / shots

@Composable
fun ShotBadge(index: Int, modifier: Modifier = Modifier, done: Boolean = false, size: Dp = 34.dp) {
    Box(modifier.size(size).clip(CircleShape).background(if (done) Rf.Success.copy(alpha = 0.18f) else Rf.Rose.copy(alpha = 0.16f)).border(1.dp, if (done) Rf.Success else Rf.Rose.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
        if (done) Icon(Icons.Rounded.CheckCircle, null, tint = Rf.Success, modifier = Modifier.size(size * 0.6f))
        else Text("$index", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Rf.RoseSoft)
    }
}

/** One shot of the script: label, time, dialogue, one-line instruction and two actions. */
@Composable
fun ScriptReviewCard(
    index: Int,
    label: String,
    seconds: Long,
    script: String,
    instruction: String,
    headline: String,
    locked: Boolean,
    busy: Boolean,
    timingNote: String?,
    timingWarn: Boolean,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(RfDims.cardRadius)).background(Rf.SurfaceGradient).border(1.dp, if (locked) Rf.Success.copy(alpha = 0.5f) else Rf.Border, RoundedCornerShape(RfDims.cardRadius)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShotBadge(index)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Rf.RoseSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${seconds}s", style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.size(RfDims.minTouch).clip(CircleShape).clickable(onClick = onToggleLock).semantics { contentDescription = if (locked) "Unlock shot" else "Lock shot" }, contentAlignment = Alignment.Center) {
                Icon(if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, null, tint = if (locked) Rf.Success else Rf.TextTertiary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        if (busy) {
            Shimmer(Modifier.fillMaxWidth().height(22.dp)); Spacer(Modifier.height(6.dp)); Shimmer(Modifier.fillMaxWidth(0.8f).height(22.dp))
        } else {
            ScriptText(script.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp))
        }
        if (headline.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            ScriptText(headline, style = MaterialTheme.typography.labelLarge.copy(color = Rf.Gold), maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Videocam, null, tint = Rf.TextTertiary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(instruction, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
        timingNote?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = if (timingWarn) Rf.Warning else Rf.TextTertiary, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Compact pill action (Edit, Try another, Record, Import...). */
@Composable
fun PillAction(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false) {
    Row(
        modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp))
            .background(if (primary) Rf.PrimaryGradient else androidx.compose.ui.graphics.SolidColor(Rf.Surface2))
            .border(1.dp, if (primary) Color.Transparent else Rf.Border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = if (enabled) Color.White else Rf.TextTertiary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = if (enabled) Color.White else Rf.TextTertiary, maxLines = 1)
    }
}

/** Shot card used in the Film stage. */
@Composable
fun ShotProgressCard(
    index: Int,
    label: String,
    seconds: Long,
    instruction: String,
    script: String,
    done: Boolean,
    current: Boolean,
    statusText: String,
    modifier: Modifier = Modifier,
    thumbnail: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val border by animateColorAsState(when { done -> Rf.Success.copy(alpha = 0.55f); current -> Rf.Rose; else -> Rf.Border }, label = "shotb")
    val elevation by animateFloatAsState(if (current) 1f else 0f, label = "shote")
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(RfDims.cardRadius)).background(if (current) Brush.verticalGradient(listOf(Color(0xFF22121A), Color(0xFF16141C))) else Rf.SurfaceGradient)
        .border((1 + elevation * 0.8f).dp, border, RoundedCornerShape(RfDims.cardRadius)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShotBadge(index, done = done)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Rf.RoseSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${seconds}s · $statusText", style = MaterialTheme.typography.bodySmall, color = if (done) Rf.Success else Rf.TextSecondary, maxLines = 1)
            }
            thumbnail?.let { Box(Modifier.width(44.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(8.dp)).background(Color.Black)) { it() } }
        }
        Spacer(Modifier.height(10.dp))
        Text(instruction, style = MaterialTheme.typography.bodyMedium.copy(color = Rf.Text))
        if (script.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            ScriptText(script, style = MaterialTheme.typography.bodyMedium, maxLines = if (current) 6 else 2)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Full-screen, blocking progress state with optional cancel (AI calls, preparation). */
@Composable
fun ProgressOverlay(message: String, modifier: Modifier = Modifier, detail: String? = null, cancelLabel: String? = null, onCancel: (() -> Unit)? = null) {
    Box(modifier.fillMaxSize().background(Rf.Background.copy(alpha = 0.92f)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp).widthIn(max = 420.dp)) {
            ThinkingOrb(size = 110.dp)
            Spacer(Modifier.height(18.dp))
            Text(message, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
            if (onCancel != null && cancelLabel != null) { Spacer(Modifier.height(18.dp)); SecondaryButton(cancelLabel, onCancel) }
        }
    }
}

/** Empty state with a single action, used for the projects list. */
@Composable
fun EmptyProjectState(title: String, body: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) =
    EmptyState(Icons.Rounded.CheckCircle, title, body, modifier, action, onAction)
