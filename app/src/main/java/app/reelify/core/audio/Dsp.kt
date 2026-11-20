package app.reelify.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** In-place iterative radix-2 complex FFT. */
class Fft(val n: Int) {
    private val levels = Integer.numberOfTrailingZeros(n)
    private val cosT = DoubleArray(n / 2) { cos(2 * PI * it / n) }
    private val sinT = DoubleArray(n / 2) { sin(2 * PI * it / n) }

    init { require(n > 0 && n and (n - 1) == 0) { "FFT size must be a power of two" } }

    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean = false) {
        for (i in 0 until n) {
            val j = Integer.reverse(i) ushr (32 - levels)
            if (j > i) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2; val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + half) {
                    val c = cosT[k]; val s = if (inverse) -sinT[k] else sinT[k]
                    val tr = re[j + half] * c + im[j + half] * s
                    val ti = -re[j + half] * s + im[j + half] * c
                    re[j + half] = re[j] - tr; im[j + half] = im[j] - ti
                    re[j] += tr; im[j] += ti
                    k += step
                }
                i += size
            }
            size *= 2
        }
        if (inverse) for (i in 0 until n) { re[i] /= n; im[i] /= n }
    }
}

/**
 * Classical single-channel speech denoiser (no machine learning):
 * STFT (sqrt-Hann, 50% overlap) → noise PSD tracking by smoothed minimum statistics →
 * decision-directed a-priori SNR (Ephraim–Malah) → Wiener gain with a spectral floor and
 * temporal gain smoothing to avoid "musical noise"/robotic artifacts → overlap-add.
 *
 * [strength] 0..1 controls over-subtraction and the gain floor.
 */
class SpectralDenoiser(sampleRate: Int, strength: Float) {
    val frameSize: Int = if (sampleRate > 32000) 1024 else 512
    private val hop = frameSize / 2
    private val fft = Fft(frameSize)
    private val bins = frameSize / 2 + 1
    private val window = DoubleArray(frameSize) { sqrt(0.5 * (1 - cos(2 * PI * it / frameSize))) }
    private val inBuf = DoubleArray(frameSize)
    private val outAccum = DoubleArray(frameSize)
    private var inFill = 0
    private val re = DoubleArray(frameSize)
    private val im = DoubleArray(frameSize)
    private val noisePsd = DoubleArray(bins)
    private val smoothPsd = DoubleArray(bins)
    private val minPsd = DoubleArray(bins)
    private val tmpMin = DoubleArray(bins)
    private val prevGain = DoubleArray(bins) { 1.0 }
    private val prevCleanPsd = DoubleArray(bins)
    private var frames = 0
    private val minWindowFrames = max(8, (1.2 * sampleRate / hop).toInt())
    private val s = strength.coerceIn(0f, 1f).toDouble()
    private val gainFloor = 10.0.pow((-6.0 - 14.0 * s) / 20.0) // -6 dB .. -20 dB
    private val overSub = 1.0 + 1.5 * s
    private val bypass = strength <= 0.001f

    /** Algorithmic latency in samples (output is delayed by this amount). */
    val latency: Int get() = if (bypass) 0 else hop

    private val pending = ArrayDeque<Float>()

    /** Processes [input] samples (mono, -1..1) and returns denoised samples (same count overall, delayed by [latency]). */
    fun process(input: FloatArray, count: Int = input.size): FloatArray {
        if (bypass) return input.copyOf(count)
        val out = FloatArray(count)
        var outIdx = 0
        for (i in 0 until count) {
            inBuf[hop + inFill] = input[i].toDouble()
            inFill++
            if (inFill == hop) {
                processFrame()
                // shift input half
                System.arraycopy(inBuf, hop, inBuf, 0, hop)
                inFill = 0
            }
            if (pending.isNotEmpty()) out[outIdx++] = pending.removeFirst() else out[outIdx++] = 0f
        }
        return out
    }

