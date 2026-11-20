package app.reelify.media

import app.reelify.Fixtures
import app.reelify.Fixtures.clip
import app.reelify.core.media.engine.IssueKind
import app.reelify.core.media.engine.TimelineResolver
import app.reelify.core.model.ClipRole
import app.reelify.core.model.FillStrategy
import app.reelify.core.model.MediaKind
import app.reelify.core.model.ReelDuration
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimelineResolverTest {

    private fun assertSectionsExact(tl: app.reelify.core.media.engine.ResolvedTimeline, duration: ReelDuration) {
        assertThat(tl.video.sumOf { it.durationMs }).isEqualTo(duration.totalMs)
        for (i in 1..3) {
            val segs = tl.video.filter { it.sectionIndex == i }
            assertThat(segs.sumOf { it.durationMs }).isEqualTo(duration.sectionMs)
            assertThat(segs.first().timelineStartMs).isEqualTo(duration.sectionMs * (i - 1))
        }
        // contiguous, no gaps/overlaps
        tl.video.zipWithNext().forEach { (a, b) -> assertThat(b.timelineStartMs).isEqualTo(a.timelineEndMs) }
    }

    @Test fun `spec scenario - 14s take trimmed, imported treatment clip, 12s take - exact 30s`() {
        val clips = listOf(
            clip("rec1", "s1", source = 14_000, trimStart = 1_800, trimEnd = 11_800),
            clip("imp2", "s2", source = 22_000, trimStart = 4_000, trimEnd = 14_000, hasAudio = false),
            clip("rec3", "s3", source = 12_000, trimStart = 900, trimEnd = 10_900),
        )
        val tl = TimelineResolver.resolve(Fixtures.bundle(clips))
        assertThat(tl.blocking).isEmpty()
        assertSectionsExact(tl, ReelDuration.S30)
        assertThat(tl.video.first().sourceStartMs).isEqualTo(1_800)
        // dialogue only from clips with audio
        assertThat(tl.dialogue.map { it.clip.id }).containsExactly("rec1", "rec3").inOrder()
    }

    @Test fun `45 and 60 second reels have three exactly equal sections`() {
        for (d in listOf(ReelDuration.S45, ReelDuration.S60)) {
            val clips = listOf(clip("a", "s1", source = 30_000), clip("b", "s2", source = 30_000), clip("c", "s3", source = 30_000))
            val tl = TimelineResolver.resolve(Fixtures.bundle(clips, d))
            assertSectionsExact(tl, d)
            assertThat(tl.issues.filter { it.kind == IssueKind.OVERFLOW }).hasSize(3)
            assertThat(tl.blocking).isEmpty()
        }
    }

    @Test fun `short footage is a blocking shortfall and talking head is never looped`() {
        val clips = listOf(clip("a", "s1", source = 6_000), clip("b", "s2"), clip("c", "s3"))
        val tl = TimelineResolver.resolve(Fixtures.bundle(clips))
        val issue = tl.blocking.single()
        assertThat(issue.kind).isEqualTo(IssueKind.SHORTFALL)
        assertThat(issue.amountMs).isEqualTo(4_000)
        // clip "a" appears exactly once (no looping)
        assertThat(tl.video.count { it.clip?.id == "a" }).isEqualTo(1)
    }

    @Test fun `auto b-roll fills the tail gap and keeps dialogue continuous`() {
        val clips = listOf(
            clip("a", "s1", source = 7_000),
            clip("br", "s1", role = ClipRole.BROLL, source = 5_000, trimEnd = 3_000, hasAudio = false),
            clip("b", "s2"), clip("c", "s3"),
        )
        val tl = TimelineResolver.resolve(Fixtures.bundle(clips))
        assertThat(tl.blocking).isEmpty()
        val s1 = tl.video.filter { it.sectionIndex == 1 }
        assertThat(s1.map { it.clip?.id }).containsExactly("a", "br").inOrder()
        assertThat(s1[1].isCutaway).isTrue()
    }

    @Test fun `placed b-roll is a cutaway over talking head while audio continues`() {
        val clips = listOf(
            clip("a", "s1", source = 10_000),
            clip("br", "s1", role = ClipRole.BROLL, source = 5_000, trimEnd = 2_000, placement = 4_000, hasAudio = false),
            clip("b", "s2"), clip("c", "s3"),
        )
        val tl = TimelineResolver.resolve(Fixtures.bundle(clips))
        val s1 = tl.video.filter { it.sectionIndex == 1 }
        assertThat(s1.map { it.clip?.id }).containsExactly("a", "br", "a").inOrder()
        assertThat(s1[2].sourceStartMs).isEqualTo(6_000) // presenter resumes in sync
        assertThat(tl.dialogue.filter { it.sectionIndex == 1 }.single().durationMs).isEqualTo(10_000)
        assertThat(tl.cutPoints().map { it.first }).containsAtLeast(4_000L, 6_000L, 10_000L, 20_000L)
    }

    @Test fun `still image stretches to fill and voiceover supplies dialogue`() {
        val clips = listOf(
            clip("a", "s1"),
            clip("img", "s2", kind = MediaKind.IMAGE, source = 0, trimEnd = 0, hasAudio = false),
            clip("vo", "s2", role = ClipRole.VOICEOVER, kind = MediaKind.AUDIO, source = 8_000),
            clip("c", "s3"),
        )
        val tl = TimelineResolver.resolve(Fixtures.bundle(clips))
        assertThat(tl.blocking).isEmpty()
        assertThat(tl.video.single { it.sectionIndex == 2 }.durationMs).isEqualTo(10_000)
        assertThat(tl.dialogue.single { it.sectionIndex == 2 }.clip.id).isEqualTo("vo")
    }

    @Test fun `brand card fill is explicit and resolves a shortfall`() {
        val p = Fixtures.project().let { it.copy(edit = it.edit.copy(fill = mapOf("s1" to FillStrategy.BRAND_CARD))) }
        val clips = listOf(clip("a", "s1", source = 7_000), clip("b", "s2"), clip("c", "s3"))
        val tl = TimelineResolver.resolve(Fixtures.bundle(clips, project = p))
        assertThat(tl.blocking).isEmpty()
        assertThat(tl.video.last { it.sectionIndex == 1 }.clip).isNull()
        assertSectionsExact(tl, ReelDuration.S30)
    }

    @Test fun `missing file and missing consent block export`() {
        val clips = listOf(
            clip("a", "s1"),
            clip("b", "s2").copy(identifiablePatient = true, consent = app.reelify.core.model.ConsentState.NOT_CONFIRMED),
            clip("c", "s3"),
        )
        val tl = TimelineResolver.resolve(Fixtures.bundle(clips)) { it.id != "c" }
        assertThat(tl.blocking.map { it.kind }).containsAtLeast(IssueKind.CONSENT, IssueKind.MISSING_FILE)
        assertThat(tl.isRenderable).isFalse()
    }

    @Test fun `split clips play consecutively`() {
        val clips = listOf(
            clip("a1", "s1", source = 20_000, trimStart = 0, trimEnd = 4_000, order = 0),
            clip("a2", "s1", source = 20_000, trimStart = 9_000, trimEnd = 15_000, order = 1),
            clip("b", "s2"), clip("c", "s3"),
        )
        val tl = TimelineResolver.resolve(Fixtures.bundle(clips))
        val s1 = tl.video.filter { it.sectionIndex == 1 }
        assertThat(s1.map { it.durationMs }).containsExactly(4_000L, 6_000L).inOrder()
        assertThat(s1[1].sourceStartMs).isEqualTo(9_000)
    }
}
