package app.reelify.integration.openai

import app.reelify.core.library.SoundLibrary
import app.reelify.core.model.MediaPlan
import app.reelify.core.model.MusicMood
import app.reelify.core.model.ReelStyle
import app.reelify.core.model.SectionPurpose
import app.reelify.core.model.ShotType
import app.reelify.core.model.TransitionType
import app.reelify.core.model.CaptionPresetId
import app.reelify.core.model.CaptionAnimation
import app.reelify.core.model.ColorLook
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ClaimFlagDto(val claim: String, val issue: String, val suggestion: String, val severity: String)

@Serializable
data class IdeaAnalysisDto(
    val interpretation: String,
    @SerialName("creative_angle") val creativeAngle: String,
    @SerialName("target_audience") val targetAudience: String,
    val hook: String,
    @SerialName("alternative_hooks") val alternativeHooks: List<String>,
    @SerialName("main_message") val mainMessage: String,
    @SerialName("recommended_style") val recommendedStyle: String,
    @SerialName("visual_approach") val visualApproach: String,
    val improvements: List<String>,
    val cautions: List<ClaimFlagDto>,
)

@Serializable
data class SectionDto(
    val index: Int,
    val purpose: String,
    val title: String,
    @SerialName("spoken_script") val spokenScript: String,
    @SerialName("is_voiceover") val isVoiceover: Boolean,
    @SerialName("shot_type") val shotType: String,
    @SerialName("camera_direction") val cameraDirection: String,
    val framing: String,
    @SerialName("lighting_tip") val lightingTip: String,
    @SerialName("delivery_direction") val deliveryDirection: String,
    @SerialName("b_roll_suggestion") val bRollSuggestion: String,
    @SerialName("on_screen_text") val onScreenText: String,
    @SerialName("caption_emphasis") val captionEmphasis: List<String>,
    @SerialName("sfx_cue") val sfxCue: String,
    @SerialName("transition_out") val transitionOut: String,
    @SerialName("edit_notes") val editNotes: String,
    @SerialName("media_plan") val mediaPlan: String,
)

@Serializable
data class PlanDto(
    val title: String,
    @SerialName("target_audience") val targetAudience: String,
    val purpose: String,
    @SerialName("creative_style") val creativeStyle: String,
    val hook: String,
    val sections: List<SectionDto>,
    @SerialName("music_mood") val musicMood: String,
    @SerialName("sfx_notes") val sfxNotes: String,
    val cta: String,
    @SerialName("editing_notes") val editingNotes: String,
    @SerialName("claim_flags") val claimFlags: List<ClaimFlagDto>,
)

@Serializable
data class SectionRegenDto(val section: SectionDto, @SerialName("change_summary") val changeSummary: String)

@Serializable
data class HooksDto(val hooks: List<String>)

@Serializable
data class EffectTogglesDto(
    @SerialName("push_in") val pushIn: String,
    val vignette: String,
    @SerialName("light_overlay") val lightOverlay: String,
    @SerialName("intro_title") val introTitle: String,
    @SerialName("lower_third") val lowerThird: String,
    @SerialName("cta_overlay") val ctaOverlay: String,
)

@Serializable
data class EditGuidanceDto(
    val summary: String,
    @SerialName("caption_preset") val captionPreset: String,
    @SerialName("caption_animation") val captionAnimation: String,
    @SerialName("caption_size") val captionSize: String,
    @SerialName("transition_style") val transitionStyle: String,
    @SerialName("transition_amount") val transitionAmount: String,
    @SerialName("music_mood") val musicMood: String,
    @SerialName("music_level") val musicLevel: String,
    @SerialName("color_look") val colorLook: String,
    @SerialName("color_intensity") val colorIntensity: String,
    val effects: EffectTogglesDto,
    @SerialName("highlight_keywords") val highlightKeywords: List<String>,
    @SerialName("sfx_level") val sfxLevel: String,
)

@Serializable
data class ClaimReviewDto(val flags: List<ClaimFlagDto>, val overall: String)

@Serializable
data class KeywordsDto(val keywords: List<String>)

@Serializable
data class SfxChoiceDto(@SerialName("cue_at") val cueAt: String, @SerialName("sfx_id") val sfxId: String)

@Serializable
data class SoundDesignDto(@SerialName("music_track_id") val musicTrackId: String, val reason: String, val sfx: List<SfxChoiceDto>)

/** Strict JSON schemas mirroring the DTOs above. */
object AiSchemas {
    const val UNCHANGED = "unchanged"
    private val severities = listOf("info", "warning", "blocker")
    val sfxCueValues = listOf("none") + SoundLibrary.sfxIds
    val cuePoints = listOf("start", "boundary_1", "boundary_2", "cta", "end")
    private val tri = listOf("on", "off", UNCHANGED)

