package app.reelify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import app.reelify.core.media.engine.TransformerRunner
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

/** Programmatically generated test media (no binary fixtures checked in). */
object FixtureMedia {
    fun image(dir: File, name: String, color: Int, label: String, w: Int = 720, h: Int = 1280): File {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(color)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; textSize = 64f }
        c.drawCircle(w / 2f, h * 0.35f, w * 0.18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.rgb(224, 172, 150) }) // face-like disc
        c.drawText(label, 40f, h * 0.8f, p)
        return File(dir, "$name.png").apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    /** Mono 48 kHz WAV of syllable-like tone bursts separated by pauses (speech-like envelope) + light noise. */
    fun speechWav(dir: File, name: String, seconds: Double): File {
        val sr = 48_000; val n = (seconds * sr).toInt()
        val rnd = java.util.Random(7)
        val pcm = ShortArray(n) { i ->
            val t = i.toDouble() / sr
            val word = (t * 3.0).toInt()
            val inPause = (t % 2.6) > 2.2
            val env = if (inPause || t < 0.4) 0.0 else 0.5 * (1 - kotlin.math.cos(2 * PI * ((t * 3.0) % 1.0)))
            val f0 = 160.0 + 30 * sin(word.toDouble())
            val v = env * (0.35 * sin(2 * PI * f0 * t) + 0.15 * sin(2 * PI * 2 * f0 * t) + 0.08 * sin(2 * PI * 3 * f0 * t)) + 0.01 * rnd.nextGaussian()
            (v.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        val f = File(dir, "$name.wav")
        DataOutputStream(java.io.BufferedOutputStream(FileOutputStream(f), 1 shl 16)).use { o ->
            fun le32(v: Int) = o.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun le16(v: Int) = o.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            o.writeBytes("RIFF"); le32(36 + n * 2); o.writeBytes("WAVEfmt "); le32(16); le16(1); le16(1); le32(sr); le32(sr * 2); le16(2); le16(16)
            o.writeBytes("data"); le32(n * 2)
            pcm.forEach { le16(it.toInt()) }
        }
        return f
    }

    /** Builds a real MP4 "recorded clip" (H.264 video from an image + AAC dialogue track) using Transformer. */
    suspend fun clip(context: Context, dir: File, name: String, color: Int, seconds: Double, w: Int = 720, h: Int = 1280): File {
        File(dir, "$name.mp4").takeIf { it.length() > 10_000 }?.let { return it } // reuse across runs
        val img = image(dir, "${name}_img", color, name, w, h)
        val wav = speechWav(dir, "${name}_vo", seconds)
        val us = (seconds * 1_000_000).toLong()
        val video = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
            .addItem(EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(img)).setImageDurationMs((seconds * 1000).toLong()).build()).setDurationUs(us).setFrameRate(30).build()).build()
        val audio = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO)).addItem(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(wav))).build()).build()
        val out = File(dir, "$name.mp4")
        TransformerRunner(context).export(Composition.Builder(video, audio).build(), out, 4_000_000, audioOnly = false) {}
        return out
    }
}
