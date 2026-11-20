package app.reelify.core.media.engine

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RenderFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Runs a Media3 Transformer export as a cancellable suspend function with real progress
 * (Transformer.getProgress). Transformer is driven from the main looper as required; all heavy
 * work happens on Media3's internal threads.
 */
class TransformerRunner(private val context: Context) {

    suspend fun export(
        composition: Composition,
        output: File,
        videoBitrate: Int,
        audioOnly: Boolean,
        onProgress: (Int) -> Unit,
    ): ExportResult = withContext(Dispatchers.Main) {
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()
        coroutineScope {
            val builder = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
            if (!audioOnly) {
                builder.setVideoMimeType(MimeTypes.VIDEO_H264)
                builder.setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(videoBitrate).build())
                        .setEnableFallback(true)
                        .build(),
                )
            }
            var transformer: Transformer? = null
            val progressJob = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    delay(250)
                    val t = transformer ?: continue
                    if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                }
            }
            try {
                suspendCancellableCoroutine { cont ->
                    val t = builder.addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(exportResult)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (cont.isActive) cont.resumeWithException(RenderFailedException(describe(exportException), exportException))
                        }
                    }).build()
                    transformer = t
                    cont.invokeOnCancellation {
                        // Called on an arbitrary thread; Transformer must be accessed from the main looper.
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            runCatching { t.cancel() }
                            runCatching { output.delete() }
                        }
                    }
                    t.start(composition, output.absolutePath)
                }
            } finally {
                progressJob.cancel()
            }
        }
    }

    private fun describe(e: ExportException): String = when (e.errorCode) {
        ExportException.ERROR_CODE_IO_FILE_NOT_FOUND, ExportException.ERROR_CODE_IO_NO_PERMISSION -> "A source file couldn't be opened. Relink missing media and try again."
        ExportException.ERROR_CODE_DECODER_INIT_FAILED, ExportException.ERROR_CODE_DECODING_FAILED, ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "This device couldn't decode one of the clips. Try converting it to H.264 MP4."
        ExportException.ERROR_CODE_ENCODER_INIT_FAILED, ExportException.ERROR_CODE_ENCODING_FAILED, ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED ->
            "The video encoder failed. Try the Standard export quality."
        ExportException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED -> "Rendering effects failed on the GPU (${e.message}). Try disabling beauty or transitions."
        ExportException.ERROR_CODE_AUDIO_PROCESSING_FAILED -> "Audio processing failed (${e.message})."
        ExportException.ERROR_CODE_MUXING_FAILED -> "Writing the MP4 failed. Check free storage."
        else -> "Export failed: ${e.errorCodeName}"
    }
}
