package app.reelify.e2e

import app.reelify.ai.AiTestSupport
import app.reelify.core.content.DialectChecker
import app.reelify.core.content.FactualBriefBuilder
import app.reelify.core.content.IraqiStyleGuide
import app.reelify.core.content.TopicLibrary
import app.reelify.core.content.TreatmentKnowledgeCatalog
import app.reelify.core.data.TreatmentSeed
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.TeleprompterMode
import app.reelify.core.template.BuiltInTemplates
import app.reelify.core.template.ProductionPlanner
import app.reelify.core.timing.TimingEstimator
import app.reelify.feature.recording.TeleprompterPacer
import app.reelify.integration.openai.HookDto
import app.reelify.integration.openai.OpenAiClient
import app.reelify.integration.openai.ScriptContext
import app.reelify.integration.openai.ScriptDto
import app.reelify.integration.openai.ScriptSectionDto
import app.reelify.integration.openai.ScriptValidator
import app.reelify.integration.openai.ScriptWriter
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * End-to-end creative, clinical-knowledge, script-budget, and teleprompter-pacing verification
 * across all three priority clinical scenarios:
 *  - Scenario A: PicoStar · Tattoo Removal · 30s · Iraqi Arabic · myth_buster
 *  - Scenario B: CFU Èlife · Facial Lifting · 45s · Iraqi Arabic · patient_question
 *  - Scenario C: Aerolase · Acne & Redness · 60s · English · expert_explains
 */
class Reelify21ScenariosTest {

    private lateinit var server: MockWebServer
    private lateinit var writer: ScriptWriter
    private val json = Json { encodeDefaults = true }
    private val timing = TimingEstimator()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        writer = ScriptWriter(
            OpenAiClient(AiTestSupport.http(), AiTestSupport.creds(), { "gpt-6.2-sol" }, server.url("/v1/").toString(), 1, 1),
            { timing },
        )
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `Scenario A PicoStar tattoo removal 30s Iraqi Arabic myth_buster`() = runTest {
        val treatment = TreatmentSeed.treatments.first { it.id == "picostar" }
        val template = BuiltInTemplates.MYTH_BUSTER
        val suggestions = TopicLibrary.suggestions(treatment, template.id, emptyList(), arabic = true, limit = 20)
        assertThat(suggestions.size).isAtLeast(10)
        val pickedTopic = TopicLibrary.topicsFor(treatment, template.id).first { it.key == "pico_mb_01" }
        val brief = FactualBriefBuilder.build(
            treatment = treatment,
            pack = TreatmentKnowledgeCatalog.picostar,
            topic = pickedTopic,
            topicTitle = pickedTopic.title.ar,
            topicBrief = pickedTopic.brief,
            userIdea = "",
            arabic = true,
        )
        assertThat(brief.toPromptBlock(arabic = true)).contains("1064")

        val dto = ScriptDto(
            title = "خرافة مسح التاتو بجلسة وحدة",
            language = "ar-IQ",
            creativeAngle = "Photoacoustic physics vs layered ink depth",
            hookOptions = listOf(
                HookDto("myth", "فكرة غلط: بيكوستار يمسح التاتو من جلسة وحدة."),
                HookDto("curiosity", "ليش حبر التاتو ما يروح بيوم واحد رغم قوة البيكوستار؟"),
                HookDto("expert_insight", "شلون جسمك يصرف حبر التاتو بعد التكسير الصوتي؟"),
            ),
            sections = listOf(
                ScriptSectionDto(1, "هواي ناس يتوقعون يطلعون بدون أي أثر، وهذا مو دقيق.", "مو من جلسة", listOf("أثر")),
                ScriptSectionDto(2, "جهاز PicoStar يكسر الحبر بالضغط الصوتي على موجتين 1064 و532 وجسمك يسحبه عبر اللمف تدريجياً.", "ضغط صوتي", listOf("PicoStar")),
                ScriptSectionDto(3, "عدد الجلسات يعتمد على عمق الحبر ولونه وموقعه، فخلّي خطتك واقعية.", "خطة واقعية", listOf("عمق")),
            ),
            ctaStyle = "save_for_later",
            medicalReview = emptyList(),
        )
        val budget = ScriptValidator.wordBudget(timing, ReelLanguage.IRAQI_ARABIC, ReelDuration.S30.sectionMs)
        val preCheck = ScriptValidator.validateScript(dto, template, ReelLanguage.IRAQI_ARABIC, budget, emptyList())
        assertWithMessage("Scenario A validation: ${preCheck.errors}").that(preCheck.errors).isEmpty()

        server.enqueue(MockResponse().setBody(AiTestSupport.responsesBody(json.encodeToString(ScriptDto.serializer(), dto))))

        val ctx = ScriptContext(
            language = ReelLanguage.IRAQI_ARABIC,
            duration = ReelDuration.S30,
            template = template,
            treatment = treatment,
            terminology = TreatmentSeed.terminology,
            brand = null,
            topicTitle = pickedTopic.title.ar,
            topicBrief = pickedTopic.brief,
            examples = IraqiStyleGuide.builtInExamples,
            factualBrief = brief,
        )
        val out = writer.generate(ctx, pickedTopic.key)
        assertThat(out.scripts).hasSize(3)
        assertThat(DialectChecker.analyze(out.scripts.joinToString(" ") { it.spokenScript }).likelyFormal).isFalse()
        assertThat(DialectChecker.genericFillerHits(out.scripts.joinToString(" ") { it.spokenScript })).isEmpty()
        assertThat(out.scripts[1].spokenScript).contains("PicoStar")

        val speechMs = timing.estimatedDurationMs(out.scripts[1].spokenScript, ReelLanguage.IRAQI_ARABIC)
        val pacePlan = TeleprompterPacer.computePlan(420f, 200f, ReelDuration.S30.sectionMs, speechMs, TeleprompterMode.AUTO_FIT, 1.0f)
        assertThat(pacePlan.effectiveDurationMs).isAtLeast(5_000L)
    }

