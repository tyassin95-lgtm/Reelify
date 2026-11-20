package app.reelify.audio

import app.reelify.core.audio.Biquad
import app.reelify.core.audio.Envelope
import app.reelify.core.audio.Fft
import app.reelify.core.audio.GainEnvelope
import app.reelify.core.audio.Limiter
import app.reelify.core.audio.SpectralDenoiser
import app.reelify.core.audio.SpeechAnalysis
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class DspTest {
    private val fs = 48_000

    private fun rms(x: FloatArray, from: Int = 0, to: Int = x.size) = sqrt((from until to).sumOf { (x[it] * x[it]).toDouble() } / (to - from)).toFloat()

    @Test fun `fft round trip`() {
        val n = 512; val fft = Fft(n)
        val re = DoubleArray(n) { sin(2 * PI * 7 * it / n) + 0.3 * sin(2 * PI * 31 * it / n) }; val orig = re.copyOf(); val im = DoubleArray(n)
        fft.transform(re, im); fft.transform(re, im, inverse = true)
        for (i in 0 until n) assertThat(abs(re[i] - orig[i])).isLessThan(1e-9)
    }

    @Test fun `denoiser keeps length, suppresses stationary noise, preserves a tone`() {
        val rnd = Random(1)
        val n = fs * 3
        val noise = FloatArray(n) { (rnd.nextFloat() * 2 - 1) * 0.05f }
        val tone = FloatArray(n) { if (it > fs) (0.3 * sin(2 * PI * 440 * it / fs)).toFloat() else 0f }
        val mix = FloatArray(n) { noise[it] + tone[it] }
        val d = SpectralDenoiser(fs, 0.6f)
        val out = d.process(mix)
        assertThat(out.size).isEqualTo(n)
        val lat = d.latency
        // noise-only region (after the estimator converges)
        val noiseIn = rms(mix, fs / 2, fs - 2000)
        val noiseOut = rms(out, fs / 2 + lat, fs - 2000 + lat)
        assertThat(noiseOut).isLessThan(noiseIn * 0.5f)
        // tone region keeps most of its energy
        val toneIn = rms(tone, (fs * 1.5).toInt(), fs * 2)
        val toneOut = rms(out, (fs * 1.5).toInt() + lat, fs * 2 + lat)
        assertThat(toneOut).isGreaterThan(toneIn * 0.7f)
    }

    @Test fun `denoiser off is bypass`() {
        val x = FloatArray(1000) { it / 1000f }
        assertThat(SpectralDenoiser(fs, 0f).process(x)).isEqualTo(x)
    }

    @Test fun `limiter never exceeds ceiling`() {
        val l = Limiter(fs, -1.0)
        val ceiling = Math.pow(10.0, -1.0 / 20)
        for (i in 0 until fs) { val y = l.process(1.8 * sin(2 * PI * 100 * i / fs)); assertThat(abs(y)).isAtMost(ceiling + 1e-9) }
    }

    @Test fun `high-pass attenuates rumble`() {
        val hp = Biquad.highPass(fs, 100.0)
        val out = FloatArray(fs) { hp.process(sin(2 * PI * 30 * it / fs)).toFloat() }
        assertThat(rms(out, fs / 2)).isLessThan(0.1f * 0.707f)
    }

    @Test fun `music envelope fades and ducks under speech`() {
        val env = GainEnvelope.music(30_000, 0.3f, 1000, 2000, listOf(10_000L..14_000L), duck = true, duckDepth = 0.6f)
        assertThat(env.at(0.0)).isWithin(0.01f).of(0f)
        assertThat(env.at(5_000.0)).isWithin(0.02f).of(0.3f)
        assertThat(env.at(12_000.0)).isLessThan(0.3f * 0.5f)
        assertThat(env.at(29_999.0)).isLessThan(0.02f)
    }

    @Test fun `normalization gain is clamped`() {
        assertThat(GainEnvelope.normalizationGain(0.1f)).isWithin(0.01f).of(1f)
        assertThat(GainEnvelope.normalizationGain(0.0001f)).isWithin(0.01f).of(GainEnvelope.dbToLinear(12f))
    }

    @Test fun `smart trim starts at speech and ends on a pause`() {
        // 14 s take: silence 0-1.5s, speech 1.5-5, pause, speech 5.6-10.2, pause, speech 10.8-13.5
        val values = FloatArray(1400) { i ->
            val t = i * 10
            if (t in 1500..5000 || t in 5600..10200 || t in 10800..13500) 0.2f else 0.004f
        }
        val s = SpeechAnalysis.suggestTrim(Envelope(10, values), 14_000, 10_000)
        assertThat(s.startMs).isIn(1300L..1500L)
        assertThat(s.endMs).isIn(10_200L..11_500L)
        assertThat(s.endMs - s.startMs).isAtMost(10_000)
    }
}
