package app.reelify.core.media.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import androidx.media3.effect.BitmapOverlay
import app.reelify.core.model.BrandProfile
import app.reelify.core.model.BrandingUse
import app.reelify.core.model.CaptionAlign
import app.reelify.core.model.CaptionAnimation
import app.reelify.core.model.CaptionSegment
import app.reelify.core.model.CaptionSettings
import app.reelify.core.model.LogoPosition
import app.reelify.core.model.LowerThirdStyle
import app.reelify.core.model.OutroStyle
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.SafeZone
import app.reelify.core.model.VisualEffects
import kotlin.math.max
import kotlin.math.min

data class SectionOverlayInfo(val index: Int, val startMs: Long, val durationMs: Long, val onScreenText: String, val talkingHead: Boolean)

/** Everything the overlay layer needs; immutable so preview and export draw identical frames. */
data class OverlayModel(
    val width: Int,
    val height: Int,
    val language: ReelLanguage,
    val totalMs: Long,
    val captions: List<CaptionSegment>,
    val captionSettings: CaptionSettings,
    val brand: BrandProfile?,
    val branding: BrandingUse,
    val effects: VisualEffects,
    val sections: List<SectionOverlayInfo>,
    val safe: SafeZone,
    val clinicalRanges: List<LongRange> = emptyList(),
    val beforeAfterRanges: List<LongRange> = emptyList(),
)

/**
 * Draws captions and branding for a timestamp into a reusable full-frame ARGB bitmap.
 * Rendering only happens when the visual state changes (word change, animation frame), so the
 * texture upload cost is small.
 */