    @Test fun `Scenario B CFU Elife facial lifting 45s Iraqi Arabic patient_question never mentions laser as identity`() = runTest {
        val treatment = TreatmentSeed.treatments.first { it.id == "cfu" }
        val template = BuiltInTemplates.PATIENT_QUESTION
        val pickedTopic = TopicLibrary.topicsFor(treatment, template.id).first { it.key == "cfu_pq_01" }
        val brief = FactualBriefBuilder.build(
            treatment = treatment,
            pack = TreatmentKnowledgeCatalog.cfu,
            topic = pickedTopic,
            topicTitle = pickedTopic.title.ar,
            topicBrief = pickedTopic.brief,
            userIdea = "",
            arabic = true,
        )
        assertThat(brief.toPromptBlock(arabic = true)).contains("مو ليزر")

        val dto = ScriptDto(
            title = "هل CFU ليزر ويناسب الصيف؟",
            language = "ar-IQ",
            creativeAngle = "Focused ultrasound clarity + neocollagenesis timeline",
            hookOptions = listOf(
                HookDto("question", "سؤال ينسأل كل صيف: هل CFU Èlife ليزر ولازم ننتظر الشتا؟"),
                HookDto("curiosity", "ليش تكدر تسوي جلسة الشد بـ CFU بعز الصيف بدون ما تتقشر بشرتك؟"),
                HookDto("expert_insight", "فرق مهم بين الهايفو وأجهزة الليزر بالعيادة."),
            ),
            sections = listOf(
                ScriptSectionDto(1, "ناس هواي يأجلون جلسة الشد لأن عبالهم كل أجهزة العيادة ليزر يحرّك سطح البشرة.", "مو ليزر", listOf("ليزر")),
                ScriptSectionDto(2, "جهاز CFU Èlife يشتغل بالموجات فوق الصوتية المركّزة HIFU مو بالليزر، مع تقنية Centerless وتبريد ACE يوصل لأعماق 1.5 و3.0 و4.5 ملم بدون جرح للسطح.", "ألتراساوند مو ليزر", listOf("HIFU")),
                ScriptSectionDto(3, "النتيجة ما تخلص بنفس اللحظة؛ بناء الكولاجين يتطور تدريجياً خلال شهرين إلى ثلاثة حسب تقييم الترهل.", "نتيجة تدريجية", listOf("الكولاجين")),
            ),
            ctaStyle = "comment_question",
            medicalReview = emptyList(),
        )
        val budget = ScriptValidator.wordBudget(timing, ReelLanguage.IRAQI_ARABIC, ReelDuration.S45.sectionMs)
        val preCheck = ScriptValidator.validateScript(dto, template, ReelLanguage.IRAQI_ARABIC, budget, emptyList())
        assertWithMessage("Scenario B validation: ${preCheck.errors}").that(preCheck.errors).isEmpty()

        server.enqueue(MockResponse().setBody(AiTestSupport.responsesBody(json.encodeToString(ScriptDto.serializer(), dto))))

        val ctx = ScriptContext(
            language = ReelLanguage.IRAQI_ARABIC,
            duration = ReelDuration.S45,
            template = template,
            treatment = treatment,
            terminology = TreatmentSeed.terminology,
            brand = null,
            topicTitle = pickedTopic.title.ar,
            topicBrief = pickedTopic.brief,
            examples = IraqiStyleGuide.builtInExamples,
            factualBrief = brief,
        )
        val out = writer.generate(ctx, pickedTopic.key)
        assertThat(out.scripts).hasSize(3)
        assertThat(out.scripts[1].spokenScript).contains("HIFU")
        assertThat(out.scripts[1].spokenScript).contains("مو بالليزر")

        val merged = ProductionPlanner.mergeScript(template, ProductionPlanner.sections(template, emptyList(), { "sec" }, 0), out.scripts, false, null, 0)
        assertThat(merged.map { it.index }).containsExactly(1, 2, 3).inOrder()
    }

