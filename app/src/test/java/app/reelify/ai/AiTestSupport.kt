package app.reelify.ai

import app.reelify.core.model.ReelLanguage
import app.reelify.core.security.AiProvider
import app.reelify.core.security.CredentialRepository
import app.reelify.core.security.InMemorySecretStore
import app.reelify.integration.openai.AiMappers
import app.reelify.integration.openai.PlanDto
import app.reelify.integration.openai.SectionDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object AiTestSupport {
    val json = Json { encodeDefaults = true }

    fun creds(openai: String? = "sk-test-123456789", deepgram: String? = "dg-test-123456789") =
        CredentialRepository(InMemorySecretStore(), buildMap {
            openai?.let { put(AiProvider.OPENAI, it) }; deepgram?.let { put(AiProvider.DEEPGRAM, it) }
        })

    fun http(timeoutMs: Long = 3000) = OkHttpClient.Builder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).readTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()

    /** Wraps structured JSON text as a Responses API payload. */
    fun responsesBody(text: String, status: String = "completed") = buildJsonObject {
        put("id", "resp_1"); put("object", "response"); put("status", status); put("model", "gpt-6.2-sol")
        put("output", buildJsonArray {
            add(buildJsonObject { put("type", "reasoning"); put("id", "rs_1") })
            add(buildJsonObject {
                put("type", "message"); put("role", "assistant")
                put("content", buildJsonArray { add(buildJsonObject { put("type", "output_text"); put("text", text) }) })
            })
        })
    }.toString()

    fun section(i: Int, lang: ReelLanguage, script: String? = null) = SectionDto(
        index = i, purpose = listOf("hook", "explanation", "call_to_action")[i - 1], title = "S$i",
        spokenScript = script ?: if (lang == ReelLanguage.IRAQI_ARABIC) listOf("شنو الفرق بين حب الشباب والتصبغات؟", "جهاز Aerolase ممكن يساعد بعض الحالات حسب حالة البشرة.", "احجز تقييم لبشرتك وخل نشوف شنو الأنسب إلك.")[i - 1]
        else listOf("Is laser right for every skin?", "Aerolase may help some acne concerns after assessment.", "Book a skin evaluation today.")[i - 1],
        isVoiceover = false, shotType = if (i == 2) "treatment_broll" else "talking_head", cameraDirection = "Eye level, centered", framing = "Medium close-up",
        lightingTip = "Soft frontal light", deliveryDirection = "Warm and confident", bRollSuggestion = "Handpiece close-up", onScreenText = "",
        captionEmphasis = listOf(), sfxCue = "none", transitionOut = "crossfade", editNotes = "Clean cut", mediaPlan = if (i == 2) "treatment_broll" else "record_talking",
    )

    fun plan(lang: ReelLanguage, sections: List<SectionDto> = (1..3).map { section(it, lang) }) = PlanDto(
        title = "Aerolase education", targetAudience = "Adults with acne", purpose = "Educate", creativeStyle = "Educational",
        hook = sections.first().spokenScript, sections = sections, musicMood = "calm_clinical", sfxNotes = "", cta = "Book an evaluation",
        editingNotes = "Restrained", claimFlags = emptyList(),
    )

    fun planJson(lang: ReelLanguage, sections: List<SectionDto> = (1..3).map { section(it, lang) }) = json.encodeToString(PlanDto.serializer(), plan(lang, sections))

    @Suppress("unused") val mappers = AiMappers
}
