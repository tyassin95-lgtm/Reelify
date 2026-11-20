package app.reelify.core.media.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import app.reelify.core.media.Thumbnails
import app.reelify.core.model.FaceSample
import app.reelify.core.model.FaceTrack
import app.reelify.core.model.MediaClip
import app.reelify.core.model.MediaKind
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * On-device face tracking (ML Kit) for face-localized beauty in post. Samples the clip's trimmed
 * range every [stepMs] and stores the largest face per sample in display-normalized coordinates.
 * Nothing is uploaded anywhere.
 */
class FaceAnalyzer(private val context: Context, private val thumbnails: Thumbnails) {
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.12f)
                .build(),
        )
    }

    suspend fun analyze(clip: MediaClip, uri: android.net.Uri, stepMs: Long = 250, maxSamples: Int = 90): FaceTrack = withContext(Dispatchers.Default) {
        val samples = mutableListOf<FaceSample>()
        if (clip.kind == MediaKind.IMAGE) {
            thumbnails.image(uri, 720, clip.rotationDegrees)?.let { bmp -> detect(bmp)?.let { samples += sample(0, it, bmp) } }
            return@withContext FaceTrack(samples, System.currentTimeMillis())
        }
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            val start = clip.trimStartMs; val end = clip.trimEndMs.coerceAtLeast(start + 1)
            val step = maxOf(stepMs, (end - start) / maxSamples)
            var t = start
            while (t <= end) {
                coroutineContext.ensureActive()
                val raw = mmr.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 480, 480)
                if (raw != null) {
                    val bmp = orient(raw, clip)
                    detect(bmp)?.let { samples += sample(t, it, bmp) }
                }
                t += step
            }
        } catch (_: Exception) {
        } finally {
            runCatching { mmr.release() }
        }
        FaceTrack(samples, System.currentTimeMillis())
    }

    /** Ensures the frame is display-oriented (some devices return un-rotated frames). */
    private fun orient(bmp: Bitmap, clip: MediaClip): Bitmap {
        val wantLandscape = clip.displayWidth > clip.displayHeight
        val isLandscape = bmp.width > bmp.height
        if (clip.rotationDegrees % 180 != 0 && wantLandscape != isLandscape) {
            return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(clip.rotationDegrees.toFloat()) }, true)
        }
        return bmp
    }

    private fun detect(bmp: Bitmap): Face? = runCatching {
        Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0))).maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
    }.getOrNull()

    private fun sample(t: Long, f: Face, bmp: Bitmap): FaceSample {
        val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
        val b = f.boundingBox
        val le = f.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val re = f.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        return FaceSample(
            tMs = t, cx = b.exactCenterX() / w, cy = b.exactCenterY() / h, w = b.width() / w, h = b.height() / h,
            leftEyeX = le?.x?.div(w) ?: -1f, leftEyeY = le?.y?.div(h) ?: -1f,
            rightEyeX = re?.x?.div(w) ?: -1f, rightEyeY = re?.y?.div(h) ?: -1f,
        )
    }
}