    private fun processFrame() {
        for (i in 0 until frameSize) { re[i] = inBuf[i] * window[i]; im[i] = 0.0 }
        fft.transform(re, im)
        frames++
        for (k in 0 until bins) {
            val p = re[k] * re[k] + im[k] * im[k]
            // Smoothed periodogram
            smoothPsd[k] = if (frames == 1) p else 0.8 * smoothPsd[k] + 0.2 * p
            // Minimum statistics over a sliding window (two-stage running minimum)
            if (frames == 1) { minPsd[k] = smoothPsd[k]; tmpMin[k] = smoothPsd[k]; noisePsd[k] = smoothPsd[k] }
            minPsd[k] = min(minPsd[k], smoothPsd[k]); tmpMin[k] = min(tmpMin[k], smoothPsd[k])
            if (frames % minWindowFrames == 0) { minPsd[k] = tmpMin[k]; tmpMin[k] = smoothPsd[k] }
            // Bias-compensated noise estimate; slow upward drift lets the floor follow changing noise.
            val target = minPsd[k] * 1.5
            noisePsd[k] = if (target < noisePsd[k]) target else 0.97 * noisePsd[k] + 0.03 * target
            val nz = noisePsd[k] * overSub + 1e-12
            val post = p / nz
            val prio = 0.98 * prevCleanPsd[k] / nz + 0.02 * max(post - 1.0, 0.0)
            var g = prio / (1.0 + prio)
            g = max(g, gainFloor)
            g = 0.6 * g + 0.4 * prevGain[k] // temporal smoothing
            prevGain[k] = g
            prevCleanPsd[k] = g * g * p
            re[k] *= g; im[k] *= g
            if (k in 1 until frameSize / 2) { re[frameSize - k] = re[k]; im[frameSize - k] = -im[k] }
        }
        fft.transform(re, im, inverse = true)
        for (i in 0 until frameSize) outAccum[i] += re[i] * window[i]
        for (i in 0 until hop) pending.addLast(outAccum[i].toFloat())
        System.arraycopy(outAccum, hop, outAccum, 0, hop)
        java.util.Arrays.fill(outAccum, hop, frameSize, 0.0)
    }
}

/** RBJ biquad filter (direct form I). */
class Biquad private constructor(private val b0: Double, private val b1: Double, private val b2: Double, private val a1: Double, private val a2: Double) {
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }

    fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }

    companion object {
        private fun make(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) = Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

        fun highPass(fs: Int, f: Double, q: Double = 0.707): Biquad {
            val w = 2 * PI * f / fs; val a = sin(w) / (2 * q); val c = cos(w)
            return make((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + a, -2 * c, 1 - a)
        }

        fun lowPass(fs: Int, f: Double, q: Double = 0.707): Biquad {
            val w = 2 * PI * f / fs; val a = sin(w) / (2 * q); val c = cos(w)
            return make((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + a, -2 * c, 1 - a)
        }

        fun peaking(fs: Int, f: Double, q: Double, gainDb: Double): Biquad {
            val A = 10.0.pow(gainDb / 40); val w = 2 * PI * f / fs; val a = sin(w) / (2 * q); val c = cos(w)
            return make(1 + a * A, -2 * c, 1 - a * A, 1 + a / A, -2 * c, 1 - a / A)
        }

        fun highShelf(fs: Int, f: Double, gainDb: Double): Biquad {
            val A = 10.0.pow(gainDb / 40); val w = 2 * PI * f / fs; val c = cos(w); val s = sin(w)
            val alpha = s / 2 * sqrt(2.0); val sq = 2 * sqrt(A) * alpha
            return make(A * ((A + 1) + (A - 1) * c + sq), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sq),
                (A + 1) - (A - 1) * c + sq, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sq)
        }
    }
}

/** Feed-forward RMS compressor with soft knee and make-up gain. */
class Compressor(fs: Int, private val thresholdDb: Double = -22.0, private val ratio: Double = 3.0, attackMs: Double = 8.0, releaseMs: Double = 140.0, private val makeupDb: Double = 4.0, private val kneeDb: Double = 6.0) {
    private val att = exp(-1.0 / (fs * attackMs / 1000))
    private val rel = exp(-1.0 / (fs * releaseMs / 1000))
    private var env = 0.0
    private var gainDbState = 0.0

    fun gainFor(x: Double): Double {
        val sq = x * x
        env = if (sq > env) att * env + (1 - att) * sq else rel * env + (1 - rel) * sq
        val levelDb = 10 * log10(env + 1e-12)
        val over = levelDb - thresholdDb
        val reduction = when {
            over <= -kneeDb / 2 -> 0.0
            over >= kneeDb / 2 -> over * (1 - 1 / ratio)
            else -> (1 - 1 / ratio) * (over + kneeDb / 2).pow(2) / (2 * kneeDb)
        }
        gainDbState = 0.9 * gainDbState + 0.1 * (-reduction)
        return 10.0.pow((gainDbState + makeupDb) / 20)
    }
}

