package app.reelify.core.media

/**
 * Validated, non-inverting trim boundaries for [RangeSlider], nudge controls, and clip persistence.
 * Guarantees: 0 <= trimStartMs < trimEndMs <= sourceDurationMs, and valid non-empty slider ranges.
 */
data class ValidatedTrimBounds(
    val sourceDurationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val minDurationMs: Long,
) {
    val trimmedDurationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)

    val sliderRange: ClosedFloatingPointRange<Float>
        get() = 0f..sourceDurationMs.toFloat().coerceAtLeast(1f)

    val sliderValue: ClosedFloatingPointRange<Float>
        get() {
            val range = sliderRange
            val start = trimStartMs.toFloat().coerceIn(range.start, range.endInclusive)
            val end = trimEndMs.toFloat().coerceIn(start, range.endInclusive)
            return start..end
        }

    fun nudgeStart(deltaMs: Long): ValidatedTrimBounds {
        val maxStart = (trimEndMs - minDurationMs).coerceAtLeast(0L)
        val nextStart = (trimStartMs + deltaMs).coerceIn(0L, maxStart)
        return copy(trimStartMs = nextStart)
    }

    fun nudgeEnd(deltaMs: Long): ValidatedTrimBounds {
        val minEnd = (trimStartMs + minDurationMs).coerceAtMost(sourceDurationMs)
        val nextEnd = (trimEndMs + deltaMs).coerceIn(minEnd, sourceDurationMs)
        return copy(trimEndMs = nextEnd)
    }

    fun updateRange(newStartMs: Long, newEndMs: Long): ValidatedTrimBounds {
        val maxStart = (sourceDurationMs - minDurationMs).coerceAtLeast(0L)
        val clampedStart = newStartMs.coerceIn(0L, maxStart)
        val minEnd = (clampedStart + minDurationMs).coerceAtMost(sourceDurationMs)
        val clampedEnd = newEndMs.coerceIn(minEnd, sourceDurationMs)
        return copy(trimStartMs = clampedStart, trimEndMs = clampedEnd)
    }

    fun reset(): ValidatedTrimBounds = copy(trimStartMs = 0L, trimEndMs = sourceDurationMs)
}

object TrimBounds {
    const val DEFAULT_MIN_TRIM_MS = 200L
    const val FALLBACK_MIN_SOURCE_MS = 500L

    fun validate(
        sourceDurationMs: Long,
        trimStartMs: Long,
        trimEndMs: Long,
        defaultMinMs: Long = DEFAULT_MIN_TRIM_MS,
    ): ValidatedTrimBounds {
        val effectiveSource = maxOf(sourceDurationMs, trimEndMs, FALLBACK_MIN_SOURCE_MS)
        val minGap = minOf(defaultMinMs.coerceAtLeast(1L), effectiveSource / 2L).coerceAtLeast(1L)
        val maxStart = (effectiveSource - minGap).coerceAtLeast(0L)
        val start = trimStartMs.coerceIn(0L, maxStart)
        val rawEnd = if (trimEndMs <= start) effectiveSource else trimEndMs
        val end = rawEnd.coerceIn((start + minGap).coerceAtMost(effectiveSource), effectiveSource)
        return ValidatedTrimBounds(
            sourceDurationMs = effectiveSource,
            trimStartMs = start,
            trimEndMs = end,
            minDurationMs = minGap,
        )
    }
}
