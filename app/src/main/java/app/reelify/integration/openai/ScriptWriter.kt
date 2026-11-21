package app.reelify.integration.openai

import app.reelify.core.content.ClinicalKnowledgeManager
import app.reelify.core.content.CreativeHistoryRecord
import app.reelify.core.content.CreativeTopic
import app.reelify.core.content.FactualBriefBuilder
import app.reelify.core.content.FactualContentBrief
import app.reelify.core.content.GeneratedContentNormalizer
import app.reelify.core.content.IdeaGenerationMode
import app.reelify.core.content.MedicalGlossary
import app.reelify.core.content.StyleExample
import app.reelify.core.content.TextField
import app.reelify.core.content.TopicLibrary
import app.reelify.core.content.TreatmentKnowledgeCatalog
import app.reelify.core.content.TreatmentKnowledgePack
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
    // ---- Added in Reelify 2.2: preserve full structured CreativeTopic and recent creative history
    val selectedTopic: CreativeTopic? = null,
    val recentHistory: List<CreativeHistoryRecord> = emptyList(),
)

data class WrittenScript(val title: String, val meta: ScriptMeta, val scripts: List<SectionScript>)

data class RewriteResult(val scripts: List<SectionScript>, val summary: String, val notes: List<String>)

typealias FreshIdea = CreativeTopic

/**
 * Reelify 2.2 AI scriptwriter & creative idea engine.
 * - Ideas are grounded in curated quality benchmarks and underused clinical knowledge clusters,
 *   validated through [IdeaValidator], and preserved end-to-end as [CreativeTopic] objects.
 * - Scripts follow a narrative outline tailored to the selected production template, enforce
 *   canonical English medical terminology across all languages, validate cross-section
 *   non-repetition and payoff, and run targeted section-level revisions before returning.
 */
