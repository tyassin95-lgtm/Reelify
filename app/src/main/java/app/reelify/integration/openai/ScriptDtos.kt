package app.reelify.integration.openai

import app.reelify.core.content.IraqiStyleGuide
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * Reelify 2.0 AI contract. The model returns ONLY creative words — never camera, framing,
 * lighting, transitions, music or SFX. Those come from the production template.
 */

@Serializable
data class HookDto(val style: String, val text: String)

@Serializable
data class ScriptSectionDto(
    val index: Int,
    @SerialName("spoken_script") val spokenScript: String,
    @SerialName("on_screen_headline") val onScreenHeadline: String,
    @SerialName("emphasis_words") val emphasisWords: List<String>,
)

@Serializable
data class ScriptDto(
    val title: String,
    val language: String,
    @SerialName("creative_angle") val creativeAngle: String,
    @SerialName("hook_options") val hookOptions: List<HookDto>,
    val sections: List<ScriptSectionDto>,
    @SerialName("cta_style") val ctaStyle: String,
    @SerialName("medical_review") val medicalReview: List<ClaimFlagDto>,
)

@Serializable
data class IdeaDto(
    val title: String,
    val angle: String,
    val hook: String,
    @SerialName("hook_style") val hookStyle: String,
    @SerialName("educational_question") val educationalQuestion: String = "",
    @SerialName("learning_objective") val learningObjective: String = "",
    @SerialName("supporting_fact_ids") val supportingFactIds: List<String> = emptyList(),
    @SerialName("template_fit_reason") val templateFitReason: String = "",
)

@Serializable
data class IdeasDto(val ideas: List<IdeaDto>)

@Serializable
data class HookSetDto(val hooks: List<HookDto>)

@Serializable
data class RewriteDto(val sections: List<ScriptSectionDto>, @SerialName("change_summary") val changeSummary: String)

@Serializable
data class EditorialIssueDto(@SerialName("section_index") val sectionIndex: Int, val quote: String, val problem: String, val suggestion: String)

@Serializable
data class EditorialReviewDto(val naturalness: Int, val engagement: Int, val issues: List<EditorialIssueDto>)

object ScriptSchemas {
    val hookStyles = listOf("curiosity", "common_mistake", "expert_insight", "relatable_concern", "myth", "question", "surprising_fact")

    private val claimFlag = S.obj(
        "claim" to S.str("Exact wording from the script"),
        "issue" to S.str(),
        "suggestion" to S.str("Safer alternative wording in the reel language"),
        "severity" to S.enum(listOf("info", "warning", "blocker")),
    )

    val hook: JsonObject = S.obj("style" to S.enum(hookStyles), "text" to S.str("Opening line in the reel language, under ~3 seconds spoken"))

    val section: JsonObject = S.obj(
        "index" to S.int("1, 2 or 3"),
        "spoken_script" to S.str("Exact words to speak, in the reel language. Plain text, no markdown, no stage directions, no emoji."),
        "on_screen_headline" to S.str("Max 5 words, reel language, plain text"),
        "emphasis_words" to S.arr(S.str(), "1-3 words that appear verbatim in spoken_script"),
    )

    val script: JsonObject = S.obj(
        "title" to S.str("Short internal title (reel language)"),
        "language" to S.enum(listOf("en", "ar-IQ")),
        "creative_angle" to S.str("One sentence, creator's UI language (English)"),
        "hook_options" to S.arr(hook, "Exactly three hooks with three DIFFERENT styles and genuinely different angles"),
        "sections" to S.arr(section, "Exactly three sections, index 1..3. Section 1 continues AFTER the hook and must not repeat it."),
        "cta_style" to S.enum(IraqiStyleGuide.ctaStyles),
        "medical_review" to S.arr(claimFlag, "Any wording in your own script that a careful clinician should double-check; empty if none"),
    )

    val ideas: JsonObject = S.obj("ideas" to S.arr(S.obj(
        "title" to S.str("Specific educational topic title in the reel language, max 9 words"),
        "angle" to S.str("Concrete clinical teaching angle with mechanism, expectation, or safety rule (English)"),
        "hook" to S.str("Scroll-stopping opening line in the reel language, zero filler"),
        "hook_style" to S.enum(hookStyles),
        "educational_question" to S.str("Real patient question or misconception answered by this reel"),
        "learning_objective" to S.str("One concrete thing viewers learn that goes beyond generic hype"),
        "supporting_fact_ids" to S.arr(S.str(), "Verified fact IDs from the brief that anchor this concept"),
        "template_fit_reason" to S.str("Why this idea naturally fits the 3-shot template"),
    ), "Five distinct, specific, fact-grounded ideas"))

    val hooks: JsonObject = S.obj("hooks" to S.arr(hook, "Exactly three hooks with different styles"))

    val rewrite: JsonObject = S.obj(
        "sections" to S.arr(section, "Only the sections you were asked to rewrite"),
        "change_summary" to S.str("One sentence (English)"),
    )

    val editorial: JsonObject = S.obj(
        "naturalness" to S.int("1-5: how natural this sounds spoken by a real person in the target dialect"),
        "engagement" to S.int("1-5"),
        "issues" to S.arr(S.obj(
            "section_index" to S.int(),
            "quote" to S.str("Exact problematic wording"),
            "problem" to S.str("English"),
            "suggestion" to S.str("Rewritten wording in the reel language"),
        )),
    )
}
