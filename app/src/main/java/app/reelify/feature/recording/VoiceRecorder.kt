package app.reelify.feature.recording

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/** Voiceover recorder (AAC/M4A, 48 kHz). Recording is never auto-stopped at the section length. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var file: File? = null; private set
    private var startedAt = 0L

    fun start(out: File) {
        stop()
        file = out
        recorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(48_000)
            setAudioEncodingBitRate(160_000)
            setAudioChannels(1)
            setOutputFile(out.absolutePath)
            prepare()
            start()
        }
        startedAt = System.currentTimeMillis()
    }

    fun amplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
    fun elapsedMs(): Long = if (recorder != null) System.currentTimeMillis() - startedAt else 0

    /** Returns the recorded duration in ms, or -1 when nothing usable was recorded. */
    fun stop(): Long {
        val r = recorder ?: return -1
        recorder = null
        val d = System.currentTimeMillis() - startedAt
        return try { r.stop(); d } catch (e: RuntimeException) { file?.delete(); -1 } finally { r.release() }
    }
}
