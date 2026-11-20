package app.reelify.policy

import app.reelify.core.model.ClaimSeverity
import app.reelify.core.model.ReelLanguage
import app.reelify.core.policy.ClaimChecker
import app.reelify.core.timing.TimingEstimator
import app.reelify.core.timing.TimingFit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimingEstimatorTest {
    private val est = TimingEstimator(2.5f, 2.1f)

    @Test fun `counts English and Arabic words, ignoring punctuation`() {
        assertThat(est.countWords("Is laser right — for everyone?")).isEqualTo(5)
        assertThat(est.countWords("شنو الفرق بين Aerolase والليزر العادي؟")).isEqualTo(6)
        assertThat(est.countWords("   ")).isEqualTo(0)
    }

    @Test fun `classifies fit`() {
        val target = 10_000L
        assertThat(est.estimate("", ReelLanguage.ENGLISH, target).fit).isEqualTo(TimingFit.EMPTY)
        assertThat(est.estimate("Short line.", ReelLanguage.ENGLISH, target).fit).isEqualTo(TimingFit.TOO_SHORT)
        val fits = (1..20).joinToString(" ") { "word" } + "."
        assertThat(est.estimate(fits, ReelLanguage.ENGLISH, target).fit).isEqualTo(TimingFit.FITS)
        val long = (1..40).joinToString(" ") { "word" } + "."
        assertThat(est.estimate(long, ReelLanguage.ENGLISH, target).fit).isEqualTo(TimingFit.TOO_LONG)
    }

    @Test fun `arabic pace is slower so the same word count takes longer`() {
        val text = (1..20).joinToString(" ") { "كلمة" }
        assertThat(est.estimate(text, ReelLanguage.IRAQI_ARABIC, 10_000).speechMs).isGreaterThan(est.estimate(text, ReelLanguage.ENGLISH, 10_000).speechMs)
    }

    @Test fun `recommended max words scales with section length`() {
        assertThat(est.estimate("", ReelLanguage.ENGLISH, 20_000).recommendedMaxWords).isGreaterThan(est.estimate("", ReelLanguage.ENGLISH, 10_000).recommendedMaxWords)
    }
}

class ClaimCheckerTest {
    @Test fun `flags English absolute claims`() {
        val f = ClaimChecker.check("Guaranteed results and 100% effective with no side effects, permanent results!")
        assertThat(f.map { it.severity }).contains(ClaimSeverity.BLOCKER)
        assertThat(f.size).isAtLeast(3)
    }

    @Test fun `flags Arabic absolute claims`() {
        val f = ClaimChecker.check("نتائج مضمونة ودائمية وبدون أي أعراض")
        assertThat(f).isNotEmpty()
        assertThat(f.all { it.severity == ClaimSeverity.BLOCKER || it.severity == ClaimSeverity.WARNING }).isTrue()
    }

    @Test fun `conservative educational phrasing passes`() {
        assertThat(ClaimChecker.check("Aerolase may be considered for some acne concerns. Results vary — book an assessment.")).isEmpty()
        assertThat(ClaimChecker.check("مو كل بشرة تناسبها نفس الجلسة، حسب حالة البشرة نحدد الأنسب")).isEmpty()
    }

    @Test fun `profile forbidden phrases are flagged`() {
        val f = ClaimChecker.check("This is the painless miracle", profileForbidden = listOf("painless miracle"))
        assertThat(f.any { it.source == "profile" || it.claim.contains("painless", true) }).isTrue()
    }
}