class ReelOverlayRenderer(private val m: OverlayModel, private val fonts: ReelFonts, private val logo: Bitmap?) {
    private val bmp: Bitmap = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bmp)
    private var lastKey: String? = null
    private val s = m.width / 1080f
    private val arabic = m.language == ReelLanguage.IRAQI_ARABIC
    private val captionStyle = m.captionSettings.style
    private val sortedCaptions = m.captions.sortedBy { it.startMs }

    /** Counters proving that captions / branding were actually composited (used by export validation). */
    @Volatile var captionRenders = 0L; private set
    @Volatile var overlayRenders = 0L; private set

    // ---------------------------------------------------------------- timing helpers
    private fun ctaWindow(): LongRange? {
        val b = m.brand ?: return null
        if (!m.branding.enabled || !m.branding.ctaOverlay) return null
        val text = ctaText(b)
        if (text.isBlank() && b.instagram.isBlank() && b.booking.isBlank()) return null
        val d = m.branding.ctaDurationMs.coerceIn(2000, m.totalMs / 3)
        return (m.totalMs - d) until m.totalMs
    }

    private fun ctaText(b: BrandProfile) = if (arabic) b.ctaTextArabic.ifBlank { b.ctaText } else b.ctaText.ifBlank { b.ctaTextArabic }

    private fun phase(t: Long, start: Long, end: Long, inMs: Long = 350, outMs: Long = 300): Float {
        if (t < start || t >= end) return 0f
        val a = ((t - start).toFloat() / inMs).coerceIn(0f, 1f)
        val b = ((end - t).toFloat() / outMs).coerceIn(0f, 1f)
        return ease(min(a, b))
    }

    private fun ease(x: Float) = x * x * (3 - 2 * x)
    private fun q(f: Float) = (f * 30).toInt() // animation quantization (≈ frame)

    // ---------------------------------------------------------------- public

    fun bitmapAt(timeMs: Long): Bitmap {
        val key = stateKey(timeMs)
        if (key != lastKey) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            draw(canvas, timeMs)
            lastKey = key
            if (currentSegment(timeMs) != null) captionRenders++
            if (lowerThirdPhase(timeMs) > 0f || introPhase(timeMs) > 0f || watermarkVisible(timeMs) || ctaWindow()?.contains(timeMs) == true) overlayRenders++
        }
        return bmp
    }

    /** Draw directly (used for thumbnails/tests). */
    fun draw(c: Canvas, t: Long) {
        drawWatermark(c, t)
        drawBeforeAfterDisclosure(c, t)
        drawIntroTitle(c, t)
        drawSectionTitles(c, t)
        drawLowerThird(c, t)
        drawCta(c, t)
        drawCaptions(c, t)
    }

    private fun currentSegment(t: Long): CaptionSegment? {
        if (!m.captionSettings.enabled || !m.captionSettings.burnIn) return null
        val tt = t - captionStyle.timingOffsetMs
        return sortedCaptions.lastOrNull { tt >= it.startMs && tt < it.endMs && it.words.isNotEmpty() }
    }

    private fun stateKey(t: Long): String = buildString {
        val seg = currentSegment(t)
        if (seg != null) {
            val tt = t - captionStyle.timingOffsetMs
            append("c:").append(seg.id).append(':').append(seg.words.indexOfLast { tt >= it.startMs })
            if (captionStyle.animation == CaptionAnimation.POP || captionStyle.animation == CaptionAnimation.FADE) {
                append(':').append(q(((tt - seg.startMs) / 180f).coerceIn(0f, 1f)))
                append(':').append(q(((seg.endMs - tt) / 150f).coerceIn(0f, 1f)))
            }
        }
        append("|i").append(q(introPhase(t)))
        m.sections.forEach { append("|s").append(q(sectionTitlePhase(it, t))) }
        append("|l").append(q(lowerThirdPhase(t)))
        val cta = ctaWindow()
        if (cta != null) append("|cta").append(q(phase(t, cta.first, cta.last + 1, 450, 1))).append(q(logoPhase(t, cta)))
        append("|w").append(watermarkVisible(t))
        append("|ba").append(m.beforeAfterRanges.any { t in it })
    }

    // ---------------------------------------------------------------- text helpers

    private fun layout(text: CharSequence, paint: TextPaint, width: Int, align: Layout.Alignment, spacing: Float = 1.08f): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(10))
            .setAlignment(align)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()

    private fun paint(tf: Typeface, sizePx: Float, color: Int): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = tf; textSize = sizePx; this.color = color
    }

    private fun Long.argb(): Int = this.toInt()

    private fun withAlpha(color: Int, a: Float): Int = Color.argb((Color.alpha(color) * a).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private val contentLeft get() = m.width * m.safe.left
    private val contentRight get() = m.width * (1 - m.safe.right)
    private val contentWidth get() = (contentRight - contentLeft).toInt()

    // ---------------------------------------------------------------- captions

    private fun drawCaptions(c: Canvas, t: Long) {
        val seg = currentSegment(t) ?: return
        val st = captionStyle
        val tt = t - st.timingOffsetMs
        val sb = SpannableStringBuilder()
        val ranges = ArrayList<IntRange>()
        seg.words.forEachIndexed { i, w ->
            if (i > 0) sb.append(' ')
            val start = sb.length
            sb.append(if (st.uppercase && !arabic) w.text.uppercase() else w.text)
            ranges += start until sb.length
        }
        val active = seg.words.indexOfLast { tt >= it.startMs }
        val highlight = st.highlightColor.argb()
        seg.words.forEachIndexed { i, w ->
            val r = ranges[i]
            val colored = when (st.animation) {
                CaptionAnimation.WORD_HIGHLIGHT -> (i == active && !st.highlightIsBackground)
                CaptionAnimation.KARAOKE -> i <= active
                else -> false
            } || (w.emphasized && !(st.highlightIsBackground && i == active))
            if (colored) sb.setSpan(ForegroundColorSpan(highlight), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val tf = fonts.caption(st.font, arabic)
        val size = st.sizeSp * s
        val fill = paint(tf, size, st.textColor.argb()).apply {
            letterSpacing = if (arabic) 0f else st.letterSpacing
            if (st.shadow) setShadowLayer(10f * s, 0f, 3f * s, Color.argb(150, 0, 0, 0))
        }
        val maxW = (contentWidth * 0.94f).toInt()
        val align = when (st.align) {
            CaptionAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            CaptionAlign.START -> Layout.Alignment.ALIGN_NORMAL
            CaptionAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val lay = layout(sb, fill, maxW, align)
        val cta = ctaWindow()
        val centerY = if (cta != null && t in cta) m.height * min(st.positionY, 0.56f) else m.height * st.positionY
        val top = (centerY - lay.height / 2f).coerceIn(m.height * m.safe.top, m.height * (1 - m.safe.bottom) - lay.height)
        val left = contentLeft + (contentWidth - maxW) / 2f

        var alpha = 1f
        var scale = 1f
        when (st.animation) {
            CaptionAnimation.FADE -> alpha = min(((tt - seg.startMs) / 180f).coerceIn(0f, 1f), ((seg.endMs - tt) / 150f).coerceIn(0f, 1f))
            CaptionAnimation.POP -> scale = 0.86f + 0.14f * ease(((tt - seg.startMs) / 180f).coerceIn(0f, 1f))
            else -> {}
        }
        c.save()
        c.translate(left, top)
        c.scale(scale, scale, maxW / 2f, lay.height / 2f)
        val layerAlpha = (alpha * 255).toInt()
        if (layerAlpha < 255) c.saveLayerAlpha(RectF(-50f, -50f, maxW + 50f, lay.height + 50f), layerAlpha)

        if (st.backgroundEnabled) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.backgroundColor.argb() }
            var minL = Float.MAX_VALUE; var maxR = 0f
            for (line in 0 until lay.lineCount) { minL = min(minL, lay.getLineLeft(line)); maxR = max(maxR, lay.getLineRight(line)) }
            val pad = 22f * s
            c.drawRoundRect(RectF(minL - pad, -pad * 0.7f, maxR + pad, lay.height + pad * 0.7f), 22f * s, 22f * s, bg)
        }
        // Active-word highlight pill (works for RTL/LTR because it uses the layout's selection path).
        if (st.animation == CaptionAnimation.WORD_HIGHLIGHT && st.highlightIsBackground && active >= 0) {
            val r = ranges[active]
            val path = Path(); lay.getSelectionPath(r.first, r.last + 1, path)
            val b = RectF(); path.computeBounds(b, true)
            val padX = 10f * s; val padY = 4f * s
            c.drawRoundRect(RectF(b.left - padX, b.top - padY, b.right + padX, b.bottom + padY), 14f * s, 14f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = highlight })
        }
        if (st.strokeWidth > 0f) {
            val strokePaint = paint(tf, size, st.strokeColor.argb()).apply {
                style = Paint.Style.STROKE; strokeWidth = st.strokeWidth * s; strokeJoin = Paint.Join.ROUND
                letterSpacing = fill.letterSpacing
            }
            layout(sb.toString(), strokePaint, maxW, align).draw(c)
        }
        lay.draw(c)
        if (layerAlpha < 255) c.restore()
        c.restore()
    }

    // ---------------------------------------------------------------- titles

    private fun introPhase(t: Long): Float {
        if (!m.effects.introTitle) return 0f
        val s1 = m.sections.firstOrNull { it.index == 1 } ?: return 0f
        if (s1.onScreenText.isBlank()) return 0f
        return phase(t, 250, 2900, 450, 400)
    }

    private fun drawIntroTitle(c: Canvas, t: Long) {
        val p = introPhase(t); if (p <= 0f) return
        val text = m.sections.first { it.index == 1 }.onScreenText
        val b = m.brand
        val tf = fonts.brand(b?.font ?: app.reelify.core.model.BrandFont.MODERN, true, arabic)
        val paint = paint(tf, 74f * s, withAlpha(Color.WHITE, p)).apply { setShadowLayer(18f * s, 0f, 4f * s, Color.argb((160 * p).toInt(), 0, 0, 0)) }
        val lay = layout(text, paint, (contentWidth * 0.9f).toInt(), Layout.Alignment.ALIGN_CENTER, 1.02f)
        val y = m.height * (m.safe.top + 0.08f) + (1 - p) * 40f * s
        c.save(); c.translate(contentLeft + contentWidth * 0.05f, y)
        // accent line
        val accent = (b?.secondaryColor ?: 0xFFE11D48).argb()
        val lineW = 120f * s * p
        c.drawRoundRect(RectF(contentWidth * 0.45f - lineW / 2, -26f * s, contentWidth * 0.45f + lineW / 2, -18f * s), 4f * s, 4f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, p) })
        lay.draw(c); c.restore()
    }

    private fun sectionTitlePhase(sec: SectionOverlayInfo, t: Long): Float {
        if (!m.effects.sectionTitles || sec.index == 1 || sec.onScreenText.isBlank()) return 0f
        return phase(t, sec.startMs + 200, sec.startMs + min(2600, sec.durationMs - 300), 350, 350)
    }

    private fun drawSectionTitles(c: Canvas, t: Long) {
        for (sec in m.sections) {
            val p = sectionTitlePhase(sec, t); if (p <= 0f) continue
            val b = m.brand
            val tf = fonts.brand(b?.font ?: app.reelify.core.model.BrandFont.MODERN, true, arabic)
            val paint = paint(tf, 50f * s, withAlpha(Color.WHITE, p))
            val lay = layout(sec.onScreenText, paint, (contentWidth * 0.8f).toInt(), Layout.Alignment.ALIGN_CENTER)
            var minL = Float.MAX_VALUE; var maxR = 0f
            for (l in 0 until lay.lineCount) { minL = min(minL, lay.getLineLeft(l)); maxR = max(maxR, lay.getLineRight(l)) }
            val x = contentLeft + contentWidth * 0.1f
            val y = m.height * (m.safe.top + 0.06f) - (1 - p) * 24f * s
            val pad = 26f * s
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha((b?.primaryColor ?: 0xFF9D174D).argb(), 0.88f * p) }
            c.save(); c.translate(x, y)
            c.drawRoundRect(RectF(minL - pad, -pad * 0.55f, maxR + pad, lay.height + pad * 0.55f), 999f, 999f, bg)
            lay.draw(c); c.restore()
        }
    }

    // ---------------------------------------------------------------- branding

    private fun lowerThirdPhase(t: Long): Float {
        val b = m.brand ?: return 0f
        if (!m.branding.enabled || !m.branding.lowerThird || b.presenterName.isBlank()) return 0f
        val sec = m.sections.firstOrNull { it.talkingHead } ?: return 0f
        val start = sec.startMs + if (sec.index == 1) 1300 else 400
        return phase(t, start, start + 3600, 420, 380)
    }

    private fun drawLowerThird(c: Canvas, t: Long) {
        val p = lowerThirdPhase(t); if (p <= 0f) return
        val b = m.brand!!
        val name = b.presenterName
        val title = listOf(b.professionalTitle, b.clinicName).filter { it.isNotBlank() }.joinToString(" · ")
        val nameP = paint(fonts.brand(b.font, true, arabic), 46f * s, withAlpha(b.textColor.argb(), p))
        val titleP = paint(fonts.brand(b.font, false, arabic), 30f * s, withAlpha(b.textColor.argb(), 0.85f * p))
        val maxW = (contentWidth * 0.78f).toInt()
        val nameL = layout(name, nameP, maxW, Layout.Alignment.ALIGN_NORMAL)
        val titleL = if (title.isNotBlank()) layout(title, titleP, maxW, Layout.Alignment.ALIGN_NORMAL) else null
        fun width(l: StaticLayout): Float { var w = 0f; for (i in 0 until l.lineCount) w = max(w, l.getLineWidth(i)); return w }
        val w = max(width(nameL), titleL?.let { width(it) } ?: 0f)
        val h = nameL.height + (titleL?.height ?: 0) + 8f * s
        val pad = 28f * s
        val slide = (1 - p) * 60f * s * if (arabic) 1 else -1
        val x = if (arabic) contentRight - w - pad * 2 else contentLeft
        val y = m.height * 0.535f
        c.save(); c.translate(x + slide, y)
        val box = RectF(0f, 0f, w + pad * 2, h + pad * 1.4f)
        when (b.lowerThird) {
            LowerThirdStyle.BAR -> {
                c.drawRoundRect(box, 14f * s, 14f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(0xE6101016.toInt(), p) })
                val barX = if (arabic) box.right - 9f * s else 0f
                c.drawRect(barX, 0f, barX + 9f * s, box.bottom, Paint().apply { color = withAlpha(b.secondaryColor.argb(), p) })
            }
            LowerThirdStyle.GLASS -> c.drawRoundRect(box, 26f * s, 26f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(0x66FFFFFF, p) })
            LowerThirdStyle.MINIMAL -> c.drawRect(if (arabic) box.right - 4f * s else 0f, 6f * s, if (arabic) box.right else 4f * s, box.bottom - 6f * s, Paint().apply { color = withAlpha(b.primaryColor.argb(), p) })
            LowerThirdStyle.PILL -> c.drawRoundRect(box, 999f, 999f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, box.right, 0f, withAlpha(b.primaryColor.argb(), 0.95f * p), withAlpha(b.secondaryColor.argb(), 0.95f * p), Shader.TileMode.CLAMP)
            })
        }
        c.translate(pad, pad * 0.7f)
        nameL.draw(c)
        titleL?.let { c.translate(0f, nameL.height + 8f * s); it.draw(c) }
        c.restore()
    }

    private fun watermarkVisible(t: Long): Boolean {
        if (logo == null || !m.branding.enabled || !m.branding.watermark) return false
        ctaWindow()?.let { if (t in it) return false }
        if (m.branding.watermarkOnlyOnTreatment) return m.clinicalRanges.any { t in it }
        return true
    }

    private fun drawWatermark(c: Canvas, t: Long) {
        if (!watermarkVisible(t)) return
        val l = logo!!
        val b = m.brand
        val maxW = 150f * s; val maxH = 90f * s
        val k = min(maxW / l.width, maxH / l.height)
        val w = l.width * k; val h = l.height * k
        val pos = m.branding.logoPosition ?: b?.logoPosition ?: LogoPosition.TOP_RIGHT
        val margin = 36f * s
        val top = m.height * m.safe.top
        var x = when (pos) {
            LogoPosition.TOP_LEFT, LogoPosition.BOTTOM_LEFT -> m.width * m.safe.left
            LogoPosition.TOP_RIGHT, LogoPosition.BOTTOM_RIGHT -> m.width - margin - w
            LogoPosition.TOP_CENTER -> (m.width - w) / 2
        }
        var y = when (pos) {
            LogoPosition.BOTTOM_LEFT, LogoPosition.BOTTOM_RIGHT -> m.height * (1 - m.safe.bottom) - h
            else -> top
        }
        x += m.branding.logoOffsetX * m.width; y += m.branding.logoOffsetY * m.height
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { alpha = ((b?.watermarkOpacity ?: 0.55f) * 255).toInt() }
        c.drawBitmap(l, null, RectF(x, y, x + w, y + h), paint)
    }

    private fun logoPhase(t: Long, cta: LongRange): Float {
        if (logo == null || !m.branding.logoReveal) return 0f
        val start = max(cta.first, m.totalMs - 1800)
        return ease(((t - start) / 600f).coerceIn(0f, 1f)).takeIf { t >= start } ?: 0f
    }

    private fun drawCta(c: Canvas, t: Long) {
        val cta = ctaWindow() ?: return
        if (t !in cta) return
        val b = m.brand!!
        val p = phase(t, cta.first, cta.last + 1, 450, 1)
        val outro = b.outro
        val main = ctaText(b)
        val sub = listOf(b.instagram.takeIf { it.isNotBlank() }?.let { if (it.startsWith("@")) it else "@$it" }, b.booking.takeIf { it.isNotBlank() }).filterNotNull().joinToString("  •  ")
        val maxW = (contentWidth * 0.86f).toInt()
        val mainP = paint(fonts.brand(b.font, true, arabic), 50f * s, withAlpha(b.textColor.argb(), p))
        val subP = paint(fonts.brand(b.font, false, arabic), 30f * s, withAlpha(b.textColor.argb(), 0.9f * p))
        val mainL = if (main.isNotBlank()) layout(main, mainP, maxW, Layout.Alignment.ALIGN_CENTER) else null
        val subL = if (sub.isNotBlank()) layout(sub, subP, maxW, Layout.Alignment.ALIGN_CENTER) else null
        val pad = 34f * s
        val h = (mainL?.height ?: 0) + (subL?.height ?: 0) + if (mainL != null && subL != null) 14f * s else 0f
        val cardW = maxW + pad * 2
        val cardH = h + pad * 2
        val x = contentLeft + (contentWidth - cardW) / 2f
        val bottomLimit = m.height * (1 - m.safe.bottom)
        val y = bottomLimit - cardH - 12f * s + (1 - p) * 80f * s

        if (outro == OutroStyle.LOGO_CENTER) {
            // dim backdrop at the very end for the logo reveal
            val lp = logoPhase(t, cta)
            if (lp > 0f) c.drawColor(Color.argb((110 * lp).toInt(), 0, 0, 0))
        }
        if (outro != OutroStyle.MINIMAL) {
            val card = RectF(x, y, x + cardW, y + cardH)
            c.drawRoundRect(card, 32f * s, 32f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(card.left, card.top, card.right, card.bottom, withAlpha(b.primaryColor.argb(), 0.94f * p), withAlpha(b.secondaryColor.argb(), 0.94f * p), Shader.TileMode.CLAMP)
                setShadowLayer(24f * s, 0f, 8f * s, Color.argb((120 * p).toInt(), 0, 0, 0))
            })
        }
        c.save(); c.translate(x + pad, y + pad)
        mainL?.let { it.draw(c); c.translate(0f, it.height + 14f * s) }
        subL?.draw(c)
        c.restore()

        val lp = logoPhase(t, cta)
        if (lp > 0f && logo != null) {
            val big = outro == OutroStyle.LOGO_CENTER
            val maxLW = (if (big) 420f else 220f) * s; val maxLH = (if (big) 260f else 120f) * s
            val k = min(maxLW / logo.width, maxLH / logo.height) * (0.85f + 0.15f * lp)
            val w = logo.width * k; val hh = logo.height * k
            val cx = m.width / 2f
            val cy = if (big) m.height * 0.42f else y - hh / 2 - 26f * s
            c.drawBitmap(logo, null, RectF(cx - w / 2, cy - hh / 2, cx + w / 2, cy + hh / 2), Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (255 * lp).toInt() })
        }
    }

    private fun drawBeforeAfterDisclosure(c: Canvas, t: Long) {
        if (m.beforeAfterRanges.none { t in it }) return
        val b = m.brand ?: return
        val text = if (arabic) b.beforeAfterDisclosureArabic else b.beforeAfterDisclosure
        if (text.isBlank()) return
        val p = paint(fonts.brand(b.font, false, arabic), 24f * s, Color.argb(220, 255, 255, 255)).apply { setShadowLayer(6f * s, 0f, 1f, Color.BLACK) }
        val lay = layout(text, p, (contentWidth * 0.9f).toInt(), Layout.Alignment.ALIGN_CENTER)
        c.save(); c.translate(contentLeft + contentWidth * 0.05f, m.height * (1 - m.safe.bottom) - lay.height - 4f * s); lay.draw(c); c.restore()
    }
}

/** Media3 overlay that asks the renderer for the bitmap at each frame's composition time. */
class ReelBitmapOverlay(private val renderer: ReelOverlayRenderer) : BitmapOverlay() {
    override fun getBitmap(presentationTimeUs: Long): Bitmap = renderer.bitmapAt(presentationTimeUs / 1000)
}
