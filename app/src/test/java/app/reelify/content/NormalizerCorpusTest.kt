package app.reelify.content

import app.reelify.core.content.GeneratedContentNormalizer as N
import app.reelify.core.content.TextField
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Regression corpus for the text-quality pipeline (AI output → storage → UI/teleprompter/captions). */
class NormalizerCorpusTest {

    private val terms = listOf("Aerolase", "Picostar", "LaseMD")

    @Test fun `plain English and Iraqi Arabic pass through unchanged`() {
        val en = "Is laser right for everyone? Not always — assessment comes first."
        val ar = "عندكم تصبغات بالوجه ومحتارين شنو الليزر المناسب؟ خلّي أوضحلكم شغلة مهمة."
        assertThat(N.normalize(en).text).isEqualTo(en)
        assertThat(N.normalize(ar).text).isEqualTo(ar)
        assertThat(N.normalize(ar).issues).isEmpty()
    }

    @Test fun `mixed Arabic-English treatment names keep order and preferred spelling`() {
        val r = N.normalize("جهاز aerolase مع PICOSTAR وlasemd", protectedTerms = terms)
        assertThat(r.text).isEqualTo("جهاز Aerolase مع Picostar وLaseMD")
    }

    @Test fun `Arabic-Indic and Western digits and Arabic punctuation are preserved`() {
        val s = "٣ شغلات، 3 جلسات؛ شنو رأيكم؟ ١٠٪"
        assertThat(N.normalize(s).text).isEqualTo(s)
        assertThat(N.normalize("شنو رأيكم ؟ زين ، تمام .").text).isEqualTo("شنو رأيكم؟ زين، تمام.")
    }

    @Test fun `quotes inside text are kept but wrapping quotes on headlines are removed`() {
        assertThat(N.normalize("He said “book first” today").text).isEqualTo("He said “book first” today")
        assertThat(N.normalize("“ثلاث شغلات”", TextField.HEADLINE).text).isEqualTo("ثلاث شغلات")
        assertThat(N.normalize("\"Is it safe?\"", TextField.HOOK).text).isEqualTo("Is it safe?")
    }

    @Test fun `format characters - ZWJ, ZWNJ and bidi marks kept, overrides and zero-width spaces removed`() {
        val zwnj = "می‌خواهم"            // ZWNJ is meaningful
        assertThat(N.normalize(zwnj).text).isEqualTo(zwnj)
        val family = "👨‍👩‍👧"      // emoji ZWJ sequence
        assertThat(N.normalize(family).text).isEqualTo(family)
        assertThat(N.normalize("Aerolase‏ جهاز").text).isEqualTo("Aerolase‏ جهاز") // RLM kept
        assertThat(N.normalize("abc‮def").text).isEqualTo("abcdef")                   // RLO removed
        assertThat(N.normalize("ab​cd﻿").text).isEqualTo("abcd")
        // balanced isolate kept, unbalanced embedding removed
        assertThat(N.normalize("قال ⁦Aerolase⁩ هسه").text).isEqualTo("قال ⁦Aerolase⁩ هسه")
        assertThat(N.normalize("x‫y").text).isEqualTo("xy")
    }

    @Test fun `combining marks - harakat survive and NFC composes`() {
        val withHarakat = "خَلّي أوضّحلكم"
        assertThat(N.normalize(withHarakat).text).isEqualTo(withHarakat)
        assertThat(N.normalize("أ").text).isEqualTo("أ") // alef + hamza above → أ
        assertThat(N.normalize("é").text).isEqualTo("é")
    }

    @Test fun `accidental markdown removed`() {
        assertThat(N.normalize("**Aerolase** is *gentle* for `some` skin").text).isEqualTo("Aerolase is gentle for some skin")
        assertThat(N.normalize("# Title\n- point one\n> quote", TextField.SPOKEN).text).isEqualTo("Title\npoint one\nquote")
        assertThat(N.normalize("2 * 3 = 6").text).isEqualTo("2 * 3 = 6") // lone asterisks in maths are not markdown
    }

    @Test fun `escaped newlines and unicode escapes are decoded and intentional newlines kept`() {
        assertThat(N.normalize("line one\\nline two").text).isEqualTo("line one\nline two")
        assertThat(N.normalize("\\u0634\\u0646\\u0648؟").text).isEqualTo("شنو؟")
        assertThat(N.normalize("a\n\n\n\nb").text).isEqualTo("a\n\nb")
        assertThat(N.normalize("Headline\nsecond", TextField.HEADLINE).text).isEqualTo("Headline second")
    }

    @Test fun `emoji survive including variation selectors`() {
        val s = "Book now ✨ ❤️ 👍🏽"
        assertThat(N.normalize(s).text).isEqualTo(s)
    }

    @Test fun `replacement characters and controls are removed and reported`() {
        val r = N.normalize("ال�ليزر\u0007 مهم")
        assertThat(r.text).isEqualTo("الليزر مهم")
        assertThat(r.issues.joinToString()).contains("replacement")
        assertThat(N.hasArtifacts("ok�")).isTrue()
        assertThat(N.hasArtifacts("شنو الفرق؟")).isFalse()
    }

    @Test fun `Arabic presentation forms become standard letters but religious ligatures stay`() {
        // "لا" written with the presentation-form ligature U+FEFB
        assertThat(N.normalize("ﻻ").text).isEqualTo("لا")
        assertThat(N.normalize("محمد ﷺ").text).isEqualTo("محمد ﷺ")
    }

    @Test fun `mojibake (UTF-8 read as Latin-1) is repaired`() {
        val original = "شنو الفرق"
        val broken = String(original.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
        assertThat(N.normalize(broken).text).isEqualTo(original)
    }

    @Test fun `whitespace normalization`() {
        assertThat(N.normalize("  too   many  spaces \t here  ").text).isEqualTo("too many spaces here")
    }

    @Test fun `long dialogue is preserved byte-for-byte when clean`() {
        val long = (1..60).joinToString(" ") { "هاي جملة رقم $it عن العناية بالبشرة بعد جلسة Aerolase." }
        assertThat(N.normalize(long, protectedTerms = terms).text).isEqualTo(long)
    }

    @Test fun `display filter is minimal and direction follows the dominant script`() {
        assertThat(N.displaySafe("نص� عادي‮")).isEqualTo("نص عادي")
        assertThat(N.displaySafe("unchanged **text**")).isEqualTo("unchanged **text**")
        assertThat(N.isArabicDominant("Aerolase يشتغل بنبضة قصيرة كلش")).isTrue()   // starts with Latin but is Arabic
        assertThat(N.isArabicDominant("Book Aerolase today, شكراً")).isFalse()
        assertThat(N.isArabicDominant("2026")).isFalse()
    }
}
