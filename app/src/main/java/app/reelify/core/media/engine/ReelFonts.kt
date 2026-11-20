package app.reelify.core.media.engine

import android.content.res.AssetManager
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import app.reelify.core.model.BrandFont
import app.reelify.core.model.CaptionFont

/**
 * Bundled OFL fonts with explicit Arabic fallback chains (Typeface.CustomFallbackBuilder) so that
 * burned-in captions render identically on every device, with proper Arabic shaping.
 */
class ReelFonts(private val assets: AssetManager) {
    private val cache = HashMap<String, Typeface>()

    private fun font(path: String, weight: Int, variable: Boolean): Font =
        Font.Builder(assets, path).setWeight(weight).apply { if (variable) setFontVariationSettings("'wght' $weight") }.build()

    private fun arabicFamily(weight: Int): FontFamily {
        val path = when {
            weight >= 750 -> "fonts/Tajawal-ExtraBold.ttf"
            weight >= 600 -> "fonts/Tajawal-Bold.ttf"
            else -> "fonts/Tajawal-Regular.ttf"
        }
        return FontFamily.Builder(font(path, weight.coerceIn(100, 900), false)).build()
    }

    private fun build(primary: String, variable: Boolean, weight: Int, arabicPrimary: Boolean, elegantArabic: Boolean = false): Typeface =
        cache.getOrPut("$primary/$weight/$arabicPrimary/$elegantArabic") {
            runCatching {
                val latin = FontFamily.Builder(font(primary, weight, variable)).build()
                val arabic = if (elegantArabic) FontFamily.Builder(font("fonts/Amiri-Regular.ttf", 400, false)).build() else arabicFamily(weight)
                val builder = if (arabicPrimary) Typeface.CustomFallbackBuilder(arabic).addCustomFallback(latin)
                else Typeface.CustomFallbackBuilder(latin).addCustomFallback(arabic)
                builder.setSystemFallback("sans-serif").build()
            }.getOrElse { Typeface.create(Typeface.SANS_SERIF, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL) }
        }

    fun caption(font: CaptionFont, arabic: Boolean): Typeface = when (font) {
        CaptionFont.SANS -> build("fonts/Manrope.ttf", true, 600, arabic)
        CaptionFont.SANS_BOLD -> build("fonts/Manrope.ttf", true, 800, arabic)
        CaptionFont.ELEGANT -> build("fonts/PlayfairDisplay.ttf", true, 500, arabic, elegantArabic = true)
        CaptionFont.ROUNDED -> build("fonts/Nunito.ttf", true, 700, arabic)
    }

    fun brand(font: BrandFont, bold: Boolean, arabic: Boolean): Typeface = when (font) {
        BrandFont.MODERN -> build("fonts/Manrope.ttf", true, if (bold) 800 else 500, arabic)
        BrandFont.ELEGANT -> build("fonts/PlayfairDisplay.ttf", true, if (bold) 700 else 400, arabic, elegantArabic = !bold)
        BrandFont.ROUNDED -> build("fonts/Nunito.ttf", true, if (bold) 800 else 500, arabic)
    }
}
