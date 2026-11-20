package app.reelify.core.media.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import app.reelify.core.audio.GainEnvelope
import app.reelify.core.library.SoundLibrary
import app.reelify.core.model.BrandProfile
import app.reelify.core.model.CaptionSegment
import app.reelify.core.model.ClipRole
import app.reelify.core.model.MediaClip
import app.reelify.core.model.MediaKind
import app.reelify.core.model.ProjectBundle
import app.reelify.core.model.ShotType
import app.reelify.core.model.TransitionType
import com.google.common.collect.ImmutableList
import java.io.File

enum class RenderPurpose { EXPORT, PREVIEW, DIALOGUE }

/** Inputs prepared ahead of rendering (all deterministic so preview == export). */
data class RenderInputs(
    val bundle: ProjectBundle,
    val timeline: ResolvedTimeline,
    val captions: List<CaptionSegment>,
    val brand: BrandProfile?,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    /** Speech ranges on the reel timeline (word timings or energy analysis) for ducking. */
    val speechRanges: List<LongRange>,
    /** Per-clip loudness normalization gains. */
    val clipGains: Map<String, Float> = emptyMap(),
    val musicUri: Uri? = null,
    val musicDurationMs: Long = 0,
    val cacheDir: File,
)

class BuiltComposition(val composition: Composition, val overlayRenderer: ReelOverlayRenderer?, val durationMs: Long)

/**
 * Builds the Media3 [Composition] from a [ResolvedTimeline]:
 *  - sequence 0: video (one item per timeline segment, audio removed)
 *  - dialogue: audio-only, J/L-cut friendly (B-roll cutaways keep the presenter's audio)
 *  - music: audio-only with a precomputed fade/duck gain envelope
 *  - sfx: audio-only, cues placed with gaps
 * Composition effects: finishing pass (transitions/vignette/light) + caption & brand overlay,
 * and a master limiter on the mixed audio.
 */
class CompositionFactory(private val context: Context, private val fonts: ReelFonts) {

    fun build(inputs: RenderInputs, purpose: RenderPurpose): BuiltComposition {
        val p = inputs.bundle.project
        val e = p.edit
        val total = inputs.timeline.totalMs
        val sequences = mutableListOf<EditedMediaItemSequence>()
        var overlayRenderer: ReelOverlayRenderer? = null

        if (purpose != RenderPurpose.DIALOGUE) {
            sequences += videoSequence(inputs)
        }
        dialogueSequence(inputs)?.let { sequences += it }
        if (purpose != RenderPurpose.DIALOGUE) {
            musicSequence(inputs)?.let { sequences += it }
            sfxSequence(inputs)?.let { sequences += it }
        }

        val videoEffects = mutableListOf<Effect>()
        val audioEffects = mutableListOf<AudioProcessor>()
        if (purpose != RenderPurpose.DIALOGUE) {
            val transitions = transitionSpecs(inputs)
            videoEffects += FinishEffect(transitions, e.effects.vignette, e.effects.lightOverlay)
            val logo = inputs.brand?.logoPath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
            val model = overlayModel(inputs)
            overlayRenderer = ReelOverlayRenderer(model, fonts, logo)
            videoEffects += OverlayEffect(ImmutableList.of<androidx.media3.effect.TextureOverlay>(ReelBitmapOverlay(overlayRenderer)))
            if (e.audio.normalize) audioEffects += MasterLimiterProcessor()
        }
        val composition = Composition.Builder(sequences)
            .setEffects(Effects(audioEffects, videoEffects))
            .build()
        return BuiltComposition(composition, overlayRenderer, total)
    }

    fun overlayModel(inputs: RenderInputs): OverlayModel {
        val p = inputs.bundle.project
        val sectionMs = inputs.timeline.sectionMs
        val clinicalRanges = inputs.timeline.video.filter { it.clip?.isClinical == true || it.isCutaway }.map { it.timelineStartMs until it.timelineEndMs }
        val baRanges = inputs.bundle.sections.filter { it.shotType == ShotType.BEFORE_AFTER }.map { (sectionMs * (it.index - 1)) until (sectionMs * it.index) }
        return OverlayModel(
            width = inputs.width, height = inputs.height, language = p.language, totalMs = inputs.timeline.totalMs,
            captions = inputs.captions, captionSettings = p.edit.captions, brand = inputs.brand, branding = p.edit.branding,
            effects = p.edit.effects,
            sections = inputs.bundle.sections.map { s ->
                SectionOverlayInfo(s.index, sectionMs * (s.index - 1), sectionMs, s.onScreenText,
                    talkingHead = s.shotType == ShotType.TALKING_HEAD && inputs.bundle.clipsFor(s.id, ClipRole.PRIMARY).any { it.kind == MediaKind.VIDEO })
            },
            safe = p.edit.safeZone, clinicalRanges = clinicalRanges, beforeAfterRanges = baRanges,
        )
    }

