package app.reelify.eval

import app.reelify.core.content.ClinicalKnowledgeManager
import app.reelify.core.content.CreativeTopic
import app.reelify.core.content.FactualBriefBuilder
import app.reelify.core.content.TopicLibrary
import app.reelify.core.content.TreatmentKnowledgeCatalog
import app.reelify.core.data.TreatmentSeed
import app.reelify.core.model.ReelLanguage
import app.reelify.core.template.BuiltInTemplates
import app.reelify.integration.openai.HookDto
import app.reelify.integration.openai.IdeaDto
import app.reelify.integration.openai.IdeaValidator
import app.reelify.integration.openai.NarrativeOutlineDto
import app.reelify.integration.openai.ScriptDto
import app.reelify.integration.openai.ScriptSectionDto
import app.reelify.integration.openai.ScriptValidator
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Automated creative quality, factual provenance, and deliberate bad-output regression suite.
 * Covers all 9 mandatory failure categories without requiring network access.
 */
class CreativeQualityEvaluationTest {

    private val picostar = TreatmentSeed.treatments.first { it.id == "picostar" }
    private val cfu = TreatmentSeed.treatments.first { it.id == "cfu" }

    @Test fun `curated gold-standard topics pass provenance and knowledge isolation checks`() {
        TreatmentKnowledgeCatalog.byId.values.filter { it.treatmentId != "cleopatra" }.forEach { pack ->
            pack.patientQuestions.forEach { q ->
                val resolved = pack.resolveFactOrQuestion(q.id)
                assertWithMessage("Resolved question ${q.id}").that(resolved).isNotNull()
                // Source IDs must trace to KnowledgeSource (src_*), NEVER confused with fact IDs!
                assertThat(resolved!!.sourceIds).isNotEmpty()
                resolved.sourceIds.forEach { sid ->
                    assertWithMessage("$sid is a real KnowledgeSource on ${q.id}").that(pack.sourceById(sid)).isNotNull()
                }
            }
        }
    }

    @Test fun `focused fact retrieval keeps facial CFU scripts free of body contouring or hyperhidrosis bleed`() {
        val topic = TopicLibrary.topicsFor(cfu, "patient_question").first { it.key == "cfu_pq_01" }
        val brief = FactualBriefBuilder.build(
            treatment = cfu,
            pack = TreatmentKnowledgeCatalog.cfu,
            topic = topic,
            topicTitle = topic.title.ar,
            topicBrief = topic.brief,
            userIdea = "",
            arabic = true,
        )
        val factIds = brief.allIncludedFactIds
        assertThat(factIds).doesNotContain("cfu_app_hyperhidrosis")
        assertThat(factIds).doesNotContain("cfu_app_body_contour")
        assertThat(brief.excludedTopicAreas).containsAtLeast("hyperhidrosis", "body_contouring")
    }

    @Test fun `bad fixture 1 - cross-section conceptual repetition is rejected`() {
        val dto = ScriptDto(
            title = "PicoStar Overview",
            language = "en",
            creativeAngle = "PicoStar intro",
            hookOptions = validEnHooks(),
            sections = listOf(
                ScriptSectionDto(1, "PicoStar is an advanced picosecond laser that breaks skin pigment cleanly with ultra short pulses for better clarity.", "PicoStar Tech", listOf("PicoStar")),
                ScriptSectionDto(2, "This picosecond laser breaks pigment cleanly in skin with ultra short PicoStar laser pulses for clearer tone.", "Short Pulses", listOf("pigment")),
                ScriptSectionDto(3, "PicoStar laser pulses break pigment cleanly and results depend on skin clarity and consultation.", "Laser Results", listOf("clarity")),
            ),
            ctaStyle = "book_assessment",
            medicalReview = emptyList(),
        )
        val check = ScriptValidator.validateScript(dto, BuiltInTemplates.EXPERT_EXPLAINS, ReelLanguage.ENGLISH, 25, emptyList())
        assertThat(check.ok).isFalse()
        assertThat(check.errors.joinToString()).contains("repeats content")
    }

