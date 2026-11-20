package app.reelify.integration.openai

import app.reelify.core.model.BrandProfile
import app.reelify.core.model.ClaimFlag
import app.reelify.core.model.CreativeFeedback
import app.reelify.core.model.PlanMeta
import app.reelify.core.model.ReelConcept
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelSection
import app.reelify.core.model.ReelStyle
import app.reelify.core.model.TerminologyEntry
import app.reelify.core.model.Treatment
import app.reelify.core.library.SoundLibrary
import app.reelify.core.timing.TimingEstimator
import app.reelify.core.util.Clock
import app.reelify.integration.common.AiError
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

data class DirectorContext(
    val language: ReelLanguage,
    val duration: ReelDuration,
    val style: ReelStyle,
    val treatment: Treatment?,
    val terminology: List<TerminologyEntry>,
    val brand: BrandProfile?,
    val concept: ReelConcept,
    val feedback: CreativeFeedback? = null,
)

data class GeneratedPlan(val meta: PlanMeta, val sections: List<ReelSection>)

data class SectionProposal(val before: ReelSection, val after: ReelSection, val summary: String, val instruction: String)

enum class SectionTask(val template: String, val label: String) {
    REWRITE(PromptTemplates.SECTION_REGENERATION, "Rewrite"),
    NATURALIZE_IRAQI(PromptTemplates.IRAQI_NATURALIZATION, "More natural Iraqi"),
    SHORTEN(PromptTemplates.TIMING_REDUCTION, "Shorten"),
    CAMERA(PromptTemplates.CAMERA_DIRECTION, "Camera direction"),
    VISUALS(PromptTemplates.SHOT_PLANNING, "Visual plan"),
}

/**
 * AI creative director use-cases. Every response is schema-constrained, decoded, validated and —
 * if invalid — repaired once by showing the model its validation errors. Nothing unvalidated is
 * returned to callers.
 */
