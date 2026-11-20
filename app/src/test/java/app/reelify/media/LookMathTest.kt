package app.reelify.media

import app.reelify.core.media.engine.LookMath
import app.reelify.core.model.BeautySettings
import app.reelify.core.model.BeautyPreset
import app.reelify.core.model.ColorGrade
import app.reelify.core.model.ColorLook
import app.reelify.core.model.FaceSample
import app.reelify.core.model.FaceTrack
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LookMathTest {
    @Test fun `center maps to center and 16x9 landscape is cropped horizontally`() {
        val (cx, cy) = LookMath.mapToOutput(0.5f, 0.5f, 1920, 1080, 0, 1080, 1920)
        assertThat(cx).isWithin(1e-4f).of(0.5f); assertThat(cy).isWithin(1e-4f).of(0.5f)
        // a point at 40% x in a landscape source is outside the narrow 9:16 crop window? (crop is 31.6% wide)
        val (x, _) = LookMath.mapToOutput(0.4f, 0.5f, 1920, 1080, 0, 1080, 1920)
        assertThat(x).isWithin(0.01f).of(0.5f - 0.1f / 0.3164f)
        // portrait 9:16 source maps 1:1
        assertThat(LookMath.mapToOutput(0.2f, 0.7f, 1080, 1920, 0, 1080, 1920).first).isWithin(1e-4f).of(0.2f)
    }

    @Test fun `beauty is excluded when not allowed (clinical)`() {
        val b = BeautySettings.forPreset(BeautyPreset.SOFT_GLOW)
        assertThat(LookMath.params(b, ColorGrade(ColorLook.NONE), allowBeauty = false).isIdentity).isTrue()
        assertThat(LookMath.params(b, ColorGrade(ColorLook.NONE)).smooth).isGreaterThan(0f)
    }

    @Test fun `face track interpolates between samples`() {
        val t = FaceTrack(listOf(FaceSample(0, 0.4f, 0.4f, 0.2f, 0.2f), FaceSample(1000, 0.6f, 0.4f, 0.2f, 0.2f)))
        assertThat(LookMath.interpolate(t, 500)!!.cx).isWithin(1e-4f).of(0.5f)
        assertThat(LookMath.interpolate(t, 5000)).isNull()
    }
}
