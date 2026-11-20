package app.reelify.feature.editor

import app.reelify.core.media.engine.ResolvedTimeline
import app.reelify.core.model.ClipRole
import app.reelify.core.model.EditSettings
import app.reelify.core.model.MediaClip
import app.reelify.core.model.MediaKind
import app.reelify.core.model.SfxCue
import app.reelify.core.util.Ids

/** Immutable editor snapshot used for undo/redo. */
data class EditorSnapshot(val clips: List<MediaClip>, val edit: EditSettings)

/** Bounded undo/redo history of snapshots. Pure and unit-tested. */
class UndoStack(private val limit: Int = 50) {
    private val undo = ArrayDeque<EditorSnapshot>()
    private val redo = ArrayDeque<EditorSnapshot>()
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun push(before: EditorSnapshot) {
        undo.addLast(before); if (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun undo(current: EditorSnapshot): EditorSnapshot? {
        val prev = undo.removeLastOrNull() ?: return null
        redo.addLast(current); return prev
    }

    fun redo(current: EditorSnapshot): EditorSnapshot? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current); return next
    }
}

/** Pure timeline operations — they never break the three-section model. */
object TimelineOps {
    const val MIN_MS = 300L

    /** Splits [clip] at source time [srcMs]; later clips in the same section/role shift order by one. */
    fun split(clips: List<MediaClip>, clipId: String, srcMs: Long): List<MediaClip> {
        val c = clips.firstOrNull { it.id == clipId } ?: return clips
        if (c.kind != MediaKind.VIDEO) return clips
        if (srcMs <= c.trimStartMs + MIN_MS || srcMs >= c.trimEndMs - MIN_MS) return clips
        val first = c.copy(trimEndMs = srcMs)
        val second = c.copy(id = Ids.new(), trimStartMs = srcMs, order = c.order + 1, placementMs = c.placementMs?.let { it + (srcMs - c.trimStartMs) }, createdAt = c.createdAt + 1)
        return clips.map { o ->
            when {
                o.id == c.id -> first
                o.sectionId == c.sectionId && o.role == c.role && o.order > c.order -> o.copy(order = o.order + 1)
                else -> o
            }
        } + second
    }

    fun trim(clips: List<MediaClip>, clipId: String, start: Long?, end: Long?): List<MediaClip> = clips.map { c ->
        if (c.id != clipId) c else {
            val s = (start ?: c.trimStartMs).coerceIn(0, c.sourceDurationMs)
            val e = (end ?: c.trimEndMs).coerceIn(0, c.sourceDurationMs)
            if (c.kind == MediaKind.IMAGE) c.copy(trimStartMs = 0, trimEndMs = e.coerceAtLeast(0))
            else if (e - s < MIN_MS) c else c.copy(trimStartMs = s, trimEndMs = e)
        }
    }

    /** Moves a clip earlier/later among clips of the same role within its section. */
    fun reorder(clips: List<MediaClip>, clipId: String, delta: Int): List<MediaClip> {
        val c = clips.firstOrNull { it.id == clipId } ?: return clips
        val group = clips.filter { it.sectionId == c.sectionId && it.role == c.role }.sortedBy { it.order }
        val idx = group.indexOfFirst { it.id == clipId }
        val target = (idx + delta).coerceIn(0, group.lastIndex)
        if (target == idx) return clips
        val reordered = group.toMutableList().apply { add(target, removeAt(idx)) }.mapIndexed { i, x -> x.copy(order = i) }.associateBy { it.id }
        return clips.map { reordered[it.id] ?: it }
    }

    fun delete(clips: List<MediaClip>, clipId: String): List<MediaClip> = clips.filterNot { it.id == clipId }

    fun duplicate(clips: List<MediaClip>, clipId: String): List<MediaClip> {
        val c = clips.firstOrNull { it.id == clipId } ?: return clips
        val copy = c.copy(id = Ids.new(), order = c.order + 1, createdAt = c.createdAt + 1)
        return clips.map { o -> if (o.sectionId == c.sectionId && o.role == c.role && o.order > c.order) o.copy(order = o.order + 1) else o } + copy
    }

    /** Snaps [ms] to the nearest marker when within [thresholdMs]. */
    fun snap(ms: Long, markers: List<Long>, thresholdMs: Long): Long = markers.minByOrNull { kotlin.math.abs(it - ms) }?.takeIf { kotlin.math.abs(it - ms) <= thresholdMs } ?: ms

    fun markers(tl: ResolvedTimeline): List<Long> = (listOf(0L, tl.sectionMs, tl.sectionMs * 2, tl.totalMs) + tl.video.map { it.timelineStartMs }).distinct().sorted()

    fun moveSfx(edit: EditSettings, cueId: String, timeMs: Long, totalMs: Long) = edit.copy(sfx = edit.sfx.map { if (it.id == cueId) it.copy(timeMs = timeMs.coerceIn(0, totalMs - 200)) else it })
    fun addSfx(edit: EditSettings, sfxId: String, timeMs: Long) = edit.copy(sfx = edit.sfx + SfxCue(Ids.new(), timeMs, sfxId, 0.6f))
    fun removeSfx(edit: EditSettings, cueId: String) = edit.copy(sfx = edit.sfx.filterNot { it.id == cueId })

    fun primaryAt(tl: ResolvedTimeline, ms: Long) = tl.video.firstOrNull { ms >= it.timelineStartMs && ms < it.timelineEndMs }
}
