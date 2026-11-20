package app.reelify.core.media.engine

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.MatrixTransformation
import android.graphics.Matrix
import app.reelify.core.model.TransitionType

/** Uniform upload shared by the camera renderer and the Media3 program. */
object BeautyUniforms {
    fun apply(p: GlProgram, s: ShaderParams, face: FaceUniform?, width: Int, height: Int) {
        p.setFloatsUniform("uTexel", floatArrayOf(1f / width, 1f / height))
        p.setFloatUniform("uScale", (minOf(width, height) / 1080f).coerceAtLeast(0.35f))
        p.setFloatUniform("uAspect", width.toFloat() / height)
        p.setFloatUniform("uSmooth", s.smooth); p.setFloatUniform("uTone", s.tone)
        p.setFloatUniform("uUnderEye", s.underEye); p.setFloatUniform("uRefine", s.refine)
        p.setFloatUniform("uExposure", s.exposure); p.setFloatUniform("uBrightness", s.brightness)
        p.setFloatUniform("uContrast", s.contrast); p.setFloatUniform("uSaturation", s.saturation)
        p.setFloatUniform("uWarmth", s.warmth); p.setFloatUniform("uTint", s.tint)
        p.setFloatUniform("uHighlights", s.highlights); p.setFloatUniform("uShadows", s.shadows)
        p.setFloatUniform("uClarity", s.clarity); p.setFloatUniform("uSharpen", s.sharpen)
        p.setFloatUniform("uSplit", s.split)
        if (face != null) {
            p.setFloatsUniform("uFace", floatArrayOf(face.cx, face.cy, face.rx, face.ry))
            p.setFloatsUniform("uEyes", floatArrayOf(face.lx, face.ly, face.rxEye, face.ryEye))
            p.setFloatUniform("uFaceOn", 1f)
        } else {
            p.setFloatsUniform("uFace", floatArrayOf(0.5f, 0.6f, 0.2f, 0.2f))
            p.setFloatsUniform("uEyes", floatArrayOf(-1f, -1f, -1f, -1f))
            p.setFloatUniform("uFaceOn", 0f)
        }
    }
}

/**
 * Per-clip beauty + color effect for the Media3 render/preview pipeline.
 * [faceAt] receives the composition timestamp (µs) and returns face geometry in output uv space.
 */
class BeautyColorEffect(
    private val params: ShaderParams,
    private val faceAt: ((Long) -> FaceUniform?)? = null,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = BeautyColorProgram(params, faceAt)
    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = params.isIdentity
}

private class BeautyColorProgram(
    private val params: ShaderParams,
    private val faceAt: ((Long) -> FaceUniform?)?,
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {
    private val program: GlProgram = try {
        GlProgram(Shaders.VERTEX, Shaders.beautyFragment(external = false))
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }
    private var w = 1
    private var h = 1

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        val id = GlUtil.create4x4IdentityMatrix()
        program.setFloatsUniform("uTransformationMatrix", id)
        program.setFloatsUniform("uTexTransformationMatrix", id)
        program.setFloatsUniform("uUvMin", floatArrayOf(0f, 0f))
        program.setFloatsUniform("uUvMax", floatArrayOf(1f, 1f))
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        w = inputWidth; h = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            val face = if (params.usesFace) runCatching { faceAt?.invoke(presentationTimeUs) }.getOrNull() else null
            BeautyUniforms.apply(program, params, face, w, h)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
}

/**
 * Zoom / pan / flip, Ken Burns for stills and a slow digital push-in, applied in NDC after the
 * 9:16 presentation crop. Time is the composition time; [segmentStartUs]/[segmentDurationUs]
 * locate the segment on the timeline. Zoom never drops below 1 so no black borders appear.
 */
class ReframeTransformation(
    private val zoom: Float,
    private val panX: Float,
    private val panY: Float,
    private val flip: Boolean,
    private val motion: Float,           // extra zoom reached at the end of the segment (0 = static)
    private val segmentStartUs: Long,
    private val segmentDurationUs: Long,
) : MatrixTransformation {
    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val p = if (segmentDurationUs <= 0) 0f else ((presentationTimeUs - segmentStartUs).toFloat() / segmentDurationUs).coerceIn(0f, 1f)
        val eased = p * p * (3 - 2 * p)
        val s = zoom.coerceAtLeast(1f) * (1f + motion * eased)
        val travel = s - 1f
        return Matrix().apply {
            if (flip) postScale(-1f, 1f)
            postScale(s, s)
            postTranslate(-panX.coerceIn(-1f, 1f) * travel, panY.coerceIn(-1f, 1f) * travel)
        }
    }

    val isStatic: Boolean get() = zoom <= 1.0001f && motion == 0f && !flip

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = isStatic
}

