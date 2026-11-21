package app.reelify.content

import app.reelify.core.content.DialectChecker
import app.reelify.core.content.ExampleStatus
import app.reelify.core.content.IraqiStyleGuide
import app.reelify.core.content.TopicLibrary
import app.reelify.core.data.TreatmentSeed
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * STRUCTURAL checks only. Passing these does not mean text is authentic Iraqi Arabic — that is
 * judged by a human (Studio → Iraqi style guide). See docs/TESTING.md.
 */
class DialectAndTopicsTest {

    @Test fun `formal MSA is flagged and conversational Iraqi is not`() {
        val msa = DialectChecker.analyze("هل تعانون من مشكلات التصبغات الجلدية؟ سوف نتحدث اليوم عن الخيارات العلاجية المتاحة.")
        assertThat(msa.likelyFormal).isTrue()
        assertThat(msa.formalHits).containsAtLeast("هل", "سوف")
        val iq = DialectChecker.analyze("عندكم تصبغات بالوجه ومحتارين شنو الليزر المناسب؟ خلّي أوضحلكم شغلة مهمة قبل لا تختارون.")
        assertThat(iq.likelyFormal).isFalse()
        assertThat(iq.iraqiHits).contains("شنو")
    }

    @Test fun `prefixed formal words are detected (وسوف)`() {
        assertThat(DialectChecker.analyze("وسوف نشرح لماذا يجب التقييم").formalHits).containsAtLeast("سوف", "لماذا", "يجب")
    }

    @Test fun `style guide has at least 20 curated examples, all marked unreviewed, none of them formal`() {
        val ex = IraqiStyleGuide.builtInExamples
        assertThat(ex.size).isAtLeast(20)
        assertThat(ex.map { it.id }.toSet()).hasSize(ex.size)
        assertThat(ex.all { it.status == ExampleStatus.UNREVIEWED }).isTrue()
        ex.forEach { e -> assertThat(DialectChecker.analyze(e.better).likelyFormal).isFalse() }
        // the "formal" counter-examples should actually look formal to the checker in most cases
        val withFormal = ex.filter { it.formal.isNotBlank() }
        assertThat(withFormal.count { DialectChecker.analyze(it.formal).formalHits.isNotEmpty() }.toFloat() / withFormal.size).isAtLeast(0.7f)
    }

    @Test fun `owner approvals rank first and rejections are excluded`() {
        val first = IraqiStyleGuide.builtInExamples.first()
        val last = IraqiStyleGuide.builtInExamples.last()
        val eff = IraqiStyleGuide.effectiveExamples(listOf(last.copy(status = ExampleStatus.APPROVED), first.copy(status = ExampleStatus.REJECTED)))
        assertThat(eff.first().id).isEqualTo(last.id)
        assertThat(eff.none { it.id == first.id }).isTrue()
    }

    @Test fun `overused marketing phrases are detected`() {
        assertThat(DialectChecker.analyze("احصلوا على بشرة نضرة ومشرقة").overusedHits).isNotEmpty()
        assertThat(DialectChecker.overused("Say goodbye to acne, it's a game-changer")).hasSize(2)
    }

    @Test fun `profiles that need configuration only get safe generic topics`() {
        val cfu = TreatmentSeed.treatments.first { it.id == "cfu" }
        assertThat(cfu.needsConfiguration).isTrue()
        val s = TopicLibrary.suggestions(cfu, "expert_explains", emptyList(), arabic = false, limit = 50)
        assertThat(s).isNotEmpty()
        assertThat(s.none { it.idea.needsProfileFacts }).isTrue()
        val aerolase = TreatmentSeed.treatments.first { it.id == "aerolase" }
        assertThat(TopicLibrary.suggestions(aerolase, "myth_buster", emptyList(), false, 50).any { it.idea.needsProfileFacts }).isTrue()
    }

    @Test fun `indication-specific topics only appear when the profile mentions them`() {
        val aerolase = TreatmentSeed.treatments.first { it.id == "aerolase" }   // acne + pigmentation
        val keys = TopicLibrary.suggestions(aerolase, null, emptyList(), false, 50).map { it.idea.key }
        assertThat(keys).containsAtLeast("acne_back", "pigment_sun")
        val general = TreatmentSeed.treatments.first { it.isGeneral }
        assertThat(TopicLibrary.suggestions(general, null, emptyList(), false, 50).map { it.idea.key }).doesNotContain("acne_back")
    }

    @Test fun `recently used topics move to the end and template matches rise`() {
        val aerolase = TreatmentSeed.treatments.first { it.id == "aerolase" }
        val fresh = TopicLibrary.suggestions(aerolase, "three_things", emptyList(), false, 50)
        val firstKey = fresh.first().idea.key
        assertThat(fresh.first().idea.templates).contains("three_things")
        val after = TopicLibrary.suggestions(aerolase, "three_things", listOf(firstKey), false, 50)
        assertThat(after.first().idea.key).isNotEqualTo(firstKey)
        assertThat(after.last().idea.key).isEqualTo(firstKey)
        assertThat(after.last().usedRecently).isTrue()
    }

    @Test fun `titles are localized and filled with the treatment name`() {
        val aerolase = TreatmentSeed.treatments.first { it.id == "aerolase" }
        val en = TopicLibrary.suggestions(aerolase, "myth_buster", emptyList(), false, 50).first { it.idea.key == "misconception" }
        val ar = TopicLibrary.suggestions(aerolase, "myth_buster", emptyList(), true, 50).first { it.idea.key == "misconception" }
        assertThat(en.title).contains("Aerolase")
        assertThat(ar.title).contains("إيروليز")
    }
}
