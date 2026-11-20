package app.reelify.captions

import app.reelify.Fixtures
import app.reelify.Fixtures.clip
import app.reelify.core.captions.CaptionBuilder
import app.reelify.core.captions.CaptionRemapper
import app.reelify.core.captions.SrtWriter
import app.reelify.core.captions.TerminologyCorrector
import app.reelify.core.data.TreatmentSeed
import app.reelify.core.media.engine.TimelineResolver
import app.reelify.core.model.CaptionWord
import app.reelify.core.model.ReelLanguage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptionsTest {
    private fun words(vararg w: String, start: Long = 0, step: Long = 300) = w.mapIndexed { i, t -> CaptionWord(t, start + i * step, start + i * step + 250) }

    @Test fun `segments respect words per caption and Arabic question marks`() {
        val segs = CaptionBuilder.segment(words("شنو", "الفرق", "بين", "إيروليز", "والليزر؟", "خل", "أوضحلكم"), wordsPerCaption = 4)
        assertThat(segs.first().words.size).isAtMost(4)
        // sentence end "؟" closes a caption
        assertThat(segs.any { it.words.last().text.endsWith("؟") }).isTrue()
        segs.zipWithNext().forEach { (a, b) -> assertThat(a.endMs).isAtMost(b.startMs) }
    }

    @Test fun `long pause starts a new caption`() {
        val w = words("Book", "now") + words("Results", "vary", start = 3000)
        assertThat(CaptionBuilder.segment(w, 6).map { it.text }).containsExactly("Book now", "Results vary").inOrder()
    }

    @Test fun `editing with the same word count keeps timestamps`() {
        val s = CaptionBuilder.segment(words("aero", "lase", "works"), 5).single()
        val e = CaptionBuilder.editSegment(s, "Aero laser helps")
        assertThat(e.words.map { it.startMs }).isEqualTo(s.words.map { it.startMs })
        assertThat(e.manuallyEdited).isTrue()
    }

    @Test fun `editing with a different word count redistributes within the range`() {
        val s = CaptionBuilder.segment(words("aero", "lase", "works"), 5).single()
        val e = CaptionBuilder.editSegment(s, "Aerolase helps")
        assertThat(e.words).hasSize(2)
        assertThat(e.words.first().startMs).isEqualTo(s.startMs)
        assertThat(e.words.last().endMs).isAtMost(s.endMs + 1)
    }

    @Test fun `terminology correction merges multi-word misrecognitions`() {
        val terms = TreatmentSeed.terminology
        val (fixed, fixes) = TerminologyCorrector.correct(words("try", "aero", "lase,", "today"), terms, ReelLanguage.ENGLISH)
        assertThat(fixed.map { it.text }).containsExactly("try", "Aerolase,", "today").inOrder()
        assertThat(fixes).hasSize(1)
        val (ar, _) = TerminologyCorrector.correct(words("جهاز", "ايرو", "ليز"), terms, ReelLanguage.IRAQI_ARABIC)
        assertThat(ar.map { it.text }).contains("إيروليز")
    }

    @Test fun `srt output is well formed`() {
        val srt = SrtWriter.write(CaptionBuilder.segment(words("Book", "now.", "Results", "vary."), 4))
        assertThat(srt).startsWith("1\n00:00:00,000 --> ")
        assertThat(srt).contains("\n2\n")
        assertThat(SrtWriter.timestamp(3_723_456)).isEqualTo("01:02:03,456")
    }

    @Test fun `captions stay synced after a trim change`() {
        val before = Fixtures.bundle(listOf(clip("a", "s1", source = 14_000, trimStart = 2_000, trimEnd = 12_000), clip("b", "s2"), clip("c", "s3")))
        val tl1 = TimelineResolver.resolve(before)
        // a word spoken at source 5.0s appears at reel 3.0s
        val anchored = CaptionRemapper.anchor(listOf(CaptionWord("hello", 3_000, 3_400)), tl1.dialogue)
        assertThat(anchored.single().srcStartMs).isEqualTo(5_000)
        val seg = CaptionBuilder.segment(anchored, 4)
        // the user trims 1s more from the start
        val after = before.copy(clips = before.clips.map { if (it.id == "a") it.copy(trimStartMs = 3_000, trimEndMs = 13_000) else it })
        val remapped = CaptionRemapper.remap(seg, TimelineResolver.resolve(after))
        assertThat(remapped.single().words.single().startMs).isEqualTo(2_000)
        // trimming the word away removes it
        val cut = before.copy(clips = before.clips.map { if (it.id == "a") it.copy(trimStartMs = 6_000, trimEndMs = 14_000) else it })
        assertThat(CaptionRemapper.remap(seg, TimelineResolver.resolve(cut))).isEmpty()
    }

    @Test fun `emphasis marks keyword words`() {
        val segs = CaptionBuilder.applyEmphasis(CaptionBuilder.segment(words("Try", "Aerolase", "today"), 5), listOf("aerolase"))
        assertThat(segs.single().words.single { it.emphasized }.text).isEqualTo("Aerolase")
    }

    @Test fun `script estimate captions cover each section`() {
        val w = CaptionBuilder.fromScript(listOf(Triple(0L, 10_000L, "one two three"), Triple(10_000L, 10_000L, "four five")))
        assertThat(w).hasSize(5)
        assertThat(w[3].startMs).isAtLeast(10_000)
    }
}
