package app.reelify.core.template

import app.reelify.core.model.BeautyPreset
import app.reelify.core.model.CaptionPresetId
import app.reelify.core.model.ColorLook
import app.reelify.core.model.MediaPlan
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.SectionPurpose
import app.reelify.core.model.ShotType
import app.reelify.core.model.TransitionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Text shown to the creator in English or Arabic UI. Spoken dialogue is never stored here. */
@Serializable
data class LocalizedText(val en: String, val ar: String = en) {
    fun resolve(arabic: Boolean): String = if (arabic && ar.isNotBlank()) ar else en
}

/** The narrative job a shot does. Fixed by the template; the AI only writes words for it. */
@Serializable
enum class ShotRole(val wire: String, val purpose: SectionPurpose, val label: LocalizedText) {
    @SerialName("hook") HOOK("hook", SectionPurpose.HOOK, LocalizedText("The Hook", "الافتتاحية")),
    @SerialName("explanation") EXPLANATION("explanation", SectionPurpose.EXPLANATION, LocalizedText("The Explanation", "الشرح")),
    @SerialName("conclusion_cta") CONCLUSION_CTA("conclusion_cta", SectionPurpose.CALL_TO_ACTION, LocalizedText("Conclusion & CTA", "الخلاصة والدعوة")),
    @SerialName("myth") MYTH("myth", SectionPurpose.MYTH, LocalizedText("The Myth", "الفكرة الغلط")),
    @SerialName("correction") CORRECTION("correction", SectionPurpose.FACT, LocalizedText("The Correction", "التصحيح")),
    @SerialName("takeaway") TAKEAWAY("takeaway", SectionPurpose.CALL_TO_ACTION, LocalizedText("The Takeaway", "الخلاصة")),
    @SerialName("introduction") INTRODUCTION("introduction", SectionPurpose.HOOK, LocalizedText("Introduction", "التعريف")),
    @SerialName("showcase") SHOWCASE("showcase", SectionPurpose.DEMONSTRATION, LocalizedText("The Showcase", "العرض")),
    @SerialName("suitability_cta") SUITABILITY_CTA("suitability_cta", SectionPurpose.CALL_TO_ACTION, LocalizedText("Who It's For & CTA", "لمن يناسب والدعوة")),
    @SerialName("insight_1") INSIGHT_1("insight_1", SectionPurpose.HOOK, LocalizedText("First Thing", "الشغلة الأولى")),
    @SerialName("insight_2") INSIGHT_2("insight_2", SectionPurpose.EXPLANATION, LocalizedText("Second Thing", "الشغلة الثانية")),
    @SerialName("insight_3_cta") INSIGHT_3_CTA("insight_3_cta", SectionPurpose.CALL_TO_ACTION, LocalizedText("Third Thing & CTA", "الشغلة الثالثة والدعوة")),
    @SerialName("question") QUESTION("question", SectionPurpose.HOOK, LocalizedText("The Question", "السؤال")),
    @SerialName("answer") ANSWER("answer", SectionPurpose.EXPLANATION, LocalizedText("The Answer", "الجواب")),
    @SerialName("qualification") QUALIFICATION("qualification", SectionPurpose.CALL_TO_ACTION, LocalizedText("The Important Detail", "التفصيل المهم")),
    @SerialName("concern") CONCERN("concern", SectionPurpose.PROBLEM, LocalizedText("The Concern", "المشكلة")),
    @SerialName("approach") APPROACH("approach", SectionPurpose.SOLUTION, LocalizedText("The Approach", "الحل")),
    @SerialName("expectations_cta") EXPECTATIONS_CTA("expectations_cta", SectionPurpose.CALL_TO_ACTION, LocalizedText("Expectations & CTA", "التوقعات والدعوة"));

    companion object {
        fun fromWire(w: String?): ShotRole? = entries.firstOrNull { it.wire == w }
    }
}

/** What media a shot needs. Determines the Film-stage card and the timeline roles. */
@Serializable
enum class MediaRequirement(val shotType: ShotType, val mediaPlan: MediaPlan, val needsVoice: Boolean, val needsFootage: Boolean) {
    /** One take of the presenter speaking to camera (footage + speech in one clip). */
    @SerialName("talking") TALKING_TO_CAMERA(ShotType.TALKING_HEAD, MediaPlan.RECORD_TALKING, needsVoice = false, needsFootage = true),
    /** Silent cutaway footage (device / treatment / clinic) + a separately recorded voiceover. */
    @SerialName("voiceover_broll") VOICEOVER_BROLL(ShotType.VOICEOVER_BROLL, MediaPlan.VOICEOVER, needsVoice = true, needsFootage = true),
}

@Serializable
enum class CameraFacing { FRONT, BACK }

/** Composition target for the framing guide drawn over the camera preview. */
@Serializable
enum class FramingPreset(
    val label: LocalizedText,
    /** Face oval guide as fractions of the 9:16 frame (left, top, width, height); null = no face guide. */
    val faceGuide: List<Float>?,
    /** Target face-height fraction band used for live framing hints. */
    val faceHeight: ClosedFloatingPointRange<Float>?,
) {
    @SerialName("medium_close_up") MEDIUM_CLOSE_UP(LocalizedText("Medium close-up", "لقطة متوسطة قريبة"), listOf(0.28f, 0.17f, 0.44f, 0.29f), 0.18f..0.42f),
    @SerialName("close_up") CLOSE_UP(LocalizedText("Close-up", "لقطة قريبة"), listOf(0.22f, 0.14f, 0.56f, 0.36f), 0.26f..0.52f),
    @SerialName("medium") MEDIUM(LocalizedText("Medium shot", "لقطة متوسطة"), listOf(0.33f, 0.16f, 0.34f, 0.22f), 0.12f..0.32f),
    @SerialName("device_macro") DEVICE_MACRO(LocalizedText("Device close-up", "لقطة قريبة للجهاز"), null, null),
    @SerialName("treatment_area") TREATMENT_AREA(LocalizedText("Treatment area", "منطقة العلاج"), null, null),
}

