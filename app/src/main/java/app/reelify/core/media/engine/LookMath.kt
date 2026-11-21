package app.reelify.core.media.engine

import app.reelify.core.model.BeautySettings
import app.reelify.core.model.ColorGrade
import app.reelify.core.model.ColorLook
import app.reelify.core.model.FaceSample
import app.reelify.core.model.FaceTrack

/** Flattened shader parameters shared by camera preview and render pipeline. */
data class ShaderParams(
    val smooth: Float = 0f, val tone: Float = 0f, val underEye: Float = 0f, val refine: Float = 0f,
    val exposure: Float = 0f, val brightness: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f,
    val warmth: Float = 0f, val tint: Float = 0f, val highlights: Float = 0f, val shadows: Float = 0f,
    val clarity: Float = 0f, val sharpen: Float = 0f, val split: Float = 0f, val glow: Float = 0f,
) {
    val isIdentity: Boolean get() = this == ShaderParams()
    val usesFace: Boolean get() = underEye > 0.001f || refine > 0.001f || smooth > 0.001f

    /** Mean absolute difference across all controls — used to verify presets are perceptibly distinct. */
    fun distanceTo(o: ShaderParams): Float = listOf(smooth - o.smooth, tone - o.tone, underEye - o.underEye, refine - o.refine, exposure - o.exposure, brightness - o.brightness, contrast - o.contrast, saturation - o.saturation, warmth - o.warmth, tint - o.tint, highlights - o.highlights, shadows - o.shadows, clarity - o.clarity, sharpen - o.sharpen, split - o.split, glow - o.glow).sumOf { kotlin.math.abs(it).toDouble() }.toFloat()
}

/** Face geometry in GL texture space (origin bottom-left, y up). */
data class FaceUniform(
    val cx: Float, val cy: Float, val rx: Float, val ry: Float,
    val lx: Float = -1f, val ly: Float = -1f, val rxEye: Float = -1f, val ryEye: Float = -1f,
)

object LookMath {
    /** Perceptual gain so the default intensity (0.7) gives a clearly visible but natural result. */
    const val SMOOTH_GAIN = 1.35f

    private fun look(l: ColorLook): ShaderParams = when (l) {
        ColorLook.NONE -> ShaderParams()
        ColorLook.NATURAL -> ShaderParams(contrast = 0.05f, saturation = 0.03f)
        ColorLook.CLEAN -> ShaderParams(brightness = 0.04f, contrast = 0.07f, saturation = -0.02f, highlights = 0.12f)
        ColorLook.WARM -> ShaderParams(warmth = 0.35f, saturation = 0.05f, contrast = 0.05f)
        ColorLook.COOL_CLINICAL -> ShaderParams(warmth = -0.25f, tint = -0.05f, brightness = 0.03f, saturation = -0.08f, contrast = 0.08f)
        ColorLook.CINEMATIC -> ShaderParams(contrast = 0.18f, saturation = -0.1f, warmth = 0.1f, highlights = 0.2f, shadows = -0.05f, split = 0.6f)
        ColorLook.MOODY -> ShaderParams(contrast = 0.22f, saturation = -0.18f, brightness = -0.04f, shadows = -0.1f, split = 0.5f)
        ColorLook.BRIGHT_SOCIAL -> ShaderParams(brightness = 0.06f, saturation = 0.15f, contrast = 0.1f, warmth = 0.05f)
    }

    /**
     * Combines per-clip beauty and the global grade into one parameter set.
     * [allowBeauty] is false for clinical footage unless explicitly enabled by the user.
     */
    fun params(beauty: BeautySettings, grade: ColorGrade, allowBeauty: Boolean = true): ShaderParams {
        val g = look(grade.look)
        val k = grade.intensity.coerceIn(0f, 1f)
        var p = ShaderParams(
            exposure = 0f,
            brightness = g.brightness * k + grade.brightness * 0.5f,
            contrast = g.contrast * k + grade.contrast * 0.6f,
            saturation = g.saturation * k + grade.saturation * 0.6f,
            warmth = g.warmth * k + grade.warmth * 0.7f,
            tint = g.tint * k + grade.tint * 0.7f,
            highlights = g.highlights * k - grade.highlights * 0.6f,
            shadows = g.shadows * k + grade.shadows * 0.6f,
            split = g.split * k,
        )
        if (beauty.enabled && allowBeauty) {
            val i = beauty.intensity.coerceIn(0f, 1f)
            p = p.copy(
                smooth = (beauty.smoothing * i * SMOOTH_GAIN).coerceAtMost(1f),
                tone = (beauty.toneEven * i * SMOOTH_GAIN).coerceAtMost(1f),
                glow = beauty.glow * i,
                split = p.split + beauty.split * i,
                underEye = beauty.underEye * i,
                refine = beauty.faceRefine * i,
                exposure = p.exposure + beauty.exposure * 0.6f * i,
                brightness = p.brightness + beauty.brightness * i,
                contrast = p.contrast + beauty.contrast * i,
                saturation = p.saturation + beauty.saturation * i,
                warmth = p.warmth + beauty.warmth * i,
                highlights = p.highlights + beauty.highlightReduction * i,
                shadows = p.shadows + beauty.shadowLift * i,
                clarity = beauty.clarity * i,
                sharpen = beauty.sharpen * i,
            )
        }
        return p
    }

