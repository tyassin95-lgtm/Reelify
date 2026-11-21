package app.reelify.eval

import app.reelify.core.content.DialectChecker
import app.reelify.core.content.IraqiStyleGuide
import app.reelify.core.data.TreatmentSeed
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.security.CredentialRepository
import app.reelify.core.security.AiProvider
import app.reelify.core.security.InMemorySecretStore
import app.reelify.core.template.BuiltInTemplates
import app.reelify.core.timing.TimingEstimator
import app.reelify.integration.openai.OpenAiClient
import app.reelify.integration.openai.ScriptContext
import app.reelify.integration.openai.ScriptValidator
import app.reelify.integration.openai.ScriptWriter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** One creative-quality evaluation scenario (see docs/eval/CREATIVE_EVAL.md). */
data class EvalScenario(val id: String, val treatment: String, val template: String, val language: ReelLanguage, val duration: ReelDuration, val topic: String)

object CreativeEvalSet {
    private val AR = ReelLanguage.IRAQI_ARABIC; private val EN = ReelLanguage.ENGLISH
    private val S30 = ReelDuration.S30; private val S45 = ReelDuration.S45; private val S60 = ReelDuration.S60
    val scenarios = listOf(
        EvalScenario("E01", "aerolase", "expert_explains", AR, S30, "ليش التقييم قبل العلاج مهم"),
        EvalScenario("E02", "aerolase", "myth_buster", AR, S30, "فكرة غلط منتشرة عن إيروليز"),
        EvalScenario("E03", "aerolase", "problem_approach", EN, S45, "Why acne keeps coming back"),
        EvalScenario("E04", "aerolase", "patient_question", AR, S45, "الجلسة تألّم؟"),
        EvalScenario("E05", "aerolase", "three_things", EN, S30, "Three things to know before Aerolase"),
        EvalScenario("E06", "picostar", "treatment_showcase", AR, S60, "تعرّفوا على بيكوستار"),
        EvalScenario("E07", "picostar", "myth_buster", EN, S30, "Myth: lasers thin your skin"),
        EvalScenario("E08", "picostar", "three_things", AR, S45, "التصبغات والشمس"),
        EvalScenario("E09", "picostar", "patient_question", EN, S60, "How many sessions will I need?"),
        EvalScenario("E10", "dermablate", "expert_explains", EN, S45, "Not all scars are the same"),
        EvalScenario("E11", "dermablate", "problem_approach", AR, S60, "ملمس البشرة والمسامات"),
        EvalScenario("E12", "dermablate", "patient_question", AR, S30, "شكد وقت تحتاج البشرة ترتاح؟"),
        EvalScenario("E13", "cfu", "expert_explains", AR, S30, "ليش التقييم قبل العلاج مهم"),
        EvalScenario("E14", "cfu", "three_things", EN, S45, "Three things to know before your session"),
        EvalScenario("E15", "lasemd", "treatment_showcase", EN, S30, "What happens during a LaseMD session?"),
        EvalScenario("E16", "lasemd", "three_things", AR, S60, "ثلاث شغلات قبل جلسة ليز إم دي"),
        EvalScenario("E17", "lasemd", "myth_buster", AR, S45, "فكرة غلط عن تقشير البشرة بالليزر"),
        EvalScenario("E18", "cleopatra", "patient_question", EN, S30, "Is this treatment right for everyone?"),
        EvalScenario("E19", "cleopatra", "expert_explains", AR, S45, "نصيحتي الصريحة قبل أي علاج"),
        EvalScenario("E20", "diamond_peeling", "treatment_showcase", AR, S30, "ورا الكواليس: شلون نحضّر الجلسة"),
        EvalScenario("E21", "diamond_peeling", "problem_approach", EN, S60, "Rough texture and visible pores"),
        EvalScenario("E22", "diamond_peeling", "three_things", AR, S30, "خطوة العناية اللي الناس تنساها"),
        EvalScenario("E23", "general", "expert_explains", EN, S30, "My honest advice before any laser"),
        EvalScenario("E24", "general", "myth_buster", AR, S30, "فكرة غلط: كل الليزرات نفس الشي"),
        EvalScenario("E25", "general", "patient_question", AR, S60, "شنو يأثر على النتيجة؟"),
        EvalScenario("E26", "general", "three_things", EN, S45, "What affects your results?"),
        EvalScenario("E27", "aerolase", "treatment_showcase", EN, S60, "Meet the Aerolase"),
        EvalScenario("E28", "picostar", "expert_explains", AR, S30, "التصبغات ترجع؟"),
        EvalScenario("E29", "dermablate", "myth_buster", EN, S60, "A common misconception about resurfacing"),
        EvalScenario("E30", "lasemd", "patient_question", AR, S30, "متى أشوف النتيجة؟"),
        EvalScenario("E31", "diamond_glow", "expert_explains", EN, S45, "Inside DiamondGlow 3-in-1 skincare mechanics"),
        EvalScenario("E32", "diamond_glow", "patient_question", AR, S30, "شنو الفرق بين دايموند جلو والتقشير الماسي العادي؟"),
    )
}

