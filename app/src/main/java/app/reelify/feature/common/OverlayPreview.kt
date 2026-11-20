package app.reelify.feature.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.reelify.core.media.engine.OverlayModel
import app.reelify.core.media.engine.ReelOverlayRenderer
import app.reelify.core.media.engine.SectionOverlayInfo
import app.reelify.core.model.BrandProfile
import app.reelify.core.model.BrandingUse
import app.reelify.core.model.CaptionSegment
import app.reelify.core.model.CaptionSettings
import app.reelify.core.model.CaptionWord
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.SafeZone
import app.reelify.core.model.VisualEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun sampleCaption(language: ReelLanguage): CaptionSegment {
    val text = if (language == ReelLanguage.IRAQI_ARABIC) "شنو الفرق بين Aerolase والليزر العادي؟" else "Is laser right for every skin type?"
    val words = text.split(" ").mapIndexed { i, w -> CaptionWord(w, 1000L + i * 300, 1000L + i * 300 + 280, emphasized = w.contains("Aerolase") || w.contains("laser", true)) }
    return CaptionSegment("sample", 1000, 1000 + words.size * 300L + 600, words)
}

/**
 * Renders a frame with the SAME overlay renderer used for export, over a neutral cinematic
 * background, so style choices preview exactly as they will be burned in.
 */
@Composable
fun OverlayPreview(
    timeMs: Long,
    language: ReelLanguage,
    captions: CaptionSettings,
    brand: BrandProfile?,
    branding: BrandingUse = BrandingUse(),
    effects: VisualEffects = VisualEffects(introTitle = false, sectionTitles = false),
    modifier: Modifier = Modifier,
    totalMs: Long = 30_000,
    caption: CaptionSegment? = sampleCaption(language),
    sections: List<SectionOverlayInfo> = listOf(SectionOverlayInfo(1, 0, totalMs / 3, "", true)),
    background: Bitmap? = null,
) {
    val fonts = appContainer().fonts
    var image by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(timeMs, language, captions, brand, branding, effects, caption, background) {
        image = withContext(Dispatchers.Default) {
            val w = 540; val h = 960
            val model = OverlayModel(w, h, language, totalMs, listOfNotNull(caption), captions, brand, branding, effects, sections, SafeZone())
            val logo = brand?.logoPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
            val r = ReelOverlayRenderer(model, fonts, logo)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            if (background != null) c.drawBitmap(background, null, android.graphics.Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            else c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF4A3B3F.toInt(), 0xFF141218.toInt(), Shader.TileMode.CLAMP) })
            c.drawBitmap(r.bitmapAt(timeMs), 0f, 0f, null)
            out
        }
    }
    image?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(18.dp))) }
        ?: androidx.compose.foundation.layout.Box(modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(18.dp)))
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
