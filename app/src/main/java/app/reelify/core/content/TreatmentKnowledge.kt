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

    fun resolveFactOrQuestion(id: String): VerifiedFact? = factById(id) ?: questionById(id)?.let { q ->
        VerifiedFact(
            id = q.id,
            category = ClaimCategory.EVIDENCE_SUPPORTED_CLINICAL,
            topicArea = "patient_question",
            statementEn = q.answerEn,
            statementAr = q.answerAr,
            qualificationEn = q.qualificationEn,
            qualificationAr = q.qualificationAr,
            sourceIds = q.factIds,
            verificationStatus = verificationStatus,
        )
    }

    fun hasReference(id: String): Boolean = factById(id) != null || questionById(id) != null
}

/**
 * Structured factual brief fed into the AI scriptwriter so every generated reel teaches
 * concrete, verified clinical details rather than generic filler.
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
    val identityPendingConfirmation: Boolean = false,
) {
    fun toPromptBlock(arabic: Boolean): String = buildString {
        appendLine("VERIFIED FACTUAL BRIEF (teach specific facts from this brief — zero generic filler):")
        appendLine("- Treatment: $treatmentName")
        if (technologySummary.isNotBlank()) appendLine("- Verified technology: $technologySummary")
        if (identityPendingConfirmation) {
            appendLine("- IMPORTANT UNCONFIRMED IDENTITY: DO NOT name unconfirmed device/brand lasers or active formulas; teach skin assessment, barrier care, and consulting the clinic directly.")
        }
        if (patientQuestion.isNotBlank()) appendLine("- Real patient question / hook anchor: $patientQuestion")
        if (coreEducationalAnswer.isNotBlank()) appendLine("- Core educational answer: $coreEducationalAnswer")
        primaryFact?.let { f ->
            val stmt = if (arabic) f.statementAr else f.statementEn
            val qual = if (arabic) f.qualificationAr else f.qualificationEn
            appendLine("- Primary fact [${f.id}]: $stmt${if (qual.isNotBlank()) " ($qual)" else ""}")
        }
        if (supportingFacts.isNotEmpty()) {
            appendLine("- Supporting verified facts:")
            supportingFacts.forEach { f ->
                val stmt = if (arabic) f.statementAr else f.statementEn
                appendLine("  * [${f.id}] (${f.category.name.lowercase()}): $stmt")
            }
        }
        if (mandatoryQualification.isNotBlank()) {
            appendLine("- Clinical qualification (state naturally once, do not repeat in every shot): $mandatoryQualification")
        }
        if (practicalTakeaway.isNotBlank()) {
            appendLine("- Concrete takeaway: $practicalTakeaway")
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
        topicTitle: String,
        topicBrief: String,
        userIdea: String,
        arabic: Boolean,
    ): FactualContentBrief {
        val name = treatment?.let { if (arabic) it.arabicName.ifBlank { it.name } else it.name }
            ?: if (arabic) "العلاج" else "Treatment"
        val tech = pack?.let { if (arabic) it.technologyAr else it.technologyEn }
            ?: treatment?.technologyType.orEmpty()

        val pendingConfirmation = treatment?.needsConfiguration == true ||
            pack?.verificationStatus == VerificationStatus.CLINIC_PROTOCOL_REQUIRED

        val topicHook = topic?.hookAngle?.let { if (arabic) it.ar.ifBlank { it.en } else it.en.ifBlank { it.ar } }
        val topicLabel = topic?.title?.let { if (arabic) it.ar.ifBlank { it.en } else it.en.ifBlank { it.ar } }
            ?: topicTitle

        if (pendingConfirmation || pack == null) {
            return FactualContentBrief(
                treatmentName = name,
                technologySummary = tech,
                patientQuestion = topicLabel,
                coreEducationalAnswer = topicBrief.ifBlank { userIdea }.ifBlank {
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
                practicalTakeaway = topic?.brief?.takeIf { it.isNotBlank() } ?: if (arabic) {
                    "الاستشارة والتشخيص الصحيح أهم خطوة قبل اختيار أي إجراء."
                } else {
                    "Proper clinical assessment comes before selecting any procedure."
                },
                visualFocus = topicHook,
                identityPendingConfirmation = pendingConfirmation,
            )
        }

        val allFacts = pack.allFacts()
        val requested = topic?.factIds.orEmpty().mapNotNull { id -> pack.resolveFactOrQuestion(id) }
        val selected = if (requested.isNotEmpty()) {
            requested
        } else {
            scoreFactsForQuery(allFacts, "$topicTitle $topicBrief $userIdea")
        }

        val primary = selected.firstOrNull() ?: allFacts.firstOrNull()
        val supporting = (selected.drop(1) + allFacts.filter { it.id != primary?.id })
            .distinctBy { it.id }
            .take(3)

        val question = topicLabel.ifBlank {
            pack.patientQuestions.firstOrNull { pq ->
                primary != null && primary.id in pq.factIds
            }?.let { if (arabic) it.questionAr else it.questionEn }.orEmpty()
        }

        val answer = primary?.let { if (arabic) it.statementAr else it.statementEn }
            ?: topicBrief.ifBlank { treatment?.description.orEmpty() }

        val qual = listOfNotNull(
            primary?.let { if (arabic) it.qualificationAr else it.qualificationEn }?.takeIf { it.isNotBlank() },
            pack.sessions.firstOrNull()?.let { if (arabic) it.qualificationAr else it.qualificationEn }?.takeIf { it.isNotBlank() },
            pack.precautions.firstOrNull()?.let { if (arabic) it.statementAr else it.statementEn }?.takeIf { it.isNotBlank() },
        ).firstOrNull().orEmpty()

        val takeaway = topic?.brief?.takeIf { it.isNotBlank() }
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
            visualFocus = topicHook,
            identityPendingConfirmation = false,
        )
    }

    private fun scoreFactsForQuery(facts: List<VerifiedFact>, query: String): List<VerifiedFact> {
        val tokens = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()
        if (tokens.isEmpty()) return facts.take(4)
        return facts.sortedByDescending { fact ->
            val hay = "${fact.topicArea} ${fact.statementEn} ${fact.statementAr}".lowercase()
            tokens.count { tok -> hay.contains(tok) }
        }.take(4)
    }
}