    private val claimFlag = S.obj(
        "claim" to S.str("Exact problematic wording"),
        "issue" to S.str(),
        "suggestion" to S.str("Safer alternative wording"),
        "severity" to S.enum(severities),
    )

    val ideaAnalysis: JsonObject = S.obj(
        "interpretation" to S.str(),
        "creative_angle" to S.str(),
        "target_audience" to S.str(),
        "hook" to S.str("Opening line in the reel language"),
        "alternative_hooks" to S.arr(S.str()),
        "main_message" to S.str(),
        "recommended_style" to S.enum(ReelStyle.entries.map { it.wire }),
        "visual_approach" to S.str(),
        "improvements" to S.arr(S.str()),
        "cautions" to S.arr(claimFlag),
    )

    val section: JsonObject = S.obj(
        "index" to S.int("1, 2 or 3"),
        "purpose" to S.enum(SectionPurpose.entries.map { it.wire }),
        "title" to S.str("Short internal title"),
        "spoken_script" to S.str("Exact words to be spoken, in the reel language. Empty only for an intentionally silent visual section."),
        "is_voiceover" to S.bool(),
        "shot_type" to S.enum(ShotType.entries.map { it.wire }),
        "camera_direction" to S.str(),
        "framing" to S.str(),
        "lighting_tip" to S.str(),
        "delivery_direction" to S.str(),
        "b_roll_suggestion" to S.str(),
        "on_screen_text" to S.str("Max ~6 words, reel language"),
        "caption_emphasis" to S.arr(S.str()),
        "sfx_cue" to S.enum(sfxCueValues),
        "transition_out" to S.enum(TransitionType.entries.map { it.wire }),
        "edit_notes" to S.str(),
        "media_plan" to S.enum(MediaPlan.entries.map { it.wire }),
    )

    val plan: JsonObject = S.obj(
        "title" to S.str(),
        "target_audience" to S.str(),
        "purpose" to S.str(),
        "creative_style" to S.str(),
        "hook" to S.str(),
        "sections" to S.arr(section, "Exactly three sections, index 1..3"),
        "music_mood" to S.enum(MusicMood.entries.map { it.wire }),
        "sfx_notes" to S.str(),
        "cta" to S.str(),
        "editing_notes" to S.str(),
        "claim_flags" to S.arr(claimFlag),
    )

    val sectionRegen: JsonObject = S.obj("section" to section, "change_summary" to S.str())

    val hooks: JsonObject = S.obj("hooks" to S.arr(S.str()))

    val editGuidance: JsonObject = S.obj(
        "summary" to S.str("One sentence describing the changes"),
        "caption_preset" to S.enum(CaptionPresetId.entries.filter { it != CaptionPresetId.CUSTOM }.map { it.name.lowercase() } + UNCHANGED),
        "caption_animation" to S.enum(CaptionAnimation.entries.map { it.name.lowercase() } + UNCHANGED),
        "caption_size" to S.enum(listOf("smaller", "larger", UNCHANGED)),
        "transition_style" to S.enum(TransitionType.entries.map { it.wire } + UNCHANGED),
        "transition_amount" to S.enum(listOf("fewer", "more", UNCHANGED)),
        "music_mood" to S.enum(MusicMood.entries.map { it.wire } + UNCHANGED),
        "music_level" to S.enum(listOf("much_lower", "lower", "higher", UNCHANGED)),
        "color_look" to S.enum(ColorLook.entries.map { it.name.lowercase() } + UNCHANGED),
        "color_intensity" to S.enum(listOf("subtle", "medium", "strong", UNCHANGED)),
        "effects" to S.obj(
            "push_in" to S.enum(tri), "vignette" to S.enum(tri), "light_overlay" to S.enum(tri),
            "intro_title" to S.enum(tri), "lower_third" to S.enum(tri), "cta_overlay" to S.enum(tri),
        ),
        "highlight_keywords" to S.arr(S.str()),
        "sfx_level" to S.enum(listOf("none", "sparse", "moderate", UNCHANGED)),
    )

    val claimReview: JsonObject = S.obj("flags" to S.arr(claimFlag), "overall" to S.str())

    val keywords: JsonObject = S.obj("keywords" to S.arr(S.str()))

    val soundDesign: JsonObject = S.obj(
        "music_track_id" to S.enum(SoundLibrary.musicIds),
        "reason" to S.str(),
        "sfx" to S.arr(S.obj("cue_at" to S.enum(cuePoints), "sfx_id" to S.enum(SoundLibrary.sfxIds))),
    )
}
