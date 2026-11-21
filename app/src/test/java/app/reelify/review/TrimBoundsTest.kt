package app.reelify.review

import app.reelify.core.media.TrimBounds
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrimBoundsTest {

    @Test fun `normal clip range stays within valid slider bounds`() {
        val b = TrimBounds.validate(sourceDurationMs = 12_000L, trimStartMs = 1_500L, trimEndMs = 9_500L)
        assertThat(b.sourceDurationMs).isEqualTo(12_000L)
        assertThat(b.trimStartMs).isEqualTo(1_500L)
        assertThat(b.trimEndMs).isEqualTo(9_500L)
        assertThat(b.trimmedDurationMs).isEqualTo(8_000L)
        assertThat(b.sliderRange).isEqualTo(0f..12_000f)
        assertThat(b.sliderValue).isEqualTo(1_500f..9_500f)
    }

    @Test fun `zero or negative duration metadata never crashes slider or nudges`() {
        val zero = TrimBounds.validate(sourceDurationMs = 0L, trimStartMs = 0L, trimEndMs = 0L)
        assertThat(zero.sourceDurationMs).isAtLeast(TrimBounds.FALLBACK_MIN_SOURCE_MS)
        assertThat(zero.trimStartMs).isEqualTo(0L)
        assertThat(zero.trimEndMs).isGreaterThan(zero.trimStartMs)
        assertThat(zero.sliderRange.endInclusive).isGreaterThan(zero.sliderRange.start)

        // Rapid nudges inside short clips never invert or throw IllegalArgumentException
        val nudgedStart = zero.nudgeStart(-100L).nudgeStart(250L).nudgeStart(10_000L)
        assertThat(nudgedStart.trimStartMs).isLessThan(nudgedStart.trimEndMs)
        assertThat(nudgedStart.trimStartMs).isAtLeast(0L)

        val nudgedEnd = zero.nudgeEnd(-10_000L).nudgeEnd(500L)
        assertThat(nudgedEnd.trimEndMs).isGreaterThan(nudgedEnd.trimStartMs)
        assertThat(nudgedEnd.trimEndMs).isAtMost(nudgedEnd.sourceDurationMs)
    }

    @Test fun `ultra short clip below 200ms remains safe for nudge controls`() {
        val tiny = TrimBounds.validate(sourceDurationMs = 120L, trimStartMs = 80L, trimEndMs = 100L)
        assertThat(tiny.sourceDurationMs).isEqualTo(TrimBounds.FALLBACK_MIN_SOURCE_MS)
        val a = tiny.nudgeStart(100L)
        val b = a.nudgeEnd(-100L)
        assertThat(a.trimEndMs - a.trimStartMs).isAtLeast(tiny.minDurationMs)
        assertThat(b.trimEndMs - b.trimStartMs).isAtLeast(tiny.minDurationMs)
        assertThat(b.sliderValue.start).isAtMost(b.sliderValue.endInclusive)
    }

    @Test fun `inverted or out-of-range inputs are normalized safely`() {
        val inverted = TrimBounds.validate(sourceDurationMs = 8_000L, trimStartMs = 7_500L, trimEndMs = 2_000L)
        assertThat(inverted.trimStartMs).isLessThan(inverted.trimEndMs)
        assertThat(inverted.trimEndMs).isAtMost(8_000L)

        val dragged = inverted.updateRange(newStartMs = 9_999L, newEndMs = 100L)
        assertThat(dragged.trimStartMs).isLessThan(dragged.trimEndMs)
        assertThat(dragged.trimEndMs).isAtMost(8_000L)

        val reset = dragged.reset()
        assertThat(reset.trimStartMs).isEqualTo(0L)
        assertThat(reset.trimEndMs).isEqualTo(8_000L)
    }
}
