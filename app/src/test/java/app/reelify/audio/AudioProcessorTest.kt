package app.reelify.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import app.reelify.core.media.engine.ClipGainProcessor
import app.reelify.core.media.engine.MasterLimiterProcessor
import app.reelify.core.media.engine.VoiceChainProcessor
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class AudioProcessorTest {
    private val fmt = AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)

    private fun pcm(frames: Int): ByteBuffer {
        val b = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        repeat(frames) { i -> val v = (8000 * sin(2 * PI * 300 * i / 48_000.0)).toInt().toShort(); b.putShort(v); b.putShort(v) }
        b.flip(); return b
    }

    /** Drives a processor like Media3's pipeline, including REPEATED queueEndOfStream calls. */
    private fun run(p: AudioProcessor, totalFrames: Int, chunk: Int = 1024): Int {
        p.configure(fmt); p.flush(AudioProcessor.StreamMetadata.DEFAULT)
        var out = 0; var fed = 0
        while (fed < totalFrames) {
            val n = minOf(chunk, totalFrames - fed)
            val input = pcm(n); p.queueInput(input); fed += n
            out += p.output.remaining() / 4
        }
        var guard = 0
        while (!p.isEnded) {
            p.queueEndOfStream() // the pipeline calls this again on every pass until isEnded()
            out += p.output.remaining() / 4
            check(++guard < 100) { "processor never ended" }
        }
        return out
    }

    @Test fun `voice chain keeps exact length and ends despite repeated end-of-stream`() {
        for (strength in listOf(0f, 0.35f, 0.85f)) {
            val frames = 48_000 * 2 + 517
            assertThat(run(VoiceChainProcessor(strength, speechEq = true, compress = true, gain = 1f), frames)).isEqualTo(frames)
        }
    }

    @Test fun `gain and limiter processors are length preserving`() {
        assertThat(run(ClipGainProcessor(0.8f, 2000), 30_000)).isEqualTo(30_000)
        assertThat(run(MasterLimiterProcessor(), 30_000)).isEqualTo(30_000)
    }
}
