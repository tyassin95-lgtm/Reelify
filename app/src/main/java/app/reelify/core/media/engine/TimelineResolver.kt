package app.reelify.core.media.engine

import app.reelify.core.model.ClipRole
import app.reelify.core.model.FillStrategy
import app.reelify.core.model.MediaClip
import app.reelify.core.model.MediaKind
import app.reelify.core.model.ProjectBundle
import app.reelify.core.model.ReelSection

/** A slice of the single video track. [sourceStartMs] is in the clip's source time. */
data class VideoSegment(
    val sectionIndex: Int,
    val timelineStartMs: Long,
    val durationMs: Long,
    val clip: MediaClip?,          // null => generated brand card
    val sourceStartMs: Long,
    val isCutaway: Boolean,
) {
    val timelineEndMs: Long get() = timelineStartMs + durationMs
}

/** A slice of dialogue audio (from a primary clip's own audio or a voiceover recording). */
data class AudioSegment(
    val sectionIndex: Int,
    val timelineStartMs: Long,
    val durationMs: Long,
    val clip: MediaClip,
    val sourceStartMs: Long,
) {
    val timelineEndMs: Long get() = timelineStartMs + durationMs
}

enum class IssueKind { SHORTFALL, OVERFLOW, NO_MEDIA, MISSING_FILE, CONSENT, CLINICAL_BEAUTY }

data class TimelineIssue(
    val kind: IssueKind,
    val sectionIndex: Int,
    val sectionId: String,
    val amountMs: Long,
    val message: String,
    val blocking: Boolean,
)

data class ResolvedTimeline(
    val totalMs: Long,
    val sectionMs: Long,
    val video: List<VideoSegment>,
    val dialogue: List<AudioSegment>,
    val issues: List<TimelineIssue>,
) {
    val blocking: List<TimelineIssue> get() = issues.filter { it.blocking }
    val isRenderable: Boolean get() = blocking.isEmpty() && video.isNotEmpty()

    /** Boundaries between consecutive video segments, tagged with whether they are section boundaries. */
    fun cutPoints(): List<Pair<Long, Boolean>> = video.zipWithNext().map { (a, b) -> b.timelineStartMs to (a.sectionIndex != b.sectionIndex) }

    /** Speech regions on the reel timeline (dialogue coverage), used for music ducking fallback. */
    fun dialogueRanges(): List<LongRange> = dialogue.map { it.timelineStartMs until it.timelineEndMs }
}

/**
 * Deterministically lays out the three equal sections of a reel from its media assignments.
 *
 * Rules:
 * - Each section occupies exactly totalMs / 3 on the timeline.
 * - PRIMARY clips play consecutively; anything beyond the section length is cut (OVERFLOW warning).
 * - BROLL clips are cutaways: they replace video only, dialogue keeps playing underneath.
 *   Auto-placed B-roll fills an uncovered tail first; otherwise it is centred after the hook.
 * - VOICEOVER clips provide dialogue audio for the section.
 * - Still images stretch to fill remaining time (with Ken Burns motion, applied by the renderer).
 * - Talking-head video is NEVER looped or slowed to fill time. Remaining gaps are reported as a
 *   blocking SHORTFALL unless the user explicitly chose a branded card fill for that section.
 */
object TimelineResolver {
    const val MIN_SEGMENT_MS = 120L

