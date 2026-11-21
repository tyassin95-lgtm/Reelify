package app.reelify.core.content

import app.reelify.core.model.Treatment
import kotlinx.serialization.Serializable

@Serializable
enum class VerificationStatus {
    VERIFIED,
    MANUFACTURER_SPEC,
    CLINICAL_GUIDANCE,
    CLINIC_PROTOCOL_REQUIRED,
    UNVERIFIED,
}

@Serializable
enum class SourceType {
    MANUFACTURER_SPEC,
    REGULATORY_CLEARANCE,
    CLINICAL_LITERATURE,
    CLINIC_REFERENCE,
}

@Serializable
enum class ClaimCategory {
    DEVICE_SPEC,
    MANUFACTURER_APPLICATION,
    EVIDENCE_SUPPORTED_CLINICAL,
    GENERAL_PRACTICE_GUIDANCE,
    CLINIC_SPECIFIC_PROTOCOL,
    UNVERIFIED_MARKETING,
}

@Serializable
data class KnowledgeSource(
    val id: String,
    val title: String,
    val url: String,
    val type: SourceType,
    val retrievalDate: String,
    val excerpt: String,
)

@Serializable
data class VerifiedFact(
    val id: String,
    val category: ClaimCategory,
    val topicArea: String,
    val statementEn: String,
    val statementAr: String,
    val qualificationEn: String = "",
    val qualificationAr: String = "",
    val sourceIds: List<String> = emptyList(),
    val verificationStatus: VerificationStatus = VerificationStatus.VERIFIED,
    val supportingFactIds: List<String> = emptyList(),
)

@Serializable
data class VerifiedQuestion(
    val id: String,
    val questionEn: String,
    val questionAr: String,
    val answerEn: String,
    val answerAr: String,
    val qualificationEn: String,
    val qualificationAr: String,
    val factIds: List<String>,
    val sourceIds: List<String> = emptyList(),
)

@Serializable
data class TreatmentKnowledgePack(
    val treatmentId: String,
    val version: Int,
    val manufacturer: String?,
    val model: String?,
    val technologyEn: String,
    val technologyAr: String,
    val mechanism: List<VerifiedFact> = emptyList(),
    val applications: List<VerifiedFact> = emptyList(),
    val treatmentAreas: List<VerifiedFact> = emptyList(),
    val procedure: List<VerifiedFact> = emptyList(),
    val patientQuestions: List<VerifiedQuestion> = emptyList(),
    val sessions: List<VerifiedFact> = emptyList(),
    val sensationAndRecovery: List<VerifiedFact> = emptyList(),
    val precautions: List<VerifiedFact> = emptyList(),
    val comparisons: List<VerifiedFact> = emptyList(),
    val misconceptions: List<VerifiedFact> = emptyList(),
    val sourceReferences: List<KnowledgeSource> = emptyList(),
    val verificationStatus: VerificationStatus = VerificationStatus.VERIFIED,
    val identityNoteEn: String? = null,
    val identityNoteAr: String? = null,
) {
    fun allFacts(): List<VerifiedFact> =
        mechanism + applications + treatmentAreas + procedure + sessions +
            sensationAndRecovery + precautions + comparisons + misconceptions

    fun factById(id: String): VerifiedFact? = allFacts().firstOrNull { it.id == id }

    fun questionById(id: String): VerifiedQuestion? = patientQuestions.firstOrNull { it.id == id }

    fun sourceById(id: String): KnowledgeSource? = sourceReferences.firstOrNull { it.id == id }

    /**
     * Resolves a verified fact ID OR a verified patient-question ID while keeping `sourceIds`
     * (src_*) strictly distinct from `supportingFactIds` (*_mech_*, etc.).
     */
    fun resolveFactOrQuestion(id: String): VerifiedFact? = factById(id) ?: questionById(id)?.let { q ->
        val backingFacts = q.factIds.mapNotNull { factById(it) }
        val resolvedSourceIds = (q.sourceIds + backingFacts.flatMap { it.sourceIds })
            .distinct()
            .ifEmpty { sourceReferences.map { it.id }.take(1) }
        VerifiedFact(
            id = q.id,
            category = backingFacts.firstOrNull()?.category ?: ClaimCategory.EVIDENCE_SUPPORTED_CLINICAL,
            topicArea = backingFacts.firstOrNull()?.topicArea ?: "patient_question",
            statementEn = q.answerEn,
            statementAr = q.answerAr,
            qualificationEn = q.qualificationEn,
            qualificationAr = q.qualificationAr,
            sourceIds = resolvedSourceIds,
            verificationStatus = backingFacts.firstOrNull()?.verificationStatus ?: verificationStatus,
            supportingFactIds = q.factIds,
        )
    }

    fun resolveSourceIdsFor(factOrQuestionIds: Collection<String>): List<String> =
        factOrQuestionIds.mapNotNull { resolveFactOrQuestion(it) }
            .flatMap { it.sourceIds }
            .filter { id -> sourceById(id) != null }
            .distinct()

    fun hasReference(id: String): Boolean = factById(id) != null || questionById(id) != null
}

