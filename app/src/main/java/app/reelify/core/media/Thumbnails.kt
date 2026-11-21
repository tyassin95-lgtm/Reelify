package app.reelify.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** Generates small JPEG thumbnails and filmstrip frames off the main thread. */
class Thumbnails(private val context: Context) {

    suspend fun videoFrame(uri: Uri, timeMs: Long, maxSize: Int = 360): Bitmap? = withContext(Dispatchers.IO) {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            mmr.getScaledFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSize, maxSize)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    suspend fun filmstrip(uri: Uri, startMs: Long, endMs: Long, count: Int, maxSize: Int = 160): List<Bitmap> = withContext(Dispatchers.IO) {
        val mmr = MediaMetadataRetriever()
        val out = mutableListOf<Bitmap>()
        try {
            mmr.setDataSource(context, uri)
            val span = (endMs - startMs).coerceAtLeast(1)
            for (i in 0 until count) {
                coroutineContext.ensureActive()
                val t = startMs + span * i / count.coerceAtLeast(1)
                mmr.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSize, maxSize)?.let(out::add)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            runCatching { mmr.release() }
        }
        out
    }

    suspend fun image(uri: Uri, maxSize: Int = 360, rotation: Int = 0): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null
            if (rotation == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 85): File = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        file
    }
}
