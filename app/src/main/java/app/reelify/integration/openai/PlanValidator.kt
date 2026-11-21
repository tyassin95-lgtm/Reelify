package app.reelify.integration.openai

import app.reelify.core.model.ClaimFlag
import app.reelify.core.model.ClaimSeverity
import app.reelify.core.model.CreativeFeedback
import app.reelify.core.model.MediaPlan
import app.reelify.core.model.MusicMood
import app.reelify.core.model.PlanMeta
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelSection
import app.reelify.core.model.ReelStyle
import app.reelify.core.model.SectionPurpose
import app.reelify.core.model.ShotType
import app.reelify.core.model.TransitionType

/**
 * Validates AI output before it can touch the project model. Malformed output is rejected (and
 * repaired by a follow-up request) rather than rendered.
 */
object PlanValidator {

    fun languageRatio(text: String): Pair<Float, Float> {
        var arabic = 0; var latin = 0
        for (ch in text) {
            when {
                ch in '؀'..'ۿ' || ch in 'ݐ'..'ݿ' || ch in 'ﭐ'..'﷿' || ch in 'ﹰ'..'﻿' -> arabic++
                ch in 'a'..'z' || ch in 'A'..'Z' -> latin++
            }
        }
        val total = (arabic + latin).coerceAtLeast(1)
        return arabic.toFloat() / total to latin.toFloat() / total
    }

    fun languageMatches(text: String, language: ReelLanguage): Boolean {
        if (text.isBlank()) return true
        val (ar, la) = languageRatio(text)
        return when (language) {
            ReelLanguage.IRAQI_ARABIC -> ar >= 0.6f       // device names in Latin script are allowed
            ReelLanguage.ENGLISH -> la >= 0.85f
        }
    }

    fun validateSection(dto: SectionDto, language: ReelLanguage, expectedIndex: Int? = null): List<String> {
        val errors = mutableListOf<String>()
        val where = "section ${dto.index}"
        if (expectedIndex != null && dto.index != expectedIndex) errors += "$where: index must be $expectedIndex"
        if (dto.index !in 1..3) errors += "$where: index must be 1, 2 or 3"
        if (SectionPurpose.fromWire(dto.purpose) == null) errors += "$where: invalid purpose '${dto.purpose}'"
        val shot = ShotType.fromWire(dto.shotType)
        if (shot == null) errors += "$where: invalid shot_type '${dto.shotType}'"
        if (TransitionType.fromWire(dto.transitionOut) == null) errors += "$where: invalid transition_out"
        if (MediaPlan.fromWire(dto.mediaPlan) == null) errors += "$where: invalid media_plan"
        if (dto.sfxCue !in AiSchemas.sfxCueValues) errors += "$where: invalid sfx_cue"
        val silentAllowed = shot != null && !shot.needsSpeech && !dto.isVoiceover
        if (dto.spokenScript.isBlank() && !silentAllowed) errors += "$where: spoken_script is empty but the shot requires dialogue"
        if (!languageMatches(dto.spokenScript, language)) errors += "$where: spoken_script must be written in ${language.displayName}"
        if (dto.onScreenText.isNotBlank() && !languageMatches(dto.onScreenText, language)) errors += "$where: on_screen_text must be in ${language.displayName}"
        if (dto.cameraDirection.isBlank()) errors += "$where: camera_direction is required"
        if (dto.deliveryDirection.isBlank() && !silentAllowed) errors += "$where: delivery_direction is required"
        if (dto.spokenScript.length > 1200) errors += "$where: spoken_script is far too long"
        return errors
    }

    fun validatePlan(dto: PlanDto, language: ReelLanguage): List<String> {
        val errors = mutableListOf<String>()
        if (dto.sections.size != 3) errors += "plan must contain exactly 3 sections (got ${dto.sections.size})"
        val indexes = dto.sections.map { it.index }.sorted()
        if (dto.sections.size == 3 && indexes != listOf(1, 2, 3)) errors += "section indexes must be exactly 1, 2, 3"
        dto.sections.forEach { errors += validateSection(it, language) }
        if (MusicMood.fromWire(dto.musicMood) == null) errors += "invalid music_mood"
        if (dto.title.isBlank()) errors += "title is required"
        return errors
    }

    fun validateIdea(dto: IdeaAnalysisDto, language: ReelLanguage): List<String> {
        val errors = mutableListOf<String>()
        if (dto.interpretation.isBlank()) errors += "interpretation is required"
        if (dto.hook.isBlank()) errors += "hook is required"
        if (!languageMatches(dto.hook, language)) errors += "hook must be written in ${language.displayName}"
        return errors
    }
}