/**
 * Live creative-quality harness. Skipped unless REELIFY_OPENAI_API_KEY is set in the environment.
 * Writes every generated script with automatic checks to app/build/reports/creative-eval.md for
 * HUMAN review (naturalness, originality, hook quality, accuracy, caution, timing, repetition,
 * clarity). Automatic checks never replace that review.
 */
class CreativeEvalHarness {
    @Test fun evalSetIsBalanced() {
        val s = CreativeEvalSet.scenarios
        assertThat(s).hasSize(32)
        assertThat(s.map { it.id }.toSet()).hasSize(32)
        assertThat(s.map { it.template }.toSet()).containsExactlyElementsIn(BuiltInTemplates.all.map { it.id })
        assertThat(s.map { it.treatment }.toSet()).containsExactlyElementsIn(TreatmentSeed.treatments.map { it.id })
        assertThat(s.count { it.language == ReelLanguage.IRAQI_ARABIC }).isAtLeast(14)
        assertThat(s.count { it.language == ReelLanguage.ENGLISH }).isAtLeast(12)
        assertThat(s.map { it.duration }.toSet()).hasSize(3)
    }

    @Test fun runLiveEvaluation() = runBlocking {
        val key = System.getenv("REELIFY_OPENAI_API_KEY").orEmpty()
        assumeTrue("Set REELIFY_OPENAI_API_KEY to run the live creative evaluation", key.isNotBlank())
        val http = OkHttpClient.Builder().callTimeout(180, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()
        val model = System.getenv("REELIFY_OPENAI_MODEL")?.takeIf { it.isNotBlank() } ?: "gpt-6.2-sol"
        val writer = ScriptWriter(OpenAiClient(http, CredentialRepository(InMemorySecretStore(), mapOf(AiProvider.OPENAI to key)), { model }), { TimingEstimator() })
        val report = StringBuilder("# Creative evaluation — live run (model $model)\n\nAutomatic checks only. Score each scenario by hand using docs/eval/CREATIVE_EVAL.md.\n\n")
        val hooksSeen = mutableListOf<String>()
        for (sc in CreativeEvalSet.scenarios) {
            val t = TreatmentSeed.treatments.first { it.id == sc.treatment }
            val ctx = ScriptContext(sc.language, sc.duration, BuiltInTemplates.byId(sc.template)!!, t, TreatmentSeed.terminology, null, sc.topic,
                recentHooks = hooksSeen.takeLast(10), examples = if (sc.language == ReelLanguage.IRAQI_ARABIC) IraqiStyleGuide.builtInExamples else emptyList())
            report.append("## ${sc.id} · ${sc.treatment} · ${sc.template} · ${sc.language.code} · ${sc.duration.label}\nTopic: ${sc.topic}\n\n")
            runCatching { writer.generate(ctx, sc.id) }.onSuccess { w ->
                hooksSeen += w.meta.activeHook
                w.meta.hookOptions.forEach { report.append("- hook (${it.style}): ${it.text}\n") }
                w.scripts.forEach { s ->
                    val d = DialectChecker.analyze(s.spokenScript)
                    report.append("\n**Shot ${s.index}** (${TimingEstimator().countWords(s.spokenScript)} words; formal markers: ${d.formalHits.joinToString().ifBlank { "none" }}): ${s.spokenScript}\n  headline: ${s.onScreenHeadline}\n")
                }
                val maxSim = w.meta.hookOptions.let { hs -> hs.indices.flatMap { i -> (i + 1 until hs.size).map { j -> ScriptValidator.similarity(hs[i].text, hs[j].text) } }.maxOrNull() ?: 0f }
                report.append("\nAuto: hook similarity max %.2f · notes: %s\n\n| Relevance | Originality | Hook | Naturalness | Accuracy | Caution | Timing | Repetition | Clarity |\n|---|---|---|---|---|---|---|---|---|\n| | | | | | | | | |\n\n".format(maxSim, w.meta.qualityNotes.joinToString("; ").ifBlank { "none" }))
            }.onFailure { report.append("FAILED: ${it.message}\n\n") }
        }
        File("build/reports").mkdirs()
        File("build/reports/creative-eval.md").writeText(report.toString())
    }
}
