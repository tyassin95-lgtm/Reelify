package app.reelify.core.media.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import app.reelify.core.audio.Biquad
import app.reelify.core.audio.Compressor
import app.reelify.core.audio.GainEnvelope
import app.reelify.core.audio.Limiter
import app.reelify.core.audio.SpectralDenoiser
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Shared PCM plumbing: converts 16-bit / float interleaved buffers to per-sample callbacks. */
abstract class PcmProcessor : BaseAudioProcessor() {
    protected var sampleRate = 0
    protected var channels = 0
    private var encoding = C.ENCODING_INVALID
    protected var framesProcessed = 0L
    protected var streamOffsetUs = 0L

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        onFormat()
        return inputAudioFormat
    }

    protected open fun onFormat() {}

    /** Process one interleaved frame in place (values -1..1). */
    protected abstract fun processFrame(frame: FloatArray)

    private var frameBuf = FloatArray(2)

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameBytes = bytesPerSample * channels
        val frames = inputBuffer.remaining() / frameBytes
        if (frames == 0) return
        if (frameBuf.size != channels) frameBuf = FloatArray(channels)
        val out = replaceOutputBuffer(frames * frameBytes)
        inputBuffer.order(ByteOrder.nativeOrder())
        repeat(frames) {
            for (c in 0 until channels) {
                frameBuf[c] = if (encoding == C.ENCODING_PCM_FLOAT) inputBuffer.float else inputBuffer.short / 32768f
            }
            processFrame(frameBuf)
            for (c in 0 until channels) {
                val v = frameBuf[c].coerceIn(-1f, 1f)
                if (encoding == C.ENCODING_PCM_FLOAT) out.putFloat(v) else out.putShort((v * 32767f).toInt().toShort())
            }
            framesProcessed++
        }
        out.flip()
    }

    /** Position of the next frame on the item's own timeline, in ms. */
    protected fun positionMs(): Double = streamOffsetUs / 1000.0 + framesProcessed * 1000.0 / sampleRate

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        framesProcessed = 0
        streamOffsetUs = streamMetadata.positionOffsetUs
        onReset2()
    }

    override fun onReset() {
        framesProcessed = 0
        streamOffsetUs = 0
        onReset2()
    }

    protected open fun onReset2() {}
}

/**
 * Dialogue chain: high-pass (rumble) → spectral noise reduction → speech EQ → compression → gain.
 * Denoiser latency is compensated (leading samples dropped, tail flushed at end-of-stream) so
 * audio stays frame-accurately in sync with video.
 */