class ScriptWriter(
    private val client: OpenAiClient,
    private val timing: () -> TimingEstimator,
    private val clock: Clock = Clock.System,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun protectedTerms(ctx: ScriptContext): List<String> =
        (listOfNotNull(ctx.treatment?.name) +
            ctx.treatment?.preferredTerminology.orEmpty() +
            ctx.terminology.map { it.english } +
            MedicalGlossary.canonicalEnglishList(ctx.treatment?.id, ctx.terminology) +
            ctx.treatment?.alternativeNames.orEmpty())
            .filter { it.isNotBlank() && it.any { c -> c in 'A'..'Z' || c in 'a'..'z' } }
            .distinctBy { it.lowercase() }

    private fun forbidden(ctx: ScriptContext) = ctx.treatment?.forbiddenClaims.orEmpty() + ctx.concept.excludedClaims

    fun budget(ctx: ScriptContext) = ScriptValidator.wordBudget(timing(), ctx.language, ctx.duration.sectionMs)

    fun system(ctx: ScriptContext): String =
        if (ctx.language == ReelLanguage.IRAQI_ARABIC) ScriptPrompts.systemIraqi(ctx.examples) else ScriptPrompts.SYSTEM_EN

    private fun resolvePack(ctx: ScriptContext): TreatmentKnowledgePack? = ctx.treatment?.let { tr ->
        TreatmentKnowledgeCatalog.packFor(tr.id, tr.linkedReferenceProfileId, tr.needsConfiguration)
    }

    private fun resolveBrief(ctx: ScriptContext): FactualContentBrief? =
        ctx.factualBrief ?: ctx.treatment?.let { tr ->
            val arabic = ctx.language == ReelLanguage.IRAQI_ARABIC
            val pack = resolvePack(ctx)
            val catalogMatch = ctx.selectedTopic?.let { sel ->
                TopicLibrary.topicsFor(tr, ctx.template.id).firstOrNull { it.key == sel.id }
            } ?: TopicLibrary.topicsFor(tr, ctx.template.id)
                .firstOrNull { it.title.en == ctx.topicTitle || it.title.ar == ctx.topicTitle }
            FactualBriefBuilder.build(
                treatment = tr,
                pack = pack,
                topic = catalogMatch,
                selectedTopic = ctx.selectedTopic,
                topicTitle = ctx.topicTitle,
                topicBrief = ctx.topicBrief,
                userIdea = ctx.userIdea,
                arabic = arabic,
            )
        }

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
        val blueprint = ScriptPrompts.templateNarrativeBlueprint(ctx.template.id)
        if (blueprint.isNotBlank()) appendLine(blueprint)
        ctx.template.shots.sortedBy { it.index }.forEach { s ->
            val media = if (s.requiredMedia == MediaRequirement.VOICEOVER_BROLL) "voiceover over device/treatment footage (viewer does not see the speaker)" else "speaking to camera"
            val shotType = if (s.requiredMedia == MediaRequirement.VOICEOVER_BROLL) ShotType.VOICEOVER_BROLL else ShotType.TALKING_HEAD
            val sb = tEst.wordBudgetForShot(ctx.language, ctx.duration.sectionMs, s.role.purpose, shotType)
            appendLine("- Shot ${s.index} · ${s.role.label.en} · $media · target ${sb.promptLabel()}. Writing brief: ${s.writingBrief}")
        }

        val brief = resolveBrief(ctx)
        if (brief != null) {
            appendLine()
            append(brief.toPromptBlock(arabic))
        }

        ctx.selectedTopic?.let { sel ->
            appendLine()
            appendLine("SELECTED CREATIVE TOPIC BRIEF (preserve all clinical focus):")
            appendLine("- Title: ${sel.title}")
            if (sel.patientQuestion.isNotBlank()) appendLine("- Patient question to answer: ${sel.patientQuestion}")
            if (sel.educationalObjective.isNotBlank()) appendLine("- Educational objective: ${sel.educationalObjective}")
            if (sel.specificTakeaway.isNotBlank()) appendLine("- Viewer takeaway: ${sel.specificTakeaway}")
            if (sel.templateFitReason.isNotBlank()) appendLine("- Template fit: ${sel.templateFitReason}")
            if (sel.primaryFactIds.isNotEmpty()) appendLine("- Primary fact IDs: ${sel.primaryFactIds.joinToString()}")
            if (sel.supportingFactIds.isNotEmpty()) appendLine("- Supporting fact IDs: ${sel.supportingFactIds.joinToString()}")
        }

        ctx.treatment?.let { t ->
            appendLine()
            appendLine("TREATMENT PROFILE (the only treatment facts you may use): ${t.name}")
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

        appendLine()
        append(MedicalGlossary.promptBlock(ctx.treatment?.id, ctx.terminology))

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

    private fun ideaContextBlock(
        ctx: ScriptContext,
        benchmarks: List<CreativeTopic>,
        clustersUsed: Set<String>,
        seedTopic: CreativeTopic?,
        mode: IdeaGenerationMode,
    ): String = buildString {
        val arabic = ctx.language == ReelLanguage.IRAQI_ARABIC
        val pack = resolvePack(ctx)
        appendLine("REEL LANGUAGE: ${ctx.language.displayName} (${ctx.language.code})")
        appendLine("TARGET TEMPLATE: ${ctx.template.name.en} — ${ctx.template.structureBrief}")
        val blueprint = ScriptPrompts.templateNarrativeBlueprint(ctx.template.id)
        if (blueprint.isNotBlank()) appendLine(blueprint)

        ctx.treatment?.let { t ->
            appendLine("TREATMENT: ${t.name} (${t.technologyType.ifBlank { t.description.take(140) }})")
            if (t.needsConfiguration) appendLine("!! NEEDS CONFIGURATION: stay safe and general (consultation, barrier care, hygiene). Do NOT invent specs or active ingredients.")
            if (t.forbiddenClaims.isNotEmpty()) appendLine("NEVER CLAIM: ${t.forbiddenClaims.joinToString("; ")}")
        }

        if (benchmarks.isNotEmpty()) {
            appendLine()
            appendLine("GOLD-STANDARD CURATED BENCHMARKS FOR THIS TREATMENT + TEMPLATE (match this depth and specificity; do NOT paraphrase them):")
            benchmarks.forEach { b ->
                appendLine("- Title: \"${b.title}\" | Hook: \"${b.hookAngle}\" | Objective: ${b.educationalObjective} | Facts: ${b.primaryFactIds.joinToString()}")
            }
        }

        if (pack != null && ctx.treatment?.needsConfiguration != true && pack.allFacts().isNotEmpty()) {
            val recentFactIds = ctx.recentHistory.flatMap { it.primaryFactIds }.toSet()
            val seedClusterArea = seedTopic?.primaryFactIds?.firstOrNull()?.let { pack.resolveFactOrQuestion(it)?.topicArea }
            val clusters = ClinicalKnowledgeManager.selectClustersForIdeaGeneration(
                pack = pack,
                recentFactIds = recentFactIds,
                focusClusterArea = if (mode == IdeaGenerationMode.MORE_LIKE_THIS) seedClusterArea else null,
                excludeClusterAreas = if (mode == IdeaGenerationMode.DIFFERENT_ANGLE && seedClusterArea != null) setOf(seedClusterArea) else clustersUsed,
                count = 5,
            )
            appendLine()
            appendLine("UNDERUSED CLINICAL KNOWLEDGE CLUSTERS (build each idea around 1-3 exact fact IDs below):")
            clusters.forEach { cluster ->
                appendLine("* Cluster [${cluster.topicArea}]: ${cluster.labelEn}")
                cluster.facts.forEach { f ->
                    val stmt = if (arabic) MedicalGlossary.enforceEnglishMedicalTerms(f.statementAr).text else f.statementEn
                    appendLine("  - [${f.id}]: $stmt")
                }
            }
        }

        if (seedTopic != null) {
            appendLine()
            when (mode) {
                IdeaGenerationMode.MORE_LIKE_THIS ->
                    appendLine("CREATOR DIRECTION (MORE LIKE THIS): Build 5 fresh variations adjacent to '${seedTopic.title}' exploring deeper mechanisms, safety caveats, or patient questions in the same clinical domain without copying the hook.")
                IdeaGenerationMode.DIFFERENT_ANGLE ->
                    appendLine("CREATOR DIRECTION (DIFFERENT ANGLE): Pivot AWAY from '${seedTopic.title}' and its mechanism/concern; propose 5 completely different clinical angles across underused clusters.")
                IdeaGenerationMode.FRESH -> Unit
            }
        }

        appendLine()
        append(MedicalGlossary.promptBlock(ctx.treatment?.id, ctx.terminology))
        if (ctx.recentHistory.isNotEmpty()) {
            appendLine("RECENTLY USED CONCEPTS TO AVOID REPEATING:")
            ctx.recentHistory.take(10).forEach { h ->
                appendLine("- ${h.topicTitle}${if (h.hook.isNotBlank()) " (hook: ${h.hook})" else ""}")
            }
        } else if (ctx.recentHooks.isNotEmpty()) {
            appendLine("RECENT HOOKS TO AVOID: " + ctx.recentHooks.take(10).joinToString(" | "))
        }
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

    suspend fun ideas(
        ctx: ScriptContext,
        seedTopic: CreativeTopic? = null,
        mode: IdeaGenerationMode = IdeaGenerationMode.FRESH,
    ): List<CreativeTopic> {
        val terms = protectedTerms(ctx)
        val pack = resolvePack(ctx)
        val arabic = ctx.language == ReelLanguage.IRAQI_ARABIC
        val benchmarks = TopicLibrary.topicsFor(ctx.treatment, ctx.template.id)
            .sortedByDescending { it.mostInteresting }
            .take(4)
            .map { t ->
                val suggestion = TopicLibrary.suggestions(ctx.treatment, ctx.template.id, emptyList(), arabic, 60)
                    .firstOrNull { it.idea.key == t.key }
                if (suggestion != null) {
                    CreativeTopic.fromCuratedSuggestion(suggestion, ctx.treatment, ctx.template.id, pack, arabic)
                } else {
                    CreativeTopic(
                        id = t.key,
                        treatmentId = ctx.treatment?.id ?: "general",
                        templateId = ctx.template.id,
                        title = if (arabic) MedicalGlossary.enforceEnglishMedicalTerms(t.title.ar.ifBlank { t.title.en }).text else t.title.en,
                        patientQuestion = if (arabic) t.title.ar.ifBlank { t.title.en } else t.title.en,
                        hookAngle = if (arabic) MedicalGlossary.enforceEnglishMedicalTerms(t.hookAngle.ar.ifBlank { t.hookAngle.en }).text else t.hookAngle.en,
                        educationalObjective = t.brief,
                        primaryFactIds = t.factIds.take(1),
                        supportingFactIds = t.factIds.drop(1),
                        specificTakeaway = t.brief,
                    )
                }
            }

        val instructions = system(ctx) + "\n\n" + ScriptPrompts.TASK_IDEAS
        val promptUser = ideaContextBlock(ctx, benchmarks, emptySet(), seedTopic, mode)
        val turns = mutableListOf(ChatTurn("user", promptUser))
        var bestAccepted = emptyList<CreativeTopic>()
        var lastRejections = emptyList<String>()

        repeat(2) { attempt ->
            val result = client.structured(instructions, turns, "fresh_ideas", ScriptSchemas.ideas, 3200)
            val decoded = runCatching { json.decodeFromString(IdeasDto.serializer(), result.text) }.getOrNull()
            if (decoded == null) {
                lastRejections = listOf("output did not match the ideas schema")
                if (attempt == 0) {
                    turns += ChatTurn("assistant", result.text.take(12000))
                    turns += ChatTurn("user", "Return valid JSON matching the exact `ideas` schema with 5 distinct clinically grounded concepts.")
                }
            } else {
                val (ranked, rejected) = IdeaValidator.evaluateAndRank(
                    rawIdeas = decoded.ideas,
                    treatment = ctx.treatment,
                    template = ctx.template,
                    language = ctx.language,
                    pack = pack,
                    curatedBenchmarks = benchmarks,
                    recentHistory = ctx.recentHistory,
                    terminology = ctx.terminology,
                    protectedTerms = terms,
                    timing = timing(),
                )
                if (ranked.size > bestAccepted.size) bestAccepted = ranked
                lastRejections = rejected
                val minimumAcceptable = minOf(3, decoded.ideas.size.coerceAtLeast(3))
                if (ranked.size >= minimumAcceptable) return ranked
                if (attempt == 0) {
                    turns += ChatTurn("assistant", result.text.take(12000))
                    turns += ChatTurn(
                        "user",
                        "Several ideas failed quality or factual validation:\n- ${rejected.take(5).joinToString("\n- ")}\n" +
                            "Generate 5 specific, non-generic, fact-grounded ideas using valid `[fact_id]` entries and English Latin medical terminology.",
                    )
                }
            }
        }
        if (bestAccepted.isNotEmpty()) return bestAccepted
        throw AiError.Malformed(lastRejections.take(3).joinToString("; ").ifBlank { "No valid clinical ideas were returned" })
    }

    suspend fun generate(ctx: ScriptContext, topicKey: String? = null, preferredHook: String? = null): WrittenScript {
        val terms = protectedTerms(ctx)
        val tEst = timing()
        val budget = budget(ctx)
        val brief = resolveBrief(ctx)
        val user = contextBlock(ctx) + (preferredHook?.let { "\nThe creator liked this opening; use it (or a close variant) as hook option 1: $it" } ?: "")

        val instructions = system(ctx) + "\n\n" + ScriptPrompts.TASK_GENERATE
        val turns = mutableListOf(ChatTurn("user", user))
        var dto: ScriptDto? = null
        var check = ScriptCheck(listOf("uninitialized"), emptyList())

        var targetedAttempted = false
        for (attempt in 0 until 2) {
            if (targetedAttempted) break
            val result = client.structured(instructions, turns, "reel_script", ScriptSchemas.script, 5200)
            val parsed = runCatching { json.decodeFromString(ScriptDto.serializer(), result.text) }.getOrNull()
                ?.let { ScriptValidator.normalize(it, terms) }
            if (parsed == null) {
                check = ScriptCheck(listOf("output did not match the schema"), emptyList())
                if (attempt == 0) {
                    turns += ChatTurn("assistant", result.text.take(12000))
                    turns += ChatTurn("user", "Your output did not match the JSON schema. Return a complete, valid script object.")
                }
            } else {
                val c = ScriptValidator.validateScript(
                    dto = parsed,
                    template = ctx.template,
                    language = ctx.language,
                    budget = budget,
                    forbidden = forbidden(ctx),
                    timing = tEst,
                    factualBrief = brief,
                    terminology = ctx.terminology,
                    treatmentName = ctx.treatment?.name,
                    sectionMs = ctx.duration.sectionMs,
                )
                dto = parsed
                check = c
                if (c.ok) break
                if (attempt == 0) {
                    // Run targeted section revision (counts as request #2; bounded to 2 total requests)
                    val useTargeted = c.targetSectionsForRevision.isNotEmpty() &&
                        c.errors.none { it.startsWith("hook") || it == "invalid cta_style" || it.startsWith("language must be") }
                    if (useTargeted) {
                        targetedAttempted = true
                        val fixedDto = performTargetedSectionRevision(ctx, parsed, c.targetSectionsForRevision, c.errors, terms)
                        if (fixedDto != null) {
                            val recheck = ScriptValidator.validateScript(
                                dto = fixedDto,
                                template = ctx.template,
                                language = ctx.language,
                                budget = budget,
                                forbidden = forbidden(ctx),
                                timing = tEst,
                                factualBrief = brief,
                                terminology = ctx.terminology,
                                treatmentName = ctx.treatment?.name,
                                sectionMs = ctx.duration.sectionMs,
                            )
                            dto = fixedDto
                            check = recheck
                        }
                        break
                    } else {
                        turns += ChatTurn("assistant", result.text.take(12000))
                        turns += ChatTurn(
                            "user",
                            "Your script failed validation:\n- " + c.errors.joinToString("\n- ") +
                                "\nRevise cleanly: keep every section's role distinct, keep medical terms in English Latin script, remove clichés/repeated disclaimers, and return a valid script.",
                        )
                    }
                }
            }
        }

        val finalDto = dto
        if (finalDto == null || !check.ok) {
            throw AiError.Malformed(check.errors.take(3).joinToString("; "))
        }

        val hook = finalDto.hookOptions.first().text
        val scripts = finalDto.sections.sortedBy { it.index }.map { s ->
            SectionScript(s.index, if (s.index == 1) ScriptValidator.compose(hook, s.spokenScript) else s.spokenScript, s.onScreenHeadline, s.emphasisWords)
        }
        val outlineSummary = listOfNotNull(
            finalDto.narrativeOutline.openingQuestion.takeIf { it.isNotBlank() }?.let { "Question: $it" },
            finalDto.narrativeOutline.centralInsight.takeIf { it.isNotBlank() }?.let { "Insight: $it" },
            finalDto.narrativeOutline.viewerLearnedPayoff.takeIf { it.isNotBlank() }?.let { "Takeaway: $it" },
        )
        val sourceIds = (ctx.selectedTopic?.sourceIds.orEmpty() + brief?.sourceIds.orEmpty()).distinct()
        val meta = ScriptMeta(
            topicTitle = ctx.selectedTopic?.title?.ifBlank { ctx.topicTitle } ?: ctx.topicTitle,
            topicKey = topicKey ?: ctx.selectedTopic?.id,
            creativeAngle = finalDto.creativeAngle,
            hookOptions = finalDto.hookOptions.map { HookOption(it.style, it.text) },
            selectedHook = 0,
            activeHook = hook,
            ctaStyle = finalDto.ctaStyle,
            medicalReview = finalDto.medicalReview.map { AiMappers.flag(it) },
            qualityNotes = check.notes,
            model = "",
            promptVersion = ScriptPrompts.VERSION,
            generatedAt = clock.now(),
            creativeTopic = ctx.selectedTopic,
            narrativeSummary = outlineSummary,
            sourceIds = sourceIds,
        )
        return WrittenScript(finalDto.title, meta, scripts)
    }

    private suspend fun performTargetedSectionRevision(
        ctx: ScriptContext,
        draft: ScriptDto,
        targets: Set<Int>,
        reasons: List<String>,
        terms: List<String>,
    ): ScriptDto? {
        val hook = draft.hookOptions.firstOrNull()?.text.orEmpty()
        val user = buildString {
            append(contextBlock(ctx)); appendLine()
            appendLine("NARRATIVE OUTLINE:")
            appendLine("- Opening question: ${draft.narrativeOutline.openingQuestion}")
            appendLine("- Central insight: ${draft.narrativeOutline.centralInsight}")
            appendLine("- Payoff: ${draft.narrativeOutline.viewerLearnedPayoff}")
            appendLine()
            appendLine("CURRENT DRAFT SECTIONS (unchanged sections are context; DO NOT repeat them):")
            draft.sections.sortedBy { it.index }.forEach { s ->
                val state = if (s.index in targets) "NEEDS REVISION" else "KEEP"
                appendLine("[Section ${s.index} — $state] ${s.spokenScript} (headline: ${s.onScreenHeadline})")
            }
            appendLine("VALIDATION ISSUES TO FIX IN TARGET SECTIONS:")
            reasons.forEach { appendLine("- $it") }
            appendLine("TARGET SECTION(S) TO REWRITE: ${targets.sorted().joinToString()}")
        }
        val instructions = system(ctx) + "\n\n" + ScriptPrompts.taskTargetedRevision(targets, reasons)
        val result = client.structured(instructions, listOf(ChatTurn("user", user)), "rewrite", ScriptSchemas.rewrite, 2600)
        val decodedSections = runCatching { json.decodeFromString(RewriteDto.serializer(), result.text).sections }
            .getOrElse {
                runCatching { json.decodeFromString(ScriptDto.serializer(), result.text).sections }.getOrNull()
            } ?: return null
        val normalizedMap = decodedSections.associate { s ->
            val n = ScriptValidator.normalize(s, terms)
            n.index to if (n.index == 1 && hook.isNotBlank()) n.copy(spokenScript = ScriptValidator.stripLeadingHook(n.spokenScript, hook)) else n
        }
        return draft.copy(
            sections = draft.sections.map { existing -> normalizedMap[existing.index] ?: existing },
        )
    }

    suspend fun hooks(ctx: ScriptContext, sections: List<ReelSection>, current: List<HookOption>, selected: String?): List<HookOption> {
        val terms = protectedTerms(ctx)
        val user = contextBlock(ctx) + "\n" + currentBlock(sections, selected) + "\nCURRENT HOOKS (write different ones): " + current.joinToString(" | ") { it.text }
        val (dto, _) = call(ctx, ScriptPrompts.TASK_HOOKS, user, "hooks", ScriptSchemas.hooks, HookSetDto.serializer(),
            prepare = { d -> d.copy(hooks = d.hooks.map { it.copy(text = GeneratedContentNormalizer.clean(it.text, TextField.HOOK, terms)) }) },
            validate = { d ->
                val errs = ScriptValidator.hookErrors(d.hooks, ctx.language, timing = timing(), terminology = ctx.terminology).toMutableList()
                d.hooks.forEachIndexed { i, h -> if (current.any { ScriptValidator.similarity(it.text, h.text) > 0.7f }) errs += "hook ${i + 1} is too close to an existing hook" }
                ScriptCheck(errs, emptyList())
            }, maxTokens = 1200)
        return dto.hooks.map { HookOption(it.style, it.text) }
    }

    /**
     * Rewrites only [targets] (section indexes). Locked sections are never targeted.
     */
    suspend fun rewrite(ctx: ScriptContext, sections: List<ReelSection>, hook: String?, action: ScriptAction, targets: Set<Int>, instruction: String? = null): RewriteResult {
        val allowed = targets.filter { i -> sections.firstOrNull { it.index == i }?.locked != true }.toSet()
        if (allowed.isEmpty()) throw IllegalStateException("Those shots are approved. Unlock them to rewrite.")
        val terms = protectedTerms(ctx)
        val tEst = timing()
        val budget = budget(ctx)
        val otherSections = sections.filter { it.index !in allowed }.map {
            ScriptSectionDto(it.index, it.spokenScript, it.onScreenText, it.captionEmphasis)
        }
        val user = buildString {
            append(contextBlock(ctx)); appendLine()
            append(currentBlock(sections, hook))
            appendLine("TARGET SECTION(S): ${allowed.sorted().joinToString()}")
            if (1 in allowed && !hook.isNullOrBlank()) appendLine("For section 1, rewrite only the body after the hook.")
            instruction?.takeIf { it.isNotBlank() }?.let { appendLine("CREATOR NOTE: $it") }
        }
        val (dto, check) = call(ctx, ScriptPrompts.taskRewrite(action), user, "rewrite", ScriptSchemas.rewrite, RewriteDto.serializer(),
            prepare = { d -> d.copy(sections = d.sections.map { s -> ScriptValidator.normalize(s, terms).let { n -> if (n.index == 1 && !hook.isNullOrBlank()) n.copy(spokenScript = ScriptValidator.stripLeadingHook(n.spokenScript, hook)) else n } }) },
            validate = {
                ScriptValidator.validateRewrite(
                    dto = it,
                    targets = allowed,
                    hook = hook,
                    template = ctx.template,
                    language = ctx.language,
                    budget = budget,
                    forbidden = forbidden(ctx),
                    timing = tEst,
                    terminology = ctx.terminology,
                    sectionMs = ctx.duration.sectionMs,
                    existingOtherSections = otherSections,
                    treatmentName = ctx.treatment?.name,
                )
            },
            maxTokens = 2500)
        val scripts = dto.sections.filter { it.index in allowed }.map { s ->
            SectionScript(s.index, if (s.index == 1 && !hook.isNullOrBlank()) ScriptValidator.compose(hook, s.spokenScript) else s.spokenScript, s.onScreenHeadline, s.emphasisWords)
        }
        return RewriteResult(scripts, GeneratedContentNormalizer.clean(dto.changeSummary, TextField.NOTE, terms), check.notes)
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
