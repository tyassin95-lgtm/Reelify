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

    @Test fun `zero or negative duration metadata never invents duration or crashes slider`() {
        val zero = TrimBounds.validate(sourceDurationMs = 0L, trimStartMs = 0L, trimEndMs = 0L)
        assertThat(zero.durationKnown).isFalse()
        assertThat(zero.sourceDurationMs).isEqualTo(0L)
        assertThat(zero.trimStartMs).isEqualTo(0L)
        assertThat(zero.trimEndMs).isEqualTo(0L)
        assertThat(TrimBounds.canSave(zero)).isFalse()
        assertThat(zero.sliderRange.endInclusive).isGreaterThan(zero.sliderRange.start)

        // Edits and nudges when duration is unknown remain safe no-ops rather than inventing 500ms
        val nudgedStart = zero.nudgeStart(-100L).nudgeStart(250L).nudgeStart(10_000L)
        assertThat(nudgedStart).isEqualTo(zero)

        val nudgedEnd = zero.nudgeEnd(-10_000L).nudgeEnd(500L)
        assertThat(nudgedEnd).isEqualTo(zero)
    }

    @Test fun `ultra short clip below 200ms preserves true source duration and stays safe`() {
        val tiny = TrimBounds.validate(sourceDurationMs = 120L, trimStartMs = 80L, trimEndMs = 100L)
        assertThat(tiny.durationKnown).isTrue()
        assertThat(tiny.sourceDurationMs).isEqualTo(120L)
        assertThat(TrimBounds.canSave(tiny)).isTrue()
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