    @Test fun `bad fixture 2 - generic promotional filler without clinical substance is rejected`() {
        val dto = ScriptDto(
            title = "Glowing Skin Today",
            language = "en",
            creativeAngle = "Promo",
            hookOptions = validEnHooks(),
            sections = listOf(
                ScriptSectionDto(1, "Unlock glowing skin and experience the difference with our modern clinic laser today.", "Glowing Skin", emptyList()),
                ScriptSectionDto(2, "Transform your skin with cutting-edge technology designed for every patient.", "Modern Care", emptyList()),
                ScriptSectionDto(3, "Schedule your consultation today for flawless radiance.", "Visit Us", emptyList()),
            ),
            ctaStyle = "book_assessment",
            medicalReview = emptyList(),
        )
        val check = ScriptValidator.validateScript(dto, BuiltInTemplates.EXPERT_EXPLAINS, ReelLanguage.ENGLISH, 25, emptyList())
        assertThat(check.ok).isFalse()
        assertThat(check.errors.joinToString().lowercase()).contains("filler")
    }

    @Test fun `bad fixture 3 - unrelated device indications leaking into focused topic are rejected`() {
        val brief = FactualBriefBuilder.build(
            treatment = cfu,
            pack = TreatmentKnowledgeCatalog.cfu,
            topic = TopicLibrary.topicsFor(cfu, "expert_explains").first { "face" in it.brief.lowercase() || "smas" in it.factIds.joinToString().lowercase() },
            topicTitle = "Why 4.5 mm SMAS depth matters for lifting",
            topicBrief = "Facial SMAS collagen tightening",
            userIdea = "",
            arabic = false,
        )
        val dto = ScriptDto(
            title = "CFU Face Lifting Depths",
            language = "en",
            creativeAngle = "SMAS depth targeting",
            hookOptions = validEnHooks(),
            sections = listOf(
                ScriptSectionDto(1, "Surface creams cannot reach the structural lifting plane under facial skin.", "Depth Matters", emptyList()),
                ScriptSectionDto(2, "CFU Èlife uses focused HIFU at 4.5 mm for facial SMAS tightening and also treats axillary hyperhidrosis sweat glands.", "SMAS 4.5 mm", listOf("SMAS")),
                ScriptSectionDto(3, "Collagen builds over 8 to 12 weeks for firmer jawline definition.", "Natural Timeline", emptyList()),
            ),
            ctaStyle = "save_for_later",
            medicalReview = emptyList(),
        )
        val check = ScriptValidator.validateScript(dto, BuiltInTemplates.EXPERT_EXPLAINS, ReelLanguage.ENGLISH, 25, emptyList(), factualBrief = brief, treatmentName = cfu.name)
        assertThat(check.ok).isFalse()
        assertThat(check.errors.joinToString()).contains("unrelated 'hyperhidrosis'")
    }

    @Test fun `bad fixture 4 and 5 - unsafe cure claims and CFU called a laser are rejected`() {
        val dto = ScriptDto(
            title = "CFU Laser Guaranteed",
            language = "en",
            creativeAngle = "CFU claims",
            hookOptions = validEnHooks(),
            sections = listOf(
                ScriptSectionDto(1, "Tired of sagging skin around your lower jawline? Look at this step.", "Jawline Care", emptyList()),
                ScriptSectionDto(2, "CFU is a powerful laser that gives 100% permanent cure in one guaranteed session forever.", "Instant Cure", emptyList()),
                ScriptSectionDto(3, "No risk at all, suitable for everyone looking for surgical-grade results.", "Zero Risk", emptyList()),
            ),
            ctaStyle = "book_assessment",
            medicalReview = emptyList(),
        )
        val check = ScriptValidator.validateScript(dto, BuiltInTemplates.EXPERT_EXPLAINS, ReelLanguage.ENGLISH, 25, cfu.forbiddenClaims, treatmentName = cfu.name)
        assertThat(check.ok).isFalse()
        assertThat(check.errors.joinToString()).contains("CFU Èlife is HIFU (focused ultrasound)")
        assertThat(check.errors.joinToString()).contains("unsafe claim")
    }

    @Test fun `bad fixture 6 - nonsensical, generic, and ungrounded AI topic titles are rejected by IdeaValidator`() {
        val badIdeas = listOf(
            IdeaDto("Why PicoStar is the best", "Promo", "PicoStar is amazing for everyone today!", "curiosity", supportingFactIds = emptyList()),
            IdeaDto("(propose fresh ideas)", "Generic", "Come see our laser machine soon!", "question", supportingFactIds = emptyList()),
            IdeaDto("PicoStar treats broken bones cleanly", "Nonsense", "We repair bone fractures quickly cleanly.", "surprising_fact", supportingFactIds = listOf("nonexistent_fact_id")),
        )
        val (accepted, rejections) = IdeaValidator.evaluateAndRank(
            rawIdeas = badIdeas,
            treatment = picostar,
            template = BuiltInTemplates.EXPERT_EXPLAINS,
            language = ReelLanguage.ENGLISH,
            pack = TreatmentKnowledgeCatalog.picostar,
            curatedBenchmarks = emptyList(),
            recentHistory = emptyList(),
            terminology = TreatmentSeed.terminology,
            protectedTerms = listOf("PicoStar"),
        )
        assertThat(accepted).isEmpty()
        assertThat(rejections).hasSize(3)
    }

