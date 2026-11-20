package app.reelify.core.camera

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import app.reelify.core.media.engine.BeautyUniforms
import app.reelify.core.media.engine.FaceUniform
import app.reelify.core.media.engine.ShaderParams
import app.reelify.core.media.engine.Shaders
import java.util.concurrent.Executor

/**
 * CameraX SurfaceProcessor that renders the live camera stream through the same beauty/color
 * fragment shader used by the export pipeline (compiled for samplerExternalOES). It targets the
 * PREVIEW stream; recordings stay untouched and the identical effect is re-applied, with the same
 * parameters and on-device face tracking, when the reel is rendered — so preview and output match
 * while the original footage remains editable.
 */
class BeautySurfaceProcessor : SurfaceProcessor {
    private val thread = HandlerThread("ReelifyCameraGL").apply { start() }
    private val handler = Handler(thread.looper)
    val executor: Executor = Executor { handler.post(it) }

    @Volatile var params: ShaderParams = ShaderParams()
    @Volatile var face: FaceUniform? = null

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program: GlProgram? = null
    private var oesTex = -1
    private var inputTexture: SurfaceTexture? = null
    private var inputSize = Size(1080, 1920)
    private val texMatrix = FloatArray(16)
    private val surfaceMatrix = FloatArray(16)
    private val outputs = LinkedHashMap<SurfaceOutput, Pair<Surface, EGLSurface>>()
    private var released = false

    init { handler.post { initGl() } }

    private fun initGl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2); EGL14.eglInitialize(display, v, 0, v, 1)
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGLExt.EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
        EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, n, 0)
        config = configs[0]
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        pbuffer = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
        val tex = IntArray(1); GLES20.glGenTextures(1, tex, 0); oesTex = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        program = GlProgram(Shaders.VERTEX, Shaders.beautyFragment(external = true)).apply {
            setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            setFloatsUniform("uTransformationMatrix", GlUtil.create4x4IdentityMatrix())
            setFloatsUniform("uUvMin", floatArrayOf(0f, 0f))
            setFloatsUniform("uUvMax", floatArrayOf(1f, 1f))
        }
    }

    override fun onInputSurface(request: SurfaceRequest) {
        if (released) { request.willNotProvideSurface(); return }
        val st = SurfaceTexture(oesTex)
        inputSize = request.resolution
        st.setDefaultBufferSize(request.resolution.width, request.resolution.height)
        val surface = Surface(st)
        request.provideSurface(surface, executor) { st.setOnFrameAvailableListener(null); st.release(); surface.release() }
        st.setOnFrameAvailableListener({ onFrame(it) }, handler)
        inputTexture = st
    }

    override fun onOutputSurface(output: SurfaceOutput) {
        if (released) { output.close(); return }
        val surface = output.getSurface(executor) {
            outputs.remove(output)?.let { (_, egl) -> EGL14.eglDestroySurface(display, egl) }
            output.close()
        }
        val egl = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        outputs[output] = surface to egl
    }

    private fun onFrame(st: SurfaceTexture) {
        if (released) return
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
        st.updateTexImage()
        st.getTransformMatrix(texMatrix)
        val prog = program ?: return
        val p = params; val f = face
        for ((out, pair) in outputs) {
            out.updateTransformMatrix(surfaceMatrix, texMatrix)
            val egl = pair.second
            EGL14.eglMakeCurrent(display, egl, egl, context)
            val size = out.size
            GLES20.glViewport(0, 0, size.width, size.height)
            runCatching {
                prog.use()
                prog.setFloatsUniform("uTexTransformationMatrix", surfaceMatrix)
                prog.setSamplerTexIdUniform("uTexSampler", oesTex, 0)
                BeautyUniforms.apply(prog, p, f, size.width, size.height)
                prog.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            }
            EGLExt.eglPresentationTimeANDROID(display, egl, st.timestamp)
            EGL14.eglSwapBuffers(display, egl)
        }
    }

    fun release() {
        handler.post {
            released = true
            outputs.forEach { (o, pair) -> EGL14.eglDestroySurface(display, pair.second); o.close() }
            outputs.clear()
            runCatching { program?.delete() }
            if (oesTex >= 0) GLES20.glDeleteTextures(1, intArrayOf(oesTex), 0)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, pbuffer)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            thread.quitSafely()
        }
    }
}

/** CameraX effect wrapper targeting the preview stream. */
class BeautyCameraEffect(val processor: BeautySurfaceProcessor, onError: (Throwable) -> Unit) :
    CameraEffect(PREVIEW, processor.executor, processor, { onError(it) })