object AiMappers {
    fun flag(dto: ClaimFlagDto, sectionId: String? = null) = ClaimFlag(
        claim = dto.claim, issue = dto.issue, suggestion = dto.suggestion,
        severity = when (dto.severity) { "blocker" -> ClaimSeverity.BLOCKER; "info" -> ClaimSeverity.INFO; else -> ClaimSeverity.WARNING },
        sectionId = sectionId, source = "ai",
    )

    fun feedback(dto: IdeaAnalysisDto, now: Long) = CreativeFeedback(
        interpretation = dto.interpretation.trim(),
        creativeAngle = dto.creativeAngle.trim(),
        targetAudience = dto.targetAudience.trim(),
        hook = dto.hook.trim(),
        alternativeHooks = dto.alternativeHooks.map { it.trim() }.filter { it.isNotEmpty() }.take(4),
        mainMessage = dto.mainMessage.trim(),
        recommendedStyle = ReelStyle.fromWire(dto.recommendedStyle),
        visualApproach = dto.visualApproach.trim(),
        improvements = dto.improvements.map { it.trim() }.filter { it.isNotEmpty() },
        cautions = dto.cautions.map { flag(it) },
        createdAt = now,
    )

    /** Maps a validated section DTO. The caller supplies the stable id — the model never controls ids. */
    fun section(dto: SectionDto, id: String, now: Long): ReelSection {
        val shot = ShotType.fromWire(dto.shotType) ?: ShotType.TALKING_HEAD
        val emphasis = dto.captionEmphasis.map { it.trim() }.filter { it.isNotEmpty() && dto.spokenScript.contains(it, ignoreCase = true) }.take(4)
        return ReelSection(
            id = id, index = dto.index,
            purpose = SectionPurpose.fromWire(dto.purpose) ?: SectionPurpose.EXPLANATION,
            title = app.reelify.core.content.GeneratedContentNormalizer.clean(dto.title, app.reelify.core.content.TextField.TITLE),
            spokenScript = app.reelify.core.content.GeneratedContentNormalizer.clean(dto.spokenScript),
            isVoiceover = dto.isVoiceover,
            shotType = shot,
            cameraDirection = dto.cameraDirection.trim(),
            framing = dto.framing.trim(),
            lightingTip = dto.lightingTip.trim(),
            deliveryDirection = dto.deliveryDirection.trim(),
            bRollSuggestion = dto.bRollSuggestion.trim(),
            onScreenText = app.reelify.core.content.GeneratedContentNormalizer.clean(dto.onScreenText, app.reelify.core.content.TextField.HEADLINE),
            captionEmphasis = emphasis,
            sfxCue = dto.sfxCue.takeIf { it != "none" }.orEmpty(),
            transitionOut = TransitionType.fromWire(dto.transitionOut) ?: TransitionType.CUT,
            editNotes = dto.editNotes.trim(),
            mediaPlan = MediaPlan.fromWire(dto.mediaPlan) ?: MediaPlan.defaultFor(shot),
            updatedAt = now,
        )
    }

    fun plan(dto: PlanDto, model: String) = PlanMeta(
        title = dto.title.trim(),
        targetAudience = dto.targetAudience.trim(),
        purpose = dto.purpose.trim(),
        creativeStyle = dto.creativeStyle.trim(),
        hook = dto.hook.trim(),
        musicMood = MusicMood.fromWire(dto.musicMood) ?: MusicMood.ELEGANT,
        sfxNotes = dto.sfxNotes.trim(),
        cta = dto.cta.trim(),
        editingNotes = dto.editingNotes.trim(),
        claimFlags = dto.claimFlags.map { flag(it) },
        promptVersion = PromptTemplates.VERSION,
        model = model,
    )

    fun toDto(s: ReelSection) = SectionDto(
        index = s.index, purpose = s.purpose.wire, title = s.title, spokenScript = s.spokenScript, isVoiceover = s.isVoiceover,
        shotType = s.shotType.wire, cameraDirection = s.cameraDirection, framing = s.framing, lightingTip = s.lightingTip,
        deliveryDirection = s.deliveryDirection, bRollSuggestion = s.bRollSuggestion, onScreenText = s.onScreenText,
        captionEmphasis = s.captionEmphasis, sfxCue = s.sfxCue.ifBlank { "none" }, transitionOut = s.transitionOut.wire,
        editNotes = s.editNotes, mediaPlan = s.mediaPlan.wire,
    )
}