/**
 * Structured factual brief fed into the AI scriptwriter so every generated reel teaches
 * concrete, verified clinical details without cross-indication contamination.
 */
data class FactualContentBrief(
    val treatmentName: String,
    val technologySummary: String,
    val patientQuestion: String,
    val coreEducationalAnswer: String,
    val primaryFact: VerifiedFact?,
    val supportingFacts: List<VerifiedFact>,
    val mandatoryQualification: String,
    val forbiddenClaims: List<String>,
    val practicalTakeaway: String,
    val visualFocus: String?,
    val templateFitReason: String = "",
    val sourceIds: List<String> = emptyList(),
    val excludedTopicAreas: Set<String> = emptySet(),
    val identityPendingConfirmation: Boolean = false,
) {
    val allIncludedFactIds: List<String>
        get() = (listOfNotNull(primaryFact?.id) + supportingFacts.map { it.id }).distinct()

    fun toPromptBlock(arabic: Boolean): String = buildString {
        fun clean(txt: String) = if (arabic) MedicalGlossary.enforceEnglishMedicalTerms(txt).text else txt
        appendLine("VERIFIED FACTUAL BRIEF (teach specific facts from this brief — zero generic filler):")
        appendLine("- Treatment: $treatmentName")
        if (technologySummary.isNotBlank()) appendLine("- Verified technology: ${clean(technologySummary)}")
        if (identityPendingConfirmation) {
            appendLine("- IMPORTANT UNCONFIRMED IDENTITY: DO NOT name unconfirmed device/brand lasers or active formulas; teach skin assessment, barrier care, and consulting the clinic directly.")
        }
        if (patientQuestion.isNotBlank()) appendLine("- Real patient question / hook anchor: ${clean(patientQuestion)}")
        if (coreEducationalAnswer.isNotBlank()) appendLine("- Core educational objective & answer: ${clean(coreEducationalAnswer)}")
        primaryFact?.let { f ->
            val stmt = clean(if (arabic) f.statementAr else f.statementEn)
            val qual = clean(if (arabic) f.qualificationAr else f.qualificationEn)
            appendLine("- Primary fact [${f.id}] (${f.topicArea}): $stmt${if (qual.isNotBlank()) " ($qual)" else ""}")
        }
        if (supportingFacts.isNotEmpty()) {
            appendLine("- Supporting verified facts (focus ONLY on this topic):")
            supportingFacts.forEach { f ->
                val stmt = clean(if (arabic) f.statementAr else f.statementEn)
                appendLine("  * [${f.id}] (${f.category.name.lowercase()} · ${f.topicArea}): $stmt")
            }
        }
        if (excludedTopicAreas.isNotEmpty()) {
            appendLine("- TOPIC FOCUS BOUNDARY: stay strictly on this topic; do NOT mix in unrelated indications (${excludedTopicAreas.joinToString(", ")}).")
        }
        if (mandatoryQualification.isNotBlank()) {
            appendLine("- Clinical qualification (state naturally AT MOST ONCE, never repeat across shots): ${clean(mandatoryQualification)}")
        }
        if (practicalTakeaway.isNotBlank()) {
            appendLine("- Concrete viewer takeaway / payoff: ${clean(practicalTakeaway)}")
        }
        if (templateFitReason.isNotBlank()) {
            appendLine("- Template structure fit: ${clean(templateFitReason)}")
        }
        if (visualFocus?.isNotBlank() == true) {
            appendLine("- Visual / conceptual focus: ${clean(visualFocus)}")
        }
        if (forbiddenClaims.isNotEmpty()) {
            appendLine("- Never imply or claim: ${forbiddenClaims.joinToString("; ")}")
        }
    }
}

