package app.reelify.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Primary call-to-action with the signature rose→burgundy gradient, press scale and haptics. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 56.dp,
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    Box(
        modifier
            .scale(scale)
            .heightIn(min = height)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) Rf.PrimaryGradient else SolidColor(Rf.Surface3))
            .clickable(interactionSource = source, indication = null, enabled = enabled && !loading) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick()
            }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (loading) {
                Spinner(size = 20.dp, color = Color.White)
                Spacer(Modifier.width(10.dp))
            } else if (icon != null) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else Rf.TextTertiary)
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, height: Dp = 52.dp) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press2")
    Box(
        modifier.scale(scale).heightIn(min = height).clip(RoundedCornerShape(16.dp)).background(Rf.Surface2)
            .border(1.dp, Rf.Border, RoundedCornerShape(16.dp))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp).alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Rf.Text, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = Rf.Text)
        }
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Rf.RoseSoft) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = color)
    }
}

/** Elevated card with a subtle gradient surface and hairline border. */
@Composable
fun CinematicCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor by animateColorAsState(if (selected) Rf.Rose else Rf.Border, label = "border")
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Rf.SurfaceGradient)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(22.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Rf.RoseSoft) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) GhostButton(action, onAction)
    }
}

/** Selectable chip used for languages, durations, styles, presets. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    val bg by animateColorAsState(if (selected) Rf.Rose.copy(alpha = 0.16f) else Rf.Surface2, label = "chipbg")
    val border by animateColorAsState(if (selected) Rf.Rose else Rf.Border, label = "chipborder")
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { Icon(leading, null, tint = if (selected) Rf.RoseSoft else Rf.TextSecondary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (selected) Rf.Text else Rf.TextSecondary)
    }
}

@Composable
fun <T> ChipRow(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
        items(options.size) { i -> val o = options[i]; Chip(label(o), o == selected, { onSelect(o) }) }
    }
}

@Composable
fun Spinner(size: Dp = 28.dp, color: Color = Rf.Rose, stroke: Dp = 3.dp) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
    Canvas(Modifier.size(size)) {
        drawArc(color.copy(alpha = 0.15f), 0f, 360f, false, style = Stroke(stroke.toPx()))
        drawArc(color, angle, 100f, false, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
    }
}

/** Animated glowing orb used for AI "thinking" states. */
@Composable
fun ThinkingOrb(modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "r")
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(Rf.Rose.copy(alpha = 0.45f), Color.Transparent), center, r * pulse), r * pulse)
        drawCircle(Brush.sweepGradient(listOf(Rf.Rose, Rf.Burgundy, Rf.Gold.copy(alpha = 0.7f), Rf.Rose), center), r * 0.42f)
        drawArc(Color.White.copy(alpha = 0.7f), rot, 70f, false, topLeft = Offset(center.x - r * 0.62f, center.y - r * 0.62f),
            size = androidx.compose.ui.geometry.Size(r * 1.24f, r * 1.24f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, size: Dp = 160.dp, stroke: Dp = 10.dp, label: String? = null) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(Rf.Surface3, -90f, 360f, false, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
            drawArc(Brush.sweepGradient(listOf(Rf.Burgundy, Rf.Rose, Rf.RoseSoft, Rf.Burgundy)), -90f, 360f * p, false, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
        }
        Text(label ?: "${(p * 100).toInt()}%", style = MaterialTheme.typography.displaySmall)
    }
}

@Composable
fun GlowProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "indet")
    val x by t.animateFloat(-0.3f, 1.1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    val p by animateFloatAsState(progress ?: 0f, tween(300), label = "bar")
    Canvas(modifier.fillMaxWidth().height(6.dp).clip(CircleShape)) {
        drawRect(Rf.Surface3)
        if (progress == null) {
            drawRect(Rf.PrimaryGradient, topLeft = Offset(size.width * x, 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height))
        } else drawRect(Rf.PrimaryGradient, size = androidx.compose.ui.geometry.Size(size.width * p, size.height))
    }
}

