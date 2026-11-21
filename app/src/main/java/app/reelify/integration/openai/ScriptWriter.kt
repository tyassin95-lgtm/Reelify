package app.reelify.integration.openai

import app.reelify.core.content.FactualBriefBuilder
import app.reelify.core.content.FactualContentBrief
import app.reelify.core.content.GeneratedContentNormalizer
import app.reelify.core.content.StyleExample
import app.reelify.core.content.TextField
import app.reelify.core.content.TreatmentKnowledgeCatalog
import app.reelify.core.model.BrandProfile
import app.reelify.core.model.HookOption
import app.reelify.core.model.ReelConcept
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelSection
import app.reelify.core.model.ScriptMeta
import app.reelify.core.model.ShotType
import app.reelify.core.model.TerminologyEntry
import app.reelify.core.model.Treatment
import app.reelify.core.template.MediaRequirement
import app.reelify.core.template.ReelTemplate
import app.reelify.core.template.SectionScript
import app.reelify.core.timing.TimingEstimator
import app.reelify.core.util.Clock
import app.reelify.integration.common.AiError
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Everything the scriptwriter may know. Production settings are described, never negotiable. */
data class ScriptContext(
    val language: ReelLanguage,
    val duration: ReelDuration,
    val template: ReelTemplate,
    val treatment: Treatment?,
    val terminology: List<TerminologyEntry>,
    val brand: BrandProfile?,
    val topicTitle: String,
    val topicBrief: String = "",
    val userIdea: String = "",
    val concept: ReelConcept = ReelConcept(),
    val recentHooks: List<String> = emptyList(),
    val recentTopics: List<String> = emptyList(),
    val examples: List<StyleExample> = emptyList(),
    val factualBrief: FactualContentBrief? = null,
)

data class WrittenScript(val title: String, val meta: ScriptMeta, val scripts: List<SectionScript>)

data class RewriteResult(val scripts: List<SectionScript>, val summary: String, val notes: List<String>)

data class FreshIdea(
    val title: String,
    val angle: String,
    val hook: String,
    val hookStyle: String,
    val educationalQuestion: String = "",
    val learningObjective: String = "",
    val supportingFactIds: List<String> = emptyList(),
    val templateFitReason: String = "",
)

/**
 * Reelify 2.0 AI scriptwriter. Returns only creative words (hooks, dialogue, headlines). Every
 * response is schema-constrained, normalized, validated and repaired once; nothing unvalidated
 * reaches the project. Template-owned production fields are never part of the contract.
 */
