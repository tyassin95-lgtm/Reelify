package app.reelify.core.captions

import app.reelify.core.media.engine.AudioSegment
import app.reelify.core.media.engine.ResolvedTimeline
import app.reelify.core.model.CaptionSegment
import app.reelify.core.model.CaptionWord

/**
 * Keeps captions synchronized when the edit changes after transcription. Words carry their
 * source anchor (clip id + source time); here they are mapped through the current timeline's
 * dialogue segments. Words whose source audio is no longer used are dropped.
 */
object CaptionRemapper {

    /** Anchors timeline-relative words (as returned by transcription of the dialogue mix) to sources. */
    fun anchor(words: List<CaptionWord>, dialogue: List<AudioSegment>): List<CaptionWord> = words.map { w ->
        val mid = (w.startMs + w.endMs) / 2
        val seg = dialogue.firstOrNull { mid >= it.timelineStartMs && mid < it.timelineEndMs }
        if (seg == null) w else w.copy(
            clipId = seg.clip.id,
            srcStartMs = seg.sourceStartMs + (w.startMs - seg.timelineStartMs),
            srcEndMs = seg.sourceStartMs + (w.endMs - seg.timelineStartMs),
        )
    }

    fun mapWord(w: CaptionWord, dialogue: List<AudioSegment>): CaptionWord? {
        if (w.clipId == null || w.srcStartMs < 0) return w
        val srcMid = (w.srcStartMs + w.srcEndMs) / 2
        val seg = dialogue.firstOrNull { it.clip.id == w.clipId && srcMid >= it.sourceStartMs && srcMid < it.sourceStartMs + it.durationMs } ?: return null
        val start = seg.timelineStartMs + (w.srcStartMs - seg.sourceStartMs)
        val end = seg.timelineStartMs + (w.srcEndMs - seg.sourceStartMs)
        return w.copy(startMs = start.coerceAtLeast(seg.timelineStartMs), endMs = end.coerceAtMost(seg.timelineEndMs).coerceAtLeast(start + 1))
    }

    fun remap(segments: List<CaptionSegment>, timeline: ResolvedTimeline): List<CaptionSegment> =
        segments.mapNotNull { s ->
            val words = s.words.mapNotNull { mapWord(it, timeline.dialogue) }
            if (words.isEmpty()) null
            else {
                val moved = words.first().startMs - (s.words.first().startMs)
                s.copy(words = words, startMs = words.first().startMs, endMs = maxOf(words.last().endMs, s.endMs + moved).coerceAtMost(words.last().endMs + 400))
            }
        }.sortedBy { it.startMs }
}