object FactualBriefBuilder {

    fun build(
        treatment: Treatment?,
        pack: TreatmentKnowledgePack?,
        topic: TreatmentTopic? = null,
        selectedTopic: CreativeTopic? = null,
        topicTitle: String,
        topicBrief: String,
        userIdea: String,
        arabic: Boolean,
    ): FactualContentBrief {
        val rawName = treatment?.let { if (arabic) it.arabicName.ifBlank { it.name } else it.name }
            ?: if (arabic) "العلاج" else "Treatment"
        val name = if (arabic) MedicalGlossary.enforceEnglishMedicalTerms(rawName).text else rawName
        val tech = pack?.let { if (arabic) it.technologyAr else it.technologyEn }
            ?: treatment?.technologyType.orEmpty()

        val pendingConfirmation = treatment?.needsConfiguration == true ||
            pack?.verificationStatus == VerificationStatus.CLINIC_PROTOCOL_REQUIRED

        val topicHook = selectedTopic?.hookAngle?.takeIf { it.isNotBlank() }
            ?: topic?.hookAngle?.let { if (arabic) it.ar.ifBlank { it.en } else it.en.ifBlank { it.ar } }
        val topicLabel = selectedTopic?.patientQuestion?.takeIf { it.isNotBlank() }
            ?: topic?.title?.let { if (arabic) it.ar.ifBlank { it.en } else it.en.ifBlank { it.ar } }
            ?: topicTitle

        if (pendingConfirmation || pack == null) {
            return FactualContentBrief(
                treatmentName = name,
                technologySummary = tech,
                patientQuestion = topicLabel,
                coreEducationalAnswer = selectedTopic?.educationalObjective?.takeIf { it.isNotBlank() }
                    ?: topicBrief.ifBlank { userIdea }.ifBlank {
                        "Explain why an in-person clinical assessment and protocol confirmation come before treatment."
                    },
                primaryFact = null,
                supportingFacts = emptyList(),
                mandatoryQualification = if (arabic) {
                    "تقييم الطبيب أو المختص بالعيادة يحدد الخطة المناسبة لكل مراجع."
                } else {
                    "An in-person assessment determines suitability and the specific clinic protocol."
                },
                forbiddenClaims = treatment?.forbiddenClaims.orEmpty(),
                practicalTakeaway = selectedTopic?.specificTakeaway?.takeIf { it.isNotBlank() }
                    ?: topic?.brief?.takeIf { it.isNotBlank() } ?: if (arabic) {
                        "الاستشارة والتشخيص الصحيح أهم خطوة قبل اختيار أي إجراء."
                    } else {
                        "Proper clinical assessment comes before selecting any procedure."
                    },
                visualFocus = selectedTopic?.visualEmphasis?.takeIf { it.isNotBlank() } ?: topicHook,
                templateFitReason = selectedTopic?.templateFitReason.orEmpty(),
                identityPendingConfirmation = pendingConfirmation,
            )
        }

        val allFacts = pack.allFacts()
        val combinedQuery = listOf(
            topicTitle,
            topicBrief,
            userIdea,
            selectedTopic?.title.orEmpty(),
            selectedTopic?.patientQuestion.orEmpty(),
            selectedTopic?.educationalObjective.orEmpty(),
        ).joinToString(" ")
        val queryTokens = tokenize(combinedQuery)

        // Resolve requested facts from selected CreativeTopic first, else TreatmentTopic, preserving provenance.
        val requestedIds = (selectedTopic?.primaryFactIds.orEmpty() +
            selectedTopic?.supportingFactIds.orEmpty() +
            topic?.factIds.orEmpty()).distinct()
        val expandedRequested = expandRequestedFacts(pack, requestedIds)

        val selected = if (expandedRequested.isNotEmpty()) {
            expandedRequested
        } else {
            val scored = scoreFactsForQuery(allFacts, queryTokens)
            if (scored.isNotEmpty()) scored else allFacts.take(3)
        }

        val primary = selected.firstOrNull() ?: allFacts.firstOrNull()
        val excludedAreas = ClinicalKnowledgeManager.incompatibleTopicAreas(
            treatmentId = pack.treatmentId,
            query = combinedQuery,
            selectedFacts = selected,
        )

        // Strict topic-relevance filter: never pad with unrelated clinical indications!
        val supporting = (selected.drop(1) + allFacts.filter { candidate ->
            candidate.id != primary?.id &&
                candidate.topicArea !in excludedAreas &&
                (candidate.topicArea == primary?.topicArea ||
                    scoreFact(candidate, queryTokens) >= 2)
        }).distinctBy { it.id }.take(3)

        val sourceIds = pack.resolveSourceIdsFor(listOfNotNull(primary?.id) + supporting.map { it.id })

        val question = topicLabel.ifBlank {
            pack.patientQuestions.firstOrNull { pq ->
                primary != null && primary.id in pq.factIds
            }?.let { if (arabic) it.questionAr else it.questionEn }.orEmpty()
        }

        val answer = selectedTopic?.educationalObjective?.takeIf { it.isNotBlank() }
            ?: primary?.let { if (arabic) it.statementAr else it.statementEn }
            ?: topicBrief.ifBlank { treatment?.description.orEmpty() }

        val qual = listOfNotNull(
            primary?.let { if (arabic) it.qualificationAr else it.qualificationEn }?.takeIf { it.isNotBlank() },
            supporting.firstOrNull()?.let { if (arabic) it.qualificationAr else it.qualificationEn }?.takeIf { it.isNotBlank() },
        ).firstOrNull().orEmpty()

        val takeaway = selectedTopic?.specificTakeaway?.takeIf { it.isNotBlank() }
            ?: topic?.brief?.takeIf { it.isNotBlank() }
            ?: supporting.firstOrNull()?.let { if (arabic) it.statementAr else it.statementEn }
            ?: answer

        return FactualContentBrief(
            treatmentName = name,
            technologySummary = tech,
            patientQuestion = question,
            coreEducationalAnswer = answer,
            primaryFact = primary,
            supportingFacts = supporting,
            mandatoryQualification = qual,
            forbiddenClaims = treatment?.forbiddenClaims.orEmpty(),
            practicalTakeaway = takeaway,
            visualFocus = selectedTopic?.visualEmphasis?.takeIf { it.isNotBlank() } ?: topicHook,
            templateFitReason = selectedTopic?.templateFitReason.orEmpty(),
            sourceIds = sourceIds,
            excludedTopicAreas = excludedAreas,
            identityPendingConfirmation = false,
        )
    }

    private fun expandRequestedFacts(pack: TreatmentKnowledgePack, ids: List<String>): List<VerifiedFact> {
        val out = mutableListOf<VerifiedFact>()
        for (id in ids) {
            val resolved = pack.resolveFactOrQuestion(id) ?: continue
            out += resolved
            if (resolved.supportingFactIds.isNotEmpty()) {
                resolved.supportingFactIds.mapNotNull { pack.factById(it) }.forEach { backing ->
                    if (out.none { it.id == backing.id }) out += backing
                }
            }
        }
        return out.distinctBy { it.id }
    }

    private fun tokenize(query: String): Set<String> =
        query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()

    private fun scoreFact(fact: VerifiedFact, tokens: Set<String>): Int {
        if (tokens.isEmpty()) return 0
        val hay = "${fact.topicArea} ${fact.statementEn} ${fact.statementAr}".lowercase()
        return tokens.count { tok -> hay.contains(tok) }
    }

    private fun scoreFactsForQuery(facts: List<VerifiedFact>, tokens: Set<String>): List<VerifiedFact> {
        if (tokens.isEmpty()) return emptyList()
        return facts
            .map { it to scoreFact(it, tokens) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(4)
    }
}