@Composable
fun Shimmer(modifier: Modifier = Modifier, corner: Dp = 16.dp) {
    val t = rememberInfiniteTransition(label = "sh")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "sx")
    Box(modifier.clip(RoundedCornerShape(corner)).background(Rf.Surface2)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent),
                start = Offset(size.width * x, 0f), end = Offset(size.width * (x + 0.6f), size.height)))
        }
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null, onDismiss: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Rf.Danger.copy(alpha = 0.12f))
            .border(1.dp, Rf.Danger.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = Rf.Danger)
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium.copy(color = Rf.Text), modifier = Modifier.weight(1f).let { m -> if (onDismiss != null) m.clickable(onClick = onDismiss) else m })
        if (onRetry != null) IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, "Retry", tint = Rf.Text) }
    }
}

@Composable
fun InfoBanner(message: String, modifier: Modifier = Modifier, color: Color = Rf.Info, icon: ImageVector = Icons.Rounded.ErrorOutline) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium.copy(color = Rf.Text))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Rf.Surface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Rf.RoseSoft, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (action != null && onAction != null) { Spacer(Modifier.height(20.dp)); PrimaryButton(action, onAction) }
    }
}

/** Standard edge-to-edge screen frame with a cinematic top bar. */
@Composable
fun ScreenFrame(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(Rf.Background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Rf.Text) }
            } else Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            actions()
        }
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        if (bottomBar != null) bottomBar()
    }
}

@Composable
fun BottomActionBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Rf.Background.copy(alpha = 0.96f), Rf.Background)))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

@Composable
fun StepIndicator(step: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val w by animateFloatAsState(if (i == step) 28f else 10f, label = "step")
            Box(Modifier.height(6.dp).width(w.dp).clip(CircleShape).background(if (i <= step) Rf.Rose else Rf.Surface3))
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, range: ClosedFloatingPointRange<Float> = 0f..1f, valueText: String? = null, onDone: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valueText ?: "${(value * 100).toInt()}", style = MaterialTheme.typography.labelMedium, color = Rf.TextSecondary)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range, onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Rf.Rose, inactiveTrackColor = Rf.Surface3),
        )
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = Rf.Rose, checkedThumbColor = Color.White, uncheckedTrackColor = Rf.Surface3, uncheckedBorderColor = Rf.Border))
    }
}

/** Text field with a premium surface; direction follows content so Arabic is right-aligned automatically. */
@Composable
fun ReelTextField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    label: String? = null,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    Column(modifier) {
        if (label != null) { Text(label, style = MaterialTheme.typography.labelMedium, color = Rf.TextSecondary); Spacer(Modifier.height(6.dp)) }
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = singleLine, minLines = minLines, maxLines = if (singleLine) 1 else maxLines,
            textStyle = textStyle.copy(color = Rf.Text, textDirection = TextDirection.Content, fontFamily = if (value.any { it in '؀'..'ۿ' }) Tajawal else textStyle.fontFamily),
            cursorBrush = SolidColor(Rf.Rose), keyboardOptions = keyboardOptions, visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Rf.Surface2).border(1.dp, Rf.Border, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    if (value.isEmpty()) Text(placeholder, style = textStyle.copy(color = Rf.TextTertiary))
                    inner()
                }
            },
        )
    }
}

/** Auto-direction text for mixed Arabic/English content. */
@Composable
fun ScriptText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge, maxLines: Int = Int.MAX_VALUE) {
    val arabic = text.count { it in '؀'..'ۿ' } > text.length / 4
    Text(
        text, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = style.copy(textDirection = TextDirection.Content, fontFamily = if (arabic) Tajawal else style.fontFamily, textAlign = if (arabic) TextAlign.Right else TextAlign.Start, lineHeight = if (arabic) style.fontSize * 1.65f else style.lineHeight),
    )
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = color,
        modifier = modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun Expandable(expanded: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { content() }
}

@Composable
fun TopGlow(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(260.dp).statusBarsPadding().background(Brush.radialGradient(listOf(Rf.Rose.copy(alpha = 0.22f), Color.Transparent), radius = 700f)))
}
