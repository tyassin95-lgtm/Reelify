package app.reelify.beauty

import app.reelify.core.camera.CameraFaceMapping
import app.reelify.core.camera.UprightBox
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

class BeautyCalibrationTest {
    private val none = ColorGrade(look = ColorLook.NONE)
    private fun p(preset: BeautyPreset, i: Float = 0.7f) = LookMath.params(BeautySettings.forPreset(preset, i), none)

    @Test fun `every preset produces a clearly non-identity effect at default intensity`() {
        BeautyPreset.entries.filter { it != BeautyPreset.CUSTOM }.forEach { pr ->
            val sp = p(pr)
            assertThat(sp.isIdentity).isFalse()
            assertThat(sp.smooth).isAtLeast(0.25f)
            assertThat(sp.distanceTo(ShaderParams())).isAtLeast(1.0f)
        }
    }

    @Test fun `presets are perceptibly distinct from each other`() {
        val ps = BeautyPreset.entries.filter { it != BeautyPreset.CUSTOM }.map { it to p(it) }
        for (i in ps.indices) for (j in i + 1 until ps.size) {
            assertWithMessage("${ps[i].first} vs ${ps[j].first}").that(ps[i].second.distanceTo(ps[j].second)).isAtLeast(0.6f)
        }
    }

    @Test fun `intensity scales the effect monotonically and zero intensity is identity`() {
        val lo = p(BeautyPreset.NATURAL, 0.3f); val mid = p(BeautyPreset.NATURAL, 0.6f); val hi = p(BeautyPreset.NATURAL, 1f)
        assertThat(lo.smooth).isLessThan(mid.smooth); assertThat(mid.smooth).isLessThan(hi.smooth)
        assertThat(lo.distanceTo(ShaderParams())).isLessThan(hi.distanceTo(ShaderParams()))
        assertThat(p(BeautyPreset.NATURAL, 0f).isIdentity).isTrue()
        assertThat(hi.smooth).isAtMost(1f)
    }

    @Test fun `clinical footage never receives beauty and disabled beauty is identity`() {
        assertThat(LookMath.params(BeautySettings.forPreset(BeautyPreset.SOFT_GLOW), none, allowBeauty = false).isIdentity).isTrue()
        assertThat(LookMath.params(BeautySettings(enabled = false), none).isIdentity).isTrue()
    }

    @Test fun `glow and split-tone parameters reach the shader`() {
        assertThat(p(BeautyPreset.SOFT_GLOW).glow).isGreaterThan(0.3f)
        assertThat(p(BeautyPreset.CINEMATIC).split).isGreaterThan(0.4f)
        val src = Shaders.beautyFragment(external = true)
        listOf("uGlow", "uUp", "uBypass", "uSmooth", "uSplit").forEach { assertThat(src).contains("uniform") ; assertThat(src).contains(it) }
        assertThat(src).contains("samplerExternalOES")
        assertThat(Shaders.beautyFragment(external = false)).contains("sampler2D uTexSampler")
    }

    // --------------------------------------------------------------- camera face mapping (root-cause fix)

    @Test fun `upright to buffer inverse rotation`() {
        assertThat(CameraFaceMapping.uprightToBuffer(0.2f, 0.3f, 0)).isEqualTo(0.2f to 0.3f)
        assertThat(CameraFaceMapping.uprightToBuffer(0.2f, 0.3f, 90)).isEqualTo(0.3f to 0.8f)
        assertThat(CameraFaceMapping.uprightToBuffer(0.2f, 0.3f, 180)).isEqualTo(0.8f to 0.7f)
        assertThat(CameraFaceMapping.uprightToBuffer(0.2f, 0.3f, 270)).isEqualTo(0.7f to 0.2f)
    }

    @Test fun `face in the upper middle of a portrait front-camera frame maps into sensor texture space`() {
        // Typical front camera: analysis rotation 270. Face centred horizontally, upper third.
        val u = CameraFaceMapping.faceUniform(UprightBox(0.35f, 0.2f, 0.65f, 0.45f), 0.42f to 0.3f, 0.58f to 0.3f, 270, 16f / 9f)
        // centre: upright (0.5, 0.325) → buffer (1-0.325, 0.5) = (0.675, 0.5) → texture (0.675, 0.5)
        assertThat(u.cx).isWithin(1e-4f).of(0.675f); assertThat(u.cy).isWithin(1e-4f).of(0.5f)
        // box width in texture s comes from the upright HEIGHT (0.25) → rx = 0.125 * aspect
        assertThat(u.rx).isWithin(1e-4f).of(0.125f * 16f / 9f)
        assertThat(u.ry).isWithin(1e-4f).of(0.15f)
        assertThat(CameraFaceMapping.upVector(270)).isEqualTo(1f to 0f)
        // the face is NOT where the old display-space mapping put it (0.5, 0.675)
        assertThat(u.cy).isNotWithin(0.1f).of(0.675f)
    }

    @Test fun `identity rotation keeps upright coordinates with a Y flip`() {
        val u = CameraFaceMapping.faceUniform(UprightBox(0.4f, 0.1f, 0.6f, 0.3f), null, null, 0, 0.5625f)
        assertThat(u.cx).isWithin(1e-4f).of(0.5f); assertThat(u.cy).isWithin(1e-4f).of(0.8f)
        assertThat(u.lx).isEqualTo(-1f)
        assertThat(CameraFaceMapping.upVector(0)).isEqualTo(0f to 1f)
    }
}
