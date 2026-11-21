package app.reelify

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.reelify.core.media.engine.BeautyUniforms
import app.reelify.core.media.engine.LookMath
import app.reelify.core.media.engine.ShaderParams
import app.reelify.core.media.engine.Shaders
import app.reelify.core.model.BeautyPreset
import app.reelify.core.model.BeautySettings
import app.reelify.core.model.ColorGrade
import app.reelify.core.model.ColorLook
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.random.Random

/**
 * Renders a synthetic, deterministic "skin" test card through the REAL beauty fragment shader on
 * the device GPU and measures the output. This proves parameters reach the shader and that presets
 * make measurable, distinct changes. It does NOT prove the look is flattering on real faces —
 * that needs a person looking at real footage on a phone (see docs/TESTING.md).
 */
@RunWith(AndroidJUnit4::class)
class BeautyShaderGpuTest {
    private val w = 270; private val h = 480

    private fun testCard(): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(android.graphics.Color.rgb(30, 60, 120)) // non-skin background (top band)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = android.graphics.Color.rgb(205, 158, 136); c.drawRect(0f, h * 0.25f, w.toFloat(), h.toFloat(), p) // skin
        p.color = android.graphics.Color.rgb(60, 42, 40); c.drawOval(w * 0.25f, h * 0.45f, w * 0.40f, h * 0.50f, p); c.drawOval(w * 0.60f, h * 0.45f, w * 0.75f, h * 0.50f, p) // "eyes"
        val rnd = Random(7)
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) { // deterministic luma noise = "skin texture"
            val n = rnd.nextInt(-16, 17)
            val col = px[i]
            fun ch(v: Int) = (v + n).coerceIn(0, 255)
            px[i] = android.graphics.Color.rgb(ch(android.graphics.Color.red(col)), ch(android.graphics.Color.green(col)), ch(android.graphics.Color.blue(col)))
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    private class Gl(w: Int, h: Int) {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        init {
            val v = IntArray(2); EGL14.eglInitialize(display, v, 0, v, 1)
        }
        private val cfg: EGLConfig = run {
            val configs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
            EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE), 0, configs, 0, 1, n, 0)
            configs[0]!!
        }
        val ctx = EGL14.eglCreateContext(display, cfg, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surf = EGL14.eglCreatePbufferSurface(display, cfg, intArrayOf(EGL14.EGL_WIDTH, w, EGL14.EGL_HEIGHT, h, EGL14.EGL_NONE), 0)
        init { EGL14.eglMakeCurrent(display, surf, surf, ctx) }
        fun release() { EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT); EGL14.eglDestroySurface(display, surf); EGL14.eglDestroyContext(display, ctx); EGL14.eglTerminate(display) }
    }

    private fun luma(c: Int) = 0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)
    private fun meanAbsDiff(a: IntArray, b: IntArray): Float {
        var s = 0.0
        for (i in a.indices) for (sh in intArrayOf(16, 8, 0)) s += abs(((a[i] shr sh) and 255) - ((b[i] shr sh) and 255))
        return (s / (a.size * 3)).toFloat()
    }
    /** Mean |pixel − mean of 4 neighbours| (luma) inside rows [y0,y1): a texture/noise measure. */
    private fun hf(px: IntArray, y0: Int, y1: Int): Float {
        var s = 0.0; var n = 0
        for (y in maxOf(1, y0) until minOf(h - 1, y1)) for (x in 1 until w - 1) {
            val i = y * w + x
            s += abs(luma(px[i]) - (luma(px[i - 1]) + luma(px[i + 1]) + luma(px[i - w]) + luma(px[i + w])) / 4f); n++
        }
        return (s / n).toFloat()
    }

    @Test fun beautyPresetsChangePixelsMeasurablyAndDistinctly() {
        val gl = Gl(w, h)
        try {
            val card = testCard()
            val inPx = IntArray(w * h).also { card.getPixels(it, 0, w, 0, 0, w, h) }
            val tex = IntArray(1); GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, card, 0)
            val prog = GlProgram(Shaders.VERTEX, Shaders.beautyFragment(external = false))
            prog.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            prog.setFloatsUniform("uTransformationMatrix", GlUtil.create4x4IdentityMatrix())
            prog.setFloatsUniform("uTexTransformationMatrix", GlUtil.create4x4IdentityMatrix())
            prog.setFloatsUniform("uUvMin", floatArrayOf(0f, 0f)); prog.setFloatsUniform("uUvMax", floatArrayOf(1f, 1f))
            val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())

            fun render(p: ShaderParams, bypass: Boolean = false): IntArray {
                GLES20.glViewport(0, 0, w, h)
                prog.use()
                prog.setSamplerTexIdUniform("uTexSampler", tex[0], 0)
                BeautyUniforms.apply(prog, p, null, w, h, bypass = bypass)
                prog.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                buf.rewind(); GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
                assertThat(GLES20.glGetError()).isEqualTo(GLES20.GL_NO_ERROR)
                val out = IntArray(w * h); buf.rewind()
                for (i in 0 until w * h) { val r = buf.get().toInt() and 255; val g = buf.get().toInt() and 255; val b = buf.get().toInt() and 255; buf.get(); out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b }
                return out
            }

            val none = ColorGrade(look = ColorLook.NONE)
            val identity = render(ShaderParams())
            val bypass = render(LookMath.params(BeautySettings.forPreset(BeautyPreset.SOFT_GLOW), none), bypass = true)
            val idDiff = meanAbsDiff(identity, inPx)
            assertWithMessage("identity render ≈ input").that(idDiff).isLessThan(1.5f)
            assertWithMessage("bypass (hold-to-compare) ≈ identity").that(meanAbsDiff(bypass, identity)).isLessThan(0.5f)

            val skin0 = (h * 0.55f).toInt(); val skin1 = h - 2
            val bg0 = 1; val bg1 = (h * 0.22f).toInt()
            val baseSkinHf = hf(identity, skin0, skin1); val baseBgHf = hf(identity, bg0, bg1)
            val outDir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "beauty").apply { mkdirs() }
            fun save(name: String, px: IntArray) = runCatching {
                val b = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888); File(outDir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            save("00_original", identity)
            val results = linkedMapOf<BeautyPreset, IntArray>()
            BeautyPreset.entries.filter { it != BeautyPreset.CUSTOM }.forEachIndexed { i, pr ->
                val out = render(LookMath.params(BeautySettings.forPreset(pr, 0.7f), none))
                results[pr] = out
                save("%02d_%s".format(i + 1, pr.name.lowercase()), out)
                val diff = meanAbsDiff(out, identity)
                val skinRed = 1f - hf(out, skin0, skin1) / baseSkinHf
                val bgRed = 1f - hf(out, bg0, bg1) / baseBgHf
                Log.i("ReelifyBeautyGpu", "preset=%s meanAbsDiff=%.2f skinTextureReduction=%.0f%% backgroundTextureReduction=%.0f%%".format(pr.name, diff, skinRed * 100, bgRed * 100))
                assertWithMessage("$pr changes the image").that(diff).isAtLeast(2.0f)
                if (pr != BeautyPreset.CLINICAL_PREMIUM && pr != BeautyPreset.CINEMATIC) assertWithMessage("$pr smooths skin texture").that(skinRed).isAtLeast(0.12f)
                assertWithMessage("$pr smooths skin more than non-skin background").that(skinRed).isGreaterThan(bgRed)
            }
            assertWithMessage("Soft Glow is the smoothest preset").that(1f - hf(results.getValue(BeautyPreset.SOFT_GLOW), skin0, skin1) / baseSkinHf)
                .isGreaterThan(1f - hf(results.getValue(BeautyPreset.NATURAL), skin0, skin1) / baseSkinHf)
            val list = results.entries.toList()
            for (i in list.indices) for (j in i + 1 until list.size) {
                val d = meanAbsDiff(list[i].value, list[j].value)
                Log.i("ReelifyBeautyGpu", "pair ${list[i].key} vs ${list[j].key}: meanAbsDiff=%.2f".format(d))
                assertWithMessage("${list[i].key} vs ${list[j].key} are visibly different").that(d).isAtLeast(1.5f)
            }
            // Intensity is monotonic on the GPU too.
            val lo = meanAbsDiff(render(LookMath.params(BeautySettings.forPreset(BeautyPreset.NATURAL, 0.3f), none)), identity)
            val hi = meanAbsDiff(render(LookMath.params(BeautySettings.forPreset(BeautyPreset.NATURAL, 1f), none)), identity)
            Log.i("ReelifyBeautyGpu", "natural intensity 0.3 → %.2f, 1.0 → %.2f".format(lo, hi))
            assertThat(hi).isGreaterThan(lo)
            prog.delete()
        } finally { gl.release() }
    }
}