/** One transition on the reel timeline. */
data class TransitionSpec(val atUs: Long, val type: TransitionType, val durationUs: Long) {
    val needsSnapshot: Boolean get() = type == TransitionType.CROSSFADE || type == TransitionType.DISSOLVE || type == TransitionType.SLIDE || type == TransitionType.SUBTLE_ZOOM
    /** Window in which the effect is visible. */
    val windowStartUs: Long get() = if (needsSnapshot) atUs else atUs - durationUs / 2
    val windowEndUs: Long get() = if (needsSnapshot) atUs + durationUs else atUs + durationUs / 2
    val mode: Float get() = when (type) {
        TransitionType.CUT -> 0f
        TransitionType.CROSSFADE -> 1f
        TransitionType.SUBTLE_ZOOM -> 2f
        TransitionType.DISSOLVE -> 3f
        TransitionType.SLIDE -> 4f
        TransitionType.LIGHT_FLASH -> 5f
        TransitionType.SOFT_BLUR -> 6f
    }
}

/**
 * Composition-level finishing pass. Transitions are implemented inside the existing timeline
 * (they never add or remove time): crossfade-type transitions blend from a GPU snapshot of the
 * outgoing clip's last frame into the incoming clip.
 */
class FinishEffect(
    private val transitions: List<TransitionSpec>,
    private val vignette: Float,
    private val lightOverlay: Boolean,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = FinishProgram(transitions.filter { it.type != TransitionType.CUT }.sortedBy { it.atUs }, vignette, lightOverlay)
    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        transitions.none { it.type != TransitionType.CUT } && vignette <= 0.001f && !lightOverlay
}

private class FinishProgram(
    private val transitions: List<TransitionSpec>,
    private val vignette: Float,
    private val lightOverlay: Boolean,
) : BaseGlShaderProgram(false, 1) {
    private val program = try { GlProgram(Shaders.VERTEX, Shaders.FINISH_FRAGMENT) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private val copy = try { GlProgram(Shaders.VERTEX, Shaders.COPY_FRAGMENT) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var w = 1
    private var h = 1
    private var snapTex = -1
    private var snapFbo = -1
    private var snapForUs = Long.MIN_VALUE
    private val captureWindowUs = 250_000L

    init {
        for (p in listOf(program, copy)) {
            p.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            p.setFloatsUniform("uTransformationMatrix", GlUtil.create4x4IdentityMatrix())
            p.setFloatsUniform("uTexTransformationMatrix", GlUtil.create4x4IdentityMatrix())
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        if (inputWidth != w || inputHeight != h || snapTex < 0) {
            releaseSnap()
            w = inputWidth; h = inputHeight
            if (transitions.any { it.needsSnapshot }) {
                snapTex = GlUtil.createTexture(w, h, false)
                snapFbo = GlUtil.createFboForTexture(snapTex)
            }
        }
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            // 1. Capture the outgoing frame shortly before a snapshot transition.
            val upcoming = transitions.firstOrNull { it.needsSnapshot && presentationTimeUs < it.atUs && presentationTimeUs >= it.atUs - captureWindowUs }
            if (upcoming != null && snapFbo >= 0) {
                val prevFbo = IntArray(1); GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFbo, 0)
                val vp = IntArray(4); GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
                GlUtil.focusFramebufferUsingCurrentContext(snapFbo, w, h)
                copy.use()
                copy.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                copy.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0])
                GLES20.glViewport(vp[0], vp[1], vp[2], vp[3])
                snapForUs = upcoming.atUs
            }
            // 2. Render with the active transition (if any).
            val active = transitions.firstOrNull { presentationTimeUs >= it.windowStartUs && presentationTimeUs < it.windowEndUs }
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setSamplerTexIdUniform("uSnap", if (snapTex >= 0) snapTex else inputTexId, 1)
            program.setFloatsUniform("uTexel", floatArrayOf(1f / w, 1f / h))
            program.setFloatUniform("uScale", (minOf(w, h) / 1080f).coerceAtLeast(0.35f))
            program.setFloatUniform("uAspect", w.toFloat() / h)
            program.setFloatUniform("uVignette", vignette)
            program.setFloatUniform("uLight", if (lightOverlay) 1f else 0f)
            program.setFloatUniform("uTime", presentationTimeUs / 1_000_000f)
            if (active != null) {
                val progress = (presentationTimeUs - active.windowStartUs).toFloat() / (active.windowEndUs - active.windowStartUs).coerceAtLeast(1)
                program.setFloatUniform("uMode", active.mode)
                program.setFloatUniform("uProgress", progress)
                program.setFloatUniform("uHasSnap", if (active.needsSnapshot && snapForUs == active.atUs && snapTex >= 0) 1f else 0f)
            } else {
                program.setFloatUniform("uMode", 0f)
                program.setFloatUniform("uProgress", 0f)
                program.setFloatUniform("uHasSnap", 0f)
            }
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    private fun releaseSnap() {
        runCatching { if (snapFbo >= 0) GLES20.glDeleteFramebuffers(1, intArrayOf(snapFbo), 0) }
        runCatching { if (snapTex >= 0) GlUtil.deleteTexture(snapTex) }
        snapFbo = -1; snapTex = -1
    }

    override fun release() {
        super.release()
        releaseSnap()
        try { program.delete(); copy.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
}