class ScriptWriter(
    private val client: OpenAiClient,
    private val timing: () -> TimingEstimator,
    private val clock: Clock = Clock.System,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun protectedTerms(ctx: ScriptContext): List<String> =
        (listOfNotNull(ctx.treatment?.name) + ctx.treatment?.preferredTerminology.orEmpty() + ctx.terminology.map { it.english } + ctx.treatment?.alternativeNames.orEmpty())
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .filter { it.isNotBlank() }.distinct()

    private fun forbidden(ctx: ScriptContext) = ctx.treatment?.forbiddenClaims.orEmpty() + ctx.concept.excludedClaims

    fun budget(ctx: ScriptContext) = ScriptValidator.wordBudget(timing(), ctx.language, ctx.duration.sectionMs)

    fun system(ctx: ScriptContext): String =
        if (ctx.language == ReelLanguage.IRAQI_ARABIC) ScriptPrompts.systemIraqi(ctx.examples) else ScriptPrompts.SYSTEM_EN

    fun contextBlock(ctx: ScriptContext): String = buildString {
        val budget = budget(ctx)
        val sec = ctx.duration.sectionMs / 1000
        val tEst = timing()
        val arabic = ctx.language == ReelLanguage.IRAQI_ARABIC
        appendLine("REEL LANGUAGE: ${ctx.language.displayName} (${ctx.language.code}). All spoken lines, hooks and headlines in this language.")
        appendLine("TARGET PLATFORM: ${ctx.concept.platform.label.en}")
        appendLine("DURATION: ${ctx.duration.seconds}s = three shots of exactly ${sec}s (~${budget}w max; section 1 includes the hook, so write its body to complement the hook cleanly).")
        appendLine()
        appendLine("PRODUCTION TEMPLATE (fixed by the app — write words for it, do not change it): ${ctx.template.name.en}")
        appendLine("Structure: ${ctx.template.structureBrief}")
        ctx.template.shots.sortedBy { it.index }.forEach { s ->
            val media = if (s.requiredMedia == MediaRequirement.VOICEOVER_BROLL) "voiceover over device/treatment footage (viewer does not see the speaker)" else "speaking to camera"
            val shotType = if (s.requiredMedia == MediaRequirement.VOICEOVER_BROLL) ShotType.VOICEOVER_BROLL else ShotType.TALKING_HEAD
            val sb = tEst.wordBudgetForShot(ctx.language, ctx.duration.sectionMs, s.role.purpose, shotType)
            appendLine("- Shot ${s.index} · ${s.role.label.en} · $media · target ${sb.promptLabel()}. Writing brief: ${s.writingBrief}")
        }
        val brief = ctx.factualBrief ?: ctx.treatment?.let { tr ->
            FactualBriefBuilder.build(
                treatment = tr,
                pack = TreatmentKnowledgeCatalog.packFor(tr.id, tr.linkedReferenceProfileId, tr.needsConfiguration),
                topic = null,
                topicTitle = ctx.topicTitle,
                topicBrief = ctx.topicBrief,
                userIdea = ctx.userIdea,
                arabic = arabic,
            )
        }
        if (brief != null) {
            appendLine()
            append(brief.toPromptBlock(arabic))
        }
        ctx.treatment?.let { t ->
            appendLine()
            appendLine("TREATMENT PROFILE (the only treatment facts you may use): ${t.name}${if (t.arabicName.isNotBlank()) " / ${t.arabicName}" else ""}")
            if (t.isGeneral) appendLine("General / custom topic: no device-specific claims at all.")
            if (t.needsConfiguration) appendLine("!! NEEDS CONFIGURATION: do not describe mechanism, indications or results. Stay generic and educational.")
            if (t.description.isNotBlank()) appendLine("Description: ${t.description}")
            listOf(
                "Approved indications" to t.approvedIndications, "Approved talking points" to t.talkingPoints, "Precautions" to t.precautions,
                "Preferred terminology/spelling" to t.preferredTerminology, "Common patient questions" to t.commonQuestions,
                "CLAIMS NEVER TO MAKE" to t.forbiddenClaims,
            ).forEach { (label, items) -> if (items.isNotEmpty()) appendLine("$label: ${items.joinToString("; ")}") }
            if (t.referenceNotes.isNotBlank()) appendLine("Reference notes: ${t.referenceNotes.take(2000)}")
        }
        if (ctx.terminology.isNotEmpty()) {
            appendLine("TERMINOLOGY (use these spellings): " + ctx.terminology.joinToString("; ") { if (it.arabic.isNotBlank()) "${it.english} = ${it.arabic}" else it.english })
        }
        ctx.brand?.takeIf { it.isConfigured }?.let { b ->
            appendLine("BRAND: ${listOf(b.clinicName, b.brandName).filter { it.isNotBlank() }.joinToString(" / ")}. Presenter: ${b.presenterName} ${b.professionalTitle}".trim())
            val cta = if (ctx.language == ReelLanguage.IRAQI_ARABIC) b.ctaTextArabic else b.ctaText
            if (cta.isNotBlank()) appendLine("Creator's own CTA wording (may be used when the CTA style fits): $cta")
        }
        appendLine()
        appendLine("TOPIC: ${ctx.topicTitle}")
        if (ctx.topicBrief.isNotBlank()) appendLine("Topic brief: ${ctx.topicBrief}")
        if (ctx.userIdea.isNotBlank()) appendLine("Creator's own idea: ${ctx.userIdea}")
        with(ctx.concept) {
            if (audience.isNotBlank()) appendLine("Audience: $audience")
            if (tone.isNotBlank()) appendLine("Tone: $tone")
            if (cta.isNotBlank()) appendLine("Desired CTA: $cta")
            if (mustInclude.isNotEmpty()) appendLine("Must include: ${mustInclude.joinToString("; ")}")
            if (excludedClaims.isNotEmpty()) appendLine("Never say: ${excludedClaims.joinToString("; ")}")
        }
        if (ctx.recentHooks.isNotEmpty()) appendLine("RECENTLY USED HOOKS (do not reuse or paraphrase): " + ctx.recentHooks.take(10).joinToString(" | "))
        if (ctx.recentTopics.isNotEmpty()) appendLine("RECENT TOPICS: " + ctx.recentTopics.take(10).joinToString(" | "))
    }

    private fun currentBlock(sections: List<ReelSection>, hook: String?) = buildString {
        appendLine("CURRENT SCRIPT:")
        if (!hook.isNullOrBlank()) appendLine("Hook (already placed before section 1): $hook")
        sections.sortedBy { it.index }.forEach { s ->
            val body = if (s.index == 1 && !hook.isNullOrBlank()) ScriptValidator.stripLeadingHook(s.spokenScript, hook) else s.spokenScript
            appendLine("[Section ${s.index}${if (s.locked) " — APPROVED, do not change" else ""}] $body")
            if (s.onScreenText.isNotBlank()) appendLine("  headline: ${s.onScreenText}")
        }
    }

    private suspend fun <T> call(
        ctx: ScriptContext,
        task: String,
        user: String,
        schemaName: String,
        schema: JsonObject,
        serializer: KSerializer<T>,
        prepare: (T) -> T,
        validate: (T) -> ScriptCheck,
        maxTokens: Int,
    ): Pair<T, ScriptCheck> {
        val instructions = system(ctx) + "\n\n" + task
        val turns = mutableListOf(ChatTurn("user", user))
        var lastErrors: List<String> = emptyList()
        repeat(2) { attempt ->
            val result = client.structured(instructions, turns, schemaName, schema, maxTokens)
            val decoded = runCatching { json.decodeFromString(serializer, result.text) }.getOrNull()?.let(prepare)
            val check = if (decoded == null) ScriptCheck(listOf("output did not match the schema"), emptyList()) else validate(decoded)
            if (decoded != null && check.ok) return decoded to check
            lastErrors = check.errors
            if (attempt == 0) {
                turns += ChatTurn("assistant", result.text.take(12000))
                turns += ChatTurn("user", "Your output failed validation:\n- " + check.errors.joinToString("\n- ") + "\nReturn a corrected, complete output that satisfies every rule.")
            }
        }
        throw AiError.Malformed(lastErrors.take(3).joinToString("; "))
    }

    // ------------------------------------------------------------------ use cases

    suspend fun ideas(ctx: ScriptContext): List<FreshIdea> {
        val terms = protectedTerms(ctx)
        val (dto, _) = call(ctx, ScriptPrompts.TASK_IDEAS, contextBlock(ctx), "fresh_ideas", ScriptSchemas.ideas, IdeasDto.serializer(),
            prepare = { d ->
                d.copy(ideas = d.ideas.map { i ->
                    i.copy(
                        title = GeneratedContentNormalizer.clean(i.title, TextField.TITLE, terms),
                        hook = GeneratedContentNormalizer.clean(i.hook, TextField.HOOK, terms),
                        angle = GeneratedContentNormalizer.clean(i.angle, TextField.NOTE),
                        educationalQuestion = GeneratedContentNormalizer.clean(i.educationalQuestion, TextField.NOTE),
                        learningObjective = GeneratedContentNormalizer.clean(i.learningObjective, TextField.NOTE),
                    )
                })
            },
            validate = { d ->
                val errors = buildList {
                    if (d.ideas.size < 3) add("return five distinct ideas")
                    d.ideas.forEachIndexed { i, idea ->
                        if (!PlanValidator.languageMatches(idea.hook, ctx.language)) add("idea ${i + 1} hook must be in ${ctx.language.displayName}")
                        if (!PlanValidator.languageMatches(idea.title, ctx.language)) add("idea ${i + 1} title must be in ${ctx.language.displayName}")
                    }
                    for (i in d.ideas.indices) for (j in i + 1 until d.ideas.size) if (ScriptValidator.similarity(d.ideas[i].hook, d.ideas[j].hook) > 0.6f) add("ideas ${i + 1} and ${j + 1} are too similar")
                }
                ScriptCheck(errors, emptyList())
            }, maxTokens = 2800)
        return dto.ideas.take(5).map {
            FreshIdea(
                title = it.title,
                angle = it.angle,
                hook = it.hook,
                hookStyle = it.hookStyle,
                educationalQuestion = it.educationalQuestion,
                learningObjective = it.learningObjective,
                supportingFactIds = it.supportingFactIds,
                templateFitReason = it.templateFitReason,
            )
        }
    }

    suspend fun generate(ctx: ScriptContext, topicKey: String? = null, preferredHook: String? = null): WrittenScript {
        val terms = protectedTerms(ctx)
        val budget = budget(ctx)
        val user = contextBlock(ctx) + (preferredHook?.let { "\nThe creator liked this opening; use it (or a close variant) as hook option 1: $it" } ?: "")
        val (dto, check) = call(ctx, ScriptPrompts.TASK_GENERATE, user, "reel_script", ScriptSchemas.script, ScriptDto.serializer(),
            prepare = { ScriptValidator.normalize(it, terms) },
            validate = { ScriptValidator.validateScript(it, ctx.template, ctx.language, budget, forbidden(ctx)) },
            maxTokens = 5000)
        val hook = dto.hookOptions.first().text
        val scripts = dto.sections.sortedBy { it.index }.map { s ->
            SectionScript(s.index, if (s.index == 1) ScriptValidator.compose(hook, s.spokenScript) else s.spokenScript, s.onScreenHeadline, s.emphasisWords)
        }
        val meta = ScriptMeta(
            topicTitle = ctx.topicTitle, topicKey = topicKey, creativeAngle = dto.creativeAngle,
            hookOptions = dto.hookOptions.map { HookOption(it.style, it.text) }, selectedHook = 0, activeHook = hook, ctaStyle = dto.ctaStyle,
            medicalReview = dto.medicalReview.map { AiMappers.flag(it) }, qualityNotes = check.notes,
            model = "", promptVersion = ScriptPrompts.VERSION, generatedAt = clock.now(),
        )
        return WrittenScript(dto.title, meta, scripts)
    }

    suspend fun hooks(ctx: ScriptContext, sections: List<ReelSection>, current: List<HookOption>, selected: String?): List<HookOption> {
        val terms = protectedTerms(ctx)
        val user = contextBlock(ctx) + "\n" + currentBlock(sections, selected) + "\nCURRENT HOOKS (write different ones): " + current.joinToString(" | ") { it.text }
        val (dto, _) = call(ctx, ScriptPrompts.TASK_HOOKS, user, "hooks", ScriptSchemas.hooks, HookSetDto.serializer(),
            prepare = { d -> d.copy(hooks = d.hooks.map { it.copy(text = GeneratedContentNormalizer.clean(it.text, TextField.HOOK, terms)) }) },
            validate = { d ->
                val errs = ScriptValidator.hookErrors(d.hooks, ctx.language).toMutableList()
                d.hooks.forEachIndexed { i, h -> if (current.any { ScriptValidator.similarity(it.text, h.text) > 0.7f }) errs += "hook ${i + 1} is too close to an existing hook" }
                ScriptCheck(errs, emptyList())
            }, maxTokens = 1200)
        return dto.hooks.map { HookOption(it.style, it.text) }
    }

    /**
     * Rewrites only [targets] (section indexes). Locked sections are never targeted. The caller
     * merges the result through ProductionPlanner so template-owned fields are untouched.
     */
    suspend fun rewrite(ctx: ScriptContext, sections: List<ReelSection>, hook: String?, action: ScriptAction, targets: Set<Int>, instruction: String? = null): RewriteResult {
        val allowed = targets.filter { i -> sections.firstOrNull { it.index == i }?.locked != true }.toSet()
        if (allowed.isEmpty()) throw IllegalStateException("Those shots are approved. Unlock them to rewrite.")
        val terms = protectedTerms(ctx)
        val budget = budget(ctx)
        val user = buildString {
            append(contextBlock(ctx)); appendLine()
            append(currentBlock(sections, hook))
            appendLine("TARGET SECTION(S): ${allowed.sorted().joinToString()}")
            if (1 in allowed && !hook.isNullOrBlank()) appendLine("For section 1, rewrite only the body after the hook.")
            instruction?.takeIf { it.isNotBlank() }?.let { appendLine("CREATOR NOTE: $it") }
        }
        val (dto, check) = call(ctx, ScriptPrompts.taskRewrite(action), user, "rewrite", ScriptSchemas.rewrite, RewriteDto.serializer(),
            prepare = { d -> d.copy(sections = d.sections.map { s -> ScriptValidator.normalize(s, terms).let { n -> if (n.index == 1 && !hook.isNullOrBlank()) n.copy(spokenScript = ScriptValidator.stripLeadingHook(n.spokenScript, hook)) else n } }) },
            validate = { ScriptValidator.validateRewrite(it, allowed, hook, ctx.template, ctx.language, budget, forbidden(ctx)) },
            maxTokens = 2500)
        val scripts = dto.sections.filter { it.index in allowed }.map { s ->
            SectionScript(s.index, if (s.index == 1 && !hook.isNullOrBlank()) ScriptValidator.compose(hook, s.spokenScript) else s.spokenScript, s.onScreenHeadline, s.emphasisWords)
        }
        return RewriteResult(scripts, GeneratedContentNormalizer.clean(dto.changeSummary, TextField.NOTE), check.notes)
    }

    /** Optional AI editorial pass (naturalness / engagement). Advisory only — never auto-applied. */
    suspend fun editorialReview(ctx: ScriptContext, sections: List<ReelSection>): EditorialReviewDto {
        val user = contextBlock(ctx) + "\n" + currentBlock(sections, null)
        val (dto, _) = call(ctx, ScriptPrompts.TASK_EDITORIAL, user, "editorial_review", ScriptSchemas.editorial, EditorialReviewDto.serializer(),
            prepare = { d -> d.copy(issues = d.issues.map { it.copy(suggestion = GeneratedContentNormalizer.clean(it.suggestion, TextField.SPOKEN, protectedTerms(ctx))) }) },
            validate = { d -> ScriptCheck(if (d.naturalness !in 1..5 || d.engagement !in 1..5) listOf("scores must be 1-5") else emptyList(), emptyList()) },
            maxTokens = 1500)
        return dto
    }
}
