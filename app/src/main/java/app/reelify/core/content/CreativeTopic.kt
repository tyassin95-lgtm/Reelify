package app.reelify.core.content

import app.reelify.core.model.Treatment
import kotlinx.serialization.Serializable

@Serializable
enum class TopicOrigin { CURATED, AI, USER }

@Serializable
enum class IdeaGenerationMode { FRESH, MORE_LIKE_THIS, DIFFERENT_ANGLE }

/**
 * Canonical creative topic model shared across curated catalog topics, AI-generated ideas,
 * and user-authored concepts. Preserves full educational and factual provenance all the way into
 * script generation and project persistence.
 */
@Serializable
data class CreativeTopic(
    val id: String,
    val treatmentId: String,
    val templateId: String,
    val title: String,
    val category: TopicCategory = TopicCategory.DEVICE,
    val uiCategory: TopicUiCategory = TopicUiCategory.HOW_IT_WORKS,
    val patientQuestion: String,
    val hookAngle: String,
    val hookStyle: String = "curiosity",
    val educationalObjective: String,
    val primaryFactIds: List<String> = emptyList(),
    val supportingFactIds: List<String> = emptyList(),
    val specificTakeaway: String,
    val visualEmphasis: String = "",
    val sourceIds: List<String> = emptyList(),
    val templateFitReason: String = "",
    val origin: TopicOrigin = TopicOrigin.CURATED,
    val verificationStatus: VerificationStatus = VerificationStatus.VERIFIED,
    val qualityScore: Float = 1.0f,
) {
    val allFactIds: List<String> get() = (primaryFactIds + supportingFactIds).distinct()

    companion object {
        fun fromCuratedSuggestion(
            suggestion: TopicSuggestion,
            treatment: Treatment?,
            templateId: String,
            pack: TreatmentKnowledgePack?,
            arabic: Boolean,
        ): CreativeTopic {
            val tid = treatment?.id ?: Treatment.GENERAL_ID
            val factIds = suggestion.factIds.distinct()
            val primary = factIds.take(1)
            val supporting = factIds.drop(1)
            val sources = pack?.resolveSourceIdsFor(factIds).orEmpty()
            val cleanTitle = if (arabic) MedicalGlossary.enforceEnglishMedicalTerms(suggestion.title).text else suggestion.title
            val cleanHook = if (arabic) MedicalGlossary.enforceEnglishMedicalTerms(suggestion.hookAngle).text else suggestion.hookAngle
            return CreativeTopic(
                id = suggestion.idea.key,
                treatmentId = tid,
                templateId = templateId,
                title = cleanTitle,
                category = suggestion.idea.category,
                uiCategory = suggestion.uiCategory,
                patientQuestion = cleanTitle,
                hookAngle = cleanHook.ifBlank { cleanTitle },
                educationalObjective = suggestion.idea.brief,
                primaryFactIds = primary,
                supportingFactIds = supporting,
                specificTakeaway = suggestion.idea.brief,
                visualEmphasis = cleanHook,
                sourceIds = sources,
                templateFitReason = "Built for ${templateId.replace('_', ' ')} three-shot arc",
                origin = TopicOrigin.CURATED,
                verificationStatus = pack?.verificationStatus ?: VerificationStatus.CLINIC_PROTOCOL_REQUIRED,
                qualityScore = if (suggestion.mostInteresting) 1.0f else 0.9f,
            )
        }

        fun fromUserInput(
            treatment: Treatment?,
            templateId: String,
            text: String,
            arabic: Boolean,
        ): CreativeTopic {
            val clean = if (arabic) MedicalGlossary.enforceEnglishMedicalTerms(text.trim()).text else text.trim()
            return CreativeTopic(
                id = "user_" + clean.lowercase().hashCode().toUInt(),
                treatmentId = treatment?.id ?: Treatment.GENERAL_ID,
                templateId = templateId,
                title = clean.take(120),
                category = TopicCategory.ADVICE,
                uiCategory = TopicUiCategory.ALL,
                patientQuestion = clean,
                hookAngle = "",
                educationalObjective = clean,
                primaryFactIds = emptyList(),
                supportingFactIds = emptyList(),
                specificTakeaway = clean,
                origin = TopicOrigin.USER,
                verificationStatus = VerificationStatus.CLINIC_PROTOCOL_REQUIRED,
                qualityScore = 0.8f,
            )
        }
    }
}