class VoiceChainProcessor(
    private val noiseStrength: Float,
    private val speechEq: Boolean,
    private val compress: Boolean,
    private val gain: Float,
) : BaseAudioProcessor() {
    private var sampleRate = 0
    private var channels = 0
    private var encoding = C.ENCODING_INVALID
    private var denoisers: Array<SpectralDenoiser> = emptyArray()
    private var hpf: Array<Biquad> = emptyArray()
    private var eqLow: Array<Biquad> = emptyArray()
    private var eqPresence: Array<Biquad> = emptyArray()
    private var eqAir: Array<Biquad> = emptyArray()
    private var comps: Array<Compressor> = emptyArray()
    private var toDrop = 0
    private var latency = 0

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        build()
        return inputAudioFormat
    }

    private fun build() {
        val fs = sampleRate
        denoisers = Array(channels) { SpectralDenoiser(fs, noiseStrength) }
        hpf = Array(channels) { Biquad.highPass(fs, if (noiseStrength > 0.7f) 100.0 else 80.0) }
        eqLow = Array(channels) { Biquad.peaking(fs, 280.0, 1.0, if (speechEq) -2.0 else 0.0) }
        eqPresence = Array(channels) { Biquad.peaking(fs, 3200.0, 0.9, if (speechEq) 2.5 else 0.0) }
        eqAir = Array(channels) { Biquad.highShelf(fs, 9000.0, if (speechEq) 1.5 else 0.0) }
        comps = Array(channels) { Compressor(fs) }
        latency = denoisers.firstOrNull()?.latency ?: 0
        toDrop = latency
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bps = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frames = inputBuffer.remaining() / (bps * channels)
        if (frames == 0) return
        inputBuffer.order(ByteOrder.nativeOrder())
        val planes = Array(channels) { FloatArray(frames) }
        for (f in 0 until frames) for (c in 0 until channels) {
            planes[c][f] = if (encoding == C.ENCODING_PCM_FLOAT) inputBuffer.float else inputBuffer.short / 32768f
        }
        emit(planes, frames)
    }

    private fun emit(planes: Array<FloatArray>, frames: Int) {
        val processed = Array(channels) { c ->
            val p = planes[c]
            for (i in 0 until frames) p[i] = hpf[c].process(p[i].toDouble()).toFloat()
            val d = denoisers[c].process(p, frames)
            for (i in 0 until frames) {
                var v = d[i].toDouble()
                v = eqLow[c].process(v); v = eqPresence[c].process(v); v = eqAir[c].process(v)
                if (compress) v *= comps[c].gainFor(v)
                d[i] = (v * gain).toFloat()
            }
            d
        }
        val drop = minOf(toDrop, frames)
        toDrop -= drop
        val outFrames = frames - drop
        if (outFrames <= 0) return
        val bps = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val out = replaceOutputBuffer(outFrames * channels * bps)
        for (i in drop until frames) for (c in 0 until channels) {
            val v = processed[c][i].coerceIn(-1f, 1f)
            if (encoding == C.ENCODING_PCM_FLOAT) out.putFloat(v) else out.putShort((v * 32767f).toInt().toShort())
        }
        out.flip()
    }

    override fun onQueueEndOfStream() {
        // Flush the denoiser tail so the item keeps its exact length.
        if (latency > 0 && channels > 0) emit(Array(channels) { FloatArray(latency) }, latency)
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) { if (sampleRate > 0) build() }
    override fun onReset() { if (sampleRate > 0) build() }
}

/** Applies a precomputed [GainEnvelope] keyed by the item's position + a constant offset (ms on the reel timeline). */
class EnvelopeGainProcessor(private val envelope: GainEnvelope, private val timelineOffsetMs: Long) : PcmProcessor() {
    override fun processFrame(frame: FloatArray) {
        val g = envelope.at(timelineOffsetMs + positionMs())
        for (i in frame.indices) frame[i] *= g
    }
}

/** Constant gain with short fades at item edges to avoid clicks at cut points. */
class ClipGainProcessor(private val gain: Float, private val durationMs: Long, private val edgeFadeMs: Double = 12.0) : PcmProcessor() {
    override fun processFrame(frame: FloatArray) {
        val t = positionMs()
        var g = gain
        if (t < edgeFadeMs) g *= (t / edgeFadeMs).toFloat()
        val remain = durationMs - t
        if (durationMs > 0 && remain < edgeFadeMs) g *= (remain / edgeFadeMs).toFloat().coerceAtLeast(0f)
        for (i in frame.indices) frame[i] *= g
    }
}

/** Master bus: gentle make-up and a true peak-safe limiter to prevent clipping after mixing. */
class MasterLimiterProcessor(private val ceilingDb: Double = -1.0) : PcmProcessor() {
    private var limiters: Array<Limiter> = emptyArray()
    override fun onFormat() { limiters = Array(channels) { Limiter(sampleRate, ceilingDb) } }
    override fun onReset2() { if (sampleRate > 0) onFormat() }
    override fun processFrame(frame: FloatArray) {
        // Linked stereo limiting keeps the image stable: apply the minimum gain across channels.
        for (c in frame.indices) frame[c] = limiters[c].process(frame[c].toDouble()).toFloat()
    }
}