    fun resolve(bundle: ProjectBundle, fileExists: (MediaClip) -> Boolean = { true }): ResolvedTimeline {
        val total = bundle.project.duration.totalMs
        val sectionMs = bundle.project.duration.sectionMs
        val video = mutableListOf<VideoSegment>()
        val dialogue = mutableListOf<AudioSegment>()
        val issues = mutableListOf<TimelineIssue>()

        for (section in bundle.sections.sortedBy { it.index }) {
            val start = sectionMs * (section.index - 1)
            val clips = bundle.clipsFor(section.id)
            clips.filter { !fileExists(it) }.forEach {
                issues += TimelineIssue(IssueKind.MISSING_FILE, section.index, section.id, 0, "Section ${section.index}: “${it.displayName.ifBlank { "media" }}” can't be found. Relink or replace it.", true)
            }
            clips.filter { it.identifiablePatient && it.consent != app.reelify.core.model.ConsentState.CONFIRMED }.forEach {
                issues += TimelineIssue(IssueKind.CONSENT, section.index, section.id, 0, "Section ${section.index}: confirm patient consent for “${it.displayName.ifBlank { "clip" }}”.", true)
            }
            val usable = clips.filter { fileExists(it) && !it.missing && !(it.identifiablePatient && it.consent != app.reelify.core.model.ConsentState.CONFIRMED) }
            resolveSection(section, start, sectionMs, usable, bundle.project.edit.fill[section.id] ?: FillStrategy.NONE, video, dialogue, issues)
        }
        return ResolvedTimeline(total, sectionMs, video.sortedBy { it.timelineStartMs }, dialogue.sortedBy { it.timelineStartMs }, issues)
    }

    private data class Span(val start: Long, val end: Long, val clip: MediaClip?, val srcStart: Long, val cutaway: Boolean)

