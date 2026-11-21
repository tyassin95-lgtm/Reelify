package app.reelify.core.media

/**
 * Validated, non-inverting trim boundaries for [RangeSlider], nudge controls, and clip persistence.
 * Guarantees:
 * - When [durationKnown] is true: 0 <= trimStartMs < trimEndMs <= sourceDurationMs.
 * - When [durationKnown] is false: sourceDurationMs == 0L, edits are no-ops, and UI/persistence
 *   must recover duration before allowing saving (never fabricating a 500 ms duration).
 */
data class ValidatedTrimBounds(
    val sourceDurationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val minDurationMs: Long,
    val durationKnown: Boolean = true,
) {
    val trimmedDurationMs: Long get() = if (durationKnown) (trimEndMs - trimStartMs).coerceAtLeast(0L) else 0L

    /** Safe range for Compose RangeSlider even when duration is unknown (slider should be disabled). */
    val sliderRange: ClosedFloatingPointRange<Float>
        get() = 0f..sourceDurationMs.toFloat().coerceAtLeast(1f)

    val sliderValue: ClosedFloatingPointRange<Float>
        get() {
            if (!durationKnown || sourceDurationMs <= 0L) return 0f..0f
            val range = sliderRange
            val start = trimStartMs.toFloat().coerceIn(range.start, range.endInclusive)
            val end = trimEndMs.toFloat().coerceIn(start, range.endInclusive)
            return start..end
        }

    fun nudgeStart(deltaMs: Long): ValidatedTrimBounds {
        if (!durationKnown || sourceDurationMs <= 0L) return this
        val maxStart = (trimEndMs - minDurationMs).coerceAtLeast(0L)
        val nextStart = (trimStartMs + deltaMs).coerceIn(0L, maxStart)
        return copy(trimStartMs = nextStart)
    }

    fun nudgeEnd(deltaMs: Long): ValidatedTrimBounds {
        if (!durationKnown || sourceDurationMs <= 0L) return this
        val minEnd = (trimStartMs + minDurationMs).coerceAtMost(sourceDurationMs)
        val nextEnd = (trimEndMs + deltaMs).coerceIn(minEnd, sourceDurationMs)
        return copy(trimEndMs = nextEnd)
    }

    fun updateRange(newStartMs: Long, newEndMs: Long): ValidatedTrimBounds {
        if (!durationKnown || sourceDurationMs <= 0L) return this
        val maxStart = (sourceDurationMs - minDurationMs).coerceAtLeast(0L)
        val clampedStart = newStartMs.coerceIn(0L, maxStart)
        val minEnd = (clampedStart + minDurationMs).coerceAtMost(sourceDurationMs)
        val clampedEnd = newEndMs.coerceIn(minEnd, sourceDurationMs)
        return copy(trimStartMs = clampedStart, trimEndMs = clampedEnd)
    }

    fun reset(): ValidatedTrimBounds =
        if (!durationKnown || sourceDurationMs <= 0L) this
        else copy(trimStartMs = 0L, trimEndMs = sourceDurationMs)
}

object TrimBounds {
    const val DEFAULT_MIN_TRIM_MS = 200L

    fun validate(
        sourceDurationMs: Long,
        trimStartMs: Long,
        trimEndMs: Long,
        defaultMinMs: Long = DEFAULT_MIN_TRIM_MS,
    ): ValidatedTrimBounds {
        if (sourceDurationMs <= 0L && trimEndMs <= 0L) {
            return ValidatedTrimBounds(
                sourceDurationMs = 0L,
                trimStartMs = 0L,
                trimEndMs = 0L,
                minDurationMs = defaultMinMs.coerceAtLeast(1L),
                durationKnown = false,
            )
        }
        val effectiveSource = maxOf(sourceDurationMs, trimEndMs).coerceAtLeast(1L)
        val minGap = if (effectiveSource <= 2L) 1L else minOf(defaultMinMs.coerceAtLeast(1L), (effectiveSource / 2L).coerceAtLeast(1L))
        val maxStart = (effectiveSource - minGap).coerceAtLeast(0L)
        val start = trimStartMs.coerceIn(0L, maxStart)
        val rawEnd = if (trimEndMs <= start) effectiveSource else trimEndMs
        val end = rawEnd.coerceIn((start + minGap).coerceAtMost(effectiveSource), effectiveSource)
        return ValidatedTrimBounds(
            sourceDurationMs = effectiveSource,
            trimStartMs = start,
            trimEndMs = end,
            minDurationMs = minGap,
            durationKnown = true,
        )
    }

    fun canSave(bounds: ValidatedTrimBounds): Boolean =
        bounds.durationKnown && bounds.sourceDurationMs > 0L && bounds.trimEndMs > bounds.trimStartMs
}
