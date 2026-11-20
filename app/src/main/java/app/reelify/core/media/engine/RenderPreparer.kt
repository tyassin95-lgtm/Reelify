package app.reelify.core.media.engine

import android.content.Context
import android.net.Uri
import app.reelify.core.audio.AudioAnalyzer
import app.reelify.core.audio.GainEnvelope
import app.reelify.core.audio.SpeechAnalysis
import app.reelify.core.captions.CaptionBuilder
import app.reelify.core.captions.CaptionRemapper
import app.reelify.core.data.ProjectRepository
import app.reelify.core.data.SettingsRepository
import app.reelify.core.library.SoundLibrary
import app.reelify.core.media.MediaProbe
import app.reelify.core.model.CaptionSegment
import app.reelify.core.model.ExportQuality
import app.reelify.core.model.MediaKind
import app.reelify.core.model.ProjectBundle
import app.reelify.core.storage.MediaStorage
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Collects every input needed to build the composition, deterministically, for preview and export alike. */
class RenderPreparer(
    private val context: Context,
    private val projects: ProjectRepository,
    private val settings: SettingsRepository,
    private val storage: MediaStorage,
    private val probe: MediaProbe,
    private val analyzer: AudioAnalyzer,
) {
    private val gainCache = ConcurrentHashMap<String, Float>()
    private val musicDurationCache = ConcurrentHashMap<String, Long>()

    fun resolve(bundle: ProjectBundle): ResolvedTimeline = TimelineResolver.resolve(bundle) { storage.isAccessible(it.uri) }

    fun captionsFor(bundle: ProjectBundle, timeline: ResolvedTimeline): List<CaptionSegment> {
        val track = bundle.project.captions ?: return emptyList()
        val remapped = CaptionRemapper.remap(track.segments, timeline)
        return CaptionBuilder.applyEmphasis(remapped, bundle.project.edit.captions.highlightKeywords + bundle.sections.flatMap { it.captionEmphasis })
    }

    fun speechRanges(captions: List<CaptionSegment>, timeline: ResolvedTimeline): List<LongRange> {
        val words = captions.flatMap { it.words }
        if (words.isEmpty()) return timeline.dialogueRanges()
        val ranges = mutableListOf<LongRange>()
        for (w in words.sortedBy { it.startMs }) {
            val last = ranges.lastOrNull()
            if (last != null && w.startMs - last.last < 350) ranges[ranges.size - 1] = last.first..maxOf(last.last, w.endMs)
            else ranges += w.startMs..w.endMs
        }
        return ranges
    }

    /** Loudness normalization: per-clip gain bringing measured speech RMS to about -20 dBFS. */
    suspend fun clipGains(timeline: ResolvedTimeline): Map<String, Float> {
        val out = HashMap<String, Float>()
        for (seg in timeline.dialogue.distinctBy { it.clip.id }) {
            val c = seg.clip
            val key = "${c.uri}|${c.trimStartMs}|${c.trimEndMs}"
            val gain = gainCache[key] ?: run {
                val env = analyzer.envelope(storage.toUri(c.uri), c.trimStartMs, c.trimEndMs)
                val g = if (env == null) 1f else {
                    val speech = SpeechAnalysis.speechRegions(env)
                    val values = speech.flatMap { r -> ((r.first - env.startMs) / env.hopMs until (r.last - env.startMs) / env.hopMs).mapNotNull { env.values.getOrNull(it.toInt()) } }
                    val rms = if (values.isEmpty()) 0f else kotlin.math.sqrt(values.map { it * it }.average()).toFloat()
                    GainEnvelope.normalizationGain(rms)
                }
                gainCache[key] = g
                g
            }
            out[c.id] = gain
        }
        return out
    }

    suspend fun music(bundle: ProjectBundle): Pair<Uri?, Long> {
        val id = bundle.project.edit.music.trackId ?: return null to 0
        SoundLibrary.track(id)?.let { return Uri.parse("asset:///${it.assetPath}") to it.durationMs }
        if (id.startsWith("file:")) {
            val path = id.removePrefix("file:")
            val uri = storage.toUri(path)
            if (!storage.isAccessible(path)) return null to 0
            val d = musicDurationCache[path] ?: runCatching { probe.probe(uri, "audio/*").durationMs }.getOrDefault(0L).also { musicDurationCache[path] = it }
            return uri to d
        }
        return null to 0
    }

    suspend fun inputs(projectId: String, quality: ExportQuality? = null, proxy: Boolean = false, normalize: Boolean = true): RenderInputs {
        val bundle = projects.getBundle(projectId) ?: error("Project not found")
        val q = quality ?: bundle.project.edit.quality
        val timeline = resolve(bundle)
        val captions = captionsFor(bundle, timeline)
        val (musicUri, musicDur) = music(bundle)
        val brand = bundle.project.brandSnapshot ?: settings.currentBrand()
        val gains = if (normalize && bundle.project.edit.audio.normalize) clipGains(timeline) else emptyMap()
        val (w, h) = if (proxy) 720 to 1280 else q.width to q.height
        return RenderInputs(
            bundle = bundle, timeline = timeline, captions = captions, brand = brand.takeIf { it.isConfigured || bundle.project.edit.branding.enabled },
            width = w, height = h, frameRate = q.fps, speechRanges = speechRanges(captions, timeline), clipGains = gains,
            musicUri = musicUri, musicDurationMs = musicDur, cacheDir = storage.cacheDir(projectId),
        )
    }

    fun hasDialogue(timeline: ResolvedTimeline) = timeline.dialogue.any { it.clip.kind != MediaKind.IMAGE }

    fun dialogueSignature(timeline: ResolvedTimeline): String =
        timeline.dialogue.joinToString("|") { "${it.clip.id}:${it.sourceStartMs}:${it.durationMs}:${it.timelineStartMs}" }.hashCode().toString()

    fun tempFile(projectId: String, name: String): File = File(storage.cacheDir(projectId), name)
}
