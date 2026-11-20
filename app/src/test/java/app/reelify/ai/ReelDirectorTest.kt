package app.reelify.ai

import app.reelify.ai.AiTestSupport.creds
import app.reelify.ai.AiTestSupport.http
import app.reelify.ai.AiTestSupport.planJson
import app.reelify.ai.AiTestSupport.responsesBody
import app.reelify.ai.AiTestSupport.section
import app.reelify.core.model.ReelConcept
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelSection
import app.reelify.core.model.ReelStyle
import app.reelify.core.model.SectionPurpose
import app.reelify.core.model.TerminologyEntry
import app.reelify.core.data.TreatmentSeed
import app.reelify.core.timing.TimingEstimator
import app.reelify.integration.common.AiError
import app.reelify.integration.openai.AiSchemas
import app.reelify.integration.openai.DirectorContext
import app.reelify.integration.openai.EditGuidanceApplier
import app.reelify.integration.openai.EditGuidanceDto
import app.reelify.integration.openai.EffectTogglesDto
import app.reelify.integration.openai.OpenAiClient
import app.reelify.integration.openai.PlanValidator
import app.reelify.integration.openai.ReelDirector
import app.reelify.integration.openai.SectionRegenDto
import app.reelify.integration.openai.SectionTask
import app.reelify.core.model.EditSettings
import app.reelify.core.model.TransitionType
import app.reelify.core.model.CaptionPresetId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class ReelDirectorTest {
    private lateinit var server: MockWebServer
    private lateinit var director: ReelDirector

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        val client = OpenAiClient(http(), creds(), { "gpt-6.2-sol" }, server.url("/v1/").toString(), 1, 1)
        director = ReelDirector(client, { TimingEstimator() })
    }
    @After fun tearDown() { server.shutdown() }

    private fun ctx(lang: ReelLanguage = ReelLanguage.IRAQI_ARABIC) = DirectorContext(
        lang, ReelDuration.S30, ReelStyle.EDUCATIONAL, TreatmentSeed.treatments.first { it.id == "aerolase" },
        listOf(TerminologyEntry("t", "Aerolase", "إيروليز")), null,
        ReelConcept("I want a video about Aerolase, explaining that it can be considered for certain acne and pigmentation concerns and why a proper consultation matters."),
    )

    private val existing = listOf(
        ReelSection("sec-A", 1, SectionPurpose.HOOK), ReelSection("sec-B", 2, SectionPurpose.EXPLANATION), ReelSection("sec-C", 3, SectionPurpose.CALL_TO_ACTION),
    )

    @Test fun `generates exactly three sections in Iraqi Arabic keeping stable ids`() = runTest {
        server.enqueue(MockResponse().setBody(responsesBody(planJson(ReelLanguage.IRAQI_ARABIC))))
        val plan = director.generatePlan(ctx(), existing)
        assertThat(plan.sections).hasSize(3)
        assertThat(plan.sections.map { it.id }).containsExactly("sec-A", "sec-B", "sec-C").inOrder()
        assertThat(plan.sections.all { PlanValidator.languageMatches(it.spokenScript, ReelLanguage.IRAQI_ARABIC) }).isTrue()
        // prompt grounding: treatment profile, duration and section budget are sent
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val user = body["input"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertThat(user).contains("ar-IQ")
        assertThat(user).contains("10 seconds each")
        assertThat(user).contains("TREATMENT PROFILE: Aerolase")
        assertThat(body["instructions"]!!.jsonPrimitive.content).contains("exactly three main sections")
    }

    @Test fun `invalid plan is repaired by a follow-up request`() = runTest {
        val two = planJson(ReelLanguage.ENGLISH, (1..2).map { section(it, ReelLanguage.ENGLISH) })
        server.enqueue(MockResponse().setBody(responsesBody(two)))
        server.enqueue(MockResponse().setBody(responsesBody(planJson(ReelLanguage.ENGLISH))))
        val plan = director.generatePlan(ctx(ReelLanguage.ENGLISH), existing)
        assertThat(plan.sections).hasSize(3)
        server.takeRequest()
        val repair = server.takeRequest().body.readUtf8()
        assertThat(repair).contains("failed validation")
        assertThat(repair).contains("exactly 3 sections")
    }

    @Test fun `persistently malformed output is rejected, never rendered`() = runTest {
        server.enqueue(MockResponse().setBody(responsesBody("""{"title":"x"}""")))
        server.enqueue(MockResponse().setBody(responsesBody("""{"nonsense":true}""")))
        val e = runCatching { director.generatePlan(ctx(), existing) }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiError.Malformed::class.java)
    }

    @Test fun `wrong language output is rejected`() = runTest {
        server.enqueue(MockResponse().setBody(responsesBody(planJson(ReelLanguage.ENGLISH))))
        server.enqueue(MockResponse().setBody(responsesBody(planJson(ReelLanguage.ENGLISH))))
        val e = runCatching { director.generatePlan(ctx(ReelLanguage.IRAQI_ARABIC), existing) }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiError.Malformed::class.java)
    }

    @Test fun `selective regeneration returns only the target section with its stable id`() = runTest {
        val current = (1..3).map { i -> app.reelify.integration.openai.AiMappers.section(section(i, ReelLanguage.IRAQI_ARABIC), "sec-$i", 0) }
        val replacement = section(2, ReelLanguage.IRAQI_ARABIC, "هسه خل أوضحلكم: Aerolase ممكن يفيد بعض حالات حب الشباب، بس حسب حالة البشرة.")
        server.enqueue(MockResponse().setBody(responsesBody(Json.encodeToString(SectionRegenDto.serializer(), SectionRegenDto(replacement, "Shorter and more natural")))))
        val p = director.regenerateSection(ctx(), current, "sec-2", "Make Section 2 shorter and more natural in Iraqi Arabic.", SectionTask.NATURALIZE_IRAQI)
        assertThat(p.after.id).isEqualTo("sec-2")
        assertThat(p.after.index).isEqualTo(2)
        assertThat(p.before.spokenScript).isEqualTo(current[1].spokenScript)
        assertThat(p.after.spokenScript).startsWith("هسه")
        val user = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["input"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertThat(user).contains("TARGET: section 2")
    }

    @Test fun `regeneration that returns the wrong index is repaired`() = runTest {
        val current = (1..3).map { i -> app.reelify.integration.openai.AiMappers.section(section(i, ReelLanguage.ENGLISH), "sec-$i", 0) }
        server.enqueue(MockResponse().setBody(responsesBody(Json.encodeToString(SectionRegenDto.serializer(), SectionRegenDto(section(1, ReelLanguage.ENGLISH), "x")))))
        server.enqueue(MockResponse().setBody(responsesBody(Json.encodeToString(SectionRegenDto.serializer(), SectionRegenDto(section(2, ReelLanguage.ENGLISH), "x")))))
        val p = director.regenerateSection(ctx(ReelLanguage.ENGLISH), current, "sec-2", "shorter")
        assertThat(p.after.index).isEqualTo(2)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test fun `schema enums cover sfx and transition values`() {
        assertThat(AiSchemas.sfxCueValues).contains("soft_whoosh")
        assertThat(AiSchemas.plan.toString()).contains("\"additionalProperties\":false")
    }

    @Test fun `edit guidance is applied deterministically and unknown values are ignored`() {
        val g = EditGuidanceDto(
            summary = "Calmer", captionPreset = "clean_clinical", captionAnimation = "unchanged", captionSize = "larger",
            transitionStyle = "dissolve", transitionAmount = "unchanged", musicMood = "calm_clinical", musicLevel = "lower",
            colorLook = "bogus_value", colorIntensity = "subtle",
            effects = EffectTogglesDto("off", "unchanged", "on", "unchanged", "unchanged", "unchanged"), highlightKeywords = listOf("Aerolase"), sfxLevel = "none",
        )
        val before = EditSettings()
        val after = EditGuidanceApplier.apply(before, g)
        assertThat(after.captions.style.preset).isEqualTo(CaptionPresetId.CLEAN_CLINICAL)
        assertThat(after.transitions.defaultType).isEqualTo(TransitionType.DISSOLVE)
        assertThat(after.music.volume).isLessThan(before.music.volume)
        assertThat(after.music.trackId).isEqualTo("calm_clinical")
        assertThat(after.color.look).isEqualTo(before.color.look) // invalid value ignored
        assertThat(after.effects.pushIn).isFalse()
        assertThat(after.effects.lightOverlay).isTrue()
        assertThat(after.captions.highlightKeywords).contains("Aerolase")
        assertThat(after.sfx).isEmpty()
    }
}
