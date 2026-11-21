package app.reelify.integration.openai

import app.reelify.core.content.CreativeHistoryRecord
import app.reelify.core.content.CreativeTopic
import app.reelify.core.content.DialectChecker
import app.reelify.core.content.GeneratedContentNormalizer
import app.reelify.core.content.MedicalGlossary
import app.reelify.core.content.TextField
import app.reelify.core.content.TopicCategory
import app.reelify.core.content.TopicOrigin
import app.reelify.core.content.TopicUiCategory
import app.reelify.core.content.TreatmentKnowledgePack
import app.reelify.core.content.VerificationStatus
import app.reelify.core.content.VerifiedFact
import app.reelify.core.model.ClaimSeverity
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.TerminologyEntry
import app.reelify.core.model.Treatment
import app.reelify.core.policy.ClaimChecker
import app.reelify.core.template.ReelTemplate
import app.reelify.core.timing.TimingEstimator

data class IdeaCandidateEvaluation(
    val topic: CreativeTopic,
    val accepted: Boolean,
    val rejectionReasons: List<String>,
    val score: Int,
)

/**
 * Multi-stage validation, clinical grounding check, deduplication, and editorial ranking pipeline
 * for AI-generated creative ideas:
 *
 *   Candidate generation -> Normalization & English terminology -> Factual & physics validation ->
 *   Editorial & dialect quality -> Ranking & deduplication
 */
object IdeaValidator {

    private val genericTitlePatterns = listOf(
        Regex("^why .+ is (great|amazing|the best|effective|worth it)\\b", RegexOption.IGNORE_CASE),
        Regex("^everything (you need )?to know about\\b", RegexOption.IGNORE_CASE),
        Regex("^discover (the secret|amazing results)\\b", RegexOption.IGNORE_CASE),
        Regex("^book your (session|consultation)\\b", RegexOption.IGNORE_CASE),
        Regex("^(كل شي عن|تعرّف على فوائد|أفضل جهاز|احجز جلستك)"),
    )

    private val nonsenseOrVaguePatterns = listOf(
        Regex("\\(propose fresh ideas\\)", RegexOption.IGNORE_CASE),
        Regex("^(general|random|test|untitled|topic \\d+)$", RegexOption.IGNORE_CASE),
    )

    fun evaluateAndRank(
        rawIdeas: List<IdeaDto>,
        treatment: Treatment?,
        template: ReelTemplate,
        language: ReelLanguage,
        pack: TreatmentKnowledgePack?,
        curatedBenchmarks: List<CreativeTopic>,
        recentHistory: List<CreativeHistoryRecord>,
        terminology: List<TerminologyEntry>,
        protectedTerms: Collection<String>,
        timing: TimingEstimator = TimingEstimator(),
    ): Pair<List<CreativeTopic>, List<String>> {
        val evaluated = rawIdeas.mapIndexed { idx, dto ->
            evaluateSingle(
                index = idx,
                dto = dto,
                treatment = treatment,
                template = template,
                language = language,
                pack = pack,
                curatedBenchmarks = curatedBenchmarks,
                recentHistory = recentHistory,
                terminology = terminology,
                protectedTerms = protectedTerms,
                timing = timing,
            )
        }
        val accepted = mutableListOf<IdeaCandidateEvaluation>()
        val rejectionSummary = mutableListOf<String>()

        for (ev in evaluated.sortedByDescending { it.score }) {
            if (!ev.accepted) {
                rejectionSummary += "Idea '${ev.topic.title}': ${ev.rejectionReasons.joinToString("; ")}"
                continue
            }
            // Deduplicate candidates against already accepted picks
            val dupOfAccepted = accepted.firstOrNull { prev ->
                ScriptValidator.contentSimilarity(prev.topic.title, ev.topic.title) > 0.52f ||
                    ScriptValidator.contentSimilarity(prev.topic.hookAngle, ev.topic.hookAngle) > 0.56f
            }
            if (dupOfAccepted != null) {
                rejectionSummary += "Idea '${ev.topic.title}' duplicated '${dupOfAccepted.topic.title}'"
                continue
            }
            accepted += ev
        }
        return accepted.take(5).map { it.topic } to rejectionSummary
    }