    @Test fun `Scenario C Aerolase acne and redness 60s English expert_explains fills long shot duration`() = runTest {
        val treatment = TreatmentSeed.treatments.first { it.id == "aerolase" }
        val template = BuiltInTemplates.EXPERT_EXPLAINS
        val pickedTopic = TopicLibrary.topicsFor(treatment, template.id).first { it.key == "aero_ee_01" }
        val brief = FactualBriefBuilder.build(
            treatment = treatment,
            pack = TreatmentKnowledgeCatalog.aerolase,
            topic = pickedTopic,
            topicTitle = pickedTopic.title.en,
            topicBrief = pickedTopic.brief,
            userIdea = "",
            arabic = false,
        )

        val dto = ScriptDto(
            title = "Why 650 Microseconds Changes Aerolase",
            language = "en",
            creativeAngle = "Pulse width vs thermal relaxation across acne and redness",
            hookOptions = listOf(
                HookDto("expert_insight", "Why can 1064 nm energy treat darker skin without numbing gel?"),
                HookDto("curiosity", "What changes when a 1064 nm pulse drops to 650 microseconds?"),
                HookDto("common_mistake", "Judging acne lasers by how much they peel misses real laser physics."),
            ),
            sections = listOf(
                ScriptSectionDto(1, "Most patients expect thick numbing cream before laser treatment, especially when treating darker or olive skin tones for acne and redness.", "650 Microseconds Explained", listOf("numbing", "olive")),
                ScriptSectionDto(2, "Delivering 1064 nanometer light in 650 microseconds heats melanin in dark spots, hemoglobin in redness, and overactive oil glands while sparing the surface epidermis cleanly across Fitzpatrick skin types.", "Three Clinical Targets", listOf("650", "hemoglobin")),
                ScriptSectionDto(3, "Because the outer skin barrier remains intact with little downtime, patients protect gains with daily sunscreen while true pitted acne scars still get routed to resurfacing.", "Protect Your Barrier", listOf("sunscreen", "epidermis")),
            ),
            ctaStyle = "book_assessment",
            medicalReview = emptyList(),
        )
        val budget = ScriptValidator.wordBudget(timing, ReelLanguage.ENGLISH, ReelDuration.S60.sectionMs)
        val preCheck = ScriptValidator.validateScript(dto, template, ReelLanguage.ENGLISH, budget, emptyList())
        assertWithMessage("Scenario C validation: ${preCheck.errors}").that(preCheck.errors).isEmpty()

        server.enqueue(MockResponse().setBody(AiTestSupport.responsesBody(json.encodeToString(ScriptDto.serializer(), dto))))

        val ctx = ScriptContext(
            language = ReelLanguage.ENGLISH,
            duration = ReelDuration.S60,
            template = template,
            treatment = treatment,
            terminology = TreatmentSeed.terminology,
            brand = null,
            topicTitle = pickedTopic.title.en,
            topicBrief = pickedTopic.brief,
            factualBrief = brief,
        )
        val out = writer.generate(ctx, pickedTopic.key)
        assertThat(out.scripts).hasSize(3)
        out.scripts.forEach { sec ->
            val words = timing.countWords(sec.spokenScript)
            assertThat(words).isAtLeast(25)
        }
        assertThat(DialectChecker.genericFillerHits(out.scripts.joinToString(" ") { it.spokenScript })).isEmpty()
    }
}
