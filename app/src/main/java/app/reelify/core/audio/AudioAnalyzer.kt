package app.reelify.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/** RMS loudness envelope with a fixed hop (default 10 ms). Values are linear 0..1. */
class Envelope(val hopMs: Int, val values: FloatArray, val startMs: Long = 0) {
    val durationMs: Long get() = values.size.toLong() * hopMs
    fun at(ms: Long): Float {
        val i = ((ms - startMs) / hopMs).toInt()
        return if (i in values.indices) values[i] else 0f
    }
}

/** Decodes compressed audio to PCM with MediaCodec and reduces it to a loudness envelope. */
class AudioAnalyzer(private val context: Context) {

    suspend fun envelope(uri: Uri, startMs: Long = 0, endMs: Long = Long.MAX_VALUE, hopMs: Int = 10): Envelope? = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            val out = ArrayList<Float>(4096)
            var acc = 0.0
            var accCount = 0
            var hopSamples = sampleRate * hopMs / 1000
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()
            var firstOutTimeUs = -1L
            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        val t = extractor.sampleTime
                        if (size < 0 || t > endMs * 1000) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, t, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 5_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        hopSamples = sampleRate * hopMs / 1000
                    }
                    outIdx >= 0 -> {
                        if (info.size > 0 && info.presentationTimeUs >= startMs * 1000) {
                            if (firstOutTimeUs < 0) firstOutTimeUs = info.presentationTimeUs
                            val bb = codec.getOutputBuffer(outIdx)!!.order(ByteOrder.nativeOrder())
                            bb.position(info.offset); bb.limit(info.offset + info.size)
                            val shorts = bb.asShortBuffer()
                            val frames = shorts.remaining() / channels
                            for (f in 0 until frames) {
                                var s = 0f
                                for (c in 0 until channels) s += shorts.get() / 32768f
                                s /= channels
                                acc += (s * s).toDouble(); accCount++
                                if (accCount >= hopSamples) {
                                    out.add(sqrt(acc / accCount).toFloat()); acc = 0.0; accCount = 0
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            Envelope(hopMs, out.toFloatArray(), if (firstOutTimeUs >= 0) firstOutTimeUs / 1000 else startMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}

data class TrimSuggestion(val startMs: Long, val endMs: Long, val reason: String)

/** Pure speech/silence heuristics on an [Envelope]; unit-tested on the JVM. */
object SpeechAnalysis {
    /** Adaptive threshold: noise floor (20th percentile) scaled, bounded to sensible dBFS levels. */
    fun threshold(env: Envelope): Float {
        if (env.values.isEmpty()) return 0.02f
        val sorted = env.values.sorted()
        val floor = sorted[(sorted.size * 0.2).toInt().coerceIn(0, sorted.size - 1)]
        val peak = sorted[(sorted.size * 0.95).toInt().coerceIn(0, sorted.size - 1)]
        return (floor + (peak - floor) * 0.18f).coerceIn(0.008f, 0.12f)
    }

    /** Returns [start,end) ms ranges of speech, merging short gaps and dropping blips. */
    fun speechRegions(env: Envelope, minGapMs: Int = 300, minSpeechMs: Int = 120): List<LongRange> {
        val th = threshold(env)
        val raw = mutableListOf<LongRange>()
        var start = -1
        env.values.forEachIndexed { i, v ->
            if (v >= th && start < 0) start = i
            if (v < th && start >= 0) { raw += (start.toLong() * env.hopMs)..(i.toLong() * env.hopMs); start = -1 }
        }
        if (start >= 0) raw += (start.toLong() * env.hopMs)..(env.values.size.toLong() * env.hopMs)
        val merged = mutableListOf<LongRange>()
        for (r in raw) {
            val last = merged.lastOrNull()
            if (last != null && r.first - last.last <= minGapMs) merged[merged.size - 1] = last.first..r.last else merged += r
        }
        return merged.filter { it.last - it.first >= minSpeechMs }.map { (it.first + env.startMs)..(it.last + env.startMs) }
    }

    /**
     * Suggests a usable range of about [targetMs] in a take: starts just before the first speech,
     * and ends at the pause closest to the target so dialogue is not cut mid-sentence.
     */
    fun suggestTrim(env: Envelope, sourceDurationMs: Long, targetMs: Long, preRollMs: Long = 150, tailMs: Long = 250): TrimSuggestion {
        val regions = speechRegions(env)
        if (regions.isEmpty()) {
            val end = minOf(sourceDurationMs, targetMs)
            return TrimSuggestion(0, end, "No clear speech detected — kept the beginning.")
        }
        val start = (regions.first().first - preRollMs).coerceAtLeast(0)
        val idealEnd = start + targetMs
        // candidate cut points: end of each speech region (+tail)
        val candidates = regions.map { (it.last + tailMs).coerceAtMost(sourceDurationMs) }
        val fitting = candidates.filter { it <= idealEnd && it > start + targetMs / 3 }
        val end = when {
            fitting.isNotEmpty() -> fitting.max()
            else -> minOf(idealEnd, sourceDurationMs)
        }
        val reason = if (fitting.isNotEmpty()) "Starts at your first words and ends on a natural pause." else "Speech runs past the section length; ends at the time limit — consider a shorter delivery."
        return TrimSuggestion(start, end.coerceAtLeast(start + 500).coerceAtMost(sourceDurationMs), reason)
    }
}
