package app.reelify.ai

import app.reelify.ai.AiTestSupport.creds
import app.reelify.ai.AiTestSupport.http
import app.reelify.ai.AiTestSupport.responsesBody
import app.reelify.core.content.IraqiStyleGuide
import app.reelify.core.data.TreatmentSeed
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelSection
import app.reelify.core.model.TerminologyEntry
import app.reelify.core.template.BuiltInTemplates
import app.reelify.core.template.ProductionPlanner
import app.reelify.core.template.ReelTemplate
import app.reelify.core.timing.TimingEstimator
import app.reelify.integration.common.AiError
import app.reelify.integration.openai.ClaimFlagDto
import app.reelify.integration.openai.HookDto
import app.reelify.integration.openai.HookSetDto
import app.reelify.integration.openai.IdeaDto
import app.reelify.integration.openai.IdeasDto
import app.reelify.integration.openai.OpenAiClient
import app.reelify.integration.openai.RewriteDto
import app.reelify.integration.openai.ScriptAction
import app.reelify.integration.openai.ScriptContext
import app.reelify.integration.openai.ScriptDto
import app.reelify.integration.openai.ScriptPrompts
import app.reelify.integration.openai.ScriptSchemas
import app.reelify.integration.openai.ScriptSectionDto
import app.reelify.integration.openai.ScriptValidator
import app.reelify.integration.openai.ScriptWriter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ScriptWriterTest {
    private lateinit var server: MockWebServer
    private lateinit var writer: ScriptWriter
    private val json = Json { encodeDefaults = true }

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        writer = ScriptWriter(OpenAiClient(http(), creds(), { "gpt-6.2-sol" }, server.url("/v1/").toString(), 1, 1), { TimingEstimator() })
    }
    @After fun tearDown() { server.shutdown() }

    private fun ctx(lang: ReelLanguage = ReelLanguage.IRAQI_ARABIC, template: ReelTemplate = BuiltInTemplates.EXPERT_EXPLAINS) = ScriptContext(
        lang, ReelDuration.S30, template, TreatmentSeed.treatments.first { it.id == "aerolase" },
        listOf(TerminologyEntry("t", "Aerolase", "إيروليز")), null, topicTitle = "أكثر غلطة يسويها الناس",
        recentHooks = listOf("old hook used yesterday"), examples = IraqiStyleGuide.builtInExamples,
    )

    private val iqHooks = listOf(
        HookDto("curiosity", "تعرفون ليش بعض الناس ما يشوفون نتيجة من الليزر؟"),
        HookDto("common_mistake", "أكثر غلطة أشوفها قبل أي جلسة ليزر."),
        HookDto("expert_insight", "من خبرتي بالعيادة، السر بالخطة مو بالجهاز."),
    )
    private val iqSections = listOf(
        ScriptSectionDto(1, "خلّي أحجيلكم شغلة مهمة عن الإيروليز وحب الشباب.", "شغلة مهمة", listOf("الإيروليز")),
        ScriptSectionDto(2, "الإيروليز يشتغل بنبضة قصيرة كلش، وعدد الجلسات يختلف حسب حالة البشرة.", "شلون يشتغل", listOf("نبضة")),
        ScriptSectionDto(3, "إذا محتارين، احجزوا تقييم ونشوف سوا شنو الأنسب إلكم.", "احجز تقييم", listOf("تقييم")),
    )
    private fun script(sections: List<ScriptSectionDto> = iqSections, hooks: List<HookDto> = iqHooks, lang: String = "ar-IQ") =
        json.encodeToString(ScriptDto.serializer(), ScriptDto("غلطة شائعة", lang, "Common mistake angle", hooks, sections, "book_assessment", emptyList()))

    private fun enqueue(text: String) = server.enqueue(MockResponse().setBody(responsesBody(text)))
    private fun body(): kotlinx.serialization.json.JsonObject = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    @Test fun `generate returns script-only content and the hook leads shot 1`() = runTest {
        enqueue(script())
        val w = writer.generate(ctx(), "one_mistake")
        assertThat(w.scripts.map { it.index }).containsExactly(1, 2, 3).inOrder()
        assertThat(w.scripts[0].spokenScript).startsWith(iqHooks[0].text)
        assertThat(w.scripts[0].spokenScript).endsWith(iqSections[0].spokenScript)
        assertThat(w.meta.hookOptions.map { it.style }).containsExactly("curiosity", "common_mistake", "expert_insight").inOrder()
        assertThat(w.meta.activeHook).isEqualTo(iqHooks[0].text)
        assertThat(w.meta.promptVersion).isEqualTo(ScriptPrompts.VERSION)
        val req = body()
        // strict structured output with the compact script schema
        val format = req["text"]!!.jsonObject["format"]!!.jsonObject
        assertThat(format["type"]!!.jsonPrimitive.content).isEqualTo("json_schema")
        assertThat(format["strict"]!!.jsonPrimitive.content).isEqualTo("true")
        // dedicated Iraqi system prompt with owner-reviewable reference lines
        val instructions = req["instructions"]!!.jsonPrimitive.content
        assertThat(instructions).contains("REFERENCE LINES")
        assertThat(instructions).contains(IraqiStyleGuide.builtInExamples.first().better)
        assertThat(instructions).doesNotContain(ScriptPrompts.SYSTEM_EN.lines().first())
        val user = req["input"].toString()
        assertThat(user).contains("RECENTLY USED HOOKS")
        assertThat(user).contains("PRODUCTION TEMPLATE")
    }

    @Test fun `the AI schema cannot express production settings`() {
        val schema = ScriptSchemas.script.toString().lowercase()
        listOf("camera", "framing", "lighting", "transition", "music", "sfx", "shot_type", "media_plan").forEach { assertThat(schema).doesNotContain(it) }
    }

    @Test fun `English uses the English system prompt`() = runTest {
        enqueue(script(listOf(
            ScriptSectionDto(1, "Here is the one thing most people get wrong before laser.", "One mistake", listOf("wrong")),
            ScriptSectionDto(2, "Aerolase uses a very short pulse, and the number of sessions depends on your skin.", "How it works", listOf("pulse")),
            ScriptSectionDto(3, "Not sure what fits your skin? Book an assessment and we'll plan it together.", "Book an assessment", listOf("assessment")),
        ), listOf(HookDto("curiosity", "Why do some people see no laser results?"), HookDto("common_mistake", "Skipping this step ruins most laser plans."), HookDto("expert_insight", "After years in clinic, the plan matters more than the device.")), "en"))
        writer.generate(ctx(ReelLanguage.ENGLISH))
        assertThat(body()["instructions"]!!.jsonPrimitive.content).startsWith(ScriptPrompts.SYSTEM_EN.lines().first())
    }

    @Test fun `formal MSA output is rejected and repaired once`() = runTest {
        val msa = iqSections.toMutableList().also { it[1] = ScriptSectionDto(2, "سوف نتحدث عن جهاز الإيروليز الذي يعمل بنبضات قصيرة جداً ولماذا يجب التقييم.", "كيف يعمل", emptyList()) }
        enqueue(script(msa)); enqueue(script())
        val w = writer.generate(ctx())
        assertThat(server.requestCount).isEqualTo(2)
        server.takeRequest()
        val repair = server.takeRequest().body.readUtf8()
        assertThat(repair).contains("formal MSA")
        assertThat(w.scripts[1].spokenScript).isEqualTo(iqSections[1].spokenScript)
    }

    @Test fun `near-duplicate hooks are rejected`() {
        val dup = listOf(HookDto("curiosity", "تعرفون ليش بعض الناس ما يشوفون نتيجة من الليزر؟"), HookDto("question", "تعرفون ليش بعض الناس ما يشوفون نتيجة من الليزر بسرعة؟"), HookDto("expert_insight", "من خبرتي، السر بالخطة."))
        assertThat(ScriptValidator.hookErrors(dup, ReelLanguage.IRAQI_ARABIC).joinToString()).contains("too similar")
        assertThat(ScriptValidator.hookErrors(iqHooks, ReelLanguage.IRAQI_ARABIC)).isEmpty()
        val sameStyle = iqHooks.map { it.copy(style = "curiosity") }
        assertThat(ScriptValidator.hookErrors(sameStyle, ReelLanguage.IRAQI_ARABIC).joinToString()).contains("different style")
    }

    @Test fun `too-long sections and unsafe claims fail validation, myths are only flagged`() {
        val budget = ScriptValidator.wordBudget(TimingEstimator(), ReelLanguage.ENGLISH, 10_000)
        val long = ScriptSectionDto(2, (1..60).joinToString(" ") { "word$it" }, "", emptyList())
        val dto = ScriptDto("t", "en", "a", listOf(HookDto("curiosity", "Why?"), HookDto("myth", "A myth."), HookDto("question", "Ready?")),
            listOf(ScriptSectionDto(1, "Some people believe results are permanent forever.", "Myth", emptyList()), long, ScriptSectionDto(3, "Results are guaranteed for everyone who books today.", "", emptyList())),
            "book_assessment", emptyList())
        val check = ScriptValidator.validateScript(dto, BuiltInTemplates.MYTH_BUSTER, ReelLanguage.ENGLISH, budget, emptyList())
        assertThat(check.errors.joinToString()).contains("section 2: too long")
        assertThat(check.errors.joinToString()).contains("section 3: unsafe claim")
        assertThat(check.errors.none { it.startsWith("section 1: unsafe") }).isTrue()   // myth shot → note, not error
        assertThat(check.notes.joinToString()).contains("permanent")
    }

    @Test fun `terminology spelling is preserved and markdown artifacts are cleaned`() = runTest {
        val messy = iqSections.toMutableList().also { it[1] = ScriptSectionDto(2, "**aerolase** يشتغل بنبضة قصيرة كلش، وعدد الجلسات يختلف حسب حالة البشرة.\\n", "\"شلون يشتغل\"", listOf("نبضة")) }
        enqueue(script(messy))
        val w = writer.generate(ctx())
        assertThat(w.scripts[1].spokenScript).startsWith("Aerolase يشتغل")
        assertThat(w.scripts[1].spokenScript).doesNotContain("*")
        assertThat(w.scripts[1].onScreenHeadline).isEqualTo("شلون يشتغل")
    }

    private fun sections(): List<ReelSection> {
        var n = 0
        val t = BuiltInTemplates.EXPERT_EXPLAINS
        return ProductionPlanner.mergeScript(t, ProductionPlanner.sections(t, emptyList(), { "s${++n}" }, 0),
            iqSections.mapIndexed { i, s -> app.reelify.core.template.SectionScript(s.index, if (i == 0) ScriptValidator.compose(iqHooks[0].text, s.spokenScript) else s.spokenScript, s.onScreenHeadline, s.emphasisWords) }, now = 0)
    }

    @Test fun `rewriting one shot returns only that shot and keeps the hook in front`() = runTest {
        val newS1 = ScriptSectionDto(1, "خلّي أوضحلكم شغلة ثانية مهمة قبل الجلسة بالضبط.", "شغلة ثانية", emptyList())
        enqueue(json.encodeToString(RewriteDto.serializer(), RewriteDto(listOf(newS1), "Fresh angle")))
        val r = writer.rewrite(ctx(), sections(), iqHooks[0].text, ScriptAction.REGENERATE_SHOT, setOf(1))
        assertThat(r.scripts.map { it.index }).containsExactly(1)
        assertThat(r.scripts.single().spokenScript).isEqualTo(iqHooks[0].text + " " + newS1.spokenScript)
        val user = body()["input"].toString()
        assertThat(user).contains("TARGET SECTION(S): 1")
    }

    @Test fun `rewrite returning the wrong sections is repaired`() = runTest {
        val wrong = RewriteDto(listOf(ScriptSectionDto(3, "إذا محتارين، احجزوا تقييم ونشوف سوا شنو الأنسب إلكم هواية.", "", emptyList())), "x")
        val right = RewriteDto(listOf(ScriptSectionDto(2, "الإيروليز يشتغل بنبضة قصيرة كلش، والنتيجة تختلف من شخص لشخص حسب البشرة.", "", emptyList())), "y")
        enqueue(json.encodeToString(RewriteDto.serializer(), wrong)); enqueue(json.encodeToString(RewriteDto.serializer(), right))
        val r = writer.rewrite(ctx(), sections(), iqHooks[0].text, ScriptAction.MORE_NATURAL_IRAQI, setOf(2))
        assertThat(r.scripts.single().index).isEqualTo(2)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test fun `locked shots are never sent for rewriting`() = runTest {
        val locked = sections().map { it.copy(locked = true) }
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { writer.rewrite(ctx(), locked, iqHooks[0].text, ScriptAction.SHORTEN, setOf(1, 2, 3)) } }
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun `new hooks must differ from current ones`() = runTest {
        val same = HookSetDto(iqHooks)
        val fresh = HookSetDto(listOf(HookDto("question", "الجلسة تألّم؟ خلّي أكولكم الصراحة."), HookDto("myth", "الليزر يخفّف البشرة؟ هاي فكرة غلط."), HookDto("surprising_fact", "مو كل حب شباب نفس النوع.")))
        enqueue(json.encodeToString(HookSetDto.serializer(), same)); enqueue(json.encodeToString(HookSetDto.serializer(), fresh))
        val hooks = writer.hooks(ctx(), sections(), iqHooks.map { app.reelify.core.model.HookOption(it.style, it.text) }, iqHooks[0].text)
        assertThat(hooks.map { it.text }).isEqualTo(fresh.hooks.map { it.text })
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test fun `fresh ideas are validated for language and diversity`() = runTest {
        val ideas = IdeasDto(listOf(
            IdeaDto("ليش حب الشباب يرجع؟", "Recurrence angle", "تعرفون ليش الحبوب ترجع بعد ما تختفي؟", "curiosity"),
            IdeaDto("غلطة قبل الجلسة", "Mistake angle", "أكثر غلطة أشوفها: الشمس قبل الجلسة.", "common_mistake"),
            IdeaDto("شكد جلسة أحتاج؟", "Expectations", "كم جلسة؟ الجواب الصريح يعتمد عليك.", "question"),
            IdeaDto("الليزر والتصبغات", "Pigment angle", "التصبغات ترجع إذا ما انتبهنا لهاي الشغلة.", "surprising_fact"),
        ))
        enqueue(json.encodeToString(IdeasDto.serializer(), ideas))
        assertThat(writer.ideas(ctx())).hasSize(4)
    }

    @Test fun `hook swapping replaces only the opening line`() {
        val spoken = ScriptValidator.compose("Old hook?", "Body text here.")
        assertThat(ScriptValidator.swapHook(spoken, "Old hook?", "New hook!")).isEqualTo("New hook! Body text here.")
        // edited text without the old hook: the new hook is prepended, nothing is deleted
        assertThat(ScriptValidator.swapHook("Edited body.", "Old hook?", "New hook!")).isEqualTo("New hook! Edited body.")
    }

    @Test fun `medical review flags from the model are mapped`() {
        val dto = ScriptDto("t", "en", "a", iqHooks, iqSections, "save_for_later", listOf(ClaimFlagDto("claim", "issue", "safer", "warning")))
        assertThat(dto.medicalReview).hasSize(1)
    }

    @Test fun `missing key fails fast without a request`() = runTest {
        val noKey = ScriptWriter(OpenAiClient(http(), creds(openai = null), { "m" }, server.url("/v1/").toString(), 1, 1), { TimingEstimator() })
        val e = runCatching { noKey.generate(ctx()) }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiError.MissingKey::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }
}