    fun evaluateSingle(
        index: Int,
        dto: IdeaDto,
        treatment: Treatment?,
        template: ReelTemplate,
        language: ReelLanguage,
        pack: TreatmentKnowledgePack?,
        curatedBenchmarks: List<CreativeTopic> = emptyList(),
        recentHistory: List<CreativeHistoryRecord> = emptyList(),
        terminology: List<TerminologyEntry> = emptyList(),
        protectedTerms: Collection<String> = emptyList(),
        timing: TimingEstimator = TimingEstimator(),
    ): IdeaCandidateEvaluation {
        val reasons = mutableListOf<String>()

        // Stage 1: Normalization & English medical terminology enforcement
        val title = GeneratedContentNormalizer.clean(dto.title, TextField.TITLE, protectedTerms)
        val hook = GeneratedContentNormalizer.clean(dto.hook, TextField.HOOK, protectedTerms)
        val angle = GeneratedContentNormalizer.clean(dto.angle, TextField.NOTE, protectedTerms)
        val question = GeneratedContentNormalizer.clean(dto.educationalQuestion, TextField.NOTE, protectedTerms)
        val objective = GeneratedContentNormalizer.clean(dto.learningObjective, TextField.NOTE, protectedTerms)
        val takeaway = GeneratedContentNormalizer.clean(dto.specificTakeaway.ifBlank { angle }, TextField.NOTE, protectedTerms)
        val visualEmphasis = GeneratedContentNormalizer.clean(dto.visualEmphasis, TextField.NOTE, protectedTerms)
        val templateFit = GeneratedContentNormalizer.clean(dto.templateFitReason, TextField.NOTE, protectedTerms)

        // Stage 2: Language, medical terminology & physics sanity checks
        if (title.isBlank() || hook.isBlank()) reasons += "missing title or hook"
        if (!PlanValidator.languageMatches(title, language)) reasons += "title must be in ${language.displayName}"
        if (!PlanValidator.languageMatches(hook, language)) reasons += "hook must be in ${language.displayName}"
        if (timing.countWords(title) < 3) reasons += "title is too vague/short"
        if (timing.countWords(hook) > 18 || timing.estimatedDurationMs(hook, language) > 8500L) {
            reasons += "hook exceeds 3-second spoken length"
        }

        val combinedText = listOf(title, hook, angle, question, objective, takeaway).joinToString(" ")
        if (MedicalGlossary.validate(title, terminology).isNotEmpty() || MedicalGlossary.validate(hook, terminology).isNotEmpty()) {
            reasons += "title/hook contains non-English medical terminology"
        }
        val genericHits = DialectChecker.genericFillerHits(combinedText) + DialectChecker.overused(combinedText)
        if (genericHits.isNotEmpty()) reasons += "generic promotional filler (${genericHits.first()})"
        if (genericTitlePatterns.any { it.containsMatchIn(title) } || nonsenseOrVaguePatterns.any { it.containsMatchIn(title) }) {
            reasons += "generic or nonsensical title"
        }
        if (language == ReelLanguage.IRAQI_ARABIC && DialectChecker.analyze(hook).likelyFormal) {
            reasons += "hook sounds like formal MSA instead of natural Iraqi"
        }
        val claims = ClaimChecker.check(combinedText, null, treatment?.forbiddenClaims.orEmpty())
        val blockers = claims.filter { it.severity == ClaimSeverity.BLOCKER }
        if (blockers.isNotEmpty()) reasons += "unsafe claim (${blockers.first().claim})"

        // Critical clinical physics guard: CFU Èlife is HIFU (ultrasound), never a laser
        if (treatment?.id == "cfu" && Regex("\\bcfu\\b[^.?!]{0,40}\\blaser\\b|\\bليزر\\s+(?:cfu|سي)", RegexOption.IGNORE_CASE).containsMatchIn(combinedText)) {
            if (!combinedText.contains("not a laser", ignoreCase = true) && !combinedText.contains("مو ليزر")) {
                reasons += "mischaracterizes CFU Èlife (HIFU ultrasound) as a laser"
            }
        }

        // Stage 3: Factual grounding & alignment
        val verifiedPack = pack?.takeIf {
            it.verificationStatus != VerificationStatus.CLINIC_PROTOCOL_REQUIRED && treatment?.needsConfiguration != true
        }
        var resolvedFacts: List<VerifiedFact> = emptyList()
        var resolvedSourceIds: List<String> = emptyList()

        if (verifiedPack != null) {
            val direct = dto.supportingFactIds.mapNotNull { verifiedPack.resolveFactOrQuestion(it) }.distinctBy { it.id }
            val unknownIds = dto.supportingFactIds.filter { !verifiedPack.hasReference(it) }
            if (unknownIds.isNotEmpty()) {
                reasons += "unknown supporting_fact_ids (${unknownIds.joinToString()})"
            }
            if (direct.isEmpty()) {
                // Allow automatic match ONLY if topic cleanly aligns with at least one verified fact (>= 2 clinical tokens)
                val qTokens = tokenize("$title $question $objective $angle")
                val matched = verifiedPack.allFacts()
                    .map { f -> f to countOverlap(qTokens, "${f.topicArea} ${f.statementEn} ${f.statementAr}") }
                    .filter { it.second >= 2 }
                    .sortedByDescending { it.second }
                    .map { it.first }
                    .take(2)
                if (matched.isEmpty()) {
                    reasons += "no valid verified fact grounding or alignment"
                } else {
                    resolvedFacts = matched
                }
            } else {
                // Verify semantic alignment between idea and referenced fact(s)
                val qTokens = tokenize("$title $question $objective $angle $takeaway")
                val anyAligned = direct.any { f ->
                    countOverlap(qTokens, "${f.topicArea} ${f.statementEn} ${f.statementAr}") >= 1
                }
                if (!anyAligned) {
                    reasons += "supporting_fact_ids do not match the topic's clinical concept"
                }
                resolvedFacts = direct
            }
            resolvedSourceIds = resolvedFacts.flatMap { it.sourceIds }.filter { verifiedPack.sourceById(it) != null }.distinct()
        }

        if (question.isBlank() && objective.isBlank() && angle.isBlank()) {
            reasons += "missing educational question and objective"
        }

        // Stage 4: Originality / deduplication vs curated benchmarks & recent creative history
        val dupCurated = curatedBenchmarks.any { b ->
            ScriptValidator.contentSimilarity(b.title, title) > 0.62f ||
                ScriptValidator.contentSimilarity(b.hookAngle, hook) > 0.65f
        }
        if (dupCurated) reasons += "too close to an existing curated benchmark topic"

        val dupHistory = recentHistory.any { h ->
            ScriptValidator.contentSimilarity(h.topicTitle, title) > 0.62f ||
                ScriptValidator.contentSimilarity(h.hook, hook) > 0.65f ||
                (objective.isNotBlank() && h.educationalObjective.isNotBlank() &&
                    ScriptValidator.contentSimilarity(h.educationalObjective, objective) > 0.70f)
        }
        if (dupHistory) reasons += "too close to a recently used reel topic"

        // Editorial quality score (0..100)
        var score = 50
        if (resolvedFacts.isNotEmpty()) score += 18
        if (resolvedSourceIds.isNotEmpty()) score += 8
        if (question.length >= 12) score += 8
        if (objective.length >= 16) score += 8
        if (templateFit.isNotBlank()) score += 5
        if (takeaway.length >= 15) score += 5
        if (reasons.isNotEmpty()) score = (score - reasons.size * 25).coerceAtLeast(0)

        val uiCategory = mapUiCategory(dto.category)
        val topic = CreativeTopic(
            id = "ai_${template.id}_${index}_${title.lowercase().hashCode().toUInt()}",
            treatmentId = treatment?.id ?: Treatment.GENERAL_ID,
            templateId = template.id,
            title = title,
            category = mapTopicCategory(uiCategory),
            uiCategory = uiCategory,
            patientQuestion = question.ifBlank { title },
            hookAngle = hook,
            hookStyle = dto.hookStyle,
            educationalObjective = objective.ifBlank { angle },
            primaryFactIds = resolvedFacts.take(1).map { it.id },
            supportingFactIds = resolvedFacts.drop(1).map { it.id },
            specificTakeaway = takeaway.ifBlank { objective.ifBlank { angle } },
            visualEmphasis = visualEmphasis,
            sourceIds = resolvedSourceIds,
            templateFitReason = templateFit,
            origin = TopicOrigin.AI,
            verificationStatus = verifiedPack?.verificationStatus ?: VerificationStatus.CLINIC_PROTOCOL_REQUIRED,
            qualityScore = (score / 100f).coerceIn(0f, 1f),
        )

        return IdeaCandidateEvaluation(
            topic = topic,
            accepted = reasons.isEmpty(),
            rejectionReasons = reasons,
            score = score,
        )
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()

    private fun countOverlap(tokens: Set<String>, haystack: String): Int {
        val h = haystack.lowercase()
        return tokens.count { tok -> h.contains(tok) }
    }

    private fun mapUiCategory(wire: String): TopicUiCategory = when (wire.lowercase()) {
        "patient_question" -> TopicUiCategory.PATIENT_QUESTIONS
        "sessions_expectations" -> TopicUiCategory.SESSIONS_EXPECTATIONS
        "myths_clarity" -> TopicUiCategory.MYTHS
        "before_aftercare" -> TopicUiCategory.BEFORE_AFTER_CARE
        "comparisons" -> TopicUiCategory.COMPARISONS
        else -> TopicUiCategory.HOW_IT_WORKS
    }

    private fun mapTopicCategory(ui: TopicUiCategory): TopicCategory = when (ui) {
        TopicUiCategory.PATIENT_QUESTIONS -> TopicCategory.PATIENT_QUESTIONS
        TopicUiCategory.SESSIONS_EXPECTATIONS -> TopicCategory.EXPECTATIONS
        TopicUiCategory.MYTHS -> TopicCategory.MISCONCEPTIONS
        TopicUiCategory.BEFORE_AFTER_CARE -> TopicCategory.AFTERCARE
        TopicUiCategory.COMPARISONS -> TopicCategory.COMPARISONS
        else -> TopicCategory.DEVICE
    }
}
