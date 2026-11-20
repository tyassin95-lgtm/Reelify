package app.reelify

import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.reelify.core.media.engine.OverlayModel
import app.reelify.core.media.engine.ReelFonts
import app.reelify.core.media.engine.ReelOverlayRenderer
import app.reelify.core.media.engine.SectionOverlayInfo
import app.reelify.core.model.BrandingUse
import app.reelify.core.model.CaptionFont
import app.reelify.core.model.CaptionSegment
import app.reelify.core.model.CaptionSettings
import app.reelify.core.model.CaptionWord
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.SafeZone
import app.reelify.core.model.VisualEffects
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the real platform text stack (shaping + bidi) used for burned-in captions. */
@RunWith(AndroidJUnit4::class)
class ArabicRenderingTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val fonts = ReelFonts(ctx.assets)

    @Test fun arabicCaptionLayoutIsRtlAndShaped() {
        val text = "شنو الفرق بين Aerolase والليزر؟"
        val paint = TextPaint().apply { typeface = fonts.caption(CaptionFont.SANS_BOLD, arabic = true); textSize = 56f }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 900).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        assertThat(layout.getParagraphDirection(0)).isEqualTo(Layout.DIR_RIGHT_TO_LEFT)
        // Shaping: contextual Arabic forms are narrower than isolated letters drawn one by one.
        val word = "الفرق"
        val joined = paint.measureText(word)
        val isolated = word.sumOf { paint.measureText(it.toString()).toDouble() }.toFloat()
        assertThat(joined).isLessThan(isolated)
        // Bundled Arabic font covers the glyphs
        assertThat(paint.hasGlyph("ق")).isTrue()
    }

    @Test fun overlayRendersArabicCaptionPixels() {
        val words = "احجز تقييم بشرتك".split(" ").mapIndexed { i, w -> CaptionWord(w, 1000L + i * 300, 1250L + i * 300) }
        val model = OverlayModel(540, 960, ReelLanguage.IRAQI_ARABIC, 30_000, listOf(CaptionSegment("x", 1000, 2200, words)), CaptionSettings(),
            null, BrandingUse(enabled = false), VisualEffects(introTitle = false), listOf(SectionOverlayInfo(1, 0, 10_000, "", true)), SafeZone())
        val r = ReelOverlayRenderer(model, fonts, null)
        val bmp = r.bitmapAt(1400)
        var opaque = 0
        for (y in 0 until bmp.height step 4) for (x in 0 until bmp.width step 4) if (Color.alpha(bmp.getPixel(x, y)) > 128) opaque++
        assertThat(opaque).isGreaterThan(50)
        assertThat(r.captionRenders).isEqualTo(1)
        // nothing drawn outside caption time
        val empty = r.bitmapAt(5000)
        var any = 0
        for (y in 0 until empty.height step 8) for (x in 0 until empty.width step 8) if (Color.alpha(empty.getPixel(x, y)) > 0) any++
        assertThat(any).isEqualTo(0)
    }
}