class ReelDirector(
    private val client: OpenAiClient,
    private val timing: () -> TimingEstimator,
    private val clock: Clock = Clock.System,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------------ context

    fun contextBlock(ctx: DirectorContext): String = buildString {
        val est = timing()
        val sectionSec = ctx.duration.sectionMs / 1000
        val budget = est.estimate("", ctx.language, ctx.duration.sectionMs).recommendedMaxWords
        appendLine("REEL LANGUAGE: ${ctx.language.displayName} (${ctx.language.code}). Spoken dialogue and on-screen text MUST be in this language.")
        appendLine("TOTAL DURATION: ${ctx.duration.seconds} seconds → three sections of exactly $sectionSec seconds each.")
        appendLine("WORD BUDGET PER SECTION: about $budget words maximum (natural pace ≈ ${est.wordsPerSecond(ctx.language)} words/sec, including pauses). Fewer is fine when B-roll carries the section.")
        appendLine("REEL STYLE: ${ctx.style.label} — ${ctx.style.blurb}")
        ctx.treatment?.let { t ->
            appendLine()
            appendLine("TREATMENT PROFILE: ${t.name}${if (t.arabicName.isNotBlank()) " / ${t.arabicName}" else ""}")
            if (t.needsConfiguration) appendLine("!! This profile is marked NEEDS CONFIGURATION. Do NOT describe its mechanism, indications or results beyond what is written below. Stay generic and educational.")
            if (t.alternativeNames.isNotEmpty()) appendLine("Also known as: ${t.alternativeNames.joinToString()}")
            if (t.description.isNotBlank()) appendLine("Description: ${t.description}")
            listOf(
                "Approved indications (user-provided)" to t.approvedIndications,
                "Approved talking points" to t.talkingPoints,
                "Precautions" to t.precautions,
                "Preferred terminology" to t.preferredTerminology,
                "Preferred hooks" to t.preferredHooks,
                "Common patient questions" to t.commonQuestions,
                "CLAIMS THE CREATOR DOES NOT WANT MADE" to t.forbiddenClaims,
                "Suitable B-roll" to t.brollSuggestions,
            ).forEach { (label, items) -> if (items.isNotEmpty()) appendLine("$label: ${items.joinToString("; ")}") }
            if (t.ctaStyle.isNotBlank()) appendLine("Preferred CTA style: ${t.ctaStyle}")
            if (t.referenceNotes.isNotBlank()) appendLine("Reference notes: ${t.referenceNotes.take(2500)}")
        }
        if (ctx.terminology.isNotEmpty()) {
            appendLine()
            appendLine("TERMINOLOGY DICTIONARY (always use these spellings):")
            ctx.terminology.forEach { appendLine("- ${it.english}${if (it.arabic.isNotBlank()) " = ${it.arabic}" else ""}") }
        }
        ctx.brand?.takeIf { it.isConfigured }?.let { b ->
            appendLine()
            appendLine("BRAND: ${listOf(b.clinicName, b.brandName).filter { it.isNotBlank() }.joinToString(" / ")}; presenter: ${b.presenterName} ${b.professionalTitle}".trim())
            val cta = if (ctx.language == ReelLanguage.IRAQI_ARABIC) b.ctaTextArabic.ifBlank { b.ctaText } else b.ctaText
            if (cta.isNotBlank()) appendLine("Preferred CTA wording: $cta")
            if (b.instagram.isNotBlank()) appendLine("Instagram: ${b.instagram}")
        }
        appendLine()
        appendLine("CREATOR'S IDEA: ${ctx.concept.summary}")
        with(ctx.concept) {
            if (desiredMessage.isNotBlank()) appendLine("Desired message: $desiredMessage")
            if (audience.isNotBlank()) appendLine("Intended audience: $audience")
            if (clinicalPoints.isNotBlank()) appendLine("Clinical points to include: $clinicalPoints")
            if (footageAvailability.isNotBlank()) appendLine("Footage availability: $footageAvailability")
            if (tone.isNotBlank()) appendLine("Preferred tone: $tone")
            if (cta.isNotBlank()) appendLine("Desired CTA: $cta")
            if (mustInclude.isNotEmpty()) appendLine("Must include: ${mustInclude.joinToString("; ")}")
            if (excludedClaims.isNotEmpty()) appendLine("Exclude these claims/points: ${excludedClaims.joinToString("; ")}")
        }
        ctx.feedback?.let { f ->
            appendLine()
            appendLine("APPROVED CREATIVE DIRECTION:")
            if (f.creativeAngle.isNotBlank()) appendLine("Angle: ${f.creativeAngle}")
            if (f.hook.isNotBlank()) appendLine("Approved hook: ${f.hook}")
            if (f.mainMessage.isNotBlank()) appendLine("Main message: ${f.mainMessage}")
            if (f.targetAudience.isNotBlank()) appendLine("Audience: ${f.targetAudience}")
            if (f.visualApproach.isNotBlank()) appendLine("Visual approach: ${f.visualApproach}")
            if (f.improvements.isNotEmpty()) appendLine("Accepted improvements: ${f.improvements.joinToString("; ")}")
        }
    }

    private fun sectionsBlock(sections: List<ReelSection>) = buildString {
        sections.sortedBy { it.index }.forEach { s ->
            appendLine("[Section ${s.index} — ${s.purpose.label}${if (s.locked) " — LOCKED/APPROVED" else ""}]")
            appendLine("Shot: ${s.shotType.wire}; Script: ${s.spokenScript}")
            if (s.onScreenText.isNotBlank()) appendLine("On-screen: ${s.onScreenText}")
        }
    }

    // ------------------------------------------------------------------ core call

    private suspend fun <T> callValidated(
        task: String,
        user: String,
        schemaName: String,
        schema: JsonObject,
        serializer: KSerializer<T>,
        validate: (T) -> List<String>,
        maxTokens: Int = 6000,
    ): Pair<T, String> {
        val instructions = PromptTemplates.DIRECTOR_SYSTEM + "\n\n" + task
        val turns = mutableListOf(ChatTurn("user", user))
        var lastErrors: List<String> = emptyList()
        repeat(2) { attempt ->
            val result = client.structured(instructions, turns, schemaName, schema, maxTokens)
            val decoded = runCatching { json.decodeFromString(serializer, result.text) }.getOrNull()
            val errors = if (decoded == null) listOf("output did not match the schema") else validate(decoded)
            if (decoded != null && errors.isEmpty()) return decoded to result.model
            lastErrors = errors
            if (attempt == 0) {
                turns += ChatTurn("assistant", result.text.take(12000))
                turns += ChatTurn("user", "Your previous output failed validation:\n- " + errors.joinToString("\n- ") + "\nReturn a corrected, complete output that satisfies every rule.")
            }
        }
        throw AiError.Malformed(lastErrors.take(3).joinToString("; "))
    }

    // ------------------------------------------------------------------ use cases

    suspend fun analyzeIdea(ctx: DirectorContext, extraInstruction: String? = null): CreativeFeedback {
        val user = contextBlock(ctx.copy(feedback = null)) + (extraInstruction?.let { "\n\nCREATOR REQUEST: $it" } ?: "")
        val (dto, _) = callValidated(PromptTemplates.IDEA_REFINEMENT, user, "idea_analysis", AiSchemas.ideaAnalysis, IdeaAnalysisDto.serializer(), { PlanValidator.validateIdea(it, ctx.language) }, 3000)
        return AiMappers.feedback(dto, clock.now())
    }

    suspend fun generateHooks(ctx: DirectorContext, count: Int = 5): List<String> {
        val user = contextBlock(ctx) + "\n\nWrite $count different hooks in ${ctx.language.displayName}."
        val (dto, _) = callValidated(PromptTemplates.HOOK_GENERATION, user, "hooks", AiSchemas.hooks, HooksDto.serializer(), { d ->
            buildList {
                if (d.hooks.isEmpty()) add("no hooks returned")
                d.hooks.filterNot { PlanValidator.languageMatches(it, ctx.language) }.forEach { add("hook not in ${ctx.language.displayName}: $it") }
            }
        }, 1500)
        return dto.hooks.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Generates a complete plan. [existing] sections that are locked are passed as fixed context
     * and are preserved by the repository regardless of the model output.
     */
    suspend fun generatePlan(ctx: DirectorContext, existing: List<ReelSection>, extraInstruction: String? = null): GeneratedPlan {
        val locked = existing.filter { it.locked && it.spokenScript.isNotBlank() }
        val user = buildString {
            append(contextBlock(ctx))
            if (locked.isNotEmpty()) {
                appendLine(); appendLine("These sections are LOCKED and must be returned unchanged at the same index:")
                append(sectionsBlock(locked))
            }
            extraInstruction?.let { appendLine(); appendLine("CREATOR REQUEST: $it") }
        }
        val (dto, model) = callValidated(PromptTemplates.SCRIPT_GENERATION, user, "reel_plan", AiSchemas.plan, PlanDto.serializer(), { PlanValidator.validatePlan(it, ctx.language) }, 8000)
        val now = clock.now()
        val byIndex = existing.associateBy { it.index }
        val sections = dto.sections.sortedBy { it.index }.map { s ->
            // Stable ids: reuse the existing section id for that slot; the model never chooses ids.
            AiMappers.section(s, byIndex[s.index]?.id ?: "sec-new-${s.index}", now)
        }
        return GeneratedPlan(AiMappers.plan(dto, model), sections)
    }

    suspend fun regenerateSection(
        ctx: DirectorContext,
        sections: List<ReelSection>,
        targetId: String,
        instruction: String,
        task: SectionTask = SectionTask.REWRITE,
    ): SectionProposal {
        val target = sections.first { it.id == targetId }
        val budget = timing().estimate("", ctx.language, ctx.duration.sectionMs).recommendedMaxWords
        val user = buildString {
            append(contextBlock(ctx))
            appendLine(); appendLine("CURRENT PLAN (context only — do not modify other sections):")
            append(sectionsBlock(sections))
            appendLine(); appendLine("TARGET: section ${target.index}. Current full definition:")
            appendLine(json.encodeToString(SectionDto.serializer(), AiMappers.toDto(target)))
            appendLine("Word budget for this section: about $budget words.")
            appendLine(); appendLine("CREATOR INSTRUCTION: $instruction")
        }
        val (dto, _) = callValidated(task.template + "\n" + PromptTemplates.SECTION_REGENERATION, user, "section_replacement", AiSchemas.sectionRegen, SectionRegenDto.serializer(), {
            PlanValidator.validateSection(it.section, ctx.language, expectedIndex = target.index)
        }, 3000)
        val after = AiMappers.section(dto.section, target.id, clock.now()).copy(locked = target.locked)
        return SectionProposal(target, after, dto.changeSummary.trim(), instruction)
    }

    suspend fun reviewClaims(ctx: DirectorContext, sections: List<ReelSection>): List<ClaimFlag> {
        val user = contextBlock(ctx) + "\n\nSCRIPT TO REVIEW:\n" + sectionsBlock(sections)
        val (dto, _) = callValidated(PromptTemplates.CLAIM_REVIEW, user, "claim_review", AiSchemas.claimReview, ClaimReviewDto.serializer(), { emptyList() }, 2500)
        return dto.flags.map { f ->
            val sid = sections.firstOrNull { it.spokenScript.contains(f.claim.take(24), ignoreCase = true) || it.onScreenText.contains(f.claim.take(24), true) }?.id
            AiMappers.flag(f, sid)
        }
    }

    suspend fun editGuidance(ctx: DirectorContext, request: String, currentSummary: String): EditGuidanceDto {
        val user = buildString {
            appendLine("Reel language: ${ctx.language.displayName}; duration ${ctx.duration.seconds}s; style ${ctx.style.label}.")
            appendLine("CURRENT EDIT: $currentSummary")
            appendLine("CREATOR REQUEST: $request")
        }
        val (dto, _) = callValidated(PromptTemplates.EDITING_RECOMMENDATIONS, user, "edit_guidance", AiSchemas.editGuidance, EditGuidanceDto.serializer(), { emptyList() }, 1500)
        return dto
    }

    suspend fun captionEmphasis(ctx: DirectorContext, transcript: String): List<String> {
        val user = "Reel language: ${ctx.language.displayName}\nTRANSCRIPT:\n$transcript"
        val (dto, _) = callValidated(PromptTemplates.CAPTION_EMPHASIS, user, "keywords", AiSchemas.keywords, KeywordsDto.serializer(), { emptyList() }, 800)
        return dto.keywords.map { it.trim() }.filter { it.isNotEmpty() && transcript.contains(it, ignoreCase = true) }.take(8)
    }

    suspend fun soundDesign(ctx: DirectorContext, plan: PlanMeta?, sections: List<ReelSection>): SoundDesignDto {
        val user = buildString {
            appendLine("Plan mood: ${plan?.musicMood?.wire ?: "unknown"}; style: ${ctx.style.label}; audience: ${plan?.targetAudience.orEmpty()}")
            appendLine("SFX notes: ${plan?.sfxNotes.orEmpty()}")
            append(sectionsBlock(sections))
            appendLine("MUSIC LIBRARY:")
            SoundLibrary.music.forEach { appendLine("- ${it.id}: ${it.title}, mood ${it.mood.wire}, ${it.bpm} bpm, energy ${it.energy}") }
            appendLine("SFX LIBRARY: ${SoundLibrary.sfx.joinToString { it.id }}")
        }
        val (dto, _) = callValidated(PromptTemplates.SOUND_DESIGN, user, "sound_design", AiSchemas.soundDesign, SoundDesignDto.serializer(), { d ->
            buildList {
                if (SoundLibrary.track(d.musicTrackId) == null) add("unknown music_track_id")
                if (d.sfx.size > 4) add("use at most 4 sfx")
            }
        }, 1000)
        return dto
    }
}