    private fun resolveSection(
        section: ReelSection,
        start: Long,
        len: Long,
        clips: List<MediaClip>,
        fill: FillStrategy,
        video: MutableList<VideoSegment>,
        dialogue: MutableList<AudioSegment>,
        issues: MutableList<TimelineIssue>,
    ) {
        val primaries = clips.filter { it.role == ClipRole.PRIMARY && it.isVisual }.sortedBy { it.order }
        val brolls = clips.filter { it.role == ClipRole.BROLL && it.isVisual }.sortedBy { it.order }
        val voiceovers = clips.filter { it.role == ClipRole.VOICEOVER || (it.role == ClipRole.PRIMARY && it.kind == MediaKind.AUDIO) }.sortedBy { it.order }

        // 1. Base layer from primary clips.
        val base = mutableListOf<Span>()
        var pos = 0L
        var overflow = 0L
        val imagesToStretch = primaries.count { it.kind == MediaKind.IMAGE }
        val videoTotal = primaries.filter { it.kind == MediaKind.VIDEO }.sumOf { it.trimmedDurationMs }
        // Images share whatever time the video primaries leave (or their explicit trimmed duration if set smaller).
        val imageShare = if (imagesToStretch > 0) ((len - videoTotal).coerceAtLeast(0) / imagesToStretch) else 0
        for (p in primaries) {
            val natural = if (p.kind == MediaKind.IMAGE) {
                val explicit = p.trimmedDurationMs
                if (explicit in 1 until imageShare) explicit else imageShare.coerceAtLeast(if (explicit > 0) explicit else 0)
            } else p.trimmedDurationMs
            if (natural <= 0) continue
            if (pos >= len) { overflow += natural; continue }
            val use = minOf(natural, len - pos)
            if (use >= MIN_SEGMENT_MS) {
                base += Span(pos, pos + use, p, p.trimStartMs, false)
                if (p.kind == MediaKind.VIDEO && p.hasAudio && !p.muted) {
                    dialogue += AudioSegment(section.index, start + pos, use, p, p.trimStartMs)
                }
            }
            overflow += natural - use
            pos += use
        }
        if (overflow > 500) {
            issues += TimelineIssue(IssueKind.OVERFLOW, section.index, section.id, overflow,
                "Section ${section.index}: footage runs ${fmt(overflow)} over; the end is cut. Adjust the trim to choose what stays.", false)
        }

        // 2. Voiceover dialogue (sequential from section start).
        var vpos = 0L
        for (v in voiceovers) {
            if (vpos >= len) break
            val use = minOf(v.trimmedDurationMs, len - vpos)
            if (use >= MIN_SEGMENT_MS && !v.muted) dialogue += AudioSegment(section.index, start + vpos, use, v, v.trimStartMs)
            vpos += use
        }

        // 3. B-roll cutaways.
        val overlays = mutableListOf<Span>()
        var tailCursor = pos
        for (b in brolls) {
            val d = if (b.kind == MediaKind.IMAGE) (b.trimmedDurationMs.takeIf { it > 0 } ?: 3000L) else b.trimmedDurationMs
            if (d < MIN_SEGMENT_MS) continue
            val placeAt = when {
                b.placementMs != null -> b.placementMs.coerceIn(0, (len - MIN_SEGMENT_MS).coerceAtLeast(0))
                tailCursor < len -> tailCursor
                else -> {
                    // Centre after the first ~25% (keeps the opening of the section on the presenter).
                    val earliest = (len * 0.25).toLong()
                    (earliest + ((len - earliest) - d) / 2).coerceIn(0, (len - d).coerceAtLeast(0))
                }
            }
            val end = minOf(placeAt + d, len)
            if (end - placeAt >= MIN_SEGMENT_MS) {
                overlays += Span(placeAt, end, b, b.trimStartMs, true)
                if (b.placementMs == null && placeAt >= tailCursor) tailCursor = end
            }
        }

        // 4. Flatten base + overlays (overlays win), then fill remaining gaps.
        val boundaries = (base.flatMap { listOf(it.start, it.end) } + overlays.flatMap { listOf(it.start, it.end) } + listOf(0L, len))
            .filter { it in 0..len }.distinct().sorted()
        val flat = mutableListOf<Span>()
        for ((a, b) in boundaries.zipWithNext()) {
            if (b - a <= 0) continue
            val mid = a + (b - a) / 2
            val ov = overlays.lastOrNull { mid >= it.start && mid < it.end }
            val bs = base.firstOrNull { mid >= it.start && mid < it.end }
            val chosen = ov ?: bs
            if (chosen == null) { flat += Span(a, b, null, 0, false); continue }
            flat += Span(a, b, chosen.clip, chosen.srcStart + (a - chosen.start), chosen.cutaway)
        }
        // Merge adjacent spans that continue the same clip contiguously.
        val merged = mutableListOf<Span>()
        for (s in flat) {
            val last = merged.lastOrNull()
            if (last != null && last.clip?.id == s.clip?.id && last.clip != null && last.srcStart + (last.end - last.start) == s.srcStart) {
                merged[merged.size - 1] = last.copy(end = s.end)
            } else if (last != null && last.clip == null && s.clip == null) {
                merged[merged.size - 1] = last.copy(end = s.end)
            } else merged += s
        }

        val gapMs = merged.filter { it.clip == null }.sumOf { it.end - it.start }
        if (gapMs > 0) {
            if (fill == FillStrategy.BRAND_CARD) {
                // explicit user choice: gaps become a branded card (generated still)
            } else if (primaries.isEmpty() && brolls.isEmpty()) {
                issues += TimelineIssue(IssueKind.NO_MEDIA, section.index, section.id, gapMs,
                    "Section ${section.index} has no footage yet. Record or import a clip.", true)
            } else {
                issues += TimelineIssue(IssueKind.SHORTFALL, section.index, section.id, gapMs,
                    "Section ${section.index} is ${fmt(gapMs)} short. Add B-roll, a still image, record a longer take, or use a branded card.", true)
            }
        }
        for (s in merged) {
            if (s.clip == null && fill != FillStrategy.BRAND_CARD && gapMs > 0) {
                // Leave unresolved gaps as brand-card placeholders so preview still works; export is blocked by the issue.
            }
            video += VideoSegment(section.index, start + s.start, s.end - s.start, s.clip, s.srcStart, s.cutaway)
        }
    }

    fun fmt(ms: Long): String = "%.1fs".format(ms / 1000f)
}