/** Fixed camera behaviour for a shot. Real hardware capabilities are checked at bind time. */
@Serializable
data class CameraPreset(
    val id: String,
    val facing: CameraFacing,
    val teleprompter: Boolean,
    val beautyByDefault: Boolean,
    val faceGuide: Boolean,
    val focusAssist: Boolean,
    val exposureHint: LocalizedText? = null,
)

@Serializable
enum class TeleprompterPosition { NEAR_LENS, MIDDLE, LOWER }

@Serializable
data class TeleprompterPreset(val id: String, val enabled: Boolean, val position: TeleprompterPosition = TeleprompterPosition.NEAR_LENS, val visibleLines: Int = 3, val sizeSp: Float = 24f)

@Serializable
data class MusicPreset(val id: String, val trackId: String, val volume: Float, val ducking: Boolean = true, val duckDepth: Float = 0.6f, val fadeInMs: Long = 600, val fadeOutMs: Long = 1800)

@Serializable
data class TransitionPreset(val id: String, val betweenShots: TransitionType, val withinShot: TransitionType, val durationMs: Long)

@Serializable
data class BrandingPreset(val id: String, val watermark: Boolean, val lowerThird: Boolean, val ctaOverlay: Boolean, val logoReveal: Boolean, val ctaDurationMs: Long = 3500)

/** Where SFX are allowed. Subtle by design — never a whoosh on every cut. */
@Serializable
enum class SfxStrategy { NONE, INTRO_ONLY, SECTION_ACCENTS }

@Serializable
enum class TitlePlacement { UPPER_THIRD, CENTER }

/** Which visual motif the template card thumbnail draws (all thumbnails are drawn in code). */
@Serializable
enum class TemplateArt { EXPERT, MYTH, SHOWCASE, THREE_THINGS, QUESTION, PROBLEM, CUSTOM }

/** One of exactly three fixed shots in a production template. */
@Serializable
data class ShotRecipe(
    val index: Int,
    val role: ShotRole,
    val requiredMedia: MediaRequirement,
    val framing: FramingPreset,
    val cameraPresetId: String,
    val teleprompterPresetId: String,
    /** One short, stable filming instruction shown on the shot card and in the camera. */
    val instruction: LocalizedText,
    /** Stable lighting / delivery notes (kept for the optional "details" panel). */
    val lighting: LocalizedText,
    val delivery: LocalizedText,
    /** What the dialogue for this shot must accomplish. Sent to the AI; never shown as dialogue. */
    val writingBrief: String,
    val optionalBroll: Boolean,
    val transitionOut: TransitionType,
    /** SFX cue at the start of this shot when the template's [SfxStrategy] allows it. */
    val sfxIn: String? = null,
    val brollHint: LocalizedText? = null,
)

/**
 * A deterministic, versioned production format. Everything about HOW a reel is filmed and edited
 * lives here; the AI only supplies words. Projects store a full snapshot so later template updates
 * never change an existing project.
 */
@Serializable
data class ReelTemplate(
    val id: String,
    val version: Int,
    val name: LocalizedText,
    val description: LocalizedText,
    val previewAsset: TemplateArt,
    val supportedDurations: Set<ReelDuration> = ReelDuration.entries.toSet(),
    val shots: List<ShotRecipe>,
    val captionPresetId: CaptionPresetId,
    val musicPresetId: String,
    val transitionPresetId: String,
    val brandingPresetId: String,
    val colorLook: ColorLook,
    val colorIntensity: Float,
    val talkingBeauty: BeautyPreset,
    val sfxStrategy: SfxStrategy,
    val titlePlacement: TitlePlacement = TitlePlacement.UPPER_THIRD,
    val introTitle: Boolean = true,
    val pushIn: Boolean = true,
    val vignette: Float = 0.2f,
    val lightOverlay: Boolean = false,
    val accent: Long = 0xFFE11D48,
    /** Narrative structure passed to the AI. */
    val structureBrief: String,
    val builtIn: Boolean = true,
    val baseTemplateId: String? = null,
) {
    fun shot(index: Int): ShotRecipe = shots.first { it.index == index }
}

/** How a project was set up. Projects created before Reelify 2.0 are LEGACY and keep their own settings. */
@Serializable
enum class ProductionMode { LEGACY, TEMPLATE }

/** Per-shot user overrides (project-level). Null = follow the template. */
@Serializable
data class ShotOverride(
    val facing: CameraFacing? = null,
    val teleprompter: Boolean? = null,
    val beauty: Boolean? = null,
)

@Serializable
data class TemplateOverrides(val shots: Map<Int, ShotOverride> = emptyMap()) {
    fun forShot(index: Int): ShotOverride = shots[index] ?: ShotOverride()
    fun withShot(index: Int, transform: (ShotOverride) -> ShotOverride) = copy(shots = shots + (index to transform(forShot(index))))
}
