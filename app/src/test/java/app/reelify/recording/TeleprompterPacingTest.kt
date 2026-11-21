package app.reelify.recording

import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.SectionPurpose
import app.reelify.core.model.ShotType
import app.reelify.core.model.TeleprompterMode
import app.reelify.core.timing.TimingEstimator
import app.reelify.feature.recording.TeleprompterPacer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TeleprompterPacingTest {

    @Test fun `short script fits viewport cleanly and remains stationary without scrolling away`() {
        val plan = TeleprompterPacer.computePlan(
            textHeightPx = 140f,
            viewportHeightPx = 220f,
            targetDurationMs = 10_000L,
            estimatedSpeechMs = 7_500L,
            mode = TeleprompterMode.AUTO_FIT,
            speedMultiplier = 1.0f,
        )
        assertThat(plan.stationary).isTrue()
        assertThat(plan.totalScrollPx).isEqualTo(0f)
        assertThat(TeleprompterPacer.targetScrollOffsetPx(plan, 5_000L)).isEqualTo(0)
        assertThat(TeleprompterPacer.targetScrollOffsetPx(plan, 10_000L)).isEqualTo(0)
    }

    @Test fun `longer script scrolls gradually across 30s 45s and 60s shot windows with lead-in hold`() {
        listOf(10_000L, 15_000L, 20_000L).forEach { shotMs ->
            val plan = TeleprompterPacer.computePlan(
                textHeightPx = 680f,
                viewportHeightPx = 220f,
                targetDurationMs = shotMs,
                estimatedSpeechMs = (shotMs * 0.88f).toLong(),
                mode = TeleprompterMode.AUTO_FIT,
                speedMultiplier = 1.0f,
            )
            assertThat(plan.stationary).isFalse()
            assertThat(plan.totalScrollPx).isGreaterThan(0f)
            // During lead-in hold, scroll remains 0 so speaker sees opening line clearly
            assertThat(TeleprompterPacer.targetScrollOffsetPx(plan, 400L)).isEqualTo(0)
            // Mid-way through shot, scroll has only advanced partway
            val mid = TeleprompterPacer.targetScrollOffsetPx(plan, shotMs / 2)
            assertThat(mid).isGreaterThan(0)
            assertThat(mid).isLessThan(plan.totalScrollPx.toInt())
            // Never exceeds max scroll at end
            assertThat(TeleprompterPacer.targetScrollOffsetPx(plan, shotMs)).isAtMost(plan.totalScrollPx.toInt() + 1)
        }
    }

    @Test fun `match shot timer honors speed multiplier and calibration math`() {
        val normal = TeleprompterPacer.computePlan(600f, 200f, 15_000L, 12_000L, TeleprompterMode.MATCH_SHOT, 1.0f)
        val slower = TeleprompterPacer.computePlan(600f, 200f, 15_000L, 12_000L, TeleprompterMode.MATCH_SHOT, 0.85f)
        assertThat(slower.pxPerSecond).isLessThan(normal.pxPerSecond)
        assertThat(slower.effectiveDurationMs).isGreaterThan(normal.effectiveDurationMs)

        val est = TimingEstimator()
        val ar15s = est.wordBudgetForShot(ReelLanguage.IRAQI_ARABIC, ReelDuration.S45.sectionMs, SectionPurpose.EXPLANATION, ShotType.TALKING_HEAD)
        assertThat(ar15s.targetMinWords).isAtLeast(20)
        assertThat(ar15s.targetMaxWords).isGreaterThan(ar15s.targetMinWords)

        val slowWps = TimingEstimator.calibratedPaceFromSample(wordsSpoken = 30, elapsedMs = 16_000L)
        assertThat(slowWps).isWithin(0.05f).of(1.875f)
    }
}