    @Test fun `bad fixture 7 and 8 - prohibited Arabic medical transliterations and stiff MSA fail validation`() {
        val dto = ScriptDto(
            title = "بيكوستار لعلاج الكلف",
            language = "ar-IQ",
            creativeAngle = "Melasma",
            hookOptions = listOf(
                HookDto("curiosity", "هل تعانون من الكلف وتريدون الحل مع بيكوستار؟"),
                HookDto("question", "كيف يعمل كولاجين الأدمة بالعيادة؟"),
                HookDto("myth", "فكرة منتشرة عن الليزر وتأثيره."),
            ),
            sections = listOf(
                ScriptSectionDto(1, "سوف نتحدث اليوم عن الكلف الذي يصيب البشرة حيث يجب العلاج فوراً.", "علاج الكلف", emptyList()),
                ScriptSectionDto(2, "إن جهاز بيكوستار يعمل على تحفيز الكولاجين في طبقة الأدمة وفقاً للحالة.", "بيكوستار", emptyList()),
                ScriptSectionDto(3, "يرجى زيارة العيادة لحجز الاستشارة وتحديد الخطة المناسبة لكل مريض.", "الاستشارة", emptyList()),
            ),
            ctaStyle = "book_assessment",
            medicalReview = emptyList(),
        )
        val check = ScriptValidator.validateScript(dto, BuiltInTemplates.EXPERT_EXPLAINS, ReelLanguage.IRAQI_ARABIC, 22, emptyList())
        assertThat(check.ok).isFalse()
        assertThat(check.errors.joinToString()).contains("Medical term must be in English")
        assertThat(check.errors.joinToString()).contains("formal MSA")
    }

    @Test fun `bad fixture 9 - missing educational payoff ending only in consultation disclaimer is rejected`() {
        val dto = ScriptDto(
            title = "PicoStar Sessions",
            language = "en",
            creativeAngle = "Empty ending",
            narrativeOutline = NarrativeOutlineDto(
                openingQuestion = "Why multiple visits?",
                centralInsight = "Immune clearance takes weeks",
                viewerLearnedPayoff = "Every patient is different, book an assessment.",
            ),
            hookOptions = validEnHooks(),
            sections = listOf(
                ScriptSectionDto(1, "Many patients wonder why dark ink needs several visits instead of finishing in one afternoon.", "Why Multiple Visits", emptyList()),
                ScriptSectionDto(2, "PicoStar uses <400 ps photoacoustic shockwaves so macrophages clear shattered ink gradually.", "Immune Clearance", emptyList()),
                ScriptSectionDto(3, "Every patient is different and results vary, so book a consultation today for individual assessment.", "Book Assessment", emptyList()),
            ),
            ctaStyle = "book_assessment",
            medicalReview = emptyList(),
        )
        val check = ScriptValidator.validateScript(dto, BuiltInTemplates.PATIENT_QUESTION, ReelLanguage.ENGLISH, 25, emptyList())
        assertThat(check.ok).isFalse()
        assertThat(check.errors.joinToString()).contains("payoff")
    }

    @Test fun `clinical knowledge clusters surface underused scientific areas cleanly`() {
        val clusters = ClinicalKnowledgeManager.selectClustersForIdeaGeneration(
            pack = TreatmentKnowledgeCatalog.picostar,
            recentFactIds = setOf("pico_sess_factors"),
            count = 5,
        )
        assertThat(clusters).hasSize(5)
        assertThat(clusters.map { it.topicArea }).containsAnyOf("picosecond_mechanism", "wavelengths", "lymphatic_clearance")
    }

    private fun validEnHooks() = listOf(
        HookDto("curiosity", "Why do two patients react differently to laser sessions?"),
        HookDto("common_mistake", "Most people judge a device before checking wavelength."),
        HookDto("question", "What actually happens inside tissue during energy passes?"),
    )
}
