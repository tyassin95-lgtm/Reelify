package app.reelify.content

import app.reelify.core.content.GeneratedContentNormalizer
import app.reelify.core.content.MedicalGlossary
import app.reelify.core.content.TextField
import app.reelify.core.data.TreatmentSeed
import app.reelify.core.model.ReelLanguage
import app.reelify.integration.openai.PlanValidator
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MedicalGlossaryTest {

    @Test fun `prohibited Arabic medical translations and transliterations are converted to canonical English`() {
        val raw = "ببيكوستار نستخدم ليزر البيكو ثانية، بس الكلف وفرط التصبغ والكولاجين في طبقة الأدمة يختلف عن السماس والهايفو."
        val enforced = MedicalGlossary.enforceEnglishMedicalTerms(raw)
        assertThat(enforced.text).contains("بـ PicoStar")
        assertThat(enforced.text).contains("Picosecond laser")
        assertThat(enforced.text).contains("Melasma")
        assertThat(enforced.text).contains("Hyperpigmentation")
        assertThat(enforced.text).contains("Collagen")
        assertThat(enforced.text).contains("Dermis")
        assertThat(enforced.text).contains("SMAS")
        assertThat(enforced.text).contains("HIFU")
        assertThat(MedicalGlossary.validate(enforced.text)).isEmpty()
    }

    @Test fun `everyday Iraqi words like يكلف or تصبغات are not falsely matched or corrupted`() {
        val everyday = "هذا العلاج ما يكلف هواي وقت، ومو كل التصبغات بالوجه تتعامل وياها بنفس الطريقة."
        val enforced = MedicalGlossary.enforceEnglishMedicalTerms(everyday)
        assertThat(enforced.text).contains("ما يكلف هواي")
        assertThat(enforced.text).contains("التصبغات بالوجه")
    }

    @Test fun `attached Arabic prefixes للـ and بـ and و preserve natural Iraqi grammar`() {
        val s = "بإيروليز نساعد البشرة، والموضوع يفيد للكولاجين وللكلف مع 1064 نانومتر و650 مايكروثانية."
        val enforced = MedicalGlossary.enforceEnglishMedicalTerms(s).text
        assertThat(enforced).contains("بـ Aerolase")
        assertThat(enforced).contains("للـ Collagen")
        assertThat(enforced).contains("للـ Melasma")
        assertThat(enforced).contains("1064 nm")
        assertThat(enforced).contains("650 microseconds")
        assertThat(MedicalGlossary.validate(enforced)).isEmpty()
    }

    @Test fun `validator flags remaining prohibited Arabic medical terms`() {
        val bad = "جهاز بيكوستار يحفز الكولاجين لعلاج الكلف."
        val errors = MedicalGlossary.validate(bad)
        assertThat(errors).isNotEmpty()
        assertThat(errors.joinToString()).contains("PicoStar")
        assertThat(errors.joinToString()).contains("Collagen")
        assertThat(errors.joinToString()).contains("Melasma")
    }

    @Test fun `mixed Iraqi Arabic headline with multiple canonical English terms passes language check`() {
        val headline = "عمق الـ SMAS بـ CFU Èlife"
        assertThat(PlanValidator.languageMatches(headline, ReelLanguage.IRAQI_ARABIC)).isTrue()

        val pureEnglish = "SMAS depth targeting with CFU Èlife"
        assertThat(PlanValidator.languageMatches(pureEnglish, ReelLanguage.IRAQI_ARABIC)).isFalse()
    }

    @Test fun `terminology seed entries never prescribe Arabic target spellings`() {
        TreatmentSeed.terminology.forEach { entry ->
            assertThat(entry.english).isNotEmpty()
            assertThat(entry.arabic).isEmpty()
        }
    }

    @Test fun `normalizer applies medical terminology policy cleanly on spoken lines and headlines`() {
        val raw = "**picostar** بـ ليزر البيكو 532 نانومتر للـ Hyperpigmentation"
        val out = GeneratedContentNormalizer.clean(raw, TextField.SPOKEN)
        assertThat(out).contains("PicoStar")
        assertThat(out).contains("Picosecond laser")
        assertThat(out).contains("532 nm")
        assertThat(out).contains("Hyperpigmentation")
        assertThat(out).doesNotContain("**")
    }
}
