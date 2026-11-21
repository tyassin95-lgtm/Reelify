package app.reelify.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class BrandTemplate(val label: String) {
    LUXURY_MINIMAL("Luxury Minimal"), CLINICAL_PREMIUM("Clinical Premium"), CINEMATIC_BURGUNDY("Cinematic Burgundy"),
    MODERN_SOCIAL("Modern Social"), CLEAN_EDUCATIONAL("Clean Educational")
}

@Serializable
enum class LowerThirdStyle(val label: String) { BAR("Accent bar"), GLASS("Frosted glass"), MINIMAL("Minimal line"), PILL("Pill") }

@Serializable
enum class OutroStyle(val label: String) { CARD("CTA card"), LOGO_CENTER("Logo reveal"), MINIMAL("Minimal text") }

@Serializable
enum class BrandFont(val label: String) { MODERN("Modern"), ELEGANT("Elegant"), ROUNDED("Rounded") }

/** Permanent, reusable branding configuration (snapshotted into each project at edit time). */
@Serializable
data class BrandProfile(
    val logoPath: String? = null,
    val brandName: String = "",
    val clinicName: String = "",
    val presenterName: String = "",
    val professionalTitle: String = "",
    val primaryColor: Long = 0xFF9D174D,
    val secondaryColor: Long = 0xFFE11D48,
    val textColor: Long = 0xFFF8FAFC,
    val font: BrandFont = BrandFont.MODERN,
    val instagram: String = "",
    val snapchat: String = "",
    val booking: String = "",
    val ctaText: String = "",
    val ctaTextArabic: String = "",
    val lowerThird: LowerThirdStyle = LowerThirdStyle.BAR,
    val outro: OutroStyle = OutroStyle.CARD,
    val logoPosition: LogoPosition = LogoPosition.TOP_RIGHT,
    val watermarkOpacity: Float = 0.55f,
    val template: BrandTemplate = BrandTemplate.CINEMATIC_BURGUNDY,
    val beforeAfterDisclosure: String = "Individual results vary. Photos unretouched.",
    val beforeAfterDisclosureArabic: String = "النتائج تختلف من شخص لآخر. الصور بدون تعديل.",
) {
    val isConfigured: Boolean get() = brandName.isNotBlank() || clinicName.isNotBlank() || logoPath != null

    companion object {
        fun applyTemplate(base: BrandProfile, t: BrandTemplate): BrandProfile = when (t) {
            BrandTemplate.LUXURY_MINIMAL -> base.copy(template = t, primaryColor = 0xFFC8A97E, secondaryColor = 0xFF1C1917, textColor = 0xFFFAFAF9, font = BrandFont.ELEGANT, lowerThird = LowerThirdStyle.MINIMAL, outro = OutroStyle.LOGO_CENTER)
            BrandTemplate.CLINICAL_PREMIUM -> base.copy(template = t, primaryColor = 0xFF0EA5E9, secondaryColor = 0xFF0F172A, textColor = 0xFFFFFFFF, font = BrandFont.MODERN, lowerThird = LowerThirdStyle.GLASS, outro = OutroStyle.CARD)
            BrandTemplate.CINEMATIC_BURGUNDY -> base.copy(template = t, primaryColor = 0xFF9D174D, secondaryColor = 0xFFE11D48, textColor = 0xFFF8FAFC, font = BrandFont.MODERN, lowerThird = LowerThirdStyle.BAR, outro = OutroStyle.CARD)
            BrandTemplate.MODERN_SOCIAL -> base.copy(template = t, primaryColor = 0xFFE11D48, secondaryColor = 0xFFFACC15, textColor = 0xFFFFFFFF, font = BrandFont.ROUNDED, lowerThird = LowerThirdStyle.PILL, outro = OutroStyle.CARD)
            BrandTemplate.CLEAN_EDUCATIONAL -> base.copy(template = t, primaryColor = 0xFF14B8A6, secondaryColor = 0xFF134E4A, textColor = 0xFFFFFFFF, font = BrandFont.ROUNDED, lowerThird = LowerThirdStyle.GLASS, outro = OutroStyle.MINIMAL)
        }
    }
}

/** Editable treatment knowledge profile used to ground the AI. */
@Serializable
data class Treatment(
    val id: String,
    val name: String,
    val arabicName: String = "",
    val alternativeNames: List<String> = emptyList(),
    val description: String = "",
    val approvedIndications: List<String> = emptyList(),
    val talkingPoints: List<String> = emptyList(),
    val precautions: List<String> = emptyList(),
    val preferredTerminology: List<String> = emptyList(),
    val preferredHooks: List<String> = emptyList(),
    val commonQuestions: List<String> = emptyList(),
    val forbiddenClaims: List<String> = emptyList(),
    val brollSuggestions: List<String> = emptyList(),
    val ctaStyle: String = "",
    val referenceNotes: String = "",
    val needsConfiguration: Boolean = false,
    val builtIn: Boolean = true,
    val sortOrder: Int = 0,
    val accentColor: Long = 0xFF9D174D,
    val updatedAt: Long = 0,
    // ---- Added in Reelify 2.1 for versioned non-destructive built-in upgrades & verified sourcing
    val seedVersion: Int = 1,
    val userEditedFields: Set<String> = emptySet(),
    val manufacturer: String = "",
    val deviceModel: String = "",
    val technologyType: String = "",
    val identityConfirmed: Boolean = true,
    val linkedReferenceProfileId: String? = null,
) {
    val isGeneral: Boolean get() = id == GENERAL_ID

    companion object {
        const val GENERAL_ID = "general"
    }
}

/** User terminology dictionary: preferred spellings + common misrecognitions for caption correction. */
@Serializable
data class TerminologyEntry(
    val id: String,
    val english: String,
    val arabic: String = "",
    val pronunciation: String = "",
    val misrecognitions: List<String> = emptyList(),
)