    /**
     * Maps a point from the display-oriented source frame (normalized, origin top-left) into the
     * 9:16 crop-to-fill output (normalized, origin top-left), after an optional user rotation.
     */
    fun mapToOutput(x: Float, y: Float, srcW: Int, srcH: Int, rotation: Int, outW: Int, outH: Int): Pair<Float, Float> {
        var px = x; var py = y; var w = srcW.toFloat(); var h = srcH.toFloat()
        when (((rotation % 360) + 360) % 360) {
            90 -> { val nx = py; val ny = 1f - px; px = nx; py = ny; val t = w; w = h; h = t }
            180 -> { px = 1f - px; py = 1f - py }
            270 -> { val nx = 1f - py; val ny = px; px = nx; py = ny; val t = w; w = h; h = t }
        }
        if (w <= 0 || h <= 0) return px to py
        val scale = maxOf(outW / w, outH / h)
        val fx = (outW / scale) / w
        val fy = (outH / scale) / h
        return ((px - 0.5f) / fx + 0.5f) to ((py - 0.5f) / fy + 0.5f)
    }

    fun interpolate(track: FaceTrack?, sourceMs: Long): FaceSample? {
        val s = track?.samples ?: return null
        if (s.isEmpty()) return null
        if (sourceMs <= s.first().tMs) return s.first().takeIf { it.tMs - sourceMs < 600 }
        if (sourceMs >= s.last().tMs) return s.last().takeIf { sourceMs - it.tMs < 600 }
        val i = s.indexOfLast { it.tMs <= sourceMs }
        val a = s[i]; val b = s[i + 1]
        if (b.tMs - a.tMs > 1500) return if (sourceMs - a.tMs < b.tMs - sourceMs) a else b
        val f = (sourceMs - a.tMs).toFloat() / (b.tMs - a.tMs).coerceAtLeast(1)
        fun l(p: Float, q: Float) = if (p < 0 || q < 0) -1f else p + (q - p) * f
        return FaceSample(sourceMs, l(a.cx, b.cx), l(a.cy, b.cy), l(a.w, b.w), l(a.h, b.h), l(a.leftEyeX, b.leftEyeX), l(a.leftEyeY, b.leftEyeY), l(a.rightEyeX, b.rightEyeX), l(a.rightEyeY, b.rightEyeY))
    }

    /** Converts a source-space face sample to GL uv (y-up) in the output frame. */
    fun toUniform(f: FaceSample, srcW: Int, srcH: Int, rotation: Int, outW: Int, outH: Int): FaceUniform {
        fun m(x: Float, y: Float): Pair<Float, Float> {
            if (x < 0 || y < 0) return -1f to -1f
            val (ox, oy) = mapToOutput(x, y, srcW, srcH, rotation, outW, outH)
            return ox to (1f - oy)
        }
        val (cx, cy) = m(f.cx, f.cy)
        val (ex1, ey1) = m(f.cx + f.w / 2, f.cy + f.h / 2)
        val aspect = outW.toFloat() / outH
        // radii expressed in aspect-corrected space (x scaled by aspect)
        val rx = kotlin.math.abs(ex1 - cx) * aspect
        val ry = kotlin.math.abs(ey1 - cy)
        val (lx, ly) = m(f.leftEyeX, f.leftEyeY)
        val (rx2, ry2) = m(f.rightEyeX, f.rightEyeY)
        return FaceUniform(cx, cy, rx.coerceAtLeast(0.02f), ry.coerceAtLeast(0.02f), lx, ly, rx2, ry2)
    }
}
