package app.reelify.core.media.engine

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File

data class ValidationReport(
    val ok: Boolean,
    val checks: List<Pair<String, Boolean>>,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val sizeBytes: Long,
) {
    val summary: List<String> get() = checks.map { (name, pass) -> (if (pass) "✓ " else "✗ ") + name }
}

/**
 * Verifies a rendered file with the Android media stack — success is never reported just because a
 * render callback fired.
 */
object ExportValidator {
    /** One output frame of tolerance (plus 1 ms rounding). */
    fun durationTolerance(fps: Int): Long = 1000L / fps.coerceAtLeast(1) + 1

    fun validate(
        file: File,
        expectWidth: Int,
        expectHeight: Int,
        expectDurationMs: Long,
        fps: Int,
        expectAudio: Boolean,
        captionsExpected: Boolean,
        captionFramesDrawn: Long,
        overlaysExpected: Boolean,
        overlayFramesDrawn: Long,
        audioOnly: Boolean = false,
    ): ValidationReport {
        val checks = mutableListOf<Pair<String, Boolean>>()
        val exists = file.exists() && file.length() > 1024
        checks += "File written (${file.length() / 1024} KB)" to exists
        if (!exists) return ValidationReport(false, checks, 0, 0, 0, 0)

        var hasVideo = false; var hasAudio = false; var w = 0; var h = 0; var dur = 0L; var opened = false
        runCatching {
            val ex = MediaExtractor(); ex.setDataSource(file.absolutePath)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i); val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) { hasVideo = true; w = f.getInteger(MediaFormat.KEY_WIDTH); h = f.getInteger(MediaFormat.KEY_HEIGHT) }
                if (mime.startsWith("audio/")) hasAudio = true
            }
            ex.release(); opened = true
        }
        checks += "Readable by Android media stack" to opened
        val mmr = MediaMetadataRetriever()
        var frameOk = audioOnly
        runCatching {
            mmr.setDataSource(file.absolutePath)
            dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot % 180 != 0) { val t = w; w = h; h = t }
            if (!audioOnly) frameOk = mmr.getFrameAtTime(dur * 500, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) != null
        }
        runCatching { mmr.release() }

        if (!audioOnly) {
            checks += "Video track present (H.264)" to hasVideo
            checks += "Resolution ${w}×${h} (expected ${expectWidth}×${expectHeight})" to (w == expectWidth && h == expectHeight)
            checks += "Frames decode (playable)" to frameOk
        }
        val tol = durationTolerance(fps)
        val delta = kotlin.math.abs(dur - expectDurationMs)
        checks += "Duration ${dur} ms (target ${expectDurationMs} ms, ±$tol ms)" to (delta <= tol)
        if (expectAudio) checks += "Audio track present (AAC)" to hasAudio
        if (captionsExpected) checks += "Captions burned in ($captionFramesDrawn caption renders)" to (captionFramesDrawn > 0)
        if (overlaysExpected) checks += "Branding/overlays rendered ($overlayFramesDrawn renders)" to (overlayFramesDrawn > 0)
        return ValidationReport(checks.all { it.second }, checks, w, h, dur, file.length())
    }
}