/** Fast-attack, smooth-release peak limiter that guarantees |y| <= ceiling. */
class Limiter(fs: Int, ceilingDb: Double = -1.0, releaseMs: Double = 80.0) {
    private val ceiling = 10.0.pow(ceilingDb / 20)
    private val rel = exp(-1.0 / (fs * releaseMs / 1000))
    private var gain = 1.0

    fun process(x: Double): Double {
        val peak = abs(x)
        val needed = if (peak * gain > ceiling) ceiling / peak else 1.0
        gain = if (needed < gain) needed else rel * gain + (1 - rel) * min(1.0, needed)
        val y = x * gain
        return y.coerceIn(-ceiling, ceiling)
    }
}

/**
 * Piecewise gain automation on the reel timeline (sampled every [hopMs]): used for music fades
 * and speech-driven ducking. Pure data — computed before rendering so preview and export match.
 */
class GainEnvelope(val hopMs: Int, val gains: FloatArray) {
    fun at(ms: Double): Float {
        if (gains.isEmpty()) return 1f
        val pos = ms / hopMs
        val i = pos.toInt().coerceIn(0, gains.size - 1)
        val j = (i + 1).coerceAtMost(gains.size - 1)
        val f = (pos - i).toFloat().coerceIn(0f, 1f)
        return gains[i] + (gains[j] - gains[i]) * f
    }

    companion object {
        /**
         * Builds a music gain envelope: base volume, fade in/out, and ducking during speech with
         * smooth attack/release so the music never pumps.
         */
        fun music(totalMs: Long, volume: Float, fadeInMs: Long, fadeOutMs: Long, speech: List<LongRange>, duck: Boolean, duckDepth: Float, hopMs: Int = 10): GainEnvelope {
            val n = (totalMs / hopMs + 1).toInt()
            val target = FloatArray(n) { 1f }
            if (duck) for (r in speech) {
                val a = (r.first / hopMs).toInt().coerceIn(0, n - 1); val b = (r.last / hopMs).toInt().coerceIn(0, n - 1)
                for (i in a..b) target[i] = 1f - duckDepth.coerceIn(0f, 0.95f)
            }
            // Smooth: attack 120 ms (towards duck), release 450 ms (back up) — both directions symmetric look-ahead.
            val sm = FloatArray(n)
            var g = target.firstOrNull() ?: 1f
            val att = exp(-hopMs / 120.0).toFloat(); val rel = exp(-hopMs / 450.0).toFloat()
            for (i in 0 until n) { val t = target[i]; g = if (t < g) att * g + (1 - att) * t else rel * g + (1 - rel) * t; sm[i] = g }
            // Look-ahead: start ducking slightly before speech by taking a backward pass minimum.
            var back = sm.last()
            for (i in n - 1 downTo 0) { back = if (sm[i] < back) sm[i] else att * back + (1 - att) * sm[i]; sm[i] = min(sm[i], back) }
            for (i in 0 until n) {
                val t = i.toLong() * hopMs
                var fade = 1f
                if (fadeInMs > 0 && t < fadeInMs) fade = min(fade, (t.toFloat() / fadeInMs))
                if (fadeOutMs > 0 && t > totalMs - fadeOutMs) fade = min(fade, ((totalMs - t).toFloat() / fadeOutMs).coerceAtLeast(0f))
                sm[i] = sm[i] * volume * smoothStep(fade)
            }
            return GainEnvelope(hopMs, sm)
        }

        private fun smoothStep(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3 - 2 * c) }

        fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)
        fun linearToDb(v: Float): Float = 20f * log10(max(v, 1e-6f))

        /** Gain needed to bring a speech RMS level to [targetDb] dBFS, clamped to ±12 dB. */
        fun normalizationGain(speechRms: Float, targetDb: Float = -20f): Float {
            if (speechRms <= 1e-5f) return 1f
            val diff = (targetDb - linearToDb(speechRms)).coerceIn(-12f, 12f)
            return dbToLinear(diff)
        }

        @Suppress("unused")
        private fun lnSafe(x: Double) = ln(max(x, 1e-12))
    }
}
