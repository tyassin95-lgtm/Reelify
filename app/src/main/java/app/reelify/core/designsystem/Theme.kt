package app.reelify.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.reelify.R

object Rf {
    val Background = Color(0xFF09090D)
    val Surface = Color(0xFF171720)
    val Surface2 = Color(0xFF22212A)
    val Surface3 = Color(0xFF2C2A36)
    val Burgundy = Color(0xFF9D174D)
    val Rose = Color(0xFFE11D48)
    val RoseSoft = Color(0xFFFB7185)
    val Text = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFFA1A1AA)
    val TextTertiary = Color(0xFF71717A)
    val Border = Color(0x1FFFFFFF)
    val BorderStrong = Color(0x33FFFFFF)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Danger = Color(0xFFF87171)
    val Info = Color(0xFF60A5FA)
    val Gold = Color(0xFFD4AF37)

    val PrimaryGradient = Brush.linearGradient(listOf(Rose, Burgundy))
    val SurfaceGradient = Brush.verticalGradient(listOf(Color(0xFF1B1A24), Color(0xFF131219)))
    val HeroScrim = Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC09090D), Background))
    val GlowGradient = Brush.radialGradient(listOf(Color(0x55E11D48), Color.Transparent))
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(R.font.manrope, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))
val Tajawal = FontFamily(Font(R.font.tajawal_regular, FontWeight.Normal), Font(R.font.tajawal_bold, FontWeight.Bold))
val Playfair = FontFamily(Font(R.font.playfair, FontWeight.Normal))

private val base = TextStyle(fontFamily = Manrope, color = Rf.Text)

val ReelifyTypography = Typography(
    displayLarge = base.copy(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 44.sp, letterSpacing = (-1).sp),
    displayMedium = base.copy(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp, letterSpacing = (-0.6).sp),
    displaySmall = base.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineMedium = base.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    headlineSmall = base.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp),
    titleLarge = base.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp),
    titleMedium = base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    bodyLarge = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp, color = Rf.TextSecondary),
    bodySmall = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp, color = Rf.TextSecondary),
    labelLarge = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    labelMedium = base.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    labelSmall = base.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Rf.TextSecondary),
)

private val scheme = darkColorScheme(
    primary = Rf.Rose, onPrimary = Color.White, primaryContainer = Rf.Burgundy, onPrimaryContainer = Color.White,
    secondary = Rf.RoseSoft, onSecondary = Color.Black, tertiary = Rf.Gold,
    background = Rf.Background, onBackground = Rf.Text, surface = Rf.Surface, onSurface = Rf.Text,
    surfaceVariant = Rf.Surface2, onSurfaceVariant = Rf.TextSecondary, surfaceContainer = Rf.Surface,
    surfaceContainerHigh = Rf.Surface2, surfaceContainerHighest = Rf.Surface3, surfaceContainerLow = Color(0xFF111118),
    outline = Rf.BorderStrong, outlineVariant = Rf.Border, error = Rf.Danger, onError = Color.Black,
    inverseSurface = Rf.Text, inverseOnSurface = Rf.Background, scrim = Color(0xCC000000),
)

val ReelifyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ReelifyTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val dark = isSystemInDarkTheme() // Reelify is always cinematic dark.
    MaterialTheme(colorScheme = scheme, typography = ReelifyTypography, shapes = ReelifyShapes, content = content)
}