    fun transitionSpecs(inputs: RenderInputs): List<TransitionSpec> {
        val t = inputs.bundle.project.edit.transitions
        val durUs = t.durationMs.coerceIn(150, 1200) * 1000
        return inputs.timeline.cutPoints().mapIndexedNotNull { i, (atMs, isSectionBoundary) ->
            val type = if (isSectionBoundary) {
                // boundary 0 = between sections 1 and 2, boundary 1 = between sections 2 and 3
                val incoming = inputs.timeline.video.first { it.timelineStartMs == atMs }.sectionIndex
                t.overrides[(incoming - 2).toString()] ?: t.defaultType
            } else t.overrides["${10 + i}"] ?: t.withinSection
            if (type == TransitionType.CUT) null else TransitionSpec(atMs * 1000, type, durUs)
        }
    }

    // ------------------------------------------------------------------ sequences

    private fun mediaItem(clip: MediaClip, startMs: Long, durMs: Long): MediaItem {
        val uri = uriOf(clip.uri)
        return if (clip.kind == MediaKind.IMAGE) {
            MediaItem.Builder().setUri(uri).setImageDurationMs(durMs).build()
        } else {
            MediaItem.Builder().setUri(uri)
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(startMs + durMs).build())
                .build()
        }
    }

    private fun videoSequence(inputs: RenderInputs): EditedMediaItemSequence {
        val e = inputs.bundle.project.edit
        val items = inputs.timeline.video.map { seg ->
            val durUs = seg.durationMs * 1000
            val clip = seg.clip
            if (clip == null) {
                val card = brandCard(inputs)
                EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(card)).setImageDurationMs(seg.durationMs).build())
                    .setDurationUs(durUs).setFrameRate(inputs.frameRate)
                    .setEffects(Effects(emptyList(), listOf(
                        Presentation.createForWidthAndHeight(inputs.width, inputs.height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
                        ReframeTransformation(1f, 0f, 0f, false, 0.05f, seg.timelineStartMs * 1000, durUs),
                    )))
                    .build()
            } else {
                val fx = mutableListOf<Effect>()
                val rot = clip.transform.rotation
                if (rot % 360 != 0) fx += ScaleAndRotateTransformation.Builder().setRotationDegrees(rot.toFloat()).build()
                fx += Presentation.createForWidthAndHeight(inputs.width, inputs.height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
                val params = LookMath.params(clip.beauty, e.color, allowBeauty = true)
                val faceTrack = clip.faceTrack
                val faceFn: ((Long) -> FaceUniform?)? = if (faceTrack != null && faceTrack.samples.isNotEmpty() && clip.kind == MediaKind.VIDEO) { tUs ->
                    val srcMs = seg.sourceStartMs + (tUs / 1000 - seg.timelineStartMs)
                    LookMath.interpolate(faceTrack, srcMs)?.let { LookMath.toUniform(it, clip.displayWidth, clip.displayHeight, rot, inputs.width, inputs.height) }
                } else if (faceTrack != null && faceTrack.samples.isNotEmpty()) { _ ->
                    LookMath.toUniform(faceTrack.samples.first(), clip.displayWidth, clip.displayHeight, rot, inputs.width, inputs.height)
                } else null
                if (!params.isIdentity) fx += BeautyColorEffect(params, faceFn)
                val motion = when {
                    clip.kind == MediaKind.IMAGE -> 0.08f
                    e.effects.pushIn && clip.role == ClipRole.PRIMARY -> 0.035f
                    else -> 0f
                }
                val reframe = ReframeTransformation(clip.transform.zoom, clip.transform.panX, clip.transform.panY, clip.transform.flipHorizontal, motion, seg.timelineStartMs * 1000, durUs)
                if (!reframe.isStatic) fx += reframe
                val builder = EditedMediaItem.Builder(mediaItem(clip, seg.sourceStartMs, seg.durationMs))
                    .setRemoveAudio(true)
                    .setEffects(Effects(emptyList(), fx))
                if (clip.kind == MediaKind.IMAGE) builder.setDurationUs(durUs).setFrameRate(inputs.frameRate)
                builder.build()
            }
        }
        return EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addItems(items).build()
    }

    private fun dialogueSequence(inputs: RenderInputs): EditedMediaItemSequence? {
        val segs = inputs.timeline.dialogue
        if (segs.isEmpty()) return null
        val a = inputs.bundle.project.edit.audio
        val b = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        var cursor = 0L
        for (seg in segs) {
            if (seg.timelineStartMs > cursor + 5) b.addGap((seg.timelineStartMs - cursor) * 1000)
            val start = maxOf(seg.timelineStartMs, cursor)
            val skip = start - seg.timelineStartMs
            val dur = seg.durationMs - skip
            if (dur < TimelineResolver.MIN_SEGMENT_MS) continue
            val gain = a.dialogueVolume * seg.clip.volume * (inputs.clipGains[seg.clip.id] ?: 1f)
            val processors = listOf<AudioProcessor>(
                VoiceChainProcessor(a.noiseReduction.strength, a.speechEq, a.compression, 1f),
                ClipGainProcessor(gain, dur),
            )
            b.addItem(
                EditedMediaItem.Builder(mediaItem(seg.clip, seg.sourceStartMs + skip, dur))
                    .setRemoveVideo(true)
                    .setEffects(Effects(processors, emptyList()))
                    .build(),
            )
            cursor = start + dur
        }
        return b.build()
    }

    private fun musicSequence(inputs: RenderInputs): EditedMediaItemSequence? {
        val m = inputs.bundle.project.edit.music
        val uri = inputs.musicUri ?: return null
        if (m.volume <= 0.001f) return null
        val total = inputs.timeline.totalMs
        val env = GainEnvelope.music(total, m.volume, m.fadeInMs, m.fadeOutMs, inputs.speechRanges, m.ducking, m.duckDepth)
        val b = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        val trackLen = (inputs.musicDurationMs - m.startOffsetMs).coerceAtLeast(1000)
        var t = 0L
        while (t < total) {
            val d = minOf(trackLen, total - t)
            val item = MediaItem.Builder().setUri(uri)
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(m.startOffsetMs).setEndPositionMs(m.startOffsetMs + d).build())
                .build()
            b.addItem(EditedMediaItem.Builder(item).setRemoveVideo(true).setEffects(Effects(listOf(EnvelopeGainProcessor(env, t)), emptyList())).build())
            t += d
        }
        return b.build()
    }

    private fun sfxSequence(inputs: RenderInputs): EditedMediaItemSequence? {
        val e = inputs.bundle.project.edit
        val cues = e.sfx.sortedBy { it.timeMs }
        if (cues.isEmpty() || e.audio.sfxVolume <= 0.001f) return null
        val b = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        var cursor = 0L
        var added = 0
        for (cue in cues) {
            val fxDef = SoundLibrary.effect(cue.sfxId) ?: continue
            val start = cue.timeMs.coerceIn(0, inputs.timeline.totalMs - 100)
            if (start < cursor) continue // skip overlapping cues
            val dur = minOf(fxDef.durationMs, inputs.timeline.totalMs - start)
            if (dur < 80) continue
            if (start > cursor) b.addGap((start - cursor) * 1000)
            val item = MediaItem.Builder().setUri(Uri.parse("asset:///${fxDef.assetPath}"))
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(dur).build()).build()
            b.addItem(EditedMediaItem.Builder(item).setEffects(Effects(listOf(ClipGainProcessor(cue.gain * e.audio.sfxVolume, dur, 4.0)), emptyList())).build())
            cursor = start + dur
            added++
        }
        return if (added > 0) b.build() else null
    }

    private fun uriOf(ref: String): Uri = if (ref.startsWith("content://") || ref.startsWith("file://") || ref.startsWith("asset://")) Uri.parse(ref) else Uri.fromFile(File(ref))

    // ------------------------------------------------------------------ brand card

    /** Generates (and caches) a branded background card used only when the user chose that fill. */
    fun brandCard(inputs: RenderInputs): File {
        val b = inputs.brand ?: BrandProfile()
        val f = File(inputs.cacheDir, "brand_card_${(b.hashCode() xor inputs.width).toUInt()}.png")
        if (f.exists()) return f
        val w = inputs.width; val h = inputs.height; val s = w / 1080f
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), (b.secondaryColor.toInt() and 0x00FFFFFF) or 0xFF000000.toInt(), 0xFF09090D.toInt(), Shader.TileMode.CLAMP)
        })
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = RadialGradient(w * 0.5f, h * 0.38f, w * 0.9f, (b.primaryColor.toInt() and 0x00FFFFFF) or 0x88000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        })
        val logo = b.logoPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
        var y = h * 0.36f
        if (logo != null) {
            val k = minOf(420f * s / logo.width, 260f * s / logo.height)
            val lw = logo.width * k; val lh = logo.height * k
            c.drawBitmap(logo, null, RectF((w - lw) / 2, y - lh / 2, (w + lw) / 2, y + lh / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            y += lh / 2 + 60f * s
        }
        val arabic = inputs.bundle.project.language.isRtl
        val title = listOf(b.clinicName, b.brandName).firstOrNull { it.isNotBlank() } ?: ""
        if (title.isNotBlank()) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.brand(b.font, true, arabic); textSize = 64f * s; color = b.textColor.toInt() }
            val lay = StaticLayout.Builder.obtain(title, 0, title.length, tp, (w * 0.8f).toInt()).setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).build()
            c.save(); c.translate(w * 0.1f, y); lay.draw(c); c.restore()
        }
        f.parentFile?.mkdirs()
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    companion object {
        const val OUTPUT_MIME = MimeTypes.VIDEO_H264
    }
}
