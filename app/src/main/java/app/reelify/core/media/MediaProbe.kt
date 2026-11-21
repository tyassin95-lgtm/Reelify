package app.reelify.core.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import app.reelify.core.model.MediaKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaInfo(
    val kind: MediaKind,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val frameRate: Float,
    val hasAudio: Boolean,
    val hasVideo: Boolean,
    val videoMime: String?,
    val audioMime: String?,
)

class UnsupportedMediaException(message: String) : Exception(message)

/** Probes metadata and verifies that the device can actually decode the media before it is accepted. */
class MediaProbe(private val context: Context) {

    suspend fun probe(uri: Uri, mimeHint: String? = null): MediaInfo = withContext(Dispatchers.IO) {
        val mime = mimeHint ?: context.contentResolver.getType(uri) ?: guessMime(uri)
        if (mime?.startsWith("image/") == true) probeImage(uri) else probeAv(uri)
    }

    /**
     * Alternate metadata duration fallback when [probe] cannot determine container duration or when
     * an existing clip lost its metadata. Returns 0L if genuinely unavailable (never fabricates).
     */
    suspend fun probeDurationFallback(uri: Uri): Long = withContext(Dispatchers.IO) {
        val mmr = MediaMetadataRetriever()
        val fromRetriever = try {
            mmr.setDataSource(context, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0L } ?: 0L
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { mmr.release() }
        }
        if (fromRetriever > 0L) return@withContext fromRetriever

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var best = 0L
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val us = f.longOrNull(MediaFormat.KEY_DURATION) ?: 0L
                if (us > 0L) best = maxOf(best, us / 1000L)
            }
            best
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun guessMime(uri: Uri): String? {
        val p = uri.toString().lowercase()
        return when {
            p.endsWith(".jpg") || p.endsWith(".jpeg") -> "image/jpeg"
            p.endsWith(".png") -> "image/png"
            p.endsWith(".webp") -> "image/webp"
            p.endsWith(".heic") || p.endsWith(".heif") -> "image/heif"
            p.endsWith(".m4a") || p.endsWith(".mp3") || p.endsWith(".wav") || p.endsWith(".aac") || p.endsWith(".ogg") || p.endsWith(".flac") -> "audio/*"
            else -> null
        }
    }

    private fun probeImage(uri: Uri): MediaInfo {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) throw UnsupportedMediaException("This image format can't be decoded on this device.")
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        return MediaInfo(MediaKind.IMAGE, 0, opts.outWidth, opts.outHeight, rotation, 0f, false, true, opts.outMimeType, null)
    }

    private fun probeAv(uri: Uri): MediaInfo {
        val extractor = MediaExtractor()
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("video/") && videoFormat == null) videoFormat = f
                if (m.startsWith("audio/") && audioFormat == null) audioFormat = f
            }
        } catch (e: Exception) {
            throw UnsupportedMediaException("This file couldn't be read. It may be damaged or use an unsupported container.")
        } finally {
            extractor.release()
        }
        if (videoFormat == null && audioFormat == null) throw UnsupportedMediaException("No playable audio or video tracks were found.")

        val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        videoFormat?.let { vf ->
            val probe = MediaFormat(vf).apply { if (containsKey(MediaFormat.KEY_FRAME_RATE)) setString(MediaFormat.KEY_FRAME_RATE, null) }
            if (codecs.findDecoderForFormat(probe) == null && codecs.findDecoderForFormat(stripProfile(vf)) == null) {
                throw UnsupportedMediaException("This device has no decoder for ${vf.getString(MediaFormat.KEY_MIME)} at this resolution. Try exporting the clip as H.264 MP4.")
            }
        }
        audioFormat?.let { af ->
            if (codecs.findDecoderForFormat(stripProfile(af)) == null) audioFormat = null // video still usable; audio ignored
        }

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: videoFormat?.longOrNull(MediaFormat.KEY_DURATION)?.div(1000)
                ?: audioFormat?.longOrNull(MediaFormat.KEY_DURATION)?.div(1000) ?: 0L
            if (dur <= 0) throw UnsupportedMediaException("The media has no duration.")
            val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: videoFormat?.intOrNull(MediaFormat.KEY_WIDTH) ?: 0
            val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: videoFormat?.intOrNull(MediaFormat.KEY_HEIGHT) ?: 0
            val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val fps = videoFormat?.let { it.intOrNull(MediaFormat.KEY_FRAME_RATE)?.toFloat() ?: it.floatOrNull(MediaFormat.KEY_FRAME_RATE) }
                ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f
            return MediaInfo(
                kind = if (videoFormat != null) MediaKind.VIDEO else MediaKind.AUDIO,
                durationMs = dur, width = w, height = h, rotation = rot, frameRate = fps,
                hasAudio = audioFormat != null, hasVideo = videoFormat != null,
                videoMime = videoFormat?.getString(MediaFormat.KEY_MIME), audioMime = audioFormat?.getString(MediaFormat.KEY_MIME),
            )
        } catch (e: UnsupportedMediaException) {
            throw e
        } catch (e: Exception) {
            throw UnsupportedMediaException("Couldn't read media details from this file.")
        } finally {
            mmr.release()
        }
    }

    private fun stripProfile(f: MediaFormat): MediaFormat = MediaFormat().apply {
        setString(MediaFormat.KEY_MIME, f.getString(MediaFormat.KEY_MIME))
        f.intOrNull(MediaFormat.KEY_WIDTH)?.let { setInteger(MediaFormat.KEY_WIDTH, it) }
        f.intOrNull(MediaFormat.KEY_HEIGHT)?.let { setInteger(MediaFormat.KEY_HEIGHT, it) }
        f.intOrNull(MediaFormat.KEY_SAMPLE_RATE)?.let { setInteger(MediaFormat.KEY_SAMPLE_RATE, it) }
        f.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)?.let { setInteger(MediaFormat.KEY_CHANNEL_COUNT, it) }
    }
}

internal fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
internal fun MediaFormat.longOrNull(key: String): Long? = if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null
internal fun MediaFormat.floatOrNull(key: String): Float? = if (containsKey(key)) runCatching { getFloat(key) }.getOrNull() else null
